package com.ivanherboch.chessduck.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.chess.PieceLexiconFr
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.ivanherboch.chessduck.chess.PIECE_NAMES_FR
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.sqName
import com.ivanherboch.chessduck.engine.isMateScore
import com.ivanherboch.chessduck.engine.mateIn
import com.ivanherboch.chessduck.engine.winPercent
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class Arrow(val from: Int, val to: Int, val color: Color)
@Immutable
data class Slide(val from: Int, val to: Int)

/**
 * Glissement de pièce(s) à animer : les pièces présentes en [Slide.to] arrivent de [Slide.from].
 * [capture] et [promotion] nuancent le retour haptique au moment où la pièce se pose.
 */
@Immutable
data class BoardAnim(val id: Int, val slides: List<Slide>, val capture: Boolean = false, val promotion: Boolean = false)
@Immutable
data class Badge(val sq: Int, val text: String, val color: Color)

private val animCounter = AtomicInteger(0)
fun nextAnimId() = animCounter.incrementAndGet()

/** Animation du coup [m] joué depuis [before] (tour comprise pour un roque). */
fun moveAnim(before: Position, m: Move): BoardAnim {
    val slides = arrayListOf(Slide(m.from, m.to))
    if (before.isCastle(m)) {
        if (fileOf(m.to) == 6) slides.add(Slide(m.to + 1, m.to - 1)) else slides.add(Slide(m.to - 2, m.to + 1))
    }
    return BoardAnim(nextAnimId(), slides, capture = before.isCapture(m), promotion = m.promo != 0)
}

/** Animation inverse (retour en arrière d'un coup). */
fun undoAnim(before: Position, m: Move): BoardAnim =
    BoardAnim(nextAnimId(), moveAnim(before, m).slides.map { Slide(it.to, it.from) })

val PIECE_RES = mapOf(
    KING to R.drawable.p_wk, QUEEN to R.drawable.p_wq, ROOK to R.drawable.p_wr,
    BISHOP to R.drawable.p_wb, KNIGHT to R.drawable.p_wn, PAWN to R.drawable.p_wp,
    KING + BLACK to R.drawable.p_bk, QUEEN + BLACK to R.drawable.p_bq, ROOK + BLACK to R.drawable.p_br,
    BISHOP + BLACK to R.drawable.p_bb, KNIGHT + BLACK to R.drawable.p_bn, PAWN + BLACK to R.drawable.p_bp,
)

/** Mode enfant : la Girafe remplace le Cavalier ; toutes les autres pièces restent classiques. */
val KID_PIECE_RES = PIECE_RES + mapOf(KNIGHT to R.drawable.p_wg, KNIGHT + BLACK to R.drawable.p_bg)

@Composable
fun rememberPieceImages(kid: Boolean = false): Array<ImageBitmap?> {
    val resources = LocalContext.current.resources
    // Chargées une seule fois par jeu de pièces (et non à chaque recomposition).
    return remember(kid, resources) {
        val arr = arrayOfNulls<ImageBitmap>(16)
        for ((piece, res) in if (kid) KID_PIECE_RES else PIECE_RES) arr[piece] = ImageBitmap.imageResource(resources, res)
        arr
    }
}

/**
 * Cache des mises en page de texte dessinées dans un Canvas (coordonnées, badges) : chaque libellé n'est
 * mesuré qu'une fois pour un style donné (la taille dépend de la case, donc reste stable entre les images).
 */
class TextLayoutCache(private val measurer: TextMeasurer) {
    private val map = HashMap<Pair<String, TextStyle>, TextLayoutResult>()
    fun get(text: String, style: TextStyle): TextLayoutResult =
        map.getOrPut(text to style) {
            if (map.size > 256) map.clear()
            measurer.measure(text, style)
        }
}

