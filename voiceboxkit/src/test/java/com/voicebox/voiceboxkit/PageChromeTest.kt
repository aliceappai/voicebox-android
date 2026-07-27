package com.voicebox.voiceboxkit

import org.junit.Assert.*
import org.junit.Test

/** Covers [VoiceboxPageChrome] — the CSS injected to reduce the recorder page to card + avatar. */
class PageChromeTest {

    @Test
    fun `css is empty when hidePageChrome is off`() {
        assertEquals("", VoiceboxPageChrome.css(hidePageChrome = false))
    }

    @Test
    fun `injectionJs is empty when hidePageChrome is off`() {
        // Callers skip evaluateJavascript entirely on empty, so this must not be a
        // no-op script — it must be the empty string.
        assertEquals("", VoiceboxPageChrome.injectionJs(hidePageChrome = false))
    }

    @Test
    fun `css hides the footer and clears the page background`() {
        val css = VoiceboxPageChrome.css(hidePageChrome = true)
        assertTrue(css.contains("#recorder-footer { display: none !important; }"))
        assertTrue(css.contains("body, html, #main { background: transparent !important; }"))
    }

    @Test
    fun `injectionJs reuses an existing style element so it is idempotent`() {
        // The fragment injects at document-start AND on page-finish; running twice must
        // not append a second <style>.
        val js = VoiceboxPageChrome.injectionJs(hidePageChrome = true)
        assertTrue(js.contains("getElementById('${VoiceboxPageChrome.STYLE_ID}')"))
        assertTrue(js.contains("if(!existing)"))
    }

    @Test
    fun `injectionJs falls back to documentElement so it is safe at document-start`() {
        val js = VoiceboxPageChrome.injectionJs(hidePageChrome = true)
        assertTrue(js.contains("document.head || document.documentElement"))
    }

    @Test
    fun `injectionJs embeds the css on a single line`() {
        // The CSS is embedded in a single-quoted JS string literal; a raw newline there
        // would be a syntax error and the whole injection would silently not apply.
        val js = VoiceboxPageChrome.injectionJs(hidePageChrome = true)
        val styleAssignment = js.lines().single { it.contains("style.textContent") }
        assertTrue(styleAssignment.contains("#recorder-footer"))
        assertTrue(styleAssignment.contains("background: transparent"))
    }

    @Test
    fun `hidePageChrome defaults to off on both public entry points`() {
        assertFalse(VoiceboxView(handle = "test").hidePageChrome)
        assertFalse(VoiceboxState(handle = "test").hidePageChrome)
    }

    @Test
    fun `state carries hidePageChrome through to the view config`() {
        val state = VoiceboxState(handle = "test").apply { hidePageChrome = true }
        assertTrue(state.toVoiceboxView().hidePageChrome)
    }

    // MARK: - dismissOnTapOutside

    @Test
    fun `dismissOnTapOutside defaults to off on both public entry points`() {
        assertFalse(VoiceboxView(handle = "test").dismissOnTapOutside)
        assertFalse(VoiceboxState(handle = "test").dismissOnTapOutside)
    }

    @Test
    fun `state carries dismissOnTapOutside through to the view config`() {
        val state = VoiceboxState(handle = "test").apply { dismissOnTapOutside = true }
        assertTrue(state.toVoiceboxView().dismissOnTapOutside)
    }

    @Test
    fun `dismiss handler is absent unless dismissOnTapOutside is set`() {
        val js = VoiceboxPageChrome.injectionJs(hidePageChrome = true, dismissOnTapOutside = false)
        assertFalse(js.contains("__vbxDismissBound"))
        assertFalse(js.contains(VoiceboxPageChrome.DISMISS_EVENT))
    }

    @Test
    fun `dismiss handler posts the dismiss event and spares the card and logo`() {
        val js = VoiceboxPageChrome.injectionJs(hidePageChrome = true, dismissOnTapOutside = true)
        assertTrue(js.contains("postMessage('${VoiceboxPageChrome.DISMISS_EVENT}')"))
        assertTrue(js.contains("getElementById('recorder-card')"))
        assertTrue(js.contains("getElementById('voicebox-logo')"))
    }

    @Test
    fun `dismiss handler is guarded so double injection binds only one listener`() {
        // The fragment injects at document-start AND on page-finish. A listener can't be
        // looked up and reused like the <style> element, so without the window guard every
        // tap would post 'dismiss' twice.
        val js = VoiceboxPageChrome.injectionJs(hidePageChrome = true, dismissOnTapOutside = true)
        assertTrue(js.contains("if(!window.__vbxDismissBound)"))
        assertEquals(1, Regex("addEventListener\\('click'").findAll(js).count())
    }

    @Test
    fun `dismissOnTapOutside alone still produces a script`() {
        // hidePageChrome off means no CSS, but the tap handler must still be injected —
        // otherwise the two options would be silently coupled.
        val js = VoiceboxPageChrome.injectionJs(hidePageChrome = false, dismissOnTapOutside = true)
        assertTrue(js.isNotEmpty())
        assertTrue(js.contains("__vbxDismissBound"))
        assertFalse(js.contains("style.textContent"))
    }

    @Test
    fun `no script at all when both options are off`() {
        assertEquals(
            "",
            VoiceboxPageChrome.injectionJs(hidePageChrome = false, dismissOnTapOutside = false),
        )
    }

}
