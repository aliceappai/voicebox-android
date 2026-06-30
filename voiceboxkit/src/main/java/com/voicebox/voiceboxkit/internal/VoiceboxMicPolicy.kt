package com.voicebox.voiceboxkit

/**
 * Pure decision logic for microphone handling — no Android dependencies, so it
 * can be unit-tested without Robolectric or mocks.
 *
 * Android's [android.webkit.WebChromeClient.onPermissionRequest] only supports
 * `grant()`/`deny()` — there is no `prompt` option like iOS's WebKit
 * `decisionHandler(.prompt)`. So the `autoGrantMicPermission` flag controls
 * whether the SDK *proactively requests* the OS `RECORD_AUDIO` permission, while
 * the WebView grant itself is always gated on the app actually holding it.
 *
 * Resulting semantics (decision: "the host app owns the permission"):
 * - `autoGrant == true`  → SDK proactively requests `RECORD_AUDIO` when the sheet
 *   opens, then grants the WebView mic once the app holds it.
 * - `autoGrant == false` (default) → SDK does **not** request `RECORD_AUDIO`; it
 *   grants the WebView mic only if the app already holds it, otherwise denies.
 *   The host app is responsible for obtaining `RECORD_AUDIO`.
 */
internal object VoiceboxMicPolicy {

    /**
     * Whether the SDK should proactively launch the OS `RECORD_AUDIO` dialog
     * before loading the recorder.
     *
     * Only when the app does not already hold the permission *and* the caller
     * opted into auto-grant — otherwise the host app owns the request.
     */
    fun shouldProactivelyRequest(appHoldsRecordAudio: Boolean, autoGrant: Boolean): Boolean =
        !appHoldsRecordAudio && autoGrant

    /**
     * Whether to grant the WebView's `getUserMedia` audio-capture request.
     *
     * Granted only for an audio request, and only when the app already holds
     * `RECORD_AUDIO` — the SDK never escalates permission on the app's behalf.
     */
    fun shouldGrantWebViewMic(wantsAudio: Boolean, appHoldsRecordAudio: Boolean): Boolean =
        wantsAudio && appHoldsRecordAudio
}
