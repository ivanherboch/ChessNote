package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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

@Immutable
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
    private val _state = MutableStateFlow(KidState())
    val state: StateFlow<KidState> = _state.asStateFlow()

    fun reset() = _state.update { KidState() }

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
        _state.update { it.copy(line = nl, cursor = nl.size, anim = moveAnim(s.position, m), result = result) }
    }

    fun back() {
        val s = state.value
        if (s.cursor <= 0) return
        val t = s.cursor - 1
        _state.update { it.copy(cursor = t, anim = undoAnim(s.line.positions[t], s.line.moves[t]), result = null) }
    }

    fun forward() {
        val s = state.value
        if (s.cursor >= s.line.size) return
        _state.update { it.copy(cursor = s.cursor + 1, anim = moveAnim(s.position, s.line.moves[s.cursor])) }
    }

    fun resign() {
        val s = state.value
        if (s.result != null) return
        val loser = if (s.position.whiteToMove) "Blancs" else "Noirs"
        val winner = if (s.position.whiteToMove) "Noirs" else "Blancs"
        _state.update { it.copy(result = "🏳️ Les $loser abandonnent : bravo aux $winner !") }
    }
}
