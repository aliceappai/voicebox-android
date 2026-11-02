package com.voicebox.voiceboxkit

import android.os.Bundle
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The recorder survives its host Activity being recreated, and a recorder restored after process
 * death closes instead of crashing the host. Both used to throw
 * `UninitializedPropertyAccessException` from `onCreateView`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecreationTest {

    private class RecordingListener : VoiceboxListener {
        var dismissed = 0
        override fun onDismiss(voiceboxView: VoiceboxView) {
            dismissed += 1
        }
    }

    @Before
    fun setUp() {
        VoiceboxKit.init(ApplicationProvider.getApplicationContext())
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun launch(): ActivityController<FragmentActivity> =
        Robolectric.buildActivity(FragmentActivity::class.java).setup()

    private fun recorder(controller: ActivityController<FragmentActivity>): VoiceboxBottomSheetFragment? =
        controller.get().supportFragmentManager.findFragmentByTag("VoiceboxKit") as VoiceboxBottomSheetFragment?

    private fun present(
        controller: ActivityController<FragmentActivity>,
        mode: VoiceboxPresentationMode = VoiceboxPresentationMode.BottomSheet,
    ): VoiceboxView {
        val view = VoiceboxView(handle = "test").apply { presentationMode = mode }
        view.present(controller.get())
        idleMain()
        assertNotNull("the recorder should be showing", recorder(controller)?.view)
        return view
    }

    // MARK: - Config change (rotation, dark mode, font size, locale, multi-window)

    @Test
    fun `a recreated bottom sheet keeps its config and renders again`() {
        val controller = launch()
        val view = present(controller)
        val before = recorder(controller)

        controller.recreate()
        idleMain()

        val after = recorder(controller)
        assertNotNull(after)
        assertNotSame(before, after)
        assertSame(view, after!!.voiceboxConfig)
        assertNotNull(after.view)
    }

    @Test
    fun `a recreated floating card keeps its config and renders again`() {
        val controller = launch()
        val view = present(controller, VoiceboxPresentationMode.FloatingCard())

        controller.recreate()
        idleMain()

        val after = recorder(controller)
        assertSame(view, after?.voiceboxConfig)
        assertNotNull(after?.view)
    }

    @Test
    fun `the recorder survives being recreated more than once`() {
        val controller = launch()
        val view = present(controller)

        controller.recreate()
        idleMain()
        controller.recreate()
        idleMain()

        assertSame(view, recorder(controller)?.voiceboxConfig)
    }

    @Test
    fun `the host's listener is left as the host set it`() {
        // The fragment used to write its auto-dismiss wrapper into VoiceboxView.listener, so the
        // host's object held a reference to the fragment, and a recreated fragment wrapped the old
        // one's wrapper — calling the dead fragment's dismiss on submit.
        val controller = launch()
        val listener = RecordingListener()
        val view = VoiceboxView(handle = "test").apply { this.listener = listener }
        view.present(controller.get())
        idleMain()

        controller.recreate()
        idleMain()

        assertSame(listener, view.listener)
    }

    @Test
    fun `dismissing after a config change tells the host once and closes the recorder`() {
        val controller = launch()
        val listener = RecordingListener()
        val view = present(controller, VoiceboxPresentationMode.FloatingCard())
        view.listener = listener

        controller.recreate()
        idleMain()
        recorder(controller)!!.dismiss()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertEquals(1, listener.dismissed)
        assertNull(recorder(controller))
    }

    // MARK: - Process death

    @Test
    fun `a recorder restored after process death closes instead of crashing`() {
        val controller = launch()
        val listener = RecordingListener()
        present(controller).listener = listener
        val savedState = Bundle()
        controller.pause().stop().saveInstanceState(savedState)

        // A new Activity from the saved state, without the old one's ViewModels: what Android
        // does when it brings the app back after killing the process.
        val restored = Robolectric.buildActivity(FragmentActivity::class.java).setup(savedState)
        idleMain()

        assertNull(recorder(restored))
        // Nobody to tell: the listener belonged to the process that died.
        assertEquals(0, listener.dismissed)
    }
}
