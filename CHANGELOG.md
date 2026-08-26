# Changelog

All notable changes to VoiceboxKit for Android are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.0]

### Added

- The recorder's anonymous session id is now reported to the host, via
  `VoiceboxListener.onAnonymousSessionId(voiceboxView, sessionId)`. Messages recorded while
  signed out belong to that id and to no account; handing it back at sign-in is what lets a
  host claim them. The id is written lazily by the recorder, so it is read at document-start,
  again on the recorder's own complete/submit events, and polled once a second (up to two
  minutes) until it exists.
- `VoiceboxKit.establishSession(url)` — sign the recorder's WebView in with a URL the host
  obtained from its own backend. The recorder runs on a different host from a host app's API,
  with its own cookie, so a natively signed-in user otherwise reaches it signed OUT and
  everything they record is anonymous. **The SDK does not mint this URL, calls no Voicebox
  API, and never handles credentials** — it navigates to what it is given, in the cookie
  store the recorder uses, and reports whether it arrived. Call it once per session, not per
  recorder open: `CookieManager` is process-global, so one call covers every voicebox opened
  afterwards. Treat failure as unimportant and never block the recorder on it — an
  unattributed recording is still captured, and can be claimed afterwards.
- `VoiceboxKit.clearSession()` — forget who was recording on this device. Clears **both**
  halves together, because a host that did one and forgot the other leaves the device in a
  state neither describes: the session cookies for `baseUrl`'s host, and the anonymous
  session id in the recorder's web storage. Clearing the id is **not** on its own a defence
  against the wrong account claiming those recordings — that protection is server-side, where
  a claim only touches messages nobody owns yet — it bounds how much history one claim covers.

### Notes

- ⚠️ **Clearing is scoped to the recorder's host, deliberately.** `CookieManager` is
  process-global — one jar shared with every WebView the host app has, including ones showing
  sites unrelated to Voicebox — so `removeAllCookies()` would log a host's users out of other
  services on our sign-out. There is no per-domain removal API, so the recorder's cookies are
  expired individually with `Max-Age=0`. `SessionCaptureTest` pins that nobody simplifies it
  back.
- Storage is cleared with `WebStorage.deleteOrigin`, not by running `localStorage.removeItem`
  in a throwaway WebView. The latter looks like it should work and does not: a WebView with
  nothing loaded sits on `about:blank`, whose storage belongs to a different origin, so the
  removal succeeds and clears nothing.
- Both session methods re-warm the preload cache afterwards, so the next fetch happens with
  the new cookie. Re-warming is the SDK's job rather than the host's — only the cache knows
  what is in the pool.
- The storage key is a **cross-repo contract** with vbx-web (`profiles_session.js`). A rename
  on either side silently stops capture, with no compile error on either; `SessionCaptureTest`
  pins the literal.
- Kept in parity with VoiceboxKit iOS 1.2.0, which ships the same two methods and callback.

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
