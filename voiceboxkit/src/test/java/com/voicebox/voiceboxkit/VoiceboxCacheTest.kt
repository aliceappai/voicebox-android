package com.voicebox.voiceboxkit

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [VoiceboxCache] using Robolectric (SharedPreferences + WebView stubs).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceboxCacheTest {

    @Before
    fun setUp() {
        VoiceboxKit.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `hasCachedContent returns false for unknown handle`() {
        assertFalse(VoiceboxCache.shared.hasCachedContent("unknown-handle-xyz"))
    }

    @Test
    fun `preload is a no-op when init has not been called`() {
        // Simulate un-initialised SDK
        VoiceboxKit.applicationContext = null
        // Should not throw
        VoiceboxCache.shared.preload("any-handle")
        // Restore for other tests
        VoiceboxKit.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `validateCache returns false when no etag is stored`() {
        var result: Boolean? = null
        VoiceboxCache.shared.validateCache("no-etag-handle") { result = it }
        // validateCache calls onResult synchronously when no etag is stored
        assertFalse(result ?: true)
    }
}
