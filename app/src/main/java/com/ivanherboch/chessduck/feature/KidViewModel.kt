package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.chess.SpokenFr
import com.ivanherboch.chessduck.ui.Narrator
import com.ivanherboch.chessduck.data.SettingsRepository
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
class KidViewModel(
    prefs: SettingsRepository,
    private val narrator: Narrator,
    /** Horloge monotone en millisecondes (remplaçable dans les tests). */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {
    private val _state = MutableStateFlow(KidState())
    val state: StateFlow<KidState> = _state.asStateFlow()

    // ---- Voix des fiches de pièces : réglage parental, une seule lecture par ouverture, jamais en partie ----
    private val kidNarration = prefs.settings.map { it.kidNarration }.distinctUntilChanged()
        .onEach { if (!it) narrator.stop() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private var lastPiece = 0
    private var lastSpokenAt = 0L

    /**
     * Fiche d'une pièce ouverte : son explication est lue une fois. Rouvrir la même pièce dans les
     * [REPEAT_COOLDOWN_MS] qui suivent ne relance pas la voix ; une autre pièce coupe la précédente.
     */
    fun pieceShown(type: Int, text: String) {
        if (!kidNarration.value) return
        val now = clock()
        if (type == lastPiece && now - lastSpokenAt < REPEAT_COOLDOWN_MS) return
        lastPiece = type
        lastSpokenAt = now
        narrator.speak(SpokenFr.text(text))
    }

    /** Fiche fermée, écran quitté ou application en arrière-plan : la voix se tait aussitôt. */
    fun pieceClosed() = narrator.stop()

    override fun onCleared() = narrator.stop()

    private companion object {
        const val REPEAT_COOLDOWN_MS = 30_000L
    }

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
