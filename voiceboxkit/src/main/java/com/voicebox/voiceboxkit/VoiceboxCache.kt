package com.voicebox.voiceboxkit

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Manages prefetching, ETag-based cache validation, and WebView warming for
 * Voicebox handles.
 *
 * **How it works:**
 * 1. [preload] fires a background GET to the Voicebox URL and stores the ETag
 *    + Last-Modified headers in [SharedPreferences].
 * 2. A transient off-screen [WebView] loads the same URL on the main thread.
 *    Because the WebView disk cache is app-global, the *Fragment's* WebView
 *    gets a cache hit the next time it loads the same URL — instant load.
 *    The warm WebView is destroyed as soon as the page finishes loading; it is
 *    never retained (iOS keeps a pool, but Android can't reuse it here because
 *    the warm WebView has JS disabled to avoid NotReadableError on getUserMedia).
 * 3. [validateCache] does a background HEAD with `If-None-Match` to check
 *    staleness without downloading the full page.
 */
internal class VoiceboxCache private constructor() {

    companion object {
        val shared = VoiceboxCache()
        private const val PREFS_NAME = "voicebox_cache"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Handles currently being warmed on the main thread. Prevents duplicate concurrent warms.
    private val inFlight = mutableSetOf<String>()

    // Every handle a host has asked us to warm, so [rewarmAll] can re-fetch them after the
    // recorder's session changes. Kept rather than asking the host to replay its own
    // preloads: the warm set is an SDK internal, and a contract that says "call preload
    // again after signing in" is silently wrong the first time somebody forgets.
    private val warmedHandles = mutableSetOf<String>()

    private val prefs
        get() = VoiceboxKit.applicationContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // MARK: - Public API

    /**
     * Pre-fetches the Voicebox page for [handle] and warms the WebView disk cache.
     *
     * - Runs a background GET; stores ETag + Last-Modified.
     * - Creates a transient off-screen [WebView] on the main thread that loads the URL,
     *   populating the shared disk cache for instant first-open, then destroys itself.
     *
     * No-op if [VoiceboxKit.init] has not been called yet.
     */
    fun preload(handle: String) {
        val context = VoiceboxKit.applicationContext ?: return
        synchronized(warmedHandles) { warmedHandles.add(handle) }
        executor.execute {
            fetchAndStoreEtag(handle)
            mainHandler.post { warmWebView(context, handle) }
        }
    }

    /**
     * Returns `true` if [preload] has completed successfully at least once
     * for this handle (ETag stored in SharedPreferences).
     */
    fun hasCachedContent(handle: String): Boolean =
        prefs?.getBoolean("cached_$handle", false) ?: false

    /**
     * Validates the cached ETag for [handle] via a background HEAD request.
     *
     * Calls [onResult] on the **main thread** with:
     * - `true` → HTTP 304 Not Modified (cache is still fresh)
     * - `false` → content has changed, or no cached ETag, or network error
     *
     * Call this on app foreground if you want to refresh stale content proactively.
     */
    fun validateCache(handle: String, onResult: (isValid: Boolean) -> Unit) {
        val storedEtag = prefs?.getString("etag_$handle", null)
        if (storedEtag == null) {
            onResult(false)
            return
        }
        executor.execute {
            val valid = try {
                val url = VoiceboxUrlBuilder.build(handle, emptyMap()).toString()
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.setRequestProperty("If-None-Match", storedEtag)
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                conn.connect()
                val fresh = conn.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED
                conn.disconnect()
                fresh
            } catch (_: Exception) {
                false
            }
            mainHandler.post { onResult(valid) }
        }
    }

    // MARK: - Private

    private fun fetchAndStoreEtag(handle: String) {
        try {
            val url = VoiceboxUrlBuilder.build(handle, emptyMap()).toString()
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "text/html")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.connect()
            val etag = conn.getHeaderField("ETag")
            val lastModified = conn.getHeaderField("Last-Modified")
            conn.disconnect()

            prefs?.edit()?.run {
                etag?.let { putString("etag_$handle", it) }
                lastModified?.let { putString("lastModified_$handle", it) }
                putBoolean("cached_$handle", true)
                apply()
            }
        } catch (_: Exception) {
            // Preload failure is silent and non-fatal — the sheet will load normally
        }
    }

    /**
     * Re-warm every handle a host has preloaded.
     *
     * Called by both session methods on [VoiceboxKit]. A warm here only populates the
     * shared HTTP disk cache (the warm WebView runs with JS disabled and is destroyed on
     * load), and the recorder revalidates rather than being served blind from cache — so
     * unlike iOS there is no live page holding stale state to throw away. What this buys is
     * that the next fetch happens WITH the new cookie, so the first real open is a hit
     * rather than a full load.
     */
    fun rewarmAll() {
        val handles = synchronized(warmedHandles) { warmedHandles.toList() }
        handles.forEach { preload(it) }
    }

    private fun warmWebView(context: Context, handle: String) {
        if (!inFlight.add(handle)) return  // already warming this handle
        val webView = WebView(context.applicationContext).apply {
            // JS intentionally disabled: we only want to populate the disk cache with
            // HTML/CSS/JS assets. Enabling JS would run RecorderController which creates
            // an AudioContext, holding the audio device open and causing NotReadableError
            // when the sheet's WebView tries to start getUserMedia.
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // Disk cache is now populated. Destroy immediately — the warm WebView is
                // never reused (JS is disabled, so it can't run the recorder anyway).
                inFlight.remove(handle)
                view.stopLoading()
                view.destroy()
            }
        }
        // Loading into an off-screen WebView populates the shared disk cache,
        // so the Fragment's WebView gets a cache hit on first presentation.
        webView.loadUrl(VoiceboxUrlBuilder.build(handle, emptyMap()).toString())
    }
}
