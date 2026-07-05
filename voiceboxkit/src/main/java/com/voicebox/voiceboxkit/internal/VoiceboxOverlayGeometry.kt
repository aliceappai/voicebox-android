package com.voicebox.voiceboxkit

import kotlin.math.abs

/**
 * Geometry of the recorder's head-and-shoulders silhouette, used by the
 * `Overlay` presentation to hit-test touches.
 *
 * The web page paints the shape itself (transparent `overlay=1` mode); native
 * only needs to know *where the shape is* so taps on the transparent corners
 * beside the head fall through to the dimmed backdrop and dismiss, exactly like
 * tapping the backdrop directly.
 *
 * Coordinates and constants are in **px** (the caller converts the dp constants
 * that mirror the recorder CSS: 340-wide content, ~114 logo "head" radius 57,
 * rounded card radius 48). The hit-test is deliberately forgiving.
 *
 * Mirrors iOS `VoiceboxOverlaySilhouette`.
 */
internal data class VoiceboxOverlaySilhouette(
    val headRadiusPx: Float,
    /** Head-circle centre from the top of the content. */
    val headCenterYPx: Float,
    /** Card top edge from the top of the content (coincides with the head centre). */
    val cardTopPx: Float,
) {
    /**
     * True when (x, y) — origin top-left of the [width]×[height] box — is inside
     * the head circle or the card body. Card corners are treated as square:
     * intentionally forgiving so small layout drift never dead-zones a real tap.
     */
    fun contains(x: Float, y: Float, width: Float, height: Float): Boolean {
        if (y >= cardTopPx && x in 0f..width) return true
        val cx = width / 2f
        val dx = x - cx
        val dy = y - headCenterYPx
        return (dx * dx + dy * dy) <= headRadiusPx * headRadiusPx
    }

    companion object {
        /** Web recorder `#main` content width, in dp. */
        const val CONTENT_WIDTH_DP = 340f
        const val HEAD_RADIUS_DP = 57f
        // ≈ #main padding-top (82) − logo overhang (57) + head radius (57).
        const val HEAD_CENTER_Y_DP = 82f
        const val CARD_TOP_DP = 82f

        /** Builds the silhouette from the dp constants at the given density. */
        fun forDensity(density: Float): VoiceboxOverlaySilhouette = VoiceboxOverlaySilhouette(
            headRadiusPx = HEAD_RADIUS_DP * density,
            headCenterYPx = HEAD_CENTER_Y_DP * density,
            cardTopPx = CARD_TOP_DP * density,
        )
    }
}

/** Helper for `onFling`: a clearly-downward fling worth dismissing. */
internal fun isDownwardDismissFling(velocityX: Float, velocityY: Float, threshold: Float): Boolean =
    velocityY > threshold && velocityY > abs(velocityX)
