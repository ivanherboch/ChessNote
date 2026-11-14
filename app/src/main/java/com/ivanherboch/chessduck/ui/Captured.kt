package com.ivanherboch.chessduck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivanherboch.chessduck.chess.BISHOP
import com.ivanherboch.chessduck.chess.BLACK
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.PIECE_VALUES
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.QUEEN
import com.ivanherboch.chessduck.chess.ROOK
import com.ivanherboch.chessduck.chess.isWhite
import com.ivanherboch.chessduck.chess.pieceType
import kotlin.math.abs
import kotlin.math.roundToInt

/** Bilan matériel d'une position : pièces capturées de chaque camp et écart (Blancs − Noirs). */
class Material(val blackLost: List<Int>, val whiteLost: List<Int>, val diff: Int)

private val ORDER = intArrayOf(PAWN, KNIGHT, BISHOP, ROOK, QUEEN)
private val START_COUNT = intArrayOf(0, 8, 2, 2, 2, 1, 1)

/**
 * Pièces manquantes par rapport à l'effectif initial, triées par valeur croissante (P, N, B, R, Q),
 * et différence de matériel avec P = 1, N = 3, B = 3, R = 5, Q = 9.
 */
fun materialOf(p: Position): Material {
    val white = IntArray(7)
    val black = IntArray(7)
    var diff = 0
    for (q in 0 until 64) {
        val piece = p[q]
        if (piece == 0) continue
        val t = pieceType(piece)
        if (isWhite(piece)) { white[t]++; if (t != 6) diff += PIECE_VALUES[t] } else { black[t]++; if (t != 6) diff -= PIECE_VALUES[t] }
    }
    fun lost(counts: IntArray, color: Int) = ORDER.flatMap { t -> List((START_COUNT[t] - counts[t]).coerceAtLeast(0)) { t + color } }
    return Material(lost(black, BLACK), lost(white, 0), diff)
}

private val PLURAL = arrayOf("", "pions", "cavaliers", "fous", "tours", "dames", "rois")
private val SINGULAR = arrayOf("", "pion", "cavalier", "fou", "tour", "dame", "roi")

/** Résumé TalkBack d'une liste de pièces : « 2 pions, 1 cavalier ». */
fun describePieces(pieces: List<Int>): String =
    if (pieces.isEmpty()) "aucune" else pieces.groupBy { pieceType(it) }.entries.joinToString(", ") { (t, l) -> "${l.size} ${if (l.size > 1) PLURAL[t] else SINGULAR[t]}" }

/**
 * Dessine des pièces capturées en miniature (mêmes images que l'échiquier), les pièces identiques
 * se chevauchant juste assez pour toujours tenir dans la largeur, avec l'avantage « +N » si [lead] > 0.
 */
private fun DrawScope.drawCaptured(
    pieces: List<Int>, lead: TextLayoutResult?, images: Array<ImageBitmap?>, dark: Boolean,
    start: Float, avail: Float, pieceSize: Float, alignEnd: Boolean,
) {
    val groups = pieces.groupBy { it }.values.toList()
    val scoreW = if (lead != null) lead.size.width + 6.dp.toPx() else 0f
    var piece = pieceSize
    var gap = 4.dp.toPx()
    var step = piece - 5.dp.toPx()
    val extra = groups.sumOf { it.size - 1 }
    val gaps = (groups.size - 1).coerceAtLeast(0)
    fun total() = groups.size * piece + extra * step + gaps * gap + scoreW
    if (total() > avail && extra > 0) step = ((avail - (groups.size * piece + gaps * gap + scoreW)) / extra).coerceIn(piece * 0.24f, step)
    if (total() > avail) gap = 2.dp.toPx()
    if (total() > avail && total() - scoreW > 0) {
        val k = ((avail - scoreW) / (total() - scoreW)).coerceAtMost(1f)
        piece *= k; step *= k; gap *= k
    }
    val width = (if (groups.isEmpty()) 0f else total() - scoreW)
    var x = if (alignEnd) start + avail - width - scoreW else start
    val top = (size.height - piece) / 2
    val d = piece.roundToInt()
    fun drawPiece(p: Int, px: Float) {
        val bmp = images[p] ?: return
        // Pièces noires sur fond sombre : halo clair. Pièces blanches sur fond clair : halo sombre.
        val halo = when {
            dark && !isWhite(p) -> Color.White.copy(alpha = 0.8f)
            !dark && isWhite(p) -> Color.Black.copy(alpha = 0.6f)
            else -> null
        }
        fun at(dx: Float, dy: Float, filter: ColorFilter?) = drawImage(
            bmp, dstOffset = IntOffset((px + dx).roundToInt(), (top + dy).roundToInt()), dstSize = IntSize(d, d), colorFilter = filter, filterQuality = FilterQuality.High,
        )
        if (halo != null) {
            val r = 1.2.dp.toPx()
            val tint = ColorFilter.tint(halo)
            for ((dx, dy) in listOf(-r to 0f, r to 0f, 0f to -r, 0f to r, -r * 0.7f to -r * 0.7f, r * 0.7f to -r * 0.7f, -r * 0.7f to r * 0.7f, r * 0.7f to r * 0.7f)) at(dx, dy, tint)
        }
        at(0f, 0f, null)
    }
    for (g in groups) {
        for (p in g) { drawPiece(p, x); x += step }
        x += piece - step + gap
    }
    if (lead != null) {
        val lx = if (groups.isEmpty()) (if (alignEnd) start + avail - lead.size.width else start) else x - gap + 6.dp.toPx()
        drawText(lead, topLeft = Offset(lx, (size.height - lead.size.height) / 2))
    }
}

