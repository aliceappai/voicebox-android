package com.voicebox.voiceboxkit

import android.net.Uri
import androidx.fragment.app.FragmentActivity

/**
 * The primary interface for presenting a Voicebox recording experience.
 *
 * Create a [VoiceboxView] with a handle and optional params, then present it
 * via [present] (View API) or the Compose [voicebox] modifier.
 *
 * Mirrors iOS `VoiceboxView`.
 *
 * ```kotlin
 * val vb = VoiceboxView(handle = "my-handle")
 * vb.present(activity)
 * ```
 */
class VoiceboxView(
    /** The Voicebox handle. Required. */
    val handle: String,
    /** Query parameters appended to the Voicebox URL. */
    val params: Map<String, String> = emptyMap(),
    /** Visual theme for the presentation. */
    val theme: VoiceboxTheme = VoiceboxTheme(),
) {
    /** How the Voicebox is presented. Default: [VoiceboxPresentationMode.BottomSheet]. */
    var presentationMode: VoiceboxPresentationMode = VoiceboxPresentationMode.BottomSheet

    /** Whether to show the close button overlay. Default: `true`. */
    var showCloseButton: Boolean = true

    /** Listener for lifecycle events (recording complete, dismiss, error). */
    var listener: VoiceboxListener? = null

    /**
     * Per-instance override for mic permission handling.
     * - `null` — uses the global [VoiceboxKit.autoGrantMicPermission] value.
     * - `true` — auto-grant WebView mic permission (app already has `RECORD_AUDIO`).
     * - `false` — let the WebView show its own permission prompt.
     */
    var autoGrantMicPermission: Boolean? = null

    /** Resolved mic permission setting (instance override > global default). */
    val effectiveAutoGrantMicPermission: Boolean
        get() = autoGrantMicPermission ?: VoiceboxKit.autoGrantMicPermission

    // MARK: - URL Construction

    /** Builds the full Voicebox URL with params and UTM tags. */
    fun buildUrl(): Uri = VoiceboxUrlBuilder.build(handle, params)

    // MARK: - View API Presentation (Phase 3)

    /**
     * Present the Voicebox view using the configured [presentationMode].
     *
     * @param activity The hosting [FragmentActivity].
     */
    fun present(activity: FragmentActivity) {
        VoiceboxBottomSheetFragment.show(activity, this)
    }

    /** Convenience: present as a bottom sheet (half + full height). */
    fun presentAsBottomSheet(activity: FragmentActivity) {
        presentationMode = VoiceboxPresentationMode.BottomSheet
        present(activity)
    }

    /** Convenience: present as a full-screen modal. */
    fun presentFullScreen(activity: FragmentActivity) {
        presentationMode = VoiceboxPresentationMode.FullScreen
        present(activity)
    }
}
