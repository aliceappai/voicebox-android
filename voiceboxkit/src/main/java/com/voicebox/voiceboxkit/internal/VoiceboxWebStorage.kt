package com.voicebox.voiceboxkit

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage

/**
 * Removes the recorder's identity from this app's WebView storage: its session cookies and
 * the anonymous session id.
 *
 * ⚠️ **Scoped to the recorder's host on purpose.** Android's cookie and storage APIs are
 * process-global — one jar shared with every WebView the host app has, including ones
 * showing sites that have nothing to do with Voicebox. `CookieManager.removeAllCookies()`
 * would take all of them. An SDK clearing a host app's unrelated logins on its own sign-out
 * is not a defensible thing to ship, so this expires the recorder's cookies individually
 * instead. There is no per-domain removal API; setting a cookie with `Max-Age=0` is how it
 * is done.
 */
internal object VoiceboxWebStorage {

    /**
     * @param onDone Called on the main thread once removal has been requested.
     */
    fun clearFor(baseUrl: String, onDone: () -> Unit) {
        val host = Uri.parse(baseUrl).host
        if (host.isNullOrEmpty()) {
            VoiceboxLog.d("clear skipped — baseUrl has no host ($baseUrl)")
            onDone()
            return
        }

        clearCookies(baseUrl, host)
        CookieManager.getInstance().flush()

        clearAnonymousId(host) {
            VoiceboxLog.d("cleared recorder session + storage for $host")
            Handler(Looper.getMainLooper()).post(onDone)
        }
    }

    /**
     * Expires every cookie currently set for the recorder's host, and for the parent domain
     * they may have been scoped to.
     *
     * Reading `getCookie` first and expiring what is actually there — rather than guessing
     * names — because the session cookie's name is vbx-web's to choose, and a hardcoded one
     * here would silently stop working the day it changed.
     */
    private fun clearCookies(baseUrl: String, host: String) {
        val manager = CookieManager.getInstance()
        val existing = manager.getCookie(baseUrl) ?: return

        val names = existing.split(";")
            .mapNotNull { it.substringBefore("=").trim().takeIf(String::isNotEmpty) }

        // Both scopes: a cookie set host-only ("vbx.to") and one set for the registrable
        // domain (".vbx.to") are different cookies to the store, and expiring one does not
        // touch the other.
        val domains = listOf(host, ".$host")

        for (name in names) {
            for (domain in domains) {
                manager.setCookie(baseUrl, "$name=; Max-Age=0; Path=/; Domain=$domain")
            }
            manager.setCookie(baseUrl, "$name=; Max-Age=0; Path=/")
        }
    }

    /**
     * Drops the anonymous session id by deleting the recorder origin's web storage.
     *
     * [WebStorage.deleteOrigin] is the only correct way to reach it from outside a loaded
     * page. Running `localStorage.removeItem` in a throwaway WebView does NOT work and is
     * worth naming, because it looks like it should: a WebView with nothing loaded sits on
     * `about:blank`, whose storage is a different origin's, so the removal succeeds and
     * clears nothing.
     *
     * Origins are matched from [WebStorage.getOrigins] rather than composed by hand —
     * WebKit reports them with an explicit port (`https://vbx.to:443`), so a string built
     * from [baseUrl] would usually miss.
     *
     * `sessionStorage` needs no equivalent: it lives and dies with a WebView instance and
     * is never persisted.
     */
    private fun clearAnonymousId(host: String, onDone: () -> Unit) {
        WebStorage.getInstance().getOrigins { origins ->
            // Raw Map on the Java side, so both the map and its values arrive as platform
            // types — treat them as nullable rather than trusting the signature.
            origins?.values.orEmpty()
                .filterIsInstance<WebStorage.Origin>()
                .map { it.origin }
                .filter { Uri.parse(it).host == host }
                .forEach { WebStorage.getInstance().deleteOrigin(it) }
            onDone()
        }
    }

}

