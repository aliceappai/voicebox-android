package com.voicebox.voiceboxkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Truth-table coverage for [VoiceboxFileChooserPolicy] — the pure file-input decision logic.
 */
class FileChooserPolicyTest {

    private val knownExtensions = mapOf("pdf" to "application/pdf", "jpg" to "image/jpeg")

    private fun mimeTypes(vararg accept: String): List<String> =
        VoiceboxFileChooserPolicy.acceptedMimeTypes(arrayOf(*accept)) { knownExtensions[it] }

    // MARK: - acceptedMimeTypes

    @Test
    fun `accept types pass through as MIME types, in order`() {
        assertEquals(
            listOf("image/*", "audio/*", "application/pdf"),
            mimeTypes("image/*", "audio/*", "application/pdf"),
        )
    }

    @Test
    fun `a comma-joined entry is split, trimmed and lower-cased`() {
        assertEquals(listOf("image/*", "audio/*"), mimeTypes("image/*, AUDIO/* ,"))
    }

    @Test
    fun `extensions resolve to MIME types and unknown ones are dropped`() {
        assertEquals(listOf("application/pdf", "image/jpeg"), mimeTypes(".pdf", ".xyz", ".JPG"))
    }

    @Test
    fun `duplicates collapse`() {
        assertEquals(listOf("application/pdf"), mimeTypes(".pdf", "application/pdf"))
    }

    @Test
    fun `no accept list means anything`() {
        assertEquals(emptyList<String>(), VoiceboxFileChooserPolicy.acceptedMimeTypes(null) { null })
        assertEquals(emptyList<String>(), mimeTypes(""))
    }

    // MARK: - acceptsImages

    @Test
    fun `an input that takes anything takes a photo`() {
        assertTrue(VoiceboxFileChooserPolicy.acceptsImages(emptyList()))
        assertTrue(VoiceboxFileChooserPolicy.acceptsImages(listOf("*/*")))
    }

    @Test
    fun `an input that lists an image type takes a photo`() {
        assertTrue(VoiceboxFileChooserPolicy.acceptsImages(listOf("audio/*", "image/png")))
    }

    @Test
    fun `an input with no image type does not take a photo`() {
        assertFalse(VoiceboxFileChooserPolicy.acceptsImages(listOf("audio/*", "application/pdf")))
    }

    // MARK: - shouldOfferCamera

    @Test
    fun `camera is offered when the host does not declare CAMERA`() {
        assertTrue(
            VoiceboxFileChooserPolicy.shouldOfferCamera(
                acceptsImages = true, hostDeclaresCamera = false, hostHoldsCamera = false,
            )
        )
    }

    @Test
    fun `camera is offered when the host declares CAMERA and holds it`() {
        assertTrue(
            VoiceboxFileChooserPolicy.shouldOfferCamera(
                acceptsImages = true, hostDeclaresCamera = true, hostHoldsCamera = true,
            )
        )
    }

    @Test
    fun `camera is not offered when the host declares CAMERA without holding it`() {
        assertFalse(
            VoiceboxFileChooserPolicy.shouldOfferCamera(
                acceptsImages = true, hostDeclaresCamera = true, hostHoldsCamera = false,
            )
        )
    }

    @Test
    fun `camera is never offered to an input that takes no images`() {
        assertFalse(
            VoiceboxFileChooserPolicy.shouldOfferCamera(
                acceptsImages = false, hostDeclaresCamera = false, hostHoldsCamera = true,
            )
        )
    }
}
