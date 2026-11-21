package com.ivanherboch.chessduck.feature

import kotlinx.coroutines.withTimeoutOrNull
import com.ivanherboch.chessduck.content.PuzzleThemes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.SpokenFr
import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.content.LessonCategory
import com.ivanherboch.chessduck.content.LessonKind
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.data.AcademyAssessment
import com.ivanherboch.chessduck.data.GameRepository
import com.ivanherboch.chessduck.data.LessonProgress
import com.ivanherboch.chessduck.data.LessonProgressRepository
import com.ivanherboch.chessduck.data.PuzzleRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.ui.REFUTE_HOLD
import com.ivanherboch.chessduck.ui.Refutation
import com.ivanherboch.chessduck.ui.undoAnim
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.LinePlaybackController
import com.ivanherboch.chessduck.ui.Narrator
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.moveAnim
import com.ivanherboch.chessduck.util.runCatchingSuspend
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Signal ponctuel de retour haptique (réussite ou erreur), identifié pour n'être joué qu'une fois. */
@Immutable
data class Pulse(val id: Long, val good: Boolean)

/** Nature d'une série de problèmes : validation d'une leçon, révision, réflexe, placement ou examen. */
enum class PracticeKind { LESSON, REVIEW, ONLY_MOVE, PLACEMENT, EXAM }

/**
 * Série de problèmes inédits. Un problème compte s'il est résolu sans erreur et sans l'indice qui montre le coup.
 */
@Immutable
data class Practice(
    val kind: PracticeKind = PracticeKind.LESSON,
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
    /** Palier concerné (examen). */
    val level: Int = 2,
    /** Réfutation d'un mauvais coup (comme dans les Problèmes) : position affichée, puis compte à rebours. */
    val refute: Refutation? = null,
    val refuteUntil: Long = 0L,
    val refuteHold: Long = 0L,
    /** Une erreur a été commise sur ce problème : même résolu ensuite, il ne compte pas. */
    val hadError: Boolean = false,
    /** Demi-coups déjà joués du problème (on peut revenir en arrière et avancer jusque-là). */
    val progress: Int = 0,
) {
    val heroWhite: Boolean get() = line?.positions?.get(0)?.whiteToMove == false
    val score: Int get() = results.count { it }
}

/** QCM de fin de leçon : choix mélangés, indice de la bonne réponse, réponse donnée. */
@Immutable
data class QuizState(val choices: List<String>, val correct: Int, val picked: Int? = null)

/** Révisions du jour : file des leçons dues et nombre de révisions réussies. */
@Immutable
data class ReviewSession(val queue: List<Int>, val index: Int = 0, val passed: Int = 0, val finished: Boolean = false)

/** « Réviser mon répertoire » : suite de (leçon, variante) jouées en Défi. */
@Immutable
data class RepertoireSession(val white: Boolean, val items: List<Pair<Int, Int>>, val index: Int = 0, val results: List<Boolean> = emptyList(), val finished: Boolean = false)

/** Accueil de l'Académie : palier, leçon à reprendre, révisions dues, leçon conseillée. */
@Immutable
data class AcademyHome(
    val level: Int = 2,
    val placementDone: Boolean = false,
    val due: List<Int> = emptyList(),
    val recommended: Int? = null,
    val continueId: Int? = null,
    /** Paliers dont l'examen a été réussi. */
    val examsPassed: Set<Int> = emptySet(),
    /** Finales jouables validées (une leçon qui les entraîne est maîtrisée). */
    val endgamesDone: Set<EndgameFamily> = emptySet(),
    /** Leçons du palier en cours : maîtrisées, et au total (barre de progression du tiroir). */
    val levelMastered: Int = 0,
    val levelTotal: Int = 0,
)

/** Comment une leçon se valide après le Défi. */
enum class Validation { NONE, PUZZLES, ENDGAME }

@Immutable
data class AcademyState(
    val lessonId: Int = 1,
    /** null = toutes les catégories. */
    val filter: LessonCategory? = null,
    val challenge: Boolean = false,
    val cursor: Int = 0,
    /** Ligne affichée : 0 = principale, 1… = variante. */
    val variant: Int = 0,
    val playing: Boolean = false,
    val flipped: Boolean = false,
    /** Échiquier retourné par l'élève pendant une séance (finale, série, réflexes) : s'ajoute à l'orientation naturelle. */
    val sessionFlipped: Boolean = false,
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
    /** Partie modèle (« Devinez le coup ») : points gagnés et points possibles. */
    val guessScore: Int = 0,
    val guessMax: Int = 0,
    /** L'Ordinateur juge le coup proposé : l'échiquier attend. */
    val judging: Boolean = false,
    /** Réfutation d'un mauvais coup du Défi : position après la réponse adverse, compte à rebours avant le retour. */
    val refute: Refutation? = null,
    val refuteUntil: Long = 0L,
    val refuteHold: Long = 0L,
    /**
     * Coup de l'élève affiché tel qu'il l'a joué pendant que l'Ordinateur le juge (et, s'il est aussi bon, un court
     * instant avant que la leçon ne reprenne son propre coup).
     */
    val tentative: Refutation? = null,
    /** Coup de la leçon montré par une flèche verte (gratuite) après un coup aussi bon mais différent. */
    val suggest: Move? = null,
    val pulse: Pulse? = null,
    /** Série de problèmes en cours (null : leçon). */
    val practice: Practice? = null,
    val quiz: QuizState? = null,
    val review: ReviewSession? = null,
    val repertoire: RepertoireSession? = null,
    /** La séance en cours a été lancée depuis le tiroir « Entraînements » : en la quittant, on y revient. */
    val fromDrawer: Boolean = false,
    /** Incrémenté quand une séance du tiroir se termine : l'écran rouvre alors le tiroir. */
    val drawerReturn: Int = 0,
)

/** Fiche de la leçon, affichée et lue à son ouverture : objectif, règle, conditions, idées et pièges. */
fun objectiveText(l: Lesson): String = buildString {
    append(l.summary)
    append("\n\nÀ retenir : ").append(l.takeaway)
    if (l.conditions.isNotEmpty()) append("\n\nConditions : ").append(l.conditions)
    if (l.ideas.isNotEmpty()) append("\n\nLes idées : ").append(l.ideas.joinToString(" "))
    if (l.traps.isNotEmpty()) append("\n\nLes pièges : ").append(l.traps.joinToString(" "))
}

