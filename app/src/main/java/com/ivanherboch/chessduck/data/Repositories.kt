package com.ivanherboch.chessduck.data

import kotlin.random.Random
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.ivanherboch.chessduck.content.PuzzleThemes

/** Réglages persistés dont les ViewModels ont besoin (implémenté par [Prefs], DataStore). */
interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun setNotationBest(v: Int)
    suspend fun setNotationLevel(v: Int)
    suspend fun setLesson(v: Int)
    /** Palier de l'Académie (1 à 5). */
    suspend fun setAcademyLevel(v: Int)
    suspend fun setPlacementDone(v: Boolean)
    suspend fun setElo(v: Int)
    suspend fun setGameSettings(twoPlayers: Boolean, color: Int, aiElo: Int)
    suspend fun setPuzzleFilter(band: Int, theme: String)
    suspend fun setPuzzleElo(v: Int)
    suspend fun setPuzzleSecondary(v: Boolean)
    suspend fun setPuzzleLength(v: Int)
    /** Place un problème en tête de l'historique des problèmes joués (et le mémorise comme problème courant). */
    suspend fun pushRecentPuzzle(id: String)
    /** Mémorise que le thème de ce problème récent était visible quand il a été joué (anti-spoiler levé). */
    suspend fun markThemeKnown(id: String)
    suspend fun markVariantDone(key: String)
    /** Ajoute l'ouverture [lessonId] à « Mon répertoire », ou l'en retire si elle y est déjà. */
    suspend fun toggleRepertoire(lessonId: Int)
    /** Note une pratique réussie de la leçon [lessonId] (le point faible qu'elle enseigne recule dans le bilan). */
    suspend fun markLessonWorked(lessonId: Int, at: Long)
    /** Note que la démonstration « Comprendre » de la leçon [lessonId] a été suivie jusqu'au bout. */
    suspend fun markUnderstood(lessonId: Int)
    /** Séance du jour : l'étape [step] est faite [today] ; la série avance quand toutes les étapes [required] le sont. */
    suspend fun markDailyStep(step: String, today: Long, required: Set<String>)
    /** Jeu Libre : active ou coupe le filet de sécurité. */
    suspend fun setSafetyNet(on: Boolean)
    /** Une gaffe ([topic], nom de ReviewTopic) a été arrêtée par le filet de sécurité. */
    suspend fun addNetEvent(topic: String, at: Long)

    // Réglages globaux (tiroir Paramètres, mode enfant, style des images partagées).
    /** 0 = suivre le système, 1 = Jour, 2 = Nuit. */
    suspend fun setThemeMode(v: Int)
    /** Index de la palette dans DuckPalette. */
    suspend fun setPalette(v: Int)
    /** Index de la matière d'échiquier dans BoardTheme. */
    suspend fun setBoardTheme(v: Int)
    suspend fun setCoords(v: Boolean)
    suspend fun setConfirmActions(v: Boolean)
    suspend fun setHaptics(v: Boolean)
    suspend fun setSound(v: Boolean)
    suspend fun setNarration(v: Boolean)
    suspend fun setKidNarration(v: Boolean)
    suspend fun setKidMode(v: Boolean)
    suspend fun setPseudo(v: String)
    suspend fun setPseudo2(v: String)
    /** Format (nom de ShareFormat) et fond clair des images partagées. */
    suspend fun setShareStyle(format: String, light: Boolean)
}

