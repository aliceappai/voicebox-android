package com.voicebox.voiceboxkit

import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The anonymous session id and the session methods. Mirrors iOS `SessionCaptureTests`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SessionCaptureTest {

    private class RecordingListener : VoiceboxListener {
        val sessionIds = mutableListOf<String>()
        var submitted = 0
        override fun onAnonymousSessionId(voiceboxView: VoiceboxView, sessionId: String) {
            sessionIds += sessionId
        }
        override fun onMessageSubmitted(voiceboxView: VoiceboxView) {
            submitted += 1
        }
    }

    private val view = VoiceboxView(handle = "test")

    private fun bridge() = VoiceboxJsBridge(
        voiceboxView = view,
        onBgColor = {},
        onContentHeight = {},
        mainHandler = Handler(Looper.getMainLooper()),
    )

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        VoiceboxKit.init(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        VoiceboxKit.init(ApplicationProvider.getApplicationContext())
    }

    // MARK: - Listener forwarding (the easy way to lose the id entirely)

    @Test
    fun `listener has a no-op default for onAnonymousSessionId`() {
        object : VoiceboxListener {}.onAnonymousSessionId(view, "abc")
    }

    @Test
    fun `the Compose state wrapper forwards onAnonymousSessionId`() {
        val listener = RecordingListener()
        val state = VoiceboxState(handle = "test").apply { this.listener = listener }

        val wrapped = state.toVoiceboxView()
        wrapped.listener!!.onAnonymousSessionId(wrapped, "session-1")

        assertEquals(listOf("session-1"), listener.sessionIds)
    }

    @Test
    fun `the fragment auto-dismiss wrapper forwards onAnonymousSessionId`() {
        val listener = RecordingListener()
        val wrapped = VoiceboxBottomSheetFragment.autoDismissListener(listener) {}

        wrapped.onAnonymousSessionId(view, "session-1")

        assertEquals(listOf("session-1"), listener.sessionIds)
    }

    @Test
    fun `the id survives BOTH wrappers stacked, as a Compose presentation stacks them`() {
        val listener = RecordingListener()
        val state = VoiceboxState(handle = "test").apply { this.listener = listener }
        val composeView = state.toVoiceboxView()
        val fragmentListener = VoiceboxBottomSheetFragment.autoDismissListener(composeView.listener) {}

        fragmentListener.onAnonymousSessionId(composeView, "session-1")

        assertEquals(listOf("session-1"), listener.sessionIds)
    }

    @Test
    fun `the auto-dismiss wrapper still dismisses after submit`() {
        val listener = RecordingListener()
        var dismissed = false
        val wrapped = VoiceboxBottomSheetFragment.autoDismissListener(listener) { dismissed = true }

        wrapped.onMessageSubmitted(view)

        assertEquals(1, listener.submitted)
        assertTrue(dismissed)
    }

    // MARK: - Bridge delivery

    @Test
    fun `a voiceboxSession message reaches the listener`() {
        val listener = RecordingListener()
        view.listener = listener

        bridge().onMessage("voiceboxSession", """{"sessionId":"abc-123","reason":"load"}""")
        idleMain()

        assertEquals(listOf("abc-123"), listener.sessionIds)
    }

    @Test
    fun `the same id is delivered once per presentation`() {
        val listener = RecordingListener()
        view.listener = listener
        val bridge = bridge()

        assertTrue(bridge.reportSessionId("abc"))
        assertFalse(bridge.reportSessionId("abc"))
        assertFalse(bridge.reportSessionId("abc"))

        assertEquals(listOf("abc"), listener.sessionIds)
    }

    @Test
    fun `a changed id is delivered again`() {
        val listener = RecordingListener()
        view.listener = listener
        val bridge = bridge()

        bridge.reportSessionId("first")
        bridge.reportSessionId("second")

        assertEquals(listOf("first", "second"), listener.sessionIds)
    }

    @Test
    fun `an id read before a listener exists is NOT marked delivered`() {
        val bridge = bridge()
        assertFalse(bridge.reportSessionId("abc"))

        val listener = RecordingListener()
        view.listener = listener
        assertTrue(bridge.reportSessionId("abc"))

        assertEquals(listOf("abc"), listener.sessionIds)
    }

    @Test
    fun `blank ids are ignored and whitespace is trimmed`() {
        val listener = RecordingListener()
        view.listener = listener
        val bridge = bridge()

        assertFalse(bridge.reportSessionId("   "))
        assertTrue(bridge.reportSessionId("  abc \n"))

        assertEquals(listOf("abc"), listener.sessionIds)
    }

    @Test
    fun `malformed voiceboxSession payloads are dropped quietly`() {
        val listener = RecordingListener()
        view.listener = listener
        val bridge = bridge()

        bridge.onMessage("voiceboxSession", "not json")
        bridge.onMessage("voiceboxSession", """{"reason":"load"}""")
        bridge.onMessage("voiceboxSession", """{"sessionId":""}""")
        idleMain()

        assertTrue(listener.sessionIds.isEmpty())
    }

    // MARK: - Parsing

    @Test
    fun `parseSessionPayload reads the id and ignores everything else`() {
        assertEquals("abc", VoiceboxJsBridge.parseSessionPayload("""{"sessionId":"abc","reason":"poll"}"""))
        assertEquals("abc", VoiceboxJsBridge.parseSessionPayload("""{"sessionId":" abc "}"""))
        assertNull(VoiceboxJsBridge.parseSessionPayload("""{"sessionId":""}"""))
        assertNull(VoiceboxJsBridge.parseSessionPayload(null))
        assertNull(VoiceboxJsBridge.parseSessionPayload("[]"))
    }

    @Test
    fun `parseEvaluatedString unwraps evaluateJavascript's JSON literal`() {
        assertEquals("abc", VoiceboxJsBridge.parseEvaluatedString("\"abc\""))
        assertNull(VoiceboxJsBridge.parseEvaluatedString("null"))
        assertNull(VoiceboxJsBridge.parseEvaluatedString("\"\""))
        assertNull(VoiceboxJsBridge.parseEvaluatedString(null))
        assertNull(VoiceboxJsBridge.parseEvaluatedString("42"))
    }

    // MARK: - Scripts (cross-repo contracts — nothing else would catch a break)

    @Test
    fun `the storage key matches vbx-web's profiles_session js`() {
        assertEquals("vbx_profiles_session_id", VoiceboxJsBridge.PROFILES_SESSION_STORAGE_KEY)
        assertTrue(VoiceboxJsBridge.SESSION_CAPTURE.contains("'vbx_profiles_session_id'"))
        assertTrue(VoiceboxJsBridge.SESSION_READ_SNIPPET.contains("'vbx_profiles_session_id'"))
    }

    @Test
    fun `the polyfill defines the voiceboxSession handler and reports delivery`() {
        assertTrue(VoiceboxJsBridge.WEBKIT_POLYFILL.contains("'voiceboxSession'"))
        assertTrue(VoiceboxJsBridge.WEBKIT_POLYFILL.contains("return true;"))
    }

    @Test
    fun `the capture script latches on delivery, not on read`() {
        val script = VoiceboxJsBridge.SESSION_CAPTURE
        assertTrue(script.contains("=== true"))
        assertTrue(script.contains("if(ok) delivered = id;"))
        assertTrue(script.contains("window.__voiceboxPostSessionId = post;"))
    }

    @Test
    fun `recorder events re-check the session id`() {
        assertTrue(VoiceboxJsBridge.EVENT_OBSERVER.contains("__voiceboxPostSessionId('event')"))
    }

    // MARK: - establishSession / clearSession

    @Test
    fun `establishSession without init reports false`() {
        VoiceboxKit.applicationContext = null
        var result: Boolean? = null

        VoiceboxKit.establishSession("https://vbx.to/consume?token=secret") { result = it }
        idleMain()

        assertEquals(false, result)
    }

    @Test
    fun `clearSession without init still calls back`() {
        VoiceboxKit.applicationContext = null
        var done = false

        VoiceboxKit.clearSession { done = true }
        idleMain()

        assertTrue(done)
    }

    @Test
    fun `establishSession times out as a failure`() {
        var result: Boolean? = null

        VoiceboxKit.establishSession("https://vbx.to/consume?token=secret") { result = it }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(VoiceboxSessionPrimer.TIMEOUT_MS + 1))

        assertEquals(false, result)
    }

    @Test
    fun `a newer establishSession cancels the one in flight`() {
        val results = mutableListOf<Pair<String, Boolean>>()

        VoiceboxKit.establishSession("https://vbx.to/a") { results += "a" to it }
        VoiceboxKit.establishSession("https://vbx.to/b") { results += "b" to it }
        idleMain()

        assertEquals(listOf("a" to false), results)
    }

    // MARK: - Scoped clearing

    @Test
    fun `cookie names are parsed from a Cookie header`() {
        assertEquals(listOf("a", "_vbx_session"), VoiceboxSessionStorage.cookieNames("a=1; _vbx_session=xyz; a=2"))
        assertTrue(VoiceboxSessionStorage.cookieNames(null).isEmpty())
    }

    @Test
    fun `cookie domain candidates cover the host and its parents`() {
        assertEquals(listOf("vbx.to"), VoiceboxSessionStorage.cookieDomainCandidates("vbx.to"))
        assertEquals(
            listOf("app.vbxstaging.com", "vbxstaging.com"),
            VoiceboxSessionStorage.cookieDomainCandidates("app.vbxstaging.com"),
        )
    }

    @Test
    fun `only the recorder host's storage origins are cleared`() {
        assertTrue(VoiceboxSessionStorage.originMatchesHost("https://vbx.to", "vbx.to"))
        assertTrue(VoiceboxSessionStorage.originMatchesHost("https://www.vbx.to", "vbx.to"))
        assertFalse(VoiceboxSessionStorage.originMatchesHost("https://notvbx.to", "vbx.to"))
        assertFalse(VoiceboxSessionStorage.originMatchesHost("https://example.com", "vbx.to"))
    }

    @Test
    fun `clearing covers the base host and every host the recorder landed on`() {
        assertEquals(
            listOf("vbxstaging.com", "app.vbxstaging.com", "vbx.vbxstaging.com"),
            VoiceboxSessionStorage.hostsToClear(
                "vbxstaging.com",
                setOf("vbx.vbxstaging.com", "vbxstaging.com", "app.vbxstaging.com"),
            ),
        )
    }

    @Test
    fun `a redirected-to host is remembered, and only http(s) hosts are`() {
        VoiceboxSessionStorage.rememberHost("https://vbx.vbxstaging.com/@test?x=1")
        VoiceboxSessionStorage.rememberHost("about:blank")
        val prefs = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("voicebox_session", android.content.Context.MODE_PRIVATE)
        val hosts = prefs.getStringSet("recorder_hosts", emptySet()).orEmpty()
        assertTrue("vbx.vbxstaging.com" in hosts)
        assertEquals(1, hosts.size)
    }

    @Test
    fun `clearing never reaches for the process-global wipes`() {
        val source = java.io.File("src/main/java/com/voicebox/voiceboxkit/internal/VoiceboxSessionStorage.kt").readText()
        val code = source.lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") }.joinToString("\n")
        assertFalse(code.contains("removeAllCookies"))
        assertFalse(code.contains("deleteAllData"))
    }
}
