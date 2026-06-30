package com.voicebox.voiceboxkit

import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mirrors iOS `ThemeTests`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeTest {

    // MARK: - Defaults (all null)

    @Test
    fun `default values are all null`() {
        val theme = VoiceboxTheme()

        assertNull(theme.cornerRadius)
        assertNull(theme.backgroundColor)
        assertNull(theme.closeButtonIconColor)
        assertNull(theme.closeButtonBackgroundColor)
        assertNull(theme.closeButtonSize)
        assertNull(theme.closeButtonIconRes)
    }

    // MARK: - Resolved defaults

    @Test
    fun `resolved cornerRadius falls back to 16`() {
        assertEquals(16f, VoiceboxTheme().resolvedCornerRadius)
    }

    @Test
    fun `resolved closeButtonSize falls back to 32`() {
        assertEquals(32f, VoiceboxTheme().resolvedCloseButtonSize)
    }

    @Test
    fun `resolved uses set value when non-null`() {
        val theme = VoiceboxTheme(cornerRadius = 24f, closeButtonSize = 44f)
        assertEquals(24f, theme.resolvedCornerRadius)
        assertEquals(44f, theme.resolvedCloseButtonSize)
    }

    // MARK: - Custom values override defaults

    @Test
    fun `custom values are stored correctly`() {
        val theme = VoiceboxTheme(
            cornerRadius = 24f,
            backgroundColor = Color.BLACK,
            closeButtonIconColor = Color.WHITE,
            closeButtonBackgroundColor = Color.RED,
            closeButtonSize = 44f,
        )

        assertEquals(24f, theme.cornerRadius)
        assertEquals(Color.BLACK, theme.backgroundColor)
        assertEquals(Color.WHITE, theme.closeButtonIconColor)
        assertEquals(Color.RED, theme.closeButtonBackgroundColor)
        assertEquals(44f, theme.closeButtonSize)
    }

    // MARK: - Partial override

    @Test
    fun `partial override only sets specified properties`() {
        val theme = VoiceboxTheme(closeButtonBackgroundColor = Color.BLUE)

        // Explicit value
        assertEquals(Color.BLUE, theme.closeButtonBackgroundColor)

        // Others stay null
        assertNull(theme.cornerRadius)
        assertNull(theme.backgroundColor)
        assertNull(theme.closeButtonIconColor)
        assertNull(theme.closeButtonSize)
        assertNull(theme.closeButtonIconRes)

        // Resolved values fall back to SDK defaults
        assertEquals(VoiceboxTheme.DEFAULT_CORNER_RADIUS, theme.resolvedCornerRadius)
        assertEquals(VoiceboxTheme.DEFAULT_CLOSE_BUTTON_SIZE, theme.resolvedCloseButtonSize)
    }

    // MARK: - data class copy

    @Test
    fun `theme can be mutated via copy`() {
        val base = VoiceboxTheme()
        val modified = base.copy(cornerRadius = 8f, closeButtonBackgroundColor = Color.BLUE)

        assertEquals(8f, modified.cornerRadius)
        assertEquals(Color.BLUE, modified.closeButtonBackgroundColor)
        // Unset fields unchanged
        assertNull(modified.backgroundColor)
    }

    // MARK: - Presets

    @Test
    fun `plain preset has all null fields`() {
        val theme = VoiceboxTheme.plain
        assertNull(theme.closeButtonBackgroundColor)
        assertNull(theme.closeButtonIconColor)
        assertNull(theme.cornerRadius)
    }

    @Test
    fun `darkCircle preset has background and white icon`() {
        val theme = VoiceboxTheme.darkCircle
        assertNotNull(theme.closeButtonBackgroundColor)
        assertEquals(Color.WHITE, theme.closeButtonIconColor)
    }

    @Test
    fun `lightCircle preset has background and null icon color`() {
        val theme = VoiceboxTheme.lightCircle
        assertNotNull(theme.closeButtonBackgroundColor)
        // null icon color means it resolves to system label color at render time
        assertNull(theme.closeButtonIconColor)
    }
}
