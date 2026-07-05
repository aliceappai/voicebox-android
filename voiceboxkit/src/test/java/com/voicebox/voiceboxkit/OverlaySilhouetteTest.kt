package com.voicebox.voiceboxkit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-geometry checks for the overlay silhouette hit-testing. */
class OverlaySilhouetteTest {

    // density 1 → px == dp, so constants map 1:1.
    private val silhouette = VoiceboxOverlaySilhouette.forDensity(1f)
    private val w = VoiceboxOverlaySilhouette.CONTENT_WIDTH_DP // 340
    private val h = 500f

    @Test
    fun `card body is inside`() {
        // Anywhere below the card top, full width, is inside.
        assertTrue(silhouette.contains(x = 10f, y = 200f, width = w, height = h))
        assertTrue(silhouette.contains(x = w - 10f, y = 480f, width = w, height = h))
    }

    @Test
    fun `head circle is inside near the top centre`() {
        assertTrue(silhouette.contains(x = w / 2f, y = 30f, width = w, height = h))
    }

    @Test
    fun `top corners beside the head are outside`() {
        // Above the card top and far from the head centre → dismiss zone.
        assertFalse(silhouette.contains(x = 4f, y = 4f, width = w, height = h))
        assertFalse(silhouette.contains(x = w - 4f, y = 4f, width = w, height = h))
    }

    @Test
    fun `downward fling past threshold dismisses`() {
        assertTrue(isDownwardDismissFling(velocityX = 100f, velocityY = 3000f, threshold = 1500f))
        assertFalse(isDownwardDismissFling(velocityX = 100f, velocityY = 800f, threshold = 1500f))
        // Mostly-horizontal fling should not dismiss.
        assertFalse(isDownwardDismissFling(velocityX = 4000f, velocityY = 1600f, threshold = 1500f))
    }
}