/** Catalogue des problèmes embarqués et progression du joueur. */
interface PuzzleRepository {
    suspend fun byId(id: String): Puzzle?
    suspend fun byIds(ids: List<String>): List<Puzzle>
    suspend fun byIdIgnoreCase(id: String): Puzzle?
    suspend fun slice(start: Int, n: Int): List<Puzzle>
    /** Échantillon de problèmes dont le thème principal est l'un de [themes] (requête indexée). */
    suspend fun sliceThemes(start: Int, themes: List<String>, n: Int): List<Puzzle>
    suspend fun sliceCastling(start: Int, n: Int): List<Puzzle>
    /**
     * Tire au hasard un problème du filtre ([themes] null = tous les thèmes) dans la tranche [range], autre que
     * [exclude] : un problème non résolu d'abord, sinon un déjà résolu ; null si aucun ne correspond.
     * [secondary] : le thème peut être secondaire (table puzzle_themes) ; [length] : longueur (PuzzleThemes.LENGTHS).
     */
    suspend fun randomId(
        themes: List<String>?, range: IntRange, exclude: String, random: Random, secondary: Boolean = false, length: Int = 0,
    ): String?
    /** Compteurs (problèmes, résolus) par thème pour une tranche : thème principal, ou tous les thèmes ([secondary]). */
    suspend fun themeCounts(range: IntRange, secondary: Boolean = false): List<ThemeCountRow>
    /**
     * Thèmes secondaires inclus : nombre de problèmes distincts (et de résolus) portant au moins un des [themes] dans la
     * tranche (une famille), chaque problème compté une seule fois même s'il a plusieurs de ces thèmes.
     */
    suspend fun linkedCount(themes: List<String>, range: IntRange): ThemeCountRow
    /**
     * Compteurs du catalogue quand un filtre de longueur est actif : un seul passage sur les problèmes de la tranche et
     * de la longueur [length], d'où chaque thème (principal, ou tous ses thèmes si [secondary]), chaque famille
     * ([groups] : clé → thèmes, problèmes distincts) et le total ("") avec leurs résolus. Clé → (problèmes, résolus).
     */
    suspend fun lengthCounts(range: IntRange, length: Int, secondary: Boolean, groups: Map<String, List<String>>): Map<String, ThemeCountRow>
    /** Recherche d'identifiant : au plus [limit] résultats classés et le nombre total de correspondances. */
    suspend fun searchIds(query: String, limit: Int): Pair<List<PuzzleHit>, Int>
    /** Problème résolu net (séries de l'Académie) : voir [recordAttempt]. */
    suspend fun markSolved(id: String, at: Long = System.currentTimeMillis()) = recordAttempt(id, PuzzleOutcome.CLEAN, at)
    /**
     * Essai conclu ([outcome] : PuzzleOutcome). Résolu (net ou après une erreur) : compté ; après une erreur ou un
     * abandon, le problème revient dans « À refaire » le lendemain ; une résolution nette l'en retire. Le favori est gardé.
     */
    suspend fun recordAttempt(id: String, outcome: Int, at: Long = System.currentTimeMillis())
    suspend fun setBookmark(id: String, on: Boolean)
    fun bookmarked(id: String): Flow<Boolean>
    fun bookmarks(): Flow<List<Puzzle>>
    /** « À refaire » : (problème, date prévue), par date. */
    fun toRedo(): Flow<List<Pair<Puzzle, Long>>>
    /** Essais conclus et thème principal de chaque problème (bilan tactique). */
    fun attempts(): Flow<List<AttemptRow>>
    fun solvedIds(): Flow<List<String>>
    /** Nombre de problèmes résolus. */
    fun solvedCount(): Flow<Int>
    /** Parmi [ids], les problèmes déjà résolus. */
    fun solvedAmong(ids: List<String>): Flow<List<String>>
    /** Importe les problèmes du catalogue livré absents de l'appareil ; vrai si un import a eu lieu. */
    suspend fun syncCatalogue(): Boolean
}

/** Historique des parties du Jeu Libre. */
interface GameRepository {
    fun games(): Flow<List<GameRecord>>
    fun tally(): Flow<List<Tally>>
    suspend fun insertGame(g: GameRecord): Long
    suspend fun saveAnalysis(id: Long, analysis: String)
    suspend fun deleteGame(id: Long)
}

/** Progression dans l'Académie : leçons, étoiles, exercices et tests. */
interface LessonProgressRepository {
    /** Leçons maîtrisées (Défi réussi et, s'il y a lieu, exercices validés). */
    fun masteredLessons(): Flow<List<Int>>
    fun progress(): Flow<List<LessonProgress>>
    suspend fun progress(id: Int): LessonProgress?
    suspend fun save(p: LessonProgress)
    suspend fun saveAssessment(a: AcademyAssessment): Long
    fun assessments(): Flow<List<AcademyAssessment>>
}

