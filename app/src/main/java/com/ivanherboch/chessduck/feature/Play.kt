package com.ivanherboch.chessduck.feature

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
    val review: Review? = null,
) {
    val position: Position get() = line.positions[cursor.coerceIn(0, line.size)]
    val canPlay: Boolean get() = result == null && !thinking && review == null && (twoPlayers || position.whiteToMove == playerWhite)
    val hint: Move? get() = analysis[cursor]?.best?.takeIf { it.isNotEmpty() }?.let { Move.fromUci(it) }
}

class PlayViewModel(app: Application) : AndroidViewModel(app) {
    private val duck = app as ChessDuckApp
    private val rnd = Random(System.nanoTime())
    val state = MutableStateFlow(PlayState())
    val settings = duck.prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val games = duck.db.user().games().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tally = duck.db.user().tally().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var aiJob: Job? = null
    private var analysisJob: Job? = null
    private var reviewJob: Job? = null
    private var reviewAnalysisJob: Job? = null
    private var analysisGameId = -1L
    /** Analyses en cours ou terminées, par partie : la revue s'ouvre sans rien recalculer. */
    private val analysisCache = HashMap<Long, List<Analysis?>>()

    init {
        viewModelScope.launch { newGame(duck.prefs.settings.first()) }
    }

    fun applySettings(twoPlayers: Boolean, color: Int, aiElo: Int) {
        viewModelScope.launch {
            duck.prefs.setGameSettings(twoPlayers, color, aiElo)
            newGame(duck.prefs.settings.first())
        }
    }

    fun newGame() = newGame(settings.value)

    private fun newGame(st: Settings) {
        aiJob?.cancel(); analysisJob?.cancel()
        val white = when (st.colorChoice) {
            0 -> true
            2 -> false
            else -> rnd.nextBoolean()
        }
        val playerWhite = st.twoPlayers || white
        state.update {
            PlayState(
                playerWhite = playerWhite, twoPlayers = st.twoPlayers, aiElo = st.aiElo, flipped = !playerWhite,
                evalOn = it.evalOn, review = it.review,
            )
        }
        if (!st.twoPlayers && !playerWhite) aiMove() else precompute()
    }

    fun flip() = state.update { s -> s.review?.let { s.copy(review = it.copy(flipped = !it.flipped)) } ?: s.copy(flipped = !s.flipped) }

    fun toggleEval() {
        state.update { s -> s.review?.let { s.copy(review = it.copy(evalOn = !it.evalOn)) } ?: s.copy(evalOn = !s.evalOn) }
        if (state.value.review == null) precompute()
    }

    /** Indice en 2 temps ; le meilleur coup est déjà pré-calculé en tâche de fond. */
    fun hint() {
        val s = state.value
        if (!s.canPlay) return
        state.update { it.copy(hintStage = (it.hintStage + 1).coerceAtMost(2)) }
    }

