package com.ivanherboch.chessduck.feature

import androidx.compose.foundation.lazy.items
import com.ivanherboch.chessduck.ui.DuckLazySheet
import com.ivanherboch.chessduck.util.normalizeForSearch
import com.ivanherboch.chessduck.ui.DuckTextField
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import com.ivanherboch.chessduck.ui.icons.ExpandLess
import com.ivanherboch.chessduck.ui.icons.ExpandMore
import com.ivanherboch.chessduck.ui.icons.Folder
import androidx.compose.material.icons.rounded.Search
import com.ivanherboch.chessduck.ui.icons.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.ivanherboch.chessduck.ui.DuckIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.data.DuckDatabase
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.SectionTitle

/** Surligne, sans tenir compte de la casse, la partie de [text] qui correspond à [query]. */
private fun highlight(text: String, query: String, bg: Color, fg: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val at = if (query.isEmpty()) -1 else text.indexOf(query, ignoreCase = true)
    if (at >= 0) addStyle(SpanStyle(background = bg, color = fg), at, at + query.length)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PuzzleCatalog(vm: PuzzlesViewModel, s: PuzzleState, onClose: () -> Unit) {
    val c = LocalDuck.current
    val counts by vm.counts.collectAsStateWithLifecycle()
    LaunchedEffect(s.band) { vm.loadCounts(s.band) }
    var query by remember { mutableStateOf("") }
    var notFound by remember { mutableStateOf(false) }
    val sugg by vm.suggestions.collectAsStateWithLifecycle()
    val open = { vm.openById(query) { ok -> if (ok) onClose() else notFound = true } }
    // Familles dépliées : par défaut, celle du thème en cours.
    val expanded = remember {
        androidx.compose.runtime.mutableStateListOf<String>().apply { PuzzleThemes.byKey[s.theme]?.group?.let { add(it) } }
    }
    val q = normalizeForSearch(query.trim().removePrefix("#"))
    val themeMatches = remember(q) {
        if (q.length >= 2) PuzzleThemes.all.filter { normalizeForSearch(it.name).contains(q) || normalizeForSearch(it.goal).contains(q) } else emptyList()
    }
    DuckLazySheet("Catalogue des problèmes", onClose) {
      item(key = "recherche") {
       Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DuckTextField(
                value = query, onValueChange = { query = it.take(24); notFound = false; vm.search(query) },
                label = "Identifiant Lichess ou thème",
                placeholder = "ex. 00sHx, fourchette…",
                leadingIcon = Icons.Rounded.Search,
                isError = notFound,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { open() }),
                modifier = Modifier.weight(1f),
            )
            DuckButton("Ouvrir", { open() }, enabled = query.isNotBlank(), accent = c.gold, filled = true, height = 56.dp, contentDescription = "Ouvrir le problème « ${query.trim()} »")
        }
        if (sugg.items.isNotEmpty() && query.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)) {
                for (item in sugg.items) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { vm.openSuggestion(item.id) { onClose() } }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(highlight("#${item.id}", sugg.query, c.goldRole.fill, c.goldRole.onFill), color = c.text, style = MaterialTheme.typography.titleSmall)
                        Text(item.type, color = c.blueText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${item.rating}", color = c.muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (sugg.more) Text("…", color = c.muted, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
            }
        }
        if (themeMatches.isNotEmpty()) {
            SectionTitle("Thèmes correspondants")
            for (t in themeMatches) ThemeRow(t.name, t.goal, counts[t.key], s.theme == t.key) { vm.setFilter(s.band, t.key); onClose() }
        }
        if (notFound) Text(
            "Aucun problème « ${query.trim()} » parmi les ${fr(DuckDatabase.PUZZLE_COUNT)} embarqués. Vérifiez l'identifiant (majuscules et minuscules comptent).",
            color = c.redText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
        )
        SectionTitle("1. Difficulté")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PuzzleThemes.BANDS.forEachIndexed { i, label -> DuckChip(label, s.band == i, { vm.setBand(i) }) }
        }
        Text(
            "Enregistrée immédiatement : elle s'applique au prochain problème aléatoire.",
            color = c.muted, style = MaterialTheme.typography.bodySmall,
        )
        SectionTitle("2. Thème — 50 thèmes officiels Lichess")
        Text(
            "Chaque problème est rangé dans un seul thème : son idée la plus caractéristique. En mode libre, le thème reste masqué jusqu'à la résolution.",
            color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp),
        )
        ThemeRow("Tous les thèmes", "Un mélange de toutes les idées, sans indication à l'avance.", counts[""], s.theme.isEmpty(), Icons.Rounded.Shuffle) {
            vm.setFilter(s.band, ""); onClose()
        }
       }
      }
        // Familles en accordéon : toucher la ligne choisit toute la famille, la flèche déplie ses thèmes.
        items(PuzzleThemes.groups, key = { it }) { g ->
          Column {
            val key = PuzzleThemes.groupKey(g)
            val isOpen = g in expanded
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ThemeRow(g, PuzzleThemes.GROUP_GOALS[g] ?: "", counts[key], s.theme == key, Icons.Rounded.Folder) { vm.setFilter(s.band, key); onClose() }
                }
                DuckIconButton(
                    if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    if (isOpen) "Replier les thèmes : $g" else "Déplier les thèmes : $g",
                    { if (isOpen) expanded.remove(g) else expanded.add(g) },
                )
            }
            AnimatedVisibility(isOpen) {
                Column(Modifier.padding(start = 16.dp)) {
                    for (t in PuzzleThemes.all.filter { it.group == g }) {
                        ThemeRow(t.name, t.goal, counts[t.key], s.theme == t.key) { vm.setFilter(s.band, t.key); onClose() }
                    }
                }
            }
          }
        }
    }
}

@Composable
private fun ThemeRow(name: String, goal: String, count: ThemeCount?, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val c = LocalDuck.current
    val enabled = count == null || count.total > 0
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.surfaceHi else c.surface)
            .then(if (selected) Modifier.border(1.dp, c.goldText, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button) { onClick() }
            .semantics { this.selected = selected; if (count != null) stateDescription = "${count.solved} résolus sur ${count.total}" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = c.goldText, modifier = Modifier.padding(end = 8.dp).size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = if (enabled) c.text else c.muted, style = MaterialTheme.typography.titleSmall)
            Text(goal, color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (count != null) {
            Text("${fr(count.solved)} / ${fr(count.total)}", color = if (count.solved > 0) c.greenText else c.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