class RoomPuzzleRepository(private val context: Context, private val db: DuckDatabase) : PuzzleRepository {
    private val dao get() = db.puzzles()
    override suspend fun byId(id: String) = dao.byId(id)
    override suspend fun byIds(ids: List<String>) = dao.byIds(ids)
    override suspend fun byIdIgnoreCase(id: String) = dao.byIdIgnoreCase(id)
    override suspend fun slice(start: Int, n: Int) = dao.slice(start, n)
    override suspend fun sliceThemes(start: Int, themes: List<String>, n: Int) = dao.sliceThemes(start, themes, n)

    /** Plus grand rowid de la table (lu une fois, relu après un import du catalogue). */
    @Volatile private var maxRow = 0L

    /**
     * Tirage d'un problème non résolu : d'abord quelques fenêtres de [PROBE_WINDOW] lignes à partir d'un rowid tiré au
     * hasard (lecture bornée, instantanée même en mode Libre) ; si elles ne trouvent rien (thème rare, presque tout
     * résolu), repli exact sur le comptage puis la lecture à un rang tiré au hasard, résolus compris en dernier recours.
     */
    override suspend fun randomId(themes: List<String>?, range: IntRange, exclude: String, random: Random, secondary: Boolean, length: Int): String? {
        val (la, lb) = PuzzleThemes.lengthPlies(length)
        val lo = range.first
        val hi = range.last
        // Thèmes secondaires : tirage exact sur la table de liaison (indexée par thème et classement).
        if (secondary && themes != null) {
            for (includeSolved in listOf(false, true)) {
                val n = dao.countLinked(themes, lo, hi, exclude, includeSolved, la, lb)
                if (n > 0) return dao.idLinkedAt(themes, lo, hi, exclude, includeSolved, random.nextInt(n), la, lb)
            }
            return null
        }
        if (maxRow == 0L) maxRow = dao.maxRowId()
        if (maxRow > 0) repeat(PROBE_TRIES) {
            val start = random.nextLong(1, maxRow + 1)
            val ids = if (themes == null) dao.probeAll(start, PROBE_WINDOW, lo, hi, exclude, la, lb)
            else dao.probeInThemes(start, PROBE_WINDOW, themes, lo, hi, exclude, la, lb)
            if (ids.isNotEmpty()) return ids[random.nextInt(ids.size)]
        }
        for (includeSolved in listOf(false, true)) {
            val n = if (themes == null) dao.countAll(lo, hi, exclude, includeSolved, la, lb)
            else dao.countInThemes(themes, lo, hi, exclude, includeSolved, la, lb)
            if (n == 0) continue
            val k = random.nextInt(n)
            return if (themes == null) dao.idAllAt(lo, hi, exclude, includeSolved, k, la, lb)
            else dao.idInThemesAt(themes, lo, hi, exclude, includeSolved, k, la, lb)
        }
        return null
    }

    override suspend fun lengthCounts(range: IntRange, length: Int, secondary: Boolean, groups: Map<String, List<String>>): Map<String, ThemeCountRow> =
        countByLength(dao.metaWithLength(range.first, range.last, PuzzleThemes.lengthPlies(length).first, PuzzleThemes.lengthPlies(length).second),
            dao.solvedIds().first().toSet(), secondary, groups)

    override suspend fun linkedCount(themes: List<String>, range: IntRange): ThemeCountRow {
        val total = dao.countLinkedTotal(themes, range.first, range.last)
        // Résolus : les problèmes résolus (peu nombreux) dont au moins un thème est dans la famille.
        val keys = themes.toSet()
        val solved = dao.solvedMeta(range.first, range.last).count { m -> m.themes.trim().split(' ').any { it in keys } }
        return ThemeCountRow("", total, solved)
    }

    override suspend fun themeCounts(range: IntRange, secondary: Boolean): List<ThemeCountRow> {
        if (!secondary) return dao.themeCounts(range.first, range.last)
        // Tous les thèmes officiels de chaque problème : un même problème compte dans plusieurs thèmes.
        val solved = HashMap<String, Int>()
        for (m in dao.solvedMeta(range.first, range.last)) for (t in m.themes.trim().split(' ')) solved.merge(t, 1, Int::plus)
        return dao.linkedTotals(range.first, range.last).map { ThemeCountRow(it.theme, it.total, solved[it.theme] ?: 0) }
    }

