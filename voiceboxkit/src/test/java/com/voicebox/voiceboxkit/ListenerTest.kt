package com.voicebox.voiceboxkit

import org.junit.Assert.*
import org.junit.Test

/** Mirrors iOS `DelegateTests`. */
class ListenerTest {

    // Test helper that records which listener methods were called.
    private class MockListener : VoiceboxListener {
        var recordingCompleteCalled = false
        var messageSubmittedCalled = false
        var dismissCalled = false
        var failureCalled = false
        var lastError: Exception? = null

        override fun onRecordingComplete(voiceboxView: VoiceboxView) {
            recordingCompleteCalled = true
        }
        override fun onMessageSubmitted(voiceboxView: VoiceboxView) {
            messageSubmittedCalled = true
        }
        override fun onDismiss(voiceboxView: VoiceboxView) {
            dismissCalled = true
        }
        override fun onFailure(voiceboxView: VoiceboxView, error: Exception) {
            failureCalled = true
            lastError = error
        }
    }

    // Partial implementation — verifies default empty impls compile and don't crash.
    private class PartialListener : VoiceboxListener {
        var dismissCalled = false
        override fun onDismiss(voiceboxView: VoiceboxView) { dismissCalled = true }
        // onRecordingComplete, onMessageSubmitted, onFailure use default empty impls
    }

    @Test
    fun `listener assignment and retrieval`() {
        val vb = VoiceboxView(handle = "test")
        val listener = MockListener()
        vb.listener = listener
        assertNotNull(vb.listener)
    }

    @Test
    fun `listener receives all callbacks`() {
        val vb = VoiceboxView(handle = "test")
        val listener = MockListener()
        vb.listener = listener

        listener.onRecordingComplete(vb)
        assertTrue(listener.recordingCompleteCalled)

        listener.onMessageSubmitted(vb)
        assertTrue(listener.messageSubmittedCalled)

        listener.onDismiss(vb)
        assertTrue(listener.dismissCalled)

        val error = Exception("test error")
        listener.onFailure(vb, error)
        assertTrue(listener.failureCalled)
        assertEquals("test error", listener.lastError?.message)
    }

    @Test
    fun `partial listener does not crash on unimplemented methods`() {
        val vb = VoiceboxView(handle = "test")
        val listener = PartialListener()
        vb.listener = listener

        // These use default empty implementations — must not throw
        listener.onRecordingComplete(vb)
        listener.onMessageSubmitted(vb)
        listener.onFailure(vb, Exception("oops"))

        // Implemented method works
        listener.onDismiss(vb)
        assertTrue(listener.dismissCalled)
    }

    @Test
    fun `listener can be set to null`() {
        val vb = VoiceboxView(handle = "test")
        vb.listener = MockListener()
        vb.listener = null
        assertNull(vb.listener)
    }
}
