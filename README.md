# VoiceboxKit — Android SDK

A drop-in Android SDK that embeds a Voicebox voice-recording experience into any app with one line of code.

---

## Requirements

| Requirement | Minimum |
|---|---|
| Android API level | 24 (Android 7.0) |
| Kotlin | 1.9+ |
| Jetpack Compose | BOM 2024.01+ |
| Activity | `FragmentActivity` or any subclass (`AppCompatActivity`, etc.) |

---

## Installation

### JitPack

**1. Add JitPack to your project-level `settings.gradle.kts`:**

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**2. Add the dependency to your app module's `build.gradle.kts`:**

```kotlin
dependencies {
    implementation("com.github.aliceappai:voicebox-android:1.0.0")
}
```

### Maven Central *(coming soon)*

```kotlin
dependencies {
    implementation("com.voicebox:voiceboxkit:1.0.0")
}
```

---

## Permissions

VoiceboxKit declares the following permissions in its manifest. They are merged
into your app automatically — no changes needed in your `AndroidManifest.xml`.

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

---

## Quick Start

### 1 — Initialise

Call `VoiceboxKit.init()` once in your `Application.onCreate()`:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VoiceboxKit.init(this)
    }
}
```

Register it in `AndroidManifest.xml`:

```xml
<application android:name=".MyApp" ...>
```

---

### Compose API

```kotlin
@Composable
fun MyScreen() {
    // 1. Create observable state
    val state = rememberVoiceboxState(handle = "your-handle")

    // 2. Wire the state to the sheet presentation layer (renders nothing itself)
    VoiceboxEffect(state = state)

    // 3. Trigger presentation
    Button(onClick = { state.isPresented = true }) {
        Text("Open Voicebox")
    }
}
```

Setting `state.isPresented = true` shows the sheet; the SDK resets it to `false`
automatically when the user dismisses it.

---

### View API

Use `VoiceboxView` from any `FragmentActivity` (including `AppCompatActivity`):

```kotlin
val vb = VoiceboxView(handle = "your-handle")
vb.present(this)   // 'this' is your FragmentActivity
```

---

## Presentation Modes

Choose how the sheet is presented by setting `presentationMode` on your state or view:

| Mode | Description |
|---|---|
| `BottomSheet` | Half-height by default, draggable to full height. **Default.** |
| `Sheet` | Starts fully expanded, not draggable. |
| `FullScreen` | Covers the entire screen. |
| `FitContent` | Sheet height auto-fits the web content (adjusted via JS bridge). |
| `Custom(height)` | Fixed height in dp. |
| `CustomFraction(fraction)` | Fraction of screen height (0.0–1.0). |
| `Overlay` | Transparent, shaped "floating" recorder over a dimmed backdrop: head-and-shoulders silhouette (via web `overlay=1`), card-width and centred, slides up from the bottom, grows/shrinks with its content, and dismisses on backdrop tap (including the transparent corners beside the head) or a downward swipe. |

### Compose

```kotlin
val state = rememberVoiceboxState(handle = "your-handle")
state.presentationMode = VoiceboxPresentationMode.BottomSheet   // default
state.presentationMode = VoiceboxPresentationMode.Sheet
state.presentationMode = VoiceboxPresentationMode.FullScreen
state.presentationMode = VoiceboxPresentationMode.FitContent
state.presentationMode = VoiceboxPresentationMode.Custom(height = 520f)
state.presentationMode = VoiceboxPresentationMode.CustomFraction(fraction = 0.65f)
state.presentationMode = VoiceboxPresentationMode.Overlay

state.isPresented = true
```

### View API

```kotlin
val vb = VoiceboxView(handle = "your-handle")
vb.presentationMode = VoiceboxPresentationMode.FullScreen
vb.present(this)
```

---

## Custom Parameters

Pass key-value pairs that are appended to the Voicebox URL as query parameters.
These let you pre-populate the session with user context:

```kotlin
val vb = VoiceboxView(
    handle = "your-handle",
    params = mapOf(
        "email"   to "jane@example.com",
        "userID"  to "usr_abc123",
        "prompt"  to "How are you liking our app?",
    ),
)
vb.present(this)
```

Compose:

```kotlin
val state = rememberVoiceboxState(
    handle = "your-handle",
    params = mapOf("email" to "jane@example.com"),
)
```

See [PARAMS.md](PARAMS.md) for the full parameter reference.

---

## Theming

Customise the close button and sheet appearance via `VoiceboxTheme`.
All properties are optional — set only what you want to override.

```kotlin
val theme = VoiceboxTheme(
    cornerRadius          = 24f,                     // dp
    closeButtonSize       = 36f,                     // dp
    closeButtonIconColor  = Color.WHITE,
    closeButtonBackgroundColor = Color.argb(200, 0, 0, 0),
)

val vb = VoiceboxView(handle = "your-handle", theme = theme)
vb.present(this)
```

### Built-in Presets

```kotlin
VoiceboxTheme.plain       // transparent button, system icon colour
VoiceboxTheme.darkCircle  // dark filled circle, white ×
VoiceboxTheme.lightCircle // light filled circle, dark ×
```

### Custom Close Icon

Supply any drawable from your app:

```kotlin
val theme = VoiceboxTheme(closeButtonIconRes = R.drawable.my_close_icon)
```

### Compose

```kotlin
val state = rememberVoiceboxState(
    handle = "your-handle",
    theme  = VoiceboxTheme.darkCircle,
)
```

---

## Callbacks

Implement `VoiceboxListener` to receive lifecycle events:

```kotlin
val vb = VoiceboxView(handle = "your-handle")

