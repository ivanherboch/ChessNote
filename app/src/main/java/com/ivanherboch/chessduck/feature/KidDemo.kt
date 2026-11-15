package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.chess.rankOf
import com.ivanherboch.chessduck.ui.DuckRadius
import com.ivanherboch.chessduck.chess.fileOf
import com.ivanherboch.chessduck.chess.sq
import com.ivanherboch.chessduck.ui.BoardGeometry
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.em
import com.ivanherboch.chessduck.chess.BLACK
import com.ivanherboch.chessduck.ui.rememberPieceImages
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivanherboch.chessduck.chess.BISHOP
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.QUEEN
import com.ivanherboch.chessduck.chess.ROOK
import com.ivanherboch.chessduck.ui.AutoText
import com.ivanherboch.chessduck.ui.KID_PIECE_RES
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalDuckFont
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout

/**
 * Une étape de démonstration : la phrase qui l'explique (**mots importants** en gras, écrite pour être dite
 * à voix haute), la case de départ, les trajets (suite de cases), les cases à signaler et la pièce à manger.
 */
private class DemoPhase(
    val say: String,
    val home: Pair<Int, Int>,
    val paths: List<List<Pair<Int, Int>>>,
    val dots: List<Pair<Int, Int>>,
    val enemy: Pair<Int, Int>? = null,
)

private fun ray(from: Pair<Int, Int>, df: Int, dr: Int): List<Pair<Int, Int>> {
    val out = ArrayList<Pair<Int, Int>>()
    var f = from.first + df
    var r = from.second + dr
    while (f in 0..7 && r in 0..7) { out.add(f to r); f += df; r += dr }
    return out
}

private val STRAIGHT = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)

private val DIAGONAL = listOf(1 to 1, 1 to -1, -1 to -1, -1 to 1)

/** Scénario de la leçon : les différents types de coups de la pièce, montrés l'un après l'autre. */
private fun demoPhases(type: Int): List<DemoPhase> {
    val home = 3 to 3 // d4
    fun rays(dirs: List<Pair<Int, Int>>) = dirs.map { ray(home, it.first, it.second) }
    return when (type) {
        ROOK -> listOf(
            DemoPhase("La **Tour** va **tout droit** : devant, derrière ou sur les côtés, aussi loin qu'elle veut.", home, rays(STRAIGHT), rays(STRAIGHT).flatten()),
        )
        BISHOP -> listOf(DemoPhase("Le **Fou** glisse **en diagonale**, aussi loin qu'il veut.", home, rays(DIAGONAL), rays(DIAGONAL).flatten()))
        // La Dame : d'abord tout droit, puis en diagonale.
        QUEEN -> listOf(
            DemoPhase("La **Dame** va **tout droit**, aussi loin qu'elle veut.", home, rays(STRAIGHT.take(2)), rays(STRAIGHT).flatten()),
            DemoPhase("Elle va aussi **en diagonale**, aussi loin qu'elle veut.", home, rays(listOf(1 to 1, -1 to -1)), rays(DIAGONAL).flatten()),
        )
        KING -> {
            val steps = (STRAIGHT + DIAGONAL).map { home.first + it.first to home.second + it.second }
            listOf(DemoPhase("Le **Roi** avance d'**une seule case**, dans **toutes les directions**.", home, listOf(0, 4, 1, 5, 2, 6, 3, 7).map { listOf(steps[it]) }, steps))
        }
        KNIGHT -> {
            val jumps = listOf(1 to 2, 2 to -1, -1 to -2, -2 to 1, 2 to 1, 1 to -2, -2 to -1, -1 to 2).map { home.first + it.first to home.second + it.second }
            listOf(
                DemoPhase(
                    "Le **Cavalier** saute en forme de **L** : deux cases tout droit, puis une sur le côté. Il peut **sauter par-dessus** les autres pièces !",
                    home, jumps.take(4).map { listOf(it) }, jumps,
                ),
            )
        }
        else -> listOf(
            // Le Pion : un pas, puis deux pas depuis sa case de départ, puis une prise en diagonale.
            DemoPhase("Le **Pion** avance **tout droit**, d'une seule case.", 3 to 1, listOf(listOf(3 to 2)), listOf(3 to 2)),
            DemoPhase("La première fois qu'il bouge, il peut avancer de **deux cases**.", 3 to 1, listOf(listOf(3 to 3)), listOf(3 to 2, 3 to 3)),
            DemoPhase("Et il mange **en diagonale** !", 3 to 3, listOf(listOf(4 to 4)), listOf(4 to 4), enemy = 4 to 4),
        )
    }
}