@Composable
fun rememberTextLayoutCache(): TextLayoutCache {
    val measurer = rememberTextMeasurer()
    return remember(measurer) { TextLayoutCache(measurer) }
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
    kid: Boolean = false,
    /** Appelé quand on touche une de ses pièces qui n'a aucun coup légal (clouée, ou roi en échec). */
    onIllegalTouch: ((Int) -> Unit)? = null,
    /**
     * Mode « toucher une case » (exercices) : chaque case touchée, ou activée avec TalkBack, est signalée, qu'elle
     * contienne une pièce ou non ; aucun coup n'est joué.
     */
    onSquareTap: ((Int) -> Unit)? = null,
    onMove: (Move) -> Unit = {},
) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val images = rememberPieceImages(kid)
    val texts = rememberTextLayoutCache()
    val slop = LocalViewConfiguration.current.touchSlop
    val font = LocalDuckFont.current
    val showCoords = LocalShowCoords.current
    val scope = rememberCoroutineScope()
    // Secousse d'une pièce qui ne peut pas bouger.
    val shake = remember { Animatable(0f) }
    var shakeSq by remember { mutableIntStateOf(-1) }
    val shakeJob = remember { arrayOfNulls<Job>(1) }
    fun refuse(sq: Int) {
        // Une nouvelle secousse remplace la précédente (pas d'animations concurrentes sur le même Animatable).
        shakeJob[0]?.cancel()
        shakeSq = sq
        fb?.error()
        onIllegalTouch?.invoke(sq)
        shakeJob[0] = scope.launch {
            try {
                shake.snapTo(0f)
                for (x in floatArrayOf(1f, -1f, 0.7f, -0.7f, 0.35f, 0f)) shake.animateTo(x, tween(45))
            } finally {
                if (shakeSq == sq) shakeSq = -1
            }
        }
    }

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
        // Retour au moment où la pièce se pose (fin du glissement), nuancé selon le coup.
        fb?.move(capture = a.capture, check = position.inCheck, promotion = a.promotion)
    }
    // La progression de l'animation n'est lue que pendant le dessin (phase Draw) : l'animation
    // de 180 ms ne provoque aucune recomposition de l'échiquier ni de sa description TalkBack.
    fun animT() = when {
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

    /** Toucher (ou activation TalkBack) d'une case : jouer vers elle, sélectionner une de ses pièces, ou désélectionner. */
    fun tap(sq: Int) {
        if (selected >= 0 && selected != sq && tryMove(selected, sq, false)) return
        val piece = position[sq]
        if (piece != 0 && isWhite(piece) == position.whiteToMove) {
            if (position.legalFrom(sq).isEmpty()) { selected = -1; refuse(sq) }
            else selected = if (selected == sq) -1 else sq
        } else {
            selected = -1
        }
    }

    val targets = if (selected >= 0 && interactive) position.legalFrom(selected) else emptyList()
    val boardClip = remember { Path() }

    // Pas de découpe sur la boîte : la pièce glissée peut dépasser en haut de l'échiquier (8e rangée) sans être rognée.
    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    // Résumé du plateau ; le détail est porté par les 64 cases (voir BoardCells).
                    contentDescription = "Échiquier, vue des ${if (flipped) "Noirs" else "Blancs"}"
                    stateDescription = "Trait aux ${if (position.whiteToMove) "Blancs" else "Noirs"}" +
                        if (position.isCheckmate) ", échec et mat" else if (position.inCheck) ", échec au roi" else ""
                }
                .pointerInput(position, flipped, interactive, onSquareTap) {
                    val s = size.width / 8f
                    fun at(o: Offset): Int = BoardGeometry.squareAt(o.x, o.y, s, flipped)
                    if (onSquareTap != null) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (waitForUpOrCancellation() != null) { fb?.key(); onSquareTap(at(down.position)) }
                        }
                        return@pointerInput
                    }
                    if (!interactive) return@pointerInput
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
                        if (dragging && position.legalFrom(sqDown).isEmpty()) {
                            dragFrom = -1
                            selected = -1
                            refuse(sqDown)
                        } else if (dragging) {
                            val inside = last.x >= 0 && last.y >= 0 && last.x <= size.width && last.y <= size.height
                            val target = at(last)
                            dragFrom = -1
                            if (inside && target != sqDown) tryMove(sqDown, target, true)
                        } else {
                            tap(sqDown)
                        }
                    }
                }
        ) {
            val s = size.width / 8f
            fun topLeft(q: Int): Offset = Offset(BoardGeometry.column(q, flipped) * s, BoardGeometry.row(q, flipped) * s)
            fun center(q: Int) = topLeft(q) + Offset(s / 2, s / 2)
            val cell = Size(s, s)
            boardClip.rewind()
            boardClip.addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(4.dp.toPx())))
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
            clipPath(boardClip) {
            // Cases
            for (q in 0 until 64) {
                val light = (fileOf(q) + rankOf(q)) % 2 == 1
                drawRect(if (light) c.squareLight else c.squareDark, topLeft(q), cell)
            }
            // Dernier coup
            lastMove?.let {
                drawRect(c.lastMoveTint, topLeft(it.from), cell)
                drawRect(c.lastMoveTint, topLeft(it.to), cell)
            }
            for ((q, col) in marks) drawRect(col.copy(alpha = 0.62f), topLeft(q), cell)
            // Roi en échec
            if (position.inCheck) {
                val k = position.kingSquare(position.whiteToMove)
                if (k >= 0) drawRect(
                    Brush.radialGradient(listOf(c.checkGlow.copy(alpha = 0.9f), c.checkGlow.copy(alpha = 0f)), center(k), s * 0.62f), topLeft(k), cell,
                )
            }
            // Sélection et case survolée
            if (selected >= 0 && interactive) drawRect(c.select.copy(alpha = 0.5f), topLeft(selected), cell)
            if (dragFrom >= 0) {
                val hover = BoardGeometry.squareAt(dragPos.x, dragPos.y, s, flipped)
                if (targets.any { it.to == hover }) {
                    drawRect(c.select.copy(alpha = 0.6f), topLeft(hover), cell)
                    drawCircle(c.select.copy(alpha = 0.2f), s * 0.95f, center(hover))
                }
            }
            // Pièces
            val t = animT()
            val sliding = if (anim != null && t < 1f) anim.slides else emptyList()
            for (q in 0 until 64) {
                val piece = position[q]
                if (piece == 0 || q == dragFrom || sliding.any { it.to == q }) continue
                drawPiece(piece, topLeft(q) + if (q == shakeSq) Offset(shake.value * s * 0.09f, 0f) else Offset.Zero)
            }
            // Coordonnées, dessinées par-dessus les pièces sur une pastille discrète : jamais masquées.
            if (showCoords) {
                val coordStyle = TextStyle(fontSize = (s * 0.19f).toSp(), fontWeight = FontWeight.Bold, fontFamily = font)
                // Pastille dans un coin de la case : lettres en bas à droite, chiffres en haut à gauche.
                fun label(text: String, cell: Offset, bottomRight: Boolean) {
                    val l = texts.get(text, coordStyle)
                    val pad = s * 0.035f
                    val w = l.size.width + pad * 2
                    val h = l.size.height.toFloat()
                    val m = s * 0.03f
                    val x = if (bottomRight) cell.x + s - m - w else cell.x + m
                    val y = if (bottomRight) cell.y + s - m - h else cell.y + m
                    drawRoundRect(c.coordPill, Offset(x, y), Size(w, h), CornerRadius(h / 3, h / 3))
                    drawText(l, c.coordInk, Offset(x + pad, y))
                }
                for (i in 0..7) {
                    val fileSq = if (flipped) sq(7 - i, 7) else sq(i, 0)
                    val rankSq = if (flipped) sq(7, 7 - i) else sq(0, i)
                    label(('a' + fileOf(fileSq)).toString(), topLeft(fileSq), true)
                    label(('1' + rankOf(rankSq)).toString(), topLeft(rankSq), false)
                }
            }
            // Destinations légales : pastilles (cases vides) et anneaux (prises)
            for (m in targets.distinctBy { it.to }) {
                if (position.isCapture(m)) drawCircle(c.select.copy(alpha = 0.7f), s * 0.43f, center(m.to), style = Stroke(s * 0.09f))
                else drawCircle(c.select.copy(alpha = 0.7f), s * 0.16f, center(m.to))
            }
            for (sl in sliding) {
                val piece = position[sl.to]
                if (piece != 0) drawPiece(piece, topLeft(sl.from) + (topLeft(sl.to) - topLeft(sl.from)) * t)
            }
            for (a in arrows) drawArrow(a, s, ::center)
            // Badges d'annotation (!!, !, ?!, ?, ??) dans le coin supérieur droit de la case
            val seen = HashSet<Int>()
            for (b in badges) {
                val right = seen.add(b.sq)
                drawBadge(texts, b, topLeft(b.sq) + Offset(if (right) s * 0.80f else s * 0.20f, s * 0.20f), s)
            }
            }
            // Pièce en cours de glissement : surélevée au-dessus du doigt, avec ombre portée
            if (dragFrom >= 0) {
                val piece = position[dragFrom]
                val at = dragPos - Offset(s / 2, s * 1.35f)
                drawOval(Color(0x40000000), at + Offset(s * 0.12f, s * 1.02f), Size(s * 0.76f, s * 0.2f))
                drawPiece(piece, at, 1.3f)
            }
        }
        // Grille sémantique : 64 cases explorables et activables par TalkBack (le dessin reste dans le Canvas).
        BoardCells(
            position, flipped, lastMove, selected.takeIf { interactive }, targets,
            onSquareTap?.let { f -> { sq: Int -> fb?.key(); f(sq) } } ?: if (interactive) ::tap else null,
        )
        promo?.let { (from, to) -> PromotionPicker(position.whiteToMove, to, flipped, kid, onCancel = { promo = null }) { type ->
            promo = null; selected = -1; onMove(Move(from, to, type))
        } }
    }
}

