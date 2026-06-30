package com.voicebox.voiceboxkit

import android.content.Context

internal fun Context.dpToPx(dp: Float): Int =
    (dp * resources.displayMetrics.density + 0.5f).toInt()

internal fun Context.dpToPx(dp: Int): Int = dpToPx(dp.toFloat())
