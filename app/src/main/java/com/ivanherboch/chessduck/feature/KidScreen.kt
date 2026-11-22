package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.ui.MIN_TEXT
import androidx.compose.animation.AnimatedContent
import com.ivanherboch.chessduck.ui.DuckRadius
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.ivanherboch.chessduck.ui.icons.Logout
import androidx.compose.material.icons.rounded.Close
import com.ivanherboch.chessduck.ui.icons.Flag
import com.ivanherboch.chessduck.ui.icons.Replay
import com.ivanherboch.chessduck.ui.icons.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.R
import com.ivanherboch.chessduck.chess.BISHOP
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.QUEEN
import com.ivanherboch.chessduck.chess.ROOK
import com.ivanherboch.chessduck.ui.AutoText
import com.ivanherboch.chessduck.ui.CapturedBar
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.KID_PIECE_RES
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout

/** Une pièce du mode enfant : couleurs de sa carte et de son texte (l'explication est dans KidDemo, étape par étape). */
internal class KidPiece(val type: Int, val name: String, val bg: Color, val edge: Color, val ink: Color, val article: String)

internal val KID_LEGEND = listOf(
    KidPiece(KING, "Roi", Color(0xFFFFE08A), Color(0xFFF2B705), Color(0xFF8A5A00), "Le Roi"),
    KidPiece(QUEEN, "Dame", Color(0xFFFFC2D4), Color(0xFFFF7AA2), Color(0xFFD6336C), "La Dame"),
    KidPiece(ROOK, "Tour", Color(0xFFBFE6FF), Color(0xFF4DB5F5), Color(0xFF1971C2), "La Tour"),
    KidPiece(BISHOP, "Fou", Color(0xFFD9C8FF), Color(0xFF9B7BFF), Color(0xFF6741D9), "Le Fou"),
    KidPiece(KNIGHT, "Cavalier", Color(0xFFFFD3A8), Color(0xFFFF9F43), Color(0xFFC2410C), "Le Cavalier"),
    KidPiece(PAWN, "Pion", Color(0xFFC9F2B0), Color(0xFF6CCB3A), Color(0xFF237A36), "Le Pion"),
)

// Couleurs denses : texte blanc ≥ 4,5:1 (face) et bordure basse plus sombre (effet « coussin »).
internal val KidBlue = Color(0xFF0369A1) to Color(0xFF024B73)

internal val KidOrange = Color(0xFFC2410C) to Color(0xFF8F300A)

internal val KidCoral = Color(0xFFD93829) to Color(0xFF9E281D)

internal val KidGreen = Color(0xFF237A36) to Color(0xFF175326)

/** Bouton pilule à effet « coussin » : la bordure basse plus sombre s'écrase quand on appuie. */
@Composable
internal fun KidButton(
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
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium), label = "bounce",
    )
    val shape = RoundedCornerShape(DuckRadius.Kid)
    // Le nom est écrit sous la carte colorée, sur toute sa largeur : les coins arrondis et la bordure ne le rognent
    // jamais (« Cavalier » tient en entier sur un écran de 360 dp). Carte et nom forment une seule cible tactile.
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, role = Role.Button) { fb?.key(); onClick() }
            .semantics(mergeDescendants = true) {
                contentDescription = "${p.name} : voir comment bouge ${p.article.replaceFirstChar { it.lowercase() }}"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().clip(shape).background(p.bg).border(2.5.dp, p.edge, shape).padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(KID_PIECE_RES.getValue(p.type)), null, Modifier.fillMaxWidth().aspectRatio(1f))
        }
        AutoText(
            p.name, Modifier.padding(top = 3.dp), color = Color(0xFF1F3B57), maxSize = 14.sp, minSize = MIN_TEXT,
            weight = FontWeight.Bold, align = TextAlign.Center,
        )
    }
}

@Composable
fun KidScreen(vm: KidViewModel, onQuit: () -> Unit) {
    // Quitter et abandonner demandent une confirmation : l'écran se floute derrière la boîte de dialogue.
    var ask by remember { mutableStateOf<KidAsk?>(null) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (ask != null) Modifier.blur(14.dp) else Modifier)) {
            KidGame(vm, onQuit = { ask = KidAsk.QUIT }, onResign = { ask = KidAsk.RESIGN })
        }
        when (ask) {
            KidAsk.QUIT -> KidConfirm(
                "Quitter le mode enfant ?", "La partie en cours du mode enfant sera effacée.",
                "Oui, quitter", Icons.AutoMirrored.Rounded.Logout, onStay = { ask = null }, onGo = { ask = null; onQuit() },
            )
            KidAsk.RESIGN -> KidConfirm(
                "Abandonner la partie ?", "L'autre joueur gagnera la partie.",
                "Oui, abandonner", Icons.Rounded.Flag, onStay = { ask = null }, onGo = { ask = null; vm.resign() },
            )
            null -> Unit
        }
    }
}

