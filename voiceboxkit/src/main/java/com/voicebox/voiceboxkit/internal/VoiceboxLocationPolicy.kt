package com.voicebox.voiceboxkit

/**
 * Pure decision logic for WebView geolocation — no Android dependencies, so it
 * can be unit-tested without Robolectric or mocks.
 *
 * The recorder's "Share precise location" toggle calls
 * `navigator.geolocation.getCurrentPosition`. Android WebView surfaces that as
 * [android.webkit.WebChromeClient.onGeolocationPermissionsShowPrompt], which
 * must explicitly grant or deny the origin. The SDK grants only when the host
 * app already holds a location permission; otherwise it requests the OS
 * permission (user already opted in via the toggle) and then grants/denies.
 */
internal object VoiceboxLocationPolicy {

    /**
     * Whether to grant the WebView origin access to geolocation immediately.
     *
     * Granted only when the app already holds fine or coarse location — the SDK
     * never escalates permission without an OS dialog.
     */
    fun shouldGrantWebViewGeolocation(appHoldsLocation: Boolean): Boolean = appHoldsLocation

    /**
     * Whether the SDK should launch the OS location permission dialog in response
     * to a WebView geolocation prompt.
     *
     * Only when the app does not already hold location permission. Unlike mic,
     * this is always on-demand (the visitor opted in via the precise-location
     * toggle), so there is no separate auto-grant flag.
     */
    fun shouldRequestOsLocation(appHoldsLocation: Boolean): Boolean = !appHoldsLocation
}
