package com.voicebox.voiceboxkit

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

/**
 * Collects non-PII app and device context for inclusion in the Voicebox URL.
 *
 * The purpose is **feedback triage**: when a user submits a recording, the
 * Voicebox backend user can immediately see which app, version, and OS the
 * feedback came from — without the host app manually passing these params.
 *
 * Collection is controlled by [VoiceboxKit.autoCollectAppContext] (default `true`).
 * App-provided params always override auto-collected values.
 *
 * **Collected:**
 * - `packageName`, `appName`, `appVersion`, `buildNumber` — app identity
 * - `platform`, `osVersion`, `deviceModel` — device context
 * - `locale` — user region
 * - `sdkVersion` — VoiceboxKit SDK version
 *
 * **Not collected** (privacy):
 * - Device name or user-identifiable label
 * - Advertising ID (GAID)
 * - Android ID or any persistent device identifier
 * - Location, contacts, or any permission-gated data
 *
 * Mirrors iOS `VoiceboxAppContext`.
 */
internal object VoiceboxAppContext {

    /**
     * Returns all auto-collectable context params as a [Map].
     *
     * @param context Optional context used to read package-level info (name,
     *   version, app label). Fields that require context are skipped if `null`.
     *   Defaults to the context stored by [VoiceboxKit.init].
     */
    fun collect(context: Context? = VoiceboxKit.applicationContext): Map<String, String> {
        val params = mutableMapOf<String, String>()

        // Platform + OS — no context required
        params["platform"] = "android"
        params["osVersion"] = Build.VERSION.RELEASE
        params["deviceModel"] = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        params["locale"] = Locale.getDefault().toString()  // "en_US" format (mirrors iOS)
        params["sdkVersion"] = VoiceboxKit.VERSION

        // Package-level fields require context
        if (context != null) {
            params["packageName"] = context.packageName

            try {
                val pm = context.packageManager
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, 0)
                }

                val appInfo = context.applicationInfo
                val appName = pm.getApplicationLabel(appInfo).toString()
                params["appName"] = appName

                info.versionName?.let { params["appVersion"] = it }

                val buildNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode.toString()
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toString()
                }
                params["buildNumber"] = buildNumber
            } catch (_: PackageManager.NameNotFoundException) {
                // Gracefully skip — shouldn't happen for the host app's own package.
            }
        }

        return params
    }
}
