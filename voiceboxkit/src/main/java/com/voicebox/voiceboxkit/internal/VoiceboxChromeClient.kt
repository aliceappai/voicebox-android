package com.voicebox.voiceboxkit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import androidx.core.content.ContextCompat

internal class VoiceboxChromeClient(
    private val context: Context,
    private val voiceboxView: VoiceboxView,
) : WebChromeClient() {

    override fun onPermissionRequest(request: PermissionRequest) {
        // Android WebChromeClient has no "prompt" option — only grant or deny. Unlike iOS
        // (which can delegate to WebKit's per-origin dialog), we must decide outright. The
        // SDK never escalates permission on the app's behalf: audio capture is granted only
        // when the app already holds RECORD_AUDIO. The autoGrantMicPermission flag governs
        // whether the SDK proactively *requests* that permission (see VoiceboxBottomSheetFragment),
        // not the grant here.
        val wantsAudio = PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources
        val appHasMic = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        VoiceboxLog.d("onPermissionRequest: origin=${request.origin}  wantsAudio=$wantsAudio  appHasMic=$appHasMic")

        if (VoiceboxMicPolicy.shouldGrantWebViewMic(wantsAudio, appHasMic)) {
            VoiceboxLog.d("Granting RESOURCE_AUDIO_CAPTURE")
            request.grant(request.resources)
            return
        }
        Log.w("VoiceboxKit", "Denying permission request (wantsAudio=$wantsAudio appHasMic=$appHasMic)")
        request.deny()
    }

    override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
        if (!VoiceboxKit.debugLogging) return true
        val priority = when (msg.messageLevel()) {
            ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
            ConsoleMessage.MessageLevel.WARNING -> Log.WARN
            else -> Log.DEBUG
        }
        VoiceboxLog.println(
            priority,
            "VoiceboxKit/JS",
            "${msg.message()}  [${msg.sourceId()}:${msg.lineNumber()}]"
        )
        return true
    }
}
