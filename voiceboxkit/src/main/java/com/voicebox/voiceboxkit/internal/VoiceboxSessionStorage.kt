package com.voicebox.voiceboxkit

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage

/**
 * Forgets who was recording on this device: the recorder's session cookies and its anonymous
 * session id, for [VoiceboxKit.baseUrl]'s host only.
 *
 * ⚠️ Never `CookieManager.removeAllCookies()` or `WebStorage.deleteAllData()`. Both are
 * process-global — one jar and one store shared with every WebView in the host app — and
 * VoiceboxKit ships inside apps that show sites unrelated to Voicebox. Clearing the
 * recorder's session must not sign a host's users out of anything else.
 */
internal object VoiceboxSessionStorage {

    private const val PREFS_NAME = "voicebox_session"
    private const val KEY_HOSTS = "recorder_hosts"

    /** Cap on remembered hosts — a recorder only ever lands on a handful. */
    private const val MAX_HOSTS = 8

    /**
     * Remember a host a recorder (or a session prime) actually finished loading on.
     *
     * `baseUrl` is only where loading STARTS: a host that redirects (e.g. a public link host that
     * 301s to the recorder's own host) leaves the recorder's cookie on a host `baseUrl` does not
     * name, and `CookieManager.getCookie(baseUrl)` cannot see a host-only cookie on another host —
     * so without this, [clearFor] would silently leave that session behind.
     */
    fun rememberHost(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme != "https" && uri.scheme != "http") return
        val host = uri.host?.takeIf { it.isNotEmpty() } ?: return
        val prefs = VoiceboxKit.applicationContext
            ?.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) ?: return
        val hosts = prefs.getStringSet(KEY_HOSTS, emptySet()).orEmpty()
        if (host in hosts) return
        prefs.edit().putStringSet(KEY_HOSTS, (hosts + host).toList().takeLast(MAX_HOSTS).toSet()).apply()
    }

    private fun rememberedHosts(): Set<String> =
        VoiceboxKit.applicationContext
            ?.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            ?.getStringSet(KEY_HOSTS, emptySet())
            .orEmpty()

    fun clearFor(baseUrl: String, onDone: () -> Unit) {
        val baseHost = Uri.parse(baseUrl).host
        if (baseHost.isNullOrEmpty()) {
            onDone()
            return
        }
        val scheme = Uri.parse(baseUrl).scheme ?: "https"
        val hosts = hostsToClear(baseHost, rememberedHosts())

        hosts.forEach { expireCookies("$scheme://$it", it) }
        CookieManager.getInstance().flush()

        WebStorage.getInstance().getOrigins { origins ->
            val matching = origins?.values.orEmpty()
                .mapNotNull { (it as? WebStorage.Origin)?.origin }
                .filter { origin -> hosts.any { originMatchesHost(origin, it) } }
            matching.forEach { WebStorage.getInstance().deleteOrigin(it) }
            Handler(Looper.getMainLooper()).post(onDone)
        }
    }

    /** [baseHost] first, then every remembered host, without duplicates. */
    internal fun hostsToClear(baseHost: String, remembered: Set<String>): List<String> =
        (listOf(baseHost) + remembered.sorted()).distinct()

    /**
     * There is no per-domain removal API, so read back every cookie visible to [baseUrl] and
     * expire each one. A cookie may have been set host-only or on a parent domain, and an
     * expiry only matches a cookie with the same Domain, so every candidate scope is tried.
     */
    private fun expireCookies(baseUrl: String, host: String) {
        val manager = CookieManager.getInstance()
        val names = cookieNames(manager.getCookie(baseUrl))
        val domains = cookieDomainCandidates(host)
        // `Secure` on an https base: `__Secure-`/`__Host-` cookies reject a write without it.
        val secure = if (Uri.parse(baseUrl).scheme == "https") "; Secure" else ""
        for (name in names) {
            manager.setCookie(baseUrl, "$name=; Max-Age=0; Path=/$secure")
            for (domain in domains) {
                manager.setCookie(baseUrl, "$name=; Max-Age=0; Path=/; Domain=$domain$secure")
            }
        }
    }

    /** `"a=1; b=2"` → `["a", "b"]`. */
    internal fun cookieNames(cookieHeader: String?): List<String> =
        cookieHeader.orEmpty()
            .split(";")
            .mapNotNull { it.substringBefore("=").trim().takeIf(String::isNotEmpty) }
            .distinct()

    /**
     * Every Domain a cookie visible to [host] could carry: the host and each parent with at
     * least two labels. `app.vbxstaging.com` → `[app.vbxstaging.com, vbxstaging.com]`.
     */
    internal fun cookieDomainCandidates(host: String): List<String> {
        val labels = host.trim('.').split(".").filter { it.isNotEmpty() }
        if (labels.size < 2) return listOfNotNull(host.takeIf { it.isNotEmpty() })
        return (0..labels.size - 2).map { labels.drop(it).joinToString(".") }
    }

    /** Whether a storage origin (`https://vbx.to`) belongs to [host] or one of its subdomains. */
    internal fun originMatchesHost(origin: String, host: String): Boolean {
        val originHost = Uri.parse(origin).host ?: return false
        return originHost == host || originHost.endsWith(".$host")
    }
}
