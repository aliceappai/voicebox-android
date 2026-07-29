package com.voicebox.voiceboxkit

import android.Manifest
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
    private lateinit var skeletonView: VoiceboxSkeletonView
    private lateinit var offlineView: VoiceboxOfflineView
    private var closeButton: ImageButton? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var sheetContainer: FrameLayout
    private lateinit var behavior: BottomSheetBehavior<FrameLayout>
    private var dismissDispatched = false

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

        // Sheet container: FrameLayout with BottomSheetBehavior attached via layout params
        sheetContainer = FrameLayout(ctx)
        val sheetParams = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply {
            behavior = BottomSheetBehavior<FrameLayout>()
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

        skeletonView.startAnimating()
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
        val upstream = voiceboxConfig.listener
        voiceboxConfig.listener = object : VoiceboxListener {
            override fun onRecordingComplete(voiceboxView: VoiceboxView) {
                upstream?.onRecordingComplete(voiceboxView)
            }
            override fun onMessageSubmitted(voiceboxView: VoiceboxView) {
                upstream?.onMessageSubmitted(voiceboxView)
                dismiss()
            }
            override fun onDismiss(voiceboxView: VoiceboxView) {
                upstream?.onDismiss(voiceboxView)
            }
            override fun onFailure(voiceboxView: VoiceboxView, error: Exception) {
                upstream?.onFailure(voiceboxView, error)
            }
        }
    }

    // MARK: - Dismiss

    internal fun dismiss() {
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
        webView.addJavascriptInterface(
            VoiceboxJsBridge(
                voiceboxView = voiceboxConfig,
                onBgColor = ::handleBgColor,
                onContentHeight = ::handleContentHeight,
                mainHandler = mainHandler,
            ),
            "VoiceboxBridge",
        )

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
        }

        // Pre-paint background to avoid white flash before page bg is detected
        voiceboxConfig.theme.backgroundColor?.let { webView.setBackgroundColor(it) }
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
        skeletonView.visibility = View.VISIBLE
        skeletonView.startAnimating()
        webView.loadUrl(voiceboxConfig.buildUrl().toString())
    }

    // MARK: - WebView callbacks

    private fun onWebPageFinished() {
        skeletonView.stopAnimating()
        offlineView.visibility = View.GONE

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

    private fun onWebError() {
        skeletonView.stopAnimating()
        skeletonView.visibility = View.GONE
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
