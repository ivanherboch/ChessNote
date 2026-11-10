package com.ivanherboch.chessduck.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf
import com.ivanherboch.chessduck.R

/**
 * Retours sensoriels : un unique son feutré de pièce en bois (aucun bip, aucun son de réussite)
 * et des vibrations subtiles (coup, touche, double vibration d'erreur, succès).
 */
class Feedback(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val wood = pool.load(context, R.raw.wood_move, 1)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun buzz(effect: VibrationEffect) {
        runCatching { vibrator?.takeIf { it.hasVibrator() }?.vibrate(effect) }
    }

    /** Pièce posée sur l'échiquier : bruit de bois discret + légère vibration. */
    fun move() {
        pool.play(wood, 0.55f, 0.55f, 1, 0, 1f)
        buzz(VibrationEffect.createOneShot(12, 70))
    }

    fun key() = buzz(VibrationEffect.createOneShot(8, 45))
    fun error() = buzz(VibrationEffect.createWaveform(longArrayOf(0, 35, 70, 35), intArrayOf(0, 150, 0, 150), -1))
    fun success() = buzz(VibrationEffect.createOneShot(45, 130))
}

val LocalFeedback = staticCompositionLocalOf<Feedback?> { null }