vb.listener = object : VoiceboxListener {
    override fun onRecordingComplete(voiceboxView: VoiceboxView) {
        // User finished recording
    }
    override fun onMessageSubmitted(voiceboxView: VoiceboxView) {
        // Recording was saved/submitted
    }
    override fun onDismiss(voiceboxView: VoiceboxView) {
        // Sheet was dismissed
    }
    override fun onFailure(voiceboxView: VoiceboxView, error: Exception) {
        // Network error or load failure
    }
}

vb.present(this)
```

All methods have default empty implementations — override only the ones you need.

### Compose

```kotlin
val state = rememberVoiceboxState(handle = "your-handle")

state.listener = object : VoiceboxListener {
    override fun onRecordingComplete(voiceboxView: VoiceboxView) { /* … */ }
    override fun onMessageSubmitted(voiceboxView: VoiceboxView)  { /* … */ }
}
```

---

## Preloading

Call `preload()` at app startup to warm the WebView cache. The sheet then opens
instantly instead of showing a loading skeleton.

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VoiceboxKit.init(this)
        VoiceboxKit.preload(handle = "your-handle")   // pre-fetches in background
    }
}
```

---

## Mic Permission

The WebView recorder needs the `RECORD_AUDIO` runtime permission. Unlike iOS,
Android's WebView permission callback can only **grant** or **deny** a mic
request — it cannot show its own prompt. So the SDK never escalates permission
on the app's behalf: it grants the WebView mic **only when your app already
holds `RECORD_AUDIO`**, otherwise it denies and the recorder shows its denied
state.

`autoGrantMicPermission` controls whether the SDK *proactively requests* that
permission for you:

- **`false`** (default) — the SDK does **not** request `RECORD_AUDIO`. Your app
  owns the permission lifecycle; make sure it has already requested and been
  granted `RECORD_AUDIO` before presenting, or the recorder can't capture audio.
- **`true`** — when the sheet opens, the SDK proactively shows the OS
  `RECORD_AUDIO` dialog (if not already granted), then grants the WebView mic
  once the permission is held. Recommended if you want the SDK to manage the
  prompt for you.

```kotlin
// Global — applies to all presentations
VoiceboxKit.autoGrantMicPermission = true

// Per-presentation override
val vb = VoiceboxView(handle = "your-handle")
vb.autoGrantMicPermission = true
vb.present(this)
```

---

## Auto-collected App Context

When `VoiceboxKit.autoCollectAppContext` is `true` (default), the SDK appends
non-PII device and app metadata to the Voicebox URL automatically. This helps
triage feedback without any extra integration work:

| Parameter | Value | Example |
|---|---|---|
| `platform` | `"android"` | `android` |
| `osVersion` | `Build.VERSION.RELEASE` | `14` |
| `deviceModel` | `Build.MANUFACTURER + " " + Build.MODEL` | `Google Pixel 8` |
| `locale` | `Locale.getDefault()` | `en_US` |
| `sdkVersion` | SDK version string | `1.0.0` |
| `packageName` | App package ID | `com.example.myapp` |
| `appName` | App display name | `My App` |
| `appVersion` | `versionName` | `2.4.1` |
| `buildNumber` | `versionCode` | `241` |

App-provided `params` **always override** auto-collected values if the same key
appears in both.

To disable auto-collection:

```kotlin
VoiceboxKit.autoCollectAppContext = false
```

> **Privacy**: No advertising ID, Android ID, device name, or any other
> persistent identifier is collected. Only the fields listed above.

---

## Configuration Reference

```kotlin
// Base URL — change for staging environments
VoiceboxKit.baseUrl = "https://vbxstaging.com"

// Auto-grant WebView mic if app already has RECORD_AUDIO (default: false)
VoiceboxKit.autoGrantMicPermission = true

// Append device/app context to URL (default: true)
VoiceboxKit.autoCollectAppContext = false
```

---

## Full Example

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HomeScreen(activity = this)
            }
        }
    }
}

@Composable
fun HomeScreen(activity: AppCompatActivity) {
    var status by remember { mutableStateOf("") }

    val state = rememberVoiceboxState(
        handle = "your-handle",
        params  = mapOf("email" to "user@example.com"),
        theme   = VoiceboxTheme.darkCircle,
    )
    state.presentationMode = VoiceboxPresentationMode.BottomSheet
    state.autoGrantMicPermission = true
    state.listener = object : VoiceboxListener {
        override fun onRecordingComplete(v: VoiceboxView) { status = "Recorded!" }
        override fun onMessageSubmitted(v: VoiceboxView)  { status = "Submitted!" }
        override fun onDismiss(v: VoiceboxView)           { status = "" }
    }

    VoiceboxEffect(state = state)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(onClick = { state.isPresented = true }) {
            Text("Give Feedback")
        }
        if (status.isNotEmpty()) {
            Text(status, color = MaterialTheme.colorScheme.primary)
        }
    }
}
```

---

## License

MIT © Voicebox
