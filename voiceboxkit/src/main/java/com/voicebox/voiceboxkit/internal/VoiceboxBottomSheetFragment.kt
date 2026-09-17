package com.voicebox.voiceboxkit

import android.Manifest
import android.animation.ValueAnimator
import android.os.Build
import android.provider.Settings
import android.view.animation.DecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Outline
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.webkit.GeolocationPermissions
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Hosts the Voicebox WebView as a full-screen Fragment overlay on the Activity window.
 *
 * Using Fragment (not BottomSheetDialogFragment) is intentional: Chrome's getUserMedia
 * audio capture requires the WebView to be in the Activity's window hierarchy. A Dialog
 * creates a separate window and AudioRecord consistently fails with NotReadableError on
 * both Samsung and Pixel devices regardless of permission grants or retries.
 *
 * Mirrors iOS VoiceboxViewController.
 */
internal class VoiceboxBottomSheetFragment : Fragment() {

    internal lateinit var voiceboxConfig: VoiceboxView

    private lateinit var webView: WebView
    private var jsBridge: VoiceboxJsBridge? = null
    private lateinit var skeletonView: VoiceboxSkeletonView
    private lateinit var offlineView: VoiceboxOfflineView
    private var closeButton: ImageButton? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var sheetContainer: FrameLayout
    private lateinit var behavior: BottomSheetBehavior<FrameLayout>
    private var dismissDispatched = false

    // MARK: Floating card state
    private lateinit var rootView: CoordinatorLayout
    private var cardSkeleton: VoiceboxCardSkeletonView? = null
    /** One-shot: onPageFinished fires more than once per load (redirects, subframes). */
    private var cardRevealed = false
    /** One-shot: the fade-out must run once however many dismiss triggers race. */
    private var cardDismissing = false

    private val isFloatingCard: Boolean
        get() = voiceboxConfig.presentationMode is VoiceboxPresentationMode.FloatingCard

