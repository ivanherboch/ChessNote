package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.ui.icons.School
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ivanherboch.chessduck.ui.icons.Tune
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import com.ivanherboch.chessduck.ui.DuckRadius
import com.ivanherboch.chessduck.ui.DuckLazySheet
import com.ivanherboch.chessduck.util.normalizeForSearch
import com.ivanherboch.chessduck.ui.DuckTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import com.ivanherboch.chessduck.ui.icons.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.EmptyState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.SectionTitle

/** Catalogue des leçons : recherche, statut (toutes / à maîtriser / maîtrisées) et catégorie. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LessonCatalog(
    vm: AcademyViewModel,
    s: AcademyState,
    mastered: Set<Int>,
    /** Filtre d'état (0 toutes, 1 à maîtriser, 2 maîtrisées, 3 à revoir), conservé par l'écran entre deux ouvertures. */
    status: Int,
    onStatus: (Int) -> Unit,
    /** Filtre de palier (0 : tous), conservé lui aussi entre deux ouvertures. */
    levelFilter: Int,
    onLevel: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalDuck.current
    val progress by vm.progress.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    // Catégorie : filtre du ViewModel (conservé, il oriente aussi Leçon précédente / suivante) ; il n'ouvre aucune leçon.
    val cat = s.filter
    val level = levelFilter.takeIf { it > 0 }
    val active = (if (status != 0) 1 else 0) + (if (level != null) 1 else 0) + (if (cat != null) 1 else 0)
    // Filtres repliés par défaut : les premières leçons sont visibles dès l'ouverture.
    var showFilters by remember { mutableStateOf(false) }
    val total = Lessons.all.size
    val q = normalizeForSearch(query.trim())
    // Filtrage et compteurs mémorisés : recalculés seulement quand un filtre ou la progression change.
    val items = remember(q, status, cat, level, mastered, progress) {
        Lessons.all.filter { l ->
            (cat == null || l.category == cat) && (level == null || l.level == level) &&
                when (status) {
                    1 -> l.id !in mastered
                    2 -> l.id in mastered
                    3 -> progress[l.id]?.let { vm.srs.isDue(it) } == true
                    else -> true
                } &&
                (q.isEmpty() || normalizeForSearch(l.title).contains(q) || normalizeForSearch(l.summary).contains(q) || Lessons.number(l.id).toString() == q)
        }
    }
    val perCategory = remember(mastered) {
        Lessons.categories.associateWith { k -> Lessons.all.count { it.category == k } to Lessons.all.count { it.category == k && it.id in mastered } }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentIndex = items.indexOfFirst { it.id == s.lessonId }
    DuckLazySheet("Leçons — ${mastered.size} / $total maîtrisées", onClose, listState) {
        item(key = "filtres") {
            Column {
                DuckTextField(
                    value = query, onValueChange = { query = it },
                    label = "Rechercher une leçon",
                    placeholder = "ex. fourchette, Lucena, sicilienne…",
                    leadingIcon = Icons.Rounded.Search,
                    trailingIcon = { if (query.isNotEmpty()) DuckIconButton(Icons.Rounded.Close, "Effacer la recherche", { query = "" }) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Une ligne récapitulative ; les trois filtres se déplient en rangées horizontales défilantes.
                val summary = listOfNotNull(
                    listOf("Toutes", "À maîtriser", "Maîtrisées", "À revoir")[status].takeIf { status != 0 },
                    level?.let { "Palier ${levelName(it)}" }, cat?.label,
                ).joinToString(" · ").ifEmpty { "Aucun filtre" }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DuckChip(
                        if (active > 0) "Filtres ($active)" else "Filtres", showFilters || active > 0, { showFilters = !showFilters },
                        Modifier.semantics { contentDescription = "Filtres : $summary. ${if (showFilters) "Replier" else "Déplier"}" },
                        accent = c.blue, icon = Icons.Rounded.Tune,
                    )
                    Text(summary, color = c.muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (active > 0) DuckChip("Effacer", false, { onStatus(0); onLevel(0); vm.setFilter(null) }, icon = Icons.Rounded.Close)
                }
                // Raccourci vers la leçon en cours, sans perdre l'accès aux filtres en tête de liste.
                if (currentIndex >= 0) DuckChip(
                    "Aller à la leçon en cours (n° ${Lessons.number(s.lessonId)})", false, { scope.launch { listState.animateScrollToItem(currentIndex + 1) } },
                    Modifier.padding(top = 4.dp), accent = c.gold, icon = Icons.Rounded.School,
                )
                if (showFilters) {
                    FilterRow("Statut") {
                        val due = Lessons.all.count { l -> progress[l.id]?.let { vm.srs.isDue(it) } == true }
                        val counts = listOf(total, total - mastered.size, mastered.size, due)
                        listOf("Toutes", "À maîtriser", "Maîtrisées", "À revoir").forEachIndexed { i, label ->
                            DuckChip("$label ${counts[i]}", status == i, { onStatus(i) }, Modifier.semantics { contentDescription = "$label : ${counts[i]} leçons" }, accent = c.blue)
                        }
                    }
                    FilterRow("Palier") {
                        DuckChip("Tous", level == null, { onLevel(0) }, accent = c.blue)
                        for (lv in 1..5) {
                            val n = Lessons.all.count { it.level == lv }
                            val m = Lessons.all.count { it.level == lv && it.id in mastered }
                            if (n > 0) DuckChip(
                                "${levelName(lv)} $m/$n", level == lv, { onLevel(lv) },
                                Modifier.semantics { contentDescription = "Palier ${levelSpoken(lv)} : $m leçons maîtrisées sur $n" }, accent = c.blue,
                            )
                        }
                    }
                    FilterRow("Catégorie") {
                        DuckChip("Toutes", cat == null, { vm.setFilter(null) }, accent = c.blue)
                        for (k in Lessons.categories) {
                            val (n, m) = perCategory.getValue(k)
                            DuckChip("${k.label} $m/$n", cat == k, { vm.setFilter(k) }, accent = c.blue)
                        }
                    }
                }
                SectionTitle("${items.size} leçon${if (items.size > 1) "s" else ""} — toutes en accès libre")
                if (items.isEmpty()) {
                    EmptyState(
                        Icons.Rounded.SearchOff, "Aucune leçon trouvée", "Essayez un autre mot ou retirez des filtres.",
                        actionLabel = "Réinitialiser les filtres", onAction = { query = ""; onStatus(0); onLevel(0); vm.setFilter(null) },
                    )
                }
            }
        }
        items(items, key = { it.id }) { item ->
            val current = item.id == s.lessonId
            val p = progress[item.id]
            val stars = p?.stars ?: 0
            // Un seul indicateur d'état par carte : à revoir aujourd'hui, maîtrisée (avec ses étoiles), en cours, ou conseillée.
            val state = when {
                p != null && vm.srs.isDue(p) -> "À revoir"
                item.id in mastered -> STATUS_MASTERED
                lessonStatus(p) == STATUS_STARTED -> STATUS_STARTED
                item.id == home.recommended -> "Conseillée"
                else -> null
            }
            val accent = when (state) {
                "À revoir" -> c.goldText
                STATUS_MASTERED -> c.greenText
                STATUS_STARTED -> c.blueText
                else -> c.greenText
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(DuckRadius.M))
                    .background(if (current) c.surfaceHi else c.surface)
                    .clickable(role = Role.Button) { vm.open(item.id, guided = true); onClose() }
                    .semantics {
                        stateDescription = listOfNotNull(
                            state?.lowercase()?.plus(if (state == STATUS_MASTERED && stars > 0) ", ${starsText(stars)}" else ""),
                            "palier ${levelSpoken(item.level)}", if (current) "dernière leçon ouverte" else null,
                        ).joinToString(", ")
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Text("${Lessons.number(item.id)}", color = c.goldText, style = MaterialTheme.typography.labelLarge)
                }
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(item.title, color = c.text, style = MaterialTheme.typography.titleSmall)
                    Text(item.summary, color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (state != null) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(state, color = accent, style = MaterialTheme.typography.labelMedium)
                        if (state == STATUS_MASTERED && stars > 0) StarRow(stars, Modifier.padding(start = 6.dp), size = 14.dp)
                    }
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.muted)
            }
        }
    }
}

/** Rangée de filtres défilante horizontalement, précédée de son nom : une seule ligne de hauteur. */
@Composable
private fun FilterRow(label: String, content: @Composable () -> Unit) {
    val c = LocalDuck.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp).semantics { heading() })
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}
