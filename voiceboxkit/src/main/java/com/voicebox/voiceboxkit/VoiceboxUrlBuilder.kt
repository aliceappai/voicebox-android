package com.voicebox.voiceboxkit

import android.net.Uri

/**
 * Builds the full Voicebox URL with params and UTM tags.
 *
 * Merge order (lowest → highest precedence):
 * 1. Auto-collected app context (when [VoiceboxKit.autoCollectAppContext] is `true`)
 * 2. App-provided `params` (always win over auto-collected)
 * 3. UTM tags (always appended last, not sorted)
 *
 * Non-UTM params are sorted alphabetically for deterministic, cache-friendly URLs.
 *
 * Mirrors iOS `VoiceboxView.buildURL()`.
 */
internal object VoiceboxUrlBuilder {

    fun build(handle: String, params: Map<String, String>): Uri {
        val builder = Uri.Builder()
            .scheme("https")
            .authority(Uri.parse(VoiceboxKit.baseUrl).host ?: "vbx.to")
            .appendEncodedPath("@$handle")

        // 1. Start with auto-collected context (if enabled)
        val merged = mutableMapOf<String, String>()
        if (VoiceboxKit.autoCollectAppContext) {
            merged.putAll(VoiceboxAppContext.collect())
        }

        // 2. App-provided params override auto-collected (explicit > implicit)
        merged.putAll(params)

        // 3. Sort alphabetically for deterministic URLs (easier to test + cache)
        merged.toSortedMap().forEach { (key, value) ->
            builder.appendQueryParameter(key, value)
        }

        // UTM params always appended last (can be disabled via VoiceboxKit.appendUtmParams)
        if (VoiceboxKit.appendUtmParams) {
            builder.appendQueryParameter("utm_source", "voiceboxkit")
            builder.appendQueryParameter("utm_medium", "android_sdk")
        }

        val uri = builder.build()
        VoiceboxLog.d("Loading URL: $uri")
        return uri
    }
}