    // Mirrors iOS requestMicrophonePermission() — proactively request RECORD_AUDIO before
    // loading so the native OS dialog appears when the sheet opens, not mid-recording.
    private val requestMicLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        loadUrl() // proceed regardless of grant/deny; page handles the denied state
    }

    // Pending WebView geolocation grant from onGeolocationPermissionsShowPrompt. Kept until
    // the OS location dialog returns so we can grant/deny the origin.
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    private val requestLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        VoiceboxLog.d("Location permission result: granted=$granted")
        pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
        pendingGeoCallback = null
        pendingGeoOrigin = null
    }

    companion object {
        private const val TAG = "VoiceboxKit"

        /** Dim fade on present / dismiss for the floating card. */
        private const val FLOATING_CARD_FADE_MS = 200L
        /** Gap between the status bar and the floating card's ×. */
        private const val FLOATING_CARD_CLOSE_TOP_GAP_DP = 8f
        private const val FLOATING_CARD_CLOSE_ELEVATION_DP = 4f

        internal fun show(activity: FragmentActivity, voiceboxView: VoiceboxView) {
            if (activity.supportFragmentManager.findFragmentByTag(TAG) != null) return
            VoiceboxBottomSheetFragment().also {
                it.voiceboxConfig = voiceboxView
            }.let { fragment ->
                activity.supportFragmentManager
                    .beginTransaction()
                    .add(android.R.id.content, fragment, TAG)
                    .commitAllowingStateLoss()
            }
        }

        /**
         * [upstream] with [onSubmitted] run after `onMessageSubmitted`. Every other callback is
         * passed straight through. Extracted from the fragment so the forwarding is unit-testable.
         */
        internal fun autoDismissListener(
            upstream: VoiceboxListener?,
            onSubmitted: () -> Unit,
        ): VoiceboxListener = object : VoiceboxListener {
            override fun onRecordingComplete(voiceboxView: VoiceboxView) {
                upstream?.onRecordingComplete(voiceboxView)
            }
            override fun onMessageSubmitted(voiceboxView: VoiceboxView) {
                upstream?.onMessageSubmitted(voiceboxView)
                onSubmitted()
            }
            override fun onDismiss(voiceboxView: VoiceboxView) {
                upstream?.onDismiss(voiceboxView)
            }
            override fun onFailure(voiceboxView: VoiceboxView, error: Exception) {
                upstream?.onFailure(voiceboxView, error)
            }
            // Must be forwarded explicitly: an unlisted callback falls through to the
            // interface's empty default and the host silently never hears it.
            override fun onAnonymousSessionId(voiceboxView: VoiceboxView, sessionId: String) {
                upstream?.onAnonymousSessionId(voiceboxView, sessionId)
            }
        }
    }

    // MARK: - Back press

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            dismiss()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().onBackPressedDispatcher.addCallback(this, backCallback)
    }

    // MARK: - Fragment lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireContext()

        // Root: full-screen CoordinatorLayout — transparent so the Activity shows through
        val root = CoordinatorLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        rootView = root

        val floatingCard = voiceboxConfig.presentationMode as? VoiceboxPresentationMode.FloatingCard
        if (floatingCard != null) {
            // Floating card: the dim is painted on the root, BEHIND the (transparent) WebView,
            // so it only shows where the page itself is transparent. No scrim view and no
            // tap-to-dismiss on it — the page wires its own tap-outside when there is no ×.
            root.setBackgroundColor(Color.argb((floatingCard.clampedDim * 255).toInt(), 0, 0, 0))
        } else {
            // Scrim: 60% dim over the Activity; tap to dismiss (except FullScreen)
            val scrim = View(ctx).apply {
                setBackgroundColor(Color.argb(153, 0, 0, 0))
            }
            if (voiceboxConfig.presentationMode != VoiceboxPresentationMode.FullScreen) {
                scrim.setOnClickListener { dismiss() }
            }
            root.addView(scrim, CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }

        // Sheet container: FrameLayout with BottomSheetBehavior attached via layout params.
        // The floating card has no sheet: its container simply fills the screen, edge to edge
        // (a BottomSheetBehavior would inset it below the status bar).
        sheetContainer = FrameLayout(ctx)
        val sheetParams = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply {
            if (floatingCard == null) behavior = BottomSheetBehavior<FrameLayout>()
        }
        root.addView(sheetContainer, sheetParams)

        webView = WebView(ctx)
        sheetContainer.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))

        skeletonView = VoiceboxSkeletonView(ctx)
        sheetContainer.addView(skeletonView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))

        if (floatingCard != null) {
            // Hidden until the page is ready, so the skeleton turns into the card rather than
            // both showing at once (the real card is sized differently).
            webView.alpha = 0f
            skeletonView.visibility = View.GONE
            cardSkeleton = VoiceboxCardSkeletonView(ctx).also {
                sheetContainer.addView(it, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ))
            }
        }

        offlineView = VoiceboxOfflineView(ctx, onRetry = ::loadUrl)
        offlineView.visibility = View.GONE
        sheetContainer.addView(offlineView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))

        if (voiceboxConfig.showCloseButton) {
            closeButton = buildCloseButton(ctx)
            val sizePx = ctx.dpToPx(voiceboxConfig.theme.resolvedCloseButtonSize)
            val margin = ctx.dpToPx(16f)
            sheetContainer.addView(closeButton, FrameLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, margin, margin, 0)
            })
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (isFloatingCard) {
            setupWebView()
            wrapListenerForAutoDismiss()
            applyFloatingCardInsets()
            // The dim fades in with the presentation; the card follows once the page is ready.
            rootView.alpha = 0f
            rootView.animate().alpha(1f).setDuration(FLOATING_CARD_FADE_MS).start()
            startLoadingIndicator()
            requestMicThenLoad()
            return
        }

        behavior = (sheetContainer.layoutParams as CoordinatorLayout.LayoutParams)
            .behavior as BottomSheetBehavior<FrameLayout>

        behavior.isHideable = true
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    dispatchDismiss()
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        setupWebView()
        wrapListenerForAutoDismiss()

        // Start off-screen; BottomSheetBehavior animates the sheet up from below on entry.
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        view.post { applyPresentationMode() }

        startLoadingIndicator()
        requestMicThenLoad()
    }

    override fun onDestroyView() {
        // Deny any in-flight geolocation prompt so Chromium isn't left waiting.
        pendingGeoCallback?.invoke(pendingGeoOrigin, false, false)
        pendingGeoCallback = null
        pendingGeoOrigin = null
        webView.stopLoading()
        webView.destroy()
        super.onDestroyView()
    }

    // Wraps the caller's listener so the sheet auto-dismisses after onMessageSubmitted,
    // mirroring iOS VoiceboxViewController which calls dismiss(animated:) on submit.
    // Covers both the JS bridge path (VoiceboxJsBridge) and the /sent/ URL fallback
    // (VoiceboxWebViewClient) because both route through voiceboxConfig.listener.
    private fun wrapListenerForAutoDismiss() {
        voiceboxConfig.listener = autoDismissListener(voiceboxConfig.listener, ::dismiss)
    }

    // MARK: - Dismiss

    internal fun dismiss() {
        if (isFloatingCard) {
            if (cardDismissing) return
            cardDismissing = true
            rootView.animate().alpha(0f).setDuration(FLOATING_CARD_FADE_MS)
                .withEndAction { dispatchDismiss() }
                .start()
            return
        }
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun dispatchDismiss() {
        if (dismissDispatched) return
        dismissDispatched = true
        voiceboxConfig.listener?.onDismiss(voiceboxConfig)
        parentFragmentManager.beginTransaction()
            .remove(this)
            .commitAllowingStateLoss()
    }

    // MARK: - Location permission (precise-location toggle)

    private fun requestLocationForGeolocation(
        origin: String?,
        callback: GeolocationPermissions.Callback?,
    ) {
        // Replace any in-flight request so we never leave a dangling callback.
        pendingGeoCallback?.invoke(pendingGeoOrigin, false, false)
        pendingGeoOrigin = origin
        pendingGeoCallback = callback
        requestLocationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    // MARK: - Mic permission

    private fun requestMicThenLoad() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // The SDK only proactively pops the OS mic dialog when the caller opted into
        // auto-grant and the app doesn't already hold RECORD_AUDIO. Otherwise the host
        // app owns the permission lifecycle: we load directly, and the WebView's mic
        // request is granted only if the permission is already held (see VoiceboxChromeClient).
        val shouldRequest = VoiceboxMicPolicy.shouldProactivelyRequest(
            appHoldsRecordAudio = granted,
            autoGrant = voiceboxConfig.effectiveAutoGrantMicPermission,
        )
        if (shouldRequest) {
            requestMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            loadUrl()
        }
    }

    // MARK: - WebView setup

    @android.annotation.SuppressLint("SetJavaScriptEnabled") // JS required for Voicebox web app
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            setSupportMultipleWindows(false)
            // Required for navigator.geolocation (precise-location toggle after send).
            setGeolocationEnabled(true)
        }

        // JS bridge — WEBKIT_POLYFILL maps window.webkit.messageHandlers
        // to window.VoiceboxBridge.onMessage() so the web app needs no platform branching.
        val bridge = VoiceboxJsBridge(
            voiceboxView = voiceboxConfig,
            onBgColor = ::handleBgColor,
            onContentHeight = ::handleContentHeight,
            mainHandler = mainHandler,
            onDismissRequest = ::dismiss,
        )
        jsBridge = bridge
        webView.addJavascriptInterface(bridge, "VoiceboxBridge")

        webView.webViewClient = VoiceboxWebViewClient(
            voiceboxView = voiceboxConfig,
            onPageFinished = ::onWebPageFinished,
            onError = ::onWebError,
        )

        webView.webChromeClient = VoiceboxChromeClient(
            context = requireContext(),
            onGeolocationPermissionNeeded = ::requestLocationForGeolocation,
        )

        // Inject the webkit polyfill before any page scripts run, then the event
        // observer (order matters: the observer relies on the polyfill's
        // voiceboxEvent handler). The observer bridges the recorder's Save/Send
        // clicks and postMessage events to onRecordingComplete/onMessageSubmitted.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                VoiceboxJsBridge.WEBKIT_POLYFILL,
                setOf("*"),
            )
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                VoiceboxJsBridge.EVENT_OBSERVER,
                setOf("*"),
            )
            // Reports the anonymous session id. After the polyfill, which defines its
            // voiceboxSession handler.
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                VoiceboxJsBridge.SESSION_CAPTURE,
                setOf("*"),
            )
            // Floating card: restyle the page into a centred card before it first paints.
            if (isFloatingCard) {
                WebViewCompat.addDocumentStartJavaScript(webView, floatingCardInjectionJs(), setOf("*"))
            }
        }
        // Without DOCUMENT_START_SCRIPT none of the scripts above run; the session id then
        // still arrives through the page-finished read (readSessionIdFromStorage).

        // Pre-paint background to avoid white flash before page bg is detected. The floating
        // card stays transparent instead, so the dim shows wherever the page does.
        if (isFloatingCard) {
            webView.setBackgroundColor(Color.TRANSPARENT)
        } else {
            voiceboxConfig.theme.backgroundColor?.let { webView.setBackgroundColor(it) }
        }
    }

    // MARK: - Floating card

    private fun floatingCardInjectionJs(): String =
        VoiceboxFloatingCard.injectionJs(
            tapOutsideDismiss = VoiceboxFloatingCard.usesTapOutsideDismiss(voiceboxConfig.showCloseButton),
        )

    /**
     * Edge to edge: the page fills the screen under the status bar, and the × sits just below
     * it. The keyboard shrinks the WebView from the bottom instead of scrolling the page, so
     * the page's `100vh` recomputes and the card re-centres above the keyboard — rather than
     * the whole page sliding up and exposing the background behind the card (iOS #246).
     */
    private fun applyFloatingCardInsets() {
        val closeTopGap = requireContext().dpToPx(FLOATING_CARD_CLOSE_TOP_GAP_DP)
        ViewCompat.setOnApplyWindowInsetsListener(sheetContainer) { _, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            (webView.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                if (lp.bottomMargin != imeBottom) {
                    lp.bottomMargin = imeBottom
                    webView.layoutParams = lp
                }
            }
            closeButton?.let { button ->
                (button.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                    lp.topMargin = statusTop + closeTopGap
                    button.layoutParams = lp
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(sheetContainer)
    }

    /**
     * Skeleton → card: (re)apply the page styling, start the card's own lift-in, then fade the
     * WebView in — settling from a slight scale-up when [VoiceboxEntranceAnimation.BackgroundReveal]
     * is on. Both animations are skipped when the device has animations turned off.
     */
    private fun revealFloatingCard() {
        if (cardRevealed) return
        cardRevealed = true
        val animations = voiceboxConfig.entranceAnimation
        val animate = areAnimationsEnabled()

        webView.evaluateJavascript(floatingCardInjectionJs(), null)
        webView.evaluateJavascript(
            VoiceboxFloatingCard.entranceJs(
                liftCard = animate && VoiceboxEntranceAnimation.CardLiftIn in animations,
            ),
            null,
        )
        cardSkeleton?.stopAnimating()

        if (animate && VoiceboxEntranceAnimation.BackgroundReveal in animations) {
            webView.scaleX = VoiceboxFloatingCard.BACKGROUND_REVEAL_START_SCALE
            webView.scaleY = VoiceboxFloatingCard.BACKGROUND_REVEAL_START_SCALE
            webView.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(VoiceboxFloatingCard.BACKGROUND_REVEAL_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            webView.alpha = 1f
        }
    }

    private fun areAnimationsEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(requireContext().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }

    private fun startLoadingIndicator() {
        if (isFloatingCard) {
            cardSkeleton?.startAnimating()
        } else {
            skeletonView.visibility = View.VISIBLE
            skeletonView.startAnimating()
        }
    }

    // MARK: - Presentation mode

    private fun applyPresentationMode() {
        val screenH = resources.displayMetrics.heightPixels

        // Container is always MATCH_PARENT so the WebView has a fully defined height to render;
        // BottomSheetBehavior controls how much is revealed via peekHeight / state.
        sheetContainer.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        sheetContainer.requestLayout()

        when (val mode = voiceboxConfig.presentationMode) {
            VoiceboxPresentationMode.FullScreen -> {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = false
                behavior.peekHeight = screenH
            }
            VoiceboxPresentationMode.BottomSheet -> {
                behavior.halfExpandedRatio = 0.6f
                behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                behavior.skipCollapsed = true
            }
            VoiceboxPresentationMode.Sheet -> {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = false
            }
            VoiceboxPresentationMode.FitContent -> {
                // Start at 300 dp; handleContentHeight() resizes once JS reports content height
                val initialPx = requireContext().dpToPx(300f)
                behavior.peekHeight = initialPx
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            is VoiceboxPresentationMode.Custom -> {
                val px = requireContext().dpToPx(mode.height)
                behavior.peekHeight = px
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            is VoiceboxPresentationMode.CustomFraction -> {
                behavior.halfExpandedRatio = mode.clamped
                behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                behavior.skipCollapsed = true
            }
            // No sheet to size — onViewCreated returns before this is ever scheduled.
            is VoiceboxPresentationMode.FloatingCard -> return
        }

        applySheetCornerRadius()
    }

    // Apply theme.cornerRadius to the sheet's top two corners via ViewOutlineProvider.
    // Only the top corners are rounded (bottom extends past the view bounds) to match
    // iOS's sheet presentationCornerRadius. FullScreen is edge-to-edge with no rounding.
    private fun applySheetCornerRadius() {
        if (voiceboxConfig.presentationMode == VoiceboxPresentationMode.FullScreen) {
            sheetContainer.clipToOutline = false
            sheetContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
            return
        }
        val radiusPx = requireContext().dpToPx(voiceboxConfig.theme.resolvedCornerRadius).toFloat()
        sheetContainer.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // Extend past the bottom so only the top corners are clipped.
                outline.setRoundRect(0, 0, view.width, (view.height + radiusPx).toInt(), radiusPx)
            }
        }
        sheetContainer.clipToOutline = true
        sheetContainer.invalidateOutline()
    }

    // MARK: - URL loading

    private fun loadUrl() {
        offlineView.visibility = View.GONE
        startLoadingIndicator()
        webView.loadUrl(voiceboxConfig.buildUrl().toString())
    }

    // MARK: - WebView callbacks

    private fun onWebPageFinished() {
        offlineView.visibility = View.GONE
        if (isFloatingCard) revealFloatingCard() else skeletonView.stopAnimating()

        readSessionIdFromStorage()

        if (voiceboxConfig.presentationMode == VoiceboxPresentationMode.FitContent) {
            // Measure the document scroll height (CSS px ≈ dp when viewport=device-width)
            // and resize the sheet immediately without depending on a JS message from the page.
            webView.evaluateJavascript(
                "(function(){return Math.max(" +
                    "document.body ? document.body.scrollHeight : 0," +
                    "document.documentElement.scrollHeight" +
                    ");})()"
            ) { result ->
                val heightDp = result?.trim()?.toFloatOrNull() ?: return@evaluateJavascript
                if (heightDp > 0f) mainHandler.post { handleContentHeight(heightDp) }
            }
        }
    }

    // The second capture path (see VoiceboxJsBridge.SESSION_READ_SNIPPET): reads storage
    // directly, so it works without document-start scripts and is gated only by the
    // bridge's native "already delivered" check.
    private fun readSessionIdFromStorage() {
        webView.evaluateJavascript(VoiceboxJsBridge.SESSION_READ_SNIPPET) { result ->
            val sessionId = VoiceboxJsBridge.parseEvaluatedString(result) ?: return@evaluateJavascript
            mainHandler.post { jsBridge?.reportSessionId(sessionId) }
        }
    }

    private fun onWebError() {
        skeletonView.stopAnimating()
        skeletonView.visibility = View.GONE
        cardSkeleton?.stopAnimating()
        offlineView.visibility = View.VISIBLE
        voiceboxConfig.listener?.onFailure(voiceboxConfig, Exception("Voicebox failed to load"))
    }

    // MARK: - JS bridge callbacks

    private fun handleBgColor(cssColor: String) {
        val color = parseCssColor(cssColor) ?: return
        webView.setBackgroundColor(color)
    }

    private fun handleContentHeight(heightDp: Float) {
        if (voiceboxConfig.presentationMode != VoiceboxPresentationMode.FitContent) return
        val maxPx = resources.displayMetrics.heightPixels
        val heightPx = requireContext().dpToPx(heightDp).coerceAtMost(maxPx)
        behavior.peekHeight = heightPx
        sheetContainer.requestLayout()
    }

    // MARK: - Close button

    private fun buildCloseButton(ctx: Context): ImageButton {
        val theme = voiceboxConfig.theme
        return ImageButton(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = ctx.dpToPx(6f)
            setPadding(pad, pad, pad, pad)
            contentDescription = "Close"

            setImageResource(theme.closeButtonIconRes ?: R.drawable.voicebox_ic_close)
            theme.closeButtonIconColor?.let { setColorFilter(it) }

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(theme.closeButtonBackgroundColor ?: Color.TRANSPARENT)
            }

            // Floating card over arbitrary voicebox backgrounds — including light images where
            // a plain white disc blends in — gets a soft shadow so it reads on ANY backdrop.
            if (isFloatingCard && theme.closeButtonBackgroundColor != null) {
                outlineProvider = ViewOutlineProvider.BACKGROUND
                elevation = ctx.dpToPx(FLOATING_CARD_CLOSE_ELEVATION_DP).toFloat()
            }

            setOnClickListener { dismiss() }
        }
    }

    // MARK: - CSS color parser

    private fun parseCssColor(css: String): Int? {
        val rgba = Regex("""rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)""")
        val rgb  = Regex("""rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""")

        rgba.find(css.trim())?.let {
            val (r, g, b, a) = it.destructured
            val alpha = (a.toFloat() * 255).toInt().coerceIn(0, 255)
            return Color.argb(alpha, r.toInt(), g.toInt(), b.toInt())
        }
        rgb.find(css.trim())?.let {
            val (r, g, b) = it.destructured
            return Color.argb(255, r.toInt(), g.toInt(), b.toInt())
        }
        return null
    }
}