/**
 * Sélecteur de promotion ancré sur la colonne d'arrivée, contre le bord où arrive le pion : la Dame est la plus
 * proche. Annulable par le bouton Retour ou en touchant le voile. Habillage clair et ludique en mode enfant.
 */
@Composable
private fun PromotionPicker(white: Boolean, to: Int, flipped: Boolean, kid: Boolean, onCancel: () -> Unit, onPick: (Int) -> Unit) {
    val c = LocalDuck.current
    BackHandler(onBack = onCancel)
    BoxWithConstraints(
        Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(if (kid) Color(0xB3FFFFFF) else Color(0x99000000))
            .clickable(onClickLabel = "Annuler la promotion", onClick = onCancel),
    ) {
        val cell = maxOf(maxWidth / 8, 48.dp)
        val atTop = BoardGeometry.row(to, flipped) == 0
        val x = minOf(maxWidth / 8 * BoardGeometry.column(to, flipped), maxWidth - cell)
        val shape = RoundedCornerShape(if (kid) DuckRadius.M else DuckRadius.S)
        val types = intArrayOf(QUEEN, ROOK, BISHOP, KNIGHT).let { if (atTop) it else it.reversedArray() }
        Column(
            Modifier.offset(x = x, y = if (atTop) 0.dp else maxHeight - cell * 4).width(cell).shadow(8.dp, shape).clip(shape).background(c.surface)
                .then(if (kid) Modifier.border(3.dp, c.goldRole.fill, shape) else Modifier),
        ) {
            for (type in types) {
                val res = (if (kid) KID_PIECE_RES else PIECE_RES).getValue(if (white) type else type + BLACK)
                Image(
                    painterResource(res), "Promouvoir en ${PIECE_NAMES_FR[type]}",
                    Modifier.size(cell).clickable(role = Role.Button) { onPick(type) }.padding(4.dp),
                )
            }
        }
    }
}

