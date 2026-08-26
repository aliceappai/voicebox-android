package com.voicebox.voiceboxkit

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Loads a host-supplied session URL in an off-screen WebView, so the cookie it sets lands
 * in the store every recorder WebView reads from.
 *
 * Why a navigation at all: only the server can mint a session cookie, via `Set-Cookie` on a
 * real response. There is nothing to inject — the value does not exist until something asks
 * for it — so the cookie has to be *earned* by loading the URL.
 *
 * Android's [CookieManager] is process-global, so one prime covers every WebView in the app,
 * including the recorder sheet's. That is also why [flush] is called: cookies are held in
 * memory and only written to disk periodically, so a process death shortly after signing in
 * would otherwise lose the session.
 */
internal class VoiceboxSessionPrimer private constructor() {

    companion object {
        val shared = VoiceboxSessionPrimer()

        /**
         * The maximum a prime may take before it is abandoned.
         *
         * A wall clock rather than trusting the navigation to end, because it is not always
         * the network that stalls — a redirect chain that never terminates finishes no page
         * and raises no error. The value matters little: priming is best-effort and hosts
         * are told not to block on it, so a slow one costs nothing but a late `false`.
         */
        private const val TIMEOUT_MS = 20_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var webView: WebView? = null
    private var onResult: ((Boolean) -> Unit)? = null
    private var timeout: Runnable? = null

    /**
     * @param onResult Always called, exactly once, on the main thread.
     */
    fun load(context: Context, url: String, onResult: (Boolean) -> Unit) {
        // A second prime while one is in flight abandons the first. The token in that URL is
        // single-use and already spent or spending, so there is nothing to preserve — and
        // letting them race would leave two WebViews writing the same cookie jar.
        finish(false)

        this.onResult = onResult

        val webView = WebView(context.applicationContext).apply {
            // No JavaScript: this loads a terminal page whose only job is to carry a
            // Set-Cookie header. Leaving JS off keeps the prime from running any page code,
            // and matches how the cache warm WebView is deliberately kept inert.
            settings.javaScriptEnabled = false
        }
        this.webView = webView

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, finishedUrl: String) {
                // The cookie is set by the response's Set-Cookie header, which the WebView
                // has already stored by the time the page it belongs to finishes — including
                // across the redirect the session URL performs. Arriving here is the success
                // signal; the page's content is never inspected, so a host can point this
                // anywhere.
                CookieManager.getInstance().flush()
                finish(true)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (!request.isForMainFrame) return
                VoiceboxLog.d("session prime failed: ${error.errorCode} ${error.description}")
                finish(false)
            }
        }

        timeout = Runnable {
            VoiceboxLog.d("session prime timed out after ${TIMEOUT_MS}ms")
            finish(false)
        }.also { mainHandler.postDelayed(it, TIMEOUT_MS) }

        webView.loadUrl(url)
    }

    /**
     * Idempotent by construction: the callback is taken before it is invoked, so a timeout
     * firing alongside a page callback cannot report twice.
     */
    private fun finish(success: Boolean) {
        timeout?.let { mainHandler.removeCallbacks(it) }
        timeout = null

        webView?.let {
            it.stopLoading()
            it.webViewClient = WebViewClient()
            it.destroy()
        }
        webView = null

        val callback = onResult ?: return
        onResult = null
        callback(success)
    }
}
