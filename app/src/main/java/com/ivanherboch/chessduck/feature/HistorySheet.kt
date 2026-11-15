package com.ivanherboch.chessduck.feature

import androidx.compose.foundation.lazy.items
import com.ivanherboch.chessduck.ui.DuckRadius
import com.ivanherboch.chessduck.ui.DuckLazySheet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import com.ivanherboch.chessduck.ui.icons.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.ui.ConfirmDialog
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.EmptyState
import com.ivanherboch.chessduck.ui.LocalDuck

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
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (label, col) = when {
                        !g.vsComputer -> (if (g.outcome == 1) "Victoire de ${g.whiteName.ifEmpty { "Blancs" }}" else if (g.outcome == -1) "Victoire de ${g.blackName.ifEmpty { "Noirs" }}" else "Nulle") to c.goldText
                        g.outcome == 1 -> "Victoire" to c.greenText
                        g.outcome == -1 -> "Défaite" to c.redText
                        else -> "Nulle" to c.goldText
                    }
                    Text(label, color = col, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
