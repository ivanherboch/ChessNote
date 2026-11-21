package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.data.AcademyAssessment
import com.ivanherboch.chessduck.data.LessonProgress
import com.ivanherboch.chessduck.data.LessonProgressRepository
import com.ivanherboch.chessduck.data.PuzzleRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.ui.REFUTE_HOLD
import com.ivanherboch.chessduck.ui.Refutation
import com.ivanherboch.chessduck.ui.undoAnim
import com.ivanherboch.chessduck.ui.Narrator
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.moveAnim
import com.ivanherboch.chessduck.util.runCatchingSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlin.random.Random

/**
 * Accès de [PracticeSeries] à l'Académie : son état (la série vit dans [AcademyState.practice]) et les quelques services
 * partagés avec les autres modes. Implémenté en privé par [AcademyViewModel], qui reste la façade de l'écran.
 */
internal interface PracticeHost {
    val mutableState: MutableStateFlow<AcademyState>
    val lesson: Lesson
    /** La dernière révision a été réussie sans hésiter (l'intervalle de répétition s'allonge). */
    val reviewFluent: Boolean
    fun pulse(good: Boolean)
    fun warmEngine()
    fun cancelAll()
    fun backToDrawer()
    suspend fun saveProgress(id: Int, update: (LessonProgress) -> LessonProgress): LessonProgress
    suspend fun completeReview(success: Boolean, fluent: Boolean)
}

/**
 * Séries de problèmes de l'Académie : validation d'une leçon, révision, « Le seul coup », test de placement, examen.
 * Tirage des problèmes, coups de l'élève comme dans l'onglet Problèmes (réfutation, indice en deux temps, solution),
 * puis bilan (maîtrise, palier, examen).
 */