class AcademyViewModel(
    private val prefs: SettingsRepository,
    private val lessons: LessonProgressRepository,
    private val narrator: Narrator,
    private val engine: ChessEngine,
    private val puzzles: PuzzleRepository,
    /** Leçon, mode et position mémorisés pour survivre à l'arrêt du processus par Android. */
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val random: Random = Random(System.nanoTime()),
    clock: () -> Long = System::currentTimeMillis,
    /** Parties du Jeu Libre, pour le bilan des thèmes faibles (facultatif). */
    games: GameRepository? = null,
    /** Fil de calcul du bilan (relecture des parties) ; null : celui du ViewModel (tests). */
    work: CoroutineDispatcher? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(AcademyState())
    val state: StateFlow<AcademyState> = _state.asStateFlow()
    val mastered = lessons.masteredLessons().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    /** Progression détaillée (étoiles, exercices, révisions) par leçon. */
    val progress = lessons.progress().map { l -> l.associateBy { it.lessonId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val srs = SrsScheduler(clock)
    /** Accueil : leçon à reprendre, révisions dues, leçon conseillée selon le palier. */
    val home: StateFlow<AcademyHome> = combine(
        lessons.progress(), prefs.settings, state.map { it.lessonId }.distinctUntilChanged(), lessons.assessments(),
    ) { list, st, current, tests ->
        val byId = list.associateBy { it.lessonId }
        AcademyHome(
            level = st.academyLevel, placementDone = st.placementDone, due = srs.due(list),
            recommended = srs.recommended(Lessons.all, byId, st.academyLevel)?.id,
            continueId = srs.continueLesson(Lessons.all, byId, current)?.id,
            examsPassed = tests.filter { it.kind == "exam" && it.passed }.map { it.level }.toSet(),
            endgamesDone = Lessons.all.filter { it.drill.isNotEmpty() && byId[it.id]?.mastered == true }.mapNotNull { EndgameFamily.of(it.drill) }.toSet(),
            levelMastered = Lessons.all.count { it.level == st.academyLevel && byId[it.id]?.mastered == true },
            levelTotal = Lessons.all.count { it.level == st.academyLevel },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AcademyHome())

    /** Bilan : les 3 priorités du moment, d'après les parties analysées et la progression dans l'Académie. */
    val priorities: StateFlow<List<Priority>> = combine(games?.games() ?: flowOf(emptyList()), lessons.progress()) { g, p ->
        WeakTopics.compute(g, p)
    }.let { f -> if (work != null) f.flowOn(work) else f }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Lecture automatique et séquences du mode Défi : un seul travail à la fois. */
    private val playback = LinePlaybackController(viewModelScope)
    private val judge = MoveJudge(engine)
    private var judgeJob: Job? = null
    /** Une leçon a été ouverte par un pont avant la fin de la restauration de l'état au démarrage. */
    private var openedExplicitly = false
    private var practiceJob: Job? = null
    private var pulses = 0L
    /** Finale jouée lancée depuis une leçon (validation) ou une révision. */
    private var endgameLesson: Int? = null
    private var endgameReview = false

    /** Finales jouables contre l'Ordinateur. */
    val endgame = EndgameTrainer(viewModelScope, engine, random, onFinished = ::endgameFinished)
    /** Exercices de réflexes (menace, prise). */
    val reflex = ReflexTrainer(viewModelScope, puzzles, engine, random, speak = { if (narration.value) narrator.speak(it) })

    val lesson: Lesson get() = Lessons.byId(state.value.lessonId)
    /** Ligne affichée (principale ou variante) et ses commentaires. */
    fun line(s: AcademyState = state.value): Line = Lessons.byId(s.lessonId).lineFor(s.variant)
    fun texts(s: AcademyState = state.value): List<String> = Lessons.byId(s.lessonId).textsFor(s.variant)

    /** Lecture à voix haute des commentaires du mode Guidé (réglage), et phrase en cours de lecture. */
    /** Variantes d'ouverture réussies en Défi (« leçon:variante ») : badge ✓ du menu Variantes. */
    val variantsDone: StateFlow<Set<String>> = prefs.settings.map { it.variantsDone }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val narration: StateFlow<Boolean> = prefs.settings.map { it.narration }.distinctUntilChanged()
        .onEach { if (!it) narrator.stop() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val speaking: StateFlow<Boolean> = narrator.speaking
    /** Voix française hors-ligne installée (null : pas encore vérifié, voir [checkVoice]). */
    val voice: StateFlow<Boolean?> = narrator.available

    fun checkVoice() = narrator.prepare()

    /** Active la lecture vocale (réglage enregistré) et lit tout de suite le commentaire affiché. */
    fun enableNarration() {
        viewModelScope.launch {
            prefs.setNarration(true)
            if (!active()) narrator.speak(spokenStep())
        }
    }

    init {
        // Le moteur démarre tout de suite, en tâche de fond : le premier coup jugé (Défi, finale) n'attend pas son lancement.
        viewModelScope.launch { runCatchingSuspend { withTimeoutOrNull(10_000) { engine.search(com.ivanherboch.chessduck.chess.START_FEN, 50) } } }
        viewModelScope.launch {
            val restoredId = saved.get<Int>(KEY_LESSON)
            val restoredChallenge = saved.get<Boolean>(KEY_CHALLENGE) ?: false
            val restoredCursor = saved.get<Int>(KEY_CURSOR) ?: 0
            val restoredVariant = saved.get<Int>(KEY_VARIANT) ?: 0
            val stored = restoredId ?: prefs.settings.first().lessonId
            // Une leçon ouverte entre-temps (pont depuis la revue de partie, bilan) garde la priorité.
            if (!openedExplicitly) open(stored, save = false, variant = restoredVariant)
            if (restoredId != null && !openedExplicitly) {
                if (restoredChallenge) resumeChallenge(restoredCursor, saved.get<Int>(KEY_ERRORS) ?: 0, saved.get<Int>(KEY_HINTS) ?: 0)
                else if (restoredCursor > 0) goTo(restoredCursor)
            }
            state.collect { st ->
                saved[KEY_LESSON] = st.lessonId
                saved[KEY_CHALLENGE] = st.challenge
                saved[KEY_CURSOR] = st.cursor
                saved[KEY_VARIANT] = st.variant
                saved[KEY_ERRORS] = st.errors
                saved[KEY_HINTS] = st.maxHint
            }
        }
    }

    private companion object {
        const val KEY_LESSON = "academy_lesson"
        const val KEY_CHALLENGE = "academy_challenge"
        const val KEY_CURSOR = "academy_cursor"
        const val KEY_VARIANT = "academy_variant"
        const val KEY_ERRORS = "academy_errors"
        const val KEY_HINTS = "academy_hints"
        const val PRACTICE_SIZE = 5
        const val PRACTICE_PASS = 4
        const val REVIEW_SIZE = 2
        const val REPERTOIRE_SIZE = 5
    }

    private fun pulse(good: Boolean) = _state.update { it.copy(pulse = Pulse(++pulses, good)) }

    /** Montre tout de suite le coup de l'élève, là où il l'a joué, pendant que l'Ordinateur le juge. */
    private fun showTentative(pos: Position, m: Move) = _state.update {
        val wait = "L'Ordinateur analyse ${pos.san(m)}…"
        it.copy(
            judging = true, tentative = Refutation(pos.play(m), m, null), anim = moveAnim(pos, m),
            message = if (it.challenge) wait else it.message, tone = if (it.challenge) Tone.INFO else it.tone,
            note = if (it.challenge) it.note else wait, noteTone = if (it.challenge) it.noteTone else Tone.INFO,
        )
    }

    /** Temps laissé pour voir son propre coup avant que la leçon ne reprenne le sien. */
    private val tentativeHold = 1_200L

    /** Une activité (Défi, série, finale, réflexes, QCM) occupe l'élève : la voix se tait. */
    private fun active(s: AcademyState = state.value) =
        s.challenge || s.practice != null || s.quiz != null || endgame.state.value != null || reflex.state.value != null

    /** Interrompt toute séquence en cours (lecture, réponse adverse, jugement, réfutation, exercices). */
    private fun cancelAll() {
        playback.cancel()
        judgeJob?.cancel()
        practiceJob?.cancel()
        endgame.stop()
        reflex.stop()
    }

    fun validation(l: Lesson = lesson): Validation = when {
        EndgameFamily.of(l.drill) != null -> Validation.ENDGAME
        l.theme.isNotEmpty() -> Validation.PUZZLES
        else -> Validation.NONE
    }

    /** Enregistre la progression ; une première maîtrise lance le calendrier des révisions. */
    private suspend fun saveProgress(id: Int, update: (LessonProgress) -> LessonProgress): LessonProgress {
        val old = lessons.progress(id) ?: LessonProgress(id, 0)
        var p = update(old)
        if (!old.mastered && p.mastered) p = srs.mastered(p)
        lessons.save(p)
        return p
    }

    /**
     * Ouvre une leçon. [session] : l'ouverture fait partie d'une révision ou du répertoire (ces séances sont gardées) ;
     * sinon, choisir une autre leçon les termine.
     */
    fun open(id: Int, save: Boolean = true, variant: Int = 0, session: Boolean = false, guided: Boolean = false) {
        cancelAll()
        val l = Lessons.byId(id)
        // Une leçon choisie (catalogue, « Reprendre », « Conseillée ») commence par le mode Guidé.
        if (guided) _state.update { it.copy(challenge = false) }
        val challenge = state.value.challenge
        _state.update {
            it.copy(
                lessonId = l.id, cursor = 0, variant = variant.coerceIn(0, l.variants.size), playing = false, flipped = !l.heroWhite,
                anim = null, hintStage = 0, done = false,
                filter = if (it.filter != null && it.filter != l.category) l.category else it.filter,
                answered = emptySet(), note = null, judging = false, refute = null, refuteUntil = 0L, tentative = null, suggest = null, practice = null, quiz = null,
                errors = 0, maxHint = 0, stars = 0, guessScore = 0, guessMax = 0,
                review = if (session) it.review else null, repertoire = if (session) it.repertoire else null,
                fromDrawer = session && it.fromDrawer,
            )
        }
        if (save) viewModelScope.launch { prefs.setLesson(l.id) }
        if (challenge) startChallenge() else _state.update { it.copy(message = "", tone = Tone.INFO) }
        // Ouverture choisie par l'utilisateur (pas la restauration au lancement) : on lit l'objectif.
        if (save) narrate()
    }

    /**
     * Filtre de catégorie du catalogue, conservé entre deux ouvertures (il oriente aussi Leçon précédente / suivante).
     * Il ne change jamais la leçon ouverte : c'est l'élève qui choisit une leçon dans la liste.
     */
    fun setFilter(cat: LessonCategory?) = _state.update { it.copy(filter = cat) }

    /** Choix manuel du palier (tiroir « Entraînements ») : les leçons conseillées et les exercices suivent ce palier. */
    fun setLevel(level: Int) {
        viewModelScope.launch { prefs.setAcademyLevel(level.coerceIn(1, 5)) }
    }

    /** Retourne l'échiquier : celui de la séance en cours (finale, série, réflexes), sinon celui de la leçon. */
    fun flip() = _state.update {
        if (it.practice != null || endgame.state.value != null || reflex.state.value != null) it.copy(sessionFlipped = !it.sessionFlipped)
        else it.copy(flipped = !it.flipped)
    }

    fun setChallenge(on: Boolean) {
        if (state.value.challenge == on) return
        cancelAll()
        // Retour au Guidé : on garde la position atteinte (après un Défi réussi, on relit la leçon là où l'on en est).
        val keep = if (on) 0 else state.value.cursor.coerceIn(0, line().size)
        _state.update {
            it.copy(
                challenge = on, cursor = keep, playing = false, anim = null, hintStage = 0, done = false, message = "", tone = Tone.INFO,
                note = null, judging = false, refute = null, refuteUntil = 0L, tentative = null, suggest = null, practice = null, quiz = null, errors = 0, maxHint = 0, stars = 0,
                guessScore = 0, guessMax = 0, review = if (on) it.review else null, repertoire = if (on) it.repertoire else null,
            )
        }
        if (on) {
            narrator.stop()
            startChallenge()
        } else narrate()
    }

    /**
     * Choisit la ligne : 0 = principale, puis les variantes. En Guidé, on part du premier coup de la variante ;
     * en Défi, la variante se rejoue depuis le début.
     */
    fun setVariant(v: Int) {
        val l = lesson
        if (v !in 0..l.variants.size || v == state.value.variant) return
        if (state.value.challenge) {
            cancelAll()
            _state.update { it.copy(variant = v) }
            return startChallenge()
        }
        pause()
        val start = if (v == 0) state.value.cursor.coerceAtMost(l.line.size) else l.variants[v - 1].from
        _state.update { it.copy(variant = v, cursor = start, anim = null, note = null, quiz = null) }
        narrate()
    }

    // ---- Mode Guidé ----

    /** Le mode Guidé attend ici le coup de l'élève (« À vous : que jouez-vous ici ? »). Arrêts de la ligne principale. */
    fun asking(s: AcademyState = state.value): Boolean {
        val l = Lessons.byId(s.lessonId)
        val ownLine = s.variant == 0 || s.cursor < l.variants[s.variant - 1].from
        return !s.challenge && s.practice == null && s.quiz == null && ownLine && s.cursor in l.pauseSteps &&
            s.cursor !in s.answered && s.cursor < l.line.size
    }

    fun goTo(target: Int) {
        val ln = line()
        val s = state.value
        val t = target.coerceIn(0, ln.size)
        if (t == s.cursor) return
        val anim = LinePlaybackController.transition(ln, s.cursor, t)
        _state.update { it.copy(cursor = t, anim = anim, note = null, suggest = null, hintStage = if (it.challenge) it.hintStage else 0) }
        narrate()
    }

    /** Indice pendant un arrêt « À vous » du mode Guidé : la pièce à jouer est surlignée en vert. */
    fun guideHint() {
        val s = state.value
        if (!asking(s) || s.judging) return
        _state.update { it.copy(hintStage = 1, note = "Indice : la pièce à jouer est surlignée en vert.", noteTone = Tone.GOLD) }
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
        showTentative(pos, m)
        judgeJob = viewModelScope.launch {
            val v = judge.judge(pos, m, expected, l.category == LessonCategory.ENDGAME)
            val lessonSan = l.line.sans[c]
            when (v) {
                Verdict.EXACT -> {
                    pulse(true)
                    _state.update { it.copy(judging = false, tentative = null, anim = null, answered = it.answered + c) }
                    goTo(c + 1)
                    _state.update { it.copy(note = "Bravo, c'est le coup de la leçon !", noteTone = Tone.GOOD) }
                    // La leçon reprend d'elle-même (après le temps de lire ou d'écouter le commentaire du coup).
                    if (state.value.cursor < line().size) startAutoplay()
                }
                Verdict.EQUIVALENT -> {
                    // Aussi bon, mais la leçon suit une autre ligne : le coup est montré, puis l'élève joue celui de la leçon.
                    pulse(true)
                    _state.update { it.copy(note = "$san : bon coup aussi !", noteTone = Tone.GOOD) }
                    delay(tentativeHold)
                    _state.update {
                        it.copy(
                            judging = false, tentative = null, anim = undoAnim(pos, m), suggest = expected, noteTone = Tone.GOOD,
                            note = "$san est aussi bon ! Cette leçon suit une autre ligne : jouez $lessonSan, indiqué par la flèche verte.",
                        )
                    }
                }
                else -> {
                    pulse(false)
                    _state.update {
                        it.copy(
                            judging = false, tentative = null, anim = undoAnim(pos, m), noteTone = Tone.GOLD,
                            note = if (v == Verdict.SLOWER) "$san garde le mat, mais plus lentement : la méthode de la leçon est plus directe. Cherchez encore, ou touchez Lecture pour la découvrir."
                            else "$san n'est pas le coup de la leçon. Cherchez encore, ou touchez Lecture pour le découvrir.",
                        )
                    }
                }
            }
        }
    }

    // ---- QCM de fin de leçon ----

    fun startQuiz() {
        val q = lesson.quiz ?: return
        pause()
        narrator.stop()
        val choices = (listOf(q.answer) + q.wrong).shuffled(random)
        _state.update { it.copy(quiz = QuizState(choices, choices.indexOf(q.answer))) }
    }

    fun answerQuiz(i: Int) {
        val q = state.value.quiz ?: return
        if (q.picked != null || i !in q.choices.indices) return
        pulse(i == q.correct)
        _state.update { it.copy(quiz = q.copy(picked = i)) }
    }

    /** Après une mauvaise réponse : la question revient, choix remélangés (la bonne réponse n'a pas été dévoilée). */
    fun retryQuiz() {
        val q = lesson.quiz ?: return
        val choices = (listOf(q.answer) + q.wrong).shuffled(random)
        _state.update { it.copy(quiz = QuizState(choices, choices.indexOf(q.answer))) }
    }

    fun closeQuiz() = _state.update { it.copy(quiz = null) }

    // ---- Lecture à voix haute (mode Guidé uniquement) ----
    /** Ce qui est lu pour la position affichée : la fiche de la leçon, puis chaque coup et son commentaire. */
    private fun spokenStep(): String {
        val l = lesson
        val c = state.value.cursor
        val base = if (c == 0) "${l.title}. ${objectiveText(l)}" else "${SpokenFr.move(line().sans[c - 1])}. ${texts()[c - 1]}"
        return SpokenFr.text(if (asking()) "$base $ASK" else base)
    }

    private fun narrate() {
        if (narration.value && !active()) narrator.speak(spokenStep())
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
            state.value.cursor >= line().size -> goTo(0)
            asking() -> reveal()
            else -> narrator.resume()
        }
        startAutoplay()
    }

    /** Déroule la leçon coup par coup (lecture automatique), jusqu'à la fin ou au prochain arrêt « À vous ». */
    private fun startAutoplay() {
        playback.cancel()
        _state.update { it.copy(playing = true) }
        playback.autoplay(
            cursor = { state.value.cursor }, size = { line().size },
            // Avec la voix, on attend la fin du commentaire puis on marque une courte pause.
            delayFor = { c -> if (narration.value) 600L else if (c == 0) 700L else 2600L }, checkBeforeDelay = true,
            // Un arrêt « À vous » interrompt la lecture : l'élève cherche le coup.
            advance = { n -> goTo(n); if (asking()) { playback.cancel(); _state.update { it.copy(playing = false) } } },
            onEnd = { _state.update { it.copy(playing = false) } },
            awaitReady = narrator::awaitIdle,
        )
    }

    // ---- Mode Défi (et « Devinez le coup » pour les parties modèles) ----
    private fun sideName(l: Lesson) = if (l.heroWhite) "Blancs" else "Noirs"

    private fun startChallenge() {
        val l = lesson
        val intro = if (l.kind == LessonKind.MODEL_GAME) {
            "Devinez les coups des ${sideName(l)} : 3 points pour le coup du maître, 1 ou 2 points pour un coup presque aussi bon."
        } else "À vous de jouer les coups des ${sideName(l)} sur l'échiquier. ${l.summary}"
        _state.update {
            it.copy(
                cursor = 0, anim = null, hintStage = 0, done = false, tone = Tone.INFO, flipped = !l.heroWhite,
                errors = 0, maxHint = 0, stars = 0, refute = null, refuteUntil = 0L, tentative = null, suggest = null, judging = false, guessScore = 0, guessMax = 0,
                message = intro,
            )
        }
        advanceOpponent()
    }

    /**
     * Défi interrompu par Android (processus arrêté) : il reprend au coup atteint, avec ses erreurs et ses indices,
     * au lieu de repartir du début.
     */
    private fun resumeChallenge(cursor: Int, errors: Int, hints: Int) {
        setChallenge(true)
        val c = cursor.coerceIn(0, line().size)
        if (c == 0 || c >= line().size) return
        cancelAll()
        _state.update {
            it.copy(cursor = c, errors = errors, maxHint = hints, anim = null, tone = Tone.INFO, message = "Défi repris là où vous l'aviez laissé. ${lesson.summary}")
        }
        if (!heroToMove(c)) advanceOpponent()
    }

    fun restartChallenge() {
        cancelAll()
        startChallenge()
    }

    private fun heroToMove(cursor: Int): Boolean {
        val ln = line()
        return cursor < ln.size && ln.positions[cursor].whiteToMove == lesson.heroWhite
    }

    /**
     * Joue les réponses adverses jusqu'au prochain coup du joueur, en ajoutant leur commentaire au message,
     * puis conclut la leçon si elle est finie (le dernier commentaire et sa règle « À retenir » restent affichés).
     */
    private fun advanceOpponent() {
        playback.launch {
            val ln = line()
            val tx = texts()
            while (state.value.cursor < ln.size && !heroToMove(state.value.cursor)) {
                delay(650)
                val cur = state.value.cursor
                val reply = "Réponse ${ln.sans[cur]} : ${tx[cur]}"
                _state.update {
                    it.copy(
                        cursor = cur + 1, anim = moveAnim(ln.positions[cur], ln.moves[cur]), hintStage = 0,
                        message = if (it.message.isEmpty()) reply else it.message + "\n\n" + reply,
                    )
                }
            }
            if (state.value.cursor >= ln.size) finishChallenge()
        }
    }

    private suspend fun finishChallenge() {
        val l = lesson
        val s = state.value
        val model = l.kind == LessonKind.MODEL_GAME
        val stars = when {
            model -> if (s.guessScore * 5 >= s.guessMax * 4) 3 else if (s.guessScore * 5 >= s.guessMax * 3) 2 else 1
            s.errors == 0 && s.maxHint == 0 -> 3
            s.errors <= 1 && s.maxHint <= 1 -> 2
            else -> 1
        }
        val takeaway = if (s.message.contains("À retenir")) "" else "\n\nÀ retenir : ${l.takeaway}"
        val scoreText = if (model) "\n\nScore : ${s.guessScore} points sur ${s.guessMax}." else ""
        _state.update { it.copy(done = true, stars = stars, tone = Tone.GOOD, message = it.message + takeaway + scoreText) }
        // Séances : révision du jour ou répertoire.
        state.value.repertoire?.let { return repertoireResult(stars >= 2, fluent = stars == 3) }
        if (state.value.review != null) return reviewChallengeDone(stars >= 2, fluent = stars == 3)
        if (s.variant > 0) {
            // Variante d'ouverture : badge de réussite, sans toucher à la maîtrise (qui suit la ligne principale).
            if (stars >= 2) prefs.markVariantDone("${l.id}:${s.variant}")
            val left = (1..l.variants.size).count { v -> v != s.variant && "${l.id}:$v" !in variantsDone.value }
            _state.update {
                it.copy(
                    message = it.message + if (stars < 2) "\n\nRejouez cette variante avec au plus une erreur pour la valider."
                    else if (left > 0) "\n\nVariante validée ! Encore $left variante${if (left > 1) "s" else ""} à travailler dans le menu Variantes."
                    else "\n\nVariante validée : toutes les variantes de cette ouverture sont réussies !",
                )
            }
            return
        }
        val v = validation(l)
        val old = lessons.progress(l.id)
        val needed = if (v == Validation.ENDGAME) EndgameTrainer.PASS else PRACTICE_PASS
        val practiced = (old?.practicePassed ?: 0) >= needed
        val bestStars = maxOf(stars, old?.stars ?: 0)
        val nowMastered = old?.mastered == true || (bestStars >= 2 && (v == Validation.NONE || practiced))
        val next = when {
            nowMastered -> ""
            stars < 2 -> "\n\nRejouez avec au plus une erreur pour viser la maîtrise."
            v == Validation.ENDGAME -> "\n\nValidez maintenant la leçon contre l'Ordinateur : réussissez ${EndgameTrainer.PASS} positions sur 5."
            else -> "\n\nValidez maintenant la leçon sur $PRACTICE_SIZE problèmes inédits du même thème."
        }
        saveProgress(l.id) { p ->
            p.copy(
                masteredAt = if (p.mastered) p.masteredAt else if (nowMastered) srs.now() else 0,
                // Cumul de toutes les tentatives (un Défi rejoué n'efface pas les erreurs d'avant) : bilan des points faibles.
                stars = bestStars, errors = p.errors + s.errors, hintsUsed = p.hintsUsed + s.maxHint,
            )
        }
        _state.update { it.copy(message = it.message + next) }
    }

    /**
     * Coup du joueur en mode Défi. Renvoie vrai si c'est le coup de la leçon (accepté tout de suite) ; sinon
     * l'Ordinateur juge le coup : aussi bon, il est accepté et la leçon reprend son propre coup ; moins bon,
     * il est réfuté puis la position d'avant l'erreur revient. Partie modèle : le coup est noté, puis le coup du
     * maître est joué.
     */
    fun challengeMove(m: Move): Boolean {
        val s = state.value
        val l = lesson
        val ln = line()
        if (!s.challenge || s.done || s.judging || s.refute != null || !heroToMove(s.cursor)) return false
        val pos = ln.positions[s.cursor]
        if (!pos.isLegal(m)) return false
        val expected = ln.moves[s.cursor]
        if (l.kind == LessonKind.MODEL_GAME) return guess(pos, m, expected)
        if (m == expected) {
            pulse(true)
            accept(s.cursor, "${ln.sans[s.cursor]} : ${texts()[s.cursor]}")
            return true
        }
        showTentative(pos, m)
        judgeJob = viewModelScope.launch {
            when (judge.judge(pos, m, expected, l.category == LessonCategory.ENDGAME)) {
                Verdict.EXACT, Verdict.EQUIVALENT -> {
                    // Aussi bon, mais la leçon suit une autre ligne : rien n'est joué à la place de l'élève. Son coup est
                    // montré, la position revient et une flèche verte (sans pénalité) indique le coup de la leçon.
                    pulse(true)
                    _state.update { it.copy(tone = Tone.GOOD, message = "${pos.san(m)} : bon coup aussi !") }
                    delay(tentativeHold)
                    // Le Défi teste la mémoire : le coup de la leçon n'est pas montré (flèche réservée à l'indice).
                    _state.update {
                        it.copy(
                            judging = false, tentative = null, anim = undoAnim(pos, m), tone = Tone.GOOD,
                            message = "${pos.san(m)} est aussi bon ! Mais cette leçon suit une autre ligne : retrouvez son coup, ou demandez un indice.",
                        )
                    }
                }
                Verdict.WORSE -> refute(pos, m)
                Verdict.SLOWER -> {
                    // Pas une faute grave, mais pas la méthode : le coup est montré, puis la position revient.
                    pulse(false)
                    _state.update {
                        it.copy(
                            errors = it.errors + 1, tone = Tone.GOLD,
                            message = "${pos.san(m)} garde le mat, mais plus lentement. La méthode de la leçon est plus directe : retrouvez-la.",
                        )
                    }
                    delay(tentativeHold)
                    _state.update { it.copy(judging = false, tentative = null, anim = undoAnim(pos, m)) }
                }
                Verdict.UNAVAILABLE -> {
                    pulse(false)
                    _state.update {
                        it.copy(
                            judging = false, tentative = null, anim = undoAnim(pos, m), errors = it.errors + 1, tone = Tone.BAD,
                            message = "${pos.san(m)} n'est pas le coup de la leçon. Réfléchissez à l'idée du thème, ou demandez un indice.",
                        )
                    }
                }
            }
        }
        return false
    }

    /** « Devinez le coup » : 3 points pour le coup du maître, 2 si presque aussi bon, 1 si raisonnable, puis le coup du maître. */
    private fun guess(pos: Position, m: Move, expected: Move): Boolean {
        val cur = state.value.cursor
        val ln = line()
        if (m == expected) {
            pulse(true)
            _state.update { it.copy(guessScore = it.guessScore + 3, guessMax = it.guessMax + 3) }
            accept(cur, "3 points : c'est le coup du maître ! ${ln.sans[cur]} : ${texts()[cur]}")
            return true
        }
        showTentative(pos, m)
        judgeJob = viewModelScope.launch {
            val loss = judge.loss(pos, m, expected)
            val pts = when {
                loss == null -> 0
                loss <= MoveJudge.MAX_LOSS -> 2
                loss <= 15.0 -> 1
                else -> 0
            }
            pulse(pts > 0)
            if (pts == 0) {
                // Coup nettement plus faible : la réponse de l'Ordinateur montre brièvement pourquoi, avant le coup du maître.
                val after = pos.play(m)
                val r = RefutationLogic.reply(engine, pos, m, minShow = 0)
                _state.update {
                    it.copy(
                        tentative = Refutation(r.position, r.reply ?: m, r.reply), anim = r.reply?.let { reply -> moveAnim(after, reply) } ?: it.anim,
                        tone = Tone.GOLD, message = "${pos.san(m)} : 0 point. ${r.text} Le maître a joué ${ln.sans[cur]}.",
                    )
                }
                delay(REFUTE_HOLD / 2)
            } else {
                _state.update { it.copy(tone = Tone.GOOD, message = "${pos.san(m)} : $pts point${if (pts > 1) "s" else ""}. Le maître a joué ${ln.sans[cur]}.") }
                delay(tentativeHold)
            }
            _state.update { it.copy(judging = false, tentative = null, anim = null, guessScore = it.guessScore + pts, guessMax = it.guessMax + 3) }
            accept(cur, "${pos.san(m)} : $pts point${if (pts > 1) "s" else ""}. Le maître a joué ${ln.sans[cur]} : ${texts()[cur]}")
        }
        return false
    }

    /** « Devinez le coup » : passe ce coup (0 point) et montre le coup du maître. */
    fun skipGuess() {
        val s = state.value
        if (!s.challenge || s.done || s.judging || !heroToMove(s.cursor) || lesson.kind != LessonKind.MODEL_GAME) return
        _state.update { it.copy(guessMax = it.guessMax + 3) }
        accept(s.cursor, "Le coup du maître : ${line().sans[s.cursor]} : ${texts()[s.cursor]}")
    }

    /** Joue le coup de la leçon (le bon coup, ou celui qui suit un coup équivalent) puis les réponses adverses. */
    private fun accept(cur: Int, text: String) {
        val ln = line()
        _state.update {
            it.copy(cursor = cur + 1, anim = moveAnim(ln.positions[cur], ln.moves[cur]), hintStage = 0, suggest = null, tone = Tone.GOOD, message = text)
        }
        advanceOpponent()
    }

    /**
     * Mauvais coup : il est joué, l'Ordinateur montre la réponse adverse (flèche rouge) pendant [REFUTE_HOLD] ms avec
     * un compte à rebours, puis la position d'avant l'erreur revient. « Reprendre au coup manqué » y revient tout de suite.
     */
    private suspend fun refute(pos: Position, m: Move) {
        pulse(false)
        val san = pos.san(m)
        val after = pos.play(m)
        val opp = if (pos.whiteToMove) "Noirs" else "Blancs"
        missed = pos to m
        _state.update {
            it.copy(
                judging = false, errors = it.errors + 1, tone = Tone.BAD, tentative = null, anim = null,
                refute = Refutation(after, m, null), message = "$san n'est pas le bon coup. Regardez la réponse des $opp…",
            )
        }
        val r = RefutationLogic.reply(engine, pos, m)
        _state.update {
            it.copy(
                anim = r.reply?.let { reply -> moveAnim(after, reply) } ?: it.anim,
                refute = Refutation(r.position, r.reply ?: m, r.reply),
                refuteUntil = System.currentTimeMillis() + REFUTE_HOLD, refuteHold = REFUTE_HOLD,
                message = r.text + RefutationLogic.AUTO_UNDO,
            )
        }
        delay(REFUTE_HOLD)
        resumeAtMiss()
    }

    /** Mauvais coup réfuté (position d'avant, coup) : il glisse en arrière quand la position revient. */
    private var missed: Pair<Position, Move>? = null

    /** « Reprendre au coup manqué » : annule le mauvais coup et revient à la position d'avant l'erreur. */
    fun resumeAtMiss() {
        if (state.value.refute == null) return
        judgeJob?.cancel()
        _state.update {
            it.copy(
                refute = null, refuteUntil = 0L, anim = missed?.let { (p, m) -> undoAnim(p, m) }, tone = Tone.INFO,
                message = "Position rétablie : à vous de retrouver le bon coup.",
            )
        }
    }

    /** Suspend le retour automatique : la réfutation reste affichée jusqu'à « Reprendre au coup manqué ». */
    fun holdRefutation() {
        if (state.value.refute == null || state.value.refuteUntil == 0L) return
        judgeJob?.cancel()
        _state.update {
            it.copy(
                refuteUntil = 0L,
                message = it.message.removeSuffix(RefutationLogic.AUTO_UNDO) + " Prenez votre temps, puis touchez « Reprendre au coup manqué ».",
            )
        }
    }

    fun hint() {
        val s = state.value
        if (!s.challenge || s.done || s.judging || s.refute != null || !heroToMove(s.cursor) || lesson.kind == LessonKind.MODEL_GAME) return
        val stage = (s.hintStage + 1).coerceAtMost(2)
        _state.update {
            it.copy(
                hintStage = stage, maxHint = maxOf(it.maxHint, stage), tone = Tone.GOLD,
                message = if (stage == 1) "Indice 1/2 : la pièce à jouer est surlignée en vert." else "Indice 2/2 : suivez la flèche.",
            )
        }
    }

    // ---- Séries de problèmes : validation, révision, « Le seul coup », placement, examen ----

    /** Validation d'une leçon de tactique : 5 problèmes inédits de son thème, au palier de la leçon. */
    fun startPractice() {
        val l = lesson
        if (l.theme.isEmpty()) return
        startSeries(PracticeKind.LESSON, List(PRACTICE_SIZE) { PuzzleSpec(listOf(l.theme), AcademyAssessments.levelRange(l.level)) })
    }

    /**
     * Pont depuis la revue de partie ou le bilan : ouvre la leçon qui enseigne le point faible et lance 5 exercices
     * de son thème, au palier du joueur. Sans thème, la leçon seule est ouverte.
     */
    fun trainTopic(lessonId: Int, theme: String) {
        openedExplicitly = true
        open(lessonId, guided = true)
        if (theme.isEmpty()) return
        viewModelScope.launch {
            val level = prefs.settings.first().academyLevel.coerceAtLeast(2)
            startSeries(PracticeKind.LESSON, List(PRACTICE_SIZE) { PuzzleSpec(listOf(theme), AcademyAssessments.levelRange(level)) }, level)
        }
    }

    /** « Le seul coup » : 5 positions où une seule défense tient. */
    fun startOnlyMove() {
        viewModelScope.launch {
            val level = prefs.settings.first().academyLevel
            startSeries(PracticeKind.ONLY_MOVE, List(PRACTICE_SIZE) { PuzzleSpec(listOf("defensiveMove"), AcademyAssessments.levelRange(level)) })
        }
    }

    /** Test de placement facultatif (12 positions, paliers 2 à 5). */
    fun startPlacement() = startSeries(PracticeKind.PLACEMENT, AcademyAssessments.placementSpecs())

    /** Examen du palier en cours (10 positions mélangées, réussite à 8). */
    fun startExam() {
        viewModelScope.launch {
            // Examen du palier en cours : le réussir fait passer au palier suivant (1 → 2, 2 → 3…).
            val level = prefs.settings.first().academyLevel.coerceIn(1, 5)
            startSeries(PracticeKind.EXAM, AcademyAssessments.examSpecs(level, random), level)
        }
    }

    private fun startSeries(kind: PracticeKind, specs: List<PuzzleSpec>, level: Int = 2) {
        cancelAll()
        narrator.stop()
        _state.update { it.copy(practice = Practice(kind = kind, level = level, message = "Préparation des problèmes…"), quiz = null, sessionFlipped = false) }
        practiceJob = viewModelScope.launch {
            val ids = ArrayList<String>()
            for (spec in specs) {
                var id: String? = null
                for (range in listOf(spec.range, 0..4000)) {
                    repeat(6) {
                        if (id != null) return@repeat
                        val c = runCatchingSuspend { puzzles.randomId(spec.themes, range, ids.lastOrNull() ?: "", random) }.getOrNull()
                        if (c != null && c !in ids) id = c
                    }
                    if (id != null) break
                }
                id?.let { ids.add(it) }
            }
            if (ids.isEmpty()) {
                _state.update { it.copy(practice = Practice(kind = kind, loading = false, finished = true, message = "Aucun problème disponible pour cette série.")) }
                return@launch
            }
            _state.update { it.copy(practice = Practice(kind = kind, ids = ids, loading = false, level = level)) }
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
        val kind = state.value.practice?.kind
        // Validation ou révision : on dit concrètement quoi chercher (le thème du problème, issu de la leçon) et on
        // rappelle la règle de la leçon, plutôt qu'une « idée de la leçon » que l'élève n'a plus sous les yeux.
        val l = lesson
        val theme = PuzzleThemes.byKey[l.theme]
        val goal = when (kind) {
            PracticeKind.LESSON, PracticeKind.REVIEW -> buildString {
                append("les $side jouent")
                if (theme != null) append(". À chercher : ${theme.name.lowercase()} (${theme.goal.replaceFirstChar { it.lowercase() }.removeSuffix(".")})")
                append(".\n\nRappel de la leçon « ${l.title} » : ${l.takeaway}")
            }
            PracticeKind.ONLY_MOVE -> "les $side trouvent la seule défense."
            else -> "les $side trouvent la meilleure suite."
        }
        _state.update { s ->
            s.copy(
                practice = s.practice?.copy(
                    index = index, line = line, cursor = 0, progress = 0, hintStage = 0, failed = false, solved = false, anim = null, tone = Tone.INFO,
                    refute = null, refuteUntil = 0L, hadError = false,
                    message = "Problème ${index + 1} sur ${ids.size} : $goal",
                ),
            )
        }
        delay(600)
        _state.update { s -> s.copy(practice = s.practice?.copy(cursor = 1, progress = 1, anim = moveAnim(line.positions[0], line.moves[0]))) }
    }

    /**
     * Coup de l'élève dans la série, comme dans les Problèmes : la solution (ou tout mat immédiat) avance ; un autre
     * coup est joué puis réfuté par l'Ordinateur (flèche rouge, compte à rebours) et l'élève réessaie. Un problème
     * résolu après une erreur, ou avec la flèche de l'indice, ne compte pas : le seuil de validation reste exigeant.
     */
    fun practiceMove(m: Move) {
        val pr = state.value.practice ?: return
        val line = pr.line ?: return
        if (pr.failed || pr.solved || pr.refute != null || pr.cursor != pr.progress || pr.cursor >= line.size ||
            line.positions[pr.cursor].whiteToMove != pr.heroWhite
        ) return
        val pos = line.positions[pr.cursor]
        if (!pos.isLegal(m)) return
        val expected = line.moves[pr.cursor]
        if (m != expected && !pos.play(m).isCheckmate) return refutePractice(pr, pos, m)
        val cur = pr.cursor
        val finished = cur + 1 >= line.size || pos.play(m).isCheckmate
        pulse(true)
        if (finished) {
            val ok = pr.hintStage < 2 && !pr.hadError
            practiceJob = viewModelScope.launch { if (ok) runCatchingSuspend { puzzles.markSolved(pr.ids[pr.index]) } }
            _state.update { s ->
                s.copy(
                    practice = pr.copy(
                        cursor = cur + 1, progress = cur + 1, anim = moveAnim(pos, m), solved = true, results = pr.results + ok,
                        tone = if (ok) Tone.GOOD else Tone.GOLD,
                        line = if (m == expected) line else Line(line.startFen, line.moves.take(cur) + m),
                        message = when {
                            ok -> "Résolu !"
                            pr.hadError -> "Résolu, mais après une erreur : ce problème ne compte pas."
                            else -> "Résolu, mais avec l'indice qui montre le coup : ce problème ne compte pas."
                        },
                    ),
                )
            }
            return
        }
        _state.update { s -> s.copy(practice = pr.copy(cursor = cur + 1, progress = cur + 1, anim = moveAnim(pos, m), hintStage = 0, tone = Tone.GOOD, message = "Bien vu. La suite arrive…")) }
        practiceJob = viewModelScope.launch {
            delay(600)
            _state.update { s ->
                val p2 = s.practice ?: return@update s
                s.copy(
                    practice = p2.copy(
                        cursor = cur + 2, progress = cur + 2, anim = moveAnim(line.positions[cur + 1], line.moves[cur + 1]),
                        message = "L'adversaire répond ${line.sans[cur + 1]} : à vous de continuer.",
                    ),
                )
            }
        }
    }

    /** Mauvais coup pendant une série : joué, réfuté par l'Ordinateur, puis annulé (comme dans les Problèmes). */
    private fun refutePractice(pr: Practice, pos: Position, m: Move) {
        pulse(false)
        val san = pos.san(m)
        val after = pos.play(m)
        val opp = if (pos.whiteToMove) "Noirs" else "Blancs"
        practiceMissed = pos to m
        _state.update { s ->
            s.copy(
                practice = pr.copy(
                    hadError = true, hintStage = 0, tone = Tone.BAD, anim = moveAnim(pos, m), refute = Refutation(after, m), refuteUntil = 0L, refuteHold = 0L,
                    message = "$san n'est pas la solution. Regardez la réponse des $opp…",
                ),
            )
        }
        practiceJob = viewModelScope.launch {
            val r = RefutationLogic.reply(engine, pos, m)
            _state.update { s ->
                val p2 = s.practice?.takeIf { it.refute != null } ?: return@update s
                s.copy(
                    practice = p2.copy(
                        anim = r.reply?.let { reply -> moveAnim(after, reply) } ?: p2.anim, refute = Refutation(r.position, r.reply ?: m, r.reply),
                        refuteUntil = System.currentTimeMillis() + REFUTE_HOLD, refuteHold = REFUTE_HOLD,
                        message = r.text + " Ce problème ne comptera plus, mais trouvez la solution." + RefutationLogic.AUTO_UNDO,
                    ),
                )
            }
            delay(REFUTE_HOLD)
            retryPractice()
        }
    }

    /** Mauvais coup de la série en cours de réfutation (position d'avant, coup) : il glisse en arrière au retour. */
    private var practiceMissed: Pair<Position, Move>? = null

    /** « Réessayer » : annule tout de suite le mauvais coup et revient à la position du problème. */
    fun retryPractice() {
        val pr = state.value.practice ?: return
        if (pr.refute == null) return
        practiceJob?.cancel()
        _state.update { s ->
            s.copy(
                practice = pr.copy(
                    refute = null, refuteUntil = 0L, anim = practiceMissed?.let { (p, m) -> undoAnim(p, m) }, tone = Tone.INFO,
                    message = "Position rétablie : à vous de rejouer. Besoin d'aide ? Indice, ou Solution.",
                ),
            )
        }
    }

    /** Suspend l'annulation automatique : la réfutation reste affichée jusqu'à « Réessayer ». */
    fun holdPracticeRefutation() {
        val pr = state.value.practice ?: return
        if (pr.refute == null || pr.refuteUntil == 0L) return
        practiceJob?.cancel()
        _state.update { s ->
            s.copy(practice = pr.copy(refuteUntil = 0L, message = pr.message.removeSuffix(RefutationLogic.AUTO_UNDO) + " Prenez votre temps, puis touchez Réessayer."))
        }
    }

    /** « Solution » : le problème est compté comme manqué et la flèche verte montre le bon coup. */
    fun practiceSolution() {
        val pr = state.value.practice ?: return
        val line = pr.line ?: return
        if (pr.failed || pr.solved || pr.refute != null || pr.progress >= line.size) return
        practiceJob?.cancel()
        _state.update { s ->
            s.copy(
                practice = pr.copy(
                    failed = true, cursor = pr.progress, results = pr.results + false, tone = Tone.BAD,
                    message = "Le bon coup était ${line.sans[pr.progress]} : regardez la flèche verte. Ce problème compte comme manqué.",
                ),
            )
        }
    }

    /** Revient d'un demi-coup dans le problème (pour revoir la solution), ou avance jusqu'au dernier coup joué. */
    fun practiceBack() = practiceStep(-1)
    fun practiceForward() = practiceStep(1)

    private fun practiceStep(d: Int) {
        val pr = state.value.practice ?: return
        val line = pr.line ?: return
        if (pr.refute != null) return
        val to = (pr.cursor + d).coerceIn(0, pr.progress)
        if (to == pr.cursor) return
        val anim = if (d > 0) moveAnim(line.positions[pr.cursor], line.moves[pr.cursor]) else undoAnim(line.positions[to], line.moves[to])
        _state.update { s -> s.copy(practice = pr.copy(cursor = to, anim = anim)) }
    }

    fun practiceHint() {
        val pr = state.value.practice ?: return
        if (pr.failed || pr.solved || pr.line == null || pr.refute != null) return
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
        val score = pr.score
        val total = pr.ids.size
        val text = when (pr.kind) {
            PracticeKind.LESSON -> lessonPracticeDone(score, total)
            PracticeKind.REVIEW -> { completeReview(score >= 1, reviewFluent && score == total); "$score sur $total." + if (score >= 1) " Révision réussie." else " Révision à refaire demain." }
            PracticeKind.ONLY_MOVE -> "$score sur $total. " + if (score >= PRACTICE_PASS) "Vous savez repérer la menace et la parer." else "Cherchez d'abord la menace adverse, puis la seule parade."
            PracticeKind.PLACEMENT -> {
                val level = AcademyAssessments.placementLevel(pr.results)
                prefs.setAcademyLevel(level)
                prefs.setPlacementDone(true)
                lessons.saveAssessment(AcademyAssessment(kind = AcademyAssessments.KIND_PLACEMENT, level = level, score = score, total = total, passed = true, completedAt = srs.now()))
                "$score sur $total. Votre palier de départ : ${levelName(level)}. Les leçons conseillées suivent ce palier."
            }
            PracticeKind.EXAM -> {
                val passed = AcademyAssessments.examPassed(score)
                if (passed && pr.level < 5) prefs.setAcademyLevel(pr.level + 1)
                lessons.saveAssessment(AcademyAssessment(kind = AcademyAssessments.KIND_EXAM, level = pr.level, score = score, total = total, passed = passed, completedAt = srs.now()))
                "$score sur $total. " + if (passed) "Examen réussi : bienvenue au palier ${levelName((pr.level + 1).coerceAtMost(5))} !" else "Il faut ${AcademyAssessments.EXAM_PASS} bonnes réponses : révisez les leçons du palier, puis retentez l'examen."
            }
        }
        val good = when (pr.kind) {
            PracticeKind.EXAM -> AcademyAssessments.examPassed(score)
            PracticeKind.REVIEW -> score >= 1
            PracticeKind.PLACEMENT -> true
            else -> score >= PRACTICE_PASS
        }
        _state.update { s -> s.copy(practice = s.practice?.copy(finished = true, solved = false, failed = false, tone = if (good) Tone.GOOD else Tone.GOLD, message = text)) }
    }

    private suspend fun lessonPracticeDone(score: Int, total: Int): String {
        val l = lesson
        val passed = score >= PRACTICE_PASS
        val p = saveProgress(l.id) { p ->
            val mastered = p.mastered || (passed && p.stars >= 2)
            p.copy(
                masteredAt = if (p.mastered) p.masteredAt else if (mastered) srs.now() else 0,
                practicePassed = maxOf(score, p.practicePassed), practiceTotal = total,
            )
        }
        return when {
            p.mastered && passed -> "$score sur $total : leçon maîtrisée ! Vous savez appliquer cette idée sur des positions inédites."
            passed -> "$score sur $total : série réussie. Réussissez aussi le Défi avec au plus une erreur pour maîtriser la leçon."
            else -> "$score sur $total : il en faut $PRACTICE_PASS pour valider. Relisez la leçon en mode Guidé, puis retentez la série."
        }
    }

    /** Quitte la série (ou son bilan) et revient à la leçon. */
    fun closePractice() {
        practiceJob?.cancel()
        _state.update { it.copy(practice = null) }
        backToDrawer()
    }

    /** Marque la séance qui va commencer comme lancée depuis le tiroir « Entraînements ». */
    fun markFromDrawer() = _state.update { it.copy(fromDrawer = true) }

    /** Fin d'une séance lancée depuis le tiroir : on y revient (au lieu de laisser la dernière leçon ouverte à l'écran). */
    private fun backToDrawer() = _state.update { if (it.fromDrawer) it.copy(fromDrawer = false, drawerReturn = it.drawerReturn + 1) else it }

    // ---- Finales jouées contre l'Ordinateur ----

    /** Lance la finale [family] ; depuis une leçon, la réussite (3 positions sur 5) valide sa maîtrise. */
    fun startEndgame(family: EndgameFamily, fromLesson: Boolean = false, total: Int = 5) {
        cancelAll()
        narrator.stop()
        endgameLesson = if (fromLesson) lesson.id else null
        endgameReview = false
        _state.update { it.copy(quiz = null, practice = null, sessionFlipped = false) }
        endgame.start(family, total)
    }

    fun closeEndgame() {
        endgame.stop()
        endgameLesson = null
        endgameReview = false
        backToDrawer()
    }

    private suspend fun endgameFinished(family: EndgameFamily, score: Int, total: Int) {
        if (endgameReview) return completeReview(score == total, reviewFluent)
        val id = endgameLesson ?: return
        val passed = score >= EndgameTrainer.PASS
        saveProgress(id) { p ->
            val mastered = p.mastered || (passed && p.stars >= 2)
            p.copy(masteredAt = if (p.mastered) p.masteredAt else if (mastered) srs.now() else 0, practicePassed = maxOf(score, p.practicePassed), practiceTotal = total)
        }
    }

    /**
     * Titre de l'entraînement en cours s'il ne dépend pas de la leçon ouverte (réflexes, finale lancée depuis le
     * tiroir, « Le seul coup », placement, examen) ; null quand on travaille la leçon elle-même.
     */
    /**
     * En-tête d'une séance en cours (entraînement, validation, révision, finale, question de fin de leçon) : il
     * remplace le sélecteur de leçon et les modes Guidé/Défi, pour qu'on sache toujours ce que montre l'échiquier.
     * Null : la leçon elle-même est à l'écran.
     */
    fun sessionTitle(s: AcademyState, eg: EndgameSession?, rx: ReflexSession?): SessionTitle? {
        val l = Lessons.byId(s.lessonId)
        val pr = s.practice
        return when {
            eg != null && endgameReview -> SessionTitle("Révision", "Finale : ${eg.family.label}")
            eg != null && endgameLesson != null -> SessionTitle("Finale de validation · ${eg.total} positions", l.title)
            eg != null -> SessionTitle("Entraînement", "Finale : ${eg.family.label}")
            rx != null -> SessionTitle("Entraînement", rx.kind.label)
            pr?.kind == PracticeKind.ONLY_MOVE -> SessionTitle("Entraînement", "Le seul coup")
            pr?.kind == PracticeKind.PLACEMENT -> SessionTitle("Test", "Test de placement")
            pr?.kind == PracticeKind.EXAM -> SessionTitle("Test", "Examen du palier ${levelName(pr.level)}")
            pr?.kind == PracticeKind.REVIEW -> SessionTitle("Révision · problèmes", l.title)
            pr != null -> SessionTitle("Validation · ${if (pr.ids.isEmpty()) PRACTICE_SIZE else pr.ids.size} problèmes", l.title)
            s.quiz != null -> SessionTitle("Question de fin de leçon", l.title)
            // Révisions du jour et répertoire : la progression s'affiche dans l'en-tête, pas au-dessus de l'échiquier.
            s.review != null -> SessionTitle("Révision ${s.review.index + 1} sur ${s.review.queue.size}", l.title)
            s.repertoire != null -> SessionTitle(
                "Répertoire ${if (s.repertoire.white) "Blancs" else "Noirs"} · ${s.repertoire.index + 1} sur ${s.repertoire.items.size}", l.title,
            )
            else -> null
        }
    }

    /** Titre de l'entraînement en cours (voir [sessionTitle]). */
    fun trainingTitle(s: AcademyState, eg: EndgameSession?, rx: ReflexSession?): String? = sessionTitle(s, eg, rx)?.title

    /**
     * Suggestions « À faire maintenant » du tiroir (révisions du jour, leçon à reprendre, leçon conseillée autre que
     * celle ouverte) : leur nombre s'affiche sur le bouton « Entraînements » de l'en-tête.
     */
    val suggestions: StateFlow<Int> = combine(home, state.map { it.lessonId }.distinctUntilChanged(), priorities) { h, current, prio ->
        val rec = h.recommended?.takeIf { it != current && it != h.continueId }
        // Les priorités du bilan (points faibles à travailler) comptent aussi, sauf la leçon déjà ouverte.
        (if (h.due.isNotEmpty()) 1 else 0) + (if (h.continueId != null) 1 else 0) + (if (rec != null) 1 else 0) +
            prio.count { it.lessonId != current }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Une séance lancée depuis le tiroir « Entraînements » (ou une révision) est en cours : le bouton est surligné. */
    val trainingActive: StateFlow<Boolean> = combine(state, endgame.state, reflex.state) { s, eg, rx ->
        eg != null || rx != null || s.practice != null || s.review != null || s.repertoire != null
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Quitte l'entraînement en cours et revient à la leçon. */
    fun closeTraining() {
        closeEndgame()
        closeReflex()
        closePractice()
        closeQuiz()
        closeReviews()
        closeRepertoire()
    }

    // ---- Réflexes ----

    fun startReflex(kind: ReflexKind) {
        cancelAll()
        narrator.stop()
        _state.update { it.copy(quiz = null, practice = null, sessionFlipped = false) }
        // Le calcul à l'aveugle est un exercice du palier 5.
        viewModelScope.launch { reflex.start(kind, if (kind == ReflexKind.BLIND) 5 else prefs.settings.first().academyLevel) }
    }

    /**
     * Calcul à l'aveugle, « Réécouter » : relit les coups annoncés ; si la voix est coupée ou absente, l'explique
     * dans la carte (les coups y restent écrits).
     */
    fun replayBlind() {
        when {
            narration.value && voice.value != false -> reflex.announce()
            voice.value == false -> reflex.note("Aucune voix française n'est installée sur ce téléphone : choisissez-en une dans les réglages vocaux d'Android. Les coups annoncés restent écrits ci-dessus.")
            else -> {
                narrator.prepare()
                reflex.note("La lecture vocale est désactivée : activez-la dans Paramètres, section Audio, voix et retours. Les coups annoncés restent écrits ci-dessus.")
            }
        }
    }

    fun closeReflex() {
        narrator.stop()
        reflex.stop()
        backToDrawer()
    }

    // ---- Révisions du jour (répétition espacée) ----

    /**
     * Révisions du jour : chaque leçon due est rejouée en Défi (au moins 2 étoiles), puis vérifiée sur 2 problèmes
     * inédits de son thème (au moins 1 réussi) ou sur une finale jouée contre l'Ordinateur. Réussie, la prochaine
     * révision s'éloigne (7, 30, 90 jours) ; ratée, la leçon revient demain.
     */
    fun startReviews() {
        viewModelScope.launch {
            val due = srs.due(lessons.progress().first())
            if (due.isEmpty()) return@launch
            _state.update { it.copy(review = ReviewSession(due), repertoire = null, challenge = true) }
            openReview(0)
        }
    }

    private fun openReview(index: Int) {
        val r = state.value.review ?: return
        _state.update { it.copy(review = r.copy(index = index), challenge = true) }
        open(r.queue[index], save = false, session = true)
    }

    /** Révision réussie sans erreur ni indice : l'intervalle s'allonge ; avec hésitation, il reste le même. */
    private var reviewFluent = true

    private suspend fun reviewChallengeDone(success: Boolean, fluent: Boolean) {
        val l = lesson
        reviewFluent = fluent
        if (!success) return completeReview(false)
        when (validation(l)) {
            Validation.PUZZLES -> startSeries(PracticeKind.REVIEW, List(REVIEW_SIZE) { PuzzleSpec(listOf(l.theme), AcademyAssessments.levelRange(l.level)) })
            Validation.ENDGAME -> {
                startEndgame(EndgameFamily.of(l.drill)!!, total = 1)
                endgameReview = true
            }
            Validation.NONE -> completeReview(true, fluent)
        }
    }

    private suspend fun completeReview(success: Boolean, fluent: Boolean = true) {
        val r = state.value.review ?: return
        val id = r.queue[r.index]
        lessons.progress(id)?.let { lessons.save(srs.reviewed(it, success, fluent)) }
        _state.update { it.copy(review = r.copy(passed = r.passed + if (success) 1 else 0)) }
        val days = srs.daysUntil(lessons.progress(id))
        val text = when {
            !success -> "\n\nRévision ratée : cette leçon reviendra demain."
            fluent -> "\n\nRévision réussie sans hésiter : prochaine révision dans $days jours."
            else -> "\n\nRévision réussie, avec une hésitation : même intervalle, prochaine révision dans $days jours."
        }
        // Révision d'une finale : le bilan s'affiche dans la carte de la finale, pas sous la leçon masquée.
        if (endgame.state.value != null) endgame.note(text) else _state.update { it.copy(message = it.message + text) }
    }

    /** Révision suivante, ou fin de la séance. */
    fun nextReview() {
        val r = state.value.review ?: return
        // Passage à la révision suivante : la finale se ferme sans renvoyer au tiroir (la séance continue).
        endgame.stop()
        endgameLesson = null
        endgameReview = false
        _state.update { it.copy(practice = null) }
        if (r.index + 1 < r.queue.size) openReview(r.index + 1)
        else _state.update { it.copy(review = r.copy(finished = true)) }
    }

    fun closeReviews() {
        _state.update { it.copy(review = null) }
        backToDrawer()
    }

    // ---- Répertoire ----

    /** « Réviser mon répertoire » : 5 ouvertures du camp choisi (révisions dues d'abord), ligne principale ou variante. */
    fun startRepertoire(white: Boolean) {
        val pool = Lessons.all.filter { it.category == LessonCategory.OPENING && it.heroWhite == white }
        if (pool.isEmpty()) return
        viewModelScope.launch {
            val dueIds = srs.due(lessons.progress().first()).toSet()
            val ordered = pool.filter { it.id in dueIds } + pool.filter { it.id !in dueIds }.shuffled(random)
            val items = ordered.take(REPERTOIRE_SIZE).map { it.id to random.nextInt(it.variants.size + 1) }
            _state.update { it.copy(repertoire = RepertoireSession(white, items), review = null, challenge = true) }
            openRepertoire(0)
        }
    }

    private fun openRepertoire(index: Int) {
        val r = state.value.repertoire ?: return
        _state.update { it.copy(repertoire = r.copy(index = index), challenge = true) }
        val (id, variant) = r.items[index]
        open(id, save = false, variant = variant, session = true)
    }

    private suspend fun repertoireResult(success: Boolean, fluent: Boolean) {
        val r = state.value.repertoire ?: return
        val id = r.items[r.index].first
        // Une leçon maîtrisée rejouée dans le répertoire compte comme une révision.
        lessons.progress(id)?.takeIf { it.mastered }?.let { lessons.save(srs.reviewed(it, success, fluent)) }
        _state.update { it.copy(repertoire = r.copy(results = r.results + success)) }
    }

    fun nextRepertoire() {
        val r = state.value.repertoire ?: return
        if (r.index + 1 < r.items.size) openRepertoire(r.index + 1)
        else _state.update { it.copy(repertoire = r.copy(finished = true)) }
    }

    fun closeRepertoire() {
        _state.update { it.copy(repertoire = null) }
        backToDrawer()
    }

    // ---- Navigation ----

    /** Leçon suivante ; [guided] : elle s'ouvre en mode Guidé (fin de Défi, de validation ou de quiz). */
    fun nextLesson(guided: Boolean = false) = shiftLesson(1, guided)
    fun prevLesson() = shiftLesson(-1)

    /** Leçon voisine dans l'ordre du catalogue (limité à la catégorie filtrée s'il y en a une). */
    private fun shiftLesson(delta: Int, guided: Boolean = false) {
        val all = Lessons.all
        if (guided) _state.update { it.copy(challenge = false) }
        val s = state.value
        val pool = if (s.filter != null) all.filter { it.category == s.filter } else all
        val idx = pool.indexOfFirst { it.id == s.lessonId }.coerceAtLeast(0)
        open(pool[(idx + delta + pool.size) % pool.size].id)
    }

    /** Dans le mode Guidé, saute directement au coup [n] (nombre de demi-coups joués). */
    fun jump(n: Int) { pause(); goTo(n) }
    fun last() { pause(); goTo(line().size) }
}

/** Question posée à chaque arrêt du mode Guidé (lue après le commentaire précédent). */
const val ASK = "À vous : que jouez-vous ici ?"

/** Nom d'un palier du cursus. */
/** Nom d'un palier pour l'affichage : « 2 · Débutant ». */
fun levelName(level: Int): String = "${level.coerceIn(1, 5)} · ${levelLabel(level)}"

/** Nom d'un palier pour TalkBack et la voix : « 2, Débutant » (sans point médian). */
fun levelSpoken(level: Int): String = "${level.coerceIn(1, 5)}, ${levelLabel(level)}"

private fun levelLabel(level: Int) = when (level) {
    1 -> "Les bases"
    2 -> "Débutant"
    3 -> "Novice"
    4 -> "Club"
    else -> "Confirmé"
}

/** En-tête d'une séance : nature (« Validation · 5 problèmes ») et sujet (titre de la leçon ou de l'exercice). */
@Immutable
data class SessionTitle(val label: String, val title: String)
