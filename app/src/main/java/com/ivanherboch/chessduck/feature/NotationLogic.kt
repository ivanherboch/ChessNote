package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.ivanherboch.chessduck.data.PuzzleRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import com.ivanherboch.chessduck.chess.PieceLexiconFr
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.ivanherboch.chessduck.chess.BISHOP
import com.ivanherboch.chessduck.chess.ROOK
import com.ivanherboch.chessduck.chess.QUEEN
import com.ivanherboch.chessduck.util.runCatchingSuspend
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.InfoPill
import com.ivanherboch.chessduck.ui.LoadingState
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.ChessDuckApp
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.PIECE_NAMES_EN
import com.ivanherboch.chessduck.chess.PIECE_NAMES_FR
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.pieceLetter
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.sqName
import com.ivanherboch.chessduck.data.DuckDatabase
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.AutoText
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.SectionTitle
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.moveAnim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Un exercice de notation : position légale réelle + coup légal à transcrire en SAN. */
@Immutable
data class NotationExercise(val before: Position, val move: Move, val san: String, val level: Int)

object NotationLogic {
    val LEVELS = listOf("Tous", "1. Bases", "2. Prises & Échecs", "3. Roques & Avancé")

    /** Niveau pédagogique d'un coup : 1 = bases, 2 = prises & échecs, 3 = roques & avancé. */
    fun levelOf(p: Position, m: Move, san: String): Int {
        val body = san.trimEnd('+', '#')
        val disamb = pieceType(p[m.from]) != PAWN && !p.isCastle(m) && body.replace("x", "").length > 3
        return when {
            p.isCastle(m) || m.promo != 0 || san.endsWith("#") || p.isEnPassant(m) || disamb -> 3
            p.isCapture(m) || san.endsWith("+") -> 2
            else -> 1
        }
    }

    /** Choisit dans [positions] un coup légal du niveau demandé (0 = n'importe lequel). */
    fun pick(positions: List<Position>, level: Int, rnd: Random, wantKind: Int = 0): NotationExercise? {
        val pool = ArrayList<NotationExercise>()
        for (p in positions.shuffled(rnd)) {
            if (p.legalMoves.isEmpty()) continue
            for (m in p.legalMoves) {
                val san = p.san(m)
                val lv = levelOf(p, m, san)
                if (level != 0 && lv != level) continue
                if (wantKind != 0 && !isKind(p, m, san, wantKind)) continue
                pool.add(NotationExercise(p, m, san, lv))
            }
            if (pool.size >= 12) break
        }
        if (pool.isEmpty()) return null
        // Varie les types de pièces plutôt que de favoriser celles qui ont le plus de coups.
        val byType = pool.groupBy { pieceType(it.before[it.move.from]) }
        return byType.values.random(rnd).random(rnd)
    }

    /** Sous-familles du niveau 3 : 1 roque, 2 promotion, 3 mat, 4 désambiguïsation. */
    fun isKind(p: Position, m: Move, san: String, kind: Int): Boolean = when (kind) {
        1 -> p.isCastle(m)
        2 -> m.promo != 0
        3 -> san.endsWith("#")
        4 -> pieceType(p[m.from]) != PAWN && !p.isCastle(m) && san.trimEnd('+', '#').replace("x", "").length > 3
        else -> true
    }

    private fun nameOf(type: Int) = "${PieceLexiconFr.title(type)} (${PIECE_NAMES_EN[type]})"