internal class PracticeSeries(
    private val host: PracticeHost,
    private val scope: CoroutineScope,
    private val prefs: SettingsRepository,
    private val lessons: LessonProgressRepository,
    private val narrator: Narrator,
    private val engine: ChessEngine,
    private val puzzles: PuzzleRepository,
    private val random: Random,
    private val srs: SrsScheduler,
) {
    private val _state get() = host.mutableState
    private val state: StateFlow<AcademyState> get() = host.mutableState
    private val lesson: Lesson get() = host.lesson
    private var practiceJob: Job? = null

    /** Arrête le travail en cours de la série (tirage, réponse adverse, réfutation). */
    fun cancel() {
        practiceJob?.cancel()
    }

    /** Validation d'une leçon de tactique : 5 problèmes inédits de son thème, au palier de la leçon. */
    fun startPractice() {
        val l = lesson
        if (l.theme.isEmpty()) return
        startSeries(PracticeKind.LESSON, List(SIZE) { PuzzleSpec(listOf(l.theme), AcademyAssessments.levelRange(l.level)) })
    }

    /** « Le seul coup » : 5 positions où une seule défense tient. */
    fun startOnlyMove() {
        scope.launch {
            val level = prefs.settings.first().academyLevel
            startSeries(PracticeKind.ONLY_MOVE, List(SIZE) { PuzzleSpec(listOf("defensiveMove"), AcademyAssessments.levelRange(level)) })
        }
    }

    /** Test de placement facultatif (12 positions, paliers 2 à 5). */
    fun startPlacement() = startSeries(PracticeKind.PLACEMENT, AcademyAssessments.placementSpecs())

    /** Examen du palier en cours (10 positions mélangées, réussite à 8). */
    fun startExam() {
        scope.launch {
            // Examen du palier en cours : le réussir fait passer au palier suivant (1 → 2, 2 → 3…).
            val level = prefs.settings.first().academyLevel.coerceIn(1, 5)
            startSeries(PracticeKind.EXAM, AcademyAssessments.examSpecs(level, random), level)
        }
    }

    fun startSeries(kind: PracticeKind, specs: List<PuzzleSpec>, level: Int = 2) {
        host.warmEngine()
        host.cancelAll()
        narrator.stop()
        _state.update { it.copy(practice = Practice(kind = kind, level = level, message = "Préparation des problèmes…"), quiz = null, sessionFlipped = false) }
        practiceJob = scope.launch {
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
            val hearts = if (kind in HEARTS_KINDS) ids.size else 0
            _state.update { it.copy(practice = Practice(kind = kind, ids = ids, loading = false, level = level, hearts = hearts, heartsMax = hearts)) }
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
                    refute = null, refuteUntil = 0L, hadError = false, penalized = false,
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
        host.pulse(true)
        if (finished) {
            val ok = pr.hintStage < 2 && !pr.penalized
            // Enregistrement à part du travail annulable de la série : passer tout de suite au problème suivant, ou fermer
            // la série, ne doit pas faire perdre la résolution.
            if (ok) {
                val id = pr.ids[pr.index]
                scope.launch { withContext(NonCancellable) { runCatchingSuspend { puzzles.markSolved(id) } } }
            }
            _state.update { s ->
                s.copy(
                    practice = pr.copy(
                        cursor = cur + 1, progress = cur + 1, anim = moveAnim(pos, m), solved = true, results = pr.results + ok,
                        tone = if (ok) Tone.GOOD else Tone.GOLD,
                        line = if (m == expected) line else Line(line.startFen, line.moves.take(cur) + m),
                        message = when {
                            ok && pr.hadError -> "Résolu ! Votre erreur vous a seulement coûté un cœur."
                            ok -> "Résolu !"
                            pr.penalized -> "Résolu, mais après une erreur sans cœur restant : ce problème ne compte pas."
                            else -> "Résolu, mais avec l'indice qui montre le coup : ce problème ne compte pas."
                        },
                    ),
                )
            }
            return
        }
        _state.update { s -> s.copy(practice = pr.copy(cursor = cur + 1, progress = cur + 1, anim = moveAnim(pos, m), hintStage = 0, tone = Tone.GOOD, message = "Bien vu. La suite arrive…")) }
        practiceJob = scope.launch {
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
        host.pulse(false)
        val after = pos.play(m)
        practiceMissed = pos to m
        // Un cœur couvre l'erreur (le problème compte encore s'il est résolu) ; sans cœur, le problème est manqué.
        val heart = pr.hearts > 0
        val left = if (heart) pr.hearts - 1 else 0
        val extra = when {
            heart -> " Vous perdez un cœur (${heartsText(left)}) : trouvez la solution, le problème comptera encore."
            pr.heartsMax > 0 -> " Plus de cœur : ce problème ne comptera plus, mais trouvez la solution."
            else -> " Ce problème ne comptera plus, mais trouvez la solution."
        }
        _state.update { s ->
            s.copy(
                practice = pr.copy(
                    hadError = true, hearts = left, penalized = pr.penalized || !heart, hintStage = 0, tone = Tone.BAD, anim = moveAnim(pos, m), refute = Refutation(after, m), refuteUntil = 0L, refuteHold = 0L,
                    message = RefutationLogic.intro(pos, m),
                ),
            )
        }
        practiceJob = scope.launch {
            RefutationLogic.play(engine, pos, m, extra = extra) { refute, anim, message, until ->
                _state.update { s ->
                    val p2 = s.practice?.takeIf { it.refute != null } ?: return@update s
                    s.copy(practice = p2.copy(anim = anim ?: p2.anim, refute = refute, refuteUntil = until, refuteHold = REFUTE_HOLD, message = message))
                }
            }
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
            s.copy(practice = pr.copy(refuteUntil = 0L, message = RefutationLogic.held(pr.message, "Réessayer")))
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
        practiceJob = scope.launch { nextPracticeOrFinish(pr.index) }
    }

    private suspend fun nextPracticeOrFinish(index: Int) {
        val pr = state.value.practice ?: return
        if (index + 1 < pr.ids.size) return showPractice(index + 1)
        val score = pr.score
        val total = pr.ids.size
        val text = when (pr.kind) {
            PracticeKind.LESSON -> lessonPracticeDone(score, total)
            PracticeKind.REVIEW -> { host.completeReview(score >= 1, host.reviewFluent && score == total); "$score sur $total." + if (score >= 1) " Révision réussie." else " Révision à refaire demain." }
            PracticeKind.ONLY_MOVE -> "$score sur $total. " + if (score >= PASS) "Vous savez repérer la menace et la parer." else "Cherchez d'abord la menace adverse, puis la seule parade."
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
            else -> score >= PASS
        }
        _state.update { s -> s.copy(practice = s.practice?.copy(finished = true, solved = false, failed = false, tone = if (good) Tone.GOOD else Tone.GOLD, message = text)) }
    }

    private suspend fun lessonPracticeDone(score: Int, total: Int): String {
        val l = lesson
        val passed = score >= PASS
        val p = host.saveProgress(l.id) { p ->
            val mastered = p.mastered || (passed && p.stars >= 2)
            p.copy(
                masteredAt = if (p.mastered) p.masteredAt else if (mastered) srs.now() else 0,
                practicePassed = maxOf(score, p.practicePassed), practiceTotal = total,
            )
        }
        return when {
            p.mastered && passed -> "$score sur $total : leçon maîtrisée ! Vous savez appliquer cette idée sur des positions inédites."
            passed -> "$score sur $total : série réussie. Réussissez aussi le Défi avec au plus une erreur pour maîtriser la leçon."
            else -> "$score sur $total : il en faut $PASS pour valider. Relisez la leçon en mode Guidé, puis retentez la série."
        }
    }

    /** Quitte la série (ou son bilan) et revient à la leçon. */
    fun closePractice() {
        practiceJob?.cancel()
        _state.update { it.copy(practice = null) }
        host.backToDrawer()
    }

    companion object {
        /** Séries avec des cœurs : les entraînements ; le placement et l'examen mesurent le niveau, sans droit à l'erreur. */
        val HEARTS_KINDS = setOf(PracticeKind.LESSON, PracticeKind.ONLY_MOVE, PracticeKind.REVIEW)
        /** Problèmes d'une série de validation (et du « Seul coup »). */
        const val SIZE = 5
        /** Problèmes réussis pour valider une série. */
        const val PASS = 4
    }
}

/** « il vous reste 3 cœurs », « il ne vous reste aucun cœur ». */
internal fun heartsText(n: Int): String = when (n) {
    0 -> "il ne vous reste aucun cœur"
    1 -> "il vous reste 1 cœur"
    else -> "il vous reste $n cœurs"
}
