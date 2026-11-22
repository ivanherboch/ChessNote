package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position

/**
 * Décision du filet de sécurité (et de la partie accompagnée), sans état : faut-il arrêter le coup [m] joué en [pos], et
 * pourquoi ? [before] : analyse de [pos] ; [after] : analyse de la position après le coup (évaluations côté Blancs).
 *
 * On n'arrête qu'une gaffe nette et explicable :
 * - le coup fait perdre au moins [BLUNDER_CP] centipions par rapport au meilleur coup ;
 * - la partie n'était pas déjà perdue (au-delà de [LOST_CP]) et le joueur ne reste pas nettement gagnant ([STILL_WINNING_CP]) ;
 * - l'explication nomme un danger concret (pièce en prise ou qui va tomber, mat permis, fourchette…), ou le coup fait pat
 *   alors que le joueur gagnait.
 */
object SafetyNet {
    const val BLUNDER_CP = 200
    const val LOST_CP = 300
    const val STILL_WINNING_CP = 300

    fun decide(pos: Position, m: Move, before: Analysis, after: Analysis): ReviewComments.NetReason? {
        val sign = if (pos.whiteToMove) 1 else -1
        val next = pos.play(m)
        // Pat alors qu'on gagnait : la victoire devient nulle.
        if (next.legalMoves.isEmpty() && !next.inCheck && sign * before.eval >= BLUNDER_CP) {
            return ReviewComments.NetReason(
                "${pos.san(m)} ferait pat : l'adversaire n'aurait plus aucun coup et la partie serait nulle, alors que vous gagnez. " +
                    "Laissez toujours une case au roi adverse.",
                null, before.best.takeIf { it.isNotEmpty() }?.let { Move.fromUci(it) }?.takeIf { pos.isLegal(it) && it != m },
                stop = true,
            )
        }
        if (sign * before.eval <= -LOST_CP) return null
        if (sign * (before.eval - after.eval) < BLUNDER_CP) return null
        if (sign * after.eval >= STILL_WINNING_CP) return null
        val reason = runCatching { ReviewComments.netReason(pos, m, before, after) }.getOrNull() ?: return null
        return reason.takeIf { it.topic != null }
    }
}
