package com.voicebox.voiceboxkit

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the anonymous-session capture contract. Mirrors iOS `SessionCaptureTests`.
 *
 * These are JVM unit tests, so they assert on the injected SCRIPTS and the surface shape
 * rather than on WebView behaviour — which is the level the risk actually lives at. The
 * things that break this feature silently are a renamed storage key, a script that reads
 * once and gives up, or a clear that takes half the identity; none of those need a device
 * to catch, and all of them are invisible at compile time.
 */
class SessionCaptureTest {

    // MARK: - Cross-repo contract

    /**
     * This key is owned by vbx-web (`app/javascript/profiles_session.js`). It is not a local
     * naming choice: messages are stamped with the id stored under it, and the backend claims
     * them by that id. A rename on either side breaks capture silently — nothing fails to
     * compile on either — so the literal is pinned here to make the break loud.
     */
    @Test
    fun `storage key matches the web contract`() {
        assertEquals("vbx_profiles_session_id", VoiceboxJsBridge.PROFILES_SESSION_STORAGE_KEY)
    }

    @Test
    fun `capture script reads the contract key`() {
        assertTrue(
            VoiceboxJsBridge.SESSION_CAPTURE
                .contains(VoiceboxJsBridge.PROFILES_SESSION_STORAGE_KEY),
        )
    }

    // MARK: - Lazy write

    /**
     * The id is written lazily — a first-time recorder has none at load — so the script has
     * to keep looking rather than reading once and giving up, and must stop as soon as it
     * has one rather than polling on for two minutes.
     */
    @Test
    fun `capture script polls for a lazily created id and stops once it has one`() {
        val script = VoiceboxJsBridge.SESSION_CAPTURE

        assertTrue("must poll for a lazily-written id", script.contains("setInterval"))
        assertTrue("must stop polling once it has one", script.contains("clearInterval"))
        assertTrue(
            "must fall back to sessionStorage, as vbx-web's own reader does",
            script.contains("sessionStorage"),
        )
    }

    /** The recorder's own events are the earliest useful trigger, so the observer calls in. */
    @Test
    fun `recorder events trigger a session read`() {
        assertTrue(
            VoiceboxJsBridge.EVENT_OBSERVER.contains("__voiceboxPostSessionId"),
        )
        assertTrue(
            "the capture script must define what the observer calls",
            VoiceboxJsBridge.SESSION_CAPTURE.contains("__voiceboxPostSessionId"),
        )
    }

    // MARK: - Bridge plumbing

    /**
     * The capture script posts through `window.webkit.messageHandlers.voiceboxSession`, which
     * only exists on Android because the polyfill creates it. Miss it out and the script runs,
     * finds no handler, swallows its own exception, and reports nothing — with no error
     * anywhere.
     */
    @Test
    fun `polyfill exposes the session handler the capture script posts to`() {
        assertTrue(VoiceboxJsBridge.WEBKIT_POLYFILL.contains("voiceboxSession"))
        assertTrue(VoiceboxJsBridge.SESSION_CAPTURE.contains("voiceboxSession"))
    }

    // MARK: - Clearing (sign-out)

    /**
     * ⚠️ The Android-specific decision most worth pinning.
     *
     * `CookieManager` is process-global — one jar shared with every WebView the host app
     * has, including ones showing sites with nothing to do with Voicebox.
     * `removeAllCookies()` takes all of them, so an SDK calling it on its own sign-out
     * would log the host's users out of unrelated services. There is no per-domain removal
     * API; expiring the recorder's own cookies individually is the way, and this asserts
     * nobody has "simplified" it back.
     *
     * Read from source because the alternative has no unit-testable surface: the wrong
     * version compiles, passes every other test, and is only visible on a device by
     * noticing something ELSE broke.
     */
    @Test
    fun `clearing never wipes the host app's other cookies`() {
        val source = sourceOf("VoiceboxWebStorage.kt")

        assertFalse(
            "removeAllCookies would clear cookies belonging to the host app, not just ours",
            source.contains("removeAllCookies"),
        )
        assertTrue(
            "the recorder's own cookies are expired individually instead",
            source.contains("Max-Age=0"),
        )
    }

    /**
     * Clearing must take the COOKIE as well as the storage. A host that cleared one and
     * forgot the other leaves the device signed in as the account that just left, or signed
     * out but still accumulating recordings under the previous identity — and the only way
     * to make that impossible is for one call to do both.
     */
    @Test
    fun `clearing removes cookies and the anonymous id together`() {
        val source = sourceOf("VoiceboxWebStorage.kt")

        assertTrue(source.contains("clearCookies"))
        assertTrue(source.contains("clearAnonymousId"))
        assertTrue(
            "deleteOrigin is the only way to reach storage from outside a loaded page",
            source.contains("deleteOrigin"),
        )
    }

    private fun sourceOf(fileName: String): String {
        val path = java.io.File(
            "src/main/java/com/voicebox/voiceboxkit/internal/$fileName",
        )
        assertTrue("expected to find $fileName at ${path.absolutePath}", path.exists())
        return path.readText()
    }

    // MARK: - Listener

    /** The callback has a default implementation, so existing listeners keep compiling. */
    @Test
    fun `listener method is optional`() {
        val listener = object : VoiceboxListener {}
        listener.onAnonymousSessionId(VoiceboxView(handle = "test"), "abc-123")
    }

    @Test
    fun `listener receives the reported id`() {
        var received: String? = null
        val listener = object : VoiceboxListener {
            override fun onAnonymousSessionId(voiceboxView: VoiceboxView, sessionId: String) {
                received = sessionId
            }
        }

        listener.onAnonymousSessionId(VoiceboxView(handle = "test"), "psid-1")

        assertEquals("psid-1", received)
    }
}
