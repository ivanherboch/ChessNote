package com.ivanherboch.chessduck.feature

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.R
import com.ivanherboch.chessduck.chess.BISHOP
import com.ivanherboch.chessduck.chess.GameEnd
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.QUEEN
import com.ivanherboch.chessduck.chess.ROOK
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.ui.AutoText
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.CapturedBar
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.KID_PIECE_RES
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalDuckFont
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.moveAnim
import com.ivanherboch.chessduck.ui.undoAnim
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class KidState(
    val line: Line = Line(START_FEN),
    val cursor: Int = 0,
    val anim: BoardAnim? = null,
    /** Message de fin de partie (null tant que la partie continue). */
    val result: String? = null,
) {
    val position: Position get() = line.positions[cursor.coerceIn(0, line.size)]
}

/**
 * Mode enfant : une partie à 2 joueurs sur le même écran, sans ordinateur,
 * sans évaluation et sans enregistrement dans l'historique.
 */
class KidViewModel : ViewModel() {
    val state = MutableStateFlow(KidState())

    fun reset() = state.update { KidState() }

    fun move(m: Move) {
        val s = state.value
        if (s.result != null || !s.position.isLegal(m)) return
        val nl = s.line.copy(s.cursor)
        nl.push(m)
        val white = !nl.last.whiteToMove
        val result = when (nl.end()) {
            GameEnd.CHECKMATE -> "🎉 Échec et mat ! Bravo aux ${if (white) "Blancs" else "Noirs"} !"
            GameEnd.NONE -> null
            else -> "🤝 Partie nulle : personne n'a perdu !"
        }
        state.update { it.copy(line = nl, cursor = nl.size, anim = moveAnim(s.position, m), result = result) }
    }

    fun back() {
        val s = state.value
        if (s.cursor <= 0) return
        val t = s.cursor - 1
        state.update { it.copy(cursor = t, anim = undoAnim(s.line.positions[t], s.line.moves[t]), result = null) }
    }

    fun forward() {
        val s = state.value
        if (s.cursor >= s.line.size) return
        state.update { it.copy(cursor = s.cursor + 1, anim = moveAnim(s.position, s.line.moves[s.cursor])) }
    }

    fun resign() {
        val s = state.value
        if (s.result != null) return
        val loser = if (s.position.whiteToMove) "Blancs" else "Noirs"
        val winner = if (s.position.whiteToMove) "Noirs" else "Blancs"
        state.update { it.copy(result = "🏳️ Les $loser abandonnent : bravo aux $winner !") }
    }
}

/** Une pièce du mode enfant : couleurs de sa carte, couleur du texte et phrase d'explication (**gras**). */
internal class KidPiece(val type: Int, val name: String, val bg: Color, val edge: Color, val ink: Color, val article: String, val text: String)

internal val KID_LEGEND = listOf(
    KidPiece(KING, "Roi", Color(0xFFFFE08A), Color(0xFFF2B705), Color(0xFF8A5A00), "Le Roi", "Le **Roi** avance d'**une seule case**, dans **toutes les directions**."),
    KidPiece(QUEEN, "Dame", Color(0xFFFFC2D4), Color(0xFFFF7AA2), Color(0xFFD6336C), "La Dame", "La **Dame** va **tout droit** ou **en diagonale**, aussi loin qu'elle veut."),
    KidPiece(ROOK, "Tour", Color(0xFFBFE6FF), Color(0xFF4DB5F5), Color(0xFF1971C2), "La Tour", "La **Tour** va **tout droit** : devant, derrière ou sur les côtés, aussi loin qu'elle veut."),
    KidPiece(BISHOP, "Fou", Color(0xFFD9C8FF), Color(0xFF9B7BFF), Color(0xFF6741D9), "Le Fou", "Le **Fou** glisse **en diagonale**, aussi loin qu'il veut."),
    KidPiece(KNIGHT, "Cavalier", Color(0xFFFFD3A8), Color(0xFFFF9F43), Color(0xFFC2410C), "Le Cavalier", "Le **Cavalier** saute en forme de **L** : 2 cases tout droit, puis 1 sur le côté. Il peut **sauter par-dessus** les autres pièces !"),
    KidPiece(PAWN, "Pion", Color(0xFFC9F2B0), Color(0xFF6CCB3A), Color(0xFF237A36), "Le Pion", "Le **Pion** avance **tout droit** d'une case (2 cases la première fois). Il mange **en diagonale** !"),
)

