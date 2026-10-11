package com.example.sensorplayground

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Visual Tilt Radar & Artificial Horizon Spirit Bubble
 * Transforms raw accelerometer readings into an engaging, interactive aircraft/gaming instrument.
 */
class TiltRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#38BDF8")
    }

    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#334155")
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#475569")
    }

    private val centerTargetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#10B981")
    }

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bubbleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private var currentBubbleX = 0f
    private var currentBubbleY = 0f
    private var targetBubbleX = 0f
    private var targetBubbleY = 0f

    private var pitchDegrees = 0f
    private var rollDegrees = 0f
    private var totalTiltDegrees = 0f

    private var accentColor = ContextCompat.getColor(context, R.color.theme_low_accent)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val cx = w / 2f
            val cy = h / 2f
            if (currentBubbleX == 0f && currentBubbleY == 0f) {
                currentBubbleX = cx
                currentBubbleY = cy
                targetBubbleX = cx
                targetBubbleY = cy
            }
        }
    }

    fun updateTilt(accelX: Float, accelY: Float, accelZ: Float) {
        if (width <= 0 || height <= 0) return

        // Compute pitch and roll angles in degrees
        val pitchRad = atan2(-accelY.toDouble(), hypot(accelX.toDouble(), accelZ.toDouble()))
        val rollRad = atan2(accelX.toDouble(), accelZ.toDouble())

        pitchDegrees = Math.toDegrees(pitchRad).toFloat()
        rollDegrees = Math.toDegrees(rollRad).toFloat()
        totalTiltDegrees = sqrt(pitchDegrees * pitchDegrees + rollDegrees * rollDegrees)

        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(cx, cy) * 0.72f

        // Map tilt (-45 deg to +45 deg) to radar radius
        val normalizedX = (rollDegrees / 45f).coerceIn(-1f, 1f)
        val normalizedY = (pitchDegrees / 45f).coerceIn(-1f, 1f)

        targetBubbleX = cx + normalizedX * maxRadius
        targetBubbleY = cy + normalizedY * maxRadius

        invalidate()
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        outerRingPaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val radarRadius = min(cx, cy) * 0.78f

        // 1. Concentric radar rings
        canvas.drawCircle(cx, cy, radarRadius, outerRingPaint)
        canvas.drawCircle(cx, cy, radarRadius * 0.66f, innerRingPaint)
        canvas.drawCircle(cx, cy, radarRadius * 0.33f, innerRingPaint)

        // 2. Center bullseye (level target)
        canvas.drawCircle(cx, cy, radarRadius * 0.18f, centerTargetPaint)

        // 3. Crosshairs
        canvas.drawLine(cx, cy - radarRadius, cx, cy + radarRadius, crosshairPaint)
        canvas.drawLine(cx - radarRadius, cy, cx + radarRadius, cy, crosshairPaint)

        // 4. Smooth interpolation for spirit bubble
        val smooth = 0.22f
        currentBubbleX += (targetBubbleX - currentBubbleX) * smooth
        currentBubbleY += (targetBubbleY - currentBubbleY) * smooth

        // Bubble color shifts from bright green (level) to orange/red as tilt increases
        val bubbleColor = when {
            totalTiltDegrees < 5f -> Color.parseColor("#10B981") // Perfect balance!
            totalTiltDegrees < 20f -> accentColor
            totalTiltDegrees < 35f -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }

        val bubbleRadius = radarRadius * 0.16f

        // Glow
        bubbleGlowPaint.color = (bubbleColor and 0x00FFFFFF) or 0x33000000
        canvas.drawCircle(currentBubbleX, currentBubbleY, bubbleRadius * 1.6f, bubbleGlowPaint)

        // Bubble fill with 3D gradient highlight
        bubblePaint.shader = RadialGradient(
            currentBubbleX - bubbleRadius * 0.3f,
            currentBubbleY - bubbleRadius * 0.3f,
            bubbleRadius,
            Color.WHITE,
            bubbleColor,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(currentBubbleX, currentBubbleY, bubbleRadius, bubblePaint)
        bubblePaint.shader = null

        // 5. Draw angle readings on bottom
        val statusText = if (totalTiltDegrees < 3f) "SEIMBANG (0°)" else String.format("Miring: %.0f°", totalTiltDegrees)
        canvas.drawText(statusText, cx, h - 8f, textPaint)

        if (kotlin.math.abs(targetBubbleX - currentBubbleX) > 0.1f ||
            kotlin.math.abs(targetBubbleY - currentBubbleY) > 0.1f) {
            invalidate()
        }
    }
}
