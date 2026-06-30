# Keep the public VoiceboxKit API surface for consumers.
-keep class com.voicebox.voiceboxkit.** { public *; }

# The JS bridge relies on @JavascriptInterface-annotated methods being kept.
-keepclassmembers class com.voicebox.voiceboxkit.** {
    @android.webkit.JavascriptInterface <methods>;
}
