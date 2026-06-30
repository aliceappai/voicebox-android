package com.voicebox.voiceboxkit

import android.util.Log

/**
 * Thin logging wrapper gated by [VoiceboxKit.debugLogging].
 *
 * All SDK diagnostic logging goes through here so production builds stay
 * silent by default. Errors that indicate a real failure still use [Log.e]
 * directly at call sites where they matter; this helper covers the verbose
 * diagnostic stream (navigation, lifecycle, permission grants, JS console).
 */
internal object VoiceboxLog {
    private const val TAG = "VoiceboxKit"

    fun d(message: String) {
        if (VoiceboxKit.debugLogging) Log.d(TAG, message)
    }

    fun d(tag: String, message: String) {
        if (VoiceboxKit.debugLogging) Log.d(tag, message)
    }

    fun println(priority: Int, tag: String, message: String) {
        if (VoiceboxKit.debugLogging) Log.println(priority, tag, message)
    }
}
