package com.ivanherboch.chessduck.feature

import androidx.lifecycle.ViewModel
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.data.GameRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import com.ivanherboch.chessduck.ui.LinePlaybackController
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.ChessDuckApp
import com.ivanherboch.chessduck.chess.GameEnd
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.PIECE_VALUES
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.data.Settings
import com.ivanherboch.chessduck.engine.MATE
import com.ivanherboch.chessduck.engine.isMateScore
import com.ivanherboch.chessduck.engine.mateIn
import com.ivanherboch.chessduck.engine.winPercent
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.moveAnim
import com.ivanherboch.chessduck.ui.undoAnim
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/** Analyse d'une position : évaluations (point de vue des Blancs) du meilleur et du 2e coup. */
data class Analysis(val eval: Int, val second: Int?, val best: String) {
    fun encode() = "$eval,${second ?: ""},$best"

    companion object {
        fun decodeAll(s: String, n: Int): List<Analysis?> {
            val parts = if (s.isEmpty()) emptyList() else s.split(';')
            return List(n) { i ->
                val t = parts.getOrNull(i)?.split(',') ?: return@List null
                val e = t.getOrNull(0)?.toIntOrNull() ?: return@List null
                Analysis(e, t.getOrNull(1)?.toIntOrNull(), t.getOrNull(2) ?: "")
            }
        }

        fun encodeAll(list: List<Analysis?>) = list.joinToString(";") { it?.encode() ?: "" }
    }
}

object GameLogic {
    /** Nouvel ELO selon la formule FIDE (K = 32). [score] : 1 victoire, 0,5 nulle, 0 défaite. */
    fun newElo(elo: Int, opponent: Int, score: Double, k: Int = 32): Int {
        val expected = 1.0 / (1.0 + 10.0.pow((opponent - elo) / 400.0))
        return (elo + k * (score - expected)).roundToInt()
    }

    /**
     * Annotation parcimonieuse d'un coup (!!, !, ?!, ?, ??) ou null pour un coup normal.
     * Calcul instantané à partir des analyses des positions avant ([a0]) et après ([a1]) le coup.
     */
    fun annotate(before: Position, move: Move, a0: Analysis?, a1: Analysis?, prev: Move?): String? {
        if (a0 == null || a1 == null) return null
        val sign = if (before.whiteToMove) 1 else -1
        val best = winPercent(sign * a0.eval)
        val after = winPercent(sign * a1.eval)
        val loss = best - after
        if (loss >= 25) return "??"
        if (loss >= 15) return "?"
        if (loss >= 9) return "?!"
        if (move.uci == a0.best && a0.second != null) {
            val second = winPercent(sign * a0.second)
            val recapture = prev != null && prev.to == move.to && before.isCapture(move)
            if (best - second >= 18 && best >= 35 && second <= 80 && !recapture) {
                return if (isSacrifice(before, move)) "!!" else "!"
            }
        }
        return null
    }

    private fun isSacrifice(before: Position, m: Move): Boolean {
        val moved = pieceType(before[m.from])
        if (moved == PAWN || moved == KING) return false
        val gain = PIECE_VALUES[pieceType(before[m.to])]
        if (gain >= PIECE_VALUES[moved]) return false
        val after = before.play(m)
        val attackers = after.attackers(m.to, after.whiteToMove)
        if (attackers.isEmpty()) return false
        val cheapest = attackers.minOf { PIECE_VALUES[pieceType(after[it])] }
        val defended = after.attackers(m.to, !after.whiteToMove).isNotEmpty()
        return cheapest < PIECE_VALUES[moved] || !defended
    }

    /**
     * Mat manqué : renvoie 1 ou 2 si le camp au trait disposait d'un mat en 1 ou 2 coups ([a0])
     * et que le coup joué ne le conserve pas ([a1] = analyse de la position obtenue), sinon null.
     */
    fun missedMate(whiteToMove: Boolean, a0: Analysis?, a1: Analysis?): Int? {
        if (a0 == null || a1 == null) return null
        val sign = if (whiteToMove) 1 else -1
        val before = sign * a0.eval
        if (!isMateScore(before) || before <= 0) return null
        val k = mateIn(before)
        if (k !in 1..2) return null
        val after = sign * a1.eval
        val kept = isMateScore(after) && after > 0 && mateIn(after) <= k - 1
        return if (kept) null else k
    }

