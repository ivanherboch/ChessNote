package com.ivanherboch.chessduck.engine

import android.content.Context
import com.ivanherboch.chessduck.chess.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.exp

/** Une variante principale : meilleur coup et score du point de vue du camp au trait. */
data class PvLine(val move: Move, val score: Int)

data class SearchResult(val best: Move?, val lines: List<PvLine>) {
    val score: Int? get() = lines.firstOrNull()?.score
}

/** Scores : centipions ; un mat en n est codé ±(MATE - n). */
const val MATE = 100_000
fun isMateScore(s: Int) = kotlin.math.abs(s) > MATE - 1000
fun mateIn(s: Int) = if (s > 0) (MATE - s + 1) / 2 else -((MATE + s + 1) / 2)

/** Probabilité de gain (0–100) estimée à partir d'une évaluation en centipions (formule Lichess). */
fun winPercent(cp: Int): Double {
    if (isMateScore(cp)) return if (cp > 0) 100.0 else 0.0
    return 50.0 + 50.0 * (2.0 / (1.0 + exp(-0.00368208 * cp)) - 1.0)
}

/**
 * Stockfish embarqué (exécutable natif dans nativeLibraryDir), piloté en UCI, 100 % hors-ligne.
 * Toutes les recherches sont sérialisées et annulables sans bloquer le thread UI.
 */
class Stockfish(private val context: Context) {
    private val mutex = Mutex()
    private var process: Process? = null
    private var out: BufferedWriter? = null
    private var lines = Channel<String>(Channel.UNLIMITED)

    private fun send(cmd: String) {
        out?.apply { write(cmd); write("\n"); flush() }
    }

    private suspend fun ensureStarted() {
        if (process?.isAlive == true) return
        val exe = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
        val p = ProcessBuilder(exe.absolutePath).redirectErrorStream(true).start()
        val ch = Channel<String>(Channel.UNLIMITED)
        thread(isDaemon = true, name = "stockfish-reader") {
            try {
                p.inputStream.bufferedReader().forEachLine { ch.trySend(it) }
            } catch (_: Exception) {
            }
            ch.close()
        }
        process = p
        out = p.outputStream.bufferedWriter()
        lines = ch
        send("uci")
        waitFor("uciok")
        send("setoption name Hash value 32")
        send("setoption name Threads value ${Runtime.getRuntime().availableProcessors().coerceIn(1, 2)}")
        send("isready")
        waitFor("readyok")
    }

    private suspend fun waitFor(token: String) {
        withTimeoutOrNull(5000) {
            for (l in lines) if (l.startsWith(token)) break
        }
    }

    /**
     * Lance une recherche. [elo] non nul limite la force (UCI_LimitStrength, 1350–2850).
     * Renvoie un résultat vide si le moteur est indisponible.
     */
    suspend fun search(
        fen: String,
        movetimeMs: Int,
        multiPv: Int = 1,
        elo: Int? = null,
        depth: Int? = null,
    ): SearchResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                ensureStarted()
                send("setoption name UCI_LimitStrength value ${elo != null}")
                if (elo != null) send("setoption name UCI_Elo value ${elo.coerceIn(1350, 2850)}")
                send("setoption name MultiPV value $multiPv")
                send("position fen $fen")
                send(if (depth != null) "go depth $depth movetime $movetimeMs" else "go movetime $movetimeMs")
                val pvs = HashMap<Int, PvLine>()
                var best: Move? = null
                var done = false
                try {
                    for (l in lines) {
                        if (l.startsWith("info") && " pv " in l && " score " in l) {
                            parseInfo(l)?.let { (idx, pv) -> pvs[idx] = pv }
                        } else if (l.startsWith("bestmove")) {
                            val tok = l.split(' ').getOrNull(1)
                            best = if (tok == null || tok == "(none)") null else Move.fromUci(tok)
                            done = true
                            break
                        }
                    }
                } finally {
                    if (!done) withContext(NonCancellable) {
                        // Annulation : on arrête proprement la recherche en cours.
                        runCatching { send("stop") }
                        withTimeoutOrNull(1500) { for (l in lines) if (l.startsWith("bestmove")) break }
                    }
                }
                SearchResult(best, pvs.toSortedMap().values.toList())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { process?.destroy() }
                process = null
                SearchResult(null, emptyList())
            }
        }
    }

    private fun parseInfo(l: String): Pair<Int, PvLine>? {
        val t = l.split(' ')
        var idx = 1
        var score: Int? = null
        var move: Move? = null
        var i = 0
        while (i < t.size) {
            when (t[i]) {
                "multipv" -> idx = t.getOrNull(i + 1)?.toIntOrNull() ?: 1
                "score" -> {
                    val v = t.getOrNull(i + 2)?.toIntOrNull() ?: 0
                    score = if (t.getOrNull(i + 1) == "mate") (if (v > 0) MATE - (2 * v - 1) else -MATE - 2 * v) else v
                }
                "pv" -> { move = t.getOrNull(i + 1)?.let { runCatching { Move.fromUci(it) }.getOrNull() }; i = t.size }
            }
            i++
        }
        if (" upperbound" in l || " lowerbound" in l) return null
        return if (score != null && move != null) idx to PvLine(move, score) else null
    }
}
