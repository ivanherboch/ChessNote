package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ivanherboch.chessduck.data.LessonProgressRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import com.ivanherboch.chessduck.ui.LinePlaybackController
import com.ivanherboch.chessduck.ui.Narrator
import com.ivanherboch.chessduck.chess.SpokenFr
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.content.LessonCategory
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.moveAnim
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class AcademyState(
    val lessonId: Int = 1,
    /** null = toutes les catégories. */
    val filter: LessonCategory? = null,
    val challenge: Boolean = false,
    val cursor: Int = 0,
    val playing: Boolean = false,
    val flipped: Boolean = false,
    val anim: BoardAnim? = null,
    val hintStage: Int = 0,
    val message: String = "",
    val tone: Tone = Tone.INFO,
    val done: Boolean = false,
)

class AcademyViewModel(
    private val prefs: SettingsRepository,
    private val lessons: LessonProgressRepository,
    private val narrator: Narrator,
    /** Leçon, mode et position mémorisés pour survivre à l'arrêt du processus par Android. */
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(AcademyState())
    val state: StateFlow<AcademyState> = _state.asStateFlow()
    val mastered = lessons.masteredLessons().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    /** Lecture automatique et séquences du mode Défi : un seul travail à la fois. */
    private val playback = LinePlaybackController(viewModelScope)

    val lesson: Lesson get() = Lessons.byId(state.value.lessonId)

    /** Lecture à voix haute des commentaires du mode Guidé (réglage), et phrase en cours de lecture. */
    val narration: StateFlow<Boolean> = prefs.settings.map { it.narration }.distinctUntilChanged()
        .onEach { if (!it) narrator.stop() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val speaking: StateFlow<Boolean> = narrator.speaking

    init {
        viewModelScope.launch {
            val restoredId = saved.get<Int>(KEY_LESSON)
            val restoredChallenge = saved.get<Boolean>(KEY_CHALLENGE) ?: false
            val restoredCursor = saved.get<Int>(KEY_CURSOR) ?: 0
            open(restoredId ?: prefs.settings.first().lessonId, save = false)
            if (restoredId != null) {
                if (restoredChallenge) setChallenge(true) else if (restoredCursor > 0) goTo(restoredCursor)
            }
            state.collect { st ->
                saved[KEY_LESSON] = st.lessonId
                saved[KEY_CHALLENGE] = st.challenge
                saved[KEY_CURSOR] = st.cursor
            }
        }
    }

    private companion object {
        const val KEY_LESSON = "academy_lesson"
        const val KEY_CHALLENGE = "academy_challenge"
        const val KEY_CURSOR = "academy_cursor"
    }

    fun open(id: Int, save: Boolean = true) {
        playback.cancel()
        val l = Lessons.byId(id)
        val challenge = state.value.challenge
        _state.update {
            it.copy(
                lessonId = l.id, cursor = 0, playing = false, flipped = !l.heroWhite, anim = null, hintStage = 0, done = false,
                filter = if (it.filter != null && it.filter != l.category) l.category else it.filter,
            )
        }
        if (save) viewModelScope.launch { prefs.setLesson(l.id) }
        if (challenge) startChallenge() else _state.update { it.copy(message = "", tone = Tone.INFO) }
        // Ouverture choisie par l'utilisateur (pas la restauration au lancement) : on lit l'objectif.
        if (save) narrate()
    }

    fun setFilter(cat: LessonCategory?) {
        _state.update { it.copy(filter = cat) }
        if (cat != null && lesson.category != cat) open(Lessons.all.first { it.category == cat }.id)
    }

    fun flip() = _state.update { it.copy(flipped = !it.flipped) }

    fun setChallenge(on: Boolean) {
        if (state.value.challenge == on) return
        playback.cancel()
        _state.update { it.copy(challenge = on, cursor = 0, playing = false, anim = null, hintStage = 0, done = false, message = "", tone = Tone.INFO) }
        if (on) {
            narrator.stop()
            startChallenge()
        } else narrate()
    }

    // ---- Mode Guidé ----
    fun goTo(target: Int) {
        val l = lesson
        val s = state.value
        val t = target.coerceIn(0, l.line.size)
        if (t == s.cursor) return
        val anim = LinePlaybackController.transition(l.line, s.cursor, t)
        _state.update { it.copy(cursor = t, anim = anim) }
        narrate()
    }

    // ---- Lecture à voix haute (mode Guidé uniquement) ----
    /** Ce qui est lu pour la position affichée : l'objectif de la leçon, puis chaque coup et son commentaire. */
    private fun spokenStep(): String {
        val l = lesson
        val c = state.value.cursor
        return if (c == 0) SpokenFr.text("${l.title}. ${l.summary}") else SpokenFr.text("${SpokenFr.move(l.line.sans[c - 1])}. ${l.texts[c - 1]}")
    }

    private fun narrate() {
        if (narration.value && !state.value.challenge) narrator.speak(spokenStep())
    }

    /** Bouton haut-parleur : arrête la phrase en cours, ou relit celle de la position affichée. */
    fun toggleSpeech() = if (speaking.value) narrator.stop() else narrator.speak(spokenStep())

    /** L'écran n'est plus visible (autre onglet, application en arrière-plan) : silence et pause. */
    fun silence() {
        pause()
        narrator.stop()
    }

    override fun onCleared() = narrator.stop()

    fun step(delta: Int) { pause(); goTo(state.value.cursor + delta) }
    fun rewind() { pause(); goTo(0) }

    fun pause() {
        playback.cancel()
        _state.update { it.copy(playing = false) }
    }

    fun togglePlay() {
        if (state.value.playing) return pause()
        playback.cancel()
        if (state.value.cursor >= lesson.line.size) goTo(0)
        _state.update { it.copy(playing = true) }
        playback.autoplay(
            cursor = { state.value.cursor }, size = { lesson.line.size },
            // Avec la voix, on attend la fin du commentaire puis on marque une courte pause.
            delayFor = { c -> if (narration.value) 600L else if (c == 0) 700L else 2600L }, checkBeforeDelay = true,
            advance = ::goTo, onEnd = { _state.update { it.copy(playing = false) } },
            awaitReady = narrator::awaitIdle,
        )
    }

    // ---- Mode Défi ----
    private fun sideName(l: Lesson) = if (l.heroWhite) "Blancs" else "Noirs"

    private fun startChallenge() {
        val l = lesson
        _state.update {
            it.copy(
                cursor = 0, anim = null, hintStage = 0, done = false, tone = Tone.INFO, flipped = !l.heroWhite,
                message = "À vous de jouer les coups des ${sideName(l)} sur l'échiquier. ${l.summary}",
            )
        }
        advanceOpponent()
    }

    fun restartChallenge() {
        playback.cancel()
        startChallenge()
    }

    private fun heroToMove(cursor: Int): Boolean {
        val l = lesson
        return cursor < l.line.size && l.line.positions[cursor].whiteToMove == l.heroWhite
    }

    /** Joue les réponses adverses jusqu'au prochain coup du joueur, puis conclut la leçon si elle est finie. */
    private fun advanceOpponent() {
        playback.launch {
            val l = lesson
            while (state.value.cursor < l.line.size && !heroToMove(state.value.cursor)) {
                delay(650)
                val cur = state.value.cursor
                _state.update { it.copy(cursor = cur + 1, anim = moveAnim(l.line.positions[cur], l.line.moves[cur]), hintStage = 0) }
            }
            if (state.value.cursor >= l.line.size) {
                lessons.markLesson(l.id)
                _state.update {
                    it.copy(done = true, tone = Tone.GOOD, message = "Leçon maîtrisée ! Vous avez joué tous les coups clés de « ${l.title} ».")
                }
            }
        }
    }

    /** Coup du joueur en mode Défi ; renvoie vrai s'il est correct. */
    fun challengeMove(m: Move): Boolean {
        val s = state.value
        val l = lesson
        if (!s.challenge || s.done || !heroToMove(s.cursor)) return false
        val expected = l.line.moves[s.cursor]
        if (m == expected) {
            _state.update {
                it.copy(
                    cursor = s.cursor + 1, anim = moveAnim(l.line.positions[s.cursor], m), hintStage = 0, tone = Tone.GOOD,
                    message = "${l.line.sans[s.cursor]} — ${l.texts[s.cursor]}",
                )
            }
            advanceOpponent()
            return true
        }
        val played = l.line.positions[s.cursor].san(m)
        _state.update {
            it.copy(tone = Tone.BAD, message = "$played n'est pas le coup de la leçon. Réfléchissez à l'idée du thème, ou demandez un indice.")
        }
        return false
    }

    fun hint() {
        val s = state.value
        if (!s.challenge || s.done || !heroToMove(s.cursor)) return
        val stage = (s.hintStage + 1).coerceAtMost(2)
        _state.update {
            it.copy(
                hintStage = stage, tone = Tone.GOLD,
                message = if (stage == 1) "Indice 1/2 : la pièce à jouer est surlignée en vert." else "Indice 2/2 : suivez la flèche.",
            )
        }
    }

    fun nextLesson() = shiftLesson(1)
    fun prevLesson() = shiftLesson(-1)

    /** Leçon voisine dans l'ordre du catalogue (limité à la catégorie filtrée s'il y en a une). */
    private fun shiftLesson(delta: Int) {
        val all = Lessons.all
        val s = state.value
        val pool = if (s.filter != null) all.filter { it.category == s.filter } else all
        val idx = pool.indexOfFirst { it.id == s.lessonId }.coerceAtLeast(0)
        open(pool[(idx + delta + pool.size) % pool.size].id)
    }

    /** Dans le mode Guidé, saute directement au coup [n] (nombre de demi-coups joués). */
    fun jump(n: Int) { pause(); goTo(n) }
    fun last() { pause(); goTo(lesson.line.size) }
}
