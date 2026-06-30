package com.voicebox.voiceboxkit

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.fragment.app.FragmentActivity

/**
 * Creates and remembers a [VoiceboxState] across recompositions (keyed on [handle]).
 *
 * ```kotlin
 * val state = rememberVoiceboxState(handle = "my-handle")
 *
 * VoiceboxEffect(state = state)
 * Button(onClick = { state.isPresented = true }) { Text("Open") }
 * ```
 *
 * Mirrors the iOS `VoiceboxModifier` factory pattern.
 */
@Composable
fun rememberVoiceboxState(
    handle: String,
    params: Map<String, String> = emptyMap(),
    theme: VoiceboxTheme = VoiceboxTheme(),
): VoiceboxState = remember(handle) {
    VoiceboxState(handle = handle, params = params, theme = theme)
}

/**
 * Side-effect composable that shows and hides the Voicebox sheet in response
 * to [VoiceboxState.isPresented] changes. Renders nothing itself — place it
 * anywhere in your composable tree alongside the trigger UI.
 *
 * ```kotlin
 * @Composable
 * fun MyScreen() {
 *     val state = rememberVoiceboxState(handle = "my-handle")
 *
 *     VoiceboxEffect(state = state)
 *
 *     Button(onClick = { state.isPresented = true }) { Text("Open Voicebox") }
 * }
 * ```
 *
 * The sheet auto-sets `state.isPresented = false` on dismiss.
 *
 * Mirrors iOS `VoiceboxModifier.body`.
 */
@Composable
fun VoiceboxEffect(state: VoiceboxState) {
    val activity = LocalActivity.current as? FragmentActivity

    LaunchedEffect(state.isPresented) {
        if (state.isPresented) {
            activity?.let { VoiceboxBottomSheetFragment.show(it, state.toVoiceboxView()) }
        }
    }
}

/**
 * Attaches Voicebox sheet behaviour to any composable — a more iOS-like call site.
 *
 * This is a thin wrapper around [VoiceboxEffect]; prefer [VoiceboxEffect] in new code
 * since [Modifier.composed] is deprecated in Compose 1.6 and will be migrated to
 * `Modifier.Node` in a future release.
 *
 * ```kotlin
 * val state = rememberVoiceboxState(handle = "my-handle")
 *
 * Column(modifier = Modifier.voicebox(state)) {
 *     Button(onClick = { state.isPresented = true }) { Text("Open Voicebox") }
 * }
 * ```
 *
 * Mirrors the iOS `.voicebox()` View extension.
 */
@Suppress("DEPRECATION") // Modifier.composed — safe until Modifier.Node migration
fun Modifier.voicebox(state: VoiceboxState): Modifier = composed {
    val activity = LocalActivity.current as? FragmentActivity

    LaunchedEffect(state.isPresented) {
        if (state.isPresented) {
            activity?.let { VoiceboxBottomSheetFragment.show(it, state.toVoiceboxView()) }
        }
    }

    this
}
