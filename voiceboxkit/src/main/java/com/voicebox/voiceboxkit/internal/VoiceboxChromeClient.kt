package com.voicebox.voiceboxkit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import androidx.core.content.ContextCompat

/**
 * Handles WebView permission prompts for the Voicebox recorder.
 *
 * Mic (`getUserMedia`) and geolocation (`navigator.geolocation`) are both
 * gated on the host app already holding the matching Android runtime
 * permission. Geolocation can additionally ask the fragment to request the OS
 * permission on demand when the visitor toggles "Share precise location".
 */
internal class VoiceboxChromeClient(
    private val context: Context,
    private val onGeolocationPermissionNeeded: (
        origin: String?,
        callback: GeolocationPermissions.Callback?,
    ) -> Unit,
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

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?,
    ) {
        val appHasLocation = hasLocationPermission()
        VoiceboxLog.d("onGeolocationPermissionsShowPrompt: origin=$origin appHasLocation=$appHasLocation")

        if (VoiceboxLocationPolicy.shouldGrantWebViewGeolocation(appHasLocation)) {
            VoiceboxLog.d("Granting WebView geolocation for origin=$origin")
            callback?.invoke(origin, true, false)
            return
        }

        if (VoiceboxLocationPolicy.shouldRequestOsLocation(appHasLocation)) {
            // Visitor opted in via the precise-location toggle — request OS permission,
            // then grant/deny the WebView origin based on the result.
            onGeolocationPermissionNeeded(origin, callback)
            return
        }

        Log.w("VoiceboxKit", "Denying WebView geolocation (origin=$origin)")
        callback?.invoke(origin, false, false)
    }

    override fun onGeolocationPermissionsHidePrompt() {
        VoiceboxLog.d("onGeolocationPermissionsHidePrompt")
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

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
