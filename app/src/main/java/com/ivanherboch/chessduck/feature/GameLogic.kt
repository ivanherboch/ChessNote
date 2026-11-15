package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.chess.GameEnd
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.PIECE_VALUES
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.sqName
import com.ivanherboch.chessduck.chess.PieceLexiconFr
import com.ivanherboch.chessduck.engine.MATE
import com.ivanherboch.chessduck.engine.mateFor
import com.ivanherboch.chessduck.engine.winPercent
import kotlin.math.pow
import kotlin.math.roundToInt

/** Analyse d'une position : évaluations (point de vue des Blancs) du meilleur et du 2e coup. */
data class Analysis(val eval: Int, val second: Int?, val best: String) {
    fun encode() = "$eval,${second ?: ""},$best"

    companion object {
        /** Évaluation définitive d'une partie terminée en [pos] : score de mat du vainqueur, ou 0 pour une nulle. */
        fun decided(pos: Position, end: GameEnd) = Analysis(if (end == GameEnd.CHECKMATE) (if (pos.whiteToMove) -MATE else MATE) else 0, null, "")

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
    /**
     * Pourquoi la pièce en [sq] (au camp qui a le trait) ne peut pas bouger, en une phrase : roi en échec,
     * pièce clouée sur son roi, ou simplement aucune case libre.
     */
    fun whyStuck(p: Position, sq: Int): String {
        val t = pieceType(p[sq])
        val name = "${PieceLexiconFr.definiteCapitalized(t)} en ${sqName(sq)}"
        val f = PieceLexiconFr.isFeminine(t)
        if (p.inCheck) return "Votre roi est en échec : ${PieceLexiconFr.definite(t)} en ${sqName(sq)} ne peut pas parer l'échec. Protégez d'abord votre roi."
        if (t != KING) {
            val king = p.kingSquare(p.whiteToMove)
            val without = Position(p.board.copyOf().also { it[sq] = 0 }, p.whiteToMove, p.castling, p.ep, p.halfmove, p.fullmove)
            if (king >= 0 && without.isAttacked(king, !p.whiteToMove)) {
                return "$name est ${if (f) "clouée" else "cloué"} : ${if (f) "si elle bougeait" else "s'il bougeait"}, votre roi serait en échec."
            }
        }
        return "$name n'a aucune case libre pour l'instant."
    }

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
        val k = mateFor(a0.eval, whiteToMove)?.takeIf { it in 1..2 } ?: return null
        val left = mateFor(a1.eval, whiteToMove)
        return if (left != null && left <= k - 1) null else k
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
