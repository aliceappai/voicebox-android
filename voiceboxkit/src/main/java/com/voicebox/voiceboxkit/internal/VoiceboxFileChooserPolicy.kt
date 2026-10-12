package com.voicebox.voiceboxkit

/**
 * Pure decision logic for the recorder's file input — no Android dependencies, so it
 * can be unit-tested without Robolectric or mocks.
 */
internal object VoiceboxFileChooserPolicy {

    /**
     * The MIME types a file input takes, from its `accept` list. Entries are MIME types
     * (`image/png`, or a wildcard subtype) or extensions (`.pdf`); [extensionToMime] resolves the latter, and an
     * extension it does not know is dropped. Empty means "anything".
     */
    fun acceptedMimeTypes(
        acceptTypes: Array<String>?,
        extensionToMime: (String) -> String?,
    ): List<String> =
        acceptTypes.orEmpty()
            .flatMap { it.split(',') }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .mapNotNull { if (it.startsWith(".")) extensionToMime(it.removePrefix(".")) else it }
            .distinct()

    /** Whether a photo is something this input would take. */
    fun acceptsImages(mimeTypes: List<String>): Boolean =
        mimeTypes.isEmpty() || mimeTypes.any { it == "*/*" || it.startsWith("image/") }

    /**
     * Whether to offer "take a photo" next to the file picker.
     *
     * Android refuses the camera intent to an app that declares `CAMERA` without holding
     * it, and the SDK never requests a permission on the app's behalf. An app that does
     * not declare `CAMERA` needs no permission to hand off to the camera app.
     */
    fun shouldOfferCamera(
        acceptsImages: Boolean,
        hostDeclaresCamera: Boolean,
        hostHoldsCamera: Boolean,
    ): Boolean = acceptsImages && (!hostDeclaresCamera || hostHoldsCamera)
}