// Couleurs denses : texte blanc ≥ 4,5:1 (face) et bordure basse plus sombre (effet « coussin »).
internal val KidBlue = Color(0xFF0369A1) to Color(0xFF024B73)
internal val KidOrange = Color(0xFFC2410C) to Color(0xFF8F300A)
internal val KidCoral = Color(0xFFD93829) to Color(0xFF9E281D)
internal val KidGreen = Color(0xFF237A36) to Color(0xFF175326)

/** Bouton pilule à effet « coussin » : la bordure basse plus sombre s'écrase quand on appuie. */
@Composable
private fun KidButton(
    label: String,
    colors: Pair<Color, Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    maxSize: TextUnit = 18.sp,
    icon: ImageVector? = null,
    contentDescription: String? = null,
) {
    val fb = LocalFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val depth = 6.dp
    val sink by animateDpAsState(if (pressed && enabled) depth else 0.dp, spring(stiffness = Spring.StiffnessHigh), label = "sink")
    val face = if (enabled) colors.first else Color(0xFFDCEAF6)
    val edge = if (enabled) colors.second else Color(0xFFC3D7EA)
    val ink = if (enabled) Color.White else Color(0xFF4A6785)
    val pill = RoundedCornerShape(50)
    Box(modifier.height(height)) {
        Box(Modifier.fillMaxSize().padding(top = depth).clip(pill).background(edge))
        Box(
            Modifier.fillMaxWidth().height(height - depth).offset(y = sink).clip(pill).background(face)
                .clickable(interactionSource = source, indication = ripple(color = Color.White), enabled = enabled, role = Role.Button) { fb?.key(); onClick() }
                .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(26.dp))
                if (label.isNotEmpty()) AutoText(
                    label, color = ink, maxSize = maxSize, minSize = 13.sp, weight = FontWeight.Bold, align = TextAlign.Center,
                    modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp),
                )
            }
        }
    }
}

/** Carte colorée d'une pièce, avec un léger rebond au toucher. */
@Composable
private fun PieceCard(p: KidPiece, modifier: Modifier, onClick: () -> Unit) {
    val fb = LocalFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium), label = "bounce",
    )
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(p.bg).border(2.5.dp, p.edge, shape)
            .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { fb?.key(); onClick() }) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "${p.name} : voir comment bouge ${p.article.replaceFirstChar { it.lowercase() }}"
                onClick { onClick(); true }
            }
            .padding(horizontal = 3.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(KID_PIECE_RES.getValue(p.type)), null, Modifier.fillMaxWidth(0.86f).aspectRatio(1f))
        AutoText(p.name, color = Color(0xFF1F3B57), maxSize = 14.sp, weight = FontWeight.Bold, align = TextAlign.Center)
    }
}

@Composable
fun KidScreen(vm: KidViewModel, onQuit: () -> Unit) {
    // Quitter demande une confirmation : l'écran se floute derrière la boîte de dialogue.
    var confirm by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (confirm) Modifier.blur(14.dp) else Modifier)) {
            KidGame(vm) { confirm = true }
        }
        if (confirm) QuitDialog(onStay = { confirm = false }, onQuit = { confirm = false; onQuit() })
    }
}

