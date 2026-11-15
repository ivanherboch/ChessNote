package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.ViewModel
import com.ivanherboch.chessduck.chess.GameEnd
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.ui.BoardAnim
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
