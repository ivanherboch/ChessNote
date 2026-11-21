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
import java.io.Closeable

/**
 * Retours sensoriels : un unique son feutré de pièce en bois (aucun bip, aucun son de réussite)
 * et des vibrations subtiles (coup, touche, double vibration d'erreur, succès).
 */
class Feedback(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private var pool: SoundPool? = null
    private var wood = 0

    /** SoundPool natif, recréé (et le son rechargé) s'il a été libéré par [close]. */
    @Synchronized
    private fun pool(): SoundPool = pool ?: SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build().also { p ->
        pool = p
        wood = p.load(appContext, R.raw.wood_move, 1)
    }

    init {
        // Chargement du son dès la création : le premier coup joué est entendu.
        pool()
    }
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** Retour haptique activable dans les Paramètres (le son de pièce en bois n'est pas concerné). */
    @Volatile
    var hapticsEnabled = true

    /** Bruit de pièce posée, désactivable dans les Paramètres. */
    @Volatile
    var soundEnabled = true

    private fun buzz(effect: VibrationEffect) {
        if (!hapticsEnabled) return
        runCatching { vibrator?.takeIf { it.hasVibrator() }?.vibrate(effect) }
    }

    /**
     * Pièce posée sur l'échiquier : bruit de bois discret + légère vibration, un peu plus marquée pour une prise,
     * doublée pour un échec et montante pour une promotion (différences subtiles, jamais d'alarme).
     */
    fun move(capture: Boolean = false, check: Boolean = false, promotion: Boolean = false) {
        if (soundEnabled) pool().play(wood, if (capture) 0.28f else 0.2f, if (capture) 0.28f else 0.2f, 1, 0, 1f)
        buzz(
            when {
                promotion -> VibrationEffect.createWaveform(longArrayOf(0, 12, 40, 18), intArrayOf(0, 60, 0, 120), -1)
                check -> VibrationEffect.createWaveform(longArrayOf(0, 14, 50, 14), intArrayOf(0, 90, 0, 90), -1)
                capture -> VibrationEffect.createOneShot(18, 110)
                else -> VibrationEffect.createOneShot(12, 70)
            },
        )
    }

    fun key() = buzz(VibrationEffect.createOneShot(8, 45))
    fun error() = buzz(VibrationEffect.createWaveform(longArrayOf(0, 35, 70, 35), intArrayOf(0, 150, 0, 150), -1))
    fun success() = buzz(VibrationEffect.createOneShot(45, 130))

    /** Libère le SoundPool natif ; il est recréé au prochain son joué. */
    @Synchronized
    override fun close() {
        pool?.release()
        pool = null
    }
}

val LocalFeedback = staticCompositionLocalOf<Feedback?> { null }