/** Boîte de confirmation au style du mode enfant : « Non » rassurant en vert, « Oui, quitter » en corail. */
@Composable
private fun QuitDialog(onStay: () -> Unit, onQuit: () -> Unit) {
    val c = LocalDuck.current
    BackHandler(onBack = onStay)
    Box(
        // Voile cliquable : toucher en dehors de la boîte revient à répondre « Non ».
        Modifier.fillMaxSize().background(Color(0x661F3B57)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onStay() },
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(30.dp)
        Column(
            Modifier.padding(horizontal = 22.dp).widthIn(max = 420.dp).shadow(16.dp, shape).clip(shape).background(Color.White).border(4.dp, Color(0xFF9CC9F0), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.mascot), null, Modifier.size(84.dp))
            AutoText("Quitter le mode enfant ?", color = c.text, maxSize = 24.sp, weight = FontWeight.Bold, align = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            AutoText("La partie en cours du mode enfant sera effacée.", color = c.muted, maxSize = 16.sp, weight = FontWeight.Medium, align = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
            KidButton("Non, on continue à jouer", KidGreen, onStay, Modifier.fillMaxWidth(), height = 58.dp, maxSize = 19.sp, icon = Icons.Rounded.SportsEsports)
            KidButton("Oui, quitter", KidCoral, onQuit, Modifier.fillMaxWidth().padding(top = 12.dp), height = 50.dp, maxSize = 17.sp, icon = Icons.AutoMirrored.Rounded.Logout)
        }
    }
}

@Composable
private fun KidGame(vm: KidViewModel, onQuit: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    val pos = s.position
    val cur = s.cursor
    val last = if (cur > 0) s.line.moves[cur - 1] else null
    val whiteTurn = pos.whiteToMove
    // Leçon animée ouverte en touchant une carte ; la partie en cours est conservée.
    var demo by rememberSaveable { mutableIntStateOf(0) }
    // Message bref quand l'enfant touche une pièce qui ne peut pas bouger.
    var blocked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(blocked) { if (blocked != null) { delay(2600); blocked = null } }
    AnimatedContent(demo, label = "leçon", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }) { shown ->
        val piece = KID_LEGEND.firstOrNull { it.type == shown }
        if (piece != null) {
            KidDemo(piece) { demo = 0 }
            return@AnimatedContent
        }
        ModuleLayout(
            bottomMin = 200.dp,
            adaptive = false,
            top = {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Bulle flottante : la bordure change de couleur selon le joueur au trait.
                    val accent = when {
                        s.result != null -> KidGreen.first
                        blocked != null -> KidOrange.first
                        whiteTurn -> Color(0xFFF59E0B)
                        else -> Color(0xFF7C3AED)
                    }
                    val bubble = RoundedCornerShape(32.dp)
                    Row(
                        Modifier.weight(1f).padding(start = 4.dp).shadow(8.dp, bubble).clip(bubble)
                            .background(Color.White).border(4.dp, accent, bubble).height(54.dp).padding(horizontal = 14.dp)
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                    ) {
                        if (s.result == null && blocked == null) {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).background(if (whiteTurn) Color.White else Color(0xFF1F2937))
                                    .border(3.dp, if (whiteTurn) Color(0xFF64748B) else Color(0xFF0B1220), CircleShape),
                            )
                        }
                        val text = s.result ?: blocked ?: ((if (pos.inCheck) "Échec ! " else "") + if (whiteTurn) "Aux Blancs de jouer" else "Aux Noirs de jouer")
                        AutoText(text, color = c.text, maxSize = 22.sp, minSize = 13.sp, weight = FontWeight.Bold, align = TextAlign.Center, maxLines = if (text.length > 22) 2 else 1, modifier = Modifier.padding(start = 10.dp))
                    }
                    // Sortie discrète, loin de la zone du pouce ; une confirmation est demandée.
                    QuitPill(onQuit, Modifier.padding(start = 8.dp, end = 4.dp))
                }
            },
            board = {
                val frame = RoundedCornerShape(22.dp)
                Box(Modifier.fillMaxSize().padding(horizontal = 4.dp).clip(frame).background(Color(0xFF3DB89C)).padding(7.dp)) {
                    ChessBoard(
                        position = pos,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp)),
                        interactive = s.result == null,
                        lastMove = last,
                        anim = s.anim,
                        kid = true,
                        onIllegalTouch = {
                            blocked = if (pos.inCheck) "Ton roi est en échec : protège-le !" else "Cette pièce est bloquée !"
                        },
                        onMove = { m -> blocked = null; vm.move(m) },
                    )
                }
            },
            underBoard = { CapturedBar(pos, Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp), kid = true) },
            bottom = {
                Column(Modifier.fillMaxSize().padding(vertical = 6.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    // Zone compacte : nom des pièces
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        AutoText("👆 Touche une pièce pour voir comment elle bouge !", color = c.text, maxSize = 17.sp, minSize = 13.sp, weight = FontWeight.Bold, align = TextAlign.Center, maxLines = 2)
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            for (p in KID_LEGEND) PieceCard(p, Modifier.weight(1f)) { demo = p.type }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KidButton("", KidBlue, vm::back, Modifier.weight(1f), enabled = cur > 0, icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Coup précédent")
                        KidButton("", KidBlue, vm::forward, Modifier.weight(1f), enabled = cur < s.line.size, icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Coup suivant")
                        if (s.result == null) KidButton("Abandonner", KidOrange, vm::resign, Modifier.weight(1.9f), enabled = s.line.size > 0, icon = Icons.Rounded.Flag)
                        else KidButton("Rejouer", KidGreen, vm::reset, Modifier.weight(1.9f), icon = Icons.Rounded.Replay)
                    }
                }
            },
        )
    }
}

