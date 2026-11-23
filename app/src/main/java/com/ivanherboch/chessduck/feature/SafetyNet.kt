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
    /** Perte minimale pour arrêter un « échec gagnant » dont la suite ne se nomme pas simplement. */
    const val CHECK_REPLY_CP = 300

    /** Thèmes de conseil général, jamais une raison suffisante pour arrêter un coup. */
    private val ADVICE = setOf(ReviewTopic.CASTLING, ReviewTopic.DEVELOPMENT)

    fun decide(pos: Position, m: Move, before: Analysis, after: Analysis): ReviewComments.NetReason? {
        val sign = if (pos.whiteToMove) 1 else -1
        val next = pos.play(m)
        // Pat alors qu'on gagnait : la victoire devient nulle.
        // (Les coups du joueur qui matent sont exclus d'office : après eux, l'évaluation est un mat pour lui.)
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
        // Seuls les dangers concrets arrêtent : le développement et le roque sont des conseils, pas une gaffe à expliquer.
        if (reason.topic != null && reason.topic !in ADVICE) return reason
        // Échec gagnant permis : la réponse est un échec qui fait basculer la partie (gain de matériel ou attaque du roi),
        // par une suite trop longue pour être nommée. On l'arrête seulement si la perte est nette, en nommant l'échec.
        val reply = after.best.takeIf { it.isNotEmpty() }?.let { Move.fromUci(it) }?.takeIf { next.isLegal(it) } ?: return null
        if (!next.play(reply).inCheck || sign * (before.eval - after.eval) < CHECK_REPLY_CP) return null
        return ReviewComments.NetReason(
            "Après ${pos.san(m)}, l'échec ${next.san(reply)} fait basculer la partie : l'adversaire gagne du matériel ou attaque " +
                "votre roi. Avant de jouer, regardez toujours les échecs que vous laissez à l'adversaire." +
                (reason.best?.let { "\n\nCoup conseillé : " + ReviewComments.bestNote(pos, it) } ?: ""),
            null, reason.best, stop = true,
        )
    }
}
