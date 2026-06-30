package com.voicebox.voiceboxkit

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout

/**
 * Placeholder shimmer shown while the Voicebox WebView is loading.
 *
 * Displays a prompt-bar shape at the bottom and a mic-circle in the centre,
 * animated with a gentle alpha pulse. Matches the approximate Voicebox UI structure.
 *
 * Call [startAnimating] when the WebView begins loading and [stopAnimating] once
 * the page finishes — the view fades out automatically.
 *
 * Mirrors iOS `VoiceboxSkeletonView`.
 */
internal class VoiceboxSkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val shimmer: ObjectAnimator

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val baseColor = 0xFFE5E5EA.toInt() // iOS systemGray5 equivalent

        // Prompt bar placeholder — sits at the bottom of the sheet
        val promptBar = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = context.dpToPx(28f).toFloat()
                setColor(baseColor)
            }
        }
        val margin = context.dpToPx(16f)
        val barH = context.dpToPx(56f)
        addView(promptBar, LayoutParams(LayoutParams.MATCH_PARENT, barH).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(margin, 0, margin, margin)
        })

        // Mic circle placeholder — centred in the sheet
        val micCircle = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(baseColor)
            }
        }
        val circleSize = context.dpToPx(72f)
        addView(micCircle, LayoutParams(circleSize, circleSize).apply {
            gravity = Gravity.CENTER
        })

        // Gentle pulse animation between full opacity and 50%
        shimmer = ObjectAnimator.ofFloat(this, "alpha", 1f, 0.5f).apply {
            duration = 700
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    fun startAnimating() {
        alpha = 1f
        visibility = View.VISIBLE
        shimmer.start()
    }

    fun stopAnimating() {
        shimmer.cancel()
        animate().alpha(0f).setDuration(200).withEndAction {
            visibility = View.GONE
        }.start()
    }
}
