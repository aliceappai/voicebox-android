package com.voicebox.voiceboxkit

import android.net.Uri
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mirrors iOS `URLConstructionTests`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UrlBuilderTest {

    private var previousAutoCollect = true

    @Before
    fun setUp() {
        previousAutoCollect = VoiceboxKit.autoCollectAppContext
        // Disable auto-collect so tests only see user-provided params + UTM
        VoiceboxKit.autoCollectAppContext = false
        VoiceboxKit.applicationContext = null
        VoiceboxKit.baseUrl = "https://vbx.to"
    }

    @After
    fun tearDown() {
        VoiceboxKit.autoCollectAppContext = previousAutoCollect
    }

    @Test
    fun `basic URL has correct scheme, host, and path`() {
        val vb = VoiceboxView(handle = "alice-feedback")
        val uri = vb.buildUrl()

        assertEquals("https", uri.scheme)
        assertEquals("vbx.to", uri.host)
        assertEquals("/@alice-feedback", uri.path)
    }

    @Test
    fun `UTM params are always present`() {
        val vb = VoiceboxView(handle = "test")
        val uri = vb.buildUrl()

        assertEquals("voiceboxkit", uri.getQueryParameter("utm_source"))
        assertEquals("android_sdk", uri.getQueryParameter("utm_medium"))
    }

    @Test
    fun `Overlay mode adds overlay=1`() {
        val vb = VoiceboxView(handle = "test")
        vb.presentationMode = VoiceboxPresentationMode.Overlay
        assertEquals("1", vb.buildUrl().getQueryParameter("overlay"))
    }

    @Test
    fun `non-overlay mode omits overlay param`() {
        val vb = VoiceboxView(handle = "test")
        vb.presentationMode = VoiceboxPresentationMode.BottomSheet
        assertNull(vb.buildUrl().getQueryParameter("overlay"))
    }

    @Test
    fun `custom params are appended`() {
        val vb = VoiceboxView(
            handle = "alice-feedback",
            params = mapOf(
                "email" to "jane@example.com",
                "userID" to "usr_abc123",
            ),
        )
        val uri = vb.buildUrl()

        assertEquals("jane@example.com", uri.getQueryParameter("email"))
        assertEquals("usr_abc123", uri.getQueryParameter("userID"))
    }

    @Test
    fun `params with special characters are URL encoded`() {
        val vb = VoiceboxView(
            handle = "test",
            params = mapOf("prompt" to "How are you liking Alice?"),
        )
        val urlString = vb.buildUrl().toString()

        // No raw spaces in URL string
        assertFalse(urlString.contains(" "))

        // Value is recoverable
        val uri = Uri.parse(urlString)
        assertEquals("How are you liking Alice?", uri.getQueryParameter("prompt"))
    }

    @Test
    fun `location param is preserved correctly`() {
        val vb = VoiceboxView(
            handle = "test",
            params = mapOf("ll" to "47.6062,-122.3321"),
        )
        val uri = vb.buildUrl()
        assertEquals("47.6062,-122.3321", uri.getQueryParameter("ll"))
    }

    @Test
    fun `params are sorted alphabetically before UTM`() {
        val vb = VoiceboxView(
            handle = "test",
            params = mapOf(
                "zebra" to "z",
                "alpha" to "a",
                "middle" to "m",
            ),
        )
        val uri = vb.buildUrl()
        val queryString = uri.encodedQuery ?: ""
        val keys = queryString.split("&").map { it.substringBefore("=") }

        // User params sorted alphabetically, then UTM at the end
        assertEquals(listOf("alpha", "middle", "zebra", "utm_source", "utm_medium"), keys)
    }

    @Test
    fun `empty params yields only UTM params`() {
        val vb = VoiceboxView(handle = "test")
        val uri = vb.buildUrl()
        val keys = uri.queryParameterNames

        assertEquals(setOf("utm_source", "utm_medium"), keys)
    }

    @Test
    fun `ampersand and equals in param values are encoded`() {
        val vb = VoiceboxView(
            handle = "test",
            params = mapOf(
                "name" to "John & Jane",
                "tag" to "a=b",
            ),
        )
        val uri = vb.buildUrl()
        assertEquals("John & Jane", uri.getQueryParameter("name"))
        assertEquals("a=b", uri.getQueryParameter("tag"))
    }

    @Test
    fun `emoji in params does not break URL`() {
        val vb = VoiceboxView(
            handle = "test",
            params = mapOf("prompt" to "How's it going? 👋"),
        )
        val uri = vb.buildUrl()
        assertEquals("How's it going? 👋", uri.getQueryParameter("prompt"))
    }

    @Test
    fun `URL contains at-symbol path segment`() {
        val vb = VoiceboxView(handle = "my-handle")
        val uri = vb.buildUrl()
        assertTrue(uri.path?.contains("@my-handle") == true)
    }

    @Test
    fun `custom baseUrl is reflected in the built URL`() {
        VoiceboxKit.baseUrl = "https://vbxstaging.com"
        val vb = VoiceboxView(handle = "test")
        val uri = vb.buildUrl()
        assertEquals("vbxstaging.com", uri.host)
        VoiceboxKit.baseUrl = "https://vbx.to"
    }
}
