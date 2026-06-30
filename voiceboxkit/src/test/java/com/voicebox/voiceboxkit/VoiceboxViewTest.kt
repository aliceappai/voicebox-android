package com.voicebox.voiceboxkit

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mirrors iOS `VoiceboxViewTests`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceboxViewTest {

    private var previousAutoCollect = true

    @Before
    fun setUp() {
        previousAutoCollect = VoiceboxKit.autoCollectAppContext
        VoiceboxKit.autoCollectAppContext = false
        VoiceboxKit.applicationContext = null
    }

    @After
    fun tearDown() {
        VoiceboxKit.autoCollectAppContext = previousAutoCollect
    }

    // MARK: - Default values

    @Test
    fun `default presentationMode is BottomSheet`() {
        val vb = VoiceboxView(handle = "test")
        assertEquals(VoiceboxPresentationMode.BottomSheet, vb.presentationMode)
    }

    @Test
    fun `default showCloseButton is true`() {
        val vb = VoiceboxView(handle = "test")
        assertTrue(vb.showCloseButton)
    }

    @Test
    fun `default listener is null`() {
        val vb = VoiceboxView(handle = "test")
        assertNull(vb.listener)
    }

    @Test
    fun `default autoGrantMicPermission instance override is null`() {
        val vb = VoiceboxView(handle = "test")
        assertNull(vb.autoGrantMicPermission)
    }

    @Test
    fun `default params is empty`() {
        val vb = VoiceboxView(handle = "test")
        assertTrue(vb.params.isEmpty())
    }

    // MARK: - Handle and params stored correctly

    @Test
    fun `handle is stored as provided`() {
        val vb = VoiceboxView(handle = "alice-feedback")
        assertEquals("alice-feedback", vb.handle)
    }

    @Test
    fun `params are stored as provided`() {
        val params = mapOf("email" to "test@example.com", "userID" to "123")
        val vb = VoiceboxView(handle = "test", params = params)
        assertEquals(params, vb.params)
    }

    // MARK: - Mic permission resolution

    @Test
    fun `effectiveAutoGrantMicPermission defaults to global when instance is null`() {
        val prev = VoiceboxKit.autoGrantMicPermission
        val vb = VoiceboxView(handle = "test")

        VoiceboxKit.autoGrantMicPermission = false
        assertFalse(vb.effectiveAutoGrantMicPermission)

        VoiceboxKit.autoGrantMicPermission = true
        assertTrue(vb.effectiveAutoGrantMicPermission)

        VoiceboxKit.autoGrantMicPermission = prev
    }

    @Test
    fun `effectiveAutoGrantMicPermission instance overrides global`() {
        val prev = VoiceboxKit.autoGrantMicPermission
        val vb = VoiceboxView(handle = "test")

        VoiceboxKit.autoGrantMicPermission = false
        vb.autoGrantMicPermission = true
        assertTrue(vb.effectiveAutoGrantMicPermission)

        VoiceboxKit.autoGrantMicPermission = true
        vb.autoGrantMicPermission = false
        assertFalse(vb.effectiveAutoGrantMicPermission)

        VoiceboxKit.autoGrantMicPermission = prev
    }

    // MARK: - URL construction edge cases

    @Test
    fun `URL contains @ path segment`() {
        val vb = VoiceboxView(handle = "my-handle")
        val uri = vb.buildUrl()
        assertTrue(uri.path?.contains("@my-handle") == true)
    }
}
