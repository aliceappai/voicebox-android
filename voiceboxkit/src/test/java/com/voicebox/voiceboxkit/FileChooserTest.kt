package com.voicebox.voiceboxkit

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for [VoiceboxFileChooser]: which picker it opens for a file input, and that
 * every request is answered exactly once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileChooserTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val launched = mutableListOf<Intent>()
    private val chooser = VoiceboxFileChooser(context) { launched += it }

    // FileProvider remembers each authority's folders for the life of the process, while
    // Robolectric gives every test a new cacheDir — so forget them between tests.
    @Before
    fun forgetFileProviderRoots() {
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }

    /** Records every value WebView would receive for one request. */
    private class Callback : ValueCallback<Array<Uri>> {
        val results = mutableListOf<Array<Uri>?>()
        override fun onReceiveValue(value: Array<Uri>?) {
            results += value
        }
    }

    private fun params(
        vararg accept: String,
        multiple: Boolean = false,
        capture: Boolean = false,
    ) = object : WebChromeClient.FileChooserParams() {
        override fun getMode() = if (multiple) MODE_OPEN_MULTIPLE else MODE_OPEN
        override fun getAcceptTypes() = arrayOf(*accept)
        override fun isCaptureEnabled() = capture
        override fun getTitle(): CharSequence? = null
        override fun getFilenameHint(): String? = null
        override fun createIntent() = Intent(Intent.ACTION_GET_CONTENT)
    }

    private fun picker(intent: Intent): Intent = intent.getParcelableExtra(Intent.EXTRA_INTENT)!!

    private fun cameraOption(intent: Intent): Intent? =
        intent.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS)?.firstOrNull() as Intent?

    /** Stands in for the camera app saving the photo it was asked for. */
    private fun savePhotoFor(camera: Intent): Uri {
        val output: Uri = camera.getParcelableExtra(MediaStore.EXTRA_OUTPUT)!!
        File(File(context.cacheDir, "voiceboxkit_capture"), output.lastPathSegment!!).writeBytes(byteArrayOf(1, 2, 3))
        return output
    }

    // MARK: - Which picker

    @Test
    fun `an input taking several types opens a picker for all of them`() {
        chooser.onShowFileChooser(Callback(), params("image/*", "audio/*", "application/pdf"))

        val intent = launched.single()
        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        assertEquals(Intent.ACTION_GET_CONTENT, picker(intent).action)
        assertEquals("*/*", picker(intent).type)
        assertArrayEquals(
            arrayOf("image/*", "audio/*", "application/pdf"),
            picker(intent).getStringArrayExtra(Intent.EXTRA_MIME_TYPES),
        )
        assertFalse(picker(intent).getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
    }

    @Test
    fun `an input taking one type opens a picker for just that type`() {
        chooser.onShowFileChooser(Callback(), params("application/pdf"))

        assertEquals("application/pdf", picker(launched.single()).type)
        assertNull(picker(launched.single()).getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
    }

    @Test
    fun `a multiple input lets several files be picked`() {
        chooser.onShowFileChooser(Callback(), params("image/*", multiple = true))

        assertTrue(picker(launched.single()).getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
    }

    @Test
    fun `an input that takes images also offers the camera`() {
        chooser.onShowFileChooser(Callback(), params("image/*", "application/pdf"))

        val camera = cameraOption(launched.single())
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, camera?.action)
        val output: Uri? = camera?.getParcelableExtra(MediaStore.EXTRA_OUTPUT)
        assertEquals("${context.packageName}.voiceboxkit.fileprovider", output?.authority)
    }

    @Test
    fun `an input that takes no images offers no camera`() {
        chooser.onShowFileChooser(Callback(), params("audio/*"))

        assertNull(cameraOption(launched.single()))
    }

    @Test
    fun `a capture input opens the camera outright`() {
        chooser.onShowFileChooser(Callback(), params("image/*", capture = true))

        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, launched.single().action)
    }

    // MARK: - Answering exactly once

    @Test
    fun `a picked file is handed to the page`() {
        val callback = Callback()
        val file = Uri.parse("content://docs/1")
        chooser.onShowFileChooser(callback, params("image/*"))

        chooser.onResult(Activity.RESULT_OK, Intent().setData(file))

        assertArrayEquals(arrayOf(file), callback.results.single())
    }

    @Test
    fun `several picked files are all handed to the page`() {
        val callback = Callback()
        val first = Uri.parse("content://docs/1")
        val second = Uri.parse("content://docs/2")
        chooser.onShowFileChooser(callback, params("image/*", multiple = true))

        val data = Intent().apply {
            clipData = ClipData.newRawUri(null, first).apply { addItem(ClipData.Item(second)) }
        }
        chooser.onResult(Activity.RESULT_OK, data)

        assertArrayEquals(arrayOf(first, second), callback.results.single())
    }

    @Test
    fun `a photo from the camera is handed to the page`() {
        val callback = Callback()
        chooser.onShowFileChooser(callback, params("image/*"))
        val photo = savePhotoFor(cameraOption(launched.single())!!)

        // A camera result carries no uri of its own.
        chooser.onResult(Activity.RESULT_OK, null)

        assertArrayEquals(arrayOf(photo), callback.results.single())
    }

    @Test
    fun `backing out of the picker answers with nothing`() {
        val callback = Callback()
        chooser.onShowFileChooser(callback, params("image/*"))

        chooser.onResult(Activity.RESULT_CANCELED, null)

        assertEquals(1, callback.results.size)
        assertNull(callback.results.single())
    }

    @Test
    fun `a result with no file and no photo answers with nothing`() {
        val callback = Callback()
        chooser.onShowFileChooser(callback, params("image/*"))

        chooser.onResult(Activity.RESULT_OK, null)

        assertNull(callback.results.single())
    }

    @Test
    fun `a newer request cancels the one still open`() {
        val first = Callback()
        val second = Callback()
        chooser.onShowFileChooser(first, params("image/*"))

        chooser.onShowFileChooser(second, params("image/*"))

        assertNull(first.results.single())
        assertTrue(second.results.isEmpty())
    }

    @Test
    fun `a picker that cannot be shown answers with nothing, and still claims the request`() {
        val callback = Callback()
        val failing = VoiceboxFileChooser(context) { error("no activity to handle it") }

        val handled = failing.onShowFileChooser(callback, params("image/*"))

        assertTrue(handled)
        assertNull(callback.results.single())
    }

    @Test
    fun `a request is never answered twice`() {
        val callback = Callback()
        chooser.onShowFileChooser(callback, params("image/*"))

        chooser.onResult(Activity.RESULT_CANCELED, null)
        chooser.onResult(Activity.RESULT_OK, Intent().setData(Uri.parse("content://docs/1")))
        chooser.cancelPending()

        assertEquals(1, callback.results.size)
    }

    @Test
    fun `dispose cancels the open request and drops saved photos`() {
        val callback = Callback()
        chooser.onShowFileChooser(callback, params("image/*"))
        savePhotoFor(cameraOption(launched.single())!!)
        assertNotNull(File(context.cacheDir, "voiceboxkit_capture").listFiles()?.firstOrNull())

        chooser.dispose()

        assertNull(callback.results.single())
        assertFalse(File(context.cacheDir, "voiceboxkit_capture").exists())
    }
}
