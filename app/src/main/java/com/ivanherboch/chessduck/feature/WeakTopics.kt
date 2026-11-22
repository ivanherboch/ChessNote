package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.data.LessonProgress

/**
 * Point faible repéré dans une partie analysée : la leçon de l'Académie qui l'enseigne et le thème des
 * 5 exercices proposés pour le travailler.
 */
enum class ReviewTopic(val label: String, val lessonId: Int, val theme: String) {
    MISSED_MATE("Mat manqué", 131, "mateIn2"),
    ALLOWED_MATE("Mat permis à l'adversaire", 132, "defensiveMove"),
    HANGING_PIECE("Pièce laissée en prise", 136, "hangingPiece"),
    MISSED_CAPTURE("Prise gagnante manquée", 133, "hangingPiece"),
    CASTLING("Roque retardé", 73, "castling"),
    DEVELOPMENT("Développement en retard", 1, "opening"),
}

/** Une priorité du bilan : ce qu'il faut travailler, pourquoi, et où. [theme] vide : la leçon seule. */
@Immutable
data class Priority(val title: String, val reason: String, val lessonId: Int, val theme: String, val score: Int)

/**
 * Bilan des thèmes faibles : les 3 priorités du moment, tirées des erreurs des parties analysées (Jeu Libre) et
 * des difficultés rencontrées dans l'Académie (erreurs en Défi, exercices de validation tirés des Problèmes).
 */
object WeakTopics {
    /** Nombre de parties récentes examinées. */
    const val GAMES = 20

    /** Erreurs du joueur humain dans une partie analysée, par point faible. */
    fun fromGame(g: GameRecord): Map<ReviewTopic, Int> {
        if (g.analysis.isEmpty()) return emptyMap()
        val line = runCatching { Line.fromUci(g.startFen.ifEmpty { com.ivanherboch.chessduck.chess.START_FEN }, g.moves) }.getOrNull()
            ?: return emptyMap()
        val analysis = Analysis.decodeAll(g.analysis, line.size + 1)
        val out = HashMap<ReviewTopic, Int>()
        for (i in 0 until line.size) {
            // Une analyse incohérente (partie interrompue, ancienne version) ne doit pas empêcher tout le bilan.
            runCatching { ReviewComments.topic(g, line, i, analysis) }.getOrNull()?.let { out[it] = (out[it] ?: 0) + 1 }
        }
        return out
    }

    /**
     * Points faibles déjà calculés, par partie : clé = identifiant, valeur = (analyse lue, résultat). Une nouvelle émission
     * de l'historique (partie ajoutée, analyse enregistrée, progression de l'Académie) ne rejoue que les parties nouvelles
     * ou réanalysées, au lieu des 20 parties. Taille bornée (les plus anciennes sont oubliées).
     */
    private val cache = object : LinkedHashMap<Long, Pair<String, Map<ReviewTopic, Int>>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Pair<String, Map<ReviewTopic, Int>>>?) = size > GAMES * 3
    }

    private fun fromGameCached(g: GameRecord): Map<ReviewTopic, Int> {
        synchronized(cache) { cache[g.id]?.takeIf { it.first == g.analysis }?.let { return it.second } }
        val topics = fromGame(g)
        synchronized(cache) { cache[g.id] = g.analysis to topics }
        return topics
    }

    fun compute(games: List<GameRecord>, progress: List<LessonProgress>): List<Priority> {
        val counts = HashMap<ReviewTopic, Int>()
        for (g in games.sortedByDescending { it.date }.take(GAMES)) {
            for ((t, n) in fromGameCached(g)) counts[t] = (counts[t] ?: 0) + n
        }
        val fromGames = counts.map { (t, n) ->
            Priority(t.label, "Repéré $n fois dans vos parties analysées.", t.lessonId, t.theme, n * 3)
        }
        val fromAcademy = progress.filter { !it.mastered }.mapNotNull { p ->
            val failedPractice = (p.practiceTotal - p.practicePassed).coerceAtLeast(0)
            val score = p.errors + 2 * failedPractice
            if (score < 2) return@mapNotNull null
            val l = Lessons.byId(p.lessonId).takeIf { it.id == p.lessonId } ?: return@mapNotNull null
            val why = buildList {
                if (p.errors > 0) add("${p.errors} erreur${if (p.errors > 1) "s" else ""} en pratique")
                if (failedPractice > 0) add("$failedPractice exercice${if (failedPractice > 1) "s" else ""} manqué${if (failedPractice > 1) "s" else ""}")
            }.joinToString(" et ")
            Priority("Leçon : ${l.title}", "Dans l'Académie : $why.", l.id, l.theme, score)
        }
        return (fromGames + fromAcademy).sortedByDescending { it.score }.distinctBy { it.lessonId }.take(3)
    }
}
