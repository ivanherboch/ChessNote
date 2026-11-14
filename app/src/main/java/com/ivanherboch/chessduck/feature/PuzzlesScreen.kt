package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.data.PuzzleRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import androidx.compose.foundation.lazy.items
import com.ivanherboch.chessduck.ui.DuckLazySheet
import com.ivanherboch.chessduck.util.normalizeForSearch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.ivanherboch.chessduck.ui.DuckSegmentedRow
import com.ivanherboch.chessduck.ui.DuckTextField
import android.graphics.Bitmap
import androidx.compose.runtime.rememberCoroutineScope
import com.ivanherboch.chessduck.util.runCatchingSuspend
import androidx.compose.ui.text.style.TextAlign
import com.ivanherboch.chessduck.ui.icons.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.produceState
import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.ivanherboch.chessduck.ui.icons.Undo
import com.ivanherboch.chessduck.ui.icons.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import com.ivanherboch.chessduck.ui.icons.Download
import com.ivanherboch.chessduck.ui.icons.ErrorOutline
import com.ivanherboch.chessduck.ui.icons.ExpandLess
import com.ivanherboch.chessduck.ui.icons.ExpandMore
import com.ivanherboch.chessduck.ui.icons.Extension
import com.ivanherboch.chessduck.ui.icons.Folder
import com.ivanherboch.chessduck.ui.icons.History
import com.ivanherboch.chessduck.ui.icons.IosShare
import com.ivanherboch.chessduck.ui.icons.Lightbulb
import androidx.compose.material.icons.rounded.Search
import com.ivanherboch.chessduck.ui.icons.SearchOff
import androidx.compose.material.icons.rounded.Share
import com.ivanherboch.chessduck.ui.icons.Shuffle
import com.ivanherboch.chessduck.ui.icons.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.ivanherboch.chessduck.ui.ButtonVariant
import com.ivanherboch.chessduck.ui.SideDot
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.EmptyState
import com.ivanherboch.chessduck.ui.LoadingState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.ChessDuckApp
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.PIECE_NAMES_FR
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.data.DuckDatabase
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.data.PuzzleProgress
import com.ivanherboch.chessduck.engine.isMateScore
import com.ivanherboch.chessduck.engine.mateIn
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.AutoText
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.NumberFormat
import java.util.Locale

private val frNumber: NumberFormat = NumberFormat.getIntegerInstance(Locale.FRANCE)

fun fr(n: Int): String = frNumber.format(n).replace(' ', ' ').replace(' ', ' ')

