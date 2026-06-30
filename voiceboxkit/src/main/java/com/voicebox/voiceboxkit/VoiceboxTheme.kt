package com.voicebox.voiceboxkit

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

/**
 * Configures the visual appearance of the Voicebox presentation.
 *
 * All properties are optional (`null` means "use the SDK default") —
 * set only what you want to override.
 *
 * Mirrors iOS `VoiceboxTheme`.
 *
 * ```kotlin
 * // Only override the close button background — everything else uses SDK defaults
 * val theme = VoiceboxTheme(
 *     closeButtonBackgroundColor = Color.BLUE,
 *     closeButtonSize = 36f,
 * )
 * ```
 */
data class VoiceboxTheme(
    /** Corner radius (dp) applied to the sheet. Default: [DEFAULT_CORNER_RADIUS]. */
    val cornerRadius: Float? = null,

    /**
     * Background color behind the WebView. `null` = system window background.
     * Default: `null` (resolved at render time to match the system theme).
     */
    @ColorInt val backgroundColor: Int? = null,

    /**
     * Color of the close icon. `null` = system label color.
     * Default: `null`.
     */
    @ColorInt val closeButtonIconColor: Int? = null,

    /**
     * Background color of the close button chip. `null` = transparent —
     * the icon floats over the sheet with no circle behind it.
     * Default: `null`.
     */
    @ColorInt val closeButtonBackgroundColor: Int? = null,

    /** Diameter of the close button in dp. Default: [DEFAULT_CLOSE_BUTTON_SIZE]. */
    val closeButtonSize: Float? = null,

    /**
     * Drawable resource used as the close button icon.
     * `null` = built-in X drawable bundled with the SDK.
     */
    @DrawableRes val closeButtonIconRes: Int? = null,
) {
    companion object {
        const val DEFAULT_CORNER_RADIUS: Float = 16f
        const val DEFAULT_CLOSE_BUTTON_SIZE: Float = 32f

        // Precomputed color ints — avoids loading android.graphics.Color at class-init
        // time, which would throw RuntimeException("Stub!") in non-Robolectric unit tests.
        // Values are identical to Color.WHITE, Color.argb(230,0,0,0), Color.argb(255,229,229,234).
        private const val COLOR_WHITE = 0xFFFFFFFF.toInt()                // Color.WHITE
        private const val COLOR_DARK_FILL = 0xE6000000.toInt()            // argb(230, 0, 0, 0) ~90% black
        private const val COLOR_LIGHT_FILL = 0xFFE5E5EA.toInt()           // argb(255, 229, 229, 234) ≈ systemGray5

        // MARK: - Presets

        /** Default: transparent close button, icon in system label color. */
        @JvmField val plain = VoiceboxTheme()

        /** Filled dark circle with a white × icon. */
        @JvmField val darkCircle = VoiceboxTheme(
            closeButtonIconColor = COLOR_WHITE,
            closeButtonBackgroundColor = COLOR_DARK_FILL,
        )

        /** Filled light circle with a dark × icon. */
        @JvmField val lightCircle = VoiceboxTheme(
            closeButtonBackgroundColor = COLOR_LIGHT_FILL,
        )
    }

    // MARK: - Resolved values (fall back to SDK defaults when property is null)

    /** Corner radius to apply, falling back to [DEFAULT_CORNER_RADIUS]. */
    val resolvedCornerRadius: Float get() = cornerRadius ?: DEFAULT_CORNER_RADIUS

    /** Close button diameter to apply, falling back to [DEFAULT_CLOSE_BUTTON_SIZE]. */
    val resolvedCloseButtonSize: Float get() = closeButtonSize ?: DEFAULT_CLOSE_BUTTON_SIZE
}
