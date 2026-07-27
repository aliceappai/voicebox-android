package com.voicebox.voiceboxkit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * A shimmer loading skeleton shaped like the floating recorder **card**.
 *
 * Unlike [VoiceboxSkeletonView] (a full-width prompt bar + mic circle, meant for an opaque
 * sheet), this draws a centred card placeholder with an **avatar-shaped badge straddling its
 * top edge** — matching where the real card + avatar appear — so the load state reads as
 * "the card is coming" instead of stray bars floating over a transparent presentation.
 *
 * Ported from iOS `VoiceboxCardSkeletonView`, including its layout constants: iOS points map
 * 1:1 to Android dp here, so the two platforms shimmer at the same proportions.
 *
 * iOS gates this on `.floatingCard`; Android has no such mode, so the fragment selects it
 * whenever [VoiceboxView.hidePageChrome] is set — that flag is precisely "the page has been
 * reduced to card + avatar", which is when a card-shaped skeleton is the honest preview.
 *
 * One deliberate difference from iOS: no `dimOpacity`. On iOS the floating card's dim is
 * painted by the web page, so the skeleton has to reproduce it or the backdrop jumps from
 * undimmed to dimmed on load. On Android the hosting fragment already paints a scrim behind
 * the sheet, so this view stays transparent and lets that scrim show through.
 */
internal class VoiceboxCardSkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private companion object {
        // iOS points → dp, from VoiceboxCardSkeletonView.layoutSubviews().
        const val CARD_HORIZONTAL_INSET = 96f
        const val CARD_MAX_WIDTH = 344f
        const val CARD_ASPECT = 1.4f
        const val CARD_MAX_HEIGHT_FRACTION = 0.62f
        const val CARD_CORNER = 40f
        const val AVATAR_WIDTH_FRACTION = 0.26f
        const val AVATAR_CORNER_FRACTION = 0.32f
        const val LINE_HEIGHT = 14f
        const val LINE_CORNER = 6f
        const val LINE_TOP_GAP = 22f
        const val LINE_GAP = 18f
        const val CONTROL_INSET = 48f
        const val BUTTON_HEIGHT = 52f
        const val LANGUAGE_HEIGHT = 40f
        const val LANGUAGE_CORNER = 12f
        const val CONTROL_GAP = 16f
        const val BOTTOM_INSET = 28f

        const val CARD_COLOR = 0xFFFFFFFF.toInt()        // iOS systemBackground (light)
        const val PLACEHOLDER_COLOR = 0xFFE5E5EA.toInt() // iOS systemGray5

