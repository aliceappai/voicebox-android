package com.voicebox.voiceboxkit

import androidx.core.content.FileProvider

/**
 * Lends the camera app the file a photo taken from the recorder's file input is saved
 * to. Its own subclass so it never collides with a `FileProvider` the host app declares.
 */
internal class VoiceboxFileProvider : FileProvider()
