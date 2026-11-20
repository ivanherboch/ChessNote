package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.SpokenFr
import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.content.LessonCategory
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.data.LessonProgress
import com.ivanherboch.chessduck.data.LessonProgressRepository
import com.ivanherboch.chessduck.data.PuzzleRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.LinePlaybackController
import com.ivanherboch.chessduck.ui.Narrator
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.moveAnim
import com.ivanherboch.chessduck.util.runCatchingSuspend
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** Signal ponctuel de retour haptique (réussite ou erreur), identifié pour n'être joué qu'une fois. */
@Immutable
data class Pulse(val id: Long, val good: Boolean)

/**
 * Série de validation : [PRACTICE_SIZE] problèmes inédits du thème de la leçon. Un problème compte s'il est résolu
 * sans erreur et sans l'indice qui montre le coup.
 */
@Immutable
data class Practice(
    val ids: List<String> = emptyList(),
    val index: Int = 0,
    val results: List<Boolean> = emptyList(),
    val line: Line? = null,
    val cursor: Int = 0,
    val hintStage: Int = 0,
    /** Le problème en cours est raté : la solution est montrée, on passe au suivant. */
    val failed: Boolean = false,
    /** Le problème en cours est résolu. */
    val solved: Boolean = false,
    val anim: BoardAnim? = null,
    val message: String = "",
    val tone: Tone = Tone.INFO,
    val loading: Boolean = true,
    /** Série terminée (résultat dans [results]). */
    val finished: Boolean = false,
) {
    val heroWhite: Boolean get() = line?.positions?.get(0)?.whiteToMove == false
    val score: Int get() = results.count { it }
}

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
    /** Mode Guidé : arrêts « À vous » déjà résolus (ou dévoilés) dans la leçon ouverte. */
    val answered: Set<Int> = emptySet(),
    /** Mode Guidé : réaction au coup proposé pendant un arrêt (null : aucun essai). */
    val note: String? = null,
    val noteTone: Tone = Tone.INFO,
    /** Mode Défi : erreurs et indice le plus fort utilisé, pour les étoiles. */
    val errors: Int = 0,
    val maxHint: Int = 0,
    /** Étoiles obtenues au Défi qui vient d'être réussi (0 tant qu'il n'est pas fini). */
    val stars: Int = 0,
    /** L'Ordinateur juge le coup proposé : l'échiquier attend. */
    val judging: Boolean = false,
    /** Réfutation d'un mauvais coup du Défi : position après la réponse adverse, compte à rebours avant le retour. */
    val refute: Refutation? = null,
    val refuteUntil: Long = 0L,
    val refuteHold: Long = 0L,
    val pulse: Pulse? = null,
    /** Série d'exercices de validation en cours (null : leçon). */
    val practice: Practice? = null,
)