        const val SHIMMER_DURATION_MS = 1400L
        const val SHADOW_RADIUS = 24f
        const val SHADOW_DY = 8f
    }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CARD_COLOR
        // Matches iOS shadowOpacity 0.08 / radius 24 / offset (0, 8). setShadowLayer is not
        // supported by the hardware pipeline for arbitrary shapes, hence the software layer
        // below — acceptable because this view only exists while the page is loading.
        setShadowLayer(
            context.dpToPx(SHADOW_RADIUS).toFloat(),
            0f,
            context.dpToPx(SHADOW_DY).toFloat(),
            Color.argb(20, 0, 0, 0),
        )
    }

    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PLACEHOLDER_COLOR
    }

    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shimmerMatrix = Matrix()
    private var shimmerShader: LinearGradient? = null

    /** 0..1 sweep position, driven by [animator]. */
    private var shimmerProgress = 0f

    private val cardRect = RectF()
    private val avatarRect = RectF()
    private val lineRects = mutableListOf<RectF>()
    private val languageRect = RectF()
    private val buttonRect = RectF()
    private val cardPath = Path()

    private val cardCornerPx = context.dpToPx(CARD_CORNER).toFloat()
    private var avatarCornerPx = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = SHIMMER_DURATION_MS
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            shimmerProgress = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutCard(w.toFloat(), h.toFloat())
    }

    private fun layoutCard(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        val centerX = width / 2f

        val cardWidth = minOf(width - context.dpToPx(CARD_HORIZONTAL_INSET), context.dpToPx(CARD_MAX_WIDTH).toFloat())
        val cardHeight = minOf(cardWidth * CARD_ASPECT, height * CARD_MAX_HEIGHT_FRACTION)
        val avatarSize = cardWidth * AVATAR_WIDTH_FRACTION
        avatarCornerPx = avatarSize * AVATAR_CORNER_FRACTION

        // Centre the AVATAR + CARD group — the web page centres `#main`, which includes the
        // avatar straddling the card top. Centring the card alone makes the skeleton ride high
        // relative to the real card.
        val groupHeight = avatarSize / 2f + cardHeight
        val cardTop = (height - groupHeight) / 2f + avatarSize / 2f
        val cardLeft = centerX - cardWidth / 2f
        cardRect.set(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)

        avatarRect.set(
            centerX - avatarSize / 2f,
            cardTop - avatarSize / 2f,
            centerX + avatarSize / 2f,
            cardTop + avatarSize / 2f,
        )

        // Text lines below the avatar: @handle, vbx.to/@handle, prompt.
        val lineHeightPx = context.dpToPx(LINE_HEIGHT).toFloat()
        val lineGapPx = context.dpToPx(LINE_GAP).toFloat()
        var y = avatarRect.bottom + context.dpToPx(LINE_TOP_GAP)
        lineRects.clear()
        for (fraction in listOf(0.42f, 0.56f, 0.70f)) {
            val w = cardWidth * fraction
            lineRects += RectF(centerX - w / 2f, y, centerX + w / 2f, y + lineHeightPx)
            y += lineHeightPx + lineGapPx
        }

        // Language selector + "Tap to Talk" button pinned near the card bottom.
        val controlWidth = cardWidth - context.dpToPx(CONTROL_INSET)
        val buttonHeightPx = context.dpToPx(BUTTON_HEIGHT).toFloat()
        val languageHeightPx = context.dpToPx(LANGUAGE_HEIGHT).toFloat()
        val buttonTop = cardRect.bottom - context.dpToPx(BOTTOM_INSET) - buttonHeightPx
        buttonRect.set(
            centerX - controlWidth / 2f,
            buttonTop,
            centerX + controlWidth / 2f,
            buttonTop + buttonHeightPx,
        )
        val languageTop = buttonTop - context.dpToPx(CONTROL_GAP) - languageHeightPx
        languageRect.set(
            centerX - controlWidth / 2f,
            languageTop,
            centerX + controlWidth / 2f,
            languageTop + languageHeightPx,
        )

        cardPath.reset()
        cardPath.addRoundRect(cardRect, cardCornerPx, cardCornerPx, Path.Direction.CW)

        shimmerShader = LinearGradient(
            0f, 0f, cardWidth, 0f,
            intArrayOf(Color.TRANSPARENT, Color.argb(115, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        ).also { shimmerPaint.shader = it }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cardRect.isEmpty) return

        canvas.drawRoundRect(cardRect, cardCornerPx, cardCornerPx, cardPaint)

        // Avatar is drawn after the card so it sits on top where it straddles the edge.
        canvas.drawRoundRect(avatarRect, avatarCornerPx, avatarCornerPx, placeholderPaint)

        val lineCorner = context.dpToPx(LINE_CORNER).toFloat()
        lineRects.forEach { canvas.drawRoundRect(it, lineCorner, lineCorner, placeholderPaint) }
        canvas.drawRoundRect(
            languageRect,
            context.dpToPx(LANGUAGE_CORNER).toFloat(),
            context.dpToPx(LANGUAGE_CORNER).toFloat(),
            placeholderPaint,
        )
        canvas.drawRoundRect(
            buttonRect,
            buttonRect.height() / 2f,
            buttonRect.height() / 2f,
            placeholderPaint,
        )

        // Sweep the highlight across the card, clipped to its rounded shape — an unclipped
        // gradient paints into the corner triangles and the corners read as square mid-sweep.
        shimmerShader?.let { shader ->
            val travel = cardRect.width()
            shimmerMatrix.reset()
            shimmerMatrix.setTranslate(cardRect.left + (shimmerProgress * 2f - 1f) * travel, 0f)
            shader.setLocalMatrix(shimmerMatrix)
            canvas.save()
            canvas.clipPath(cardPath)
            canvas.drawRect(cardRect, shimmerPaint)
            canvas.restore()
        }
    }

    fun startAnimating() {
        alpha = 1f
        visibility = VISIBLE
        if (!animator.isRunning) animator.start()
    }

    fun stopAnimating() {
        animate().alpha(0f).setDuration(300).withEndAction {
            visibility = GONE
            animator.cancel()
            alpha = 1f
        }.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
