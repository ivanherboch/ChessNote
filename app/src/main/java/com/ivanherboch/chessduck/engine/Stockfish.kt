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
import java.io.Closeable
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

/** Forme d'un coup UCI valide : case de départ, case d'arrivée et promotion éventuelle (ex. e2e4, e7e8q). */
private val UCI_MOVE = Regex("^[a-h][1-8][a-h][1-8][qrbn]?$")

/**
 * Stockfish embarqué (exécutable natif dans nativeLibraryDir), piloté en UCI, 100 % hors-ligne.
 * Toutes les recherches sont sérialisées et annulables sans bloquer le thread UI. Après une annulation,
 * le moteur est resynchronisé (stop puis isready/readyok) ; s'il ne répond plus, il est relancé, afin
 * qu'aucun « bestmove » orphelin ne soit lu par la recherche suivante.
 */
class Stockfish(private val context: Context) : Closeable {
    private val mutex = Mutex()
    private var process: Process? = null
    private var out: BufferedWriter? = null
    private var lines = Channel<String>(Channel.UNLIMITED)

    // Dernières options envoyées au processus actif : on ne les renvoie que si elles changent.
    private var sentLimit: Boolean? = null
    private var sentElo: Int? = null
    private var sentMultiPv: Int? = null

    private fun send(cmd: String) {
        out?.apply { write(cmd); write("\n"); flush() }
    }

    private suspend fun ensureStarted() {
        if (process?.isAlive == true) return
        shutdown()
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

    /** Attend une ligne commençant par [token] ; renvoie faux en cas de délai dépassé ou de flux fermé. */
    private suspend fun waitFor(token: String, timeoutMs: Long = 5000): Boolean =
        withTimeoutOrNull(timeoutMs) {
            for (l in lines) if (l.startsWith(token)) return@withTimeoutOrNull true
            false
        } ?: false

    /** Ferme les flux et arrête le processus ; les options mémorisées sont oubliées. */
    private fun shutdown(force: Boolean = false) {
        runCatching { out?.close() }
        runCatching { process?.inputStream?.close() }
        runCatching { if (force) process?.destroyForcibly() else process?.destroy() }
        lines.close()
        out = null
        process = null
        sentLimit = null; sentElo = null; sentMultiPv = null
    }

    /** Libère le moteur (fermeture propre des flux et du processus natif). */
    override fun close() {
        if (mutex.tryLock()) {
            try { shutdown() } finally { mutex.unlock() }
        } else {
            shutdown(force = true)
        }
    }

    private fun setOptions(elo: Int?, multiPv: Int) {
        val limit = elo != null
        if (sentLimit != limit) { send("setoption name UCI_LimitStrength value $limit"); sentLimit = limit }
        if (elo != null) {
            val e = elo.coerceIn(1350, 2850)
            if (sentElo != e) { send("setoption name UCI_Elo value $e"); sentElo = e }
        }
        if (sentMultiPv != multiPv) { send("setoption name MultiPV value $multiPv"); sentMultiPv = multiPv }
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
                setOptions(elo, multiPv)
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
                            best = if (tok != null && UCI_MOVE.matches(tok)) Move.fromUci(tok) else null
                            done = true
                            break
                        }
                    }
                } finally {
                    if (!done) withContext(NonCancellable) {
                        // Annulation : on arrête la recherche, on consomme son « bestmove », puis on vérifie
                        // que le moteur est de nouveau prêt. Sinon il est relancé à la prochaine recherche.
                        runCatching { send("stop") }
                        val stopped = waitFor("bestmove", 1500)
                        runCatching { send("isready") }
                        val ready = stopped && waitFor("readyok", 1000)
                        if (!ready) shutdown(force = true)
                    }
                }
                SearchResult(best, pvs.toSortedMap().values.toList())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                shutdown(force = true)
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
                "pv" -> { move = t.getOrNull(i + 1)?.takeIf { UCI_MOVE.matches(it) }?.let { Move.fromUci(it) }; i = t.size }
            }
            i++
        }
        if (" upperbound" in l || " lowerbound" in l) return null
        return if (score != null && move != null) idx to PvLine(move, score) else null
    }
}
