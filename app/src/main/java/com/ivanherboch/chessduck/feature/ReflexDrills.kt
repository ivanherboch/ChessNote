package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PIECE_VALUES
import com.ivanherboch.chessduck.chess.PieceLexiconFr
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.isWhite
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.sqName
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.data.PuzzleRepository
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.util.runCatchingSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** « Quelle est la menace ? » : l'adversaire vient de jouer [lastMove] ; il menace de prendre la pièce en [target]. */
@Immutable
data class ThreatItem(val position: Position, val lastMove: Move, val target: Int, val attacker: Int, val explanation: String)

/** « Puis-je prendre ? » : la prise [capture] est-elle bonne ([good]) ? */
@Immutable
data class CaptureItem(val position: Position, val capture: Move, val good: Boolean, val explanation: String)

/** Exercices de réflexes générés automatiquement à partir des problèmes embarqués. */
object ReflexDrills {
    /** Thèmes où l'adversaire crée une menace claire de gain de matériel. */
    val THREAT_THEMES = listOf("fork", "skewer", "pin", "discoveredAttack", "trappedPiece", "hangingPiece", "attraction")
    /** Thèmes riches en prises à évaluer. */
    val CAPTURE_THEMES = listOf("hangingPiece", "capturingDefender", "trappedPiece", "fork", "pin", "skewer")

    private fun value(type: Int) = PIECE_VALUES[type]
    private fun points(n: Int) = "$n point${if (n > 1) "s" else ""}"

    /**
     * Menace tirée d'un problème Lichess : après la faute (1er coup) et le coup gagnant (2e coup), l'élève a le trait
     * avec le camp qui a fait la faute. La menace est la prise que la solution réalise au 4e coup, sur une pièce qui
     * était déjà sur sa case (l'élève ne l'a pas déplacée en répondant).
     */
    fun threatFrom(p: Puzzle): ThreatItem? {
        val line = runCatching { Line.fromUci(p.fen, p.moves) }.getOrNull() ?: return null
        if (line.size < 4) return null
        val pos = line.positions[2]
        val follow = line.moves[3]
        if (!line.positions[3].isCapture(follow) || line.moves[2].from == follow.to || line.moves[2].to == follow.to) return null
        val victim = pos[follow.to]
        val attacker = pos[follow.from]
        if (victim == 0 || attacker == 0 || isWhite(victim) != pos.whiteToMove || isWhite(attacker) == pos.whiteToMove) return null
        if (pieceType(victim) == KING) return null
        val text = "${PieceLexiconFr.definiteCapitalized(pieceType(attacker))} ${sqName(follow.from)} menace de prendre " +
            "votre ${PieceLexiconFr.name(pieceType(victim))} en ${sqName(follow.to)}."
        return ThreatItem(pos, line.moves[1], follow.to, follow.from, text)
    }

    /**
     * Question de prise sur la position [pos] (élève au trait, pas en échec) : une prise nettement bonne ou nettement
     * mauvaise selon le bilan matériel (pièce prise, puis reprise éventuelle de la pièce qui a pris).
     */
    fun captureFrom(pos: Position, rnd: Random): CaptureItem? {
        if (pos.inCheck) return null
        val options = pos.legalMoves.filter { pos[it.to] != 0 && it.promo == 0 }.mapNotNull { m ->
            val victim = pieceType(pos[m.to])
            val attacker = pieceType(pos[m.from])
            if (attacker == KING) return@mapNotNull null
            val defended = pos.play(m).legalMoves.any { it.to == m.to }
            val gain = value(victim) - if (defended) value(attacker) else 0
            if (gain in -1..1) null else Triple(m, gain, defended)
        }
        val (m, gain, defended) = options.randomOrNull(rnd) ?: return null
        val victim = pieceType(pos[m.to])
        val attacker = pieceType(pos[m.from])
        val target = "${PieceLexiconFr.definite(victim)} en ${sqName(m.to)}"
        val fem = if (PieceLexiconFr.isFeminine(victim)) "e" else ""
        val text = when {
            gain > 0 && !defended -> "Oui : $target n'est pas protégé$fem. Vous gagnez ${points(value(victim))}."
            gain > 0 -> "Oui : $target est protégé$fem, mais vous échangez votre ${PieceLexiconFr.name(attacker)} contre une pièce plus forte : vous gagnez ${points(gain)}."
            else -> "Non : $target est protégé$fem. Vous donneriez votre ${PieceLexiconFr.name(attacker)} pour ${PieceLexiconFr.indefinite(victim)} : vous perdriez ${points(-gain)}."
        }
        return CaptureItem(pos, m, gain > 0, text)
    }
}

/** Type d'exercice de réflexes. */
enum class ReflexKind(val label: String, val question: String) {
    THREAT("Quelle est la menace ?", "L'adversaire vient de jouer. Touchez la pièce qu'il menace de prendre."),
    CAPTURE("Puis-je prendre ?", "Cette prise est-elle bonne ? Répondez par oui ou par non."),
}

@Immutable
data class ReflexSession(
    val kind: ReflexKind,
    val total: Int = 5,
    val index: Int = 0,
    val results: List<Boolean> = emptyList(),
    val threat: ThreatItem? = null,
    val capture: CaptureItem? = null,
    /** Réponse donnée à la question en cours. */
    val answered: Boolean = false,
    /** Case touchée (menace). */
    val tapped: Int = -1,
    val loading: Boolean = true,
    val finished: Boolean = false,
    val message: String = "",
    val tone: Tone = Tone.INFO,
) {
    val score: Int get() = results.count { it }
    val position: Position? get() = threat?.position ?: capture?.position
}

