package com.voicebox.voiceboxkit

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Loads a host-supplied session URL in an off-screen WebView so the session cookie it sets
 * lands in the app's shared [CookieManager] — the same jar the recorder's WebView reads.
 *
 * A separate WebView, not the recorder's: the recorder only ever navigates to its own handle.
 * One load at a time; a newer call cancels the one in flight (which reports `false`).
 *
 * Main thread only. Mirrors iOS `SessionPrimer`.
 */
internal class VoiceboxSessionPrimer private constructor() {

    companion object {
        val shared = VoiceboxSessionPrimer()

        /** Same ceiling as iOS. A handoff that has not landed by now is not going to. */
        internal const val TIMEOUT_MS = 20_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var webView: WebView? = null
    private var onResult: ((Boolean) -> Unit)? = null
    private var timeout: Runnable? = null

    @SuppressLint("SetJavaScriptEnabled") // the handoff page may redirect via script
    fun load(context: Context, url: String, onResult: (Boolean) -> Unit) {
        finish(false)
        this.onResult = onResult

        val webView = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }
        this.webView = webView
        CookieManager.getInstance().setAcceptCookie(true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, finishedUrl: String) {
                if (view !== this@VoiceboxSessionPrimer.webView) return
                // The session cookie now lives on this host, so clearSession must know to clear it.
                VoiceboxSessionStorage.rememberHost(finishedUrl)
                finish(true)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (!request.isForMainFrame || view !== this@VoiceboxSessionPrimer.webView) return
                finish(false)
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (!request.isForMainFrame || view !== this@VoiceboxSessionPrimer.webView) return
                finish(false)
            }
        }

        timeout = Runnable { finish(false) }.also { mainHandler.postDelayed(it, TIMEOUT_MS) }

        webView.loadUrl(url)
    }

    /**
     * Stop a session load still in flight: the WebView is stopped and destroyed, and its callback
     * reports `false`. Called by [VoiceboxKit.clearSession] BEFORE it clears — otherwise a load that
     * finishes after the clear would set the previous account's cookie again.
     */
    fun cancel() = finish(false)

    private fun finish(success: Boolean) {
        timeout?.let { mainHandler.removeCallbacks(it) }
        timeout = null

        webView?.let {
            it.stopLoading()
            it.webViewClient = WebViewClient()
            it.destroy()
        }
        webView = null

        // Cookies are held in memory and written to disk lazily; a process death right after
        // signing in would otherwise lose the session that was just established.
        if (success) CookieManager.getInstance().flush()

        val callback = onResult ?: return
        onResult = null
        callback(success)
    }
}
