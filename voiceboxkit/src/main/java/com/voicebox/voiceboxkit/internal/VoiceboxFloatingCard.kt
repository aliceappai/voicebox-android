package com.voicebox.voiceboxkit

/**
 * The page-side half of [VoiceboxPresentationMode.FloatingCard]: the CSS that turns the
 * full-page recorder into a centred card, and the JS that applies it and plays the card's
 * entrance. Pure strings, so the cross-repo selectors are unit-testable.
 *
 * Mirrors iOS `VoiceboxView.chromeCSS` / `chromeInjectionJS` and
 * `VoiceboxViewController.playFloatingCardEntrance`.
 *
 * ⚠️ Cross-repo contract with vbx-web's recorder markup: `#main`, `#recorder-card`,
 * `#recorder-footer`, `#voicebox-logo`. A rename there silently un-styles the card.
 */
internal object VoiceboxFloatingCard {

    /** Card lift-in, run by the page's own Web Animations API (milliseconds). Same as iOS. */
    const val CARD_LIFT_IN_DURATION_MS = 300

    /** Delay before the lift-in, so it reads as following the background (milliseconds). */
    const val CARD_LIFT_IN_DELAY_MS = 60

    /** Background reveal: fade + settle from a slight scale-up (milliseconds). Same as iOS. */
    const val BACKGROUND_REVEAL_DURATION_MS = 350L

    /** Start scale of the background reveal. */
    const val BACKGROUND_REVEAL_START_SCALE = 1.08f

    /**
     * Hide the footer, let the card be its natural height, and centre it vertically. The page
     * background is left untouched, so a voicebox's configured colour or image fills the screen.
     */
    const val CSS =
        "#recorder-footer { display: none !important; } " +
            "#recorder-card { height: auto !important; } " +
            "html, body { min-height: 100vh !important; margin: 0 !important; } " +
            "#main { min-height: 100vh !important; display: flex !important; " +
            "flex-direction: column !important; justify-content: center !important; }"

    /** Tap-outside dismiss is only wired when there is no close button — never both. */
    fun usesTapOutsideDismiss(showCloseButton: Boolean): Boolean = !showCloseButton

    /**
     * Injects [CSS] into a `<style>` tag (idempotent — re-running replaces it). Runs now and
     * again at DOMContentLoaded, because at document start there is no `<head>` yet. With
     * [tapOutsideDismiss], a click outside the card and logo posts `dismiss` on the
     * `voiceboxEvent` handler.
     */
    fun injectionJs(tapOutsideDismiss: Boolean): String {
        val dismiss = if (!tapOutsideDismiss) "" else """
                if (!window.__vbxDismissBound) {
                    window.__vbxDismissBound = true;
                    document.addEventListener('click', function(e) {
                        var card = document.getElementById('recorder-card');
                        var logo = document.getElementById('voicebox-logo');
                        var inCard = card && card.contains(e.target);
                        var inLogo = logo && logo.contains(e.target);
                        var h = window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.voiceboxEvent;
                        if (!inCard && !inLogo && h) { h.postMessage('dismiss'); }
                    });
                }"""
        return """
            (function(){
                if(window.top !== window) return;
                function apply(){
                    var root = document.head || document.documentElement;
                    if(!root) return;
                    var style = document.getElementById('voiceboxkit-card-style');
                    if(!style){
                        style = document.createElement('style');
                        style.id = 'voiceboxkit-card-style';
                        root.appendChild(style);
                    }
                    style.textContent = '$CSS';
                }
                apply();
                document.addEventListener('DOMContentLoaded', apply);$dismiss
            })();
        """.trimIndent()
    }

    /**
     * Lifts the card assembly (`#main`: logo + card) up and scales it into place. A no-op when
     * [liftCard] is off, when the user prefers reduced motion, or without Web Animations.
     */
    fun entranceJs(liftCard: Boolean): String = """
        (function(){
            if(!$liftCard) return;
            var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
            var main = document.getElementById('main');
            if(reduce || !main || typeof main.animate !== 'function') return;
            main.animate(
                [ { opacity: 0, transform: 'translateY(26px) scale(0.96)' },
                  { opacity: 1, transform: 'translateY(0) scale(1)' } ],
                { duration: $CARD_LIFT_IN_DURATION_MS, delay: $CARD_LIFT_IN_DELAY_MS,
                  easing: 'cubic-bezier(0.16, 1, 0.3, 1)', fill: 'both' }
            );
        })();
    """.trimIndent()
}
