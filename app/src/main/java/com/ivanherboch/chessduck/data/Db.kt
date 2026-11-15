package com.ivanherboch.chessduck.data

import android.database.sqlite.SQLiteDatabase
import com.ivanherboch.chessduck.content.PuzzleThemes
import kotlin.coroutines.cancellation.CancellationException
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Problème Lichess (base officielle database.lichess.org, licence CC0).
 * v3 : [primaryTheme] (type principal, règle PuzzleThemes.primary) indexé avec le classement pour le tirage,
 * les compteurs du catalogue et les exercices de notation directement en SQL.
 */
@Entity(tableName = "puzzles", indices = [Index("rating"), Index("primaryTheme", "rating")])
data class Puzzle(
    @PrimaryKey val id: String,
    val fen: String,
    val moves: String,
    val rating: Int,
    val popularity: Int,
    val plays: Int,
    /** Thèmes Lichess séparés par des espaces, avec un espace en tête et en queue. */
    val themes: String,
    /** Clé du thème principal (voir PuzzleThemes.primary), calculée une fois pour toutes. */
    @ColumnInfo(defaultValue = "")
    val primaryTheme: String = "",
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

data class PuzzleMeta(val id: String, val rating: Int, val themes: String, val primaryTheme: String)

/** Suggestion de recherche : identifiant, classement et thème principal. */
data class PuzzleHit(val id: String, val rating: Int, val primaryTheme: String)

/** Compteurs du catalogue pour un thème principal : problèmes et problèmes résolus. */
data class ThemeCountRow(val theme: String, val total: Int, val solved: Int)
data class Tally(val outcome: Int, val n: Int)

@Dao
interface PuzzleDao {
    /** Index complet (vérification du catalogue dans les tests) : l'application interroge désormais la base indexée. */
    @Query("SELECT id, rating, themes, primaryTheme FROM puzzles")
    suspend fun allMeta(): List<PuzzleMeta>

    @Query("SELECT * FROM puzzles WHERE id = :id")
    suspend fun byId(id: String): Puzzle?

    @Query("SELECT * FROM puzzles WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<Puzzle>

    @Query("SELECT * FROM puzzles WHERE id = :id COLLATE NOCASE LIMIT 1")
    suspend fun byIdIgnoreCase(id: String): Puzzle?

    @Query("SELECT * FROM puzzles WHERE rowid >= :start LIMIT :n")
    suspend fun slice(start: Int, n: Int): List<Puzzle>

    /** Échantillon indexé de problèmes dont le thème principal est l'un de [themes]. */
    @Query("SELECT * FROM puzzles WHERE rowid >= :start AND primaryTheme IN (:themes) LIMIT :n")
    suspend fun sliceThemes(start: Int, themes: List<String>, n: Int): List<Puzzle>

    // ---- Tirage aléatoire indexé : comptage puis lecture à un rang tiré au hasard ----

    @Query(
        "SELECT COUNT(*) FROM puzzles WHERE primaryTheme IN (:themes) AND rating BETWEEN :lo AND :hi AND id != :exclude " +
            "AND (:includeSolved OR id NOT IN (SELECT puzzleId FROM puzzle_progress))",
    )
    suspend fun countInThemes(themes: List<String>, lo: Int, hi: Int, exclude: String, includeSolved: Boolean): Int

    @Query(
        "SELECT id FROM puzzles WHERE primaryTheme IN (:themes) AND rating BETWEEN :lo AND :hi AND id != :exclude " +
            "AND (:includeSolved OR id NOT IN (SELECT puzzleId FROM puzzle_progress)) LIMIT 1 OFFSET :offset",
    )
    suspend fun idInThemesAt(themes: List<String>, lo: Int, hi: Int, exclude: String, includeSolved: Boolean, offset: Int): String?

    @Query(
        "SELECT COUNT(*) FROM puzzles WHERE rating BETWEEN :lo AND :hi AND id != :exclude " +
            "AND (:includeSolved OR id NOT IN (SELECT puzzleId FROM puzzle_progress))",
    )
    suspend fun countAll(lo: Int, hi: Int, exclude: String, includeSolved: Boolean): Int

    @Query(
        "SELECT id FROM puzzles WHERE rating BETWEEN :lo AND :hi AND id != :exclude " +
            "AND (:includeSolved OR id NOT IN (SELECT puzzleId FROM puzzle_progress)) LIMIT 1 OFFSET :offset",
    )
    suspend fun idAllAt(lo: Int, hi: Int, exclude: String, includeSolved: Boolean, offset: Int): String?

    /** Compteurs agrégés du catalogue par thème principal, pour une tranche de classement. */
    @Query(
        "SELECT primaryTheme AS theme, COUNT(*) AS total, " +
            "SUM(CASE WHEN id IN (SELECT puzzleId FROM puzzle_progress) THEN 1 ELSE 0 END) AS solved " +
            "FROM puzzles WHERE rating BETWEEN :lo AND :hi GROUP BY primaryTheme",
    )
    suspend fun themeCounts(lo: Int, hi: Int): List<ThemeCountRow>

    /**
     * Recherche d'identifiant insensible à la casse : identique, puis préfixe, puis contenu (ordre du catalogue).
     * [pattern] est déjà échappé (\\, %, _).
     */
    @Query(
        "SELECT id, rating, primaryTheme FROM puzzles WHERE id LIKE '%' || :pattern || '%' ESCAPE '\\' " +
            "ORDER BY CASE WHEN id = :exact COLLATE NOCASE THEN 0 WHEN id LIKE :pattern || '%' ESCAPE '\\' THEN 1 ELSE 2 END, rowid " +
            "LIMIT :limit",
    )
    suspend fun searchIds(pattern: String, exact: String, limit: Int): List<PuzzleHit>

    @Query("SELECT COUNT(*) FROM puzzles WHERE id LIKE '%' || :pattern || '%' ESCAPE '\\'")
    suspend fun countIds(pattern: String): Int

    @Query("SELECT * FROM puzzles WHERE rowid >= :start AND fen NOT LIKE '% w - %' AND fen NOT LIKE '% b - %' LIMIT :n")
    suspend fun sliceCastling(start: Int, n: Int): List<Puzzle>

    @Query("SELECT COUNT(*) FROM puzzles")
    suspend fun count(): Int

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
    version = 3,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /**
         * Mise à jour d'une installation existante : Room ne recopie la base embarquée qu'à la première création.
         * Si la base de l'appareil contient moins de problèmes que le catalogue livré, on importe les manquants
         * (progression, historique et réglages sont conservés). Sans effet sur une installation neuve.
         */
        suspend fun syncPuzzles(context: Context, db: DuckDatabase): Boolean = withContext(Dispatchers.IO) {
            if (db.puzzles().count() >= PUZZLE_COUNT) return@withContext false
            val tmp = File(context.cacheDir, "catalogue.db")
            try {
                context.assets.open("chessduck.db").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                // Import en SQL natif : la base livrée est attachée puis copiée en une instruction (pas de boucle Kotlin).
                // Connexion dédiée au même fichier (et non celle de Room, dont les déclencheurs d'invalidation
                // reposent sur une table temporaire propre à sa connexion) ; SQLite sérialise les écritures.
                val path = db.openHelper.writableDatabase.path ?: return@withContext false
                SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { conn ->
                    conn.rawQuery("PRAGMA busy_timeout = 10000", null).use { it.moveToFirst() }
                    conn.execSQL("ATTACH DATABASE ? AS catalogue", arrayOf<Any>(tmp.path))
                    try {
                        conn.execSQL(
                            "INSERT OR IGNORE INTO puzzles (id, fen, moves, rating, popularity, plays, themes, primaryTheme) " +
                                "SELECT id, fen, moves, rating, popularity, plays, themes, " + PuzzleThemes.primarySql() + " FROM catalogue.puzzles",
                        )
                    } finally {
                        conn.execSQL("DETACH DATABASE catalogue")
                    }
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            } finally {
                tmp.delete()
            }
        }

        /** v2 : noms des joueurs et ELO de l'ordinateur dans l'historique (la base embarquée reste en v1). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN whiteName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE games ADD COLUMN blackName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE games ADD COLUMN opponentElo INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v3 : thème principal de chaque problème, calculé en SQL avec exactement l'ordre de PuzzleThemes, et index
         * (thème principal, classement). La base livrée est déjà en v3 : cette migration ne sert qu'aux installations
         * existantes.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE puzzles ADD COLUMN primaryTheme TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE puzzles SET primaryTheme = " + PuzzleThemes.primarySql())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_puzzles_primaryTheme_rating` ON `puzzles` (`primaryTheme`, `rating`)")
            }
        }
    }
}
