package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.PIECE_NAMES_FR
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.engine.isMateScore
import com.ivanherboch.chessduck.engine.mateIn
import com.ivanherboch.chessduck.util.runCatchingSuspend
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Réponse adverse à un mauvais coup : [reply] (null si l'adversaire n'a plus de coup légal : mat ou pat),
 * [position] après cette réponse, et le [text] qui explique la conséquence de la faute.
 */
class RefutationReply(val reply: Move?, val position: Position, val text: String)

/**
 * Calcul commun de la réfutation (Problèmes, Académie) : l'Ordinateur cherche la meilleure réponse pendant que le mauvais
 * coup reste affiché au moins [minShow] ms ; sans réponse à temps, un coup légal plausible (reprise sur la case) la
 * remplace. Le texte nomme la conséquence : mat, pièce perdue, avantage rendu, égalisation.
 */
object RefutationLogic {
    const val AUTO_UNDO = " Touchez l'échiquier pour rejouer tout de suite (sinon, le coup s'annule seul)."

    suspend fun reply(engine: ChessEngine, pos: Position, m: Move, minShow: Long = 650): RefutationReply {
        val t0 = System.currentTimeMillis()
        val san = pos.san(m)
        val after = pos.play(m)
        val opp = if (pos.whiteToMove) "Noirs" else "Blancs"
        if (after.legalMoves.isEmpty()) {
            val text = if (after.inCheck) "$san donne échec et mat : ce n'est pourtant pas la ligne attendue."
            else "$san met l'adversaire pat : la partie serait nulle alors que vous aviez bien mieux !"
            return RefutationReply(null, after, text)
        }
        val res = runCatchingSuspend { withTimeoutOrNull(1800) { engine.search(after.toFen(), 380) } }.getOrNull()
        delay((minShow - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
        val reply = res?.best?.takeIf { after.isLegal(it) }
            ?: after.legalMoves.firstOrNull { it.to == m.to } ?: after.legalMoves.first()
        val after2 = after.play(reply)
        val score = res?.score
        val captured = after[reply.to]
        val detail = when {
            after2.isCheckmate -> " : échec et mat !"
            score != null && isMateScore(score) && score > 0 -> " et forcent le mat en ${mateIn(score)}."
            captured != 0 && pieceType(captured) != PAWN -> " et capturent votre ${PIECE_NAMES_FR[pieceType(captured)].lowercase()}."
            captured != 0 -> " et gagnent un pion."
            score != null && score > 150 -> " et prennent l'avantage."
            score != null && score > -80 -> " et égalisent : votre avantage s'est envolé."
            else -> " et se défendent : il existait un coup bien plus fort."
        }
        return RefutationReply(reply, after2, "Réfutation : après $san, les $opp répondent ${after.san(reply)}$detail")
    }
}
