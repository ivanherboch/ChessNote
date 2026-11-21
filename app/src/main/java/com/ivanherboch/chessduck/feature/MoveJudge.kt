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
    /** Un coup qui garde le mat, mais moins vite que la méthode de la leçon. */
    SLOWER,
    /** L'Ordinateur n'a pas pu trancher (absent ou trop lent) : seul le coup attendu est accepté. */
    UNAVAILABLE,
}

/**
 * Juge de coups de l'Académie : un coup différent de celui de la leçon est-il aussi bon ?
 * L'Ordinateur cherche les 4 meilleures suites (MultiPV) ; un coup absent de ces suites est évalué depuis la position
 * qu'il donne. Les deux scores (point de vue du joueur) sont comparés en probabilité de gain ([winPercent]).
 * - Leçon qui mène au mat : un autre coup n'est équivalent que s'il mate au moins aussi vite ; s'il garde le mat mais
 *   plus lentement, c'est [Verdict.SLOWER] (la méthode de la leçon est plus directe) ; s'il perd le mat, il est moins bon.
 * - Ouverture et milieu de partie : équivalent si la perte est d'au plus [MAX_LOSS] points de pourcentage.
 * - Finale : un gain décisif (≥ 95 %) ne doit pas passer sous 90 % (pat, gain gâché) ; une nulle ne doit pas perdre
 *   plus de 12 points.
 */
class MoveJudge(private val engine: ChessEngine, private val movetimeMs: Int = 250) {
    /**
     * Attente maximale d'une réponse, file d'attente comprise : le moteur peut être en train de démarrer ou de finir
     * une autre analyse (pré-chauffage, indice). Trop courte, elle faisait refuser de bons coups au premier essai.
     */
    private val waitMs = maxOf(movetimeMs * 4L + 500, JUDGE_WAIT)

    suspend fun judge(position: Position, played: Move, expected: Move, endgame: Boolean): Verdict {
        if (played == expected) return Verdict.EXACT
        if (!position.isLegal(played)) return Verdict.WORSE
        if (position.play(played).isCheckmate) return Verdict.EQUIVALENT
        val (mine, target) = scores(position, played, expected) ?: return Verdict.UNAVAILABLE
        return compare(mine, target, endgame)
    }

    /**
     * Perte (en points de pourcentage de gain, 0 si le coup est au moins aussi bon) du coup [played] par rapport à
     * [expected] ; null si l'Ordinateur ne répond pas. Sert au barème de « Devinez le coup ».
     */
    suspend fun loss(position: Position, played: Move, expected: Move): Double? {
        if (played == expected || position.play(played).isCheckmate) return 0.0
        val (mine, target) = scores(position, played, expected) ?: return null
        return (winPercent(target) - winPercent(mine)).coerceAtLeast(0.0)
    }

    /** Scores (point de vue du joueur) du coup joué et du coup attendu. */
    private suspend fun scores(position: Position, played: Move, expected: Move): Pair<Int, Int>? {
        // Une recherche MultiPV (4 meilleures suites) suffit le plus souvent : les deux coups y figurent avec des scores
        // comparables. Sinon, la position obtenue après chacun des deux coups est évaluée à part.
        val lines = runCatchingSuspend {
            withTimeoutOrNull(waitMs) { engine.search(position.toFen(), movetimeMs, multiPv = 4).lines }
        }.getOrNull().orEmpty().associate { it.move to it.score }
        val both = played in lines && expected in lines
        val mine = (if (both) lines[played] else score(position.play(played))) ?: return null
        val target = (if (both) lines[expected] else score(position.play(expected))) ?: return null
        return mine to target
    }

    /** Score (centipions, mat codé ±(MATE − n)) de la position du point de vue du joueur qui vient de jouer. */
    private suspend fun score(after: Position): Int? = when {
        after.isCheckmate -> com.ivanherboch.chessduck.engine.MATE
        after.legalMoves.isEmpty() -> 0 // pat
        else -> runCatchingSuspend {
            withTimeoutOrNull(waitMs) { engine.search(after.toFen(), movetimeMs).score }
        }.getOrNull()?.let { -it }
    }

    companion object {
        const val MAX_LOSS = 6.0
        /** Attente minimale d'un jugement (démarrage du moteur sur un téléphone lent compris). */
        const val JUDGE_WAIT = 5_000L

        /** Règle de comparaison, isolée pour les tests : [mine] et [target] du point de vue du joueur. */
        fun compare(mine: Int, target: Int, endgame: Boolean): Verdict {
            if (isMateScore(target) && target > 0) return when {
                // Mat codé MATE − n : plus le score est grand, plus le mat est rapide.
                isMateScore(mine) && mine > 0 && mine >= target -> Verdict.EQUIVALENT
                isMateScore(mine) && mine > 0 -> Verdict.SLOWER
                else -> Verdict.WORSE
            }
            val wMine = winPercent(mine)
            val wTarget = winPercent(target)
            val ok = when {
                !endgame -> wTarget - wMine <= MAX_LOSS
                wTarget >= 95.0 -> wMine >= 90.0
                else -> wTarget - wMine <= 12.0
            }
            return if (ok) Verdict.EQUIVALENT else Verdict.WORSE
        }

        fun acceptable(mine: Int, target: Int, endgame: Boolean): Boolean = compare(mine, target, endgame) == Verdict.EQUIVALENT
    }
}
