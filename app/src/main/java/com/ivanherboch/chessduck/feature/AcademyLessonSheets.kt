package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.ui.moveLabel
import com.ivanherboch.chessduck.content.PuzzleThemes
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import com.ivanherboch.chessduck.chess.Position
import androidx.compose.foundation.layout.heightIn
import com.ivanherboch.chessduck.ui.SideDot
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import com.ivanherboch.chessduck.ui.icons.ExpandMore
import androidx.compose.ui.semantics.semantics
import com.ivanherboch.chessduck.ui.DuckRadius
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.ui.ButtonVariant
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.SectionTitle
import com.ivanherboch.chessduck.ui.icons.Extension
import com.ivanherboch.chessduck.ui.icons.SportsEsports
import com.ivanherboch.chessduck.ui.turnOf

// Fiche de la leçon, choix du camp, variantes et passerelles de fin de leçon.

/** Libellé d'une ligne d'ouverture : « Ligne principale » ou l'écart de l'adversaire (« Si 3… Nf6 »). */
internal fun variantLabel(l: Lesson, v: Int): String {
    if (v == 0) return "Ligne principale"
    val vv = l.variants[v - 1]
    val line = l.lineFor(v)
    val turn = line.turnOf(vv.from, first = 1)
    val first = line.sans[vv.from]
    return "Si " + if (line.positions[vv.from].whiteToMove) "$turn. $first" else "$turn… $first"
}

/**
 * Choix de la ligne d'une ouverture, en une puce à menu placée à côté des modes Guidé/Défi : aucune rangée
 * supplémentaire sous l'échiquier.
 */
@Composable
internal fun VariantMenu(l: Lesson, selected: Int, onSelect: (Int) -> Unit, done: Set<String> = emptySet()) {
    val c = LocalDuck.current
    var open by remember { mutableStateOf(false) }
    Box {
        DuckChip(
            if (selected == 0) "Variantes" else "Variante $selected", selected != 0, { open = true },
            Modifier.semantics { contentDescription = "Ligne affichée : ${variantLabel(l, selected)}. Choisir une autre ligne" },
            accent = c.blue, icon = Icons.Rounded.ExpandMore,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
            for (v in 0..l.variants.size) {
                DropdownMenuItem(
                    text = { Text(variantLabel(l, v), style = MaterialTheme.typography.bodyMedium, color = c.text) },
                    trailingIcon = if (v > 0 && "${l.id}:$v" in done) {
                        { Icon(Icons.Rounded.CheckCircle, contentDescription = "réussie en pratique", tint = c.greenText) }
                    } else null,
                    onClick = { open = false; onSelect(v) },
                    leadingIcon = { if (v == selected) Icon(Icons.Rounded.Check, contentDescription = "sélectionnée", tint = c.blueText) else Spacer(Modifier.size(24.dp)) },
                )
            }
        }
    }
}

