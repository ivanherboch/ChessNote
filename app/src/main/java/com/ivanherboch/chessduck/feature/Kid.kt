package com.ivanherboch.chessduck.feature

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
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
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.KID_PIECE_RES
import com.ivanherboch.chessduck.ui.KidColors
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalDuckFont
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.captureBadge
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

/** Police Fredoka (Google Fonts, licence OFL), en graisses variables. */
@OptIn(ExperimentalTextApi::class)
private val Fredoka = FontFamily(
    Font(R.font.fredoka, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.fredoka, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.fredoka, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.fredoka, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.fredoka, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Habillage du mode enfant : fond bleu ciel, cases menthe et vanille, police Fredoka partout. */
@Composable
fun KidTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDuck provides KidColors, LocalDuckFont provides Fredoka, content = content)
}

private class KidPiece(val type: Int, val name: String, val bg: Color, val edge: Color)

private val KID_LEGEND = listOf(
    KidPiece(KING, "Roi", Color(0xFFFFE08A), Color(0xFFF2B705)),
    KidPiece(QUEEN, "Dame", Color(0xFFFFC2D4), Color(0xFFFF7AA2)),
    KidPiece(ROOK, "Tour", Color(0xFFBFE6FF), Color(0xFF4DB5F5)),
    KidPiece(BISHOP, "Fou", Color(0xFFD9C8FF), Color(0xFF9B7BFF)),
    KidPiece(KNIGHT, "Cavalier", Color(0xFFFFD3A8), Color(0xFFFF9F43)),
    KidPiece(PAWN, "Pion", Color(0xFFC9F2B0), Color(0xFF6CCB3A)),
)

private val KidBlue = Color(0xFF1CB0F6) to Color(0xFF1483C2)
private val KidOrange = Color(0xFFFFA94D) to Color(0xFFE07F1F)
private val KidCoral = Color(0xFFFF6F61) to Color(0xFFD9483B)
private val KidGreen = Color(0xFF58CC02) to Color(0xFF3F9A00)

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
) {
    val fb = LocalFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val depth = 6.dp
    val sink by animateDpAsState(if (pressed && enabled) depth else 0.dp, spring(stiffness = Spring.StiffnessHigh), label = "sink")
    val face = if (enabled) colors.first else Color(0xFFCFE3F5)
    val edge = if (enabled) colors.second else Color(0xFFB3CDE3)
    val pill = RoundedCornerShape(50)
    Box(modifier.height(height)) {
        Box(Modifier.fillMaxSize().padding(top = depth).clip(pill).background(edge))
        Box(
            Modifier.fillMaxWidth().height(height - depth).offset(y = sink).clip(pill).background(face)
                .clickable(interactionSource = source, indication = null, enabled = enabled) { fb?.key(); onClick() }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            AutoText(label, color = if (enabled) Color.White else Color(0xFF8AA6C1), maxSize = maxSize, weight = FontWeight.Bold, align = TextAlign.Center)
        }
    }
}

/** Carte colorée d'une pièce, avec un léger rebond au toucher. */
@Composable
private fun PieceCard(p: KidPiece, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium), label = "bounce",
    )
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(p.bg).border(2.5.dp, p.edge, shape)
            .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }) }
            .padding(horizontal = 3.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(KID_PIECE_RES.getValue(p.type)), p.name, Modifier.fillMaxWidth(0.86f).aspectRatio(1f))
        AutoText(p.name, color = Color(0xFF1F3B57), maxSize = 14.sp, weight = FontWeight.Bold, align = TextAlign.Center)
    }
}

@Composable
fun KidScreen(vm: KidViewModel, onQuit: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    val pos = s.position
    val cur = s.cursor
    val last = if (cur > 0) s.line.moves[cur - 1] else null
    val whiteTurn = pos.whiteToMove
    ModuleLayout(
        bottomMin = 244.dp,
        top = {
            // Bulle flottante : la bordure change de couleur selon le joueur au trait.
            val accent = when {
                s.result != null -> KidGreen.first
                whiteTurn -> Color(0xFFFFB020)
                else -> Color(0xFF8B5CF6)
            }
            val bubble = RoundedCornerShape(32.dp)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp).shadow(8.dp, bubble).clip(bubble)
                    .background(Color.White).border(4.dp, accent, bubble).height(54.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                if (s.result == null) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(if (whiteTurn) Color.White else Color(0xFF1F2937))
                            .border(3.dp, if (whiteTurn) Color(0xFF94A3B8) else Color(0xFF0B1220), CircleShape),
                    )
                }
                val text = s.result ?: ((if (pos.inCheck) "Échec ! " else "") + if (whiteTurn) "Aux Blancs de jouer" else "Aux Noirs de jouer")
                AutoText(text, color = c.text, maxSize = 23.sp, weight = FontWeight.Bold, align = TextAlign.Center, modifier = Modifier.padding(start = 10.dp))
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
                    badges = if (last != null) listOfNotNull(captureBadge(s.line.positions[cur - 1], last)) else emptyList(),
                    kid = true,
                    onMove = vm::move,
                )
            }
        },
        bottom = {
            Column(Modifier.fillMaxSize().padding(vertical = 6.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                // Zone compacte : nom des pièces
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AutoText("Nom des pièces", color = c.text, maxSize = 19.sp, weight = FontWeight.Bold, align = TextAlign.Center)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (p in KID_LEGEND) PieceCard(p, Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KidButton("◀", KidBlue, vm::back, Modifier.weight(1f), enabled = cur > 0, maxSize = 24.sp)
                    KidButton("▶", KidBlue, vm::forward, Modifier.weight(1f), enabled = cur < s.line.size, maxSize = 24.sp)
                    if (s.result == null) KidButton("🏳️ Abandonner", KidOrange, vm::resign, Modifier.weight(1.9f), enabled = s.line.size > 0)
                    else KidButton("🎉 Rejouer", KidGreen, vm::reset, Modifier.weight(1.9f))
                }
                KidButton("✕ Quitter le mode enfant", KidCoral, onQuit, Modifier.fillMaxWidth(), height = 48.dp, maxSize = 16.sp)
            }
        },
    )
}
