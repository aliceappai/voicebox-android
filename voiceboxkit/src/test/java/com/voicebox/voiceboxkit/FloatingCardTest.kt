package com.voicebox.voiceboxkit

import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [VoiceboxPresentationMode.FloatingCard]. Mirrors the iOS `.floatingCard` behaviour. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FloatingCardTest {

    // MARK: - Mode + configuration

    @Test
    fun `floating card defaults to a 35 percent dim, like iOS`() {
        assertEquals(0.35f, VoiceboxPresentationMode.FloatingCard().dimOpacity)
    }

    @Test
    fun `dim is clamped to 0-1`() {
        assertEquals(0f, VoiceboxPresentationMode.FloatingCard(-0.5f).clampedDim)
        assertEquals(1f, VoiceboxPresentationMode.FloatingCard(3f).clampedDim)
        assertEquals(0.15f, VoiceboxPresentationMode.FloatingCard(0.15f).clampedDim)
    }

    @Test
    fun `entrance animation defaults to both, and the Compose state carries it through`() {
        assertEquals(VoiceboxEntranceAnimation.ALL, VoiceboxView(handle = "x").entranceAnimation)

        val state = VoiceboxState(handle = "x").apply {
            presentationMode = VoiceboxPresentationMode.FloatingCard(0.15f)
            entranceAnimation = setOf(VoiceboxEntranceAnimation.CardLiftIn)
        }
        val view = state.toVoiceboxView()

        assertEquals(VoiceboxPresentationMode.FloatingCard(0.15f), view.presentationMode)
        assertEquals(setOf(VoiceboxEntranceAnimation.CardLiftIn), view.entranceAnimation)
    }

    // MARK: - Page styling (cross-repo selectors)

    @Test
    fun `the CSS hides the footer, sizes the card to content and centres it`() {
        val css = VoiceboxFloatingCard.CSS
        assertTrue(css.contains("#recorder-footer { display: none !important; }"))
        assertTrue(css.contains("#recorder-card { height: auto !important; }"))
        assertTrue(css.contains("#main { min-height: 100vh !important;"))
        assertTrue(css.contains("justify-content: center !important;"))
        // The page background is left alone, so a voicebox's own colour/image fills the screen.
        assertFalse(css.contains("background"))
    }

    @Test
    fun `the injection is idempotent and re-applies once the head exists`() {
        val js = VoiceboxFloatingCard.injectionJs(tapOutsideDismiss = false)
        assertTrue(js.contains("voiceboxkit-card-style"))
        assertTrue(js.contains("DOMContentLoaded"))
        assertTrue(js.contains(VoiceboxFloatingCard.CSS))
    }

    @Test
    fun `tap outside dismisses only when there is no close button`() {
        assertTrue(VoiceboxFloatingCard.usesTapOutsideDismiss(showCloseButton = false))
        assertFalse(VoiceboxFloatingCard.usesTapOutsideDismiss(showCloseButton = true))

        assertTrue(VoiceboxFloatingCard.injectionJs(tapOutsideDismiss = true).contains("postMessage('dismiss')"))
        assertFalse(VoiceboxFloatingCard.injectionJs(tapOutsideDismiss = false).contains("dismiss"))
    }

    @Test
    fun `the card lift-in matches iOS timing and can be switched off`() {
        val on = VoiceboxFloatingCard.entranceJs(liftCard = true)
        assertTrue(on.contains("if(!true) return;"))
        assertTrue(on.contains("duration: 300"))
        assertTrue(on.contains("delay: 60"))
        assertTrue(on.contains("prefers-reduced-motion"))
        assertTrue(VoiceboxFloatingCard.entranceJs(liftCard = false).contains("if(!false) return;"))
    }

    // MARK: - Bridge

    @Test
    fun `a dismiss event from the page asks the host to dismiss`() {
        var dismissed = 0
        val bridge = VoiceboxJsBridge(
            voiceboxView = VoiceboxView(handle = "x"),
            onBgColor = {},
            onContentHeight = {},
            mainHandler = Handler(Looper.getMainLooper()),
            onDismissRequest = { dismissed += 1 },
        )

        bridge.onMessage("voiceboxEvent", "dismiss")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, dismissed)
    }

    // MARK: - Card skeleton geometry (iOS VoiceboxCardSkeletonView)

    @Test
    fun `the skeleton card is clamped to 344 wide and centred with its avatar`() {
        val g = CardSkeletonGeometry.layout(viewWidth = 412f, viewHeight = 900f)
        assertEquals(316f, g.card.width(), 0.01f)          // 412 - 96
        assertEquals(206f, g.card.centerX(), 0.01f)
        val avatar = 316f * 0.26f
        // The avatar straddles the card's top edge.
        assertEquals(g.card.top, g.avatar.centerY(), 0.01f)
        assertEquals(avatar, g.avatar.width(), 0.01f)
        // Avatar + card group is vertically centred.
        val groupTop = g.avatar.top
        assertEquals(900f - g.card.bottom, groupTop, 0.01f)
    }

    @Test
    fun `wide screens cap the card and short screens cap its height`() {
        assertEquals(344f, CardSkeletonGeometry.layout(1000f, 2000f).card.width(), 0.01f)
        val short = CardSkeletonGeometry.layout(412f, 400f)
        assertEquals(400f * 0.62f, short.card.height(), 0.01f)
    }

    @Test
    fun `the controls sit at the bottom of the card, language above button`() {
        val g = CardSkeletonGeometry.layout(412f, 900f)
        assertEquals(g.card.bottom - 28f, g.buttonBar.bottom, 0.01f)
        assertEquals(g.buttonBar.top - 16f, g.languageBar.bottom, 0.01f)
        assertEquals(3, g.lines.size)
    }
}
