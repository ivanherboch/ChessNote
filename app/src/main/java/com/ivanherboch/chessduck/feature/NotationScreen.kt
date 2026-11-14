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

private val LEVEL_SHORT = listOf("Tous", "Bases", "Prises", "Roques")

@Composable
fun NotationScreen(vm: NotationViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val ex = s.exercise
    if (ex == null) {
        LoadingState("Préparation de l'exercice…")
        return
    }
    ModuleLayout(
        bottomMin = 320.dp,
        top = {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                LevelMenu(s.level, vm::setLevel, Modifier.weight(1f))
                DuckIconButton(Icons.Rounded.SwapVert, "Retourner l'échiquier", vm::flip)
                DuckIconButton(Icons.Rounded.Replay, "Revoir le coup", vm::replay)
                DuckIconButton(
                    Icons.Rounded.Lightbulb, "Indice", vm::hint, enabled = !s.solved, accent = c.green,
                    badge = if (s.hintStage > 0) "${s.hintStage}/2" else null,
                )
                DuckIconButton(Icons.Rounded.SkipNext, "Exercice suivant", vm::next, accent = c.gold, filled = s.solved)
            }
        },
        board = {
            val pos = if (s.shown) ex.before.play(ex.move) else ex.before
            ChessBoard(
                position = pos,
                modifier = Modifier.fillMaxSize(),
                flipped = s.flipped,
                lastMove = if (s.shown) ex.move else null,
                anim = s.anim,
                arrows = if (s.shown) listOf(Arrow(ex.move.from, ex.move.to, c.blue)) else emptyList(),
            )
        },
        underBoard = {
            // Orientation de l'échiquier et série en cours : toujours présents, la mise en page ne saute pas.
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoPill(
                    if (s.flipped) "Vue Noirs" else "Vue Blancs",
                    icon = Icons.Rounded.SwapVert,
                    description = if (s.flipped) "Échiquier vu du côté des Noirs : les colonnes vont de h à a" else "Échiquier vu du côté des Blancs",
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(0.01f))
                InfoPill(
                    "Série ${s.streak} · record ${s.best}", icon = Icons.Rounded.LocalFireDepartment, accent = if (s.streak > 0) c.gold else null,
                    description = "Série en cours : ${s.streak} bonne${if (s.streak > 1) "s" else ""} réponse${if (s.streak > 1) "s" else ""}. Record : ${s.best}",
                )
            }
        },
        bottom = {
            FeedbackCard(s.message, s.tone, Modifier.padding(top = 6.dp).weight(1f))
            // Barre de saisie : « Tout effacer » à gauche, ⌫ (appui long = tout effacer) et Valider à droite.
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(52.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(c.surface)
                        .border(1.dp, if (s.solved) c.greenText else c.outlineStrong, RoundedCornerShape(12.dp))
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (s.input.isEmpty()) "Votre réponse : vide" else "Votre réponse : ${s.input.toList().joinToString(" ")}"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (s.input.isNotEmpty() && !s.solved) {
                        DuckIconButton(Icons.Rounded.Close, "Tout effacer", vm::clear)
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        s.input.ifEmpty { "Votre réponse…" },
                        color = if (s.input.isEmpty()) c.muted else if (s.solved) c.greenText else c.text,
                        style = if (s.input.isEmpty()) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                    )
                }
                BackspaceKey(enabled = !s.solved && s.input.isNotEmpty(), onTap = vm::backspace, onLong = vm::clear)
                if (s.solved) DuckButton("Suivant", vm::next, Modifier.width(112.dp), accent = c.green, filled = true, height = 52.dp, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                else DuckButton("Valider", { if (vm.validate()) fb?.success() else fb?.error() }, Modifier.width(112.dp), enabled = s.input.isNotEmpty(), accent = c.gold, filled = true, height = 52.dp, icon = Icons.Rounded.Check)
            }
            // Clavier virtuel FIDE (aucun clavier système), désactivé une fois la réponse validée.
            val type: (String) -> Unit = { k -> if (!vm.type(k)) fb?.error() }
            val on = !s.solved
            Column(Modifier.padding(top = 6.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((pt, sub) in listOf(KING to "King", QUEEN to "Queen", ROOK to "Rook", BISHOP to "Bishop", KNIGHT to "Knight")) {
                        val k = pieceLetter(pt).toString()
                        Key(k, sub, Modifier.weight(1f), on, "$k, ${sub}, ${PIECE_NAMES_FR[pt]}") { type(k) }
                    }
                }
                Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (ch in 'a'..'h') Key(ch.toString(), null, Modifier.weight(1f), on, "colonne $ch") { type(ch.toString()) }
                }
                Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (ch in '1'..'8') Key(ch.toString(), null, Modifier.weight(1f), on, "rangée $ch") { type(ch.toString()) }
                }
                Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((k, d) in listOf("x" to "x, prise", "+" to "plus, échec", "#" to "dièse, échec et mat", "=" to "égal, promotion")) {
                        Key(k, null, Modifier.weight(1f), on, d) { type(k) }
                    }
                    Key("O-O", null, Modifier.weight(1.4f), on, "petit roque") { type("O-O") }
                    Key("O-O-O", null, Modifier.weight(1.8f), on, "grand roque") { type("O-O-O") }
                }
            }
        },
    )
}

/** Sélecteur de niveau compact (menu déroulant). */
@Composable
private fun LevelMenu(level: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val c = LocalDuck.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        DuckButton(
            "Niveau : ${LEVEL_SHORT[level]}", { open = true }, Modifier.widthIn(max = 180.dp), icon = Icons.Rounded.Tune,
            contentDescription = "Niveau d'exercice : ${NotationLogic.LEVELS[level]}. Toucher pour changer",
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
            NotationLogic.LEVELS.forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = { Text(if (i == 0) "Tous les niveaux" else label, style = MaterialTheme.typography.bodyMedium, color = c.text) },
                    onClick = { open = false; onSelect(i) },
                    leadingIcon = { if (i == level) Icon(Icons.Rounded.Check, contentDescription = "sélectionné", tint = c.goldText) else Spacer(Modifier.size(24.dp)) },
                )
            }
        }
    }
}

/** Touche ⌫ : un appui efface un caractère, un appui long efface toute la saisie. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackspaceKey(enabled: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) c.surfaceHi else c.surfaceSubtle)
            .combinedClickable(
                enabled = enabled, role = Role.Button, onClickLabel = "Effacer un caractère", onLongClickLabel = "Tout effacer",
                onLongClick = { fb?.error(); onLong() },
            ) { fb?.key(); onTap() }
            .semantics { contentDescription = "Effacer" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null, tint = if (enabled) c.text else c.muted)
    }
}

@Composable
private fun Key(label: String, sub: String?, modifier: Modifier, enabled: Boolean, description: String, onClick: () -> Unit) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(if (enabled) c.surfaceHi else c.surfaceSubtle)
            .clickable(enabled = enabled, role = Role.Button) { fb?.key(); onClick() }
            .clearAndSetSemantics { contentDescription = description; if (!enabled) disabled() },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = if (enabled) c.text else c.muted, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        if (sub != null) Text(sub, color = c.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
