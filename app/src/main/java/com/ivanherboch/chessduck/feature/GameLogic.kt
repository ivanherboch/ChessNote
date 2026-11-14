package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
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
