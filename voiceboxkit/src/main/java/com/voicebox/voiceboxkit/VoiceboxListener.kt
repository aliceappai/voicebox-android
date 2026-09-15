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
     * Called with the recorder's anonymous session id — the id that messages recorded
     * without an account belong to. A host that later signs the user in can send it to its
     * backend to claim those messages.
     *
     * The recorder writes the id lazily, so this can arrive at page load, on submit, or not
     * at all. It is reported once per id per presentation; a later presentation reports the
     * same id again, so treat it as "the latest known id", not as an event.
     *
     * Mirrors iOS `VoiceboxDelegate.voicebox(_:didResolveAnonymousSessionId:)`.
     */
    fun onAnonymousSessionId(voiceboxView: VoiceboxView, sessionId: String) {}
}
