package com.voicebox.voiceboxkit

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mirrors iOS `AppContextTests`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppContextTest {

    private val context: Application by lazy {
        ApplicationProvider.getApplicationContext()
    }

    private var previousAutoCollect = true

    @Before
    fun setUp() {
        previousAutoCollect = VoiceboxKit.autoCollectAppContext
    }

    @After
    fun tearDown() {
        VoiceboxKit.autoCollectAppContext = previousAutoCollect
    }

    // MARK: - Raw context collection

    @Test
    fun `collect returns non-empty map`() {
        val ctx = VoiceboxAppContext.collect(context)
        assertTrue(ctx.isNotEmpty())
    }

    @Test
    fun `collect includes platform = android`() {
        val ctx = VoiceboxAppContext.collect(context)
        assertEquals("android", ctx["platform"])
    }

    @Test
    fun `collect includes sdkVersion`() {
        val ctx = VoiceboxAppContext.collect(context)
        assertEquals(VoiceboxKit.VERSION, ctx["sdkVersion"])
    }

    @Test
    fun `collect includes non-empty osVersion`() {
        val ctx = VoiceboxAppContext.collect(context)
        assertNotNull(ctx["osVersion"])
        assertTrue(ctx["osVersion"]!!.isNotEmpty())
    }

    @Test
    fun `collect includes non-empty deviceModel`() {
        val ctx = VoiceboxAppContext.collect(context)
        assertNotNull(ctx["deviceModel"])
        assertTrue(ctx["deviceModel"]!!.isNotEmpty())
    }

    @Test
    fun `collect includes non-empty locale`() {
        val ctx = VoiceboxAppContext.collect(context)
        assertNotNull(ctx["locale"])
        assertTrue(ctx["locale"]!!.isNotEmpty())
    }

    @Test
    fun `collect does not include privacy sensitive fields`() {
        val ctx = VoiceboxAppContext.collect(context)

        // Advertising ID / device identifiers — must never be auto-collected
        assertNull(ctx["advertisingId"])
        assertNull(ctx["gaid"])
        assertNull(ctx["androidId"])
        assertNull(ctx["deviceName"])
    }

    // MARK: - Integration with buildUrl

    @Test
    fun `buildUrl includes auto context when enabled`() {
        VoiceboxKit.autoCollectAppContext = true
        VoiceboxKit.applicationContext = context

        val vb = VoiceboxView(handle = "test")
        val uri = vb.buildUrl()
        val keys = uri.queryParameterNames

        assertTrue(keys.contains("platform"))
        assertTrue(keys.contains("sdkVersion"))
        assertTrue(keys.contains("osVersion"))
        assertTrue(keys.contains("deviceModel"))
        assertTrue(keys.contains("locale"))
        assertTrue(keys.contains("utm_source"))
        assertTrue(keys.contains("utm_medium"))
    }

    @Test
    fun `buildUrl excludes auto context when disabled`() {
        VoiceboxKit.autoCollectAppContext = false

        val vb = VoiceboxView(handle = "test")
        val uri = vb.buildUrl()
        val keys = uri.queryParameterNames

        assertFalse(keys.contains("platform"))
        assertFalse(keys.contains("sdkVersion"))
        assertFalse(keys.contains("osVersion"))
        assertFalse(keys.contains("deviceModel"))
        assertFalse(keys.contains("locale"))

        // UTM params still present
        assertTrue(keys.contains("utm_source"))
        assertTrue(keys.contains("utm_medium"))
    }

    @Test
    fun `app-provided param overrides auto-collected`() {
        VoiceboxKit.autoCollectAppContext = true
        VoiceboxKit.applicationContext = context

        val vb = VoiceboxView(
            handle = "test",
            params = mapOf("platform" to "android-custom-build"),
        )
        val uri = vb.buildUrl()

        // App-provided value wins
        assertEquals("android-custom-build", uri.getQueryParameter("platform"))

        // Exactly one `platform` entry — no duplicates
        assertEquals(1, uri.queryParameterNames.count { it == "platform" })
    }

    @Test
    fun `autoCollectAppContext flag is writable`() {
        VoiceboxKit.autoCollectAppContext = true
        assertTrue(VoiceboxKit.autoCollectAppContext)

        VoiceboxKit.autoCollectAppContext = false
        assertFalse(VoiceboxKit.autoCollectAppContext)
    }

    @Test
    fun `collect without context omits package-level fields but keeps device fields`() {
        val ctx = VoiceboxAppContext.collect(context = null)

        // Device fields — no context required
        assertNotNull(ctx["platform"])
        assertNotNull(ctx["osVersion"])
        assertNotNull(ctx["deviceModel"])
        assertNotNull(ctx["locale"])
        assertNotNull(ctx["sdkVersion"])

        // Package-level fields — require context, should be absent
        assertNull(ctx["packageName"])
        assertNull(ctx["appName"])
        assertNull(ctx["appVersion"])
        assertNull(ctx["buildNumber"])
    }
}
