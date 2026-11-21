package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.fileOf
import com.ivanherboch.chessduck.chess.rankOf
import com.ivanherboch.chessduck.chess.sq
import com.ivanherboch.chessduck.chess.sqName
import com.ivanherboch.chessduck.ui.Tone
import kotlin.random.Random

/** Les trois exercices de « Vision des cases ». */
enum class VisionKind(val label: String) {
    COLOR("Couleur de la case"),
    TOUCH("Toucher la case"),
    KNIGHT("Chemin du cavalier"),
}

/** Une question : [square] (case demandée, ou départ du cavalier) et [target] (arrivée du cavalier). */
@Immutable
data class VisionQuestion(val kind: VisionKind, val square: Int, val target: Int = -1)

/**
 * Exercice facultatif de visualisation, sans chronomètre : l'élève répond à son rythme. Les réponses se donnent
 * par des boutons ou en touchant une case, ce qui reste utilisable avec TalkBack (chaque case est annoncée).
 */
@Immutable
data class VisionSession(
    val question: VisionQuestion,
    val answered: Boolean = false,
    val correct: Boolean = false,
    /** Case touchée (toucher la case). */
    val tapped: Int = -1,
    val score: Int = 0,
    val total: Int = 0,
    val message: String = "",
    val tone: Tone = Tone.INFO,
) {
    /** Échiquier affiché : vide, avec le cavalier sur sa case de départ pour le chemin du cavalier (position mise en cache). */
    val position: Position
        get() = SquareVision.positionFor(question)
}

object SquareVision {
    const val EMPTY = "8/8/8/8/8/8/8/8 w - - 0 1"

    private val emptyPosition: Position by lazy { Position.fromFen(EMPTY) }
    /** Échiquiers « cavalier seul », un par case de départ, construits une seule fois (lus à chaque recomposition). */
    private val knightPositions = arrayOfNulls<Position>(64)

    /** Échiquier d'une question, sans réanalyser la FEN à chaque lecture. */
    fun positionFor(q: VisionQuestion): Position =
        if (q.kind != VisionKind.KNIGHT) emptyPosition
        else synchronized(knightPositions) { knightPositions[q.square] ?: Position.fromFen(withKnight(q.square)).also { knightPositions[q.square] = it } }
    /** Case claire ? (a1 est une case foncée.) */
    fun isLight(square: Int): Boolean = (fileOf(square) + rankOf(square)) % 2 == 1

    private val JUMPS = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)

    /** Nombre minimal de sauts de cavalier pour aller de [from] à [to] (parcours en largeur sur l'échiquier vide). */
    fun knightDistance(from: Int, to: Int): Int {
        if (from == to) return 0
        val dist = IntArray(64) { -1 }
        dist[from] = 0
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val q = queue.removeFirst()
            for ((df, dr) in JUMPS) {
                val f = fileOf(q) + df
                val r = rankOf(q) + dr
                if (f !in 0..7 || r !in 0..7) continue
                val n = sq(f, r)
                if (dist[n] >= 0) continue
                dist[n] = dist[q] + 1
                if (n == to) return dist[n]
                queue.addLast(n)
            }
        }
        return -1
    }

    /** FEN d'un échiquier vide avec un cavalier blanc en [square]. */
    fun withKnight(square: Int): String {
        val rows = (7 downTo 0).joinToString("/") { r ->
            if (r != rankOf(square)) "8" else {
                val f = fileOf(square)
                (if (f > 0) "$f" else "") + "N" + (if (f < 7) "${7 - f}" else "")
            }
        }
        return "$rows w - - 0 1"
    }

    /** Question au hasard ; [kind] null : un des trois exercices. Chemin du cavalier : 1 à 4 sauts. */
    fun question(rnd: Random, kind: VisionKind? = null): VisionQuestion {
        val k = kind ?: VisionKind.entries.random(rnd)
        val a = rnd.nextInt(64)
        if (k != VisionKind.KNIGHT) return VisionQuestion(k, a)
        while (true) {
            val b = rnd.nextInt(64)
            if (knightDistance(a, b) in 1..4) return VisionQuestion(k, a, b)
        }
    }

    fun prompt(q: VisionQuestion): String = when (q.kind) {
        VisionKind.COLOR -> "Sans regarder l'échiquier : la case ${sqName(q.square)} est-elle claire ou foncée ?"
        VisionKind.TOUCH -> "Touchez la case ${sqName(q.square)}."
        VisionKind.KNIGHT -> "Combien de sauts faut-il au cavalier pour aller de ${sqName(q.square)} à ${sqName(q.target)} ?"
    }
}
