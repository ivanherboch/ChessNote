package com.ivanherboch.chessduck.feature

import androidx.compose.foundation.lazy.items
import com.ivanherboch.chessduck.ui.DuckRadius
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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

/** Bilan tactique compact : ELO tactique, précision, série en cours et thèmes les plus manqués. */
@Composable
private fun TacticCard(st: TacticStats) {
    val c = LocalDuck.current
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface).padding(12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text("Bilan tactique", color = c.text, style = MaterialTheme.typography.titleSmall)
        Text(
            if (st.tried == 0) "ELO tactique ${st.elo}. Vos statistiques apparaîtront après vos premiers problèmes."
            else "ELO tactique ${st.elo} · précision ${st.precision} % (${st.clean} sur ${st.tried} sans erreur) · série ${st.streak}",
            color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp),
        )
        if (st.missed.isNotEmpty()) Text(
            "Plus manqués : " + st.missed.joinToString(", ") { (t, n) -> "${PuzzleThemes.byKey[t]?.name ?: t} ($n)" },
            color = c.goldText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

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
    val stats by vm.stats.collectAsStateWithLifecycle()
    LaunchedEffect(s.band, s.puzzleElo, s.length) { vm.loadCounts(s.band) }
    var query by remember { mutableStateOf("") }
    // Réglages à l'ouverture du catalogue : si seules la difficulté ou la longueur changent, « Tirer un problème » s'affiche.
    val filtersAtOpen = remember { s.band to s.length }
    var notFound by remember { mutableStateOf(false) }
    val sugg by vm.suggestions.collectAsStateWithLifecycle()

    // Familles dépliées : par défaut, celle du thème en cours.
    val expanded = remember {
        androidx.compose.runtime.mutableStateListOf<String>().apply { PuzzleThemes.byKey[s.theme]?.group?.let { add(it) } }
    }
    val q = normalizeForSearch(query.trim().removePrefix("#"))
    val themeMatches = remember(q) {
        if (q.length >= 2) PuzzleThemes.all.filter { normalizeForSearch(it.name).contains(q) || normalizeForSearch(it.goal).contains(q) } else emptyList()
    }
    // « Ouvrir » : une saisie qui ne peut pas être un identifiant (« fourchette ») ouvre le thème reconnu ; sinon
    // l'identifiant exact, puis l'unique suggestion de cette saisie (jamais celle d'une saisie précédente), puis le thème.
    val open = {
        val typed = query.trim().removePrefix("#")
        if (!PuzzlesViewModel.looksLikeId(typed)) {
            if (themeMatches.isNotEmpty()) { vm.setFilter(s.band, themeMatches.first().key); onClose() } else notFound = true
        } else vm.openById(query) { ok ->
            val only = sugg.items.singleOrNull()?.takeIf { sugg.query.equals(typed, ignoreCase = true) }
            when {
                ok -> onClose()
                only != null -> vm.openSuggestion(only.id) { onClose() }
                themeMatches.isNotEmpty() -> { vm.setFilter(s.band, themeMatches.first().key); onClose() }
                else -> notFound = true
            }
        }
    }
    DuckLazySheet("Catalogue des problèmes", onClose) {
      item(key = "recherche") {
       Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DuckTextField(
                value = query, onValueChange = { query = it.take(24); notFound = false; vm.search(query) },
                // Court : en grande police, une étiquette sur deux lignes chevaucherait le cadre du champ.
                label = "Identifiant ou thème",
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
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)) {
                for (item in sugg.items) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { vm.openSuggestion(item.id) { onClose() } }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Anti-spoiler : le thème d'un problème n'apparaît pas avant de l'avoir ouvert (identifiant et difficulté seulement).
                        Text(highlight("#${item.id}", sugg.query, c.goldRole.fill, c.goldRole.onFill), color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${PuzzleThemes.BANDS[PuzzleThemes.bandOf(item.rating)]} · ${item.rating}", color = c.muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (sugg.more > 0) Text(
                    "Affinez l'identifiant pour voir ${if (sugg.more > 1) "les ${fr(sugg.more)} autres résultats" else "l'autre résultat"}.",
                    color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                )
            }
        }
        if (themeMatches.isNotEmpty()) {
            SectionTitle("Thèmes correspondants")
            for (t in themeMatches) ThemeRow(t.name, t.goal, counts[t.key], s.theme == t.key) { vm.setFilter(s.band, t.key); onClose() }
        }
        if (notFound) Text(
            "Aucun problème « ${query.trim()} » parmi les ${fr(DuckDatabase.PUZZLE_COUNT)} embarqués. Vérifiez l'identifiant (5 lettres ou chiffres, ex. 00sHx) ou cherchez un thème par son nom.",
            color = c.redText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
        )
        TacticCard(stats)
        SectionTitle("1. Difficulté")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PuzzleThemes.BANDS.forEachIndexed { i, label ->
                // Écran « aucun problème » : changer de difficulté relance aussitôt le tirage.
                DuckChip(label, s.band == i, { vm.setBand(i); if (s.empty) vm.random() })
            }
            // « Mon niveau » : des problèmes autour de votre ELO tactique, qui suit vos résultats.
            DuckChip(
                "Mon niveau · ${s.puzzleElo}", s.band == PuzzleThemes.MY_LEVEL, { vm.setBand(PuzzleThemes.MY_LEVEL); if (s.empty) vm.random() },
                Modifier.semantics { contentDescription = "Mon niveau : problèmes autour de votre ELO tactique, ${s.puzzleElo}" }, accent = c.green,
            )
        }
        Text("Longueur de la solution", color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PuzzleThemes.LENGTHS.forEachIndexed { i, label -> DuckChip(label, s.length == i, { vm.setLength(i); if (s.empty) vm.random() }) }
        }
        if (s.length != 0) Text(
            when (s.length) {
                1 -> "Un seul coup à trouver. Les nombres ci-dessous ne comptent que ces problèmes."
                2 -> "Deux coups à trouver. Les nombres ci-dessous ne comptent que ces problèmes."
                else -> "Trois coups ou plus à trouver. Les nombres ci-dessous ne comptent que ces problèmes (aucun mat en 1 ou en 2, par exemple)."
            },
            color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
        )
        // Les réglages seuls ne tirent pas de problème : un bouton le fait, sans repasser par un thème.
        if ((s.band to s.length) != filtersAtOpen && !s.empty) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Enregistré : s'applique au prochain problème aléatoire.", color = c.muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            DuckButton(
                "Tirer un problème", { vm.random(); onClose() }, accent = c.gold, filled = true, icon = Icons.Rounded.Shuffle,
                contentDescription = "Tirer un problème : ${PuzzleThemes.bandLabel(s.band)}, longueur ${PuzzleThemes.LENGTHS[s.length].lowercase()}",
            )
        } else Text(
            "Enregistrés immédiatement : ils s'appliquent au prochain problème aléatoire.",
            color = c.muted, style = MaterialTheme.typography.bodySmall,
        )
        if (s.allSolvedInFilter) Text(
            "Tous les problèmes non résolus de cette sélection sont épuisés : le tirage repropose des problèmes déjà résolus. Changez de difficulté ou de thème pour en découvrir d'autres.",
            color = c.goldText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
        )
        SectionTitle("2. Thème — 50 thèmes officiels Lichess")
        Text(
            "Chaque problème est rangé dans un seul thème : son idée la plus caractéristique. Quand tous ceux d'un thème sont résolus " +
                "à votre niveau, le tirage continue avec les problèmes où ce thème apparaît aussi. En mode libre, le thème reste masqué jusqu'à la résolution.",
            color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp),
        )
        ThemeRow("Tous les thèmes", "Un mélange de toutes les idées, sans indication à l'avance.", counts[""], s.theme.isEmpty(), Icons.Rounded.Shuffle) {
            vm.setFilter(s.band, ""); onClose()
        }
       }
      }
        // Familles en accordéon : toucher la ligne déplie ses thèmes ; « Toute la famille » la choisit en bloc.
        items(PuzzleThemes.groups, key = { it }) { g ->
          Column {
            val key = PuzzleThemes.groupKey(g)
            val isOpen = g in expanded
            ThemeRow(
                g, PuzzleThemes.GROUP_GOALS[g] ?: "", counts[key], s.theme == key, Icons.Rounded.Folder,
                trailing = if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                expanded = isOpen,
            ) { if (isOpen) expanded.remove(g) else expanded.add(g) }
            AnimatedVisibility(isOpen) {
                Column(Modifier.padding(start = 16.dp)) {
                    DuckChip("Toute la famille", s.theme == key, { vm.setFilter(s.band, key); onClose() }, Modifier.padding(vertical = 4.dp), accent = c.gold)
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
private fun ThemeRow(
    name: String, goal: String, count: ThemeCount?, selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /** Flèche d'accordéon : la ligne déplie alors son contenu au lieu de choisir le thème. */
    trailing: androidx.compose.ui.graphics.vector.ImageVector? = null,
    expanded: Boolean? = null,
    onClick: () -> Unit,
) {
    val c = LocalDuck.current
    val enabled = count == null || count.total > 0
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(DuckRadius.M))
            .background(if (selected) c.surfaceHi else c.surface)
            .then(if (selected) Modifier.border(1.dp, c.goldText, RoundedCornerShape(DuckRadius.M)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button) { onClick() }
            .semantics {
                this.selected = selected
                stateDescription = listOfNotNull(
                    expanded?.let { if (it) "déplié" else "replié" },
                    count?.let { "${it.solved} résolus sur ${it.total}" },
                ).joinToString(", ")
            }
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
        if (trailing != null) Icon(trailing, contentDescription = null, tint = c.muted, modifier = Modifier.padding(start = 8.dp))
    }
}
