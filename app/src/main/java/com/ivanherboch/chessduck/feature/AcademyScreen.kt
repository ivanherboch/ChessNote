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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademyScreen(vm: AcademyViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val mastered by vm.mastered.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val l = Lessons.byId(s.lessonId)
    val line = l.line
    var catalog by remember { mutableStateOf(false) }
    val total = Lessons.all.size
    val cur = s.cursor.coerceIn(0, line.size)

    ModuleLayout(
        bottomMin = 190.dp,
        top = {
            // Sélecteur de leçon : précédente / leçon courante (ouvre le catalogue) / suivante.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Leçon précédente", vm::prevLesson)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.surface)
                        .clickable(role = Role.Button, onClickLabel = "Ouvrir le catalogue des leçons") { catalog = true }
                        .clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = "Leçon ${l.id} : ${l.title}, ${l.category.label}" + (if (l.id in mastered) ", maîtrisée" else "") +
                                ". ${mastered.size} leçons maîtrisées sur $total. Ouvrir le catalogue des leçons"
                            onClick { catalog = true; true }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InfoPill(l.category.label, accent = c.blue)
                        if (l.id in mastered) Icon(Icons.Rounded.CheckCircle, "Maîtrisée", tint = c.greenText, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                        Spacer(Modifier.weight(1f))
                        Text("${mastered.size}/$total", color = c.goldText, style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        "${l.id}. ${l.title}", color = c.text, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Leçon suivante", vm::nextLesson)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DuckSegmentedRow(
                    listOf("Guidé (démo)", "Défi (pratique)"), if (s.challenge) 1 else 0, { i -> vm.setChallenge(i == 1) },
                    Modifier.weight(1f), icons = listOf(Icons.Rounded.Visibility, Icons.Rounded.SportsScore),
                )
                DuckIconButton(Icons.Rounded.SwapVert, "Retourner l'échiquier", vm::flip)
            }
        },
        board = {
            val pos = line.positions[cur]
            val last = if (cur > 0) line.moves[cur - 1] else null
            val heroTurn = s.challenge && !s.done && cur < line.size && pos.whiteToMove == l.heroWhite
            val next = if (cur < line.size) line.moves[cur] else null
            ChessBoard(
                position = pos,
                modifier = Modifier.fillMaxSize(),
                flipped = s.flipped,
                interactive = heroTurn,
                lastMove = last,
                anim = s.anim,
                arrows = when {
                    heroTurn && s.hintStage >= 2 && next != null -> listOf(Arrow(next.from, next.to, c.green))
                    !s.challenge && last != null -> listOf(Arrow(last.from, last.to, c.blue))
                    else -> emptyList()
                },
                marks = if (heroTurn && s.hintStage >= 1 && next != null) listOf(next.from to c.green) else emptyList(),
                onMove = { m -> if (vm.challengeMove(m)) fb?.success() else fb?.error() },
            )
        },
        bottom = {
            // Progression dans la leçon : chaque coup est cliquable en mode Guidé.
            MoveStrip(
                line, cur, Modifier.padding(top = 4.dp),
                onSelect = if (!s.challenge) vm::jump else null,
                onPrev = if (!s.challenge) ({ vm.step(-1) }) else null,
                onNext = if (!s.challenge) ({ vm.step(1) }) else null,
                emptyText = "Les coups de la leçon s'afficheront ici.",
            )
            if (!s.challenge) {
                val title = if (cur == 0) "Objectif de la leçon" else moveLabel(line.positions[cur - 1].fullmove, line.positions[cur - 1].whiteToMove, line.sans[cur - 1]) + " · coup $cur / ${line.size}"
                val text = if (cur == 0) l.summary + " Touchez Lecture pour dérouler la leçon coup par coup." else l.texts[cur - 1]
                FeedbackCard(text, Tone.INFO, Modifier.padding(top = 4.dp).weight(1f), title = title)
                EqualRow(Modifier.padding(vertical = 6.dp)) {
                    DuckButton("Début", vm::rewind, Modifier.weight(1f), enabled = cur > 0, icon = Icons.Rounded.SkipPrevious)
                    DuckButton(
                        if (s.playing) "Pause" else "Lecture", vm::togglePlay, Modifier.weight(1.5f), accent = c.blue, filled = true,
                        icon = if (s.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    )
                    DuckButton("Fin", vm::last, Modifier.weight(1f), enabled = cur < line.size, icon = Icons.Rounded.SkipNext)
                }
            } else {
                val title = if (s.done) "Défi réussi" else "Défi : jouez les ${if (l.heroWhite) "Blancs" else "Noirs"} · coup $cur / ${line.size}"
                FeedbackCard(s.message, s.tone, Modifier.padding(top = 4.dp).weight(1f), title = title)
                EqualRow(Modifier.padding(vertical = 6.dp)) {
                    DuckButton("Recommencer", vm::restartChallenge, Modifier.weight(1f), icon = Icons.Rounded.RestartAlt)
                    if (s.done) DuckButton("Leçon suivante", vm::nextLesson, Modifier.weight(1.4f), accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                    else DuckButton("Indice ${(s.hintStage + 1).coerceAtMost(2)}/2", vm::hint, Modifier.weight(1.4f), accent = c.green, icon = Icons.Rounded.Lightbulb)
                }
            }
        },
    )

    if (catalog) LessonCatalog(vm, s, mastered) { catalog = false }
}

fun moveLabel(fullmove: Int, white: Boolean, san: String) = if (white) "$fullmove. $san" else "$fullmove… $san"