/**
 * Entraîneur de réflexes : prépare [ReflexSession.total] questions à partir des problèmes du palier, puis enchaîne
 * question, réponse et explication. Une prise n'est retenue que si l'Ordinateur confirme le verdict matériel.
 */
class ReflexTrainer(
    private val scope: CoroutineScope,
    private val puzzles: PuzzleRepository,
    private val engine: ChessEngine,
    private val random: Random,
    private val onFinished: suspend (ReflexKind, Int, Int) -> Unit = { _, _, _ -> },
) {
    private val _state = MutableStateFlow<ReflexSession?>(null)
    val state: StateFlow<ReflexSession?> = _state.asStateFlow()
    private var job: Job? = null
    private val queue = ArrayList<Any>()
    /** L'Ordinateur a répondu jusqu'ici ; s'il se tait une fois, on garde le verdict matériel pour la séance. */
    private var engineOk = true

    fun start(kind: ReflexKind, level: Int, total: Int = 5) {
        job?.cancel()
        _state.value = ReflexSession(kind, total = total, message = "Préparation des exercices…")
        job = scope.launch {
            queue.clear()
            engineOk = true
            val range = AcademyAssessments.levelRange(level)
            val themes = if (kind == ReflexKind.THREAT) ReflexDrills.THREAT_THEMES else ReflexDrills.CAPTURE_THEMES
            var exclude = ""
            repeat(total * 8) {
                if (queue.size >= total) return@repeat
                val id = runCatchingSuspend { puzzles.randomId(themes, range, exclude, random) }.getOrNull() ?: return@repeat
                exclude = id
                val p = runCatchingSuspend { puzzles.byId(id) }.getOrNull() ?: return@repeat
                val item: Any? = when (kind) {
                    ReflexKind.THREAT -> ReflexDrills.threatFrom(p)
                    ReflexKind.CAPTURE -> runCatching { Line.fromUci(p.fen, p.moves).positions[1] }.getOrNull()
                        ?.let { ReflexDrills.captureFrom(it, random) }?.takeIf { confirmed(it) }
                }
                if (item != null && queue.none { it == item }) queue.add(item)
            }
            if (queue.isEmpty()) {
                _state.value = ReflexSession(kind, total = 0, loading = false, finished = true, message = "Aucun exercice disponible pour ce palier.")
                return@launch
            }
            _state.update { it?.copy(total = queue.size, loading = false) }
            show(0)
        }
    }

    /** L'Ordinateur confirme-t-il le verdict ? (sans réponse du moteur, on garde le verdict matériel) */
    private suspend fun confirmed(c: CaptureItem): Boolean {
        if (!engineOk) return true
        val best = runCatchingSuspend { withTimeoutOrNull(2_000) { engine.search(c.position.toFen(), 200).score } }.getOrNull()
            ?: return true.also { engineOk = false }
        val after = c.position.play(c.capture)
        if (after.legalMoves.isEmpty()) return c.good
        val reply = runCatchingSuspend { withTimeoutOrNull(2_000) { engine.search(after.toFen(), 200).score } }.getOrNull()
            ?: return true.also { engineOk = false }
        val engineGood = best - (-reply) <= 150
        return engineGood == c.good
    }

    private fun show(index: Int) {
        val item = queue[index]
        _state.update {
            it?.copy(
                index = index, answered = false, tapped = -1, tone = Tone.INFO,
                threat = item as? ThreatItem, capture = item as? CaptureItem,
                message = "Question ${index + 1} sur ${queue.size}. ${it.kind.question}",
            )
        }
    }

    /** Menace : case touchée par l'élève. */
    fun tap(sq: Int) {
        val s = _state.value ?: return
        val t = s.threat ?: return
        if (s.answered) return
        val ok = sq == t.target
        _state.value = s.copy(
            answered = true, tapped = sq, results = s.results + ok, tone = if (ok) Tone.GOOD else Tone.BAD,
            message = (if (ok) "Bien vu ! " else "Ce n'est pas la pièce menacée. ") + t.explanation,
        )
    }

    /** Prise : réponse oui ou non. */
    fun answer(yes: Boolean) {
        val s = _state.value ?: return
        val c = s.capture ?: return
        if (s.answered) return
        val ok = yes == c.good
        _state.value = s.copy(
            answered = true, results = s.results + ok, tone = if (ok) Tone.GOOD else Tone.BAD,
            message = (if (ok) "Exact. " else "Pas tout à fait. ") + c.explanation,
        )
    }

    fun next() {
        val s = _state.value ?: return
        if (!s.answered) return
        if (s.index + 1 < queue.size) return show(s.index + 1)
        _state.value = s.copy(
            finished = true, tone = if (s.score * 5 >= s.total * 4) Tone.GOOD else Tone.GOLD,
            message = "${s.score} sur ${s.total}. " + if (s.score * 5 >= s.total * 4) "Excellent réflexe !" else "Recommencez pour l'automatiser : ce réflexe évite les gaffes.",
        )
        job = scope.launch { onFinished(s.kind, s.score, s.total) }
    }

    fun stop() {
        job?.cancel()
        _state.value = null
    }
}
