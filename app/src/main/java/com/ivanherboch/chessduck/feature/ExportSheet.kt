package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.ui.DuckSegmentedRow
import com.ivanherboch.chessduck.ui.DuckRadius
import android.graphics.Bitmap
import androidx.compose.ui.text.style.TextAlign
import com.ivanherboch.chessduck.ui.icons.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import com.ivanherboch.chessduck.ui.icons.Download
import com.ivanherboch.chessduck.ui.icons.ErrorOutline
import com.ivanherboch.chessduck.ui.icons.Extension
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ivanherboch.chessduck.ui.ButtonVariant
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.LocalDuck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.ivanherboch.chessduck.util.runCatchingSuspend
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog

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
    masked: Boolean = false, enabled: Boolean = true,
    /** Toucher l'aperçu masqué : l'appelant démasque (et ne calcule l'image qu'à ce moment-là). */
    onReveal: () -> Unit = {},
    /** Aperçu impossible à générer : message à la place du chargement. */
    failed: Boolean = false,
    /** Cette image est en cours de préparation pour le partage. */
    busy: Boolean = false,
    onShare: () -> Unit, onSave: () -> Unit,
) {
    val c = LocalDuck.current
    // Anti-spoiler : l'aperçu de la solution reste caché tant que le problème n'est pas résolu ou démasqué.
    var zoom by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(DuckRadius.L)).background(c.surface).padding(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(96.dp).height(120.dp).clip(RoundedCornerShape(DuckRadius.S)).background(c.surfaceHi), contentAlignment = Alignment.Center) {
                if (masked) {
                    Column(
                        Modifier.fillMaxSize().clickable(onClickLabel = "Afficher l'aperçu de la solution") { onReveal() },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = c.muted, modifier = Modifier.size(24.dp))
                        Text("Toucher pour voir", color = c.muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
                    }
                } else if (preview != null) {
                    androidx.compose.foundation.Image(
                        preview, contentDescription = "Aperçu : $title", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clickable(onClickLabel = "Agrandir l'aperçu") { zoom = true },
                    )
                } else if (failed) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.redText, modifier = Modifier.size(24.dp))
                        Text("Aperçu indisponible", color = c.muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(4.dp))
                    }
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
            DuckButton(
                if (busy) "Préparation…" else "Partager", onShare, Modifier.weight(1f), enabled = enabled, accent = accent, filled = true,
                icon = Icons.Rounded.Share, contentDescription = if (busy) "Préparation de l'image : $title" else "Partager : $title",
            )
            DuckButton("Enregistrer", onSave, Modifier.weight(1f), enabled = enabled, accent = accent, variant = ButtonVariant.OUTLINED, icon = Icons.Rounded.Download, contentDescription = "Enregistrer dans la galerie : $title")
        }
    }
    // Aperçu plein écran : toucher n'importe où le referme.
    if (zoom && preview != null) {
        Dialog({ zoom = false }, DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable(onClickLabel = "Fermer l'aperçu") { zoom = false }.padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(preview, contentDescription = "Aperçu agrandi : $title", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
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
    /** Thème connu du joueur : sinon, l'indice (qui nomme le thème) est décoché par défaut. */
    themeVisible: Boolean = true,
    loadStyle: suspend () -> ShareStyle,
    saveStyle: (ShareStyle) -> Unit,
    onExport: (suspend () -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalDuck.current
    val context = LocalContext.current
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    DuckSheet("Exporter en image", onClose) {
        var styleOrNull by remember { mutableStateOf<ShareStyle?>(null) }
        LaunchedEffect(Unit) { if (styleOrNull == null) styleOrNull = loadStyle() }
        val style = styleOrNull ?: ShareStyle()
        // [saveStyle] écrit hors du tiroir : le fermer n'annule jamais l'enregistrement.
        val setStyle = { v: ShareStyle -> styleOrNull = v; saveStyle(v) }
        // Aperçus calculés hors du fil principal, recalculés à chaque changement de réglage ;
        // la clé (style, problème) permet de réutiliser le bitmap à l'export s'il correspond encore.
        // Un rendu en échec donne (clé, null) : l'aperçu affiche alors « indisponible » au lieu de tourner sans fin.
        // « Inclure l'indice » (énoncé) : choix de ce tiroir, l'indice est inclus par défaut.
        var includeTip by remember { mutableStateOf(themeVisible) }
        // Aperçu de la solution démasqué : il n'est calculé (et donc présent en mémoire) qu'à partir de là, ou une fois résolu.
        var revealed by remember { mutableStateOf(false) }
        val showSolution = solved || revealed
        val problemShot by produceState<Pair<Any, Bitmap?>?>(null, styleOrNull, p.id, includeTip) {
            if (styleOrNull == null) return@produceState
            val key = Triple(style, p.id, includeTip)
            value = key to withContext(Dispatchers.Default) { runCatching { ShareCard.render(context, p, line.positions[1], playerWhite, line.moves[0], style, includeTip) }.getOrNull() }
        }
        val solutionShot by produceState<Pair<Any, Bitmap?>?>(null, styleOrNull, p.id, showSolution) {
            if (styleOrNull == null || !showSolution) return@produceState
            val key = style to p.id
            value = key to withContext(Dispatchers.Default) { runCatching { ShareCard.renderSolution(context, p, style) }.getOrNull() }
        }
        val key = style to p.id
        val problemDone = problemShot?.takeIf { it.first == Triple(style, p.id, includeTip) }
        val solutionDone = solutionShot?.takeIf { it.first == key }
        val problemReady = problemDone?.second
        val solutionReady = solutionDone?.second
        val problemPreview = remember(problemReady) { problemReady?.asImageBitmap() }
        val solutionPreview = remember(solutionReady) { solutionReady?.asImageBitmap() }
        val failed = false to "L'image n'a pas pu être enregistrée."
        val shareFailed = false to "L'image n'a pas pu être préparée pour le partage. Réessayez."
        // Image en cours de partage (0 aucune, 1 problème, 2 solution) : le tiroir reste ouvert jusqu'au sélecteur.
        var sharing by remember { mutableIntStateOf(0) }
        // Export lancé dans la portée de l'écran (survit à la fermeture du tiroir), un seul à la fois.
        fun export(block: suspend () -> Unit) = onExport(block)
        fun share(which: Int, send: suspend () -> Boolean) {
            status = null; sharing = which
            export { val ok = runCatchingSuspend { send() }.getOrDefault(false); sharing = 0; if (ok) onClose() else status = shareFailed }
        }
        ExportOption("Format", listOf("Portrait 4:5" to (style.format == ShareFormat.PORTRAIT), "Carré 1:1" to (style.format == ShareFormat.SQUARE))) { i ->
            setStyle(style.copy(format = if (i == 0) ShareFormat.PORTRAIT else ShareFormat.SQUARE))
        }
        Text(
            if (style.format == ShareFormat.PORTRAIT) "Idéal pour WhatsApp et les messageries : affiché en entier, sans recadrage."
            else "Idéal pour une publication Instagram ou un aperçu compact.",
            color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp),
        )
        ExportOption("Fond", listOf("Sombre" to !style.light, "Clair" to style.light)) { i -> setStyle(style.copy(light = i == 1)) }
        ExportOption("Indice", listOf("Inclure" to includeTip, "Sans" to !includeTip)) { i -> includeTip = i == 0 }
        ExportCard(
            "Le problème", if (includeTip) "L'énoncé seul : le défi, l'échiquier, la difficulté et un indice — sans la solution."
            else "L'énoncé seul : le défi, l'échiquier et la difficulté — sans indice ni solution.", c.blue, Icons.Rounded.Extension, problemPreview,
            enabled = !exporting, failed = problemDone != null && problemReady == null, busy = exporting && sharing == 1,
            onShare = { share(1) { ShareCard.share(context, p, line.positions[1], playerWhite, line.moves[0], style, problemReady, includeTip) } },
            onSave = { status = null; export { status = ShareCard.saveProblem(context, p, line.positions[1], playerWhite, line.moves[0], style, problemReady, includeTip)?.let { true to it } ?: failed } },
        )
        ExportCard(
            "La solution", "Une carte par coup : flèches, échecs (+), mat (#), pièces prises et conclusion.", c.green, Icons.Rounded.CheckCircle, solutionPreview, masked = !showSolution, onReveal = { revealed = true },
            enabled = !exporting, failed = solutionDone != null && solutionReady == null, busy = exporting && sharing == 2,
            onShare = { share(2) { ShareCard.shareSolution(context, p, style, solutionReady) } },
            onSave = { status = null; export { status = ShareCard.saveSolution(context, p, style, solutionReady)?.let { true to it } ?: failed } },
        )
        status?.let { (ok, msg) ->
            Row(Modifier.padding(top = 4.dp, bottom = 6.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, contentDescription = null, tint = if (ok) c.greenText else c.redText, modifier = Modifier.size(18.dp))
                Text(msg, color = if (ok) c.greenText else c.redText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
