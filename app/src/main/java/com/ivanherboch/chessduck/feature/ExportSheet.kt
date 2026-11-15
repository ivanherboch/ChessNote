package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.ui.DuckSegmentedRow
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
import com.ivanherboch.chessduck.ChessDuckApp
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.LocalDuck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
