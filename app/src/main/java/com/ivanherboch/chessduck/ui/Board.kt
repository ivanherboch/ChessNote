package com.ivanherboch.chessduck.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivanherboch.chessduck.R
import com.ivanherboch.chessduck.chess.BISHOP
import com.ivanherboch.chessduck.chess.BLACK
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.QUEEN
import com.ivanherboch.chessduck.chess.ROOK
import com.ivanherboch.chessduck.chess.fileOf
import com.ivanherboch.chessduck.chess.isWhite
import com.ivanherboch.chessduck.chess.rankOf
import com.ivanherboch.chessduck.chess.sq
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Arrow(val from: Int, val to: Int, val color: Color)
data class Slide(val from: Int, val to: Int)

/** Glissement de pièce(s) à animer : les pièces présentes en [Slide.to] arrivent de [Slide.from]. */
data class BoardAnim(val id: Int, val slides: List<Slide>)
data class Badge(val sq: Int, val text: String, val color: Color)

private var animCounter = 0
fun nextAnimId() = ++animCounter

/** Animation du coup [m] joué depuis [before] (tour comprise pour un roque). */
fun moveAnim(before: Position, m: Move): BoardAnim {
    val slides = arrayListOf(Slide(m.from, m.to))
    if (before.isCastle(m)) {
        if (fileOf(m.to) == 6) slides.add(Slide(m.to + 1, m.to - 1)) else slides.add(Slide(m.to - 2, m.to + 1))
    }
    return BoardAnim(nextAnimId(), slides)
}

/** Animation inverse (retour en arrière d'un coup). */
fun undoAnim(before: Position, m: Move): BoardAnim =
    BoardAnim(nextAnimId(), moveAnim(before, m).slides.map { Slide(it.to, it.from) })

val CaptureBadgeColor = Color(0xFF475569)

/** Badge discret ✕ sur la case d'arrivée d'une prise. */
fun captureBadge(before: Position, m: Move): Badge? = if (before.isCapture(m)) Badge(m.to, "✕", CaptureBadgeColor) else null

val PIECE_RES = mapOf(
    KING to R.drawable.p_wk, QUEEN to R.drawable.p_wq, ROOK to R.drawable.p_wr,
    BISHOP to R.drawable.p_wb, KNIGHT to R.drawable.p_wn, PAWN to R.drawable.p_wp,
    KING + BLACK to R.drawable.p_bk, QUEEN + BLACK to R.drawable.p_bq, ROOK + BLACK to R.drawable.p_br,
    BISHOP + BLACK to R.drawable.p_bb, KNIGHT + BLACK to R.drawable.p_bn, PAWN + BLACK to R.drawable.p_bp,
)

@Composable
fun rememberPieceImages(): Array<ImageBitmap?> {
    val arr = arrayOfNulls<ImageBitmap>(16)
    for ((piece, res) in PIECE_RES) arr[piece] = ImageBitmap.imageResource(res)
    return arr
}

/**
 * Échiquier interactif : toucher-relâcher persistant, glisser-déposer avec pièce surélevée,
 * coups animés (180 ms), flèches, surlignages et badges. Rien ne recouvre jamais la grille
 * hormis le sélecteur ponctuel de promotion.
 */
