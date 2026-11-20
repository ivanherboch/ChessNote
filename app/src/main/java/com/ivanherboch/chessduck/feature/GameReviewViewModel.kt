package com.ivanherboch.chessduck.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.ui.LinePlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import com.ivanherboch.chessduck.ui.BoardAnim
import kotlinx.coroutines.flow.first

@Immutable
data class Review(
    val record: GameRecord,
    val line: Line,
    val cursor: Int = 0,
    val playing: Boolean = true,
    val analysis: List<Analysis?>,
    val flipped: Boolean,
    val evalOn: Boolean = true,
    val anim: BoardAnim? = null,
)

/**
 * Revue commentée d'une partie de l'historique : lecture automatique, navigation coup par coup,
 * jauge d'évaluation et analyses publiées au fil de l'eau par [GameAnalysisService].
 */
class GameReviewViewModel(private val analysis: GameAnalysisService) : ViewModel() {
    private val _review = MutableStateFlow<Review?>(null)
    /** Revue ouverte, ou null si l'on est sur la partie en cours. */
    val review: StateFlow<Review?> = _review.asStateFlow()
    private val playback = LinePlaybackController(viewModelScope)

    init {
        // Les analyses arrivent en tâche de fond : la revue ouverte les reçoit dès qu'elles sont prêtes.
        viewModelScope.launch {
            analysis.analyses.collect { all ->
                _review.update { r -> r?.let { all[it.record.id]?.let { a -> it.copy(analysis = a) } ?: it } }
            }
        }
    }

    /** Ouvre une partie de l'historique sur son résumé, en pause : le joueur lance la lecture quand il veut. */
    fun openReview(g: GameRecord) {
        playback.cancel()
        val line = runCatching { Line.fromUci(g.startFen.ifEmpty { START_FEN }, g.moves) }.getOrNull() ?: return
        _review.value = Review(g, line, 0, false, analysis.analysisOf(g, line), flipped = g.vsComputer && !g.playerWhite)
        analysis.start(g, line)
    }

    private fun autoplay() {
        playback.autoplay(
            cursor = { _review.value?.cursor }, size = { _review.value?.line?.size ?: 0 },
            delayFor = { 1150L }, checkBeforeDelay = false,
            advance = ::go,
            onEnd = { _review.update { it?.copy(playing = false) } },
        )
    }

    private fun go(target: Int) {
        _review.update { r ->
            if (r == null) return@update null
            val t = target.coerceIn(0, r.line.size)
            if (t == r.cursor) r else r.copy(cursor = t, anim = LinePlaybackController.transition(r.line, r.cursor, t))
        }
    }

    fun pause() {
        playback.cancel()
        _review.update { it?.copy(playing = false) }
    }

    fun toggle() {
        val r = _review.value ?: return
        if (r.playing) return pause()
        if (r.cursor >= r.line.size) go(0)
        _review.update { it?.copy(playing = true) }
        autoplay()
    }

    /** Saute directement au coup [n] (clic dans la liste ou le tableau des coups). */
    fun jump(n: Int) { pause(); go(n) }

    fun step(delta: Int) { pause(); go((_review.value?.cursor ?: 0) + delta) }
    fun first() { pause(); go(0) }
    fun last() { pause(); go(Int.MAX_VALUE) }

    fun flip() = _review.update { it?.copy(flipped = !it.flipped) }
    fun toggleEval() = _review.update { it?.copy(evalOn = !it.evalOn) }

    fun close() {
        // L'analyse continue en arrière-plan : elle sera prête à la prochaine ouverture.
        playback.cancel()
        _review.value = null
    }
}
