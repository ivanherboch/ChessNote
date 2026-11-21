package com.ivanherboch.chessduck.feature

/** Un problème à tirer : thèmes (null = tous) et tranche de classement. */
data class PuzzleSpec(val themes: List<String>?, val range: IntRange)

/**
 * Tests de l'Académie, sur des problèmes embarqués jamais annoncés par leur thème.
 * - Test de placement (facultatif) : 3 positions par palier, du palier 2 au palier 5 ; on retient le dernier palier
 *   réussi (au moins 2 sur 3) sans interruption.
 * - Examen de palier : 10 positions mélangées (tactique, défense, finale courte) ; réussite à 8 sur 10 (80 %).
 */
object AcademyAssessments {
    const val PLACEMENT_PER_LEVEL = 3
    const val EXAM_SIZE = 10
    const val EXAM_PASS = 8
    const val KIND_PLACEMENT = "placement"
    const val KIND_EXAM = "exam"

    val TACTICS = listOf(
        "fork", "pin", "skewer", "discoveredAttack", "doubleCheck", "deflection", "attraction", "hangingPiece",
        "trappedPiece", "capturingDefender", "backRankMate", "mateIn1", "mateIn2", "sacrifice", "xRayAttack",
    )
    val ENDGAMES = listOf("pawnEndgame", "rookEndgame", "queenEndgame", "knightEndgame", "bishopEndgame")

    /** Tranche de classement des problèmes d'un palier (1 Les bases … 5 Confirmé). */
    fun levelRange(level: Int): IntRange = when (level) {
        // Les problèmes « débutant » de la base sont surtout entre 1100 et 1199 : la borne inclut cette tranche.
        1, 2 -> 400..1199
        3 -> 1000..1500
        4 -> 1400..1900
        else -> 1800..2500
    }

    fun placementSpecs(): List<PuzzleSpec> = (2..5).flatMap { lv -> List(PLACEMENT_PER_LEVEL) { PuzzleSpec(null, levelRange(lv)) } }

    /** Palier atteint d'après les résultats du placement, dans l'ordre des paliers 2 à 5 (1 si le palier 2 échoue). */
    fun placementLevel(results: List<Boolean>): Int {
        var level = 1
        for ((i, lv) in (2..5).withIndex()) {
            val part = results.drop(i * PLACEMENT_PER_LEVEL).take(PLACEMENT_PER_LEVEL)
            if (part.size == PLACEMENT_PER_LEVEL && part.count { it } >= 2) level = lv else break
        }
        return level
    }

    /** Examen du palier [level] : 6 positions tactiques, 2 défensives, 2 finales, dans un ordre mélangé. */
    fun examSpecs(level: Int, random: kotlin.random.Random): List<PuzzleSpec> {
        val r = levelRange(level)
        return (List(6) { PuzzleSpec(TACTICS, r) } + List(2) { PuzzleSpec(listOf("defensiveMove"), r) } + List(2) { PuzzleSpec(ENDGAMES, r) })
            .shuffled(random)
    }

    fun examPassed(score: Int): Boolean = score >= EXAM_PASS
}
