package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ivanherboch.chessduck.data.LessonProgressRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import androidx.compose.foundation.lazy.items
import com.ivanherboch.chessduck.ui.DuckLazySheet
import com.ivanherboch.chessduck.ui.LinePlaybackController
import com.ivanherboch.chessduck.util.normalizeForSearch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.ivanherboch.chessduck.ui.DuckSegmentedRow
import com.ivanherboch.chessduck.ui.DuckTextField
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import com.ivanherboch.chessduck.ui.icons.ExpandMore
import com.ivanherboch.chessduck.ui.icons.Lightbulb
import com.ivanherboch.chessduck.ui.icons.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import com.ivanherboch.chessduck.ui.icons.RestartAlt
import androidx.compose.material.icons.rounded.Search
import com.ivanherboch.chessduck.ui.icons.SearchOff
import com.ivanherboch.chessduck.ui.icons.SkipNext
import com.ivanherboch.chessduck.ui.icons.SkipPrevious
import com.ivanherboch.chessduck.ui.icons.SportsScore
import com.ivanherboch.chessduck.ui.icons.SwapVert
import com.ivanherboch.chessduck.ui.icons.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.EmptyState
import com.ivanherboch.chessduck.ui.InfoPill
import com.ivanherboch.chessduck.ui.MoveStrip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.ChessDuckApp
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.content.LessonCategory
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.data.LessonProgress
import com.ivanherboch.chessduck.ui.Arrow
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
import com.ivanherboch.chessduck.ui.undoAnim
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
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