/** Nom TalkBack d'une pièce : « Cavalier blanc », « Tour noire » ; « case vide » sinon. */
private fun spokenPiece(piece: Int): String {
    if (piece == 0) return "case vide"
    val t = pieceType(piece)
    val f = PieceLexiconFr.isFeminine(t)
    return PIECE_NAMES_FR[t] + if (isWhite(piece)) (if (f) " blanche" else " blanc") else (if (f) " noire" else " noir")
}

/**
 * 64 cases accessibles, posées sur le dessin de l'échiquier (sans intercepter les gestes) : chacune annonce
 * sa coordonnée, son occupant et son état, dans l'ordre visuel (qui suit l'orientation). [onActivate] permet
 * de sélectionner une pièce puis de jouer par double toucher TalkBack ou au clavier.
 */
@Composable
private fun BoardCells(position: Position, flipped: Boolean, lastMove: Move?, selected: Int?, targets: List<Move>, onActivate: ((Int) -> Unit)?) {
    val checked = if (position.inCheck) position.kingSquare(position.whiteToMove) else -1
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            for (q in 0 until 64) {
                val move = targets.firstOrNull { it.to == q }
                val states = listOfNotNull(
                    "sélectionnée".takeIf { q == selected },
                    move?.let { if (position.isCapture(it)) "prise possible" else "destination légale" },
                    "dernier coup".takeIf { lastMove != null && (q == lastMove.from || q == lastMove.to) },
                    "roi en échec".takeIf { q == checked },
                )
                Box(
                    Modifier.semantics {
                        contentDescription = "${sqName(q)}, ${spokenPiece(position[q])}"
                        if (states.isNotEmpty()) stateDescription = states.joinToString(", ")
                        if (onActivate != null) {
                            role = Role.Button
                            this.selected = q == selected
                            onClick { onActivate(q); true }
                        }
                    },
                )
            }
        },
    ) { measurables, constraints ->
        val s = constraints.maxWidth / 8
        val cells = measurables.map { it.measure(Constraints.fixed(s, s)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            cells.forEachIndexed { q, p -> p.place(BoardGeometry.column(q, flipped) * s, BoardGeometry.row(q, flipped) * s) }
        }
    }
}

