package com.ivanherboch.chessduck.feature

import androidx.compose.material3.SwitchDefaults
import com.ivanherboch.chessduck.ui.icons.Shield
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import com.ivanherboch.chessduck.ui.icons.People
import androidx.compose.material.icons.rounded.PlayArrow
import com.ivanherboch.chessduck.ui.icons.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.SectionTitle

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameSettingsSheet(
    twoPlayers: Boolean, color: Int, aiElo: Int,
    /** Filet de sécurité : appliqué tout de suite (partie en cours comprise) et mémorisé. */
    safetyNet: Boolean, onSafetyNet: (Boolean) -> Unit,
    onClose: () -> Unit, onApply: (Boolean, Int, Int) -> Unit,
) {
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
            // Filet de sécurité : interrupteur appliqué aussitôt, sans relancer la partie.
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp)
                    .toggleable(value = safetyNet, role = Role.Switch, onValueChange = onSafetyNet),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = if (safetyNet) c.greenText else c.muted, modifier = Modifier.size(24.dp))
                Column(Modifier.padding(horizontal = 10.dp).weight(1f)) {
                    Text("Filet de sécurité", color = c.text, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "L'Ordinateur arrête une gaffe nette avant qu'elle soit jouée et vous dit pourquoi. La partie ne compte alors plus pour l'ELO.",
                        color = c.muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = safetyNet, onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.goldRole.onFill, checkedTrackColor = c.goldRole.fill,
                        uncheckedThumbColor = c.outlineStrong, uncheckedTrackColor = c.surfaceHi, uncheckedBorderColor = c.outlineStrong,
                    ),
                )
            }
        }
        DuckButton("Lancer une nouvelle partie", { onApply(two, col, elo.toInt()) }, Modifier.fillMaxWidth().padding(top = 16.dp), accent = c.gold, filled = true, icon = Icons.Rounded.PlayArrow)
        Text("Le pseudo, le thème et les confirmations se règlent dans Paramètres, en haut à droite.", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}