@Composable
fun PuzzlesScreen(vm: PuzzlesViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val solved by vm.solvedIds.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val context = LocalContext.current
    var catalog by remember { mutableStateOf(false) }
    var shareChoice by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val exportScope = rememberCoroutineScope()
    var recentSheet by remember { mutableStateOf(false) }
    val recents by vm.recents.collectAsStateWithLifecycle()
    val line = s.line
    val p = s.puzzle

    if (p == null || line == null) {
        if (s.empty) Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            EmptyState(
                Icons.Rounded.SearchOff, "Aucun problème trouvé", "Aucun problème ne correspond à ces filtres.",
                actionLabel = "Ouvrir le catalogue", onAction = { catalog = true },
                secondaryLabel = "Réinitialiser les filtres", onSecondary = { vm.setFilter(0, "") },
            )
        } else LoadingState("Chargement du problème…")
        if (catalog) PuzzleCatalog(vm, s, onClose = { catalog = false; vm.clearSearch() })
        return
    }
    val type = PuzzleThemes.primary(p.themes)
    val visible = s.themeVisible

    ModuleLayout(
        bottomMin = 170.dp,
        top = {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DuckButton(
                    "${fr(solved.size)} / ${fr(DuckDatabase.PUZZLE_COUNT)}", { catalog = true }, Modifier.weight(1f),
                    accent = c.blue, icon = Icons.Rounded.Extension,
                    contentDescription = "Catalogue des problèmes : ${fr(solved.size)} résolus sur ${fr(DuckDatabase.PUZZLE_COUNT)}",
                )
                DuckIconButton(Icons.Rounded.Casino, "Problème aléatoire", vm::random, accent = c.gold, filled = s.solved || s.empty)
                DuckIconButton(Icons.Rounded.History, "Problèmes récents", { recentSheet = true }, enabled = recents.isNotEmpty())
                DuckIconButton(Icons.Rounded.IosShare, "Exporter en image", { shareChoice = true })
            }
            // Ligne d'information toujours lisible en entier : elle passe à la ligne au lieu de se couper.
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                SideDot(s.playerWhite)
                Text(
                    if (s.playerWhite) "Trait aux Blancs" else "Trait aux Noirs", color = c.text, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Text(
                    (if (visible) type.name else "Thème masqué") + " · ${PuzzleThemes.BANDS[PuzzleThemes.bandOf(p.rating)]} ${p.rating} · #${p.id}",
                    color = c.muted, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
            }
        },
        board = {
            val r = s.refute
            val cur = s.cursor.coerceIn(0, line.size)
            val pos = r?.position ?: line.positions[cur]
            val last = r?.last ?: if (cur > 0) line.moves[cur - 1] else null
            val canPlay = vm.canPlay(s)
            val next = if (canPlay) line.moves[cur] else null
            ChessBoard(
                position = pos,
                modifier = Modifier.fillMaxSize(),
                flipped = !s.playerWhite,
                interactive = canPlay,
                lastMove = last,
                anim = s.anim,
                arrows = listOfNotNull(r?.arrow, if (next != null && s.hintStage >= 2) Arrow(next.from, next.to, c.green) else null),
                marks = if (next != null && s.hintStage >= 1) listOf(next.from to c.green) else emptyList(),
                onMove = { m ->
                    when (vm.onMove(m)) {
                        2 -> fb?.success()
                        -1 -> fb?.error()
                    }
                },
            )
        },
        bottom = {
            if (s.empty) {
                EmptyState(
                    Icons.Rounded.SearchOff, "Plus aucun problème", "Aucun problème ne correspond à ces filtres.",
                    Modifier.padding(top = 6.dp), actionLabel = "Ouvrir le catalogue", onAction = { catalog = true },
                    secondaryLabel = "Réinitialiser les filtres", onSecondary = { vm.setFilter(0, "") },
                )
                Spacer(Modifier.weight(1f))
            } else {
                // Anti-spoiler : en mode libre (tous les thèmes ou une famille), le thème et son conseil
                // restent masqués jusqu'à la résolution, sauf demande explicite.
                val showTip = visible && s.refute == null && !s.solved && s.tone == Tone.INFO
                FeedbackCard(
                    s.message + if (showTip) "\n\nConseil : ${type.tip}" else "",
                    s.tone, Modifier.padding(top = 6.dp).weight(1f),
                    title = if (visible) "Type de problème : ${type.name}" else "Thème masqué (mode libre)",
                    action = if (!visible) ({
                        DuckButton("Révéler le thème", vm::revealTheme, icon = Icons.Rounded.Visibility, accent = c.blue, variant = ButtonVariant.OUTLINED)
                    }) else null,
                )
                if (s.refute != null && s.refuteUntil > 0) RefuteCountdown(s.refuteUntil, s.refuteHold)
                EqualRow(Modifier.padding(vertical = 6.dp)) {
                    DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Coup précédent", vm::back, enabled = s.refute == null && s.cursor > 0)
                    if (s.refute != null) DuckButton("Réessayer", vm::retry, Modifier.weight(1f), accent = c.red, filled = true, icon = Icons.AutoMirrored.Rounded.Undo)
                    else if (s.solved) DuckButton("Problème aléatoire", vm::random, Modifier.weight(1f), accent = c.gold, filled = true, icon = Icons.Rounded.Casino)
                    else DuckButton("Indice ${(s.hintStage + 1).coerceAtMost(2)}/2", vm::hint, Modifier.weight(1f), enabled = vm.canPlay(s), accent = c.green, icon = Icons.Rounded.Lightbulb)
                    DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Coup suivant", vm::forward, enabled = s.refute == null && s.cursor < s.progress)
                }
            }
        },
    )

    if (catalog) PuzzleCatalog(vm, s, onClose = { catalog = false; vm.clearSearch() })
    if (recentSheet) {
        DuckSheet("Derniers problèmes joués", { recentSheet = false }) {
            for (r in recents) {
                val done = r.id in solved
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)
                        .clickable(role = Role.Button) { recentSheet = false; vm.openPuzzle(r) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        // Le thème d'un problème non résolu reste masqué (anti-spoiler).
                        Text("#${r.id} · ${if (done) PuzzleThemes.primary(r.themes).name else "thème masqué"}", color = c.text, style = MaterialTheme.typography.titleSmall)
                        Text("${PuzzleThemes.BANDS[PuzzleThemes.bandOf(r.rating)]} · ${r.rating}", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (done) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.greenText, modifier = Modifier.padding(end = 4.dp).size(18.dp))
                    Text(if (done) "Résolu" else "À résoudre", color = if (done) c.greenText else c.goldText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    if (shareChoice) {
        ExportSheet(
            p, line, s.playerWhite, s.solved, exporting,
            onExport = { block ->
                if (!exporting) {
                    exporting = true
                    exportScope.launch { try { block() } finally { exporting = false } }
                }
            },
            onClose = { shareChoice = false },
        )
    }
}

/** Compte à rebours visuel avant l'annulation automatique d'une réfutation. */
@Composable
private fun RefuteCountdown(until: Long, hold: Long) {
    val c = LocalDuck.current
    val progress = remember(until) { Animatable(((until - System.currentTimeMillis()).toFloat() / hold).coerceIn(0f, 1f)) }
    LaunchedEffect(until) {
        val left = (until - System.currentTimeMillis()).coerceAtLeast(0)
        progress.animateTo(0f, tween(left.toInt(), easing = LinearEasing))
    }
    LinearProgressIndicator(
        progress = { progress.value }, color = c.redRole.fill, trackColor = c.surfaceHi,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
            .semantics { contentDescription = "Annulation automatique du coup imminente" },
    )
}
