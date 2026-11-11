package com.ivanherboch.chessduck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

/** Miniature d'une pièce (même image que sur l'échiquier) avec un contour contrasté optionnel. */
@Composable
private fun MiniPiece(img: ImageBitmap?, size: Dp, halo: Color?) {
    Canvas(Modifier.size(size)) {
        val bmp = img ?: return@Canvas
        val d = this.size.width.roundToInt()
        fun draw(dx: Float, dy: Float, filter: ColorFilter?) = drawImage(
            bmp, dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()), dstSize = IntSize(d, d), colorFilter = filter, filterQuality = FilterQuality.High,
        )
        if (halo != null) {
            // Équivalent d'un « drop-shadow(0 0 1.5px …) » : la silhouette teintée, décalée tout autour.
            val r = 1.2.dp.toPx()
            val tint = ColorFilter.tint(halo)
            for ((dx, dy) in listOf(-r to 0f, r to 0f, 0f to -r, 0f to r, -r * 0.7f to -r * 0.7f, r * 0.7f to -r * 0.7f, -r * 0.7f to r * 0.7f, r * 0.7f to r * 0.7f)) draw(dx, dy, tint)
        }
        draw(0f, 0f, null)
    }
}

@Composable
private fun PieceGroups(pieces: List<Int>, images: Array<ImageBitmap?>, size: Dp, haloFor: (Int) -> Color?) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Les pièces identiques se chevauchent légèrement ; un petit écart sépare les types.
        for (group in pieces.groupBy { it }.values) {
            Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
                for (piece in group) MiniPiece(images[piece], size, haloFor(piece))
            }
        }
    }
}

/**
 * Barre des prises, placée juste sous l'échiquier : pièces noires capturées à gauche,
 * pièces blanches capturées à droite, et avantage matériel (« +3 ») du côté du camp qui mène.
 */
@Composable
fun CapturedBar(position: Position, modifier: Modifier = Modifier, kid: Boolean = false) {
    val c = LocalDuck.current
    val images = rememberPieceImages(kid)
    val m = materialOf(position)
    // Pièces noires sur fond sombre : halo clair. Pièces blanches sur fond clair : halo sombre.
    val haloFor: (Int) -> Color? = { piece ->
        when {
            c.dark && !isWhite(piece) -> Color.White.copy(alpha = 0.8f)
            !c.dark && isWhite(piece) -> Color.Black.copy(alpha = 0.6f)
            else -> null
        }
    }
    val shape = RoundedCornerShape(if (kid) 16.dp else 8.dp)
    val scoreColor = if (kid) Color(0xFF1F3B57) else c.text
    Row(
        modifier.fillMaxWidth().height(if (kid) 34.dp else 28.dp).clip(shape).background(if (kid) Color.White else c.surface)
            .then(if (kid) Modifier.border(2.5.dp, Color(0xFF9CC9F0), shape) else Modifier)
            .padding(horizontal = if (kid) 12.dp else 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        val size = if (kid) 22.dp else 20.dp
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PieceGroups(m.blackLost, images, size, haloFor)
            if (m.diff > 0) AutoText("+${m.diff}", color = scoreColor, maxSize = 14.sp, weight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (m.diff < 0) AutoText("+${-m.diff}", color = scoreColor, maxSize = 14.sp, weight = FontWeight.Bold)
            PieceGroups(m.whiteLost, images, size, haloFor)
        }
    }
}
