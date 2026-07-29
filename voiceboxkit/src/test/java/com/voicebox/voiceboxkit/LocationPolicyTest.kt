package com.voicebox.voiceboxkit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Truth-table coverage for [VoiceboxLocationPolicy] — the pure geolocation
 * decision logic used by [VoiceboxChromeClient].
 */
class LocationPolicyTest {

    @Test
    fun `grants WebView geolocation only when the app holds a location permission`() {
        assertTrue(VoiceboxLocationPolicy.shouldGrantWebViewGeolocation(appHoldsLocation = true))
        assertFalse(VoiceboxLocationPolicy.shouldGrantWebViewGeolocation(appHoldsLocation = false))
    }

    @Test
    fun `requests OS location only when the app does not already hold it`() {
        assertTrue(VoiceboxLocationPolicy.shouldRequestOsLocation(appHoldsLocation = false))
        assertFalse(VoiceboxLocationPolicy.shouldRequestOsLocation(appHoldsLocation = true))
    }
}
