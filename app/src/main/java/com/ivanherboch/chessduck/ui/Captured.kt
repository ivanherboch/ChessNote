package com.ivanherboch.chessduck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/**
 * Barre des prises, placée juste sous l'échiquier : pièces noires capturées à gauche,
 * pièces blanches capturées à droite, et avantage matériel (« +3 ») du côté du camp qui mène.
 *
 * Le chevauchement des pièces identiques est calculé d'après la largeur disponible : la barre
 * contient toujours tout, même si les 30 pièces (hors rois) ont été capturées, sur téléphone comme sur tablette.
 */
@Composable
fun CapturedBar(position: Position, modifier: Modifier = Modifier, kid: Boolean = false) {
    val c = LocalDuck.current
    val images = rememberPieceImages(kid)
    val measurer = rememberTextMeasurer()
    val font = LocalDuckFont.current
    val m = materialOf(position)
    val shape = RoundedCornerShape(if (kid) 16.dp else 8.dp)
    val scoreColor = if (kid) Color(0xFF1F3B57) else c.text
    Canvas(
        modifier.fillMaxWidth().height(if (kid) 34.dp else 28.dp).clip(shape).background(if (kid) Color.White else c.surface)
            .then(if (kid) Modifier.border(2.5.dp, Color(0xFF9CC9F0), shape) else Modifier),
    ) {
        val pad = (if (kid) 12.dp else 8.dp).toPx()
        val avail = size.width - 2 * pad
        val left = m.blackLost.groupBy { it }.values.toList()
        val right = m.whiteLost.groupBy { it }.values.toList()
        val score = if (m.diff != 0) measurer.measure("+${abs(m.diff)}", TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = font, color = scoreColor)) else null
        val scoreW = if (score != null) score.size.width + 6.dp.toPx() else 0f
        val middle = 10.dp.toPx()

        var piece = (if (kid) 22.dp else 20.dp).toPx()
        var gap = 4.dp.toPx()
        var step = piece - 5.dp.toPx() // chevauchement léger par défaut
        val groups = left.size + right.size
        val extra = left.sumOf { it.size - 1 } + right.sumOf { it.size - 1 }
        val gaps = (left.size - 1).coerceAtLeast(0) + (right.size - 1).coerceAtLeast(0)
        fun total() = groups * piece + extra * step + gaps * gap + scoreW + middle
        if (total() > avail && extra > 0) {
            // On resserre d'abord les pièces identiques, sans descendre sous un décalage encore lisible.
            step = ((avail - (groups * piece + gaps * gap + scoreW + middle)) / extra).coerceIn(piece * 0.24f, step)
        }
        if (total() > avail) gap = 2.dp.toPx()
        if (total() > avail) {
            // Dernier recours : réduire légèrement l'ensemble.
            val k = (avail - scoreW - middle) / (total() - scoreW - middle)
            piece *= k; step *= k; gap *= k
        }
        val top = (size.height - piece) / 2
        val d = piece.roundToInt()
        fun drawPiece(p: Int, x: Float) {
            val bmp = images[p] ?: return
            // Pièces noires sur fond sombre : halo clair. Pièces blanches sur fond clair : halo sombre.
            val halo = when {
                c.dark && !isWhite(p) -> Color.White.copy(alpha = 0.8f)
                !c.dark && isWhite(p) -> Color.Black.copy(alpha = 0.6f)
                else -> null
            }
            fun at(dx: Float, dy: Float, filter: ColorFilter?) = drawImage(
                bmp, dstOffset = IntOffset((x + dx).roundToInt(), (top + dy).roundToInt()), dstSize = IntSize(d, d), colorFilter = filter, filterQuality = FilterQuality.High,
            )
            if (halo != null) {
                val r = 1.2.dp.toPx()
                val tint = ColorFilter.tint(halo)
                for ((dx, dy) in listOf(-r to 0f, r to 0f, 0f to -r, 0f to r, -r * 0.7f to -r * 0.7f, r * 0.7f to -r * 0.7f, -r * 0.7f to r * 0.7f, r * 0.7f to r * 0.7f)) at(dx, dy, tint)
            }
            at(0f, 0f, null)
        }
        fun width(gs: List<List<Int>>) = gs.sumOf { (piece + (it.size - 1) * step).toDouble() }.toFloat() + (gs.size - 1).coerceAtLeast(0) * gap
        fun drawGroups(gs: List<List<Int>>, start: Float) {
            var x = start
            for (g in gs) {
                for (p in g) { drawPiece(p, x); x += step }
                x += piece - step + gap
            }
        }
        // Gauche : pièces noires capturées (+ score si les Blancs mènent)
        drawGroups(left, pad)
        if (score != null && m.diff > 0) drawText(score, topLeft = Offset(pad + width(left) + (if (left.isEmpty()) 0f else 6.dp.toPx()), (size.height - score.size.height) / 2))
        // Droite : pièces blanches capturées (+ score si les Noirs mènent)
        val rightStart = size.width - pad - width(right)
        drawGroups(right, rightStart)
        if (score != null && m.diff < 0) drawText(score, topLeft = Offset(rightStart - score.size.width - (if (right.isEmpty()) 0f else 6.dp.toPx()), (size.height - score.size.height) / 2))
    }
}
