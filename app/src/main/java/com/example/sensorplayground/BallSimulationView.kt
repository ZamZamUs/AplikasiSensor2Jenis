package com.example.sensorplayground

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import java.util.LinkedList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Interactive Game & Sensor Simulation View
 * Features:
 * - Real bouncy tilt physics & drag fling
 * - Dynamic Game Modes (Star Catcher, Bubble Pop, Physics Lab)
 * - Animated cute character with expressive eyes & facial emotion
 * - Glowing particle bursts & floating score text
 * - Trail / Comet tail
 * - Combo & Level Progression
 */
class BallSimulationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class GameMode {
        STAR_CATCHER,
        BUBBLE_POP,
        PHYSICS_LAB
    }

    enum class BallSkin {
        SMILEY_HERO,
        ASTRO_BOT,
        ARCADE_ORB
    }

    interface GameEventListener {
        fun onScoreUpdated(score: Int, combo: Int, highScore: Int)
        fun onLevelUp(newLevel: Int)
        fun onSpeedUpdated(speedPxPerSec: Float, gForce: Float)
        fun onHapticEvent(type: HapticType)
    }

    enum class HapticType {
        STAR_COLLECT,
        WALL_BOUNCE,
        LEVEL_UP,
        BUBBLE_POP
    }

    var gameEventListener: GameEventListener? = null

    // Game Mode & Skin
    var currentMode: GameMode = GameMode.STAR_CATCHER
        set(value) {
            field = value
            resetGameObjects()
            invalidate()
        }

    var currentSkin: BallSkin = BallSkin.SMILEY_HERO
        set(value) {
            field = value
            invalidate()
        }

    // Physics parameters
    private val ballRadiusDp = 22f
    private var ballRadiusPx = 0f

    var ballX = 0f
    var ballY = 0f
    private var velX = 0f
    private var velY = 0f

    // Sensor Calibration & Tilts
    private var calibrationX = 0f
    private var calibrationY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var isMotionDetected = false
    private val motionThreshold = 2.2f

    // Light Overdrive multiplier
    private var speedMultiplier = 1.0f
    private var scoreMultiplier = 1

    // Trail points for comet tail
    private val trailPoints = LinkedList<Pair<Float, Float>>()
    private val maxTrailSize = 14

    // Game Data: Star Catcher Mode
    var currentScore = 0
        private set
    var highScore = 0
        private set
    var currentLevel = 1
        private set
    var comboCount = 0
        private set
    private var lastCollectTime = 0L

    // Collectible Item
    private data class GameItem(
        var x: Float,
        var y: Float,
        val radius: Float,
        val isGem: Boolean,
        var rotation: Float = 0f,
        var pulse: Float = 0f
    )
    private val activeItems = mutableListOf<GameItem>()

    // Bubble Item for Bubble Pop Mode
    private data class Bubble(
        var x: Float,
        var y: Float,
        var radius: Float,
        var vx: Float,
        var vy: Float,
        val color: Int
    )
    private val activeBubbles = mutableListOf<Bubble>()

    // Vortex Hazard
    private var vortexX = 0f
    private var vortexY = 0f
    private var vortexRadius = 35f
    private var vortexAngle = 0f

    // Particles & Floating Texts
    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val color: Int,
        var alpha: Int = 255,
        val radius: Float
    )
    private val particles = mutableListOf<Particle>()

    private data class FloatingText(
        var text: String,
        var x: Float,
        var y: Float,
        var alpha: Int = 255,
        val color: Int
    )
    private val floatingTexts = mutableListOf<FloatingText>()

    // Wall Bounce Ripples
    private data class WallRipple(
        var x: Float,
        var y: Float,
        var radius: Float = 0f,
        var maxRadius: Float = 40f,
        var alpha: Int = 220,
        val color: Int
    )
    private val wallRipples = mutableListOf<WallRipple>()

    // Paints
    private val backgroundGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ballGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        style = Paint.Style.FILL
    }

    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    private val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val itemGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textEffectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D000000")
        style = Paint.Style.FILL
    }

    private val starPath = Path()

    // Themes
    private var baseBallColor: Int = ContextCompat.getColor(context, R.color.theme_low_ball)
    private var alertColor: Int = ContextCompat.getColor(context, R.color.motion_alert_color)
    private var accentColor: Int = ContextCompat.getColor(context, R.color.theme_low_accent)

    // Touch Dragging State
    private var isDragging = false
    private var touchStartX = 0f
    private var touchStartY = 0f

    // Wall impact reaction
    private var dizzyTimer = 0

    init {
        val density = resources.displayMetrics.density
        ballRadiusPx = ballRadiusDp * density
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            if (ballX == 0f && ballY == 0f) {
                ballX = w / 2f
                ballY = h / 2f
            }
            resetGameObjects()
        }
    }

    private fun resetGameObjects() {
        if (width <= 0 || height <= 0) return
        activeItems.clear()
        activeBubbles.clear()
        particles.clear()
        floatingTexts.clear()
        wallRipples.clear()
        trailPoints.clear()

        val padding = ballRadiusPx + 40f
        val spawnW = (width - padding * 2).coerceAtLeast(10f)
        val spawnH = (height - padding * 2).coerceAtLeast(10f)

        when (currentMode) {
            GameMode.STAR_CATCHER -> {
                // Spawn 3 stars and 1 gem
                for (i in 0 until 3) {
                    activeItems.add(
                        GameItem(
                            x = padding + Random.nextFloat() * spawnW,
                            y = padding + Random.nextFloat() * spawnH,
                            radius = ballRadiusPx * 0.75f,
                            isGem = false
                        )
                    )
                }
                activeItems.add(
                    GameItem(
                        x = padding + Random.nextFloat() * spawnW,
                        y = padding + Random.nextFloat() * spawnH,
                        radius = ballRadiusPx * 0.85f,
                        isGem = true
                    )
                )
                // Set Vortex in center-ish
                vortexX = width * 0.25f + Random.nextFloat() * (width * 0.5f)
                vortexY = height * 0.25f + Random.nextFloat() * (height * 0.5f)
            }
            GameMode.BUBBLE_POP -> {
                // Spawn 6 floating bubbles
                val bubbleColors = intArrayOf(
                    Color.parseColor("#38BDF8"),
                    Color.parseColor("#EC4899"),
                    Color.parseColor("#A855F7"),
                    Color.parseColor("#10B981"),
                    Color.parseColor("#FBBF24")
                )
                for (i in 0 until 6) {
                    activeBubbles.add(
                        Bubble(
                            x = padding + Random.nextFloat() * spawnW,
                            y = padding + Random.nextFloat() * spawnH,
                            radius = ballRadiusPx * (0.6f + Random.nextFloat() * 0.6f),
                            vx = (Random.nextFloat() - 0.5f) * 1.5f,
                            vy = (Random.nextFloat() - 0.5f) * 1.5f,
                            color = bubbleColors[Random.nextInt(bubbleColors.size)]
                        )
                    )
                }
            }
            GameMode.PHYSICS_LAB -> {
                // Lab mode: empty clean playground with high bounce
            }
        }
    }

    /**
     * Updates physics with raw accelerometer values.
     * Horizontal tilt: -rawX, Vertical tilt: +rawY.
     */
    fun updateSensorValues(rawX: Float, rawY: Float): Boolean {
        lastRawX = rawX
        lastRawY = rawY

        val calibratedX = rawX - calibrationX
        val calibratedY = rawY - calibrationY

        val magnitude = hypot(calibratedX.toDouble(), calibratedY.toDouble()).toFloat()
        isMotionDetected = magnitude > motionThreshold

        if (width <= 0 || height <= 0) return isMotionDetected

        if (!isDragging) {
            // Acceleration mapped to tilt
            val accelSensitivity = 1.35f * speedMultiplier
            val ax = -calibratedX * accelSensitivity
            val ay = calibratedY * accelSensitivity

            velX += ax
            velY += ay

            // Friction / Drag
            val friction = if (currentMode == GameMode.PHYSICS_LAB) 0.988f else 0.965f
            velX *= friction
            velY *= friction

            // Terminal speed clamp
            val maxSpeed = 35f * speedMultiplier
            val currentSpeed = hypot(velX.toDouble(), velY.toDouble()).toFloat()
            if (currentSpeed > maxSpeed) {
                velX = (velX / currentSpeed) * maxSpeed
                velY = (velY / currentSpeed) * maxSpeed
            }

            ballX += velX
            ballY += velY

            // Boundaries with bounce
            val minX = ballRadiusPx + 12f
            val maxX = width - ballRadiusPx - 12f
            val minY = ballRadiusPx + 12f
            val maxY = height - ballRadiusPx - 12f

            val bounceFactor = if (currentMode == GameMode.PHYSICS_LAB) 0.88f else 0.72f

            if (ballX < minX) {
                ballX = minX
                if (velX < -1.5f) onWallHit(minX, ballY)
                velX = -velX * bounceFactor
            } else if (ballX > maxX) {
                ballX = maxX
                if (velX > 1.5f) onWallHit(maxX, ballY)
                velX = -velX * bounceFactor
            }

            if (ballY < minY) {
                ballY = minY
                if (velY < -1.5f) onWallHit(ballX, minY)
                velY = -velY * bounceFactor
            } else if (ballY > maxY) {
                ballY = maxY
                if (velY > 1.5f) onWallHit(ballX, maxY)
                velY = -velY * bounceFactor
            }

            // Speed & G-Force listener dispatch
            val speedPxPerSec = currentSpeed * 60f
            val gForce = (magnitude / 9.81f).coerceAtLeast(0.05f)
            gameEventListener?.onSpeedUpdated(speedPxPerSec, gForce)
        }

        // Trail update
        trailPoints.addFirst(Pair(ballX, ballY))
        if (trailPoints.size > maxTrailSize) {
            trailPoints.removeLast()
        }

        // Game Mode Logic
        checkGameCollisions()

        invalidate()
        return isMotionDetected
    }

    private fun onWallHit(x: Float, y: Float) {
        dizzyTimer = 16
        wallRipples.add(WallRipple(x = x, y = y, color = accentColor))
        gameEventListener?.onHapticEvent(HapticType.WALL_BOUNCE)
    }

    private fun checkGameCollisions() {
        when (currentMode) {
            GameMode.STAR_CATCHER -> {
                // Check collectible collisions
                val padding = ballRadiusPx + 40f
                val spawnW = (width - padding * 2).coerceAtLeast(10f)
                val spawnH = (height - padding * 2).coerceAtLeast(10f)

                for (item in activeItems) {
                    val dist = hypot((ballX - item.x).toDouble(), (ballY - item.y).toDouble()).toFloat()
                    if (dist < (ballRadiusPx + item.radius)) {
                        // Collected item!
                        val now = System.currentTimeMillis()
                        if (now - lastCollectTime < 2800L) {
                            comboCount++
                        } else {
                            comboCount = 1
                        }
                        lastCollectTime = now

                        val basePoints = if (item.isGem) 30 else 10
                        val pts = basePoints * comboCount * scoreMultiplier
                        currentScore += pts
                        if (currentScore > highScore) highScore = currentScore

                        // Spawn celebration particles
                        val pColor = if (item.isGem) Color.parseColor("#EC4899") else Color.parseColor("#FBBF24")
                        spawnParticleBurst(item.x, item.y, pColor, 16)

                        // Floating score text
                        val textStr = if (comboCount > 1) "+$pts (x$comboCount!)" else "+$pts"
                        floatingTexts.add(FloatingText(text = textStr, x = item.x, y = item.y, color = pColor))

                        // Respawn item in new position
                        item.x = padding + Random.nextFloat() * spawnW
                        item.y = padding + Random.nextFloat() * spawnH

                        // Haptic & event notification
                        gameEventListener?.onHapticEvent(HapticType.STAR_COLLECT)
                        gameEventListener?.onScoreUpdated(currentScore, comboCount, highScore)

                        // Level progression (every 60 points)
                        val newLevel = (currentScore / 60) + 1
                        if (newLevel > currentLevel) {
                            currentLevel = newLevel
                            floatingTexts.add(
                                FloatingText(
                                    text = "LEVEL UP! Lv.$currentLevel",
                                    x = width / 2f,
                                    y = height / 2f,
                                    color = Color.parseColor("#8B5CF6")
                                )
                            )
                            spawnParticleBurst(width / 2f, height / 2f, Color.parseColor("#8B5CF6"), 24)
                            gameEventListener?.onHapticEvent(HapticType.LEVEL_UP)
                            gameEventListener?.onLevelUp(currentLevel)
                        }
                    }
                }

                // Check Vortex collision (obstacle that pushes ball away)
                val vortexDist = hypot((ballX - vortexX).toDouble(), (ballY - vortexY).toDouble()).toFloat()
                if (vortexDist < (ballRadiusPx + vortexRadius)) {
                    val angle = atan2((ballY - vortexY).toDouble(), (ballX - vortexX).toDouble())
                    velX += (cos(angle) * 16f).toFloat()
                    velY += (sin(angle) * 16f).toFloat()
                    dizzyTimer = 20
                    spawnParticleBurst(vortexX, vortexY, Color.parseColor("#EF4444"), 8)
                    gameEventListener?.onHapticEvent(HapticType.WALL_BOUNCE)
                }
            }
            GameMode.BUBBLE_POP -> {
                // Update bubbles motion
                val padding = ballRadiusPx + 30f
                val spawnW = (width - padding * 2).coerceAtLeast(10f)
                val spawnH = (height - padding * 2).coerceAtLeast(10f)

                val it = activeBubbles.iterator()
                while (it.hasNext()) {
                    val b = it.next()
                    b.x += b.vx
                    b.y += b.vy

                    // Bounce bubbles inside screen
                    if (b.x < b.radius || b.x > width - b.radius) b.vx = -b.vx
                    if (b.y < b.radius || b.y > height - b.radius) b.vy = -b.vy

                    val dist = hypot((ballX - b.x).toDouble(), (ballY - b.y).toDouble()).toFloat()
                    if (dist < (ballRadiusPx + b.radius)) {
                        // Pop bubble!
                        spawnParticleBurst(b.x, b.y, b.color, 14)
                        currentScore += 5 * scoreMultiplier
                        if (currentScore > highScore) highScore = currentScore
                        floatingTexts.add(FloatingText(text = "POP! +5", x = b.x, y = b.y, color = b.color))
                        gameEventListener?.onHapticEvent(HapticType.BUBBLE_POP)
                        gameEventListener?.onScoreUpdated(currentScore, 1, highScore)

                        // Respawn bubble
                        b.x = padding + Random.nextFloat() * spawnW
                        b.y = padding + Random.nextFloat() * spawnH
                    }
                }
            }
            GameMode.PHYSICS_LAB -> {
                // Playground free physics
            }
        }
    }

    private fun spawnParticleBurst(cx: Float, cy: Float, color: Int, count: Int) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = 3f + Random.nextFloat() * 7f
            particles.add(
                Particle(
                    x = cx,
                    y = cy,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = color,
                    alpha = 255,
                    radius = 3f + Random.nextFloat() * 5f
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f

        // 1. Concentric background radar rings
        val maxRadius = min(cx, cy) * 0.88f
        canvas.drawCircle(cx, cy, maxRadius * 0.33f, ringPaint)
        canvas.drawCircle(cx, cy, maxRadius * 0.66f, ringPaint)
        canvas.drawCircle(cx, cy, maxRadius, ringPaint)
        canvas.drawLine(cx, cy - maxRadius, cx, cy + maxRadius, backgroundGridPaint)
        canvas.drawLine(cx - maxRadius, cy, cx + maxRadius, cy, backgroundGridPaint)

        // 2. Draw Vortex in Star Catcher mode
        if (currentMode == GameMode.STAR_CATCHER) {
            vortexAngle += 4f
            drawVortex(canvas, vortexX, vortexY, vortexRadius, vortexAngle)
        }

        // 3. Draw Game Items / Stars / Gems
        if (currentMode == GameMode.STAR_CATCHER) {
            for (item in activeItems) {
                item.rotation += 2f
                item.pulse = (item.pulse + 0.08f) % (Math.PI.toFloat() * 2)
                val pulseScale = 1f + 0.12f * sin(item.pulse)

                if (item.isGem) {
                    drawGem(canvas, item.x, item.y, item.radius * pulseScale, item.rotation)
                } else {
                    drawStar(canvas, item.x, item.y, item.radius * pulseScale, item.rotation)
                }
            }
        } else if (currentMode == GameMode.BUBBLE_POP) {
            drawBubbles(canvas)
        }

        // 4. Draw Wall Impact Ripples
        val rippleIt = wallRipples.iterator()
        while (rippleIt.hasNext()) {
            val r = rippleIt.next()
            r.radius += 2.5f
            r.alpha = (r.alpha - 12).coerceAtLeast(0)
            if (r.alpha <= 0 || r.radius >= r.maxRadius) {
                rippleIt.remove()
            } else {
                particlePaint.color = (r.color and 0x00FFFFFF) or (r.alpha shl 24)
                particlePaint.style = Paint.Style.STROKE
                particlePaint.strokeWidth = 3f
                canvas.drawCircle(r.x, r.y, r.radius, particlePaint)
                particlePaint.style = Paint.Style.FILL
            }
        }

        // 5. Draw Comet Speed Trail
        var trailIndex = 0
        val trailTotal = trailPoints.size
        for (pt in trailPoints) {
            val progress = 1f - (trailIndex.toFloat() / trailTotal.coerceAtLeast(1))
            val radius = ballRadiusPx * 0.7f * progress
            val alpha = (progress * 130).toInt().coerceIn(0, 255)
            val trailColor = if (scoreMultiplier > 1) Color.parseColor("#F97316") else accentColor
            trailPaint.color = (trailColor and 0x00FFFFFF) or (alpha shl 24)
            canvas.drawCircle(pt.first, pt.second, radius, trailPaint)
            trailIndex++
        }

        // 6. Draw Ball Shadow
        canvas.drawCircle(ballX + 6f, ballY + 8f, ballRadiusPx, shadowPaint)

        // 7. Draw Ball Glow (Overdrive Solar Aura if high lux)
        val activeBallColor = if (isMotionDetected) alertColor else baseBallColor
        val glowColor = if (scoreMultiplier > 1) Color.parseColor("#F97316") else activeBallColor
        ballGlowPaint.color = (glowColor and 0x00FFFFFF) or 0x44000000
        canvas.drawCircle(ballX, ballY, ballRadiusPx * (1.35f + if (scoreMultiplier > 1) 0.25f else 0f), ballGlowPaint)

        // 8. Draw Main Hero Ball with 3D Radial Gradient & Face
        ballPaint.shader = RadialGradient(
            ballX - ballRadiusPx * 0.35f,
            ballY - ballRadiusPx * 0.35f,
            ballRadiusPx * 1.3f,
            Color.WHITE,
            activeBallColor,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(ballX, ballY, ballRadiusPx, ballPaint)
        ballPaint.shader = null

        // 9. Draw Character Face based on skin & motion state
        drawHeroCharacterFace(canvas)

        // 10. Update & Draw Particles
        val pIt = particles.iterator()
        while (pIt.hasNext()) {
            val p = pIt.next()
            p.x += p.vx
            p.y += p.vy
            p.vx *= 0.94f
            p.vy *= 0.94f
            p.alpha = (p.alpha - 10).coerceAtLeast(0)
            if (p.alpha <= 0) {
                pIt.remove()
            } else {
                particlePaint.color = (p.color and 0x00FFFFFF) or (p.alpha shl 24)
                canvas.drawCircle(p.x, p.y, p.radius, particlePaint)
            }
        }

        // 11. Update & Draw Floating Texts
        val ftIt = floatingTexts.iterator()
        while (ftIt.hasNext()) {
            val ft = ftIt.next()
            ft.y -= 2.2f
            ft.alpha = (ft.alpha - 8).coerceAtLeast(0)
            if (ft.alpha <= 0) {
                ftIt.remove()
            } else {
                textEffectPaint.color = (ft.color and 0x00FFFFFF) or (ft.alpha shl 24)
                canvas.drawText(ft.text, ft.x, ft.y, textEffectPaint)
            }
        }

        if (dizzyTimer > 0) dizzyTimer--

        // Continuous redraw loop while moving or effects active
        if (hypot(velX.toDouble(), velY.toDouble()) > 0.05f ||
            particles.isNotEmpty() ||
            floatingTexts.isNotEmpty() ||
            wallRipples.isNotEmpty() ||
            currentMode == GameMode.BUBBLE_POP ||
            currentMode == GameMode.STAR_CATCHER
        ) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawHeroCharacterFace(canvas: Canvas) {
        val speed = hypot(velX.toDouble(), velY.toDouble()).toFloat()
        val moveAngle = atan2(velY.toDouble(), velX.toDouble())
        val eyeOffsetDist = (ballRadiusPx * 0.22f).coerceAtMost(speed * 0.8f)

        val eyeLookX = (cos(moveAngle) * eyeOffsetDist).toFloat()
        val eyeLookY = (sin(moveAngle) * eyeOffsetDist).toFloat()

        when (currentSkin) {
            BallSkin.SMILEY_HERO -> {
                // Left & Right Eyes
                val eyeSpacing = ballRadiusPx * 0.38f
                val eyeY = ballY - ballRadiusPx * 0.15f
                val eyeRadius = ballRadiusPx * 0.24f

                if (dizzyTimer > 0) {
                    // Dizzy X-eyes when bumped
                    drawDizzyEye(canvas, ballX - eyeSpacing, eyeY, eyeRadius)
                    drawDizzyEye(canvas, ballX + eyeSpacing, eyeY, eyeRadius)
                } else {
                    canvas.drawCircle(ballX - eyeSpacing, eyeY, eyeRadius, eyePaint)
                    canvas.drawCircle(ballX + eyeSpacing, eyeY, eyeRadius, eyePaint)

                    // Pupils looking at movement direction
                    val pupilRadius = eyeRadius * 0.55f
                    canvas.drawCircle(ballX - eyeSpacing + eyeLookX, eyeY + eyeLookY, pupilRadius, pupilPaint)
                    canvas.drawCircle(ballX + eyeSpacing + eyeLookX, eyeY + eyeLookY, pupilRadius, pupilPaint)

                    // Pupil sparkle shine
                    canvas.drawCircle(ballX - eyeSpacing + eyeLookX - 1.5f, eyeY + eyeLookY - 1.5f, pupilRadius * 0.35f, eyePaint)
                    canvas.drawCircle(ballX + eyeSpacing + eyeLookX - 1.5f, eyeY + eyeLookY - 1.5f, pupilRadius * 0.35f, eyePaint)
                }

                // Mouth
                val mouthY = ballY + ballRadiusPx * 0.32f
                if (speed > 12f) {
                    // Open happy mouth `O` when moving fast
                    mouthPaint.style = Paint.Style.FILL
                    mouthPaint.color = Color.parseColor("#0F172A")
                    canvas.drawCircle(ballX, mouthY, ballRadiusPx * 0.18f, mouthPaint)
                    mouthPaint.style = Paint.Style.STROKE
                } else {
                    // Smiling arc
                    val mouthPath = Path().apply {
                        moveTo(ballX - ballRadiusPx * 0.26f, mouthY)
                        quadTo(ballX, mouthY + ballRadiusPx * 0.2f, ballX + ballRadiusPx * 0.26f, mouthY)
                    }
                    canvas.drawPath(mouthPath, mouthPaint)
                }
            }
            BallSkin.ASTRO_BOT -> {
                // Cyber Visor
                val visorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#0F172A")
                    style = Paint.Style.FILL
                }
                val visorGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#38BDF8")
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                }
                canvas.drawRoundRect(
                    ballX - ballRadiusPx * 0.65f,
                    ballY - ballRadiusPx * 0.3f,
                    ballX + ballRadiusPx * 0.65f,
                    ballY + ballRadiusPx * 0.25f,
                    12f, 12f, visorPaint
                )
                canvas.drawRoundRect(
                    ballX - ballRadiusPx * 0.65f,
                    ballY - ballRadiusPx * 0.3f,
                    ballX + ballRadiusPx * 0.65f,
                    ballY + ballRadiusPx * 0.25f,
                    12f, 12f, visorGlowPaint
                )
                // Glowing cyan eye line
                canvas.drawCircle(ballX - ballRadiusPx * 0.25f + eyeLookX, ballY - ballRadiusPx * 0.05f + eyeLookY, 4f, eyePaint)
                canvas.drawCircle(ballX + ballRadiusPx * 0.25f + eyeLookX, ballY - ballRadiusPx * 0.05f + eyeLookY, 4f, eyePaint)
            }
            BallSkin.ARCADE_ORB -> {
                // Retro Arcade Pac-face
                val mouthAngle = if (speed > 3f) (sin(System.currentTimeMillis() * 0.02) * 35.0).toFloat() else 0f
                val pacPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#0F172A")
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(ballX + eyeLookX, ballY - ballRadiusPx * 0.3f + eyeLookY, ballRadiusPx * 0.2f, eyePaint)
                canvas.drawCircle(ballX + eyeLookX, ballY - ballRadiusPx * 0.3f + eyeLookY, ballRadiusPx * 0.1f, pacPaint)
            }
        }
    }

    private fun drawDizzyEye(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            strokeWidth = 3.5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val r = radius * 0.75f
        canvas.drawLine(cx - r, cy - r, cx + r, cy + r, p)
        canvas.drawLine(cx - r, cy + r, cx + r, cy - r, p)
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, rotation: Float) {
        itemGlowPaint.color = Color.parseColor("#33FBBF24")
        canvas.drawCircle(cx, cy, radius * 1.6f, itemGlowPaint)

        starPath.reset()
        val spikes = 5
        val innerRadius = radius * 0.45f
        var rot = Math.toRadians(rotation.toDouble())

        for (i in 0 until spikes * 2) {
            val r = if (i % 2 == 0) radius else innerRadius
            val x = cx + (cos(rot) * r).toFloat()
            val y = cy + (sin(rot) * r).toFloat()
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
            rot += Math.PI / spikes
        }
        starPath.close()

        itemPaint.color = Color.parseColor("#FBBF24")
        canvas.drawPath(starPath, itemPaint)
    }

    private fun drawGem(canvas: Canvas, cx: Float, cy: Float, radius: Float, rotation: Float) {
        itemGlowPaint.color = Color.parseColor("#33EC4899")
        canvas.drawCircle(cx, cy, radius * 1.5f, itemGlowPaint)

        val gemPath = Path()
        val sides = 6
        var rot = Math.toRadians(rotation.toDouble())
        for (i in 0 until sides) {
            val x = cx + (cos(rot) * radius).toFloat()
            val y = cy + (sin(rot) * radius).toFloat()
            if (i == 0) gemPath.moveTo(x, y) else gemPath.lineTo(x, y)
            rot += Math.PI * 2 / sides
        }
        gemPath.close()

        itemPaint.color = Color.parseColor("#EC4899")
        canvas.drawPath(gemPath, itemPaint)
    }

    private fun drawBubbles(canvas: Canvas) {
        for (b in activeBubbles) {
            // Bubble glow
            itemGlowPaint.color = (b.color and 0x00FFFFFF) or 0x44000000
            canvas.drawCircle(b.x, b.y, b.radius, itemGlowPaint)

            // Bubble border
            itemPaint.color = (b.color and 0x00FFFFFF) or 0xCC000000.toInt()
            itemPaint.style = Paint.Style.STROKE
            itemPaint.strokeWidth = 2.5f
            canvas.drawCircle(b.x, b.y, b.radius, itemPaint)

            // Bubble specular shine
            itemPaint.style = Paint.Style.FILL
            itemPaint.color = Color.WHITE
            canvas.drawCircle(b.x - b.radius * 0.35f, b.y - b.radius * 0.35f, b.radius * 0.22f, itemPaint)
        }
    }

    private fun drawVortex(canvas: Canvas, cx: Float, cy: Float, radius: Float, angle: Float) {
        val vortexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#EF4444")
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), angle)
        }
        canvas.drawCircle(cx, cy, radius * 1.2f, vortexPaint)
        canvas.drawCircle(cx, cy, radius * 0.6f, vortexPaint)

        // Center black hole core
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7F1D1D")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius * 0.35f, corePaint)
    }

    // Interactive Touch / Drag Fling
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val dist = hypot((event.x - ballX).toDouble(), (event.y - ballY).toDouble()).toFloat()
                if (dist < ballRadiusPx * 2.2f) {
                    isDragging = true
                    touchStartX = event.x
                    touchStartY = event.y
                    velX = 0f
                    velY = 0f
                    return true
                } else {
                    // Tap elsewhere: spawn interactive ripple or star!
                    if (currentMode == GameMode.STAR_CATCHER) {
                        spawnParticleBurst(event.x, event.y, Color.parseColor("#FBBF24"), 10)
                    } else {
                        spawnParticleBurst(event.x, event.y, accentColor, 8)
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    ballX = event.x.coerceIn(ballRadiusPx, width - ballRadiusPx)
                    ballY = event.y.coerceIn(ballRadiusPx, height - ballRadiusPx)
                    checkGameCollisions()
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    // Fling velocity
                    velX = (event.x - touchStartX) * 0.25f
                    velY = (event.y - touchStartY) * 0.25f
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    fun calibrate(currentRawX: Float, currentRawY: Float) {
        calibrationX = currentRawX
        calibrationY = currentRawY
        if (width > 0 && height > 0) {
            ballX = width / 2f
            ballY = height / 2f
            velX = 0f
            velY = 0f
        }
        invalidate()
    }

    fun resetBall() {
        calibrationX = 0f
        calibrationY = 0f
        if (width > 0 && height > 0) {
            ballX = width / 2f
            ballY = height / 2f
            velX = 0f
            velY = 0f
        }
        invalidate()
    }

    fun restartGame() {
        currentScore = 0
        comboCount = 0
        currentLevel = 1
        resetGameObjects()
        resetBall()
        gameEventListener?.onScoreUpdated(0, 0, highScore)
        gameEventListener?.onLevelUp(1)
        invalidate()
    }

    fun setBaseBallColor(color: Int) {
        this.baseBallColor = color
        invalidate()
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        invalidate()
    }

    fun setLightOverdrive(isOverdrive: Boolean) {
        if (isOverdrive) {
            speedMultiplier = 1.35f
            scoreMultiplier = 2
        } else {
            speedMultiplier = 1.0f
            scoreMultiplier = 1
        }
        invalidate()
    }

    fun cycleNextSkin() {
        currentSkin = when (currentSkin) {
            BallSkin.SMILEY_HERO -> BallSkin.ASTRO_BOT
            BallSkin.ASTRO_BOT -> BallSkin.ARCADE_ORB
            BallSkin.ARCADE_ORB -> BallSkin.SMILEY_HERO
        }
    }
}
