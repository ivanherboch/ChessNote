package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.data.LessonProgress

/**
 * Point faible repéré dans une partie analysée : la leçon de l'Académie qui l'enseigne en priorité, le thème des
 * exercices proposés, et les thèmes des autres leçons qui le travaillent (voir [WeakTopics.lessonFor]).
 */
enum class ReviewTopic(val label: String, val lessonId: Int, val theme: String, val themes: List<String> = listOf(theme)) {
    MISSED_MATE("Mat manqué", 131, "mateIn2", listOf("mateIn1", "mateIn2", "mateIn3")),
    ALLOWED_MATE("Mat permis à l'adversaire", 132, "defensiveMove"),
    HANGING_PIECE("Pièce laissée en prise", 136, "hangingPiece"),
    MISSED_CAPTURE("Prise gagnante manquée", 133, "hangingPiece", listOf("hangingPiece", "trappedPiece", "capturingDefender")),
    FORK_ALLOWED("Fourchette subie", 38, "fork"),
    MISSED_FORK("Fourchette manquée", 138, "fork"),
    CASTLING("Roque retardé", 73, "castling", listOf("castling", "exposedKing")),
    DEVELOPMENT("Développement en retard", 1, "opening"),
}

/** Une priorité du bilan : ce qu'il faut travailler, pourquoi, et où. [theme] vide : la leçon seule. */
@Immutable
data class Priority(val title: String, val reason: String, val lessonId: Int, val theme: String, val score: Double)

/**
 * Bilan des thèmes faibles : les 3 priorités du moment, tirées des erreurs des parties analysées (Jeu Libre) et
 * des difficultés rencontrées dans l'Académie (erreurs en pratique, exercices manqués).
 * - Les parties récentes pèsent plus que les anciennes ([DECAY] par partie).
 * - Un point travaillé avec succès (leçon maîtrisée ou pratique réussie, voir [worked]) recule : ses erreurs d'avant
 *   ne comptent plus qu'à [WORKED_WEIGHT], et il disparaît [SNOOZE_DAYS] jours s'il n'est pas réapparu depuis.
 * - Chaque point faible propose à tour de rôle plusieurs leçons de son thème ([lessonFor]).
 */
object WeakTopics {
    /** Nombre de parties récentes examinées. */
    const val GAMES = 20
    /** Poids d'une partie par rapport à la suivante, plus récente (la 10e partie compte environ deux fois moins). */
    const val DECAY = 0.93
    /** Poids des erreurs commises avant que le point n'ait été travaillé avec succès. */
    const val WORKED_WEIGHT = 0.25
    /** Jours pendant lesquels un point travaillé disparaît, s'il n'est pas réapparu depuis. */
    const val SNOOZE_DAYS = 3
    private const val DAY = 86_400_000L

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

    /** Leçons qui travaillent [t] : la leçon de référence, puis celles de ses thèmes, des plus simples aux plus avancées. */
    fun pool(t: ReviewTopic): List<Lesson> {
        val main = Lessons.all.firstOrNull { it.id == t.lessonId }
        val byTheme = Lessons.all.filter { it.theme in t.themes && it.id != t.lessonId && it.drill.isEmpty() }.sortedBy { it.level }
        return listOfNotNull(main) + byTheme
    }

    /** Dernier travail réussi d'une leçon : sa maîtrise, ou une pratique réussie depuis ([worked]). */
    private fun workedAt(id: Int, byId: Map<Int, LessonProgress>, worked: Map<Int, Long>) =
        maxOf(byId[id]?.masteredAt ?: 0L, worked[id] ?: 0L)

    /**
     * Leçon proposée pour [t] : la première de son lot qui n'est pas encore maîtrisée ; sinon celle travaillée il y a le
     * plus longtemps (on varie au lieu de reproposer toujours la même).
     */
    fun lessonFor(t: ReviewTopic, progress: List<LessonProgress>, worked: Map<Int, Long> = emptyMap()): Lesson? {
        val byId = progress.associateBy { it.lessonId }
        val lessons = pool(t)
        return lessons.firstOrNull { byId[it.id]?.mastered != true } ?: lessons.minByOrNull { workedAt(it.id, byId, worked) }
    }

    fun compute(
        games: List<GameRecord>, progress: List<LessonProgress>, worked: Map<Int, Long> = emptyMap(), now: Long = System.currentTimeMillis(),
    ): List<Priority> {
        val byId = progress.associateBy { it.lessonId }
        val recent = games.sortedByDescending { it.date }.take(GAMES)
        val analyzed = recent.count { it.analysis.isNotEmpty() }
        val fromGames = ReviewTopic.entries.mapNotNull { t ->
            val since = pool(t).maxOfOrNull { workedAt(it.id, byId, worked) } ?: 0L
            var weight = 0.0
            var total = 0
            var after = 0
            var last = 0L
            recent.forEachIndexed { i, g ->
                val n = fromGameCached(g)[t] ?: return@forEachIndexed
                total += n
                if (g.date > since) after += n
                last = maxOf(last, g.date)
                weight += n * Math.pow(DECAY, i.toDouble()) * (if (g.date > since) 1.0 else WORKED_WEIGHT)
            }
            if (total == 0) return@mapNotNull null
            // Travaillé récemment et pas réapparu depuis : on laisse le temps de le vérifier en partie.
            if (since > 0 && after == 0 && now - since < SNOOZE_DAYS * DAY) return@mapNotNull null
            val l = lessonFor(t, progress, worked) ?: return@mapNotNull null
            val days = ((now - last) / DAY).toInt().coerceAtLeast(0)
            val when_ = when (days) { 0 -> "aujourd'hui"; 1 -> "hier"; else -> "il y a $days jours" }
            val where = if (analyzed > 1) "vos $analyzed dernières parties" else "votre dernière partie"
            // Court (deux lignes au plus sur un téléphone) : l'accueil tient sans défilement.
            val reason = "$total fois dans $where, la dernière $when_." + if (since > 0) " $after depuis votre entraînement." else ""
            Priority(t.label, reason, l.id, l.theme.ifEmpty { t.theme }, weight * 3)
        }
        val fromAcademy = progress.filter { !it.mastered }.mapNotNull { p ->
            val failedPractice = (p.practiceTotal - p.practicePassed).coerceAtLeast(0)
            val score = p.errors + 2 * failedPractice
            if (score < 2) return@mapNotNull null
            val l = Lessons.byId(p.lessonId).takeIf { it.id == p.lessonId } ?: return@mapNotNull null
            val why = buildList {
                if (p.errors > 0) add("${p.errors} erreur${if (p.errors > 1) "s" else ""}")
                if (failedPractice > 0) add("$failedPractice exercice${if (failedPractice > 1) "s" else ""} manqué${if (failedPractice > 1) "s" else ""}")
            }.joinToString(" et ")
            Priority(l.title, "Leçon : $why en pratique.", l.id, l.theme, score.toDouble())
        }
        return (fromGames + fromAcademy).sortedByDescending { it.score }.distinctBy { it.lessonId }.take(3)
    }
}
