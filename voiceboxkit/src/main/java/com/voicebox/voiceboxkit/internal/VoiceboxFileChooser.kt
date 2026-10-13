package com.voicebox.voiceboxkit

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Answers the recorder page's `<input type="file">`: a WebView opens nothing on its own,
 * so without this a tap on "add file" does nothing. Offers the system file picker and,
 * when the input takes images, the camera.
 *
 * Every request is resolved **exactly once** — with the chosen files, or with `null`
 * when the picker is cancelled, cannot be shown, is replaced by a newer request, or its
 * host goes away. A request left unresolved leaves the page's input dead; one resolved
 * twice makes WebView throw.
 *
 * [launch] starts the picker for a result, which the host hands back to [onResult]. It
 * must throw when it cannot.
 */
internal class VoiceboxFileChooser(
    private val context: Context,
    private val launch: (Intent) -> Unit,
) {
    private var pending: ValueCallback<Array<Uri>>? = null

    /** Where the camera was asked to save a photo for the request in flight. */
    private var cameraOutput: File? = null

    /** Call from `WebChromeClient.onShowFileChooser` and return its result (always `true`). */
    fun onShowFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        cancelPending()
        pending = callback
        val launched = runCatching { launch(chooserIntent(params)) }.isSuccess
        if (!launched) cancelPending()
        // The callback is ours to resolve. Returning false after resolving it makes
        // WebView resolve it again: "Duplicate showFileChooser result".
        return true
    }

    /** Resolve the request in flight with the picker's `(resultCode, data)`. */
    fun onResult(resultCode: Int, data: Intent?) {
        val ok = resultCode == Activity.RESULT_OK
        val picked = if (ok) pickedUris(data) else emptyList()
        // A camera result carries no uri of its own: the photo is in the file we lent.
        val photo = cameraOutput?.takeIf { ok && picked.isEmpty() && it.length() > 0 }
        if (photo == null) cameraOutput?.delete()
        cameraOutput = null
        resolve(
            when {
                picked.isNotEmpty() -> picked.toTypedArray()
                photo != null -> arrayOf(uriFor(photo))
                else -> null
            },
        )
    }

    /** Resolve the request in flight as cancelled. No-op when there is none. */
    fun cancelPending() {
        cameraOutput?.delete()
        cameraOutput = null
        resolve(null)
    }

    /** The host is going away: cancel, and drop photos the page can no longer upload. */
    fun dispose() {
        cancelPending()
        captureDir().deleteRecursively()
    }

    // Clear before invoking, so a callback is never resolved twice even if invoking it
    // throws or re-enters this class.
    private fun resolve(value: Array<Uri>?) {
        val callback = pending ?: return
        pending = null
        callback.onReceiveValue(value)
    }

    private fun chooserIntent(params: WebChromeClient.FileChooserParams): Intent {
        val mimeTypes = VoiceboxFileChooserPolicy.acceptedMimeTypes(params.acceptTypes) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
        }
        val camera = cameraIntent(mimeTypes)
        // `<input capture>` asks for the camera outright.
        if (camera != null && params.isCaptureEnabled) return camera

        // Built here rather than with params.createIntent(), which keeps only the first
        // accept type — an input taking images, audio and PDFs would offer images alone.
        val picker = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeTypes.singleOrNull() ?: "*/*"
            if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }
        return Intent.createChooser(picker, null).apply {
            if (camera != null) putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
        }
    }

    /** The camera intent saving into a file of ours, or null when it should not be offered. */
    private fun cameraIntent(mimeTypes: List<String>): Intent? {
        val offer = VoiceboxFileChooserPolicy.shouldOfferCamera(
            acceptsImages = VoiceboxFileChooserPolicy.acceptsImages(mimeTypes),
            hostDeclaresCamera = hostDeclaresCamera(),
            hostHoldsCamera = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
        if (!offer) return null
        // A host that stripped the provider from its manifest just gets no camera option.
        return runCatching {
            val file = File(captureDir().apply { mkdirs() }, "photo_${System.currentTimeMillis()}.jpg")
            val uri = uriFor(file)
            cameraOutput = file
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                // The camera app writes our private file through this grant.
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrNull()
    }

    private fun pickedUris(data: Intent?): List<Uri> {
        val clip = data?.clipData
        if (clip != null && clip.itemCount > 0) {
            return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        }
        return listOfNotNull(data?.data)
    }

    private fun hostDeclaresCamera(): Boolean = runCatching {
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.contains(Manifest.permission.CAMERA) == true
    }.getOrDefault(false)

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)

    private fun captureDir(): File = File(context.cacheDir, CAPTURE_DIR)

    private companion object {
        /** Matches the provider in the SDK manifest and `res/xml/voiceboxkit_file_paths.xml`. */
        const val AUTHORITY_SUFFIX = ".voiceboxkit.fileprovider"
        const val CAPTURE_DIR = "voiceboxkit_capture"
    }
}