    /** Diagnostic pédagogique d'une réponse fausse, sans donner la solution. */
    fun diagnose(input: String, ex: NotationExercise): String {
        val san = ex.san
        val p = ex.before
        val m = ex.move
        val type = pieceType(p[m.from])
        val castle = p.isCastle(m)
        if (input.isEmpty()) return "Écrivez d'abord le coup avec le clavier ci-dessous."
        if (input.startsWith("P")) return "Les pions n'ont pas de lettre : on écrit « e4 », jamais « Pe4 »."
        if (castle) {
            return if (input.startsWith("O-O")) "Attention au côté : O-O = petit roque (côté roi), O-O-O = grand roque (côté dame)."
            else "Le roi se déplace de deux cases : c'est un roque. Il se note O-O (petit) ou O-O-O (grand)."
        }
        if (input.startsWith("O")) return "Ce coup n'est pas un roque : commencez par la lettre de la pièce (rien pour un pion)."
        val inBody = input.trimEnd('+', '#')
        val sanBody = san.trimEnd('+', '#')
        val inSuffix = input.substring(inBody.length)
        val sanSuffix = san.substring(sanBody.length)
        if (inBody == sanBody) {
            return when {
                sanSuffix == "#" && inSuffix == "+" -> "C'est plus qu'un échec : le roi n'a aucune parade. L'échec et mat se note « # »."
                sanSuffix == "#" -> "Ce coup donne échec et mat : terminez par « # »."
                sanSuffix == "+" && inSuffix == "#" -> "Le roi peut encore parer : c'est un simple échec, noté « + »."
                sanSuffix == "+" -> "Ce coup donne échec au roi : ajoutez « + » à la fin."
                else -> "Ce coup ne donne pas échec : retirez le « $inSuffix » final."
            }
        }
        val first = input[0]
        if (type == KNIGHT && first == 'K') return "N = Knight (Cavalier). La lettre K est réservée au King (Roi)."
        if (type == KING && first == 'N') return "K = King (Roi). La lettre N désigne le Knight (Cavalier)."
        if (type == PAWN && first.isUpperCase()) return "C'est un pion qui joue : aucune lettre de pièce, on commence par la colonne."
        if (type != PAWN && first != pieceLetter(type)) {
            return "La pièce jouée est un(e) ${nameOf(type)} : sa lettre est « ${pieceLetter(type)} »."
        }
        val capture = p.isCapture(m)
        if (capture && 'x' !in inBody) {
            return if (type == PAWN) "Le pion capture : colonne de départ, puis « x », puis la case d'arrivée (comme « exd5 »)."
            else "C'est une prise : ajoutez « x » juste avant la case d'arrivée."
        }
        if (!capture && 'x' in inBody) return "Aucune pièce n'est capturée : pas de « x » dans ce coup."
        if (m.promo != 0) {
            if ('=' !in inBody) return "Le pion atteint la dernière rangée : indiquez la promotion avec « = » et la pièce (ex. « =Q »)."
            if (inBody.substringAfter('=') != sanBody.substringAfter('=')) return "Regardez bien la pièce apparue : la lettre après « = » n'est pas la bonne."
        } else if ('=' in inBody) return "Pas de promotion ici : le « = » ne sert que lorsqu'un pion atteint la dernière rangée."
        val dest = sqName(m.to)
        if (dest !in inBody) return "La case d'arrivée est inexacte : repérez la colonne (a–h) puis la rangée (1–8) de la case où la pièce se pose."
        if (type == PAWN && capture && inBody[0] != sanBody[0]) return "Pour une prise de pion, on commence par la colonne de départ du pion."
        if (type != PAWN) {
            val inCore = inBody.replace("x", "")
            val sanCore = sanBody.replace("x", "")
            if (sanCore.length > 3 && inCore.length == 3) {
                return "Deux ${PIECE_NAMES_FR[type].lowercase()}s peuvent aller sur $dest : précisez la colonne (ou la rangée) de départ, comme dans « Rad1 »."
            }
            if (sanCore.length == 3 && inCore.length > 3) return "Une seule pièce peut jouer ce coup : inutile de préciser sa case de départ."
            if (sanCore.length > 3) return "La précision de départ est inexacte : indiquez la colonne de la pièce qui joue (ou sa rangée si les colonnes sont identiques)."
        }
        return "Presque ! Ordre à respecter : pièce, (départ), (x), case d'arrivée, (=promotion), (+ ou #)."
    }

    fun hint(ex: NotationExercise, stage: Int): String {
        val p = ex.before
        val m = ex.move
        val type = pieceType(p[m.from])
        if (stage >= 2) return "Solution : ${ex.san} — recopiez-la pour la mémoriser."
        val sb = StringBuilder()
        when {
            p.isCastle(m) -> sb.append("Le roi et la tour bougent ensemble : c'est un roque (O-O ou O-O-O).")
            type == PAWN -> sb.append("Un pion joue : pas de lettre de pièce.")
            else -> sb.append("Pièce : ${nameOf(type)} → lettre « ${pieceLetter(type)} ».")
        }
        if (p.isCapture(m)) sb.append(" C'est une prise (x).")
        if (m.promo != 0) sb.append(" Il y a promotion (=).")
        if (ex.san.endsWith("#")) sb.append(" Le coup fait mat (#).") else if (ex.san.endsWith("+")) sb.append(" Le coup donne échec (+).")
        return sb.toString()
    }

    /** Exercices de secours si la base n'est pas disponible. */
    val FALLBACK = listOf(
        "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
        "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 6 5",
        "r3k2r/ppp2ppp/2n1bn2/3qp3/8/2NP1N2/PPP1BPPP/R2Q1RK1 b kq - 0 9",
        "8/4P1k1/8/8/8/8/1p4K1/8 w - - 0 1",
        "3r2k1/5ppp/8/8/8/8/5PPP/R2R2K1 w - - 0 1",
    )
}
