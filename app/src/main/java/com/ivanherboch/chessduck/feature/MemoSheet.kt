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
import com.ivanherboch.chessduck.ui.icons.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import com.ivanherboch.chessduck.ui.icons.LocalFireDepartment
import com.ivanherboch.chessduck.ui.icons.Lightbulb
import com.ivanherboch.chessduck.ui.icons.Replay
import com.ivanherboch.chessduck.ui.icons.SkipNext
import com.ivanherboch.chessduck.ui.icons.SwapVert
import com.ivanherboch.chessduck.ui.icons.Tune
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

/** Fiche « Mémo Anglais » : lettres FIDE et symboles. */
@Composable
fun MemoSheet(onClose: () -> Unit) {
    val c = LocalDuck.current
    DuckSheet("Mémo anglais — notation FIDE", onClose) {
        SectionTitle("Les pièces")
        val pieces = listOf(
            Triple("K", "King", "Roi"), Triple("Q", "Queen", "Dame"), Triple("R", "Rook", "Tour"),
            Triple("B", "Bishop", "Fou"), Triple("N", "Knight", "Cavalier"), Triple("—", "Pawn", "Pion : aucune lettre"),
        )
        for ((l, en, fr) in pieces) MemoRow(l, "$en  →  $fr")
        SectionTitle("Les symboles")
        MemoRow("x", "Prise (capture) : Bxf7, exd5")
        MemoRow("+", "Échec au roi : Qh5+")
        MemoRow("#", "Échec et mat : Qxf7#")
        MemoRow("=", "Promotion du pion : e8=Q")
        MemoRow("O-O", "Petit roque (côté roi)")
        MemoRow("O-O-O", "Grand roque (côté dame)")
        SectionTitle("Les règles d'or")
        for (t in listOf(
            "Un coup = lettre de la pièce + case d'arrivée : Nf3.",
            "Un pion ne prend jamais de lettre : e4. S'il capture, on indique sa colonne de départ : exd5.",
            "N comme kNight : le K est réservé au roi (King).",
            "Si deux pièces identiques peuvent aller sur la même case, on précise la colonne de départ (Rad1), sinon la rangée (N5f3).",
            "L'ordre est toujours : pièce, départ éventuel, x, case d'arrivée, =promotion, + ou #.",
        )) {
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("•  ", color = c.goldText, style = MaterialTheme.typography.bodyMedium)
                Text(t, color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MemoRow(key: String, text: String) {
    val c = LocalDuck.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.height(36.dp).fillMaxWidth(0.22f).clip(RoundedCornerShape(8.dp)).background(c.surfaceHi), contentAlignment = Alignment.Center) {
            Text(key, color = c.goldText, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Text(text, color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
    }
}
