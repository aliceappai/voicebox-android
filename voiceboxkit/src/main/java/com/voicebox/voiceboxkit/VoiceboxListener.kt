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

    /**
     * Called with the recorder's anonymous session id, once it exists.
     *
     * Messages recorded while signed out are attributed to this id and to no account.
     * Keep it, and hand it back when the user signs in, so those messages can be claimed
     * for the new account — without it they stay anonymous permanently.
     *
     * May be called more than once for a session (a re-open reports the same id again) and
     * may never be called at all — someone who opens the recorder without submitting
     * anything has no id to report. Treat it as "the latest known id", store it, and expect
     * repeats.
     */
    fun onAnonymousSessionId(voiceboxView: VoiceboxView, sessionId: String) {}
}
