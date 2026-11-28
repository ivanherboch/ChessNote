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

/** Nombre de coups du camp [white] pour mater d'après [evalWhite] (point de vue des Blancs), sinon null. */
fun mateFor(evalWhite: Int, white: Boolean): Int? =
    if (isMateScore(evalWhite) && (evalWhite > 0) == white) kotlin.math.abs(mateIn(evalWhite)) else null

/** Probabilité de gain (0–100) estimée à partir d'une évaluation en centipions (formule Lichess). */
fun winPercent(cp: Int): Double {
    if (isMateScore(cp)) return if (cp > 0) 100.0 else 0.0
    return 50.0 + 50.0 * (2.0 / (1.0 + exp(-0.00368208 * cp)) - 1.0)
}

/** Moteur d'analyse (Stockfish en production, faux moteur dans les tests). */
interface ChessEngine {
    /** Recherche depuis [fen] ; [elo] non nul bride la force ; résultat vide si le moteur est indisponible. */
    suspend fun search(fen: String, movetimeMs: Int, multiPv: Int = 1, elo: Int? = null, depth: Int? = null): SearchResult
}


/**
 * Stockfish piloté en UCI, 100 % hors-ligne. Dans l'application, c'est l'exécutable natif embarqué
 * (nativeLibraryDir) ; les outils de test peuvent lui passer un autre exécutable UCI.
 * Toutes les recherches sont sérialisées et annulables sans bloquer le thread UI. Après une annulation,
 * le moteur est resynchronisé (stop puis isready/readyok) ; s'il ne répond plus, il est relancé, afin
 * qu'aucun « bestmove » orphelin ne soit lu par la recherche suivante.
 */
class Stockfish(private val executable: File) : ChessEngine, Closeable {
    constructor(context: Context) : this(File(context.applicationInfo.nativeLibraryDir, "libstockfish.so"))

    private val mutex = Mutex()
    /** Lu sans verrou par [close] (arrêt forcé pendant une recherche) : seule la référence est partagée. */
    @Volatile private var process: Process? = null
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
        val p = ProcessBuilder(executable.absolutePath).redirectErrorStream(true).start()
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
        // Moteur muet au démarrage (binaire absent, plantage, appareil saturé) : le processus est arrêté et la
        // recherche échoue proprement (résultat vide) au lieu d'attendre un « bestmove » qui ne viendra jamais.
        if (!waitFor("uciok")) failStart("uciok")
        send("setoption name Hash value 32")
        send("setoption name Threads value ${Runtime.getRuntime().availableProcessors().coerceIn(1, 2)}")
        send("isready")
        if (!waitFor("readyok")) failStart("readyok")
    }

    private fun failStart(token: String): Nothing {
        shutdown(force = true)
        throw IllegalStateException("Stockfish n'a pas répondu « $token »")
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

    /**
     * Libère le moteur (fermeture propre des flux et du processus natif). Si une recherche tient le verrou, seul le
     * processus est détruit : son flux se ferme, la recherche se termine d'elle-même et remet les champs à zéro
     * sous le verrou (aucun champ n'est modifié ici pendant qu'un autre thread s'en sert). Le moteur n'est pas condamné :
     * il redémarre de lui-même à la recherche suivante (voir AppContainer.release, nouvelle activité dans le même processus).
     */
    override fun close() {
        if (mutex.tryLock()) {
            try { shutdown() } finally { mutex.unlock() }
        } else {
            runCatching { process?.destroyForcibly() }
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
    override suspend fun search(
        fen: String,
        movetimeMs: Int,
        multiPv: Int,
        elo: Int?,
        depth: Int?,
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
                    // Délai de garde : la recherche est bornée par movetime, mais un moteur bloqué ou un flux qui ne se
                    // ferme pas ne doit jamais garder le verrou indéfiniment (Jeu Libre, analyse des parties).
                    withTimeoutOrNull(movetimeMs + SEARCH_GRACE_MS) {
                        for (l in lines) {
                            if (l.startsWith("info") && " pv " in l && " score " in l) {
                                parseInfo(l)?.let { (idx, pv) -> pvs[idx] = pv }
                            } else if (l.startsWith("bestmove")) {
                                val tok = l.split(' ').getOrNull(1)
                                best = if (tok != null && Move.isUci(tok)) Move.fromUci(tok) else null
                                done = true
                                break
                            }
                        }
                    }
                } finally {
                    if (!done) withContext(NonCancellable) {
                        // Annulation, délai dépassé ou flux fermé : on arrête la recherche, on consomme son « bestmove », puis on vérifie
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

    private companion object {
        /** Marge accordée au moteur au-delà de movetime avant de le considérer comme bloqué (téléphone lent compris). */
        const val SEARCH_GRACE_MS = 4_000L
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
                "pv" -> { move = t.getOrNull(i + 1)?.takeIf { Move.isUci(it) }?.let { Move.fromUci(it) }; i = t.size }
            }
            i++
        }
        if (" upperbound" in l || " lowerbound" in l) return null
        return if (score != null && move != null) idx to PvLine(move, score) else null
    }
}