/** Petite pilule « Quitter » en haut à droite : discrète, hors de portée des appuis accidentels. */
@Composable
private fun QuitPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fb = LocalFeedback.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier.heightIn(min = 48.dp).clip(shape)
            .clickable(role = Role.Button, onClickLabel = "Quitter le mode enfant") { fb?.key(); onClick() }
            .semantics { contentDescription = "Quitter le mode enfant" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(shape).background(Color.White).border(2.dp, Color(0xFF9CC9F0), shape).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = Color(0xFF4A6785), modifier = Modifier.size(18.dp))
            Text("Quitter", color = Color(0xFF4A6785), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

// ------------------------------------------------------------------ Leçons animées

/** Une étape de démonstration : case de départ, trajets (suite de cases), cases à signaler, pièce à manger. */
private class DemoPhase(
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
        ROOK -> listOf(DemoPhase(home, rays(STRAIGHT), rays(STRAIGHT).flatten()))
        BISHOP -> listOf(DemoPhase(home, rays(DIAGONAL), rays(DIAGONAL).flatten()))
        // La Dame : d'abord tout droit, puis en diagonale.
        QUEEN -> listOf(
            DemoPhase(home, rays(STRAIGHT.take(2)), rays(STRAIGHT).flatten()),
            DemoPhase(home, rays(listOf(1 to 1, -1 to -1)), rays(DIAGONAL).flatten()),
        )
        KING -> {
            val steps = (STRAIGHT + DIAGONAL).map { home.first + it.first to home.second + it.second }
            listOf(DemoPhase(home, listOf(0, 4, 1, 5, 2, 6, 3, 7).map { listOf(steps[it]) }, steps))
        }
        KNIGHT -> {
            val jumps = listOf(1 to 2, 2 to -1, -1 to -2, -2 to 1, 2 to 1, 1 to -2, -2 to -1, -1 to 2).map { home.first + it.first to home.second + it.second }
            listOf(DemoPhase(home, jumps.take(4).map { listOf(it) }, jumps))
        }
        else -> listOf(
            // Le Pion : un pas, puis deux pas depuis sa case de départ, puis une prise en diagonale.
            DemoPhase(3 to 1, listOf(listOf(3 to 2)), listOf(3 to 2)),
            DemoPhase(3 to 1, listOf(listOf(3 to 3)), listOf(3 to 2, 3 to 3)),
            DemoPhase(3 to 3, listOf(listOf(4 to 4)), listOf(4 to 4), enemy = 4 to 4),
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
 */
@Composable
private fun KidDemo(piece: KidPiece, onClose: () -> Unit) {
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
        while (true) {
            for (ph in phases) {
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
            }
        }
    }

    ModuleLayout(
        bottomMin = 190.dp,
        adaptive = false,
        top = {
            val bubble = RoundedCornerShape(32.dp)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp).shadow(8.dp, bubble).clip(bubble)
                    .background(Color.White).border(4.dp, piece.edge, bubble).height(54.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Image(painterResource(KID_PIECE_RES.getValue(piece.type)), null, Modifier.size(38.dp))
                AutoText("Comment bouge ${piece.article.replaceFirstChar { it.lowercase() }} ?", color = piece.ink, maxSize = 22.sp, weight = FontWeight.Bold, align = TextAlign.Center, modifier = Modifier.padding(start = 8.dp))
            }
        },
        board = {
            val frame = RoundedCornerShape(22.dp)
            Box(Modifier.fillMaxSize().padding(horizontal = 4.dp).clip(frame).background(Color(0xFF3DB89C)).padding(7.dp)) {
                Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp))) {
                    val s = size.width / 8f
                    fun topLeft(q: Pair<Int, Int>) = Offset(q.first * s, (7 - q.second) * s)
                    fun center(q: Pair<Int, Int>) = topLeft(q) + Offset(s / 2, s / 2)
                    for (f in 0..7) for (r in 0..7) {
                        drawRect(if ((f + r) % 2 == 1) c.squareLight else c.squareDark, topLeft(f to r), Size(s, s))
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
                        val corner = if (abs(tg.second - h.second) == 2) h.first to tg.second else tg.first to h.second
                        val path = Path().apply {
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
                val bubble = RoundedCornerShape(22.dp)
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(bottom = 10.dp).clip(bubble).background(Color.White).border(3.dp, piece.edge, bubble).padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Le texte reprend la couleur de la carte touchée ; les mots importants sont en gras.
                    BasicText(
                        boldText(piece.text),
                        style = TextStyle(color = piece.ink, fontFamily = LocalDuckFont.current, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 1.25.em),
                        autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 22.sp, stepSize = 0.5.sp),
                    )
                }
                KidButton("Revenir à la partie", KidGreen, onClose, Modifier.fillMaxWidth(), height = 56.dp, maxSize = 19.sp, icon = Icons.AutoMirrored.Rounded.ArrowBack)
            }
        },
    )
}