/** Transforme une phrase « avec des **mots importants** » en texte dont ces mots sont en gras. */
private fun boldText(src: String): AnnotatedString = buildAnnotatedString {
    src.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

/**
 * Leçon animée d'une pièce : seule sur un échiquier vide, elle rejoue en boucle, en sautillant,
 * ses différents types de coups. Ni promotion ni prise en passant : trop compliqué pour un enfant.
 * Si la voix est activée, chaque type de coup est annoncé au moment où il est montré (premier tour seulement) ;
 * sinon le rythme de l'animation est inchangé.
 */
@Composable
internal fun KidDemo(piece: KidPiece, vm: KidViewModel, onClose: () -> Unit) {
    val c = LocalDuck.current
    BackHandler(onBack = onClose)
    val images = rememberPieceImages(kid = true)
    val fb = LocalFeedback.current
    val phases = remember(piece.type) { demoPhases(piece.type) }
    var phase by remember { mutableStateOf(phases[0]) }
    var from by remember { mutableStateOf(phases[0].home) }
    var to by remember { mutableStateOf(phases[0].home) }
    var enemyAlive by remember { mutableStateOf(true) }
    var target by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val t = remember { Animatable(1f) }

    LaunchedEffect(piece.type) {
        vm.pieceShown(piece.type)
        while (true) {
            for (ph in phases) {
                vm.say(piece.type, ph.say)
                for (path in ph.paths) {
                    phase = ph; from = ph.home; to = ph.home; enemyAlive = true; target = path.last()
                    t.snapTo(1f)
                    delay(450)
                    var cur = ph.home
                    for (step in path) {
                        from = cur; to = step
                        t.snapTo(0f)
                        val far = maxOf(abs(step.first - cur.first), abs(step.second - cur.second))
                        t.animateTo(1f, tween(if (piece.type == KNIGHT) 560 else 250 + 110 * far, easing = LinearEasing))
                        fb?.move() // même bruit de pièce posée que dans les parties
                        cur = step
                        if (step == ph.enemy) enemyAlive = false
                    }
                    delay(650)
                }
                // La phrase de cette étape se termine avant de passer à la suivante : voix et image restent synchrones.
                vm.awaitVoice(piece.type)
            }
            vm.voiceDone(piece.type)
        }
    }
    val explanation = remember(piece.type) { boldText(phases.joinToString(" ") { it.say }) }

    ModuleLayout(
        bottomMin = 190.dp,
        adaptive = false,
        top = {
            val bubble = RoundedCornerShape(DuckRadius.Kid)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp).shadow(8.dp, bubble).clip(bubble)
                    .background(Color.White).border(4.dp, piece.edge, bubble).heightIn(min = 54.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Image(painterResource(KID_PIECE_RES.getValue(piece.type)), null, Modifier.size(38.dp))
                AutoText("Comment bouge ${piece.article.replaceFirstChar { it.lowercase() }} ?", color = piece.ink, maxSize = 22.sp, minSize = 13.sp, weight = FontWeight.Bold, align = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
            }
        },
        board = {
            val frame = RoundedCornerShape(DuckRadius.Kid)
            Box(Modifier.fillMaxSize().padding(horizontal = 4.dp).clip(frame).background(Color(0xFF3DB89C)).padding(7.dp)) {
                val knightPath = remember { Path() }
                Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(DuckRadius.Kid - 7.dp))) {
                    val s = size.width / 8f
                    val cell = Size(s, s)
                    fun topLeft(q: Pair<Int, Int>) = Offset(q.first * s, (7 - q.second) * s)
                    fun center(q: Pair<Int, Int>) = topLeft(q) + Offset(s / 2, s / 2)
                    // Cases dessinées sans allouer de paire par case et par image.
                    for (f in 0..7) for (r in 0..7) {
                        drawRect(if ((f + r) % 2 == 1) c.squareLight else c.squareDark, Offset(f * s, (7 - r) * s), cell)
                    }
                    // Cases où la pièce a le droit d'aller
                    for (d in phase.dots) {
                        if (d == phase.enemy) drawCircle(piece.edge, s * 0.44f, center(d), style = Stroke(s * 0.09f))
                        else drawCircle(piece.edge.copy(alpha = 0.85f), s * 0.15f, center(d))
                    }
                    drawRect(piece.edge.copy(alpha = 0.35f), topLeft(phase.home), Size(s, s))
                    // Le « L » du Cavalier, tracé sur le plateau
                    val tg = target
                    if (piece.type == KNIGHT && tg != null) {
                        val h = phase.home
                        // Coude du « L » : même règle que les flèches de l'échiquier (BoardGeometry).
                        val c = BoardGeometry.knightCorner(sq(h.first, h.second), sq(tg.first, tg.second))
                        val corner = if (c >= 0) fileOf(c) to rankOf(c) else tg
                        val path = knightPath.apply {
                            rewind()
                            moveTo(center(h).x, center(h).y); lineTo(center(corner).x, center(corner).y); lineTo(center(tg).x, center(tg).y)
                        }
                        drawPath(path, piece.edge.copy(alpha = 0.55f), style = Stroke(s * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    fun drawPiece(p: Int, at: Offset, scaleY: Float = 1f) {
                        val img = images[p] ?: return
                        val h = (s * scaleY).roundToInt()
                        drawImage(img, dstOffset = IntOffset(at.x.roundToInt(), (at.y + (s - h)).roundToInt()), dstSize = IntSize(s.roundToInt(), h), filterQuality = FilterQuality.High)
                    }
                    phase.enemy?.let { if (enemyAlive) drawPiece(ROOK + BLACK, topLeft(it)) }
                    // La pièce sautille : arc de saut, ombre au sol et léger écrasement à l'atterrissage.
                    val p = t.value
                    val ground = topLeft(from) + (topLeft(to) - topLeft(from)) * p
                    val dist = maxOf(abs(to.first - from.first), abs(to.second - from.second)).coerceAtLeast(1)
                    val lift = sin(PI.toFloat() * p) * s * (if (piece.type == KNIGHT) 0.75f else 0.28f + 0.1f * dist)
                    drawOval(Color(0x33000000), ground + Offset(s * 0.2f, s * 0.8f), Size(s * 0.6f, s * 0.14f))
                    val squash = if (p > 0.85f && p < 1f) 0.92f else 1f
                    drawPiece(piece.type, ground - Offset(0f, lift), squash)
                    if (phase.enemy != null && !enemyAlive) {
                        // Petite étoile « miam » quand le pion mange.
                        drawCircle(Color(0xFFFF6F61), s * 0.16f, topLeft(phase.enemy!!) + Offset(s * 0.82f, s * 0.18f))
                    }
                }
            }
        },
        bottom = {
            Column(Modifier.fillMaxSize().padding(vertical = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                val bubble = RoundedCornerShape(DuckRadius.Kid)
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(bottom = 10.dp).clip(bubble).background(Color.White).border(3.dp, piece.edge, bubble).padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Le texte reprend la couleur de la carte touchée ; les mots importants sont en gras.
                    BasicText(
                        explanation,
                        style = TextStyle(color = piece.ink, fontFamily = LocalDuckFont.current, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 1.25.em),
                        autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 22.sp, stepSize = 0.5.sp),
                    )
                }
                KidButton("Revenir à la partie", KidGreen, onClose, Modifier.fillMaxWidth(), height = 56.dp, maxSize = 19.sp, icon = Icons.AutoMirrored.Rounded.ArrowBack)
            }
        },
    )
}
