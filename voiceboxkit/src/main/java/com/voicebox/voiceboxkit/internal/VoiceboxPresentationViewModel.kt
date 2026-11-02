package com.voicebox.voiceboxkit

import androidx.lifecycle.ViewModel

/**
 * What `VoiceboxBottomSheetFragment.show` handed the fragment, kept where a recreated fragment
 * can find it.
 *
 * When the host Activity is recreated (rotation, dark mode, font size, locale, multi-window
 * resize), FragmentManager rebuilds the fragment with its empty constructor. The fragment's
 * ViewModelStore survives that, so the new instance reads its [voiceboxView] back from here and
 * renders the recorder again. The WebView itself is rebuilt and the page reloads; the recorder
 * page recovers a chunked recording that was interrupted on its own at load.
 *
 * After process death this is empty: a [VoiceboxView] carries host callbacks, which cannot be
 * saved, so there is nothing to rebuild the recorder from and the fragment removes itself.
 *
 * Holding [voiceboxView] keeps the host's [VoiceboxView.listener] alive across the change, and
 * with it whatever that listener captures — often the old Activity. That lasts only until the
 * recorder closes, when the fragment is removed and this ViewModel is cleared, and it is what
 * lets the host still hear `onDismiss` for a presentation that outlived its Activity (a hoisted
 * Compose [VoiceboxState] relies on it to reset `isPresented`). The fragment writes nothing of
 * its own into the [VoiceboxView], so nothing here references a fragment or its views.
 */
internal class VoiceboxPresentationViewModel : ViewModel() {
    var voiceboxView: VoiceboxView? = null
}