    fun playerMove(m: Move) {
        val s = state.value
        if (!s.canPlay || !s.position.isLegal(m)) return
        analysisJob?.cancel()
        val cur = s.cursor
        val nl = s.line.copy(cur)
        nl.push(m)
        state.update {
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
        state.update { it.copy(thinking = true) }
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
                val lines = duck.engine.search(pos.toFen(), 160, multiPv = 30, depth = 4).lines
                val top = lines.firstOrNull()?.score
                if (top != null) {
                    val cap = 150 + ((1350 - elo) * 1.2).toInt()
                    move = lines.drop(1).filter { top - it.score <= cap && pos.isLegal(it.move) }.randomOrNull(rnd)?.move
                }
            }
            if (move == null) move = duck.engine.search(pos.toFen(), 220 + elo / 10, elo = elo.coerceAtLeast(1350)).best
            if (move == null || !pos.isLegal(move)) move = pos.legalMoves.random(rnd)
            delay((550 - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
            val nl = s.line.copy()
            nl.push(move)
            state.update { it.copy(line = nl, cursor = nl.size, anim = moveAnim(pos, move), thinking = false, hintStage = 0) }
            val end = nl.end()
            if (end != GameEnd.NONE) finish(end, null) else precompute()
        }
    }

    private suspend fun analyse(pos: Position, ms: Int): Analysis? {
        if (pos.legalMoves.isEmpty()) return Analysis(if (pos.inCheck) (if (pos.whiteToMove) -MATE else MATE) else 0, null, "")
        val r = duck.engine.search(pos.toFen(), ms, multiPv = 2)
        val first = r.lines.firstOrNull() ?: return null
        val sign = if (pos.whiteToMove) 1 else -1
        return Analysis(sign * first.score, r.lines.getOrNull(1)?.let { sign * it.score }, first.move.uci)
    }

    /** Pré-calcule en tâche de fond le meilleur coup de la position affichée (indice instantané). */
    private fun precompute() {
        analysisJob?.cancel()
        val s = state.value
        if (s.thinking || s.analysis.containsKey(s.cursor)) return
        val ply = s.cursor
        val line = s.line
        analysisJob = viewModelScope.launch {
            val a = analyse(line.positions[ply], 450) ?: return@launch
            state.update { if (it.line === line) it.copy(analysis = it.analysis + (ply to a)) else it }
        }
    }

    fun back() = navigate(-1)
    fun forward() = navigate(1)

    private fun navigate(delta: Int) {
        val s = state.value
        if (s.thinking) return
        val t = (s.cursor + delta).coerceIn(0, s.line.size)
        if (t == s.cursor) return
        val anim = if (delta > 0) moveAnim(s.line.positions[s.cursor], s.line.moves[s.cursor]) else undoAnim(s.line.positions[t], s.line.moves[t])
        state.update { it.copy(cursor = t, anim = anim, hintStage = 0) }
        precompute()
    }

    fun resign() {
        val s = state.value
        if (s.result != null || s.review != null) return
        aiJob?.cancel()
        state.update { it.copy(thinking = false) }
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
            state.update { it.copy(result = GameResult("Partie interrompue avant le premier échange : rien n'est enregistré.", Tone.NEUTRAL)) }
            return
        }
        viewModelScope.launch {
            val st = duck.prefs.settings.first()
            val analysis = Analysis.encodeAll(List(line.size + 1) { s.analysis[it] })
            if (s.twoPlayers) {
                val text = when (whiteResult) {
                    1 -> "🏆 Victoire de ${st.pseudo} (Blancs) par $reason."
                    -1 -> "🏆 Victoire de ${st.pseudo2} (Noirs) par $reason."
                    else -> "🤝 Partie nulle ($reason)."
                } + " Partie à 2 joueurs : l'ELO ne change pas."
                state.update { it.copy(result = GameResult(text, Tone.GOLD), thinking = false) }
                val record = GameRecord(
                        date = System.currentTimeMillis(), opponent = "2 joueurs", vsComputer = false, playerWhite = true, result = resultStr,
                        outcome = whiteResult, reason = reason, eloBefore = st.elo, eloAfter = st.elo, moves = line.uciMoves(), analysis = analysis,
                        whiteName = st.pseudo, blackName = st.pseudo2,
                    )
                startAnalysis(record.copy(id = duck.db.user().insertGame(record)), line)
            } else {
                val outcome = if (s.playerWhite) whiteResult else -whiteResult
                val newElo = GameLogic.newElo(st.elo, s.aiElo, (outcome + 1) / 2.0)
                val diff = newElo - st.elo
                val delta = if (diff >= 0) "+$diff" else "−${-diff}"
                val head = when (outcome) {
                    1 -> "🏆 Victoire par $reason !"
                    -1 -> "Défaite par $reason."
                    else -> "🤝 Partie nulle ($reason)."
                }
                state.update {
                    it.copy(
                        thinking = false,
                        result = GameResult("$head ELO ${st.elo} → $newElo ($delta). Revoyez la partie dans 📜 Historique.", if (outcome == 1) Tone.GOOD else if (outcome == 0) Tone.GOLD else Tone.BAD),
                    )
                }
                duck.prefs.setElo(newElo)
                val record = GameRecord(
                        date = System.currentTimeMillis(), opponent = "Ordinateur ${s.aiElo}", vsComputer = true, playerWhite = s.playerWhite,
                        result = resultStr, outcome = outcome, reason = reason, eloBefore = st.elo, eloAfter = newElo, moves = line.uciMoves(), analysis = analysis,
                        whiteName = if (s.playerWhite) st.pseudo else COMPUTER, blackName = if (s.playerWhite) COMPUTER else st.pseudo, opponentElo = s.aiElo,
                    )
                startAnalysis(record.copy(id = duck.db.user().insertGame(record)), line)
            }
        }
    }