@Composable
fun ChessBoard(
    position: Position,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    interactive: Boolean = false,
    lastMove: Move? = null,
    anim: BoardAnim? = null,
    arrows: List<Arrow> = emptyList(),
    marks: List<Pair<Int, Color>> = emptyList(),
    badges: List<Badge> = emptyList(),
    onMove: (Move) -> Unit = {},
) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val images = rememberPieceImages()
    val measurer = rememberTextMeasurer()
    val slop = LocalViewConfiguration.current.touchSlop

    var selected by remember(position) { mutableIntStateOf(-1) }
    var dragFrom by remember(position) { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var promo by remember(position) { mutableStateOf<Pair<Int, Int>?>(null) }
    val skip = remember { arrayOfNulls<Move>(1) }

    val progress = remember { Animatable(1f) }
    var runningId by remember { mutableIntStateOf(anim?.id ?: -1) }
    val pending = anim != null && runningId != anim.id
    val skipping = pending && skip[0]?.let { s -> anim!!.slides.any { it.from == s.from && it.to == s.to } } == true
    LaunchedEffect(anim?.id) {
        val a = anim ?: return@LaunchedEffect
        if (runningId == a.id) return@LaunchedEffect
        if (skipping) {
            skip[0] = null
            progress.snapTo(1f)
            runningId = a.id
        } else {
            progress.snapTo(0f)
            runningId = a.id
            progress.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        }
        fb?.move()
    }
    val t = when {
        skipping -> 1f
        pending -> 0f
        else -> progress.value
    }

    fun tryMove(from: Int, to: Int, viaDrag: Boolean): Boolean {
        val cands = position.legalMoves.filter { it.from == from && it.to == to }
        if (cands.isEmpty()) return false
        if (cands.size > 1) {
            promo = from to to
        } else {
            if (viaDrag) skip[0] = cands[0]
            selected = -1
            onMove(cands[0])
        }
        return true
    }

    val targets = if (selected >= 0 && interactive) position.legalFrom(selected) else emptyList()

    Box(modifier.clip(RoundedCornerShape(4.dp))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(position, flipped, interactive) {
                    if (!interactive) return@pointerInput
                    val s = size.width / 8f
                    fun at(o: Offset): Int {
                        val col = (o.x / s).toInt().coerceIn(0, 7)
                        val row = (o.y / s).toInt().coerceIn(0, 7)
                        return if (flipped) sq(7 - col, row) else sq(col, 7 - row)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (promo != null) return@awaitEachGesture
                        val sqDown = at(down.position)
                        val piece = position[sqDown]
                        val own = piece != 0 && isWhite(piece) == position.whiteToMove
                        var dragging = false
                        var last = down.position
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            last = ch.position
                            if (own && !dragging && (last - down.position).getDistance() > slop) {
                                dragging = true
                                selected = sqDown
                                dragFrom = sqDown
                            }
                            if (dragging) {
                                dragPos = last
                                ch.consume()
                            }
                        }
                        if (dragging) {
                            val inside = last.x >= 0 && last.y >= 0 && last.x <= size.width && last.y <= size.height
                            val target = at(last)
                            dragFrom = -1
                            if (inside && target != sqDown) tryMove(sqDown, target, true)
                        } else if (selected >= 0 && selected != sqDown && tryMove(selected, sqDown, false)) {
                            // coup joué par toucher-relâcher
                        } else if (own) {
                            selected = if (selected == sqDown) -1 else sqDown
                        } else {
                            selected = -1
                        }
                    }
                }
        ) {
            val s = size.width / 8f
            fun topLeft(q: Int): Offset {
                val col = if (flipped) 7 - fileOf(q) else fileOf(q)
                val row = if (flipped) rankOf(q) else 7 - rankOf(q)
                return Offset(col * s, row * s)
            }
            fun center(q: Int) = topLeft(q) + Offset(s / 2, s / 2)
            val cell = Size(s, s)

            // Cases
            for (q in 0 until 64) {
                val light = (fileOf(q) + rankOf(q)) % 2 == 1
                drawRect(if (light) c.squareLight else c.squareDark, topLeft(q), cell)
            }
            // Dernier coup
            lastMove?.let {
                drawRect(Color(0x66F7EC5E), topLeft(it.from), cell)
                drawRect(Color(0x66F7EC5E), topLeft(it.to), cell)
            }
            for ((q, col) in marks) drawRect(col.copy(alpha = 0.62f), topLeft(q), cell)
            // Roi en échec
            if (position.inCheck) {
                val k = position.kingSquare(position.whiteToMove)
                if (k >= 0) drawRect(
                    Brush.radialGradient(listOf(Color(0xE6EF4444), Color(0x00EF4444)), center(k), s * 0.62f), topLeft(k), cell,
                )
            }
            // Coordonnées intégrées en bordure des cases
            val coordStyle = TextStyle(fontSize = (s * 0.21f).toSp(), fontWeight = FontWeight.Bold)
            for (i in 0..7) {
                val fileSq = if (flipped) sq(i, 7) else sq(i, 0)
                val rankSq = if (flipped) sq(7, i) else sq(0, i)
                val fl = measurer.measure(('a' + i).toString(), coordStyle)
                val lightF = (fileOf(fileSq) + rankOf(fileSq)) % 2 == 1
                drawText(fl, if (lightF) c.squareDark else c.squareLight, topLeft(fileSq) + Offset(s - fl.size.width - s * 0.05f, s - fl.size.height - s * 0.01f))
                val rl = measurer.measure(('1' + i).toString(), coordStyle)
                val lightR = (fileOf(rankSq) + rankOf(rankSq)) % 2 == 1
                drawText(rl, if (lightR) c.squareDark else c.squareLight, topLeft(rankSq) + Offset(s * 0.05f, s * 0.01f))
            }
            // Sélection et case survolée
            if (selected >= 0 && interactive) drawRect(Color(0x8010B981), topLeft(selected), cell)
            if (dragFrom >= 0) {
                val col = (dragPos.x / s).toInt().coerceIn(0, 7)
                val row = (dragPos.y / s).toInt().coerceIn(0, 7)
                val hover = if (flipped) sq(7 - col, row) else sq(col, 7 - row)
                if (targets.any { it.to == hover }) {
                    drawRect(Color(0x9910B981), topLeft(hover), cell)
                    drawCircle(Color(0x3310B981), s * 0.95f, center(hover))
                }
            }
            // Pièces
            fun drawPiece(piece: Int, at: Offset, scale: Float = 1f) {
                val img = images[piece] ?: return
                val d = (s * scale).roundToInt()
                drawImage(
                    img,
                    dstOffset = IntOffset((at.x - (d - s) / 2).roundToInt(), (at.y - (d - s) / 2).roundToInt()),
                    dstSize = IntSize(d, d),
                    filterQuality = FilterQuality.High,
                )
            }
            val sliding = if (anim != null && t < 1f) anim.slides else emptyList()
            for (q in 0 until 64) {
                val piece = position[q]
                if (piece == 0 || q == dragFrom || sliding.any { it.to == q }) continue
                drawPiece(piece, topLeft(q))
            }
            // Destinations légales : pastilles (cases vides) et anneaux (prises)
            for (m in targets.distinctBy { it.to }) {
                if (position.isCapture(m)) drawCircle(Color(0xB310B981), s * 0.43f, center(m.to), style = Stroke(s * 0.09f))
                else drawCircle(Color(0xB310B981), s * 0.16f, center(m.to))
            }
            for (sl in sliding) {
                val piece = position[sl.to]
                if (piece != 0) drawPiece(piece, topLeft(sl.from) + (topLeft(sl.to) - topLeft(sl.from)) * t)
            }
            for (a in arrows) drawArrow(a, s, ::center)
            // Badges (✕ de prise, annotations) dans le coin supérieur droit de la case
            val seen = HashSet<Int>()
            for (b in badges) {
                val right = seen.add(b.sq)
                drawBadge(measurer, b, topLeft(b.sq) + Offset(if (right) s * 0.80f else s * 0.20f, s * 0.20f), s)
            }
            // Pièce en cours de glissement : surélevée au-dessus du doigt, avec ombre portée
            if (dragFrom >= 0) {
                val piece = position[dragFrom]
                val at = dragPos - Offset(s / 2, s * 1.35f)
                drawOval(Color(0x40000000), at + Offset(s * 0.12f, s * 1.02f), Size(s * 0.76f, s * 0.2f))
                drawPiece(piece, at, 1.3f)
            }
        }
        promo?.let { (from, to) ->
            val white = position.whiteToMove
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000)).clickable { promo = null },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(c.surface).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (type in intArrayOf(QUEEN, ROOK, BISHOP, KNIGHT)) {
                        val res = PIECE_RES.getValue(if (white) type else type + BLACK)
                        Image(
                            painterResource(res), null,
                            Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)).background(c.squareLight)
                                .clickable { promo = null; selected = -1; onMove(Move(from, to, type)) },
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBadge(measurer: TextMeasurer, b: Badge, center: Offset, s: Float) {
    val r = s * 0.19f
    drawCircle(Color(0x55000000), r * 1.08f, center + Offset(0f, r * 0.12f))
    drawCircle(b.color, r, center)
    val size = if (b.text.length > 1) r * 1.05f else r * 1.25f
    val layout = measurer.measure(b.text, TextStyle(fontSize = size.toSp(), fontWeight = FontWeight.Black))
    drawText(layout, Color.White, center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}

/** Flèche directionnelle ; coudée en « L » pour un saut de Cavalier. */
private fun DrawScope.drawArrow(a: Arrow, s: Float, center: (Int) -> Offset) {
    val df = abs(fileOf(a.to) - fileOf(a.from))
    val dr = abs(rankOf(a.to) - rankOf(a.from))
    val pts = arrayListOf(center(a.from))
    if ((df == 1 && dr == 2) || (df == 2 && dr == 1)) {
        pts.add(if (dr == 2) center(sq(fileOf(a.from), rankOf(a.to))) else center(sq(fileOf(a.to), rankOf(a.from))))
    }
    pts.add(center(a.to))
    val color = a.color.copy(alpha = 0.85f)
    val width = s * 0.17f
    val head = s * 0.42f
    fun unit(p: Offset, q: Offset): Offset {
        val d = q - p
        val len = sqrt(d.x * d.x + d.y * d.y).coerceAtLeast(0.001f)
        return Offset(d.x / len, d.y / len)
    }
    val n = pts.size
    val uStart = unit(pts[0], pts[1])
    val uEnd = unit(pts[n - 2], pts[n - 1])
    val start = pts[0] + uStart * (s * 0.28f)
    val tip = pts[n - 1] + uEnd * (s * 0.12f)
    val shaftEnd = tip - uEnd * (head * 0.85f)
    val path = Path().apply {
        moveTo(start.x, start.y)
        for (i in 1 until n - 1) lineTo(pts[i].x, pts[i].y)
        lineTo(shaftEnd.x, shaftEnd.y)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val ang = atan2(uEnd.y, uEnd.x)
    val spread = 0.52f
    val headPath = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - head * cos(ang - spread), tip.y - head * sin(ang - spread))
        lineTo(tip.x - head * cos(ang + spread), tip.y - head * sin(ang + spread))
        close()
    }
    drawPath(headPath, color)
}

/** Jauge latérale d'évaluation ([whiteShare] : part des Blancs entre 0 et 1). */
@Composable
fun EvalBar(whiteShare: Float, flipped: Boolean, modifier: Modifier = Modifier) {
    val share by animateFloatAsState(whiteShare.coerceIn(0.03f, 0.97f), tween(350), label = "eval")
    Canvas(modifier.fillMaxHeight().width(12.dp).clip(RoundedCornerShape(4.dp))) {
        drawRect(Color(0xFF1F2937))
        val h = size.height * share
        drawRect(Color(0xFFF1F5F9), Offset(0f, if (flipped) 0f else size.height - h), Size(size.width, h))
        drawRect(Color(0xFFF59E0B), Offset(0f, size.height / 2 - 1f), Size(size.width, 2f))
    }
}

/** Échiquier avec jauge d'évaluation optionnelle à gauche. */
@Composable
fun BoardRow(evalShare: Float?, flipped: Boolean, board: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (evalShare != null) {
            EvalBar(evalShare, flipped, Modifier.padding(end = 3.dp))
        }
        Box(Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
            board(Modifier.aspectRatio(1f))
        }
    }
}