    fun endText(end: GameEnd): String = when (end) {
        GameEnd.CHECKMATE -> "échec et mat"
        GameEnd.STALEMATE -> "pat"
        GameEnd.INSUFFICIENT -> "matériel insuffisant"
        GameEnd.FIFTY_MOVES -> "règle des 50 coups"
        GameEnd.REPETITION -> "triple répétition"
        GameEnd.NONE -> ""
    }
}

data class GameResult(val text: String, val tone: Tone)

const val COMPUTER = "🤖 Ordinateur"

data class Review(
    val record: GameRecord,
    val line: Line,
    val cursor: Int = 0,
    val playing: Boolean = true,
    val analysis: List<Analysis?>,
    val flipped: Boolean,
    val evalOn: Boolean = true,
    val anim: BoardAnim? = null,
)

data class PlayState(
    val line: Line = Line(START_FEN),
    val cursor: Int = 0,
    val playerWhite: Boolean = true,
    val twoPlayers: Boolean = false,
    val aiElo: Int = 1200,
    val flipped: Boolean = false,
    val anim: BoardAnim? = null,
    val thinking: Boolean = false,
    val result: GameResult? = null,
    val hintStage: Int = 0,
    val analysis: Map<Int, Analysis> = emptyMap(),
    val evalOn: Boolean = false,
    /** Partie qui vient de se terminer, enregistrée dans l'historique : « Analyser la partie » l'ouvre en revue. */
    val lastRecord: GameRecord? = null,
) {
    /** Une partie est en cours (au moins un échange joué, pas encore de résultat). */
    val inProgress: Boolean get() = result == null && line.size >= 2

    val position: Position get() = line.positions[cursor.coerceIn(0, line.size)]
    val canPlay: Boolean get() = result == null && !thinking && (twoPlayers || position.whiteToMove == playerWhite)
    val hint: Move? get() = analysis[cursor]?.best?.takeIf { it.isNotEmpty() }?.let { Move.fromUci(it) }
}