    fun deleteGame(id: Long) {
        viewModelScope.launch { duck.db.user().deleteGame(id) }
    }

    // ---- Revue de partie ----

    /**
     * Analyse une partie en tâche de fond (une position après l'autre, recherche courte) et enregistre le résultat.
     * Lancée dès la fin de la partie : quand on ouvre la revue, l'essentiel est déjà prêt.
     */
    private fun startAnalysis(g: GameRecord, line: Line) {
        if (analysisGameId == g.id && reviewAnalysisJob?.isActive == true) return
        reviewAnalysisJob?.cancel()
        analysisGameId = g.id
        val list = (analysisCache[g.id] ?: Analysis.decodeAll(g.analysis, line.size + 1)).toMutableList()
        analysisCache[g.id] = list.toList()
        reviewAnalysisJob = viewModelScope.launch {
            var changed = false
            try {
                for (i in 0..line.size) {
                    val existing = list[i]
                    if (existing != null && (existing.second != null || existing.best.isEmpty())) continue
                    val a = analyse(line.positions[i], 110) ?: continue
                    list[i] = a
                    changed = true
                    val snapshot = list.toList()
                    analysisCache[g.id] = snapshot
                    state.update { s ->
                        val r = s.review
                        if (r == null || r.record.id != g.id) s else s.copy(review = r.copy(analysis = snapshot))
                    }
                }
            } finally {
                // Même interrompue, l'analyse déjà faite est conservée.
                if (changed) withContext(NonCancellable) { duck.db.user().saveAnalysis(g.id, Analysis.encodeAll(list)) }
            }
        }
    }

    /** Ouvre une partie de l'historique : évaluation activée et lecture automatique lancée. */
    fun openReview(g: GameRecord) {
        reviewJob?.cancel()
        val line = runCatching { Line.fromUci(START_FEN, g.moves) }.getOrNull() ?: return
        val analysis = analysisCache[g.id] ?: Analysis.decodeAll(g.analysis, line.size + 1)
        state.update { it.copy(review = Review(g, line, 0, true, analysis, flipped = g.vsComputer && !g.playerWhite)) }
        autoplay()
        startAnalysis(g, line)
    }

    private fun autoplay() {
        reviewJob?.cancel()
        reviewJob = viewModelScope.launch {
            while (true) {
                delay(1150)
                val r = state.value.review ?: return@launch
                if (r.cursor >= r.line.size) break
                reviewGo(r.cursor + 1)
            }
            state.update { s -> s.review?.let { s.copy(review = it.copy(playing = false)) } ?: s }
        }
    }

    private fun reviewGo(target: Int) {
        state.update { s ->
            val r = s.review ?: return@update s
            val t = target.coerceIn(0, r.line.size)
            if (t == r.cursor) return@update s
            val anim = when (t) {
                r.cursor + 1 -> moveAnim(r.line.positions[r.cursor], r.line.moves[r.cursor])
                r.cursor - 1 -> undoAnim(r.line.positions[t], r.line.moves[t])
                else -> null
            }
            s.copy(review = r.copy(cursor = t, anim = anim))
        }
    }

    fun reviewPause() {
        reviewJob?.cancel()
        state.update { s -> s.review?.let { s.copy(review = it.copy(playing = false)) } ?: s }
    }

    fun reviewToggle() {
        val r = state.value.review ?: return
        if (r.playing) return reviewPause()
        if (r.cursor >= r.line.size) reviewGo(0)
        state.update { s -> s.review?.let { s.copy(review = it.copy(playing = true)) } ?: s }
        autoplay()
    }

    fun reviewStep(delta: Int) { reviewPause(); reviewGo((state.value.review?.cursor ?: 0) + delta) }
    fun reviewFirst() { reviewPause(); reviewGo(0) }
    fun reviewLast() { reviewPause(); reviewGo(Int.MAX_VALUE) }

    fun closeReview() {
        // L'analyse continue en arrière-plan : elle sera prête à la prochaine ouverture.
        reviewJob?.cancel()
        state.update { it.copy(review = null) }
    }
}
