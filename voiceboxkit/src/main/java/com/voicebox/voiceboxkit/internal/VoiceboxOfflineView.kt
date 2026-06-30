package com.voicebox.voiceboxkit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

/**
 * Offline fallback shown when the Voicebox WebView fails to load.
 *
 * Mirrors iOS `VoiceboxOfflineView`.
 *
 * @param onRetry Called when the user taps "Try Again".
 */
@SuppressLint("SetTextI18n") // SDK internal offline UI — intentionally not localised
internal class VoiceboxOfflineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    val onRetry: () -> Unit = {},
) : LinearLayout(context, attrs) {

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val pad = context.dpToPx(32f)
        setPadding(pad, pad, pad, pad)
        setBackgroundColor(Color.WHITE)

        val iconSize = context.dpToPx(48f)
        val margin8 = context.dpToPx(8f)
        val margin16 = context.dpToPx(16f)
        val margin24 = context.dpToPx(24f)

        // Wifi-off icon
        val icon = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.voicebox_ic_wifi_off))
        }
        addView(icon, LayoutParams(iconSize, iconSize).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = margin16
        })

        // Title
        addView(TextView(context).apply {
            text = "No Internet Connection"
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = margin8
        })

        // Subtitle
        addView(TextView(context).apply {
            text = "Check your connection and try again."
            textSize = 15f
            setTextColor(0xFF8E8E93.toInt()) // iOS secondaryLabel
            gravity = Gravity.CENTER
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = margin24
        })

        // Retry button
        addView(MaterialButton(context).apply {
            text = "Try Again"
            setBackgroundColor(0xFF007AFF.toInt()) // iOS tintColor blue
            setTextColor(Color.WHITE)
            cornerRadius = context.dpToPx(24f)
            setOnClickListener { onRetry() }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, context.dpToPx(48f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
    }
}
