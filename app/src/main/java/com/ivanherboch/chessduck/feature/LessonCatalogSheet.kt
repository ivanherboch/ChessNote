package com.ivanherboch.chessduck.feature

import androidx.compose.foundation.lazy.items
import com.ivanherboch.chessduck.ui.DuckRadius
import com.ivanherboch.chessduck.ui.DuckLazySheet
import com.ivanherboch.chessduck.util.normalizeForSearch
import com.ivanherboch.chessduck.ui.DuckTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import com.ivanherboch.chessduck.ui.icons.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.content.LessonCategory
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.SectionTitle

/** Catalogue des leçons : recherche, statut (toutes / à maîtriser / maîtrisées) et catégorie. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LessonCatalog(vm: AcademyViewModel, s: AcademyState, mastered: Set<Int>, onClose: () -> Unit) {
    val c = LocalDuck.current
    var query by remember { mutableStateOf("") }
    var status by remember { mutableIntStateOf(0) }
    var cat by remember { mutableStateOf(s.filter) }
    val total = Lessons.all.size
    val q = normalizeForSearch(query.trim())
    // Filtrage et compteurs mémorisés : recalculés seulement quand un filtre ou la progression change.
    val items = remember(q, status, cat, mastered) {
        Lessons.all.filter { l ->
            (cat == null || l.category == cat) &&
                (status == 0 || (status == 1) == (l.id !in mastered)) &&
                (q.isEmpty() || normalizeForSearch(l.title).contains(q) || normalizeForSearch(l.summary).contains(q) || l.id.toString() == q)
        }
    }
    val perCategory = remember(mastered) {
        LessonCategory.entries.associateWith { k -> Lessons.all.count { it.category == k } to Lessons.all.count { it.category == k && it.id in mastered } }
    }
    DuckLazySheet("Leçons — ${mastered.size} / $total maîtrisées", onClose) {
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
                SectionTitle("Statut")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Toutes", "À maîtriser", "Maîtrisées").forEachIndexed { i, label -> DuckChip(label, status == i, { status = i }, accent = c.blue) }
                }
                SectionTitle("Catégorie")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DuckChip("Toutes", cat == null, { cat = null; vm.setFilter(null) }, accent = c.blue)
                    for (k in LessonCategory.entries) {
                        val (n, m) = perCategory.getValue(k)
                        DuckChip("${k.label} $m/$n", cat == k, { cat = k; vm.setFilter(k) }, accent = c.blue)
                    }
                }
                SectionTitle("${items.size} leçon${if (items.size > 1) "s" else ""} — toutes en accès libre")
                if (items.isEmpty()) {
                    EmptyState(
                        Icons.Rounded.SearchOff, "Aucune leçon trouvée", "Essayez un autre mot ou retirez des filtres.",
                        actionLabel = "Réinitialiser les filtres", onAction = { query = ""; status = 0; cat = null; vm.setFilter(null) },
                    )
                }
            }
        }
        items(items, key = { it.id }) { item ->
            val current = item.id == s.lessonId
            val done = item.id in mastered
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(DuckRadius.M))
                    .background(if (current) c.surfaceHi else c.surface)
                    .clickable(role = Role.Button) { vm.open(item.id); onClose() }
                    .semantics { stateDescription = (if (done) "maîtrisée" else "à maîtriser") + if (current) ", leçon en cours" else "" }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    if (done) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.greenText)
                    else Text("${item.id}", color = c.goldText, style = MaterialTheme.typography.labelLarge)
                }
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(item.title, color = c.text, style = MaterialTheme.typography.titleSmall)
                    Text(item.summary, color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.muted)
            }
        }
    }
}
