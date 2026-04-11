package com.example.cookgpt.util

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.Transformation
import kotlin.math.sin

class WaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePath = Path()

    private var progress = 0f // 0.0 to 1.0
    private var waveOffset = 0f
    private val waveColor = Color.parseColor("#3B82F6")
    private val bgColor = Color.parseColor("#F0F9FF")

    private var animator: WaveAnimation? = null

    init {
        wavePaint.color = waveColor
        wavePaint.style = Paint.Style.FILL
        
        circlePaint.color = bgColor
        circlePaint.style = Paint.Style.FILL

        startAnimation()
    }

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val radius = width.coerceAtMost(height) / 2f
        val centerX = width / 2f
        val centerY = height / 2f

        // Draw background circle
        canvas.drawCircle(centerX, centerY, radius, circlePaint)

        // Clip to circle
        wavePath.reset()
        wavePath.addCircle(centerX, centerY, radius, Path.Direction.CW)
        canvas.clipPath(wavePath)

        // Draw Wave
        wavePath.reset()
        val waveHeight = 20f
        val waveLength = width.toFloat()
        
        // Calculate Y position based on progress
        val yBase = height - (progress * height)

        wavePath.moveTo(-waveLength + waveOffset, yBase)
        
        var i = -waveLength
        while (i <= width + waveLength) {
            val y = yBase + waveHeight * sin((i + waveOffset) / waveLength * 2 * Math.PI).toFloat()
            wavePath.lineTo(i, y)
            i += 10f
        }

        wavePath.lineTo(width.toFloat(), height.toFloat())
        wavePath.lineTo(0f, height.toFloat())
        wavePath.close()

        canvas.drawPath(wavePath, wavePaint)
    }

    private fun startAnimation() {
        animator = WaveAnimation()
        animator?.duration = 2000
        animator?.repeatCount = Animation.INFINITE
        animator?.interpolator = LinearInterpolator()
        startAnimation(animator)
    }

    private inner class WaveAnimation : Animation() {
        override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
            waveOffset = interpolatedTime * width
            invalidate()
        }
    }
}
