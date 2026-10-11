package com.example.sensorplayground

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.sensorplayground.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), SensorEventListener, BallSimulationView.GameEventListener {

    private lateinit var binding: ActivityMainBinding

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lightSensor: Sensor? = null
    private var vibrator: Vibrator? = null

    // Raw accelerometer cache
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f

    // Theme state
    private enum class LightThemeState { LOW, MEDIUM, HIGH }
    private var currentThemeState: LightThemeState = LightThemeState.LOW

    // Light Quests state
    private var questDarkCompleted = false
    private var questRoomCompleted = false
    private var questBrightCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-To-Edge system insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootScrollView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, systemBars.bottom)
            insets
        }

        // Initialize Sensors & Vibrator
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        // Attach listener to BallSimulationView
        binding.ballSimulationView.gameEventListener = this

        setupSensorStatusBadges()
        setupSensorToggleSelector()
        setupGameModes()
        setupControlButtons()

        // Apply initial theme
        applyTheme(LightThemeState.LOW)
    }

    private fun setupSensorToggleSelector() {
        binding.toggleSensorSelector.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnTabMotion -> {
                        binding.layoutMotionSection.visibility = View.VISIBLE
                        binding.layoutLightSection.visibility = View.GONE
                        animateFadeIn(binding.layoutMotionSection)
                        Toast.makeText(this, "Menampilkan Sensor Kemiringan & Arena Game 🕹️", Toast.LENGTH_SHORT).show()
                    }
                    R.id.btnTabLight -> {
                        binding.layoutMotionSection.visibility = View.GONE
                        binding.layoutLightSection.visibility = View.VISIBLE
                        animateFadeIn(binding.layoutLightSection)
                        Toast.makeText(this, "Menampilkan Sensor Cahaya & Maskot Cuaca ☀️", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun animateFadeIn(view: View) {
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(220).start()
    }

    private fun setupSensorStatusBadges() {
        if (accelerometer != null) {
            binding.tvMotionBadge.text = "🎯 Motion: Aktif"
            binding.tvMotionBadge.setBackgroundResource(R.drawable.bg_badge_active)
            binding.tvMotionBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_active_text))
        } else {
            binding.tvMotionBadge.text = getString(R.string.sensor_motion_unavailable)
            binding.tvMotionBadge.setBackgroundResource(R.drawable.bg_badge_inactive)
            binding.tvMotionBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_inactive_text))
            binding.tvMotionStatus.text = "Tidak Ada Sensor"
        }

        if (lightSensor != null) {
            binding.tvLightBadge.text = "☀️ Light: Aktif"
            binding.tvLightBadge.setBackgroundResource(R.drawable.bg_badge_active)
            binding.tvLightBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_active_text))
        } else {
            binding.tvLightBadge.text = getString(R.string.sensor_light_unavailable)
            binding.tvLightBadge.setBackgroundResource(R.drawable.bg_badge_inactive)
            binding.tvLightBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_inactive_text))
            binding.tvValLux.text = "N/A"
            binding.tvLightCondition.text = "Tidak Ada Sensor"
        }
    }

    private fun setupGameModes() {
        binding.chipGroupModes.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chipStarCatcher) -> {
                    binding.ballSimulationView.currentMode = BallSimulationView.GameMode.STAR_CATCHER
                    Toast.makeText(this, "Mode: Tangkap Bintang & Gem! 🌟", Toast.LENGTH_SHORT).show()
                }
                checkedIds.contains(R.id.chipBubblePop) -> {
                    binding.ballSimulationView.currentMode = BallSimulationView.GameMode.BUBBLE_POP
                    Toast.makeText(this, "Mode: Pecah Balon Berwarna! 🫧", Toast.LENGTH_SHORT).show()
                }
                checkedIds.contains(R.id.chipPhysicsLab) -> {
                    binding.ballSimulationView.currentMode = BallSimulationView.GameMode.PHYSICS_LAB
                    Toast.makeText(this, "Mode: Lab Fisika & Pantulan Bebas! 🧪", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupControlButtons() {
        binding.btnCalibrate.setOnClickListener {
            binding.ballSimulationView.calibrate(lastAccelX, lastAccelY)
            animateButtonPunch(binding.btnCalibrate)
            Toast.makeText(this, "🎯 Posisi netral berhasil dikalibrasi!", Toast.LENGTH_SHORT).show()
        }

        binding.btnReset.setOnClickListener {
            binding.ballSimulationView.restartGame()
            animateButtonPunch(binding.btnReset)
            Toast.makeText(this, "🔄 Simulasi & Skor direset!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSkin.setOnClickListener {
            binding.ballSimulationView.cycleNextSkin()
            val skinName = when (binding.ballSimulationView.currentSkin) {
                BallSimulationView.BallSkin.SMILEY_HERO -> "Smiley Hero 😊"
                BallSimulationView.BallSkin.ASTRO_BOT -> "Astro Bot 🤖"
                BallSimulationView.BallSkin.ARCADE_ORB -> "Arcade Orb 🟡"
            }
            binding.btnSkin.text = skinName
            animateButtonPunch(binding.btnSkin)
            Toast.makeText(this, "Karakter: $skinName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun animateButtonPunch(view: View) {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.15f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.15f, 1f)
        ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY).apply {
            duration = 200
            interpolator = OvershootInterpolator()
            start()
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.isEmpty()) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> processAccelerometerData(event)
            Sensor.TYPE_LIGHT -> processLightSensorData(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun processAccelerometerData(event: SensorEvent) {
        if (event.values.size < 3) return

        lastAccelX = event.values[0]
        lastAccelY = event.values[1]
        lastAccelZ = event.values[2]

        // Update raw coordinate texts
        binding.tvValX.text = getString(R.string.val_x, lastAccelX)
        binding.tvValY.text = getString(R.string.val_y, lastAccelY)
        binding.tvValZ.text = getString(R.string.val_z, lastAccelZ)

        // Pass readings to visual Tilt Radar
        binding.tiltRadarView.updateTilt(lastAccelX, lastAccelY, lastAccelZ)

        // Pass readings to Game & Simulation View
        binding.ballSimulationView.updateSensorValues(lastAccelX, lastAccelY)
    }

    private fun processLightSensorData(event: SensorEvent) {
        if (event.values.isEmpty()) return

        val lux = event.values[0]
        binding.tvValLux.text = getString(R.string.val_lux, lux)
        binding.pbLightLevel.progress = lux.toInt().coerceIn(0, 1000)

        // Pass lux to interactive Celestial Mascot
        binding.lightVisualizerView.setLux(lux)
        binding.tvPeakLux.text = String.format("Puncak: %.1f Lux", binding.lightVisualizerView.getMaxLux())

        // Check Quests
        checkLightQuests(lux)

        // Environmental Theme Engine
        val targetTheme = when {
            lux < 50f -> LightThemeState.LOW
            lux in 50f..300f -> LightThemeState.MEDIUM
            else -> LightThemeState.HIGH
        }

        if (targetTheme != currentThemeState) {
            applyTheme(targetTheme)
        }
    }

    private fun checkLightQuests(lux: Float) {
        val completedColor = Color.parseColor("#4ADE80")

        if (!questDarkCompleted && lux < 15f) {
            questDarkCompleted = true
            binding.tvQuestDark.text = "SELESAI! ✅"
            binding.tvQuestDark.setTextColor(completedColor)
            animateButtonPunch(binding.tvQuestDark)
        }

        if (!questRoomCompleted && lux > 100f) {
            questRoomCompleted = true
            binding.tvQuestRoom.text = "SELESAI! ✅"
            binding.tvQuestRoom.setTextColor(completedColor)
            animateButtonPunch(binding.tvQuestRoom)
        }

        if (!questBrightCompleted && lux > 400f) {
            questBrightCompleted = true
            binding.tvQuestBright.text = "SELESAI! ✅"
            binding.tvQuestBright.setTextColor(completedColor)
            animateButtonPunch(binding.tvQuestBright)
        }
    }

    private fun applyTheme(themeState: LightThemeState) {
        currentThemeState = themeState

        val isOverdrive = (themeState == LightThemeState.HIGH)
        binding.ballSimulationView.setLightOverdrive(isOverdrive)

        val (bgColor, cardColor, strokeColor, accentColor, textPrimary, textSecondary, ballColor, conditionText, perkText) = when (themeState) {
            LightThemeState.LOW -> ThemeColors(
                bgColor = ContextCompat.getColor(this, R.color.theme_low_bg),
                cardColor = ContextCompat.getColor(this, R.color.theme_low_card),
                strokeColor = ContextCompat.getColor(this, R.color.theme_low_card_stroke),
                accentColor = ContextCompat.getColor(this, R.color.theme_low_accent),
                textPrimary = ContextCompat.getColor(this, R.color.theme_low_text_primary),
                textSecondary = ContextCompat.getColor(this, R.color.theme_low_text_secondary),
                ballColor = ContextCompat.getColor(this, R.color.theme_low_ball),
                conditionText = "🌙 Mode Malam (Neon)",
                perkText = getString(R.string.light_perk_low)
            )
            LightThemeState.MEDIUM -> ThemeColors(
                bgColor = ContextCompat.getColor(this, R.color.theme_med_bg),
                cardColor = ContextCompat.getColor(this, R.color.theme_med_card),
                strokeColor = ContextCompat.getColor(this, R.color.theme_med_card_stroke),
                accentColor = ContextCompat.getColor(this, R.color.theme_med_accent),
                textPrimary = ContextCompat.getColor(this, R.color.theme_med_text_primary),
                textSecondary = ContextCompat.getColor(this, R.color.theme_med_text_secondary),
                ballColor = ContextCompat.getColor(this, R.color.theme_med_ball),
                conditionText = "🌅 Mode Senja (Synthwave)",
                perkText = getString(R.string.light_perk_med)
            )
            LightThemeState.HIGH -> ThemeColors(
                bgColor = ContextCompat.getColor(this, R.color.theme_high_bg),
                cardColor = ContextCompat.getColor(this, R.color.theme_high_card),
                strokeColor = ContextCompat.getColor(this, R.color.theme_high_card_stroke),
                accentColor = ContextCompat.getColor(this, R.color.theme_high_accent),
                textPrimary = ContextCompat.getColor(this, R.color.theme_high_text_primary),
                textSecondary = ContextCompat.getColor(this, R.color.theme_high_text_secondary),
                ballColor = ContextCompat.getColor(this, R.color.theme_high_ball),
                conditionText = "☀️ Solar Overdrive (2x Poin!)",
                perkText = getString(R.string.light_perk_high)
            )
        }

        // 1. Root & Cards Backgrounds
        binding.rootScrollView.setBackgroundColor(bgColor)
        binding.cvHudCard.setCardBackgroundColor(cardColor)
        binding.cvHudCard.strokeColor = strokeColor
        binding.cvSimulationCard.setCardBackgroundColor(cardColor)
        binding.cvSimulationCard.strokeColor = strokeColor
        binding.cvMotionCard.setCardBackgroundColor(cardColor)
        binding.cvMotionCard.strokeColor = strokeColor

        binding.cvLightMascotCard.setCardBackgroundColor(cardColor)
        binding.cvLightMascotCard.strokeColor = strokeColor
        binding.cvLightMeterCard.setCardBackgroundColor(cardColor)
        binding.cvLightMeterCard.strokeColor = strokeColor
        binding.cvLightQuestsCard.setCardBackgroundColor(cardColor)
        binding.cvLightQuestsCard.strokeColor = strokeColor

        // 2. Titles & Texts
        binding.tvTitle.setTextColor(textPrimary)
        binding.tvSubtitle.setTextColor(textSecondary)
        binding.tvSimTitle.setTextColor(textPrimary)
        binding.tvMotionTitle.setTextColor(textPrimary)
        binding.tvLightMascotTitle.setTextColor(textPrimary)
        binding.tvLightTitle.setTextColor(textPrimary)
        binding.tvQuestTitle.setTextColor(textPrimary)
        binding.tvSpeed.setTextColor(textPrimary)
        binding.tvLuxDesc.text = perkText
        binding.tvLuxDesc.setTextColor(textSecondary)
        binding.tvLightCondition.text = conditionText
        binding.tvValLux.setTextColor(accentColor)

        // 3. Buttons & Gauges
        binding.btnCalibrate.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.btnCalibrate.setTextColor(bgColor)
        binding.btnCalibrate.iconTint = ColorStateList.valueOf(bgColor)

        binding.btnReset.setTextColor(textPrimary)
        binding.btnReset.iconTint = ColorStateList.valueOf(textPrimary)
        binding.btnReset.strokeColor = ColorStateList.valueOf(strokeColor)

        binding.pbLightLevel.setIndicatorColor(accentColor)
        binding.pbLightLevel.trackColor = strokeColor

        // 4. Custom views colors
        binding.tiltRadarView.setAccentColor(accentColor)
        binding.ballSimulationView.setBaseBallColor(ballColor)
        binding.ballSimulationView.setAccentColor(accentColor)
    }

    // BallSimulationView.GameEventListener callbacks
    override fun onScoreUpdated(score: Int, combo: Int, highScore: Int) {
        binding.tvHudScore.text = score.toString()
        binding.tvHudHighScore.text = highScore.toString()
        binding.tvHudCombo.text = if (combo > 1) "x$combo 🔥" else "x1"
        animateButtonPunch(binding.tvHudScore)
    }

    override fun onLevelUp(newLevel: Int) {
        binding.tvHudLevel.text = "Lv.$newLevel"
        animateButtonPunch(binding.tvHudLevel)
        Toast.makeText(this, "🎉 LEVEL UP! Sekarang Level $newLevel! 🚀", Toast.LENGTH_SHORT).show()
    }

    override fun onSpeedUpdated(speedPxPerSec: Float, gForce: Float) {
        binding.tvSpeed.text = String.format("Kecepatan: %.0f px/s", speedPxPerSec)
        binding.tvGForce.text = String.format("G-Force: %.2f g", gForce)

        when {
            speedPxPerSec > 600f -> {
                binding.tvMotionStatus.text = "🚀 Turbo Meluncur!"
                binding.tvMotionStatus.setTextColor(ContextCompat.getColor(this, R.color.game_combo))
            }
            speedPxPerSec > 250f -> {
                binding.tvMotionStatus.text = "⚡ Manuver Lincah"
                binding.tvMotionStatus.setTextColor(ContextCompat.getColor(this, R.color.game_gold))
            }
            gForce > 1.25f -> {
                binding.tvMotionStatus.text = "🔄 Miring Ekstrem"
                binding.tvMotionStatus.setTextColor(ContextCompat.getColor(this, R.color.motion_alert_color))
            }
            else -> {
                binding.tvMotionStatus.text = "🟢 Stabil Seimbang"
                binding.tvMotionStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_active_text))
            }
        }
    }

    override fun onHapticEvent(type: BallSimulationView.HapticType) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (type) {
                    BallSimulationView.HapticType.STAR_COLLECT -> {
                        vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    BallSimulationView.HapticType.BUBBLE_POP -> {
                        vib.vibrate(VibrationEffect.createOneShot(20, 180))
                    }
                    BallSimulationView.HapticType.WALL_BOUNCE -> {
                        vib.vibrate(VibrationEffect.createOneShot(15, 120))
                    }
                    BallSimulationView.HapticType.LEVEL_UP -> {
                        val timings = longArrayOf(0, 40, 50, 80)
                        val amplitudes = intArrayOf(0, 255, 0, 255)
                        vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                when (type) {
                    BallSimulationView.HapticType.STAR_COLLECT -> vib.vibrate(30)
                    BallSimulationView.HapticType.BUBBLE_POP -> vib.vibrate(20)
                    BallSimulationView.HapticType.WALL_BOUNCE -> vib.vibrate(15)
                    BallSimulationView.HapticType.LEVEL_UP -> vib.vibrate(120)
                }
            }
        } catch (_: Exception) {
            // Gracefully ignore
        }
    }

    private data class ThemeColors(
        val bgColor: Int,
        val cardColor: Int,
        val strokeColor: Int,
        val accentColor: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val ballColor: Int,
        val conditionText: String,
        val perkText: String
    )
}
