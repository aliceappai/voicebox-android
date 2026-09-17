package com.voicebox.voiceboxkit

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Card-shaped loading placeholder for [VoiceboxPresentationMode.FloatingCard]: a rounded card
 * with an avatar badge straddling its top edge, three text lines, and the language selector +
 * "Tap to Talk" bars near the bottom — the recorder's own layout, so the skeleton turns into the
 * real card in place. Transparent around the card, so the presentation's dim shows through.
 *
 * Geometry follows iOS `VoiceboxCardSkeletonView` (see [CardSkeletonGeometry]).
 */
internal class VoiceboxCardSkeletonView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CARD_COLOR
        setShadowLayer(24f * density, 0f, 8f * density, SHADOW_COLOR)
    }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLACEHOLDER_COLOR }

    private val pulse = ObjectAnimator.ofFloat(this, "alpha", 1f, 0.55f).apply {
        duration = 700
        repeatMode = ObjectAnimator.REVERSE
        repeatCount = ObjectAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
    }

    init {
        // Software layer: Paint.setShadowLayer on shapes only renders without hardware accel.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = CardSkeletonGeometry.layout(width / density, height / density)
        fun RectF.px() = RectF(left * density, top * density, right * density, bottom * density)

        canvas.drawRoundRect(g.card.px(), g.cardRadius * density, g.cardRadius * density, cardPaint)
        canvas.drawRoundRect(g.avatar.px(), g.avatarRadius * density, g.avatarRadius * density, placeholderPaint)
        for (line in g.lines) {
            canvas.drawRoundRect(line.px(), g.lineRadius * density, g.lineRadius * density, placeholderPaint)
        }
        canvas.drawRoundRect(g.languageBar.px(), g.languageRadius * density, g.languageRadius * density, placeholderPaint)
        val buttonRadius = g.buttonBar.height() / 2 * density
        canvas.drawRoundRect(g.buttonBar.px(), buttonRadius, buttonRadius, placeholderPaint)
    }

    fun startAnimating() {
        alpha = 1f
        visibility = VISIBLE
        pulse.start()
    }

    /** Fades out, then hides — the real card fades in underneath at the same time. */
    fun stopAnimating() {
        pulse.cancel()
        animate().alpha(0f).setDuration(300).withEndAction {
            visibility = GONE
            alpha = 1f
        }.start()
    }

    private companion object {
        const val CARD_COLOR = 0xFFFAF9F6.toInt()       // the recorder card's own warm white
        const val PLACEHOLDER_COLOR = 0xFFE5E5EA.toInt() // iOS systemGray5, as the sheet skeleton
        const val SHADOW_COLOR = 0x14000000              // black at 8%
    }
}

/**
 * Pure layout for [VoiceboxCardSkeletonView], in dp, so the proportions are unit-testable.
 * Values mirror iOS `VoiceboxCardSkeletonView.layoutSubviews`.
 */
internal object CardSkeletonGeometry {

    data class Layout(
        val card: RectF,
        val cardRadius: Float,
        val avatar: RectF,
        val avatarRadius: Float,
        val lines: List<RectF>,
        val lineRadius: Float,
        val languageBar: RectF,
        val languageRadius: Float,
        val buttonBar: RectF,
    )

    fun layout(viewWidth: Float, viewHeight: Float): Layout {
        val centerX = viewWidth / 2
        val cardWidth = minOf(viewWidth - 96f, 344f).coerceAtLeast(0f)
        val cardHeight = minOf(cardWidth * 1.4f, viewHeight * 0.62f)
        val avatarSize = cardWidth * 0.26f
        // Centre the avatar + card GROUP, as the page centres `#main` (avatar included).
        val groupHeight = avatarSize / 2 + cardHeight
        val cardTop = (viewHeight - groupHeight) / 2 + avatarSize / 2
        val card = RectF(centerX - cardWidth / 2, cardTop, centerX + cardWidth / 2, cardTop + cardHeight)
        val avatar = RectF(centerX - avatarSize / 2, cardTop - avatarSize / 2, centerX + avatarSize / 2, cardTop + avatarSize / 2)

        var y = avatar.bottom + 22f
        val lines = listOf(0.42f, 0.56f, 0.70f).map { fraction ->
            val w = cardWidth * fraction
            RectF(centerX - w / 2, y, centerX + w / 2, y + 14f).also { y += 14f + 18f }
        }

        val controlWidth = cardWidth - 48f
        val buttonBottom = card.bottom - 28f
        val buttonBar = RectF(centerX - controlWidth / 2, buttonBottom - 52f, centerX + controlWidth / 2, buttonBottom)
        val languageBar = RectF(centerX - controlWidth / 2, buttonBar.top - 16f - 40f, centerX + controlWidth / 2, buttonBar.top - 16f)

        return Layout(
            card = card,
            cardRadius = 40f,
            avatar = avatar,
            avatarRadius = avatarSize * 0.32f,
            lines = lines,
            lineRadius = 6f,
            languageBar = languageBar,
            languageRadius = 12f,
            buttonBar = buttonBar,
        )
    }
}
