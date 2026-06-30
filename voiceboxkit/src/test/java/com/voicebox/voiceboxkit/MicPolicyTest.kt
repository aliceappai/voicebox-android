package com.voicebox.voiceboxkit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Truth-table coverage for [VoiceboxMicPolicy] — the pure mic-permission decision logic.
 */
class MicPolicyTest {

    // MARK: - shouldProactivelyRequest

    @Test
    fun `proactively requests only when auto-grant is on and permission not yet held`() {
        assertTrue(
            VoiceboxMicPolicy.shouldProactivelyRequest(appHoldsRecordAudio = false, autoGrant = true)
        )
    }

    @Test
    fun `does not request when auto-grant is off (host app owns the permission)`() {
        assertFalse(
            VoiceboxMicPolicy.shouldProactivelyRequest(appHoldsRecordAudio = false, autoGrant = false)
        )
    }

    @Test
    fun `does not request when permission is already held`() {
        assertFalse(
            VoiceboxMicPolicy.shouldProactivelyRequest(appHoldsRecordAudio = true, autoGrant = true)
        )
        assertFalse(
            VoiceboxMicPolicy.shouldProactivelyRequest(appHoldsRecordAudio = true, autoGrant = false)
        )
    }

    // MARK: - shouldGrantWebViewMic

    @Test
    fun `grants WebView mic only for an audio request when the app holds the permission`() {
        assertTrue(
            VoiceboxMicPolicy.shouldGrantWebViewMic(wantsAudio = true, appHoldsRecordAudio = true)
        )
    }

    @Test
    fun `denies WebView mic when the app does not hold the permission`() {
        assertFalse(
            VoiceboxMicPolicy.shouldGrantWebViewMic(wantsAudio = true, appHoldsRecordAudio = false)
        )
    }

    @Test
    fun `denies non-audio requests regardless of held permission`() {
        assertFalse(
            VoiceboxMicPolicy.shouldGrantWebViewMic(wantsAudio = false, appHoldsRecordAudio = true)
        )
        assertFalse(
            VoiceboxMicPolicy.shouldGrantWebViewMic(wantsAudio = false, appHoldsRecordAudio = false)
        )
    }
}
