package com.ivanherboch.chessduck.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Réglages persistés dont les ViewModels ont besoin (implémenté par [Prefs], DataStore). */
interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun setNotationBest(v: Int)
    suspend fun setNotationLevel(v: Int)
    suspend fun setLesson(v: Int)
    suspend fun setElo(v: Int)
    suspend fun setGameSettings(twoPlayers: Boolean, color: Int, aiElo: Int)
    suspend fun setPuzzleFilter(band: Int, theme: String)
    /** Place un problème en tête de l'historique des problèmes joués (et le mémorise comme problème courant). */
    suspend fun pushRecentPuzzle(id: String)
}

/** Catalogue des problèmes embarqués et progression du joueur. */
interface PuzzleRepository {
    suspend fun byId(id: String): Puzzle?
    suspend fun byIds(ids: List<String>): List<Puzzle>
    suspend fun byIdIgnoreCase(id: String): Puzzle?
    /** Index compact du catalogue (identifiant, classement, thèmes) pour le tirage et la recherche. */
    suspend fun allMeta(): List<PuzzleMeta>
    suspend fun slice(start: Int, n: Int): List<Puzzle>
    suspend fun sliceTheme(start: Int, theme: String, n: Int): List<Puzzle>
    suspend fun sliceCastling(start: Int, n: Int): List<Puzzle>
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

/** Leçons de l'Académie maîtrisées. */
interface LessonProgressRepository {
    fun masteredLessons(): Flow<List<Int>>
    suspend fun markLesson(id: Int, at: Long = System.currentTimeMillis())
}

class RoomPuzzleRepository(private val context: Context, private val db: DuckDatabase) : PuzzleRepository {
    private val dao get() = db.puzzles()
    override suspend fun byId(id: String) = dao.byId(id)
    override suspend fun byIds(ids: List<String>) = dao.byIds(ids)
    override suspend fun byIdIgnoreCase(id: String) = dao.byIdIgnoreCase(id)
    override suspend fun allMeta() = dao.allMeta()
    override suspend fun slice(start: Int, n: Int) = dao.slice(start, n)
    override suspend fun sliceTheme(start: Int, theme: String, n: Int) = dao.sliceTheme(start, theme, n)
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
    override fun masteredLessons() = db.user().masteredLessons()
    override suspend fun markLesson(id: Int, at: Long) = db.user().markLesson(LessonProgress(id, at))
}
