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
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Visibility
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

/** Choix segmenté d'un réglage d'export (format, fond). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportOption(label: String, options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    val c = LocalDuck.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(72.dp))
        DuckSegmentedRow(options.map { it.first }, options.indexOfFirst { it.second }, onSelect, Modifier.weight(1f))
    }
}

/** Une image exportable : aperçu, titre, description et deux actions, partager ou enregistrer. */
@Composable
internal fun ExportCard(
    title: String, description: String, accent: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, preview: ImageBitmap?,
    masked: Boolean = false, enabled: Boolean = true, onShare: () -> Unit, onSave: () -> Unit,
) {
    val c = LocalDuck.current
    // Anti-spoiler : l'aperçu de la solution reste caché tant que le problème n'est pas résolu.
    var reveal by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).padding(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(96.dp).height(120.dp).clip(RoundedCornerShape(8.dp)).background(c.surfaceHi), contentAlignment = Alignment.Center) {
                if (masked && !reveal) {
                    Column(
                        Modifier.fillMaxSize().clickable(onClickLabel = "Afficher l'aperçu de la solution") { reveal = true },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = c.muted, modifier = Modifier.size(24.dp))
                        Text("Toucher pour voir", color = c.muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
                    }
                } else if (preview != null) {
                    androidx.compose.foundation.Image(preview, contentDescription = "Aperçu : $title", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    CircularProgressIndicator(Modifier.size(22.dp), color = c.role(accent).fill, strokeWidth = 2.dp)
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = c.role(accent).text, modifier = Modifier.size(20.dp))
                    Text(title, color = c.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                }
                Text(description, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
        EqualRow(gap = 8.dp, modifier = Modifier.padding(top = 10.dp)) {
            DuckButton("Partager", onShare, Modifier.weight(1f), enabled = enabled, accent = accent, filled = true, icon = Icons.Rounded.Share, contentDescription = "Partager : $title")
            DuckButton("Enregistrer", onSave, Modifier.weight(1f), enabled = enabled, accent = accent, variant = ButtonVariant.OUTLINED, icon = Icons.Rounded.Download, contentDescription = "Enregistrer dans la galerie : $title")
        }
    }
}

/**
 * Tiroir « Exporter en image » : format et fond (mémorisés), aperçus calculés hors du fil principal,
 * partage ou enregistrement de l'énoncé et de la solution (aperçu de la solution masqué tant que non résolu).
 * [onExport] lance l'export dans la portée de l'écran (il survit à la fermeture du tiroir), un seul à la fois.
 */
@Composable
internal fun ExportSheet(
    p: Puzzle,
    line: Line,
    playerWhite: Boolean,
    solved: Boolean,
    exporting: Boolean,
    onExport: (suspend () -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalDuck.current
    val context = LocalContext.current
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    DuckSheet("Exporter en image", onClose) {
        val duckApp = context.applicationContext as ChessDuckApp
        var styleOrNull by remember { mutableStateOf<ShareStyle?>(null) }
        LaunchedEffect(Unit) { if (styleOrNull == null) styleOrNull = ShareStyle.from(duckApp.prefs.settings.first()) }
        val style = styleOrNull ?: ShareStyle()
        // Écrit dans la portée de l'application : fermer le tiroir n'annule jamais l'enregistrement.
        val setStyle = { v: ShareStyle -> styleOrNull = v; duckApp.scope.launch { duckApp.prefs.setShareStyle(v.format.name, v.light) }; Unit }
        // Aperçus calculés hors du fil principal, recalculés à chaque changement de réglage ;
        // la clé (style, problème) permet de réutiliser le bitmap à l'export s'il correspond encore.
        val problemShot by produceState<Pair<Any, Bitmap>?>(null, styleOrNull, p.id) {
            if (styleOrNull == null) return@produceState
            val key = style to p.id
            value = key to withContext(Dispatchers.Default) { ShareCard.render(context, p, line.positions[1], playerWhite, line.moves[0], style) }
        }
        val solutionShot by produceState<Pair<Any, Bitmap>?>(null, styleOrNull, p.id) {
            if (styleOrNull == null) return@produceState
            val key = style to p.id
            value = key to withContext(Dispatchers.Default) { ShareCard.renderSolution(context, p, style) }
        }
        val key = style to p.id
        val problemReady = problemShot?.takeIf { it.first == key }?.second
        val solutionReady = solutionShot?.takeIf { it.first == key }?.second
        val problemPreview = remember(problemReady) { problemReady?.asImageBitmap() }
        val solutionPreview = remember(solutionReady) { solutionReady?.asImageBitmap() }
        val failed = false to "L'image n'a pas pu être enregistrée."
        // Export lancé dans la portée de l'écran (survit à la fermeture du tiroir), un seul à la fois.
        fun export(block: suspend () -> Unit) = onExport(block)
        ExportOption("Format", listOf("Portrait 4:5" to (style.format == ShareFormat.PORTRAIT), "Carré 1:1" to (style.format == ShareFormat.SQUARE))) { i ->
            setStyle(style.copy(format = if (i == 0) ShareFormat.PORTRAIT else ShareFormat.SQUARE))
        }
        Text(
            if (style.format == ShareFormat.PORTRAIT) "Idéal pour WhatsApp et les messageries : affiché en entier, sans recadrage."
            else "Idéal pour une publication Instagram ou un aperçu compact.",
            color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp),
        )
        ExportOption("Fond", listOf("Sombre" to !style.light, "Clair" to style.light)) { i -> setStyle(style.copy(light = i == 1)) }
        ExportCard(
            "Le problème", "L'énoncé seul : le défi, l'échiquier, la difficulté et un indice — sans la solution.", c.blue, Icons.Rounded.Extension, problemPreview,
            enabled = !exporting,
            onShare = { onClose(); export { ShareCard.share(context, p, line.positions[1], playerWhite, line.moves[0], style, problemReady) } },
            onSave = { export { status = ShareCard.saveProblem(context, p, line.positions[1], playerWhite, line.moves[0], style, problemReady)?.let { true to it } ?: failed } },
        )
        ExportCard(
            "La solution", "Une carte par coup : flèches, échecs (+), mat (#), pièces prises et conclusion.", c.green, Icons.Rounded.CheckCircle, solutionPreview, masked = !solved,
            enabled = !exporting,
            onShare = { onClose(); export { ShareCard.shareSolution(context, p, style, solutionReady) } },
            onSave = { export { status = ShareCard.saveSolution(context, p, style, solutionReady)?.let { true to it } ?: failed } },
        )
        status?.let { (ok, msg) ->
            Row(Modifier.padding(top = 4.dp, bottom = 6.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, contentDescription = null, tint = if (ok) c.greenText else c.redText, modifier = Modifier.size(18.dp))
                Text(msg, color = if (ok) c.greenText else c.redText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
