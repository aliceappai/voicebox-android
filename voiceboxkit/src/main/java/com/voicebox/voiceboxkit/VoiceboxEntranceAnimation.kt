package com.voicebox.voiceboxkit

/**
 * Entrance animation(s) for [VoiceboxPresentationMode.FloatingCard]. Other presentation modes
 * ignore this. Pass any combination through [VoiceboxView.entranceAnimation]:
 *
 * ```kotlin
 * vb.entranceAnimation = VoiceboxEntranceAnimation.ALL        // default
 * vb.entranceAnimation = setOf(VoiceboxEntranceAnimation.CardLiftIn)
 * vb.entranceAnimation = VoiceboxEntranceAnimation.NONE
 * ```
 *
 * Both are skipped when the device has animations turned off. Mirrors iOS
 * `VoiceboxEntranceAnimation`.
 */
enum class VoiceboxEntranceAnimation {
    /** The full-screen background fades in, settling from a slight scale-up. */
    BackgroundReveal,

    /** The card (logo + card) lifts up and scales into place, a beat after the background. */
    CardLiftIn,
    ;

    companion object {
        /** Both animations. The default. */
        @JvmField val ALL: Set<VoiceboxEntranceAnimation> = setOf(BackgroundReveal, CardLiftIn)

        /** No entrance animation — the card simply appears. */
        @JvmField val NONE: Set<VoiceboxEntranceAnimation> = emptySet()
    }
}
