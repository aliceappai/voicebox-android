# VoiceboxKit — URL Parameter Reference

Parameters are passed via the `params` map on `VoiceboxView` or `rememberVoiceboxState`.
They are appended to the Voicebox URL as query parameters.

## Merge Order (lowest → highest precedence)

1. **Auto-collected context** (`VoiceboxKit.autoCollectAppContext = true`) — device/app metadata
2. **App-provided `params`** — always win over auto-collected
3. **UTM tags** — always appended last (`utm_source=voiceboxkit`, `utm_medium=android_sdk`)

Non-UTM parameters are sorted alphabetically for deterministic, cache-friendly URLs.

---

## User Context Parameters

Pre-populate the session with user information so feedback is attributed correctly
in the Voicebox dashboard.

| Parameter | Type | Description | Example |
|---|---|---|---|
| `email` | String | User's email address | `jane@example.com` |
| `userID` | String | Your internal user identifier | `usr_abc123` |
| `name` | String | User's display name | `Jane Smith` |

```kotlin
VoiceboxView(
    handle = "your-handle",
    params = mapOf(
        "email"  to "jane@example.com",
        "userID" to "usr_abc123",
        "name"   to "Jane Smith",
    ),
)
```

---

## Session Parameters

Customise the recording experience for a specific session.

| Parameter | Type | Description | Example |
|---|---|---|---|
| `prompt` | String | Custom question or prompt shown to the user | `How is our onboarding going?` |
| `ll` | String | Latitude and longitude (comma-separated) | `47.6062,-122.3321` |
| `ref` | String | Source or referrer tag for analytics | `settings-screen` |

```kotlin
VoiceboxView(
    handle = "your-handle",
    params = mapOf(
        "prompt" to "How are you finding the new dashboard?",
        "ref"    to "home-screen",
    ),
)
```

---

## Auto-collected Context Parameters

When `VoiceboxKit.autoCollectAppContext = true` (default), the SDK appends the
following parameters automatically. You can override any of them by passing the
same key in `params`.

| Parameter | Android source | Example value |
|---|---|---|
| `platform` | Constant `"android"` | `android` |
| `osVersion` | `Build.VERSION.RELEASE` | `14` |
| `deviceModel` | `Build.MANUFACTURER + " " + Build.MODEL` | `Google Pixel 8` |
| `locale` | `Locale.getDefault().toString()` | `en_US` |
| `sdkVersion` | `VoiceboxKit.VERSION` | `1.0.0` |
| `packageName` | `context.packageName` | `com.example.myapp` |
| `appName` | `ApplicationInfo.loadLabel()` | `My App` |
| `appVersion` | `PackageInfo.versionName` | `2.4.1` |
| `buildNumber` | `PackageInfo.longVersionCode` | `241` |

### Overriding Auto-collected Values

```kotlin
VoiceboxView(
    handle = "your-handle",
    params = mapOf(
        // Override the auto-collected platform tag for a custom build
        "platform" to "android-enterprise",
        // Force a specific locale regardless of device setting
        "locale"   to "en_US",
    ),
)
```

---

## UTM Parameters

The SDK always appends these UTM tags — they cannot be overridden:

| Parameter | Value |
|---|---|
| `utm_source` | `voiceboxkit` |
| `utm_medium` | `android_sdk` |

---

## iOS ↔ Android Parameter Parity

Parameters are identical between platforms so cross-platform deployments share the
same Voicebox dashboard without any backend changes.

| iOS auto-collected | Android equivalent |
|---|---|
| `bundleID` | `packageName` |
| `platform = "ios"` | `platform = "android"` |
| `osVersion` (iOS version) | `osVersion` (Android release) |
| `deviceModel` (hardware ID e.g. `iPhone16,2`) | `deviceModel` (`Google Pixel 8`) |
| `utm_medium = "ios_sdk"` | `utm_medium = "android_sdk"` |

All other parameters (`email`, `userID`, `prompt`, `ref`, `ll`, etc.) are identical
on both platforms.