/** Choix du camp avant d'ouvrir une position de partie modèle dans le Jeu Libre. */
@Composable
internal fun ChooseColorSheet(fen: String, l: Lesson, onPick: (Boolean) -> Unit, onClose: () -> Unit) {
    val c = LocalDuck.current
    val whiteToMove = Position.fromFen(fen).whiteToMove
    DuckSheet("Jouer cette position", onClose) {
        Text(
            "Trait aux ${if (whiteToMove) "Blancs" else "Noirs"} · le maître jouait les ${if (l.heroWhite) "Blancs" else "Noirs"}.",
            color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp),
        )
        for (white in listOf(true, false)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(DuckRadius.M))
                    .border(1.dp, c.outlineStrong, RoundedCornerShape(DuckRadius.M))
                    .clickable(role = Role.Button) { onPick(white) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SideDot(white)
                Text(
                    "Jouer avec les ${if (white) "Blancs" else "Noirs"}", color = c.text, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

/**
 * Fiche de la leçon dans un tiroir, pour toutes les leçons : objectif, règle, thème tactique, moments clés (coups des arrêts
 * « À vous »), et, quand la leçon en a, conditions, idées et pièges. L'échiquier garde toute sa taille.
 */
@Composable
internal fun LessonBriefSheet(l: Lesson, onClose: () -> Unit) {
    val c = LocalDuck.current
    DuckSheet("Fiche de la leçon", onClose) {
        Text(l.title, color = c.text, style = MaterialTheme.typography.titleMedium)
        SectionTitle("Objectif")
        Text(l.summary, color = c.text, style = MaterialTheme.typography.bodyMedium)
        SectionTitle("À retenir")
        Text(l.takeaway, color = c.text, style = MaterialTheme.typography.bodyMedium)
        // Thème tactique de la leçon : son but et le conseil des Problèmes.
        PuzzleThemes.byKey[l.theme]?.let { t ->
            SectionTitle("Thème : ${t.name}")
            Text(t.goal + if (t.tip.isNotEmpty()) " ${t.tip}" else "", color = c.text, style = MaterialTheme.typography.bodyMedium)
        }
        // Moments clés : les coups à trouver aux arrêts « À vous », avec leur commentaire.
        val key = l.pauseSteps.filter { it < l.line.size }.sorted()
        if (key.isNotEmpty()) {
            SectionTitle("Les moments clés")
            for (i in key) {
                val label = moveLabel(l.line.turnOf(i, first = 1), l.line.positions[i].whiteToMove, l.line.sans[i])
                Text("• $label : ${l.texts[i]}", color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        if (l.conditions.isNotEmpty()) {
            SectionTitle("Conditions")
            Text(l.conditions, color = c.text, style = MaterialTheme.typography.bodyMedium)
        }
        if (l.ideas.isNotEmpty()) {
            SectionTitle("Les idées")
            for (t in l.ideas) Text("• $t", color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (l.traps.isNotEmpty()) {
            SectionTitle("Les pièges")
            for (t in l.traps) Text("• $t", color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

/** Leçon « L'échiquier et la notation » : sa passerelle mène à la Vision des cases. */
internal const val NOTATION_LESSON = 115

/** Passerelles de fin de leçon : thème dans Problèmes, position dans le Jeu Libre (une seule rangée, sous le commentaire). */
@Composable
internal fun LessonBridges(
    l: Lesson, endFen: String?, onTrainTheme: (String, Int) -> Unit, onPlayPosition: (String, Boolean) -> Unit, onVision: () -> Unit = {},
    /** « S'entraîner » (onglet Problèmes) : masqué quand Pratiquer est déjà une série de problèmes du thème. */
    showTrain: Boolean = true,
) {
    val c = LocalDuck.current
    val vision = l.id == NOTATION_LESSON
    // Position finale de mat ou de pat : rien à jouer, le bouton n'apparaît pas (plutôt que désactivé sans raison).
    val train = showTrain && l.theme.isNotEmpty()
    if (!train && endFen == null && !vision) return
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // « L'échiquier et la notation » : on s'entraîne aussitôt à repérer les cases (onglet Notation).
        if (vision) DuckButton(
            "Vision des cases", onVision, Modifier.weight(1f), accent = c.blue, variant = ButtonVariant.OUTLINED,
            icon = Icons.Rounded.Extension, contentDescription = "S'entraîner à trouver les cases : Vision des cases, dans l'onglet Notation",
        )
        if (train) DuckButton(
            "S'entraîner", { onTrainTheme(l.theme, l.level) }, Modifier.weight(1f), accent = c.blue, variant = ButtonVariant.OUTLINED,
            icon = Icons.Rounded.Extension, contentDescription = "S'entraîner sur ce thème dans l'onglet Problèmes",
        )
        if (endFen != null) DuckButton(
            "Jouer cette position", { onPlayPosition(endFen, l.heroWhite) }, Modifier.weight(1f),
            accent = c.blue, variant = ButtonVariant.OUTLINED, icon = Icons.Rounded.SportsEsports,
            contentDescription = "Jouer cette position contre l'Ordinateur dans le Jeu Libre",
        )
    }
}
