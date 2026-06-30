package com.voicebox.voiceboxkit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Compose-observable state holder for a Voicebox presentation.
 *
 * Set [isPresented] to `true` to show the sheet; the SDK resets it to `false`
 * automatically when the sheet is dismissed (mirrors iOS `@Binding<Bool>`).
 *
 * Create via [rememberVoiceboxState] inside a composable, or manually if you need
 * to hoist the state above the composition (e.g. in a ViewModel).
 *
 * Mirrors iOS usage of `@State var isPresented = false` + `.voicebox()` modifier.
 *
 * ```kotlin
 * val state = rememberVoiceboxState(handle = "my-handle")
 * Button(onClick = { state.isPresented = true }) { Text("Open Voicebox") }
 * VoiceboxEffect(state = state)
 * ```
 */
class VoiceboxState(
    val handle: String,
    val params: Map<String, String> = emptyMap(),
    val theme: VoiceboxTheme = VoiceboxTheme(),
) {
    /** Controls sheet visibility. Set to `true` to present, `false` to dismiss. */
    var isPresented: Boolean by mutableStateOf(false)

    /** Presentation mode. Default: [VoiceboxPresentationMode.BottomSheet]. */
    var presentationMode: VoiceboxPresentationMode = VoiceboxPresentationMode.BottomSheet

    /** Whether to show the built-in close button. Default: `true`. */
    var showCloseButton: Boolean = true

    /** Lifecycle listener for this presentation. */
    var listener: VoiceboxListener? = null

    /**
     * Per-instance mic permission override. `null` defers to
     * [VoiceboxKit.autoGrantMicPermission].
     */
    var autoGrantMicPermission: Boolean? = null

    /**
     * Converts this state to a [VoiceboxView] config for the fragment layer.
     *
     * The returned [VoiceboxView] wraps [listener] so that [onDismiss] also
     * resets [isPresented] to `false`, keeping Compose state in sync.
     */
    internal fun toVoiceboxView(): VoiceboxView {
        val vb = VoiceboxView(handle = handle, params = params, theme = theme)
        vb.presentationMode = presentationMode
        vb.showCloseButton = showCloseButton
        vb.autoGrantMicPermission = autoGrantMicPermission
        vb.listener = object : VoiceboxListener {
            override fun onRecordingComplete(voiceboxView: VoiceboxView) {
                listener?.onRecordingComplete(voiceboxView)
            }
            override fun onMessageSubmitted(voiceboxView: VoiceboxView) {
                listener?.onMessageSubmitted(voiceboxView)
            }
            override fun onDismiss(voiceboxView: VoiceboxView) {
                isPresented = false   // sync Compose state back when sheet closes
                listener?.onDismiss(voiceboxView)
            }
            override fun onFailure(voiceboxView: VoiceboxView, error: Exception) {
                listener?.onFailure(voiceboxView, error)
            }
        }
        return vb
    }
}
