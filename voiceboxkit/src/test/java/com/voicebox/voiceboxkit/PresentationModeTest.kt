package com.voicebox.voiceboxkit

import org.junit.Assert.*
import org.junit.Test

/** Mirrors iOS `PresentationModeTests`. */
class PresentationModeTest {

    @Test
    fun `all modes exist and are distinct`() {
        val modes = listOf(
            VoiceboxPresentationMode.FullScreen,
            VoiceboxPresentationMode.BottomSheet,
            VoiceboxPresentationMode.Sheet,
            VoiceboxPresentationMode.FitContent,
        )
        // Each toString() is unique
        assertEquals(4, modes.map { it.javaClass.simpleName }.toSet().size)
    }

    @Test
    fun `BottomSheet is the default presentation mode`() {
        val vb = VoiceboxView(handle = "test")
        assertEquals(VoiceboxPresentationMode.BottomSheet, vb.presentationMode)
    }

    @Test
    fun `presentationMode assignment works for all cases`() {
        val vb = VoiceboxView(handle = "test")

        vb.presentationMode = VoiceboxPresentationMode.FullScreen
        assertEquals(VoiceboxPresentationMode.FullScreen, vb.presentationMode)

        vb.presentationMode = VoiceboxPresentationMode.Sheet
        assertEquals(VoiceboxPresentationMode.Sheet, vb.presentationMode)

        vb.presentationMode = VoiceboxPresentationMode.FitContent
        assertEquals(VoiceboxPresentationMode.FitContent, vb.presentationMode)

        vb.presentationMode = VoiceboxPresentationMode.BottomSheet
        assertEquals(VoiceboxPresentationMode.BottomSheet, vb.presentationMode)
    }

    @Test
    fun `Custom mode stores height`() {
        val mode = VoiceboxPresentationMode.Custom(height = 520f)
        assertEquals(520f, mode.height)
    }

    @Test
    fun `CustomFraction mode stores and clamps fraction`() {
        val mode = VoiceboxPresentationMode.CustomFraction(fraction = 0.6f)
        assertEquals(0.6f, mode.fraction)
        assertEquals(0.6f, mode.clamped)

        val tooLow = VoiceboxPresentationMode.CustomFraction(fraction = 0.0f)
        assertEquals(0.1f, tooLow.clamped)

        val tooHigh = VoiceboxPresentationMode.CustomFraction(fraction = 1.5f)
        assertEquals(1.0f, tooHigh.clamped)
    }

    @Test
    fun `equality works for object cases`() {
        assertEquals(VoiceboxPresentationMode.BottomSheet, VoiceboxPresentationMode.BottomSheet)
        assertEquals(VoiceboxPresentationMode.FullScreen, VoiceboxPresentationMode.FullScreen)
    }

    @Test
    fun `equality works for data class cases`() {
        assertEquals(
            VoiceboxPresentationMode.Custom(400f),
            VoiceboxPresentationMode.Custom(400f),
        )
        assertNotEquals(
            VoiceboxPresentationMode.Custom(400f),
            VoiceboxPresentationMode.Custom(500f),
        )
        assertEquals(
            VoiceboxPresentationMode.CustomFraction(0.5f),
            VoiceboxPresentationMode.CustomFraction(0.5f),
        )
        assertNotEquals(
            VoiceboxPresentationMode.CustomFraction(0.5f),
            VoiceboxPresentationMode.CustomFraction(0.6f),
        )
    }

    @Test
    fun `Custom and FitContent are not equal`() {
        assertNotEquals(
            VoiceboxPresentationMode.Custom(400f) as VoiceboxPresentationMode,
            VoiceboxPresentationMode.FitContent,
        )
    }

    @Test
    fun `VoiceboxView stores custom modes`() {
        val vb = VoiceboxView(handle = "test")

        vb.presentationMode = VoiceboxPresentationMode.Custom(600f)
        assertEquals(VoiceboxPresentationMode.Custom(600f), vb.presentationMode)

        vb.presentationMode = VoiceboxPresentationMode.CustomFraction(0.75f)
        assertEquals(VoiceboxPresentationMode.CustomFraction(0.75f), vb.presentationMode)
    }
}
