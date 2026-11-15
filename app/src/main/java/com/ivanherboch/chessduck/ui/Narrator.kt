package com.ivanherboch.chessduck.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

/** Lecture à voix haute (commentaires des leçons de l'Académie). */
interface Narrator {
    /** Voix française hors-ligne disponible : null tant que le moteur n'a pas répondu. */
    val available: StateFlow<Boolean?>
    /** Vrai pendant la lecture d'une phrase (y compris en attendant le démarrage du moteur). */
    val speaking: StateFlow<Boolean>
    /** Démarre le moteur pour connaître [available] (aucune lecture) ; revérifie si aucune voix n'était installée. */
    fun prepare()
    /** Lit [text] en interrompant la phrase en cours. */
    fun speak(text: String)
    fun stop()
    /** Attend la fin de la phrase en cours (au plus une minute, au cas où le moteur ne répondrait plus). */
    suspend fun awaitIdle() {
        withTimeoutOrNull(60_000) { speaking.first { !it } }
    }
}

/**
 * Synthèse vocale d'Android, limitée à une voix française installée sur l'appareil (aucun accès réseau).
 * Le moteur n'est démarré qu'à la première utilisation. Si TalkBack est actif, rien n'est lu :
 * le lecteur d'écran lit déjà les textes et deux voix se superposeraient.
 */
class TtsNarrator(context: Context) : Narrator, Closeable {
    private val appContext = context.applicationContext
    private val a11y = appContext.getSystemService(AccessibilityManager::class.java)
    private val _available = MutableStateFlow<Boolean?>(null)
    override val available: StateFlow<Boolean?> = _available.asStateFlow()
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    /** Identifiant de la dernière phrase demandée : seule sa fin compte. */
    private val current = AtomicInteger()
    private var tts: TextToSpeech? = null
    private var pending: String? = null

    private val listener = object : UtteranceProgressListener() {
        private fun finished(id: String?) {
            if (id == current.get().toString()) _speaking.value = false
        }
        override fun onStart(id: String?) = Unit
        override fun onDone(id: String?) = finished(id)
        @Deprecated("Remplacé par onError(String, Int)", ReplaceWith("finished(id)"))
        override fun onError(id: String?) = finished(id)
        override fun onError(id: String?, errorCode: Int) = finished(id)
        override fun onStop(id: String?, interrupted: Boolean) = finished(id)
    }

    override fun prepare() {
        if (_available.value == false) {
            tts?.shutdown()
            tts = null
            _available.value = null
        }
        if (tts == null) tts = TextToSpeech(appContext, ::onInit).also { it.setOnUtteranceProgressListener(listener) }
    }

    private fun onInit(status: Int) {
        val engine = tts ?: return
        val voice = if (status == TextToSpeech.SUCCESS) {
            engine.voices.orEmpty()
                .filter { it.locale.language == "fr" && !it.isNetworkConnectionRequired && "notInstalled" !in it.features }
                .sortedWith(compareBy({ it.locale.country != "FR" }, { -it.quality }))
                .firstOrNull()
        } else null
        if (voice != null) engine.voice = voice
        _available.value = voice != null
        pending?.let { pending = null; if (voice != null) speak(it) else _speaking.value = false }
    }

    override fun speak(text: String) {
        if (a11y?.isTouchExplorationEnabled == true || _available.value == false) return
        val id = current.incrementAndGet()
        _speaking.value = true
        val engine = tts
        if (engine == null || _available.value == null) {
            pending = text
            prepare()
        } else {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id.toString())
        }
    }

    override fun stop() {
        pending = null
        current.incrementAndGet()
        _speaking.value = false
        tts?.stop()
    }

    override fun close() {
        tts?.shutdown()
        tts = null
    }
}
