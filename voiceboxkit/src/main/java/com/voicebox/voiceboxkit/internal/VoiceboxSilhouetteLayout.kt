package com.voicebox.voiceboxkit

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Card-width host for the overlay WebView that makes only the silhouette
 * interactive. Touches that start on the transparent corners beside the head are
 * intercepted and, on release, dismiss (like tapping the dimmed backdrop).
 * Touches inside the head/card reach the WebView normally.
 *
 * A downward fling that starts while the WebView is scrolled to the top also
 * dismisses (requirement: swipe-down to dismiss), without stealing the recorder's
 * own scrolling — the detector only observes events in [dispatchTouchEvent].
 *
 * Mirrors the role of iOS `VoiceboxOverlayContainerView`.
 */
@SuppressLint("ClickableViewAccessibility")
internal class VoiceboxSilhouetteLayout(context: Context) : FrameLayout(context) {

    var silhouette: VoiceboxOverlaySilhouette =
        VoiceboxOverlaySilhouette.forDensity(context.resources.displayMetrics.density)

    /** Invoked when a tap lands outside the silhouette (transparent corners). */
    var onOutsideTap: (() -> Unit)? = null

    /** Invoked on a clear downward fling that [canSwipeToDismiss] permits. */
    var onSwipeDownDismiss: (() -> Unit)? = null

    /** Gate so a fling only dismisses when the content is at the top (not mid-scroll). */
    var canSwipeToDismiss: () -> Boolean = { true }

    private var downOutside = false
    private val flingThresholdPx = 1500f * context.resources.displayMetrics.density / 2f

    private val flingDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (isDownwardDismissFling(velocityX, velocityY, flingThresholdPx) &&
                    canSwipeToDismiss()
                ) {
                    onSwipeDownDismiss?.invoke()
                    return true
                }
                return false
            }
        },
    )

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Observe for the dismiss fling without consuming, so the WebView still works.
        flingDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            downOutside = !silhouette.contains(ev.x, ev.y, width.toFloat(), height.toFloat())
        }
        return downOutside
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (downOutside && ev.actionMasked == MotionEvent.ACTION_UP) {
            onOutsideTap?.invoke()
        }
        return downOutside
    }
}