    override suspend fun searchIds(query: String, limit: Int): Pair<List<PuzzleHit>, Int> {
        val pattern = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return dao.searchIds(pattern, query, limit) to dao.countIds(pattern)
    }
    override suspend fun sliceCastling(start: Int, n: Int) = dao.sliceCastling(start, n)
    override suspend fun recordAttempt(id: String, outcome: Int, at: Long) {
        val old = dao.progressOf(id) ?: PuzzleProgress(id, 0)
        dao.markSolved(PuzzleProgress.after(old, outcome, at))
    }
    override suspend fun setBookmark(id: String, on: Boolean) {
        val old = dao.progressOf(id) ?: PuzzleProgress(id, 0)
        dao.markSolved(old.copy(bookmarked = on))
    }
    override fun bookmarked(id: String) = dao.bookmarked(id)
    override fun bookmarks() = dao.bookmarks()
    override fun toRedo(): Flow<List<Pair<Puzzle, Long>>> = dao.toRedo().map { rows ->
        val byId = if (rows.isEmpty()) emptyMap() else dao.byIds(rows.map { it.puzzleId }).associateBy { it.id }
        rows.mapNotNull { r -> byId[r.puzzleId]?.let { it to r.nextReviewAt } }
    }
    override fun attempts() = dao.attempts()
    override fun solvedIds() = dao.solvedIds()
    override fun solvedCount() = dao.solvedCount()
    override fun solvedAmong(ids: List<String>) = dao.solvedAmong(ids)
    override suspend fun syncCatalogue() = DuckDatabase.syncPuzzles(context, db).also { if (it) maxRow = 0L }

    private companion object {
        const val PROBE_WINDOW = 3000
        const val PROBE_TRIES = 4
    }
}

class RoomGameRepository(private val db: DuckDatabase) : GameRepository {
    private val dao get() = db.user()
    override fun games() = dao.games()
    override fun tally() = dao.tally()
    override suspend fun insertGame(g: GameRecord) = dao.insertGame(g)
    override suspend fun saveAnalysis(id: Long, analysis: String) = dao.saveAnalysis(id, analysis)
    override suspend fun deleteGame(id: Long) = dao.deleteGame(id)
}

class RoomLessonProgressRepository(private val db: DuckDatabase) : LessonProgressRepository {
    private val dao get() = db.user()
    override fun masteredLessons() = dao.masteredLessons()
    override fun progress() = dao.lessonProgress()
    override suspend fun progress(id: Int) = dao.lessonProgress(id)
    override suspend fun save(p: LessonProgress) = dao.saveLesson(p)
    override suspend fun saveAssessment(a: AcademyAssessment) = dao.insertAssessment(a)
    override fun assessments() = dao.assessments()
}

/**
 * Compteurs par thème, par famille et au total d'une liste de problèmes (déjà filtrée), chacun avec ses résolus :
 * thème principal seulement, ou tous les thèmes officiels ([secondary]) ; une famille compte ses problèmes distincts.
 */
internal fun countByLength(
    metas: List<PuzzleMeta>, solved: Set<String>, secondary: Boolean, groups: Map<String, List<String>>,
): Map<String, ThemeCountRow> {
    val keys = PuzzleThemes.all.map { it.key }.toSet()
    val total = HashMap<String, Int>()
    val done = HashMap<String, Int>()
    fun add(k: String, s: Boolean) { total.merge(k, 1, Int::plus); if (s) done.merge(k, 1, Int::plus) }
    val groupOfTheme = groups.flatMap { (g, ts) -> ts.map { it to g } }.groupBy({ it.first }, { it.second })
    for (m in metas) {
        val s = m.id in solved
        add("", s)
        val tags = if (secondary) m.themes.trim().split(' ').filter { it in keys }.toSet() else setOf(m.primaryTheme)
        for (t in tags) add(t, s)
        for (g in tags.flatMap { groupOfTheme[it].orEmpty() }.toSet()) add(g, s)
    }
    return total.mapValues { (k, n) -> ThemeCountRow(k, n, done[k] ?: 0) }
}