class AcademyViewModel(
    private val prefs: SettingsRepository,
    private val lessons: LessonProgressRepository,
    private val narrator: Narrator,
    private val engine: ChessEngine,
    private val puzzles: PuzzleRepository,
    /** Leçon, mode et position mémorisés pour survivre à l'arrêt du processus par Android. */
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val random: Random = Random(System.nanoTime()),
) : ViewModel() {
    private val _state = MutableStateFlow(AcademyState())
    val state: StateFlow<AcademyState> = _state.asStateFlow()
    val mastered = lessons.masteredLessons().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    /** Progression détaillée (étoiles, exercices) par leçon. */
    val progress = lessons.progress().map { l -> l.associateBy { it.lessonId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    /** Lecture automatique et séquences du mode Défi : un seul travail à la fois. */
    private val playback = LinePlaybackController(viewModelScope)
    private val judge = MoveJudge(engine)
    private var judgeJob: Job? = null
    private var practiceJob: Job? = null
    private var pulses = 0L

    val lesson: Lesson get() = Lessons.byId(state.value.lessonId)

    /** Lecture à voix haute des commentaires du mode Guidé (réglage), et phrase en cours de lecture. */
    val narration: StateFlow<Boolean> = prefs.settings.map { it.narration }.distinctUntilChanged()
        .onEach { if (!it) narrator.stop() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val speaking: StateFlow<Boolean> = narrator.speaking
    /** Voix française hors-ligne installée (null : pas encore vérifié, voir [checkVoice]). */
    val voice: StateFlow<Boolean?> = narrator.available

    fun checkVoice() = narrator.prepare()

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
        const val PRACTICE_SIZE = 5
        const val PRACTICE_PASS = 4
        val RED = Color(0xFFEF4444)
    }

    private fun pulse(good: Boolean) = _state.update { it.copy(pulse = Pulse(++pulses, good)) }

    /** Interrompt toute séquence en cours (lecture, réponse adverse, jugement, réfutation, exercices). */
    private fun cancelAll() {
        playback.cancel()
        judgeJob?.cancel()
        practiceJob?.cancel()
    }

    fun open(id: Int, save: Boolean = true) {
        cancelAll()
        val l = Lessons.byId(id)
        val challenge = state.value.challenge
        _state.update {
            it.copy(
                lessonId = l.id, cursor = 0, playing = false, flipped = !l.heroWhite, anim = null, hintStage = 0, done = false,
                filter = if (it.filter != null && it.filter != l.category) l.category else it.filter,
                answered = emptySet(), note = null, judging = false, refute = null, refuteUntil = 0L, practice = null,
                errors = 0, maxHint = 0, stars = 0,
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
        cancelAll()
        // Retour au Guidé : on garde la position atteinte (après un Défi réussi, on relit la leçon là où l'on en est).
        val keep = if (on) 0 else state.value.cursor.coerceIn(0, lesson.line.size)
        _state.update {
            it.copy(
                challenge = on, cursor = keep, playing = false, anim = null, hintStage = 0, done = false, message = "", tone = Tone.INFO,
                note = null, judging = false, refute = null, refuteUntil = 0L, practice = null, errors = 0, maxHint = 0, stars = 0,
            )
        }
        if (on) {
            narrator.stop()
            startChallenge()
        } else narrate()
    }

    // ---- Mode Guidé ----

    /** Le mode Guidé attend ici le coup de l'élève (« À vous : que jouez-vous ici ? »). */
    fun asking(s: AcademyState = state.value): Boolean =
        !s.challenge && s.practice == null && s.cursor in lesson.pauseSteps && s.cursor !in s.answered && s.cursor < lesson.line.size

    fun goTo(target: Int) {
        val l = lesson
        val s = state.value
        val t = target.coerceIn(0, l.line.size)
        if (t == s.cursor) return
        val anim = LinePlaybackController.transition(l.line, s.cursor, t)
        _state.update { it.copy(cursor = t, anim = anim, note = null) }
        narrate()
    }

    /** Arrêt « À vous » : dévoile le coup de la leçon (bouton suivant ou Lecture) et le marque comme vu. */
    private fun reveal() {
        val c = state.value.cursor
        _state.update { it.copy(answered = it.answered + c) }
        goTo(c + 1)
    }

    /** Coup proposé pendant un arrêt du mode Guidé : bravo s'il est juste (ou aussi bon), sinon on peut réessayer. */
    fun guideMove(m: Move) {
        val s = state.value
        if (!asking(s) || s.judging) return
        val l = lesson
        val c = s.cursor
        val pos = l.line.positions[c]
        if (!pos.isLegal(m)) return
        val expected = l.line.moves[c]
        val san = pos.san(m)
        _state.update { it.copy(judging = true) }
        judgeJob = viewModelScope.launch {
            val v = judge.judge(pos, m, expected, l.category == LessonCategory.ENDGAME)
            val lessonSan = l.line.sans[c]
            when (v) {
                Verdict.EXACT, Verdict.EQUIVALENT -> {
                    pulse(true)
                    _state.update { it.copy(judging = false, answered = it.answered + c) }
                    goTo(c + 1)
                    val bravo = if (v == Verdict.EXACT) "Bravo, c'est le coup de la leçon !" else "Bon coup aussi ! La leçon poursuit avec $lessonSan."
                    _state.update { it.copy(note = bravo, noteTone = Tone.GOOD) }
                }
                else -> {
                    pulse(false)
                    _state.update {
                        it.copy(
                            judging = false, noteTone = Tone.GOLD,
                            note = "$san n'est pas le coup de la leçon. Cherchez encore, ou touchez Lecture pour le découvrir.",
                        )
                    }
                }
            }
        }
    }

    // ---- Lecture à voix haute (mode Guidé uniquement) ----
    /** Ce qui est lu pour la position affichée : l'objectif de la leçon, puis chaque coup et son commentaire. */
    private fun spokenStep(): String {
        val l = lesson
        val c = state.value.cursor
        val base = if (c == 0) "${l.title}. ${l.summary}" else "${SpokenFr.move(l.line.sans[c - 1])}. ${l.texts[c - 1]}"
        return SpokenFr.text(if (asking()) "$base $ASK" else base)
    }

    private fun narrate() {
        val s = state.value
        if (narration.value && !s.challenge && s.practice == null) narrator.speak(spokenStep())
    }

    /** Bouton haut-parleur : arrête la phrase en cours, ou relit celle de la position affichée. */
    fun toggleSpeech() = if (speaking.value) narrator.stop() else narrator.speak(spokenStep())

    /** L'écran n'est plus visible (autre onglet, application en arrière-plan) : silence et pause. */
    fun silence() {
        pause()
        narrator.stop()
    }

    override fun onCleared() = narrator.stop()

    fun step(delta: Int) {
        pause()
        if (delta > 0 && asking()) reveal() else goTo(state.value.cursor + delta)
    }

    fun rewind() { pause(); goTo(0) }

    /** Arrête la lecture automatique ; la voix se met en pause et reprendra au début de sa phrase. */
    fun pause() {
        playback.cancel()
        narrator.pause()
        _state.update { it.copy(playing = false) }
    }

    fun togglePlay() {
        if (state.value.playing) return pause()
        playback.cancel()
        when {
            state.value.cursor >= lesson.line.size -> goTo(0)
            asking() -> reveal()
            else -> narrator.resume()
        }
        _state.update { it.copy(playing = true) }
        playback.autoplay(
            cursor = { state.value.cursor }, size = { lesson.line.size },
            // Avec la voix, on attend la fin du commentaire puis on marque une courte pause.
            delayFor = { c -> if (narration.value) 600L else if (c == 0) 700L else 2600L }, checkBeforeDelay = true,
            // Un arrêt « À vous » interrompt la lecture : l'élève cherche le coup.
            advance = { n -> goTo(n); if (asking()) { playback.cancel(); _state.update { it.copy(playing = false) } } },
            onEnd = { _state.update { it.copy(playing = false) } },
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
                errors = 0, maxHint = 0, stars = 0, refute = null, refuteUntil = 0L, judging = false,
                message = "À vous de jouer les coups des ${sideName(l)} sur l'échiquier. ${l.summary}",
            )
        }
        advanceOpponent()
    }

    fun restartChallenge() {
        cancelAll()
        startChallenge()
    }

    private fun heroToMove(cursor: Int): Boolean {
        val l = lesson
        return cursor < l.line.size && l.line.positions[cursor].whiteToMove == l.heroWhite
    }

    /**
     * Joue les réponses adverses jusqu'au prochain coup du joueur, en ajoutant leur commentaire au message,
     * puis conclut la leçon si elle est finie (le dernier commentaire et sa règle « À retenir » restent affichés).
     */
    private fun advanceOpponent() {
        playback.launch {
            val l = lesson
            while (state.value.cursor < l.line.size && !heroToMove(state.value.cursor)) {
                delay(650)
                val cur = state.value.cursor
                val reply = "Réponse ${l.line.sans[cur]} : ${l.texts[cur]}"
                _state.update {
                    it.copy(
                        cursor = cur + 1, anim = moveAnim(l.line.positions[cur], l.line.moves[cur]), hintStage = 0,
                        message = if (it.message.isEmpty()) reply else it.message + "\n\n" + reply,
                    )
                }
            }
            if (state.value.cursor >= l.line.size) finishChallenge()
        }
    }

    private suspend fun finishChallenge() {
        val l = lesson
        val s = state.value
        val stars = when {
            s.errors == 0 && s.maxHint == 0 -> 3
            s.errors <= 1 && s.maxHint <= 1 -> 2
            else -> 1
        }
        val takeaway = if (s.message.contains("À retenir")) "" else "\n\nÀ retenir : ${l.takeaway}"
        val old = lessons.progress(l.id)
        val practiced = (old?.practicePassed ?: 0) >= PRACTICE_PASS
        val bestStars = maxOf(stars, old?.stars ?: 0)
        val nowMastered = old?.mastered == true || (bestStars >= 2 && (l.theme.isEmpty() || practiced))
        val next = when {
            nowMastered -> ""
            stars < 2 -> "\n\nRejouez le Défi avec au plus une erreur pour viser la maîtrise."
            else -> "\n\nValidez maintenant la leçon sur $PRACTICE_SIZE problèmes inédits du même thème."
        }
        lessons.save(
            (old ?: LessonProgress(l.id, 0)).copy(
                masteredAt = if (old?.mastered == true) old.masteredAt else if (nowMastered) System.currentTimeMillis() else 0,
                stars = bestStars, errors = s.errors, hintsUsed = s.maxHint,
            ),
        )
        _state.update { it.copy(done = true, stars = stars, tone = Tone.GOOD, message = it.message + takeaway + next) }
    }

    /**
     * Coup du joueur en mode Défi. Renvoie vrai si c'est le coup de la leçon (accepté tout de suite) ; sinon
     * l'Ordinateur juge le coup : aussi bon, il est accepté et la leçon reprend son propre coup ; moins bon,
     * il est réfuté puis la position d'avant l'erreur revient.
     */
    fun challengeMove(m: Move): Boolean {
        val s = state.value
        val l = lesson
        if (!s.challenge || s.done || s.judging || s.refute != null || !heroToMove(s.cursor)) return false
        val pos = l.line.positions[s.cursor]
        if (!pos.isLegal(m)) return false
        val expected = l.line.moves[s.cursor]
        if (m == expected) {
            pulse(true)
            accept(s.cursor, "${l.line.sans[s.cursor]} : ${l.texts[s.cursor]}")
            return true
        }
        _state.update { it.copy(judging = true) }
        judgeJob = viewModelScope.launch {
            when (judge.judge(pos, m, expected, l.category == LessonCategory.ENDGAME)) {
                Verdict.EXACT, Verdict.EQUIVALENT -> {
                    pulse(true)
                    _state.update { it.copy(judging = false) }
                    accept(s.cursor, "Bon coup aussi ! La leçon poursuit avec ${l.line.sans[s.cursor]} : ${l.texts[s.cursor]}")
                }
                Verdict.WORSE -> refute(pos, m)
                Verdict.UNAVAILABLE -> {
                    pulse(false)
                    _state.update {
                        it.copy(
                            judging = false, errors = it.errors + 1, tone = Tone.BAD,
                            message = "${pos.san(m)} n'est pas le coup de la leçon. Réfléchissez à l'idée du thème, ou demandez un indice.",
                        )
                    }
                }
            }
        }
        return false
    }

    /** Joue le coup de la leçon (le bon coup, ou celui qui suit un coup équivalent) puis les réponses adverses. */
    private fun accept(cur: Int, text: String) {
        val l = lesson
        _state.update {
            it.copy(cursor = cur + 1, anim = moveAnim(l.line.positions[cur], l.line.moves[cur]), hintStage = 0, tone = Tone.GOOD, message = text)
        }
        advanceOpponent()
    }

    /**
     * Mauvais coup : il est joué, l'Ordinateur montre la réponse adverse (flèche rouge) pendant [REFUTE_HOLD] ms avec
     * un compte à rebours, puis la position d'avant l'erreur revient. « Reprendre au coup manqué » y revient tout de suite.
     */
    private suspend fun refute(pos: Position, m: Move) {
        pulse(false)
        val l = lesson
        val san = pos.san(m)
        val after = pos.play(m)
        val opp = if (l.heroWhite) "Noirs" else "Blancs"
        _state.update {
            it.copy(
                judging = false, errors = it.errors + 1, tone = Tone.BAD, anim = moveAnim(pos, m),
                refute = Refutation(after, m, null), message = "$san n'est pas le bon coup. Regardez la réponse des $opp…",
            )
        }
        val reply = if (after.legalMoves.isEmpty()) null
        else runCatchingSuspend { withTimeoutOrNull(1800) { engine.search(after.toFen(), 380).best } }.getOrNull()?.takeIf { after.isLegal(it) }
        delay(650)
        val text = if (reply == null) {
            if (after.legalMoves.isEmpty() && !after.inCheck) "$san met l'adversaire pat : la partie serait nulle."
            else "$san n'est pas le coup de la leçon."
        } else "Après $san, les $opp répondent ${after.san(reply)}. Cherchez mieux : pensez à l'idée de la leçon."
        _state.update {
            it.copy(
                anim = reply?.let { r -> moveAnim(after, r) } ?: it.anim,
                refute = Refutation(reply?.let { r -> after.play(r) } ?: after, reply ?: m, reply?.let { r -> Arrow(r.from, r.to, RED) }),
                refuteUntil = System.currentTimeMillis() + REFUTE_HOLD, refuteHold = REFUTE_HOLD, message = text,
            )
        }
        delay(REFUTE_HOLD)
        resumeAtMiss()
    }

    /** « Reprendre au coup manqué » : annule le mauvais coup et revient à la position d'avant l'erreur. */
    fun resumeAtMiss() {
        if (state.value.refute == null) return
        judgeJob?.cancel()
        _state.update {
            it.copy(refute = null, refuteUntil = 0L, anim = null, tone = Tone.INFO, message = "Position rétablie : à vous de retrouver le bon coup.")
        }
    }

    /** Suspend le retour automatique : la réfutation reste affichée jusqu'à « Reprendre au coup manqué ». */
    fun holdRefutation() {
        if (state.value.refute == null || state.value.refuteUntil == 0L) return
        judgeJob?.cancel()
        _state.update { it.copy(refuteUntil = 0L) }
    }

    fun hint() {
        val s = state.value
        if (!s.challenge || s.done || s.judging || s.refute != null || !heroToMove(s.cursor)) return
        val stage = (s.hintStage + 1).coerceAtMost(2)
        _state.update {
            it.copy(
                hintStage = stage, maxHint = maxOf(it.maxHint, stage), tone = Tone.GOLD,
                message = if (stage == 1) "Indice 1/2 : la pièce à jouer est surlignée en vert." else "Indice 2/2 : suivez la flèche.",
            )
        }
    }

    // ---- Série de validation : problèmes inédits du thème ----

    /** Tranche de classement des problèmes selon le palier de la leçon. */
    private fun ratingRange(level: Int): IntRange = when (level) {
        1, 2 -> 400..1300
        3 -> 900..1600
        4 -> 1300..2000
        else -> 1700..2700
    }

    fun startPractice() {
        val l = lesson
        if (l.theme.isEmpty()) return
        cancelAll()
        narrator.stop()
        _state.update { it.copy(practice = Practice(message = "Préparation des problèmes…")) }
        practiceJob = viewModelScope.launch {
            val ids = ArrayList<String>()
            for (range in listOf(ratingRange(l.level), 0..4000)) {
                repeat(PRACTICE_SIZE * 2) {
                    if (ids.size >= PRACTICE_SIZE) return@repeat
                    val id = runCatchingSuspend { puzzles.randomId(listOf(l.theme), range, ids.lastOrNull() ?: "", random) }.getOrNull()
                    if (id != null && id !in ids) ids.add(id)
                }
                if (ids.size >= PRACTICE_SIZE) break
            }
            if (ids.isEmpty()) {
                _state.update { it.copy(practice = Practice(loading = false, finished = true, message = "Aucun problème de ce thème n'est disponible.")) }
                return@launch
            }
            _state.update { it.copy(practice = Practice(ids = ids, loading = false)) }
            showPractice(0)
        }
    }

    private suspend fun showPractice(index: Int) {
        val ids = state.value.practice?.ids ?: return
        val p = runCatchingSuspend { puzzles.byId(ids[index]) }.getOrNull()
        val line = p?.let { runCatching { Line.fromUci(it.fen, it.moves) }.getOrNull() }
        if (line == null) {
            _state.update { s -> s.copy(practice = s.practice?.copy(results = s.practice.results + false)) }
            nextPracticeOrFinish(index)
            return
        }
        val side = if (line.positions[1].whiteToMove) "Blancs" else "Noirs"
        _state.update { s ->
            s.copy(
                practice = s.practice?.copy(
                    index = index, line = line, cursor = 0, hintStage = 0, failed = false, solved = false, anim = null, tone = Tone.INFO,
                    message = "Problème ${index + 1} sur ${ids.size} : les $side jouent et appliquent l'idée de la leçon.",
                ),
            )
        }
        delay(600)
        _state.update { s -> s.copy(practice = s.practice?.copy(cursor = 1, anim = moveAnim(line.positions[0], line.moves[0]))) }
    }

    /** Coup de l'élève dans la série : la solution (ou tout mat immédiat) avance, une autre suite échoue le problème. */
    fun practiceMove(m: Move) {
        val pr = state.value.practice ?: return
        val line = pr.line ?: return
        if (pr.failed || pr.solved || pr.cursor >= line.size || line.positions[pr.cursor].whiteToMove != pr.heroWhite) return
        val pos = line.positions[pr.cursor]
        if (!pos.isLegal(m)) return
        val expected = line.moves[pr.cursor]
        if (m != expected && !pos.play(m).isCheckmate) {
            pulse(false)
            _state.update { s ->
                s.copy(
                    practice = pr.copy(
                        failed = true, results = pr.results + false, tone = Tone.BAD,
                        message = "${pos.san(m)} n'est pas la solution. Le bon coup était ${line.sans[pr.cursor]} : regardez la flèche verte.",
                    ),
                )
            }
            return
        }
        val cur = pr.cursor
        val finished = cur + 1 >= line.size || pos.play(m).isCheckmate
        pulse(true)
        if (finished) {
            val ok = pr.hintStage < 2
            practiceJob = viewModelScope.launch { if (ok) runCatchingSuspend { puzzles.markSolved(pr.ids[pr.index]) } }
            _state.update { s ->
                s.copy(
                    practice = pr.copy(
                        cursor = cur + 1, anim = moveAnim(pos, m), solved = true, results = pr.results + ok, tone = if (ok) Tone.GOOD else Tone.GOLD,
                        line = if (m == expected) line else Line(line.startFen, line.moves.take(cur) + m),
                        message = if (ok) "Résolu ! Vous avez appliqué l'idée de la leçon." else "Résolu, mais avec l'indice qui montre le coup : ce problème ne compte pas.",
                    ),
                )
            }
            return
        }
        _state.update { s -> s.copy(practice = pr.copy(cursor = cur + 1, anim = moveAnim(pos, m), hintStage = 0, tone = Tone.GOOD, message = "Bien vu. La suite arrive…")) }
        practiceJob = viewModelScope.launch {
            delay(600)
            _state.update { s ->
                val p2 = s.practice ?: return@update s
                s.copy(practice = p2.copy(cursor = cur + 2, anim = moveAnim(line.positions[cur + 1], line.moves[cur + 1]), message = "L'adversaire répond ${line.sans[cur + 1]} : à vous de continuer."))
            }
        }
    }

    fun practiceHint() {
        val pr = state.value.practice ?: return
        if (pr.failed || pr.solved || pr.line == null) return
        val stage = (pr.hintStage + 1).coerceAtMost(2)
        _state.update { s ->
            s.copy(
                practice = pr.copy(
                    hintStage = stage, tone = Tone.GOLD,
                    message = if (stage == 1) "Indice 1/2 : la pièce à jouer est surlignée en vert." else "Indice 2/2 : la flèche montre le coup ; ce problème ne comptera pas.",
                ),
            )
        }
    }

    /** Problème suivant, ou bilan de la série. */
    fun nextPractice() {
        val pr = state.value.practice ?: return
        if (!pr.failed && !pr.solved) return
        practiceJob?.cancel()
        practiceJob = viewModelScope.launch { nextPracticeOrFinish(pr.index) }
    }

    private suspend fun nextPracticeOrFinish(index: Int) {
        val pr = state.value.practice ?: return
        if (index + 1 < pr.ids.size) return showPractice(index + 1)
        val l = lesson
        val score = pr.score
        val old = lessons.progress(l.id)
        val stars = old?.stars ?: 0
        val passed = score >= PRACTICE_PASS
        val mastered = old?.mastered == true || (passed && stars >= 2)
        lessons.save(
            (old ?: LessonProgress(l.id, 0)).copy(
                masteredAt = if (old?.mastered == true) old.masteredAt else if (mastered) System.currentTimeMillis() else 0,
                practicePassed = maxOf(score, old?.practicePassed ?: 0), practiceTotal = pr.ids.size,
            ),
        )
        val text = when {
            mastered && passed -> "$score sur ${pr.ids.size} : leçon maîtrisée ! Vous savez appliquer cette idée sur des positions inédites."
            passed -> "$score sur ${pr.ids.size} : série réussie. Réussissez aussi le Défi avec au plus une erreur pour maîtriser la leçon."
            else -> "$score sur ${pr.ids.size} : il en faut $PRACTICE_PASS pour valider. Relisez la leçon en mode Guidé, puis retentez la série."
        }
        _state.update { s -> s.copy(practice = pr.copy(finished = true, solved = false, failed = false, tone = if (passed) Tone.GOOD else Tone.GOLD, message = text)) }
    }

    /** Quitte la série (ou son bilan) et revient à la leçon. */
    fun closePractice() {
        practiceJob?.cancel()
        _state.update { it.copy(practice = null) }
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

/** Question posée à chaque arrêt du mode Guidé (lue après le commentaire précédent). */
const val ASK = "À vous : que jouez-vous ici ?"
