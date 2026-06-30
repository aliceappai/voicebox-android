package com.voicebox.voiceboxkit

import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

internal class VoiceboxWebViewClient(
    private val voiceboxView: VoiceboxView,
    private val onPageFinished: () -> Unit,
    private val onError: () -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        val allowed = isAllowedUri(uri)
        VoiceboxLog.d("Navigation: $uri  allowed=$allowed")

        // /sent/ URL path is a fallback signal for messageSubmitted (mirrors iOS nav delegate)
        if (uri.path?.contains("/sent/") == true) {
            VoiceboxLog.d("Message submitted via /sent/ path")
            voiceboxView.listener?.onMessageSubmitted(voiceboxView)
        }

        // Allow navigation only to trusted hosts; block everything else
        return !allowed
    }

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        VoiceboxLog.d("Page started: $url")
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        VoiceboxLog.d("Page finished: $url")
        onPageFinished()
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            Log.e("VoiceboxKit", "WebView error [${error.errorCode}]: ${error.description}  url=${request.url}")
            onError()
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame) {
            Log.e("VoiceboxKit", "HTTP error ${errorResponse.statusCode}  url=${request.url}")
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // Never proceed past SSL errors — cancel the load so an invalid/spoofed
        // certificate can't serve content into the WebView. Voicebox hosts
        // (vbx.to, voicebox.ai, vbxstaging.com) all present valid CA-signed certs.
        Log.e("VoiceboxKit", "SSL error ${error.primaryError}  url=${error.url} — load cancelled")
        handler.cancel()
        onError()
    }

    private fun isAllowedUri(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        if (scheme in listOf("about", "data", "blob")) return true

        val host = uri.host ?: return false
        if (host.endsWith("vbx.to")) return true
        if (host.endsWith("voicebox.ai")) return true

        // Allow the configured baseUrl host and its subdomains
        val baseHost = Uri.parse(VoiceboxKit.baseUrl).host ?: ""
        if (baseHost.isNotEmpty() && (host == baseHost || host.endsWith(".$baseHost"))) {
            return true
        }
        return false
    }
}
