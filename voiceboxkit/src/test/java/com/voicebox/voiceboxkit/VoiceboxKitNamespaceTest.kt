package com.voicebox.voiceboxkit

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Mirrors iOS `VoiceboxKitNamespaceTests`. */
class VoiceboxKitNamespaceTest {

    private var previousAutoGrantMic = false

    @Before
    fun setUp() {
        previousAutoGrantMic = VoiceboxKit.autoGrantMicPermission
    }

    @After
    fun tearDown() {
        VoiceboxKit.autoGrantMicPermission = previousAutoGrantMic
        VoiceboxKit.baseUrl = "https://vbx.to"
    }

    @Test
    fun `version is not empty`() {
        assertTrue(VoiceboxKit.VERSION.isNotEmpty())
    }

    @Test
    fun `version matches semver pattern`() {
        val semver = Regex("""^\d+\.\d+\.\d+$""")
        assertTrue(
            "Version '${VoiceboxKit.VERSION}' does not match X.Y.Z",
            semver.matches(VoiceboxKit.VERSION),
        )
    }

    @Test
    fun `baseUrl is https`() {
        assertTrue(VoiceboxKit.baseUrl.startsWith("https://"))
    }

    @Test
    fun `default autoGrantMicPermission is false`() {
        VoiceboxKit.autoGrantMicPermission = false
        assertFalse(VoiceboxKit.autoGrantMicPermission)
    }

    @Test
    fun `autoGrantMicPermission can be toggled`() {
        VoiceboxKit.autoGrantMicPermission = true
        assertTrue(VoiceboxKit.autoGrantMicPermission)

        VoiceboxKit.autoGrantMicPermission = false
        assertFalse(VoiceboxKit.autoGrantMicPermission)
    }
}
