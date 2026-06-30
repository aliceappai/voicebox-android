package com.voicebox.voiceboxkit

/**
 * Controls how the Voicebox recording experience is presented.
 *
 * Mirrors iOS `VoiceboxPresentationMode`.
 */
sealed class VoiceboxPresentationMode {

    /** Full-screen modal covering the entire screen. */
    object FullScreen : VoiceboxPresentationMode()

    /**
     * Bottom sheet starting at half height, expandable to full.
     * This is the default presentation mode.
     */
    object BottomSheet : VoiceboxPresentationMode()

    /** Standard bottom sheet presented at full height only. */
    object Sheet : VoiceboxPresentationMode()

    /**
     * Sheet that auto-sizes its height to match the web content.
     * The sheet starts at 300 dp; the JS bridge reports the content height
     * once the page loads and the sheet resizes to fit.
     */
    object FitContent : VoiceboxPresentationMode()

    /**
     * Sheet pinned to a fixed height in dp.
     *
     * ```kotlin
     * vb.presentationMode = VoiceboxPresentationMode.Custom(height = 520f)
     * ```
     *
     * @param height Sheet height in dp.
     */
    data class Custom(val height: Float) : VoiceboxPresentationMode()

    /**
     * Sheet sized as a fraction of screen height (0.0–1.0).
     * Values are clamped to [0.1, 1.0].
     *
     * ```kotlin
     * vb.presentationMode = VoiceboxPresentationMode.CustomFraction(fraction = 0.6f)
     * ```
     */
    data class CustomFraction(val fraction: Float) : VoiceboxPresentationMode() {
        /** Fraction clamped to [0.1, 1.0]. */
        val clamped: Float get() = fraction.coerceIn(0.1f, 1.0f)
    }
}
