package com.ivanherboch.chessduck.feature

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.chess.BISHOP
import com.ivanherboch.chessduck.chess.GameEnd
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.QUEEN
import com.ivanherboch.chessduck.chess.ROOK
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.ui.AutoText
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.KID_PIECE_RES
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.captureBadge
import com.ivanherboch.chessduck.ui.moveAnim
import com.ivanherboch.chessduck.ui.undoAnim
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class KidState(
    val line: Line = Line(START_FEN),
    val cursor: Int = 0,
    val anim: BoardAnim? = null,
    /** Message de fin de partie (null tant que la partie continue). */
    val result: String? = null,
) {
    val position: Position get() = line.positions[cursor.coerceIn(0, line.size)]
}

/**
 * Mode enfant : une partie à 2 joueurs sur le même écran, sans ordinateur,
 * sans évaluation et sans enregistrement dans l'historique.
 */
class KidViewModel : ViewModel() {
    val state = MutableStateFlow(KidState())

    fun reset() = state.update { KidState() }

    fun move(m: Move) {
        val s = state.value
        if (s.result != null || !s.position.isLegal(m)) return
        val nl = s.line.copy(s.cursor)
        nl.push(m)
        val white = !nl.last.whiteToMove
        val result = when (nl.end()) {
            GameEnd.CHECKMATE -> "🎉 Échec et mat ! Bravo aux ${if (white) "Blancs" else "Noirs"} !"
            GameEnd.NONE -> null
            else -> "🤝 Partie nulle : personne n'a perdu !"
        }
        state.update { it.copy(line = nl, cursor = nl.size, anim = moveAnim(s.position, m), result = result) }
    }

    fun back() {
        val s = state.value
        if (s.cursor <= 0) return
        val t = s.cursor - 1
        state.update { it.copy(cursor = t, anim = undoAnim(s.line.positions[t], s.line.moves[t]), result = null) }
    }

    fun forward() {
        val s = state.value
        if (s.cursor >= s.line.size) return
        state.update { it.copy(cursor = s.cursor + 1, anim = moveAnim(s.position, s.line.moves[s.cursor])) }
    }

    fun resign() {
        val s = state.value
        if (s.result != null) return
        val loser = if (s.position.whiteToMove) "Blancs" else "Noirs"
        val winner = if (s.position.whiteToMove) "Noirs" else "Blancs"
        state.update { it.copy(result = "🏳️ Les $loser abandonnent : bravo aux $winner !") }
    }
}

private val KID_LEGEND = listOf(
    KING to "Roi", QUEEN to "Dame", ROOK to "Tour", BISHOP to "Fou", KNIGHT to "Girafe", PAWN to "Pion",
)

@Composable
fun KidScreen(vm: KidViewModel, onQuit: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    val pos = s.position
    val cur = s.cursor
    val last = if (cur > 0) s.line.moves[cur - 1] else null
    val whiteTurn = pos.whiteToMove
    ModuleLayout(
        bottomMin = 176.dp,
        top = {
            val text = s.result ?: ((if (pos.inCheck) "Échec ! " else "") + if (whiteTurn) "⬜ Aux Blancs de jouer" else "⬛ Aux Noirs de jouer")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (s.result != null) c.gold else c.surface).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                AutoText(text, color = if (s.result != null) c.onAccent else c.text, maxSize = 22.sp, weight = FontWeight.Bold, align = TextAlign.Center)
            }
        },
        board = {
            ChessBoard(
                position = pos,
                modifier = Modifier.fillMaxSize(),
                interactive = s.result == null,
                lastMove = last,
                anim = s.anim,
                badges = if (last != null) listOfNotNull(captureBadge(s.line.positions[cur - 1], last)) else emptyList(),
                kid = true,
                onMove = vm::move,
            )
        },
        bottom = {
            // Rappel des pièces et de leur nom
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).weight(1f).clip(RoundedCornerShape(14.dp)).background(c.surface).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for ((type, name) in KID_LEGEND) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Image(painterResource(KID_PIECE_RES.getValue(type)), name, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(c.squareDark))
                        AutoText(name, maxSize = 15.sp, weight = FontWeight.Bold, align = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            EqualRow(Modifier.padding(top = 6.dp), gap = 6.dp) {
                DuckButton("◀", vm::back, Modifier.weight(1f), enabled = cur > 0, height = 52.dp, maxSize = 24.sp)
                DuckButton("▶", vm::forward, Modifier.weight(1f), enabled = cur < s.line.size, height = 52.dp, maxSize = 24.sp)
                if (s.result == null) DuckButton("🏳️ Abandonner", vm::resign, Modifier.weight(1.7f), enabled = s.line.size > 0, accent = c.red, height = 52.dp, maxSize = 16.sp)
                else DuckButton("🆕 Rejouer", vm::reset, Modifier.weight(1.7f), accent = c.green, filled = true, height = 52.dp, maxSize = 17.sp)
            }
            DuckButton("✕ Quitter le mode enfant", onQuit, Modifier.fillMaxWidth().padding(vertical = 6.dp), height = 40.dp, maxSize = 14.sp)
        },
    )
}
