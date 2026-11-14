package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.ivanherboch.chessduck.data.PuzzleRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import com.ivanherboch.chessduck.chess.PieceLexiconFr
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.ivanherboch.chessduck.chess.BISHOP
import com.ivanherboch.chessduck.chess.ROOK
import com.ivanherboch.chessduck.chess.QUEEN
import com.ivanherboch.chessduck.util.runCatchingSuspend
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.InfoPill
import com.ivanherboch.chessduck.ui.LoadingState
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.ChessDuckApp
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.PIECE_NAMES_EN
import com.ivanherboch.chessduck.chess.PIECE_NAMES_FR
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.pieceLetter
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.sqName
import com.ivanherboch.chessduck.data.DuckDatabase
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.AutoText
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.SectionTitle
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.moveAnim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Immutable
data class NotationState(
    val level: Int = 0,
    val exercise: NotationExercise? = null,
    val shown: Boolean = false,
    val anim: BoardAnim? = null,
    val flipped: Boolean = false,
    val input: String = "",
    val message: String = "Observez le coup joué sur l'échiquier, puis écrivez-le en notation anglaise FIDE.",
    val tone: Tone = Tone.INFO,
    val solved: Boolean = false,
    val hintStage: Int = 0,
    val streak: Int = 0,
    /** Meilleure série de bonnes réponses (persistée). */
    val best: Int = 0,
)

class NotationViewModel(
    private val prefs: SettingsRepository,
    private val puzzles: PuzzleRepository,
) : ViewModel() {
    private val rnd = Random(System.nanoTime())
    private val _state = MutableStateFlow(NotationState())
    val state: StateFlow<NotationState> = _state.asStateFlow()
    private var showJob: Job? = null
    /** Génération d'exercice en cours : annulée si l'on change de niveau ou redemande un exercice. */
    private var nextJob: Job? = null

    init {
        viewModelScope.launch {
            val st = prefs.settings.first()
            _state.update { it.copy(level = st.notationLevel, best = st.notationBest) }
            next()
        }
    }

    fun setLevel(level: Int) {
        _state.update { it.copy(level = level) }
        viewModelScope.launch { prefs.setNotationLevel(level) }
        next()
    }

    fun next() {
        nextJob?.cancel()
        nextJob = viewModelScope.launch {
            val level = state.value.level
            val ex = withContext(Dispatchers.Default) { generate(level) }
            _state.update {
                it.copy(
                    exercise = ex, shown = false, anim = null, flipped = !ex.before.whiteToMove, input = "", solved = false, hintStage = 0,
                    message = "Quel coup vient d'être joué ? Écrivez-le en notation anglaise FIDE.", tone = Tone.INFO,
                )
            }
            play(450)
        }
    }

    private suspend fun generate(level: Int): NotationExercise {
        val dao = puzzles
        val target = if (level == 0) rnd.nextInt(1, 4) else level
        runCatchingSuspend {
            repeat(6) {
                val start = rnd.nextInt(1, DuckDatabase.PUZZLE_COUNT - 40)
                val kind = if (target == 3) rnd.nextInt(1, 5) else 0
                val batch: List<Puzzle> = when (kind) {
                    1 -> dao.sliceCastling(start, 60)
                    2 -> dao.sliceTheme(start, "promotion", 30)
                    3 -> dao.sliceTheme(start, "mateIn1", 30)
                    else -> dao.slice(start, 30)
                }
                val positions = batch.flatMap { pz ->
                    val line = runCatching { Line.fromUci(pz.fen, pz.moves) }.getOrNull() ?: return@flatMap emptyList()
                    when (kind) {
                        1 -> listOf(line.positions[0], line.positions[1])
                        3 -> listOf(line.positions[line.size - 1])
                        else -> line.positions.dropLast(1)
                    }
                }
                NotationLogic.pick(positions, target, rnd, kind)?.let { return it }
            }
        }
        val positions = NotationLogic.FALLBACK.map { Position.fromFen(it) }
        return NotationLogic.pick(positions, target, rnd) ?: NotationLogic.pick(positions, 0, rnd)!!
    }

    private fun play(delayMs: Long) {
        showJob?.cancel()
        showJob = viewModelScope.launch {
            delay(delayMs)
            val ex = state.value.exercise ?: return@launch
            _state.update { it.copy(shown = true, anim = moveAnim(ex.before, ex.move)) }
        }
    }

    /** Rejoue l'animation une seule fois, à la demande. */
    fun replay() {
        _state.update { it.copy(shown = false, anim = null) }
        play(350)
    }

    fun flip() = _state.update { it.copy(flipped = !it.flipped) }

    /** Ajoute [key] à la saisie ; renvoie faux (et explique pourquoi) si la touche est refusée. */
    fun type(key: String): Boolean {
        val s = state.value
        if (s.solved) return false
        if (key.startsWith("O-O")) {
            // Le roque s'écrit seul : il ne doit jamais écraser en silence une saisie commencée.
            if (s.input.isNotEmpty() && !s.input.startsWith("O-O")) {
                _state.update { it.copy(tone = Tone.GOLD, message = "Le roque s'écrit seul (O-O ou O-O-O). Effacez d'abord la saisie : touche « Tout effacer » ou appui long sur ⌫.") }
                return false
            }
            _state.update { it.copy(input = key) }
            return true
        }
        if (s.input.startsWith("O-O") && key != "+" && key != "#") {
            _state.update { it.copy(tone = Tone.GOLD, message = "Après un roque, seuls « + » (échec) ou « # » (mat) peuvent suivre.") }
            return false
        }
        if (s.input.length + key.length > 9) return false
        _state.update { it.copy(input = it.input + key) }
        return true
    }

    fun backspace() = _state.update { if (it.solved) it else it.copy(input = it.input.dropLast(1)) }
    fun clear() = _state.update { if (it.solved) it else it.copy(input = "") }

    fun hint() {
        val s = state.value
        val ex = s.exercise ?: return
        if (s.solved) return
        val stage = (s.hintStage + 1).coerceAtMost(2)
        _state.update { it.copy(hintStage = stage, message = NotationLogic.hint(ex, stage), tone = Tone.GOLD) }
    }

    /** Valide la saisie ; renvoie vrai si la réponse est exacte. */
    fun validate(): Boolean {
        val s = state.value
        val ex = s.exercise ?: return false
        if (s.input == ex.san) {
            val streak = s.streak + 1
            _state.update {
                it.copy(
                    solved = true, tone = Tone.GOOD, streak = streak, best = maxOf(it.best, streak),
                    message = "Exact : ${ex.san}. " + explain(ex),
                )
            }
            if (streak > s.best) viewModelScope.launch { prefs.setNotationBest(streak) }
            return true
        }
        _state.update { it.copy(tone = Tone.BAD, streak = 0, message = NotationLogic.diagnose(s.input, ex)) }
        return false
    }

    private fun explain(ex: NotationExercise): String {
        val p = ex.before
        val m = ex.move
        val type = pieceType(p[m.from])
        return when {
            p.isCastle(m) -> if (ex.san.startsWith("O-O-O")) "Grand roque, côté dame." else "Petit roque, côté roi."
            type == PAWN && m.promo != 0 -> "Le pion est promu en ${PIECE_NAMES_FR[m.promo]} (${PIECE_NAMES_EN[m.promo]})."
            type == PAWN -> "Le pion va en ${sqName(m.to)}" + if (p.isCapture(m)) " en capturant." else "."
            else -> "${PIECE_NAMES_FR[type]} (${PIECE_NAMES_EN[type]}) en ${sqName(m.to)}" + if (p.isCapture(m)) ", avec prise." else "."
        }
    }
}
