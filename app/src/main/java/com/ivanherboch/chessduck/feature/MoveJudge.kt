package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.engine.isMateScore
import com.ivanherboch.chessduck.engine.winPercent
import com.ivanherboch.chessduck.util.runCatchingSuspend
import kotlinx.coroutines.withTimeoutOrNull

/** Verdict sur un coup joué à la place du coup attendu d'une leçon. */
enum class Verdict {
    /** Le coup attendu. */
    EXACT,
    /** Un autre coup, aussi bon : on l'accepte sans le compter comme une erreur. */
    EQUIVALENT,
    /** Un coup nettement moins bon. */
    WORSE,
    /** L'Ordinateur n'a pas pu trancher (absent ou trop lent) : seul le coup attendu est accepté. */
    UNAVAILABLE,
}

/**
 * Juge de coups de l'Académie : un coup différent de celui de la leçon est-il aussi bon ?
 * L'Ordinateur cherche les 4 meilleures suites (MultiPV) ; un coup absent de ces suites est évalué depuis la position
 * qu'il donne. Les deux scores (point de vue du joueur) sont comparés en probabilité de gain ([winPercent]).
 * - Ouverture et milieu de partie : équivalent si la perte est d'au plus [MAX_LOSS] points de pourcentage.
 * - Finale : le résultat théorique doit être préservé. Un mat forcé doit rester un mat forcé (mat en 6 au lieu de 5
 *   accepté) ; un gain décisif (≥ 95 %) ne doit pas passer sous 90 % (pat, gain gâché) ; une nulle ne doit pas perdre
 *   plus de 12 points.
 */
class MoveJudge(private val engine: ChessEngine, private val movetimeMs: Int = 250) {

    suspend fun judge(position: Position, played: Move, expected: Move, endgame: Boolean): Verdict {
        if (played == expected) return Verdict.EXACT
        if (!position.isLegal(played)) return Verdict.WORSE
        val after = position.play(played)
        if (after.isCheckmate) return Verdict.EQUIVALENT
        // Une recherche MultiPV (4 meilleures suites) suffit le plus souvent : les deux coups y figurent avec des scores
        // comparables. Sinon, la position obtenue après chacun des deux coups est évaluée à part.
        val lines = runCatchingSuspend {
            withTimeoutOrNull(movetimeMs * 4L + 500) { engine.search(position.toFen(), movetimeMs, multiPv = 4).lines }
        }.getOrNull().orEmpty().associate { it.move to it.score }
        val both = played in lines && expected in lines
        val mine = (if (both) lines[played] else score(after)) ?: return Verdict.UNAVAILABLE
        val target = (if (both) lines[expected] else score(position.play(expected))) ?: return Verdict.UNAVAILABLE
        return if (acceptable(mine, target, endgame)) Verdict.EQUIVALENT else Verdict.WORSE
    }

    /** Score (centipions, mat codé ±(MATE − n)) de la position du point de vue du joueur qui vient de jouer. */
    private suspend fun score(after: Position): Int? = when {
        after.isCheckmate -> com.ivanherboch.chessduck.engine.MATE
        after.legalMoves.isEmpty() -> 0 // pat
        else -> runCatchingSuspend {
            withTimeoutOrNull(movetimeMs * 4L + 500) { engine.search(after.toFen(), movetimeMs).score }
        }.getOrNull()?.let { -it }
    }

    companion object {
        const val MAX_LOSS = 6.0

        /** Règle de comparaison, isolée pour les tests : [mine] et [target] du point de vue du joueur. */
        fun acceptable(mine: Int, target: Int, endgame: Boolean): Boolean {
            val wMine = winPercent(mine)
            val wTarget = winPercent(target)
            if (!endgame) return wTarget - wMine <= MAX_LOSS
            return when {
                isMateScore(target) && target > 0 -> isMateScore(mine) && mine > 0
                wTarget >= 95.0 -> wMine >= 90.0
                else -> wTarget - wMine <= 12.0
            }
        }
    }
}
