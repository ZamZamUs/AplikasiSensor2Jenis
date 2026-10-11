package com.example.sensorplayground

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Interactive Celestial Light Mascot & Energy Gauge View
 * Transforms ambient lux data into an animated Sun/Moon mascot with rotating rays,
 * energetic particle sparkles, and real-time solar intensity gauge.
 */
class LightVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentLux = 0f
    private var targetLux = 0f
    private var maxRecordedLux = 0f

    // Rotation angles for sunbeams and cosmic stars
    private var rayRotation = 0f
    private var pulsePhase = 0f

    // Mascot state
    private enum class MascotState { MOON_NIGHT, SUNSET_COZY, SOLAR_KING }
    private var state = MascotState.MOON_NIGHT

    // Sparkles
    private data class Sparkle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var alpha: Int = 255,
        val color: Int,
        val radius: Float
    )
    private val sparkles = mutableListOf<Sparkle>()

    // Paints
    private val ringTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        color = Color.parseColor("#334155")
        strokeCap = Paint.Cap.ROUND
    }

    private val ringProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
    }

    private val mascotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        style = Paint.Style.FILL
    }

    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DF43F5E")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun setLux(lux: Float) {
        targetLux = lux
        if (lux > maxRecordedLux) {
            maxRecordedLux = lux
        }
        state = when {
            lux < 50f -> MascotState.MOON_NIGHT
            lux in 50f..300f -> MascotState.SUNSET_COZY
            else -> MascotState.SOLAR_KING
        }
        invalidate()
    }

    fun getMaxLux(): Float = maxRecordedLux

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val mascotRadius = min(cx, cy) * 0.44f
        val gaugeRadius = min(cx, cy) * 0.76f

        // Smooth interpolate lux
        currentLux += (targetLux - currentLux) * 0.15f
        pulsePhase += 0.05f
        rayRotation += if (state == MascotState.SOLAR_KING) 1.8f else 0.6f

        val pulseScale = 1f + 0.05f * sin(pulsePhase)

        // 1. Draw Outer Energy Dial Track
        canvas.drawCircle(cx, cy, gaugeRadius, ringTrackPaint)

        // 2. Draw Outer Energy Progress Arc
        val progressSweep = (currentLux / 1000f).coerceIn(0.01f, 1f) * 360f
        val gaugeColor = when (state) {
            MascotState.MOON_NIGHT -> Color.parseColor("#38BDF8")
            MascotState.SUNSET_COZY -> Color.parseColor("#A855F7")
            MascotState.SOLAR_KING -> Color.parseColor("#EA580C")
        }
        ringProgressPaint.color = gaugeColor

        val arcRect = android.graphics.RectF(cx - gaugeRadius, cy - gaugeRadius, cx + gaugeRadius, cy + gaugeRadius)
        canvas.drawArc(arcRect, -90f, progressSweep, false, ringProgressPaint)

        // 3. Draw Rays for Sun states
        if (state != MascotState.MOON_NIGHT) {
            drawSunRays(canvas, cx, cy, mascotRadius * pulseScale * 1.35f, rayRotation)
        }

        // 4. Draw Mascot Glow Aura
        glowPaint.color = (gaugeColor and 0x00FFFFFF) or 0x44000000
        canvas.drawCircle(cx, cy, mascotRadius * pulseScale * 1.4f, glowPaint)

        // 5. Draw Mascot Body
        when (state) {
            MascotState.MOON_NIGHT -> {
                drawMoonMascot(canvas, cx, cy, mascotRadius * pulseScale)
            }
            MascotState.SUNSET_COZY -> {
                drawSunsetMascot(canvas, cx, cy, mascotRadius * pulseScale)
            }
            MascotState.SOLAR_KING -> {
                drawSolarKingMascot(canvas, cx, cy, mascotRadius * pulseScale)
            }
        }

        // 6. Update and draw sparkles
        val it = sparkles.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.x += s.vx
            s.y += s.vy
            s.alpha = (s.alpha - 12).coerceAtLeast(0)
            if (s.alpha <= 0) {
                it.remove()
            } else {
                mascotPaint.color = (s.color and 0x00FFFFFF) or (s.alpha shl 24)
                canvas.drawCircle(s.x, s.y, s.radius, mascotPaint)
            }
        }

        // Keep animating continuously
        postInvalidateOnAnimation()
    }

    private fun drawMoonMascot(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // Glowing Crescent / Moon sphere
        mascotPaint.shader = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.2f,
            Color.WHITE, Color.parseColor("#38BDF8"), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, mascotPaint)
        mascotPaint.shader = null

        // Craters with soft transparent cyan
        val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#260284C7")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx - radius * 0.45f, cy - radius * 0.3f, radius * 0.22f, craterPaint)
        canvas.drawCircle(cx + radius * 0.45f, cy + radius * 0.35f, radius * 0.16f, craterPaint)

        // Sleeping / Winking Eyes `^ ^`
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
        }
        val leftEyePath = Path().apply {
            moveTo(cx - radius * 0.4f, cy - radius * 0.05f)
            quadTo(cx - radius * 0.25f, cy - radius * 0.22f, cx - radius * 0.1f, cy - radius * 0.05f)
        }
        val rightEyePath = Path().apply {
            moveTo(cx + radius * 0.1f, cy - radius * 0.05f)
            quadTo(cx + radius * 0.25f, cy - radius * 0.22f, cx + radius * 0.4f, cy - radius * 0.05f)
        }
        canvas.drawPath(leftEyePath, eyePaint)
        canvas.drawPath(rightEyePath, eyePaint)

        // Cute smiling mouth
        val mouthPath = Path().apply {
            moveTo(cx - radius * 0.16f, cy + radius * 0.22f)
            quadTo(cx, cy + radius * 0.36f, cx + radius * 0.16f, cy + radius * 0.22f)
        }
        canvas.drawPath(mouthPath, mouthPaint)

        // Pink cheeks
        canvas.drawCircle(cx - radius * 0.45f, cy + radius * 0.15f, radius * 0.14f, blushPaint)
        canvas.drawCircle(cx + radius * 0.45f, cy + radius * 0.15f, radius * 0.14f, blushPaint)
    }

    private fun drawSunsetMascot(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        mascotPaint.shader = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.3f,
            Color.WHITE, Color.parseColor("#A855F7"), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, mascotPaint)
        mascotPaint.shader = null

        // Open friendly eyes
        canvas.drawCircle(cx - radius * 0.28f, cy - radius * 0.1f, radius * 0.14f, facePaint)
        canvas.drawCircle(cx + radius * 0.28f, cy - radius * 0.1f, radius * 0.14f, facePaint)
        // White sparkle in eyes
        val sparkleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(cx - radius * 0.32f, cy - radius * 0.14f, radius * 0.05f, sparkleP)
        canvas.drawCircle(cx + radius * 0.24f, cy - radius * 0.14f, radius * 0.05f, sparkleP)

        // Happy mouth
        val mouthPath = Path().apply {
            moveTo(cx - radius * 0.2f, cy + radius * 0.2f)
            quadTo(cx, cy + radius * 0.38f, cx + radius * 0.2f, cy + radius * 0.2f)
        }
        canvas.drawPath(mouthPath, mouthPaint)

        // Blushing cheeks
        canvas.drawCircle(cx - radius * 0.42f, cy + radius * 0.14f, radius * 0.14f, blushPaint)
        canvas.drawCircle(cx + radius * 0.42f, cy + radius * 0.14f, radius * 0.14f, blushPaint)
    }

    private fun drawSolarKingMascot(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        mascotPaint.shader = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.3f,
            Color.parseColor("#FEF08A"), Color.parseColor("#F97316"), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, mascotPaint)
        mascotPaint.shader = null

        // Cool Sunglasses 😎
        val glassesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }
        val glassGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }

        val glassW = radius * 0.38f
        val glassH = radius * 0.26f
        val glassY = cy - radius * 0.15f

        // Left lens & right lens
        canvas.drawRoundRect(cx - radius * 0.48f, glassY, cx - radius * 0.06f, glassY + glassH, 8f, 8f, glassesPaint)
        canvas.drawRoundRect(cx + radius * 0.06f, glassY, cx + radius * 0.48f, glassY + glassH, 8f, 8f, glassesPaint)
        // Bridge
        canvas.drawRect(cx - radius * 0.08f, glassY + 4f, cx + radius * 0.08f, glassY + 12f, glassesPaint)

        // Cool white diagonal gleam on sunglasses
        canvas.drawLine(cx - radius * 0.42f, glassY + glassH * 0.2f, cx - radius * 0.2f, glassY + glassH * 0.8f, glassGlow)
        canvas.drawLine(cx + radius * 0.14f, glassY + glassH * 0.2f, cx + radius * 0.36f, glassY + glassH * 0.8f, glassGlow)

        // Smug triumphant grin `_--~`
        mouthPaint.strokeWidth = 5f
        val grinPath = Path().apply {
            moveTo(cx - radius * 0.22f, cy + radius * 0.28f)
            quadTo(cx + radius * 0.05f, cy + radius * 0.42f, cx + radius * 0.25f, cy + radius * 0.25f)
        }
        canvas.drawPath(grinPath, mouthPaint)
    }

    private fun drawSunRays(canvas: Canvas, cx: Float, cy: Float, radius: Float, rotDeg: Float) {
        rayPaint.color = if (state == MascotState.SOLAR_KING) Color.parseColor("#FBBF24") else Color.parseColor("#C084FC")
        val numRays = 8
        for (i in 0 until numRays) {
            val angle = Math.toRadians((rotDeg + (i * 360f / numRays)).toDouble())
            val rx = cx + (cos(angle) * radius).toFloat()
            val ry = cy + (sin(angle) * radius).toFloat()
            canvas.drawCircle(rx, ry, radius * 0.09f, rayPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // Tap mascot for festive burst
            val burstColor = when (state) {
                MascotState.MOON_NIGHT -> Color.parseColor("#38BDF8")
                MascotState.SUNSET_COZY -> Color.parseColor("#A855F7")
                MascotState.SOLAR_KING -> Color.parseColor("#F97316")
            }
            for (i in 0 until 14) {
                val ang = Random.nextFloat() * Math.PI.toFloat() * 2f
                val spd = 4f + Random.nextFloat() * 6f
                sparkles.add(
                    Sparkle(
                        x = event.x,
                        y = event.y,
                        vx = cos(ang) * spd,
                        vy = sin(ang) * spd,
                        color = burstColor,
                        radius = 4f + Random.nextFloat() * 5f
                    )
                )
            }
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }
}