/**
 * Barre des prises du mode enfant : pièces noires capturées à gauche, blanches à droite, avantage
 * matériel du côté du camp qui mène. Rien n'est affiché tant qu'aucune pièce n'a été prise.
 */
@Composable
fun CapturedBar(position: Position, modifier: Modifier = Modifier, kid: Boolean = false) {
    val c = LocalDuck.current
    val m = materialOf(position)
    if (m.blackLost.isEmpty() && m.whiteLost.isEmpty()) return
    val images = rememberPieceImages(kid)
    val measurer = rememberTextMeasurer()
    val font = LocalDuckFont.current
    val shape = RoundedCornerShape(if (kid) 16.dp else 8.dp)
    val desc = "Pièces prises par les Blancs : ${describePieces(m.blackLost)}. Pièces prises par les Noirs : ${describePieces(m.whiteLost)}." +
        if (m.diff != 0) " Avantage ${if (m.diff > 0) "Blancs" else "Noirs"} +${abs(m.diff)}." else " Matériel égal."
    Canvas(
        modifier.fillMaxWidth().height(if (kid) 34.dp else 28.dp).clip(shape).background(if (kid) Color.White else c.surface)
            .then(if (kid) Modifier.border(2.5.dp, c.outline, shape) else Modifier)
            .semantics { contentDescription = desc },
    ) {
        val pad = (if (kid) 12.dp else 8.dp).toPx()
        val half = (size.width - 2 * pad - 10.dp.toPx()) / 2
        val style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = font, color = c.text, fontFeatureSettings = TNUM)
        val lead = if (m.diff != 0) measurer.measure("+${abs(m.diff)}", style) else null
        val pieceSize = (if (kid) 22.dp else 20.dp).toPx()
        drawCaptured(m.blackLost, lead.takeIf { m.diff > 0 }, images, c.dark, pad, half, pieceSize, alignEnd = false)
        drawCaptured(m.whiteLost, lead.takeIf { m.diff < 0 }, images, c.dark, size.width - pad - half, half, pieceSize, alignEnd = true)
    }
}

/** Disque de la couleur d'un camp (blanc ou noir), cerclé pour rester visible sur tout fond. */
@Composable
fun SideDot(white: Boolean, modifier: Modifier = Modifier) {
    val c = LocalDuck.current
    Box(
        modifier.size(14.dp).clip(CircleShape).background(if (white) Color(0xFFF8FAFC) else Color(0xFF111827))
            .border(1.5.dp, c.outlineStrong, CircleShape),
    )
}

/**
 * Bandeau d'un joueur, placé au-dessus (adversaire) ou au-dessous (joueur) de l'échiquier :
 * couleur, nom, détail (ELO), indicateur « au trait » ou « réfléchit », pièces qu'il a prises et avantage.
 */
@Composable
fun PlayerBar(
    name: String,
    detail: String?,
    white: Boolean,
    position: Position,
    toMove: Boolean,
    modifier: Modifier = Modifier,
    thinking: Boolean = false,
) {
    val c = LocalDuck.current
    val m = materialOf(position)
    val captured = if (white) m.blackLost else m.whiteLost
    val lead = if (white) m.diff else -m.diff
    val images = rememberPieceImages(false)
    val measurer = rememberTextMeasurer()
    val side = if (white) "Blancs" else "Noirs"
    val desc = buildString {
        append("$name, $side")
        if (detail != null) append(", $detail")
        if (thinking) append(", réfléchit") else if (toMove) append(", au trait")
        if (captured.isNotEmpty()) append(". A pris : ${describePieces(captured)}")
        if (lead > 0) append(". Avantage +$lead")
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 34.dp).clearAndSetSemantics { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideDot(white)
        Text(
            name, color = c.text, style = MaterialTheme.typography.labelLarge, maxLines = 2,
            modifier = Modifier.padding(start = 6.dp).widthIn(max = 150.dp),
        )
        if (detail != null) Text(" $detail", color = c.muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        if (thinking) {
            CircularProgressIndicator(Modifier.padding(start = 8.dp).size(14.dp), color = c.goldRole.fill, strokeWidth = 2.dp)
        } else if (toMove) {
            Box(Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(c.greenRole.fill))
        }
        val style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.text, fontFeatureSettings = TNUM)
        Canvas(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp)) {
            val leadText = if (lead > 0) measurer.measure("+$lead", style) else null
            drawCaptured(captured, leadText, images, c.dark, 0f, size.width, 20.dp.toPx(), alignEnd = true)
        }
    }
}