private enum class KidAsk { QUIT, RESIGN }

/** Boîte de confirmation au style du mode enfant : « Non » rassurant en vert, « Oui, … » en corail. */
@Composable
private fun KidConfirm(title: String, text: String, goLabel: String, goIcon: ImageVector, onStay: () -> Unit, onGo: () -> Unit) {
    val c = LocalDuck.current
    BackHandler(onBack = onStay)
    Box(
        // Voile cliquable : toucher en dehors de la boîte revient à répondre « Non ».
        Modifier.fillMaxSize().background(Color(0x661F3B57)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onStay() },
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(DuckRadius.Kid)
        Column(
            Modifier.padding(horizontal = 22.dp).widthIn(max = 420.dp).shadow(16.dp, shape).clip(shape).background(Color.White).border(4.dp, Color(0xFF9CC9F0), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.mascot), null, Modifier.size(84.dp))
            AutoText(title, color = c.text, maxSize = 24.sp, weight = FontWeight.Bold, align = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            AutoText(text, color = c.muted, maxSize = 16.sp, weight = FontWeight.Medium, align = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
            KidButton("Non, on continue à jouer", KidGreen, onStay, Modifier.fillMaxWidth(), height = 58.dp, maxSize = 19.sp, icon = Icons.Rounded.SportsEsports)
            KidButton(goLabel, KidCoral, onGo, Modifier.fillMaxWidth().padding(top = 12.dp), height = 50.dp, maxSize = 17.sp, icon = goIcon)
        }
    }
}

@Composable
private fun KidGame(vm: KidViewModel, onQuit: () -> Unit, onResign: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    val pos = s.position
    val cur = s.cursor
    val last = if (cur > 0) s.line.moves[cur - 1] else null
    val whiteTurn = pos.whiteToMove
    // Leçon animée ouverte en touchant une carte ; la partie en cours est conservée.
    var demo by rememberSaveable { mutableIntStateOf(0) }
    // Voix des fiches : lue à l'ouverture, coupée à la fermeture et dès que l'écran n'est plus visible.
    LaunchedEffect(demo) { if (demo == 0) vm.pieceClosed() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.pieceClosed() }
    DisposableEffect(vm) { onDispose { vm.pieceClosed() } }
    // Message bref quand l'enfant touche une pièce qui ne peut pas bouger.
    var blocked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(blocked) { if (blocked != null) { delay(4000); blocked = null } }
    AnimatedContent(demo, label = "leçon", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }) { shown ->
        val piece = KID_LEGEND.firstOrNull { it.type == shown }
        if (piece != null) {
            KidDemo(piece, vm) { demo = 0 }
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
                    val bubble = RoundedCornerShape(DuckRadius.Kid)
                    Row(
                        Modifier.weight(1f).padding(start = 4.dp).shadow(8.dp, bubble).clip(bubble)
                            .background(Color.White).border(4.dp, accent, bubble).heightIn(min = 54.dp).padding(horizontal = 14.dp)
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
                        // Messages longs (nulles expliquées) : plus de lignes plutôt qu'un texte rapetissé.
                        AutoText(text, color = c.text, maxSize = 22.sp, minSize = 16.sp, weight = FontWeight.Bold, align = TextAlign.Center, maxLines = if (text.length > 22) 5 else 1, modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp))
                    }
                    // Sortie discrète, loin de la zone du pouce ; une confirmation est demandée.
                    QuitPill(onQuit, Modifier.padding(start = 8.dp, end = 4.dp))
                }
            },
            board = {
                val frame = RoundedCornerShape(DuckRadius.Kid)
                Box(Modifier.fillMaxSize().padding(horizontal = 4.dp).clip(frame).background(Color(0xFF3DB89C)).padding(7.dp)) {
                    ChessBoard(
                        position = pos,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(DuckRadius.Kid - 7.dp)),
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
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (p in KID_LEGEND) PieceCard(p, Modifier.weight(1f)) { demo = p.type }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KidButton("", KidBlue, vm::back, Modifier.weight(1f), enabled = cur > 0, icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Coup précédent")
                        KidButton("", KidBlue, vm::forward, Modifier.weight(1f), enabled = cur < s.line.size, icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Coup suivant")
                        if (s.result == null) KidButton("Abandonner", KidOrange, onResign, Modifier.weight(1.9f), enabled = s.line.size > 0, icon = Icons.Rounded.Flag)
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
