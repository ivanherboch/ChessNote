package com.ivanherboch.chessduck.feature

import androidx.compose.foundation.lazy.items
import com.ivanherboch.chessduck.ui.DuckLazySheet
import com.ivanherboch.chessduck.ui.LocalIsTwoPane
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.Badge
import com.ivanherboch.chessduck.ui.BoardRow
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.ConfirmDialog
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.EmptyState
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.MoveStrip
import com.ivanherboch.chessduck.ui.PlayerBar
import com.ivanherboch.chessduck.ui.SectionTitle
import com.ivanherboch.chessduck.ui.Tone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HistorySheet(games: List<GameRecord>, onClose: () -> Unit, onDelete: (Long) -> Unit, onReplay: (GameRecord) -> Unit) {
    val c = LocalDuck.current
    var pendingDelete by remember { mutableStateOf<GameRecord?>(null) }
    DuckLazySheet("Historique des parties", onClose) {
        if (games.isEmpty()) item(key = "vide") {
            EmptyState(
                Icons.Rounded.History, "Aucune partie pour l'instant",
                "Terminez une partie en Jeu Libre : elle apparaîtra ici avec sa revue commentée.",
                actionLabel = "Fermer", onAction = onClose,
            )
        }
        items(games, key = { it.id }) { g ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(c.surface).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (label, col) = when {
                        !g.vsComputer -> (if (g.outcome == 1) "Victoire de ${g.whiteName.ifEmpty { "Blancs" }}" else if (g.outcome == -1) "Victoire de ${g.blackName.ifEmpty { "Noirs" }}" else "Nulle") to c.goldText
                        g.outcome == 1 -> "Victoire" to c.greenText
                        g.outcome == -1 -> "Défaite" to c.redText
                        else -> "Nulle" to c.goldText
                    }
                    Text(label, color = col, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (g.vsComputer) {
                        val d = g.eloAfter - g.eloBefore
                        Text("ELO ${g.eloAfter} (${if (d >= 0) "+$d" else "−${-d}"})", color = if (d >= 0) c.greenText else c.redText, style = MaterialTheme.typography.labelLarge)
                    }
                    DuckIconButton(Icons.Rounded.Delete, "Supprimer cette partie", { pendingDelete = g })
                }
                val nbMoves = (g.moves.count { it == ' ' } + 2) / 2
                val players = when {
                    g.whiteName.isEmpty() -> "contre ${g.opponent.replace("Stockfish", "Ordinateur")}" + if (g.vsComputer) " · avec les ${if (g.playerWhite) "Blancs" else "Noirs"}" else ""
                    g.vsComputer -> {
                        val me = "(${g.eloBefore})"
                        val ai = "(${g.opponentElo})"
                        "Blancs : ${g.whiteName} ${if (g.playerWhite) me else ai}  –  Noirs : ${g.blackName} ${if (g.playerWhite) ai else me}"
                    }
                    else -> "Blancs : ${g.whiteName}  –  Noirs : ${g.blackName} · hors ELO"
                }
                Text(players, color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                Text(
                    "${formatDate(g.date)} · ${g.result} par ${g.reason} · $nbMoves coups",
                    color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp),
                )
                DuckButton("Rejouer sur l’échiquier", { onReplay(g) }, Modifier.fillMaxWidth().padding(end = 8.dp), accent = c.blue, filled = true, icon = Icons.Rounded.PlayArrow)
            }
        }
    }
    pendingDelete?.let { g ->
        ConfirmDialog(
            "Supprimer cette partie ?", "Elle disparaîtra définitivement de l'historique (${formatDate(g.date)}).",
            "Supprimer", { pendingDelete = null; onDelete(g.id) }, { pendingDelete = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameSettingsSheet(twoPlayers: Boolean, color: Int, aiElo: Int, onClose: () -> Unit, onApply: (Boolean, Int, Int) -> Unit) {
    val c = LocalDuck.current
    var two by remember { mutableStateOf(twoPlayers) }
    var col by remember { mutableIntStateOf(color) }
    var elo by remember { mutableFloatStateOf(aiElo.toFloat()) }
    DuckSheet("Paramètres de partie", onClose) {
        SectionTitle("Mode de jeu")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DuckChip("Contre l'ordinateur", !two, { two = false }, icon = Icons.Rounded.SmartToy)
            DuckChip("2 joueurs (Pass & Play)", two, { two = true }, icon = Icons.Rounded.People)
        }
        if (!two) {
            SectionTitle("Votre couleur")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DuckChip("Blancs", col == 0, { col = 0 })
                DuckChip("Aléatoire", col == 1, { col = 1 })
                DuckChip("Noirs", col == 2, { col = 2 })
            }
            SectionTitle("Niveau de l'ordinateur : ${elo.toInt()} ELO")
            Slider(
                value = elo, onValueChange = { elo = (it / 50).toInt() * 50f }, valueRange = 600f..2400f, steps = 35,
                colors = SliderDefaults.colors(thumbColor = c.goldRole.fill, activeTrackColor = c.goldRole.fill, inactiveTrackColor = c.surfaceHi, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                modifier = Modifier.semantics { contentDescription = "Niveau de l'ordinateur : ${elo.toInt()} ELO" },
            )
            Row(Modifier.fillMaxWidth()) {
                Text("600 · débutant", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("2400 · maître", color = c.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
        DuckButton("Lancer une nouvelle partie", { onApply(two, col, elo.toInt()) }, Modifier.fillMaxWidth().padding(top = 16.dp), accent = c.gold, filled = true, icon = Icons.Rounded.PlayArrow)
        Text("Le pseudo, le thème et les confirmations se règlent dans Paramètres, en haut à droite.", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}
