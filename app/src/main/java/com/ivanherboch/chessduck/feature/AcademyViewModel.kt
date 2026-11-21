package com.ivanherboch.chessduck.feature

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
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
import com.ivanherboch.chessduck.data.GameRepository
import com.ivanherboch.chessduck.data.LessonProgress
import com.ivanherboch.chessduck.data.LessonProgressRepository
import com.ivanherboch.chessduck.data.PuzzleRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.ui.REFUTE_HOLD
import com.ivanherboch.chessduck.ui.Refutation
import com.ivanherboch.chessduck.ui.undoAnim
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

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
    val srs = SrsScheduler(clock)
    /** Progression, accueil, bilan et suggestions du tiroir (voir AcademyOverview). */
    private val overview = AcademyOverview(
        viewModelScope, prefs, lessons, games, srs, state.map { it.lessonId }.distinctUntilChanged(), work,
    )
    val mastered: StateFlow<Set<Int>> = overview.mastered
    /** Progression détaillée (étoiles, exercices, révisions) par leçon. */
    val progress: StateFlow<Map<Int, LessonProgress>> = overview.progress
    /** Accueil : leçon à reprendre, révisions dues, leçon conseillée selon le palier. */
    val home: StateFlow<AcademyHome> = overview.home
    /** Bilan : les 3 priorités du moment, d'après les parties analysées et la progression dans l'Académie. */
    val priorities: StateFlow<List<Priority>> = overview.priorities

    /** Lecture automatique et séquences du mode Défi : un seul travail à la fois. */
    private val playback = LinePlaybackController(viewModelScope)
    private val judge = MoveJudge(engine)
    private var judgeJob: Job? = null
    /** Une leçon a été ouverte par un pont avant la fin de la restauration de l'état au démarrage. */
    private var openedExplicitly = false
    private var pulses = 0L
    /** Finale jouée lancée depuis une leçon (validation) ou une révision. */
    private var endgameLesson: Int? = null
    private var endgameReview = false

    /** Finales jouables contre l'Ordinateur. */
    val endgame = EndgameTrainer(viewModelScope, engine, random, onFinished = ::endgameFinished, work = work ?: EmptyCoroutineContext)
    /** Exercices de réflexes (menace, prise). */
    val reflex = ReflexTrainer(
        viewModelScope, puzzles, engine, random, speak = { if (narration.value) narrator.speak(it) }, work = work ?: EmptyCoroutineContext,
    )

    /**
     * Séries de problèmes (voir PracticeSeries). Déclarée avant le bloc init : l'ouverture de la leçon au démarrage
     * (cancelAll) s'en sert déjà quand le fil principal exécute la coroutine sans attendre.
     */
    private val practice = PracticeSeries(
        object : PracticeHost {
            override val mutableState get() = _state
            override val lesson get() = this@AcademyViewModel.lesson
            override val reviewFluent get() = this@AcademyViewModel.reviewFluent
            override fun pulse(good: Boolean) = this@AcademyViewModel.pulse(good)
            override fun warmEngine() = this@AcademyViewModel.warmEngine()
            override fun cancelAll() = this@AcademyViewModel.cancelAll()
            override fun backToDrawer() = this@AcademyViewModel.backToDrawer()
            override suspend fun saveProgress(id: Int, update: (LessonProgress) -> LessonProgress) = this@AcademyViewModel.saveProgress(id, update)
            override suspend fun completeReview(success: Boolean, fluent: Boolean) = this@AcademyViewModel.completeReview(success, fluent)
        },
        viewModelScope, prefs, lessons, narrator, engine, puzzles, random, srs,
    )

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

    /** Le moteur a déjà été démarré (pré-chauffage fait une seule fois). */
    private var engineWarm = false

    /**
     * Démarre le moteur en tâche de fond, une seule fois : dès l'ouverture de l'Académie (init), pour que le premier
     * coup jugé (arrêt « À vous », Défi, exercices, finale, réflexes) n'attende jamais son lancement ; les points
     * d'entrée des séances le rappellent par sûreté (sans effet s'il est déjà démarré).
     */
    private fun warmEngine() {
        if (engineWarm) return
        engineWarm = true
        viewModelScope.launch { runCatchingSuspend { withTimeoutOrNull(10_000) { engine.search(com.ivanherboch.chessduck.chess.START_FEN, 50) } } }
    }

    init {
        // L'Académie est l'onglet d'accueil : l'Ordinateur démarre aussitôt, en arrière-plan, sans bloquer l'écran.
        warmEngine()
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
        const val PRACTICE_SIZE = PracticeSeries.SIZE
        const val PRACTICE_PASS = PracticeSeries.PASS
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
        practice.cancel()
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
        if (asking()) warmEngine()
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
        if (asking()) warmEngine()
        narrate()
    }

    /** Indice pendant un arrêt « À vous » du mode Guidé : la pièce à jouer est surlignée en vert. */
    fun guideHint() {
        val s = state.value
        if (!asking(s) || s.judging || s.hintStage >= 2) return
        // Deux temps, comme partout ailleurs : la pièce à jouer, puis la flèche du coup.
        val stage = s.hintStage + 1
        _state.update {
            it.copy(
                hintStage = stage, noteTone = Tone.GOLD,
                note = if (stage == 1) "Indice 1/2 : la pièce à jouer est surlignée en vert." else "Indice 2/2 : la flèche verte montre le coup.",
            )
        }
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
                Verdict.UNAVAILABLE -> _state.update {
                    // L'Ordinateur n'a pas répondu à temps : le coup n'est pas jugé faux, l'élève le rejoue.
                    it.copy(
                        judging = false, tentative = null, anim = undoAnim(pos, m), noteTone = Tone.GOLD,
                        note = "L'Ordinateur n'a pas pu juger $san à temps : rejouez votre coup.",
                    )
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
        warmEngine()
        val l = lesson
        val intro = if (l.kind == LessonKind.MODEL_GAME) {
            (if (state.value.keyMoments) "Devinez les ${l.pauseSteps.size} moments clés des ${sideName(l)} (les autres coups se jouent seuls)" else "Devinez les coups des ${sideName(l)}") +
                " : 3 points pour le coup du maître, 1 ou 2 points pour un coup presque aussi bon."
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
        val l = lesson
        if (cursor >= ln.size || ln.positions[cursor].whiteToMove != l.heroWhite) return false
        // « Moments clés » : seuls les arrêts de la partie modèle sont à deviner.
        return !(state.value.keyMoments && l.kind == LessonKind.MODEL_GAME && l.pauseSteps.isNotEmpty() && cursor !in l.pauseSteps)
    }

    /** Partie modèle : deviner tous les coups, ou seulement les moments clés (le Défi repart du début). */
    fun setKeyMoments(on: Boolean) {
        if (state.value.keyMoments == on) return
        _state.update { it.copy(keyMoments = on) }
        if (state.value.challenge) restartChallenge()
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
                // Coup du joueur joué seul (« Moments clés ») : ce n'est pas une réponse adverse.
                val own = ln.positions[cur].whiteToMove == lesson.heroWhite
                val reply = if (own) "${ln.sans[cur]} : ${tx[cur]}" else "Réponse ${ln.sans[cur]} : ${tx[cur]}"
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
                    // L'Ordinateur n'a pas répondu à temps : ce n'est pas la faute de l'élève, aucune erreur n'est comptée.
                    _state.update {
                        it.copy(
                            judging = false, tentative = null, anim = undoAnim(pos, m), tone = Tone.GOLD,
                            message = "L'Ordinateur n'a pas pu juger ${pos.san(m)} à temps : rejouez votre coup.",
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
            if (loss == null) {
                // L'Ordinateur n'a pas répondu à temps : pas de 0 point injuste, le coup est simplement à rejouer.
                _state.update {
                    it.copy(
                        judging = false, tentative = null, anim = undoAnim(pos, m), tone = Tone.GOLD,
                        message = "L'Ordinateur n'a pas pu juger ${pos.san(m)} à temps : rejouez votre coup.",
                    )
                }
                return@launch
            }
            val pts = when {
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
        val after = pos.play(m)
        missed = pos to m
        _state.update {
            it.copy(
                judging = false, errors = it.errors + 1, tone = Tone.BAD, tentative = null, anim = null,
                refute = Refutation(after, m, null), message = RefutationLogic.intro(pos, m, "le bon coup"),
            )
        }
        RefutationLogic.play(engine, pos, m) { refute, anim, message, until ->
            _state.update { it.copy(anim = anim ?: it.anim, refute = refute, refuteUntil = until, refuteHold = REFUTE_HOLD, message = message) }
        }
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
                message = RefutationLogic.held(it.message, "« Reprendre au coup manqué »"),
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

    // ---- Séries de problèmes : validation, révision, « Le seul coup », placement, examen (voir PracticeSeries) ----


    /** Validation d'une leçon de tactique : 5 problèmes inédits de son thème, au palier de la leçon. */
    fun startPractice() = practice.startPractice()
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
    fun startOnlyMove() = practice.startOnlyMove()
    /** Test de placement facultatif (12 positions, paliers 2 à 5). */
    fun startPlacement() = practice.startPlacement()
    /** Examen du palier en cours (10 positions mélangées, réussite à 8). */
    fun startExam() = practice.startExam()
    private fun startSeries(kind: PracticeKind, specs: List<PuzzleSpec>, level: Int = 2) = practice.startSeries(kind, specs, level)
    fun practiceMove(m: Move) = practice.practiceMove(m)
    fun retryPractice() = practice.retryPractice()
    fun holdPracticeRefutation() = practice.holdPracticeRefutation()
    fun practiceSolution() = practice.practiceSolution()
    fun practiceBack() = practice.practiceBack()
    fun practiceForward() = practice.practiceForward()
    fun practiceHint() = practice.practiceHint()
    fun nextPractice() = practice.nextPractice()
    fun closePractice() = practice.closePractice()

    /** Marque la séance qui va commencer comme lancée depuis le tiroir « Entraînements ». */
    fun markFromDrawer() = _state.update { it.copy(fromDrawer = true) }

    /** Fin d'une séance lancée depuis le tiroir : on y revient (au lieu de laisser la dernière leçon ouverte à l'écran). */
    private fun backToDrawer() = _state.update { if (it.fromDrawer) it.copy(fromDrawer = false, drawerReturn = it.drawerReturn + 1) else it }

    // ---- Finales jouées contre l'Ordinateur ----

    /** Lance la finale [family] ; depuis une leçon, la réussite (3 positions sur 5) valide sa maîtrise. */
    fun startEndgame(family: EndgameFamily, fromLesson: Boolean = false, total: Int = 5) {
        warmEngine()
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

    /** Nombre de suggestions « À faire maintenant » du tiroir, affiché sur le bouton « Entraînements ». */
    val suggestions: StateFlow<Int> = overview.suggestions

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
        warmEngine()
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
                // Sans finale connue (donnée de leçon incohérente), la révision est validée par le Défi seul.
                val family = EndgameFamily.of(l.drill) ?: return completeReview(true, fluent)
                startEndgame(family, total = 1)
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