class PlayViewModel(
    private val prefs: SettingsRepository,
    private val gamesRepo: GameRepository,
    private val engine: ChessEngine,
    private val analyzer: GameAnalysisService,
) : ViewModel() {
    private val rnd = Random(System.nanoTime())
    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state.asStateFlow()
    // Flux partagés seulement tant que l'écran les observe (5 s de grâce pour une rotation ou un changement d'onglet).
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val games = gamesRepo.games().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tally = gamesRepo.tally().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Jeton de partie : incrémenté à chaque nouvelle partie, il invalide les résultats asynchrones d'une partie précédente. */
    private var gameGeneration = 0L
    private var applyJob: Job? = null
    private var aiJob: Job? = null
    private var analysisJob: Job? = null
    /** Lecture automatique de la revue de partie. */

    init {
        viewModelScope.launch { newGame(prefs.settings.first()) }
    }

    fun applySettings(twoPlayers: Boolean, color: Int, aiElo: Int) {
        applyJob?.cancel()
        applyJob = viewModelScope.launch {
            prefs.setGameSettings(twoPlayers, color, aiElo)
            newGame(prefs.settings.first())
        }
    }

    /** Nouvelle partie avec les réglages enregistrés (lus dans DataStore, même si l'écran n'est pas abonné). */
    fun newGame() {
        applyJob?.cancel()
        applyJob = viewModelScope.launch { newGame(prefs.settings.first()) }
    }

    private fun newGame(st: Settings) {
        gameGeneration++
        aiJob?.cancel(); analysisJob?.cancel()
        val white = when (st.colorChoice) {
            0 -> true
            2 -> false
            else -> rnd.nextBoolean()
        }
        val playerWhite = st.twoPlayers || white
        _state.update {
            PlayState(
                playerWhite = playerWhite, twoPlayers = st.twoPlayers, aiElo = st.aiElo, flipped = !playerWhite,
                evalOn = it.evalOn,
            )
        }
        if (!st.twoPlayers && !playerWhite) aiMove() else precompute()
    }

    fun flip() = _state.update { it.copy(flipped = !it.flipped) }

    fun toggleEval() {
        _state.update { it.copy(evalOn = !it.evalOn) }
        precompute()
    }

    /** Indice en 2 temps ; le meilleur coup est déjà pré-calculé en tâche de fond. */
    fun hint() {
        val s = state.value
        if (!s.canPlay) return
        _state.update { it.copy(hintStage = (it.hintStage + 1).coerceAtMost(2)) }
    }

    fun playerMove(m: Move) {
        val s = state.value
        if (!s.canPlay || !s.position.isLegal(m)) return
        analysisJob?.cancel()
        val cur = s.cursor
        val nl = s.line.copy(cur)
        nl.push(m)
        _state.update {
            it.copy(line = nl, cursor = nl.size, anim = moveAnim(s.position, m), hintStage = 0, analysis = it.analysis.filterKeys { k -> k <= cur })
        }
        afterMove()
    }

    private fun afterMove() {
        val s = state.value
        val end = s.line.end()
        when {
            end != GameEnd.NONE -> finish(end, null)
            !s.twoPlayers && s.line.last.whiteToMove != s.playerWhite -> aiMove()
            else -> precompute()
        }
    }

    private fun aiMove() {
        analysisJob?.cancel()
        aiJob?.cancel()
        _state.update { it.copy(thinking = true) }
        val gen = gameGeneration
        aiJob = viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            val s = state.value
            val pos = s.line.last
            val elo = s.aiElo
            // À partir de 1350 ELO, Stockfish se bride lui-même (UCI_Elo) : il choisit son coup à faible profondeur,
            // si bien que le temps de réflexion n'influe pas sur son niveau. Sous 1350 (son plancher), on affaiblit
            // le jeu par des fautes « humaines » : un coup plausible mais inférieur, dont la gravité est bornée par
            // le niveau choisi (jamais un coup tiré totalement au hasard).
            var move: Move? = null
            if (elo < 1350 && rnd.nextDouble() < (1350 - elo) / 1500.0) {
                val lines = engine.search(pos.toFen(), 160, multiPv = 30, depth = 4).lines
                val top = lines.firstOrNull()?.score
                if (top != null) {
                    val cap = 150 + ((1350 - elo) * 1.2).toInt()
                    move = lines.drop(1).filter { top - it.score <= cap && pos.isLegal(it.move) }.randomOrNull(rnd)?.move
                }
            }
            if (move == null) move = engine.search(pos.toFen(), 220 + elo / 10, elo = elo.coerceAtLeast(1350)).best
            if (move == null || !pos.isLegal(move)) move = pos.legalMoves.random(rnd)
            delay((550 - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
            // La partie a changé pendant la réflexion (nouvelle partie, réglages) : ce coup ne la concerne plus.
            if (gen != gameGeneration || state.value.line !== s.line) return@launch
            val nl = s.line.copy()
            nl.push(move)
            _state.update { it.copy(line = nl, cursor = nl.size, anim = moveAnim(pos, move), thinking = false, hintStage = 0) }
            val end = nl.end()
            if (end != GameEnd.NONE) finish(end, null) else precompute()
        }
    }

    private suspend fun analyse(pos: Position, ms: Int): Analysis? = analyzer.analyse(pos, ms)

    /** Pré-calcule en tâche de fond le meilleur coup de la position affichée (indice instantané). */
    private fun precompute() {
        analysisJob?.cancel()
        val s = state.value
        if (s.thinking || s.analysis.containsKey(s.cursor)) return
        val ply = s.cursor
        val line = s.line
        analysisJob = viewModelScope.launch {
            val a = analyse(line.positions[ply], 450) ?: return@launch
            _state.update { if (it.line === line) it.copy(analysis = it.analysis + (ply to a)) else it }
        }
    }

    fun back() = navigate(-1)
    fun forward() = navigate(1)

    /** Affiche la position après [n] demi-coups (clic dans la liste des coups). */
    fun goTo(n: Int) {
        val s = state.value
        if (s.thinking) return
        val t = n.coerceIn(0, s.line.size)
        if (t != s.cursor) navigate(t - s.cursor)
    }

    /**
     * Cible de « Annuler mon coup » : contre l'ordinateur, on revient avant le dernier coup du joueur
     * (deux demi-coups si l'ordinateur a déjà répondu) ; à deux joueurs, d'un demi-coup. -1 si impossible.
     */
    fun undoTarget(s: PlayState = state.value): Int {
        if (s.thinking || s.result != null || s.cursor == 0) return -1
        if (s.twoPlayers) return s.cursor - 1
        var t = s.cursor - 1
        while (t >= 0 && s.line.positions[t].whiteToMove != s.playerWhite) t--
        return t
    }

    fun undoMove() {
        val t = undoTarget()
        if (t < 0) return
        aiJob?.cancel()
        goTo(t)
    }

    private fun navigate(delta: Int) {
        val s = state.value
        if (s.thinking) return
        val t = (s.cursor + delta).coerceIn(0, s.line.size)
        if (t == s.cursor) return
        val anim = if (delta > 0) moveAnim(s.line.positions[s.cursor], s.line.moves[s.cursor]) else undoAnim(s.line.positions[t], s.line.moves[t])
        _state.update { it.copy(cursor = t, anim = anim, hintStage = 0) }
        precompute()
    }

    fun resign() {
        val s = state.value
        if (s.result != null) return
        aiJob?.cancel()
        _state.update { it.copy(thinking = false) }
        finish(GameEnd.NONE, if (s.twoPlayers) s.line.last.whiteToMove else s.playerWhite)
    }

    /** Clôt la partie : résultat, ELO FIDE (K = 32) contre l'ordinateur, enregistrement dans l'historique. */
    private fun finish(end: GameEnd, resignedWhite: Boolean?) {
        val s = state.value
        val line = s.line
        // 1 = victoire des Blancs, 0 = nulle, -1 = victoire des Noirs
        val whiteResult = when {
            resignedWhite != null -> if (resignedWhite) -1 else 1
            end == GameEnd.CHECKMATE -> if (line.last.whiteToMove) -1 else 1
            else -> 0
        }
        val reason = if (resignedWhite != null) "abandon" else GameLogic.endText(end)
        val resultStr = when (whiteResult) { 1 -> "1-0"; -1 -> "0-1"; else -> "1/2-1/2" }
        if (line.size < 2) {
            _state.update { it.copy(result = GameResult("Partie interrompue avant le premier échange : rien n'est enregistré.", Tone.NEUTRAL)) }
            return
        }
        val gen = gameGeneration
        // Mise à jour de l'écran seulement si la partie affichée est toujours celle qui vient de se terminer.
        fun show(f: (PlayState) -> PlayState) = _state.update { if (gen == gameGeneration) f(it) else it }
        // La partie est réellement terminée : son enregistrement et l'ELO ne sont jamais annulés.
        viewModelScope.launch(NonCancellable) {
            val st = prefs.settings.first()
            val analysis = Analysis.encodeAll(List(line.size + 1) { s.analysis[it] })
            if (s.twoPlayers) {
                val text = when (whiteResult) {
                    1 -> "Victoire de ${st.pseudo} (Blancs) par $reason."
                    -1 -> "Victoire de ${st.pseudo2} (Noirs) par $reason."
                    else -> "Partie nulle ($reason)."
                } + " Partie à 2 joueurs : l'ELO ne change pas."
                show { it.copy(result = GameResult(text, Tone.GOLD), thinking = false) }
                var record = GameRecord(
                        date = System.currentTimeMillis(), opponent = "2 joueurs", vsComputer = false, playerWhite = true, result = resultStr,
                        outcome = whiteResult, reason = reason, eloBefore = st.elo, eloAfter = st.elo, moves = line.uciMoves(), analysis = analysis,
                        whiteName = st.pseudo, blackName = st.pseudo2,
                    )
                record = record.copy(id = gamesRepo.insertGame(record))
                show { it.copy(lastRecord = record) }
                analyzer.start(record, line)
            } else {
                val outcome = if (s.playerWhite) whiteResult else -whiteResult
                val newElo = GameLogic.newElo(st.elo, s.aiElo, (outcome + 1) / 2.0)
                val diff = newElo - st.elo
                val delta = if (diff >= 0) "+$diff" else "−${-diff}"
                val head = when (outcome) {
                    1 -> "Victoire par $reason !"
                    -1 -> "Défaite par $reason."
                    else -> "Partie nulle ($reason)."
                }
                show {
                    it.copy(
                        thinking = false,
                        result = GameResult("$head ELO ${st.elo} → $newElo ($delta).", if (outcome == 1) Tone.GOOD else if (outcome == 0) Tone.GOLD else Tone.BAD),
                    )
                }
                prefs.setElo(newElo)
                var record = GameRecord(
                        date = System.currentTimeMillis(), opponent = "Ordinateur ${s.aiElo}", vsComputer = true, playerWhite = s.playerWhite,
                        result = resultStr, outcome = outcome, reason = reason, eloBefore = st.elo, eloAfter = newElo, moves = line.uciMoves(), analysis = analysis,
                        whiteName = if (s.playerWhite) st.pseudo else COMPUTER, blackName = if (s.playerWhite) COMPUTER else st.pseudo, opponentElo = s.aiElo,
                    )
                record = record.copy(id = gamesRepo.insertGame(record))
                show { it.copy(lastRecord = record) }
                analyzer.start(record, line)
            }
        }
    }

    fun deleteGame(id: Long) {
        viewModelScope.launch { gamesRepo.deleteGame(id) }
    }

}
