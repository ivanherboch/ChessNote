package com.ivanherboch.chessduck.data

import kotlin.random.Random
import android.content.Context
import kotlinx.coroutines.flow.Flow

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
    /** Place un problème en tête de l'historique des problèmes joués (et le mémorise comme problème courant). */
    suspend fun pushRecentPuzzle(id: String)
    /** Mémorise que le thème de ce problème récent était visible quand il a été joué (anti-spoiler levé). */
    suspend fun markThemeKnown(id: String)

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
     */
    suspend fun randomId(themes: List<String>?, range: IntRange, exclude: String, random: Random): String?
    /** Compteurs (problèmes, résolus) par thème principal pour une tranche de classement. */
    suspend fun themeCounts(range: IntRange): List<ThemeCountRow>
    /** Recherche d'identifiant : au plus [limit] résultats classés et le nombre total de correspondances. */
    suspend fun searchIds(query: String, limit: Int): Pair<List<PuzzleHit>, Int>
    suspend fun markSolved(id: String, at: Long = System.currentTimeMillis())
    fun solvedIds(): Flow<List<String>>
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

    override suspend fun randomId(themes: List<String>?, range: IntRange, exclude: String, random: Random): String? {
        for (includeSolved in listOf(false, true)) {
            val n = if (themes == null) dao.countAll(range.first, range.last, exclude, includeSolved)
            else dao.countInThemes(themes, range.first, range.last, exclude, includeSolved)
            if (n == 0) continue
            val k = random.nextInt(n)
            return if (themes == null) dao.idAllAt(range.first, range.last, exclude, includeSolved, k)
            else dao.idInThemesAt(themes, range.first, range.last, exclude, includeSolved, k)
        }
        return null
    }

    override suspend fun themeCounts(range: IntRange) = dao.themeCounts(range.first, range.last)

    override suspend fun searchIds(query: String, limit: Int): Pair<List<PuzzleHit>, Int> {
        val pattern = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return dao.searchIds(pattern, query, limit) to dao.countIds(pattern)
    }
    override suspend fun sliceCastling(start: Int, n: Int) = dao.sliceCastling(start, n)
    override suspend fun markSolved(id: String, at: Long) = dao.markSolved(PuzzleProgress(id, at))
    override fun solvedIds() = dao.solvedIds()
    override suspend fun syncCatalogue() = DuckDatabase.syncPuzzles(context, db)
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
