# Changelog

All notable changes to VoiceboxKit for Android are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.0] — Unreleased

Session support, in parity with VoiceboxKit iOS 1.1.4.

### Added

- `VoiceboxListener.onAnonymousSessionId(voiceboxView, sessionId)` — the recorder's anonymous
  session id, which messages recorded without an account belong to. A host can send it to its
  backend after sign-in to claim them. Read from the page's storage
  (`vbx_profiles_session_id`) at document start, DOMContentLoaded and `load`, on the recorder's
  save/submit events, once a second for up to two minutes, and once more natively when the
  page finishes loading (which also covers WebViews without `DOCUMENT_START_SCRIPT`). An id
  counts as reported only once a listener has actually received it.
- `VoiceboxKit.establishSession(url, onResult)` — sign the recorder in by loading a
  host-supplied session URL in an off-screen WebView, so the cookie lands in the shared jar.
  20 s timeout; a newer call cancels one in flight. The SDK does not mint the URL, calls no
  Voicebox API and never handles credentials.
- `VoiceboxKit.clearSession(onDone)` — expire the recorder's cookies and delete its storage
  for `baseUrl`'s host only. Never uses `removeAllCookies()` / `WebStorage.deleteAllData()`,
  which would also sign a host's users out of unrelated sites. Cancels an `establishSession`
  still loading first, so it can't finish afterwards and sign the recorder back in.

- `VoiceboxPresentationMode.FloatingCard(dimOpacity)` — the recorder card centred over a dimmed,
  edge-to-edge screen, matching iOS `.floatingCard`. The page is restyled (footer hidden, card
  sized to content and vertically centred), a card-shaped skeleton shows while it loads, the ×
  becomes a shadowed disc below the status bar, and the keyboard shrinks the page so the card
  re-centres above it. With no close button, a tap outside the card dismisses.
- `VoiceboxEntranceAnimation` (`BackgroundReveal`, `CardLiftIn`; `ALL`/`NONE`) and
  `VoiceboxView.entranceAnimation` / `VoiceboxState.entranceAnimation` — the floating card's
  entrance (350 ms background fade/settle, 300 ms card lift-in after 60 ms, as iOS). Skipped when
  system animations are off.

### Fixed

- `VERSION` said `1.0.0` while the published artifact was `1.0.1`; both are now `1.1.0`.

---

## [1.0.1] — 2026-07-30

### Fixed

- Precise-location toggle in the WebView recorder. The SDK now implements
  `WebChromeClient.onGeolocationPermissionsShowPrompt`, declares
  `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`, enables WebView
  geolocation, and requests the OS location permission on demand when the
  visitor turns on "Share precise location". Without this, the page showed
  "Precise location unavailable" and never prompted.

---

## [1.0.0] — 2026-06-29

Initial release of VoiceboxKit for Android, feature-parity with VoiceboxKit iOS 1.0.3.

### Added

**Compose API**
- `rememberVoiceboxState(handle, params, theme)` — creates and remembers a Compose-observable state holder
- `VoiceboxEffect(state)` — side-effect composable that shows/hides the sheet as `state.isPresented` changes
- `Modifier.voicebox(state)` — iOS-style modifier shortcut (wraps `VoiceboxEffect`)
- `VoiceboxState` — observable state with `isPresented: Boolean by mutableStateOf()` that auto-resets on dismiss

**View / Fragment API**
- `VoiceboxView(handle, params, theme)` — configuration object; call `.present(activity)` to show
- `VoiceboxView.presentAsBottomSheet(activity)` and `presentFullScreen(activity)` convenience methods

**Presentation modes** (all 6 iOS modes ported)
- `BottomSheet` — half-height, draggable to full (default)
- `Sheet` — fully expanded, not draggable
- `FullScreen` — edge-to-edge full-screen modal
- `FitContent` — height auto-adjusts to web content via JS bridge
- `Custom(height: Float)` — fixed height in dp
- `CustomFraction(fraction: Float)` — fraction of screen height, clamped to 0.1–1.0

**Theming**
- `VoiceboxTheme` data class — `cornerRadius`, `backgroundColor`, `closeButtonIconColor`, `closeButtonBackgroundColor`, `closeButtonSize`, `closeButtonIconRes`
- Built-in presets: `VoiceboxTheme.plain`, `.darkCircle`, `.lightCircle`

**Lifecycle callbacks**
- `VoiceboxListener` interface — `onRecordingComplete`, `onMessageSubmitted`, `onDismiss`, `onFailure`; all methods have default empty implementations

**WebView host** (`VoiceboxBottomSheetFragment`)
- Navigation allowlist: `*.vbx.to`, `*.voicebox.ai`, configured `baseUrl` host
- `window.webkit.messageHandlers` polyfill injected at document-start — Voicebox web app runs unchanged on Android
- Skeleton shimmer placeholder while loading
- Offline fallback view with "Try Again" button
- CSS `rgb()`/`rgba()` background-colour detection → navigation bar strip sync
- Mic permission auto-grant via `WebChromeClient.onPermissionRequest` (gated by `RECORD_AUDIO` runtime check)

**Preloading** (`VoiceboxCache`)
- `VoiceboxKit.preload(handle)` — background GET warms the WebView disk cache; ETag stored in `SharedPreferences`
- `VoiceboxCache.validateCache(handle)` — HEAD with `If-None-Match` to check staleness
- `VoiceboxCache.hasCachedContent(handle)` — boolean flag

**Auto-collected app context** (`VoiceboxAppContext`)
- Collects `packageName`, `appName`, `appVersion`, `buildNumber`, `platform`, `osVersion`, `deviceModel`, `locale`, `sdkVersion`
- Controlled by `VoiceboxKit.autoCollectAppContext` (default `true`)
- App-provided params always override auto-collected values

**Global configuration** (`VoiceboxKit` object)
- `VoiceboxKit.init(context)` — stores application context; call once in `Application.onCreate()`
- `VoiceboxKit.baseUrl` — defaults to `https://vbx.to`; set to staging URL for dev builds
- `VoiceboxKit.autoGrantMicPermission` — global default for mic auto-grant (default `false`)
- `VoiceboxKit.autoCollectAppContext` — global default for context collection (default `true`)

**Distribution**
- Published via JitPack: `com.github.aliceappai:voicebox-android:1.0.0`
- Maven publishing configured for future Maven Central release (`com.voicebox:voiceboxkit:1.0.0`)
- AAR includes sources JAR
