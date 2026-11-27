package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.PIECE_NAMES_FR
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.engine.isMateScore
import com.ivanherboch.chessduck.engine.mateIn
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.REFUTE_HOLD
import com.ivanherboch.chessduck.ui.Refutation
import com.ivanherboch.chessduck.ui.moveAnim
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
 * coup reste affiché au moins [minShow] ms ; sans réponse à temps, aucune réponse n'est inventée (le texte le dit). Le texte nomme la conséquence : mat, pièce perdue, avantage rendu, égalisation.
 */
object RefutationLogic {
    const val AUTO_UNDO = " Touchez l'échiquier pour rejouer tout de suite (sinon, le coup s'annule seul)."

    /** Camp qui réfute le coup joué en [pos] (l'adversaire de celui qui a le trait). */
    fun opponentName(pos: Position): String = if (pos.whiteToMove) "Noirs" else "Blancs"

    /** Message affiché dès le mauvais coup [m], avant la réponse : « Fxe5 n'est pas la solution. Regardez… ». */
    fun intro(pos: Position, m: Move, what: String = "la solution"): String =
        "${pos.san(m)} n'est pas $what. Regardez la réponse des ${opponentName(pos)}…"

    /** Message une fois le retour automatique suspendu : la consigne finale nomme le bouton qui rend la main. */
    fun held(message: String, button: String): String = message.removeSuffix(AUTO_UNDO) + " Prenez votre temps, puis touchez $button."

    /**
     * Déroulé commun d'une réfutation (Problèmes, Défi, séries de l'Académie), une fois le mauvais coup [m] montré :
     * la réponse adverse est calculée, puis publiée par [show] (réfutation, glissement de la réponse, texte suivi de
     * [extra] et de [AUTO_UNDO], échéance du retour automatique) ; après [REFUTE_HOLD] ms, l'appelant rétablit la position.
     */
    suspend fun play(
        engine: ChessEngine, pos: Position, m: Move, extra: String = "",
        show: (refute: Refutation, anim: BoardAnim?, message: String, until: Long) -> Unit,
    ) {
        val after = pos.play(m)
        val r = reply(engine, pos, m)
        show(
            Refutation(r.position, r.reply ?: m, r.reply), r.reply?.let { reply -> moveAnim(after, reply) },
            r.text + extra + AUTO_UNDO, System.currentTimeMillis() + REFUTE_HOLD,
        )
        delay(REFUTE_HOLD)
    }

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
        // L'Ordinateur n'a pas répondu à temps : aucune réponse inventée (pas de flèche sur un coup pris au hasard).
        val reply = res?.best?.takeIf { after.isLegal(it) }
            ?: return RefutationReply(null, after, "$san n'est pas la solution. L'Ordinateur n'a pas pu calculer la réponse des $opp à temps : cherchez un autre coup.")
        val after2 = after.play(reply)
        // Score du point de vue du camp qui répond ; négatif : le joueur reste devant malgré tout.
        val score = res.score
        // Type de la pièce prise (prise en passant comprise : la case d'arrivée est alors vide).
        val captured = if (after.isCapture(reply)) after.capturedType(reply) else 0
        val detail = when {
            after2.isCheckmate -> " : échec et mat !"
            score != null && isMateScore(score) && score > 0 -> " et forcent le mat en ${mateIn(score)}."
            // Le coup du joueur gagnait aussi, autrement : on ne parle pas d'une « pièce capturée » dans un mat inévitable.
            score != null && isMateScore(score) -> " : $san mate aussi, mais plus lentement (mat en ${-mateIn(score)}). La solution est plus directe."
            score != null && score <= -300 -> " : $san garde un gros avantage, mais la solution gagne plus nettement."
            captured != 0 && captured != PAWN -> " et capturent votre ${PIECE_NAMES_FR[captured].lowercase()}."
            captured != 0 -> " et gagnent un pion."
            score != null && score > 150 -> " et prennent l'avantage."
            score != null && score > -80 -> " et égalisent : votre avantage s'est envolé."
            else -> " et se défendent : il existait un coup bien plus fort."
        }
        return RefutationReply(reply, after2, "Réfutation : après $san, les $opp répondent ${after.san(reply)}$detail")
    }
}
