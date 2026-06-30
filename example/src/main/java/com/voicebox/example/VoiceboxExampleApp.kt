package com.voicebox.example

import android.app.Application
import com.voicebox.voiceboxkit.VoiceboxKit

class VoiceboxExampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialise the SDK with application context — required before any
        // preload() or present() call. In production apps do this in Application.onCreate().
        VoiceboxKit.baseUrl = "https://vbxstaging.com"
        // Verbose SDK logging under the `VoiceboxKit` / `VoiceboxKit/JS` logcat tags.
        // Handy during integration; leave off (the default) in production.
        VoiceboxKit.debugLogging = true
        // Let the SDK proactively request RECORD_AUDIO when the sheet opens.
        // In a production app that already manages mic permission separately, leave
        // this false (the default) and request RECORD_AUDIO before presenting.
        VoiceboxKit.autoGrantMicPermission = true
        VoiceboxKit.init(this)

        VoiceboxKit.preload(handle = "p-test")
    }
}
