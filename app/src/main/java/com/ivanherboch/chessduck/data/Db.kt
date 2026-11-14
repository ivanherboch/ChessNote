package com.ivanherboch.chessduck.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Problème Lichess (base officielle database.lichess.org, licence CC0). */
@Entity(tableName = "puzzles", indices = [Index("rating")])
data class Puzzle(
    @PrimaryKey val id: String,
    val fen: String,
    val moves: String,
    val rating: Int,
    val popularity: Int,
    val plays: Int,
    /** Thèmes Lichess séparés par des espaces, avec un espace en tête et en queue. */
    val themes: String,
)

@Entity(tableName = "puzzle_progress")
data class PuzzleProgress(@PrimaryKey val puzzleId: String, val solvedAt: Long)

@Entity(tableName = "lesson_progress")
data class LessonProgress(@PrimaryKey val lessonId: Int, val masteredAt: Long)

@Entity(tableName = "games")
data class GameRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val opponent: String,
    val vsComputer: Boolean,
    val playerWhite: Boolean,
    /** "1-0", "0-1" ou "1/2-1/2". */
    val result: String,
    /** Du point de vue du joueur : 1 victoire, 0 nulle, -1 défaite. */
    val outcome: Int,
    val reason: String,
    val eloBefore: Int,
    val eloAfter: Int,
    val moves: String,
    @ColumnInfo(defaultValue = "") val analysis: String = "",
    /** Noms des joueurs (vides pour les parties enregistrées avant la version 1.3). */
    @ColumnInfo(defaultValue = "") val whiteName: String = "",
    @ColumnInfo(defaultValue = "") val blackName: String = "",
    /** ELO de l'ordinateur (0 pour une partie à 2 joueurs). */
    @ColumnInfo(defaultValue = "0") val opponentElo: Int = 0,
)

data class PuzzleMeta(val id: String, val rating: Int, val themes: String)
data class Tally(val outcome: Int, val n: Int)

@Dao
interface PuzzleDao {
    @Query("SELECT id, rating, themes FROM puzzles")
    suspend fun allMeta(): List<PuzzleMeta>

    @Query("SELECT * FROM puzzles WHERE id = :id")
    suspend fun byId(id: String): Puzzle?

    @Query("SELECT * FROM puzzles WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<Puzzle>

    @Query("SELECT * FROM puzzles WHERE id = :id COLLATE NOCASE LIMIT 1")
    suspend fun byIdIgnoreCase(id: String): Puzzle?

    @Query("SELECT * FROM puzzles WHERE rowid >= :start LIMIT :n")
    suspend fun slice(start: Int, n: Int): List<Puzzle>

    @Query("SELECT * FROM puzzles WHERE rowid >= :start AND themes LIKE '% ' || :theme || ' %' LIMIT :n")
    suspend fun sliceTheme(start: Int, theme: String, n: Int): List<Puzzle>

    @Query("SELECT * FROM puzzles WHERE rowid >= :start AND fen NOT LIKE '% w - %' AND fen NOT LIKE '% b - %' LIMIT :n")
    suspend fun sliceCastling(start: Int, n: Int): List<Puzzle>

    @Query("SELECT COUNT(*) FROM puzzles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(puzzles: List<Puzzle>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markSolved(p: PuzzleProgress)

    @Query("SELECT puzzleId FROM puzzle_progress")
    fun solvedIds(): Flow<List<String>>
}

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markLesson(p: LessonProgress)

    @Query("SELECT lessonId FROM lesson_progress")
    fun masteredLessons(): Flow<List<Int>>

    @Insert
    suspend fun insertGame(g: GameRecord): Long

    @Query("UPDATE games SET analysis = :analysis WHERE id = :id")
    suspend fun saveAnalysis(id: Long, analysis: String)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteGame(id: Long)

    @Query("SELECT * FROM games ORDER BY date DESC")
    fun games(): Flow<List<GameRecord>>

    @Query("SELECT outcome, COUNT(*) AS n FROM games WHERE vsComputer = 1 GROUP BY outcome")
    fun tally(): Flow<List<Tally>>
}

@Database(
    entities = [Puzzle::class, PuzzleProgress::class, LessonProgress::class, GameRecord::class],
    version = 2,
    exportSchema = true,
)
abstract class DuckDatabase : RoomDatabase() {
    abstract fun puzzles(): PuzzleDao
    abstract fun user(): UserDao

    companion object {
        const val PUZZLE_COUNT = 150_000

        fun create(context: Context): DuckDatabase =
            Room.databaseBuilder(context, DuckDatabase::class.java, "chessduck.db")
                .createFromAsset("chessduck.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * Mise à jour d'une installation existante : Room ne recopie la base embarquée qu'à la première création.
         * Si la base de l'appareil contient moins de problèmes que le catalogue livré, on importe les manquants
         * (progression, historique et réglages sont conservés). Sans effet sur une installation neuve.
         */
        suspend fun syncPuzzles(context: Context, db: DuckDatabase): Boolean = withContext(Dispatchers.IO) {
            if (db.puzzles().count() >= PUZZLE_COUNT) return@withContext false
            val tmp = File(context.cacheDir, "catalogue.db")
            runCatching {
                context.assets.open("chessduck.db").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { src ->
                    src.rawQuery("SELECT id, fen, moves, rating, popularity, plays, themes FROM puzzles", null).use { c ->
                        val batch = ArrayList<Puzzle>(2000)
                        while (c.moveToNext()) {
                            batch.add(Puzzle(c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getInt(5), c.getString(6)))
                            if (batch.size == 2000) { db.puzzles().insertAll(batch.toList()); batch.clear() }
                        }
                        if (batch.isNotEmpty()) db.puzzles().insertAll(batch)
                    }
                }
            }
            tmp.delete()
            true
        }

        /** v2 : noms des joueurs et ELO de l'ordinateur dans l'historique (la base embarquée reste en v1). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN whiteName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE games ADD COLUMN blackName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE games ADD COLUMN opponentElo INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
