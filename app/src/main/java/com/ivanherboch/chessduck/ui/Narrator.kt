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
    /** Arrête la lecture et oublie ce qui restait à lire. */
    fun stop()
    /** Met la lecture en pause : [resume] la reprendra au début de la phrase en cours. */
    fun pause()
    /** Reprend une lecture mise en pause ; renvoie faux s'il n'y avait rien à reprendre. */
    fun resume(): Boolean
    /** Attend la fin de la phrase en cours (au plus une minute, au cas où le moteur ne répondrait plus). */
    suspend fun awaitIdle() {
        withTimeoutOrNull(60_000) { speaking.first { !it } }
    }

    companion object {
        /**
         * Où reprendre [text] quand la voix a été interrompue au caractère [offset] : au début de la phrase en cours,
         * comme les lecteurs à voix haute, pour ne jamais repartir au milieu d'un mot ni perdre le fil.
         */
        fun resumePoint(text: String, offset: Int): Int {
            val before = text.substring(0, offset.coerceIn(0, text.length))
            val end = before.indexOfLast { it == '.' || it == '!' || it == '?' || it == ':' }
            if (end < 0) return 0
            var i = end + 1
            while (i < text.length && text[i].isWhitespace()) i++
            return i
        }
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
    /** Texte en cours de lecture et position du mot prononcé (signalée par le moteur). */
    @Volatile private var spokenText = ""
    @Volatile private var spokenAt = 0
    /** Ce qui reste à lire après une pause, depuis le début de la phrase interrompue. */
    private var paused: String? = null

    private val listener = object : UtteranceProgressListener() {
        private fun finished(id: String?) {
            if (id == current.get().toString()) _speaking.value = false
        }
        override fun onStart(id: String?) = Unit
        override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
            if (id == current.get().toString()) spokenAt = start
        }
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
            // Certains moteurs de synthèse lèvent une exception en listant leurs voix : on fait alors comme s'il n'y en avait aucune.
            runCatching { engine.voices }.getOrNull().orEmpty()
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
        paused = null
        spokenText = text
        spokenAt = 0
        val engine = tts
        if (engine == null || _available.value == null) {
            pending = text
            prepare()
        } else {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id.toString())
        }
    }

    override fun stop() {
        paused = null
        silence()
    }

    override fun pause() {
        if (!_speaking.value) return
        val text = pending ?: spokenText
        val from = if (pending != null) 0 else Narrator.resumePoint(text, spokenAt)
        silence()
        paused = text.substring(from).takeIf { it.isNotBlank() }
    }

    override fun resume(): Boolean {
        val rest = paused ?: return false
        speak(rest)
        return true
    }

    /** Coupe la voix ; les fins de lecture de la phrase interrompue sont ignorées. */
    private fun silence() {
        pending = null
        current.incrementAndGet()
        _speaking.value = false
        tts?.stop()
    }

    /** Libère le moteur de synthèse ; une lecture ultérieure le redémarre (voir [speak]). */
    override fun close() {
        paused = null
        silence()
        tts?.shutdown()
        tts = null
    }
}
