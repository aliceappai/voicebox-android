package com.voicebox.voiceboxkit

/**
 * Builds the page-chrome CSS injected into the recorder page, plus the JS that applies it and
 * (optionally) wires tap-outside-the-card to dismiss.
 *
 * Ported from the iOS `VoiceboxView.chromeCSS` / `chromeInjectionJS` pair. Kept in its own
 * object rather than inlined in the fragment so the exact CSS/JS is unit-testable, and so a
 * future floating-card mode can reuse the same injection path with a different rule set.
 *
 * The injected `<style>` carries [STYLE_ID], and injection reuses an existing element with
 * that id instead of appending a second one — the fragment injects both at document-start
 * (no footer flash) and again on page-finish (devices without `DOCUMENT_START_SCRIPT`), so
 * the script must be safe to run twice on the same document.
 */
internal object VoiceboxPageChrome {

    /** Id of the injected `<style>` element. Injection is idempotent on this id. */
    const val STYLE_ID = "voiceboxkit-chrome-style"

    /** `voiceboxEvent` payload posted when the user taps outside the card. */
    const val DISMISS_EVENT = "dismiss"

    /**
     * CSS applied to the recorder page for the given options, or `""` when there is
     * nothing to inject.
     *
     * With [hidePageChrome] the page's own footer is hidden and its background is made
     * transparent, so only the avatar badge + card remain and whatever is behind the WebView
     * (the sheet background, or the dimmed app when that background is transparent) shows
     * through everywhere else.
     */
    fun css(hidePageChrome: Boolean): String {
        if (!hidePageChrome) return ""
        return "#recorder-footer { display: none !important; }" +
            " body, html, #main { background: transparent !important; }"
    }

    /**
     * JS that applies [css] and, when [dismissOnTapOutside] is set, binds a tap-outside-the-card
     * dismiss handler. Returns `""` when neither option is on (callers skip the
     * `evaluateJavascript` entirely in that case).
     *
     * The style injection falls back to `document.documentElement` when `<head>` doesn't exist
     * yet, which is what makes this safe to run at document-start — a `<style>` on the root
     * element still applies, and the rules match `#recorder-footer` / `#main` whenever the page
     * builds them.
     */
    fun injectionJs(hidePageChrome: Boolean, dismissOnTapOutside: Boolean = false): String {
        val css = css(hidePageChrome)
        if (css.isEmpty() && !dismissOnTapOutside) return ""

        val styleBlock = if (css.isEmpty()) "" else """
            |    var existing = document.getElementById('$STYLE_ID');
            |    var style = existing || document.createElement('style');
            |    style.id = '$STYLE_ID';
            |    style.textContent = '${css.escapeForJsSingleQuotes()}';
            |    if(!existing){ (document.head || document.documentElement).appendChild(style); }
        """.trimMargin()

        // Ported from the iOS floating-card dismiss bridge. Guarded on a window flag rather
        // than an element id because a listener can't be looked up and reused the way a
        // <style> can — without the guard, the document-start + page-finish double injection
        // would bind two handlers and post 'dismiss' twice per tap.
        val dismissBlock = if (!dismissOnTapOutside) "" else """
            |    if(!window.__vbxDismissBound){
            |        window.__vbxDismissBound = true;
            |        document.addEventListener('click', function(e){
            |            var card = document.getElementById('recorder-card');
            |            var logo = document.getElementById('voicebox-logo');
            |            if((card && card.contains(e.target)) || (logo && logo.contains(e.target))) return;
            |            try{
            |                var mh = window.webkit
            |                    && window.webkit.messageHandlers
            |                    && window.webkit.messageHandlers.voiceboxEvent;
            |                if(mh) mh.postMessage('$DISMISS_EVENT');
            |            }catch(err){}
            |        });
            |    }
        """.trimMargin()

        return listOf("(function(){", styleBlock, dismissBlock, "})();")
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /**
     * Escapes a CSS string for embedding in a single-quoted JS string literal.
     *
     * Today's rules contain neither backslashes nor quotes, so this is defensive — but the
     * rules are edited by hand and a stray quote would otherwise produce a syntax error that
     * silently skips the whole injection rather than failing loudly.
     */
    private fun String.escapeForJsSingleQuotes(): String =
        replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
}