private fun DrawScope.drawBadge(texts: TextLayoutCache, b: Badge, center: Offset, s: Float) {
    val r = s * 0.19f
    drawCircle(Color(0x55000000), r * 1.08f, center + Offset(0f, r * 0.12f))
    drawCircle(b.color, r, center)
    val size = if (b.text.length > 1) r * 1.05f else r * 1.25f
    val layout = texts.get(b.text, TextStyle(fontSize = size.toSp(), fontWeight = FontWeight.Black))
    drawText(layout, Color.White, center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}

/** Flèche directionnelle ; coudée en « L » pour un saut de Cavalier. */
private fun DrawScope.drawArrow(a: Arrow, s: Float, center: (Int) -> Offset) {
    val squares = BoardGeometry.arrowSquares(a.from, a.to)
    val cx = FloatArray(squares.size) { center(squares[it]).x }
    val cy = FloatArray(squares.size) { center(squares[it]).y }
    val shape = BoardGeometry.arrowShape(cx, cy, s, head = s * 0.42f)
    val color = a.color.copy(alpha = 0.85f)
    val path = Path().apply {
        moveTo(shape.shaftX[0], shape.shaftY[0])
        for (i in 1 until shape.shaftX.size) lineTo(shape.shaftX[i], shape.shaftY[i])
    }
    drawPath(path, color, style = Stroke(s * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val headPath = Path().apply {
        moveTo(shape.headX[0], shape.headY[0])
        lineTo(shape.headX[1], shape.headY[1])
        lineTo(shape.headX[2], shape.headY[2])
        close()
    }
    drawPath(headPath, color)
}

/** Libellé court d'une évaluation (point de vue des Blancs) : « +1.4 », « −0.3 », « M2 ». */
fun evalShort(evalWhite: Int): String = when {
    isMateScore(evalWhite) -> if (mateIn(evalWhite) == 0) "#" else "M${abs(mateIn(evalWhite))}"
    // Au-delà de 10 pions la décimale n'apporte rien et élargirait la jauge.
    else -> (if (evalWhite >= 0) "+" else "−") + String.format(Locale.US, if (abs(evalWhite) >= 1000) "%.0f" else "%.1f", abs(evalWhite) / 100.0)
}

/** Libellés les plus larges que peut produire [evalShort] : la jauge est dimensionnée pour eux. */
private val EVAL_WIDEST = listOf("−9.9", "+99", "M99")
private val EVAL_STYLE = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TNUM)

/**
 * Jauge latérale d'évaluation : part des Blancs, encoche médiane (égalité) et valeur affichée
 * à l'extrémité du camp qui a l'avantage. [evalWhite] en centipions (ou score de mat), point de vue des Blancs.
 */
@Composable
fun EvalBar(evalWhite: Int, flipped: Boolean, modifier: Modifier = Modifier) {
    val share by animateFloatAsState((winPercent(evalWhite) / 100.0).toFloat().coerceIn(0.03f, 0.97f), tween(350), label = "eval")
    val measurer = rememberTextMeasurer()
    val whiteAhead = evalWhite >= 0
    val label = evalShort(evalWhite)
    val desc = when {
        isMateScore(evalWhite) && mateIn(evalWhite) == 0 -> "Évaluation : échec et mat, victoire des ${if (evalWhite > 0) "Blancs" else "Noirs"}"
        isMateScore(evalWhite) -> "Évaluation : mat en ${abs(mateIn(evalWhite))} pour les ${if (evalWhite > 0) "Blancs" else "Noirs"}"
        abs(evalWhite) < 30 -> "Évaluation : position équilibrée"
        else -> "Évaluation : avantage ${if (whiteAhead) "Blancs" else "Noirs"} ${String.format(Locale.FRANCE, "%.1f", abs(evalWhite) / 100.0)}"
    }
    val labelLayout = remember(label, measurer) { measurer.measure(label, EVAL_STYLE) }
    val c = LocalDuck.current
    Canvas(
        modifier.fillMaxHeight().width(evalBarWidth()).clip(RoundedCornerShape(DuckRadius.S)).border(1.dp, c.outlineStrong, RoundedCornerShape(DuckRadius.S))
            .semantics { contentDescription = desc },
    ) {
        val dark = Color(0xFF1F2937)
        val light = Color(0xFFF1F5F9)
        drawRect(dark)
        val h = size.height * share
        drawRect(light, Offset(0f, if (flipped) 0f else size.height - h), Size(size.width, h))
        // Encoche médiane nette (égalité)
        drawRect(Color(0xFFF59E0B), Offset(0f, size.height / 2 - 1.5.dp.toPx()), Size(size.width, 3.dp.toPx()))
        val t = labelLayout
        val whiteAtBottom = !flipped
        val atBottom = whiteAhead == whiteAtBottom
        val y = if (atBottom) size.height - t.size.height - 3.dp.toPx() else 3.dp.toPx()
        drawText(t, if (whiteAhead) Color(0xFF0F172A) else Color.White, Offset((size.width - t.size.width) / 2f, y))
    }
}

/**
 * Largeur de la jauge : fixe (l'échiquier ne saute pas quand le score change) et assez large pour le score
 * le plus long, quelle que soit la taille de police du système.
 */
@Composable
fun evalBarWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density) {
        val widest = EVAL_WIDEST.maxOf { measurer.measure(it, EVAL_STYLE).size.width }
        maxOf(26.dp, with(density) { widest.toDp() } + 6.dp)
    }
}

/** Espace occupé à gauche de l'échiquier par la jauge : les bandeaux des joueurs s'alignent dessus. */
@Composable
fun evalBarSpace(): Dp = evalBarWidth() + EVAL_GAP

private val EVAL_GAP = 4.dp

/** Échiquier avec jauge d'évaluation optionnelle à gauche ([evalWhite] null : pas de jauge). */
@Composable
fun BoardRow(evalWhite: Int?, flipped: Boolean, board: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (evalWhite != null) {
            EvalBar(evalWhite, flipped, Modifier.padding(end = EVAL_GAP))
        }
        Box(Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
            board(Modifier.aspectRatio(1f))
        }
    }
}
