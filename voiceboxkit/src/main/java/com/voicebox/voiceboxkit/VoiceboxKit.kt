package com.voicebox.voiceboxkit

import android.content.Context
import android.os.Handler
import android.os.Looper

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

    // MARK: - Session

    /**
     * Sign the recorder in with a URL the host obtained from its own backend, so a natively
     * signed-in user reaches the recorder signed in too and what they record is attributed
     * to their account when it is submitted.
     *
     * The URL is loaded in an off-screen WebView; the session cookie it sets lands in the
     * app's shared cookie jar, which the recorder's WebView reads.
     *
     * **The SDK does not mint this URL, calls no Voicebox API and never handles
     * credentials** — it navigates to what it is given and reports whether it arrived.
     * Call it once per session, not per presentation. Treat failure as unimportant and never
     * block the recorder on it: an unattributed recording is still captured.
     *
     * Requires [init]. Safe to call from any thread; [onResult] runs on the main thread.
     * A newer call cancels one still in flight, which reports `false`.
     *
     * Mirrors iOS `VoiceboxKit.establishSession(from:completion:)`.
     */
    fun establishSession(url: String, onResult: ((Boolean) -> Unit)? = null) {
        runOnMain {
            val context = applicationContext
            if (context == null) {
                onResult?.invoke(false)
                return@runOnMain
            }
            VoiceboxSessionPrimer.shared.load(context, url) { success -> onResult?.invoke(success) }
        }
    }

    /**
     * Forget who was recording on this device: the recorder's session cookies AND its
     * anonymous session id, for [baseUrl]'s host only. Call on sign-out.
     *
     * Both halves together, because either one left behind describes a state the other no
     * longer does. Other sites' cookies and storage are never touched.
     *
     * Requires [init]. Safe to call from any thread; [onDone] runs on the main thread.
     *
     * Mirrors iOS `VoiceboxKit.clearSession(completion:)`.
     */
    fun clearSession(onDone: (() -> Unit)? = null) {
        runOnMain {
            if (applicationContext == null) {
                onDone?.invoke()
                return@runOnMain
            }
            VoiceboxSessionStorage.clearFor(baseUrl) { onDone?.invoke() }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else Handler(Looper.getMainLooper()).post(block)
    }
}
