package com.voicebox.voiceboxkit

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager

/**
 * Top-level namespace for VoiceboxKit configuration and preloading.
 *
 * Call [init] once at app start (e.g. in `Application.onCreate`) before
 * any `preload` or presentation call.
 *
 * Mirrors the iOS `VoiceboxKit` enum.
 */
object VoiceboxKit {

    /** SDK version string. */
    const val VERSION = "1.1.0"

    /** Base URL for Voicebox handles. Defaults to production (`https://vbx.to`). */
    var baseUrl: String = "https://vbx.to"

    /**
     * When `true`, the SDK emits verbose diagnostic logs (navigation, page
     * lifecycle, permission grants, and WebView JS console output) under the
     * `VoiceboxKit` / `VoiceboxKit/JS` logcat tags. Default `false` — keep it
     * off in production to avoid noisy logs and leaking page internals.
     */
    var debugLogging: Boolean = false

    /**
     * When `true`, the SDK auto-grants microphone permission to the WebView
     * if the host app already holds the `RECORD_AUDIO` permission. Default `false`.
     *
     * Individual presentations can override this value.
     */
    var autoGrantMicPermission: Boolean = false

    /**
     * When `true`, the SDK automatically collects non-PII app and device
     * context (packageName, appVersion, osVersion, deviceModel, locale, etc.)
     * and includes it in the Voicebox URL. App-provided params always win.
     * Default `true`.
     */
    var autoCollectAppContext: Boolean = true

    /**
     * When `true`, `utm_source=voiceboxkit` and `utm_medium=android_sdk` are
     * appended to every Voicebox URL. Set to `false` during development/staging
     * to keep URLs clean. Default `true`.
     */
    var appendUtmParams: Boolean = true

    /**
     * Application context stored by [init]. Used internally for package-level
     * auto-collection (app name, version, package ID). Safe to hold: this is
     * always the application context, never an activity or view context.
     */
    internal var applicationContext: Context? = null

    /**
     * Initialise the SDK. Call once from `Application.onCreate()` before
     * any [preload] or presentation call.
     *
     * ```kotlin
     * class MyApp : Application() {
     *     override fun onCreate() {
     *         super.onCreate()
     *         VoiceboxKit.init(this)
     *         VoiceboxKit.preload(handle = "my-handle")
     *     }
     * }
     * ```
     */
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    /**
     * Prefetch and cache the Voicebox page for the given handle.
     *
     * Call this once during app startup so the WebView loads instantly
     * when the user triggers a presentation.
     *
     * Requires [init] to be called first.
     *
     * @param handle The Voicebox handle to preload.
     */
    fun preload(handle: String) {
        VoiceboxCache.shared.preload(handle)
    }

    // MARK: - Recorder session

    /**
     * Sign the recorder's WebView in, using a URL your app obtained from its own backend.
     *
     * The recorder runs on a different host from your app's API, with its own cookie, so a
     * natively signed-in user still reaches it signed OUT — and everything they record is
     * anonymous. Loading a session URL here fixes that: messages are then attributed as
     * they are submitted.
     *
     * **VoiceboxKit does not mint this URL, calls no Voicebox API, and never handles
     * credentials.** It navigates to what you give it, in the cookie store the recorder
     * uses, and reports whether it arrived. Most integrations never need this — it is for a
     * host whose users have Voicebox accounts.
     *
     * Call it once per session, not per recorder open: Android's [CookieManager] is
     * process-global, so one call covers every voicebox opened afterwards. Sensible moments
     * are app start when already signed in, straight after your own sign-in, and a return to
     * the foreground when the session may have lapsed.
     *
     * **Treat failure as unimportant.** Do not block opening the recorder on it and do not
     * show an error: a recording made without a session is still captured, just anonymously,
     * and can be claimed afterwards. Blocking trades a working recorder for a spinner.
     *
     * Requires [init] to be called first.
     *
     * @param url The session URL from your backend.
     * @param onResult Called on the main thread with whether the load succeeded.
     */
    fun establishSession(url: String, onResult: ((Boolean) -> Unit)? = null) {
        val context = applicationContext
        if (context == null) {
            VoiceboxLog.d("establishSession skipped — VoiceboxKit.init(context) has not run")
            onResult?.invoke(false)
            return
        }

        runOnMain {
            VoiceboxLog.d("establishing session via ${Uri.parse(url).host ?: "?"}")
            VoiceboxSessionPrimer.shared.load(context, url) { success ->
                VoiceboxLog.d("establish ${if (success) "succeeded" else "failed"}")
                // AFTER the load, so anything re-fetched carries the new cookie.
                VoiceboxCache.shared.rewarmAll()
                onResult?.invoke(success)
            }
        }
    }

    /**
     * Forget who was recording on this device. Call it on sign-out.
     *
     * Clears both halves of the recorder's identity, because a host that did one and forgot
     * the other would leave the device in a state neither of them describes:
     *
     * - the **session cookies** for [baseUrl]'s host, so the recorder stops being signed in
     *   as the account that just left;
     * - the **anonymous session id** in the recorder's own web storage, which outlives any
     *   session and would otherwise keep accumulating every recording made on this device
     *   under one identity.
     *
     * Clearing the id is **not** on its own a defence against the wrong account claiming
     * those recordings — that protection is server-side, where a claim only ever touches
     * messages nobody owns yet. What it does is bound how much history one claim can cover.
     *
     * Caches are left alone: this forgets who was recording, not everything the recorder
     * ever loaded.
     *
     * @param onResult Called on the main thread once the data has been removed.
     */
    fun clearSession(onResult: (() -> Unit)? = null) {
        // Guarded even though clearing itself needs no context: without [init] the cache
        // cannot re-warm afterwards, so a "cleared" that only did half the job would be
        // more confusing than a no-op.
        if (applicationContext == null) {
            VoiceboxLog.d("clearSession skipped — VoiceboxKit.init(context) has not run")
            onResult?.invoke()
            return
        }

        runOnMain {
            VoiceboxWebStorage.clearFor(baseUrl) {
                VoiceboxCache.shared.rewarmAll()
                onResult?.invoke()
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else Handler(Looper.getMainLooper()).post(block)
    }
}
