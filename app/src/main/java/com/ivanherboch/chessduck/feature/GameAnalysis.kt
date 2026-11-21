package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.data.GameRepository
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.chess.GameEnd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Analyse des positions et des parties terminées, partagée par le Jeu Libre (indices, analyse lancée à la fin
 * d'une partie) et la revue. Les analyses en cours ou terminées sont publiées par partie dans [analyses] :
 * la revue s'ouvre sans rien recalculer, et l'analyse d'une partie continue même si la revue est fermée.
 */
class GameAnalysisService(
    private val engine: ChessEngine,
    private val games: GameRepository,
    private val scope: CoroutineScope,
) {
    private val _analyses = MutableStateFlow<Map<Long, List<Analysis?>>>(emptyMap())
    val analyses: StateFlow<Map<Long, List<Analysis?>>> = _analyses.asStateFlow()
    private var job: Job? = null
    private var gameId = -1L

    /** Évaluation d'une position (point de vue des Blancs) et meilleur coup ; null si le moteur ne répond pas. */
    suspend fun analyse(pos: Position, ms: Int): Analysis? {
        if (pos.legalMoves.isEmpty()) return Analysis.decided(pos, if (pos.inCheck) GameEnd.CHECKMATE else GameEnd.STALEMATE)
        val r = engine.search(pos.toFen(), ms, multiPv = 2)
        val first = r.lines.firstOrNull() ?: return null
        val sign = if (pos.whiteToMove) 1 else -1
        return Analysis(sign * first.score, r.lines.getOrNull(1)?.let { sign * it.score }, first.move.uci)
    }

    /**
     * Range l'analyse de la partie [id] en tête du cache, borné aux [MAX_CACHED] parties les plus récentes :
     * les plus anciennes restent relues depuis la base via [analysisOf].
     */
    private fun publish(id: Long, list: List<Analysis?>) = _analyses.update { m ->
        val next = LinkedHashMap<Long, List<Analysis?>>(m)
        next.remove(id)
        next[id] = list
        val it = next.keys.iterator()
        while (next.size > MAX_CACHED && it.hasNext()) { it.next(); it.remove() }
        next
    }

    /** Analyses connues d'une partie (cache), sinon celles enregistrées avec la partie. */
    fun analysisOf(g: GameRecord, line: Line): List<Analysis?> = _analyses.value[g.id] ?: Analysis.decodeAll(g.analysis, line.size + 1)

    /**
     * Analyse une partie en tâche de fond (une position après l'autre, recherche courte) et enregistre le résultat.
     * Lancée dès la fin de la partie : quand on ouvre la revue, l'essentiel est déjà prêt.
     */
    /** Une analyse de partie est en cours (le moteur ne doit pas être libéré pendant ce temps). */
    val busy: Boolean get() = job?.isActive == true

    fun start(g: GameRecord, line: Line) {
        if (gameId == g.id && job?.isActive == true) return
        job?.cancel()
        gameId = g.id
        val list = analysisOf(g, line).toMutableList()
        publish(g.id, list.toList())
        job = scope.launch {
            var changed = false
            try {
                for (i in 0..line.size) {
                    val existing = list[i]
                    if (existing != null && (existing.second != null || existing.best.isEmpty())) continue
                    val a = analyse(line.positions[i], 110) ?: continue
                    list[i] = a
                    changed = true
                    val snapshot = list.toList()
                    publish(g.id, snapshot)
                }
            } finally {
                // Même interrompue, l'analyse déjà faite est conservée.
                if (changed) withContext(NonCancellable) { games.saveAnalysis(g.id, Analysis.encodeAll(list)) }
            }
        }
    }

    private companion object {
        const val MAX_CACHED = 20
    }
}
