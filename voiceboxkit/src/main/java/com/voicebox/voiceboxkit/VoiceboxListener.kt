package com.voicebox.voiceboxkit

/**
 * Listener interface for receiving Voicebox lifecycle events.
 *
 * All methods have default empty implementations — implement only the
 * callbacks you care about.
 *
 * Mirrors iOS `VoiceboxDelegate`.
 *
 * ```kotlin
 * val vb = VoiceboxView(handle = "my-handle")
 * vb.listener = object : VoiceboxListener {
 *     override fun onRecordingComplete(voiceboxView: VoiceboxView) {
 *         println("Recording saved!")
 *     }
 * }
 * ```
 */
interface VoiceboxListener {

    /** Called when the user finishes recording (JS `voicebox:recordingComplete`). */
    fun onRecordingComplete(voiceboxView: VoiceboxView) {}

    /** Called when the recording is submitted/saved (JS `voicebox:messageSubmitted`). */
    fun onMessageSubmitted(voiceboxView: VoiceboxView) {}

    /** Called when the Voicebox view is dismissed (back gesture, close button, or programmatic). */
    fun onDismiss(voiceboxView: VoiceboxView) {}

    /** Called when the Voicebox fails to load (network error, timeout, etc.). */
    fun onFailure(voiceboxView: VoiceboxView, error: Exception) {}
}
