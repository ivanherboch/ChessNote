package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PIECE_VALUES
import com.ivanherboch.chessduck.chess.PieceLexiconFr
import com.ivanherboch.chessduck.chess.SpokenFr
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

/**
 * « Calcul à l'aveugle » : depuis [start], deux demi-coups sont annoncés sans être joués sur l'échiquier ; l'élève
 * trouve le coup suivant ([expected]) dans la position [after] qu'il doit se représenter.
 */
@Immutable
data class BlindItem(val start: Position, val announced: List<String>, val after: Position, val expected: Move, val answer: String)

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
     * Calcul à l'aveugle tiré d'un problème : après la faute adverse, le coup gagnant et la réponse sont annoncés ;
     * l'élève doit trouver le coup suivant de la solution.
     */
    fun blindFrom(p: Puzzle): BlindItem? {
        val line = runCatching { Line.fromUci(p.fen, p.moves) }.getOrNull() ?: return null
        if (line.size < 4) return null
        return BlindItem(line.positions[1], listOf(line.sans[1], line.sans[2]), line.positions[3], line.moves[3], line.sans[3])
    }

    /** Texte des coups annoncés, numérotés comme dans une partie (« 24. Nf5+ Kh8 » ou « 24… Qd2 25. Rxd2 »). */
    fun announcedText(b: BlindItem): String {
        val n = b.start.fullmove
        val (a, r) = b.announced
        return if (b.start.whiteToMove) "$n. $a $r" else "$n… $a ${n + 1}. $r"
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
    BLIND(
        "Calcul à l'aveugle",
        "Sans les voir joués, suivez les deux coups annoncés, puis touchez la case de départ et la case d'arrivée du coup suivant.",
    ),
}

@Immutable
data class ReflexSession(
    val kind: ReflexKind,
    val total: Int = 5,
    val index: Int = 0,
    val results: List<Boolean> = emptyList(),
    val threat: ThreatItem? = null,
    val capture: CaptureItem? = null,
    val blind: BlindItem? = null,
    /** Calcul à l'aveugle : case de départ touchée, et position après les coups annoncés réaffichée (aide). */
    val from: Int = -1,
    val shown: Boolean = false,
    /** Énoncé de la question en cours, auquel s'ajoutent les précisions (case de départ, aide sur la voix). */
    val prompt: String = "",
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
    val position: Position? get() = threat?.position ?: capture?.position ?: blind?.let { if (shown || answered) it.after else it.start }
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
    /** Lecture vocale des coups annoncés (calcul à l'aveugle) ; ne lit rien si la voix est coupée. */
    private val speak: (String) -> Unit = {},
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
            val themes = when (kind) {
                ReflexKind.THREAT -> ReflexDrills.THREAT_THEMES
                ReflexKind.CAPTURE -> ReflexDrills.CAPTURE_THEMES
                ReflexKind.BLIND -> null
            }
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
                    ReflexKind.BLIND -> ReflexDrills.blindFrom(p)
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
        val blind = item as? BlindItem
        _state.update {
            val prompt = "Question ${index + 1} sur ${queue.size}. ${it?.kind?.question.orEmpty()}" +
                (blind?.let { b -> " Coups annoncés : ${ReflexDrills.announcedText(b)}." } ?: "")
            it?.copy(
                index = index, answered = false, tapped = -1, from = -1, shown = false, tone = Tone.INFO,
                threat = item as? ThreatItem, capture = item as? CaptureItem, blind = blind, prompt = prompt, message = prompt,
            )
        }
        blind?.let { announce(it) }
    }

    /** Calcul à l'aveugle : lit les coups annoncés (si la voix est active ; sous TalkBack, le texte suffit). */
    fun announce(b: BlindItem? = _state.value?.blind) {
        val item = b ?: return
        val side = if (item.after.whiteToMove) "des Blancs" else "des Noirs"
        speak(SpokenFr.text("Coups annoncés : ${SpokenFr.move(item.announced[0])}, puis ${SpokenFr.move(item.announced[1])}. Quel est le coup suivant $side ?"))
    }

    /** Précision ajoutée à l'énoncé de la question en cours (par exemple : comment activer la voix). */
    fun note(text: String) = _state.update { s -> s?.takeIf { !it.answered && !it.finished }?.copy(message = "${s.prompt}\n\n$text", tone = Tone.GOLD) ?: s }

    /** Calcul à l'aveugle : réaffiche l'échiquier après les coups annoncés (la réponse ne comptera plus). */
    fun showBoard() = _state.update { it?.takeIf { s -> s.blind != null && !s.answered }?.copy(shown = true) ?: it }

    /** Menace : case touchée par l'élève ; calcul à l'aveugle : case de départ, puis case d'arrivée. */
    fun tap(sq: Int) {
        val s = _state.value ?: return
        s.blind?.let { b -> return blindTap(s, b, sq) }
        val t = s.threat ?: return
        if (s.answered) return
        val ok = sq == t.target
        _state.value = s.copy(
            answered = true, tapped = sq, results = s.results + ok, tone = if (ok) Tone.GOOD else Tone.BAD,
            message = (if (ok) "Bien vu ! " else "Ce n'est pas la pièce menacée. ") + t.explanation,
        )
    }

    private fun blindTap(s: ReflexSession, b: BlindItem, sq: Int) {
        if (s.answered) return
        if (s.from < 0 || s.from == sq) {
            // Premier toucher : la case de départ est écrite dans la carte ; un second toucher l'annule.
            val from = if (s.from == sq) -1 else sq
            _state.value = s.copy(
                from = from, tone = Tone.INFO,
                message = if (from < 0) s.prompt else "${s.prompt}\n\nDépart : ${sqName(from)}. Touchez maintenant la case d'arrivée.",
            )
            return
        }
        val right = s.from == b.expected.from && sq == b.expected.to
        val ok = right && !s.shown
        _state.value = s.copy(
            answered = true, tapped = sq, results = s.results + ok, tone = if (right) Tone.GOOD else Tone.BAD,
            message = when {
                ok -> "Bien calculé ! Le coup suivant était ${b.answer}."
                right -> "Exact : ${b.answer}. Vous avez réaffiché l'échiquier : réessayez sans le regarder."
                else -> "Ce n'est pas le bon coup. Il fallait jouer ${b.answer}. Regardez la position après les coups annoncés."
            },
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
