package com.ivanherboch.chessduck.data

import android.database.sqlite.SQLiteDatabase
import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.feature.SrsScheduler
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
 * v5 : index couvrants (classement, id) et (thème principal, classement, id) : comptages et tirages sans lire la table.
 */
@Entity(tableName = "puzzles", indices = [Index("rating", "id"), Index("primaryTheme", "rating", "id")])
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

/**
 * Suivi d'un problème. [solvedAt] > 0 : compté comme résolu (date). v5 : [status] du dernier essai (voir [PuzzleOutcome]),
 * nombre d'[attempts], prochaine fois « À refaire » ([nextReviewAt], 0 : rien à refaire), dernier essai ([lastTriedAt])
 * et favori ([bookmarked]). Une ligne peut exister sans résolution (essai manqué, favori).
 */
@Entity(tableName = "puzzle_progress")
data class PuzzleProgress(
    @PrimaryKey val puzzleId: String,
    val solvedAt: Long,
    @ColumnInfo(defaultValue = "0") val status: Int = 0,
    @ColumnInfo(defaultValue = "0") val attempts: Int = 0,
    @ColumnInfo(defaultValue = "0") val nextReviewAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val lastTriedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val bookmarked: Boolean = false,
) {
    companion object {
        /** Une journée : un problème manqué revient dans « À refaire » le lendemain. */
        const val REDO_DELAY = SrsScheduler.DAY

        /**
         * Suivi après un essai conclu [outcome] à l'instant [at] (favori conservé). « À refaire » suit la répétition
         * espacée de l'Académie ([SrsScheduler.INTERVAL_DAYS] : 1, 7, 30 puis 90 jours) : un abandon remet à un jour ;
         * une erreur garde l'intervalle en cours (au moins un jour) ; une résolution nette d'un problème à refaire passe à
         * l'intervalle suivant, puis le retire de la liste après le dernier. Résolu net sans avoir jamais été manqué : rien à refaire.
         */
        fun after(old: PuzzleProgress, outcome: Int, at: Long): PuzzleProgress {
            val solved = outcome == PuzzleOutcome.CLEAN || outcome == PuzzleOutcome.WITH_ERROR
            val days = SrsScheduler.INTERVAL_DAYS
            // Intervalle en cours (en jours, d'après la dernière planification) et son rang dans la suite ; -1 : pas planifié.
            val stage = if (old.nextReviewAt > 0 && old.lastTriedAt > 0) {
                days.indexOf(((old.nextReviewAt - old.lastTriedAt + REDO_DELAY / 2) / REDO_DELAY).toInt())
            } else -1
            val next = when (outcome) {
                PuzzleOutcome.GAVE_UP -> at + days[0] * REDO_DELAY
                PuzzleOutcome.WITH_ERROR -> at + days[stage.coerceAtLeast(0)] * REDO_DELAY
                else -> if (stage < 0 || stage + 1 >= days.size) 0 else at + days[stage + 1] * REDO_DELAY
            }
            return old.copy(
                solvedAt = if (solved && old.solvedAt <= 0) at else old.solvedAt, status = outcome, attempts = old.attempts + 1,
                lastTriedAt = at, nextReviewAt = next,
            )
        }
    }
}

/** Candidats lus par fenêtre de tirage, parmi lesquels un problème est choisi au hasard. */
const val PROBE_PICK = 200

/** Issue d'un essai de problème (colonne [PuzzleProgress.status]). */
object PuzzleOutcome {
    /** Aucun essai conclu (ligne créée pour un favori). */
    const val NONE = 0
    /** Résolu sans erreur ni flèche de l'indice. */
    const val CLEAN = 1
    /** Résolu après une erreur : compté, mais à refaire. */
    const val WITH_ERROR = 2
    /** Abandonné (solution dévoilée ou flèche de l'indice) : non compté, à refaire. */
    const val GAVE_UP = 3
}

/**
 * Lien problème → thème officiel (v5), pour l'option « Inclure les thèmes secondaires » : tous les thèmes officiels
 * d'un problème, pas seulement le principal. Clé primaire (thème, classement, id) : la table sert elle-même d'index
 * (créée WITHOUT ROWID dans la base livrée et par la migration, pour rester compacte).
 */
@Entity(tableName = "puzzle_themes", primaryKeys = ["theme", "rating", "puzzleId"])
data class PuzzleThemeLink(val theme: String, val rating: Int, val puzzleId: String)

/** Essai d'un problème avec son thème principal (bilan tactique). */
data class AttemptRow(val status: Int, val attempts: Int, val lastTriedAt: Long, val theme: String)

/**
 * Progression d'une leçon de l'Académie (v4). [masteredAt] > 0 : leçon maîtrisée (0 : pas encore maîtrisée).
 * [stars] : meilleur résultat du Défi (1 à 3). [practicePassed]/[practiceTotal] : meilleure série d'exercices inédits.
 * Les champs de révision espacée ([srsStage], [nextReviewAt], [lastReviewedAt]) sont préparés pour les révisions.
 */
@Entity(tableName = "lesson_progress")
data class LessonProgress(
    @PrimaryKey val lessonId: Int,
    val masteredAt: Long,
    @ColumnInfo(defaultValue = "0") val stars: Int = 0,
    @ColumnInfo(defaultValue = "0") val errors: Int = 0,
    @ColumnInfo(defaultValue = "0") val hintsUsed: Int = 0,
    @ColumnInfo(defaultValue = "0") val practicePassed: Int = 0,
    @ColumnInfo(defaultValue = "0") val practiceTotal: Int = 0,
    @ColumnInfo(defaultValue = "0") val srsStage: Int = 0,
    @ColumnInfo(defaultValue = "0") val nextReviewAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val lastReviewedAt: Long = 0,
) {
    val mastered: Boolean get() = masteredAt > 0
}

/** Résultat d'un test de l'Académie (placement, examen de palier…) : [kind] identifie le type de test. */
@Entity(tableName = "academy_assessments")
data class AcademyAssessment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val level: Int,
    val score: Int,
    val total: Int,
    val passed: Boolean,
    val completedAt: Long,
)

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
    /** Position de départ (FEN) d'une partie jouée depuis une leçon ; vide pour la position initiale. */
    @ColumnInfo(defaultValue = "") val startFen: String = "",
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

    // ---- Tirage aléatoire rapide : une fenêtre de rowid à partir d'un point tiré au hasard ----

    @Query("SELECT IFNULL(MAX(rowid), 0) FROM puzzles")
    suspend fun maxRowId(): Long

    /**
     * Problèmes non résolus de la tranche (au plus [PROBE_PICK]) dans les [window] lignes qui suivent le rowid [start] :
     * lecture bornée, quelle que soit la taille de la base (environ un millième de seconde au lieu d'un comptage
     * complet). Le tirage choisit au hasard parmi eux : prendre toujours le premier favoriserait les problèmes qui
     * suivent une longue suite de problèmes résolus ou hors filtre.
     */
    @Query(
        "SELECT id FROM (SELECT id, rating, moves FROM puzzles WHERE rowid >= :start ORDER BY rowid LIMIT :window) " +
            "WHERE rating BETWEEN :lo AND :hi AND id != :exclude AND id NOT IN (SELECT puzzleId FROM puzzle_progress WHERE solvedAt > 0) " +
            "AND (:minPly = 0 OR (length(moves) - length(replace(moves, ' ', '')) + 1) BETWEEN :minPly AND :maxPly) LIMIT $PROBE_PICK",
    )
    suspend fun probeAll(start: Long, window: Int, lo: Int, hi: Int, exclude: String, minPly: Int = 0, maxPly: Int = 0): List<String>

    /** Comme [probeAll], limité aux problèmes dont le thème principal est l'un de [themes]. */
    @Query(
        "SELECT id FROM (SELECT id, rating, primaryTheme, moves FROM puzzles WHERE rowid >= :start ORDER BY rowid LIMIT :window) " +
            "WHERE primaryTheme IN (:themes) AND rating BETWEEN :lo AND :hi AND id != :exclude " +
            "AND id NOT IN (SELECT puzzleId FROM puzzle_progress WHERE solvedAt > 0) " +
            "AND (:minPly = 0 OR (length(moves) - length(replace(moves, ' ', '')) + 1) BETWEEN :minPly AND :maxPly) LIMIT $PROBE_PICK",
    )
    suspend fun probeInThemes(start: Long, window: Int, themes: List<String>, lo: Int, hi: Int, exclude: String, minPly: Int = 0, maxPly: Int = 0): List<String>

    // ---- Tirage exact (repli) : comptage puis lecture à un rang tiré au hasard ----

    @Query(
        "SELECT COUNT(*) FROM puzzles WHERE primaryTheme IN (:themes) AND rating BETWEEN :lo AND :hi AND id != :exclude " +
            "AND (:includeSolved OR id NOT IN (SELECT puzzleId FROM puzzle_progress WHERE solvedAt > 0)) " +
            "AND (:minPly = 0 OR (length(moves) - length(replace(moves, ' ', '')) + 1) BETWEEN :minPly AND :maxPly)",
    )
    suspend fun countInThemes(themes: List<String>, lo: Int, hi: Int, exclude: String, includeSolved: Boolean, minPly: Int = 0, maxPly: Int = 0): Int

    @Query(
        "SELECT id FROM puzzles WHERE primaryTheme IN (:themes) AND rating BETWEEN :lo AND :hi AND id != :exclude " +
            "AND (:includeSolved OR id NOT IN (SELECT puzzleId FROM puzzle_progress WHERE solvedAt > 0)) " +
            "AND (:minPly = 0 OR (length(moves) - length(replace(moves, ' ', '')) + 1) BETWEEN :minPly AND :maxPly) LIMIT 1 OFFSET :offset",
    )
    suspend fun idInThemesAt(themes: List<String>, lo: Int, hi: Int, exclude: String, includeSolved: Boolean, offset: Int, minPly: Int = 0, maxPly: Int = 0): String?

    @Query(
        "SELECT COUNT(*) FROM puzzles WHERE rating BETWEEN :lo AND :hi AND id != :exclude " +
            "AND (:includeSolved OR id NOT IN (SELECT puzzleId FROM puzzle_progress WHERE solvedAt > 0)) " +
            "AND (:minPly = 0 OR (length(moves) - length(replace(moves, ' ', '')) + 1) BETWEEN :minPly AND :maxPly)",
    )
    suspend fun countAll(lo: Int, hi: Int, exclude: String, includeSolved: Boolean, minPly: Int = 0, maxPly: Int = 0): Int

    @Query(
        "SELECT id FROM puzzles WHERE rating BETWEEN :lo AND :hi AND id != :exclude " +
            "AND (:includeSolved OR id NOT IN (SELECT puzzleId FROM puzzle_progress WHERE solvedAt > 0)) " +
            "AND (:minPly = 0 OR (length(moves) - length(replace(moves, ' ', '')) + 1) BETWEEN :minPly AND :maxPly) LIMIT 1 OFFSET :offset",
    )
    suspend fun idAllAt(lo: Int, hi: Int, exclude: String, includeSolved: Boolean, offset: Int, minPly: Int = 0, maxPly: Int = 0): String?

    /**
     * Compteurs agrégés du catalogue par thème principal, pour une tranche de classement.
     * Les totaux parcourent l'index couvrant (primaryTheme, rating), sans lecture de la table ni tri temporaire ;
     * les résolus partent de la petite table puzzle_progress (CROSS JOIN : ordre de jointure imposé). Environ dix fois
     * plus rapide que le comptage par sous-requête sur toute la tranche, pour un résultat identique.
     */
    @Query(
        "SELECT t.theme AS theme, t.total AS total, IFNULL(s.solved, 0) AS solved FROM " +
            "(SELECT primaryTheme AS theme, COUNT(*) AS total FROM puzzles INDEXED BY index_puzzles_primaryTheme_rating_id " +
            "WHERE rating BETWEEN :lo AND :hi GROUP BY primaryTheme) t " +
            "LEFT JOIN (SELECT p.primaryTheme AS theme, COUNT(*) AS solved FROM puzzle_progress pp " +
            "CROSS JOIN puzzles p ON p.id = pp.puzzleId WHERE pp.solvedAt > 0 AND p.rating BETWEEN :lo AND :hi GROUP BY p.primaryTheme) s " +
            "ON s.theme = t.theme",
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

    @Query("SELECT puzzleId FROM puzzle_progress WHERE solvedAt > 0")
    fun solvedIds(): Flow<List<String>>

    /** Nombre de problèmes résolus (sans charger leurs identifiants). */
    @Query("SELECT COUNT(*) FROM puzzle_progress WHERE solvedAt > 0")
    fun solvedCount(): Flow<Int>

    /** Parmi [ids] (quelques problèmes récents), ceux déjà résolus. */
    @Query("SELECT puzzleId FROM puzzle_progress WHERE solvedAt > 0 AND puzzleId IN (:ids)")
    fun solvedAmong(ids: List<String>): Flow<List<String>>

    // ---- v5 : suivi détaillé, « À refaire », favoris, bilan ----

    @Query("SELECT * FROM puzzle_progress WHERE puzzleId = :id")
    suspend fun progressOf(id: String): PuzzleProgress?

    @Query("SELECT IFNULL((SELECT bookmarked FROM puzzle_progress WHERE puzzleId = :id), 0)")
    fun bookmarked(id: String): Flow<Boolean>

    /** Favoris, les plus récemment joués d'abord. */
    @Query("SELECT p.* FROM puzzle_progress pp CROSS JOIN puzzles p ON p.id = pp.puzzleId WHERE pp.bookmarked = 1 ORDER BY pp.lastTriedAt DESC, pp.puzzleId LIMIT 100")
    fun bookmarks(): Flow<List<Puzzle>>

    /** « À refaire » : problèmes manqués ou résolus avec erreur, par date prévue. */
    @Query("SELECT * FROM puzzle_progress WHERE nextReviewAt > 0 ORDER BY nextReviewAt LIMIT 100")
    fun toRedo(): Flow<List<PuzzleProgress>>

    /** Essais conclus et thème principal de chaque problème (bilan tactique). */
    @Query(
        "SELECT pp.status AS status, pp.attempts AS attempts, pp.lastTriedAt AS lastTriedAt, p.primaryTheme AS theme " +
            "FROM puzzle_progress pp CROSS JOIN puzzles p ON p.id = pp.puzzleId WHERE pp.status > 0",
    )
    fun attempts(): Flow<List<AttemptRow>>

    // ---- v5 : « Inclure les thèmes secondaires » (table puzzle_themes) ----

    @Query(
        "SELECT COUNT(DISTINCT pt.puzzleId) FROM puzzle_themes pt CROSS JOIN puzzles p ON p.id = pt.puzzleId " +
            "WHERE pt.theme IN (:themes) AND pt.rating BETWEEN :lo AND :hi AND pt.puzzleId != :exclude " +
            "AND (:includeSolved OR pt.puzzleId NOT IN (SELECT puzzleId FROM puzzle_progress WHERE solvedAt > 0)) " +
            "AND (:minPly = 0 OR (length(p.moves) - length(replace(p.moves, ' ', '')) + 1) BETWEEN :minPly AND :maxPly)",
    )
    suspend fun countLinked(themes: List<String>, lo: Int, hi: Int, exclude: String, includeSolved: Boolean, minPly: Int, maxPly: Int): Int

    @Query(
        "SELECT DISTINCT pt.puzzleId FROM puzzle_themes pt CROSS JOIN puzzles p ON p.id = pt.puzzleId " +
            "WHERE pt.theme IN (:themes) AND pt.rating BETWEEN :lo AND :hi AND pt.puzzleId != :exclude " +
            "AND (:includeSolved OR pt.puzzleId NOT IN (SELECT puzzleId FROM puzzle_progress WHERE solvedAt > 0)) " +
            "AND (:minPly = 0 OR (length(p.moves) - length(replace(p.moves, ' ', '')) + 1) BETWEEN :minPly AND :maxPly) ORDER BY pt.puzzleId LIMIT 1 OFFSET :offset",
    )
    suspend fun idLinkedAt(themes: List<String>, lo: Int, hi: Int, exclude: String, includeSolved: Boolean, offset: Int, minPly: Int, maxPly: Int): String?

    /** Problèmes par thème officiel (principal ou secondaire) dans une tranche : la table de liaison sert d'index. */
    @Query("SELECT theme AS theme, COUNT(*) AS total, 0 AS solved FROM puzzle_themes WHERE rating BETWEEN :lo AND :hi GROUP BY theme")
    suspend fun linkedTotals(lo: Int, hi: Int): List<ThemeCountRow>

    /** Problèmes résolus de la tranche avec tous leurs thèmes (peu nombreux : comptés par thème en Kotlin). */
    @Query(
        "SELECT p.id AS id, p.rating AS rating, p.themes AS themes, p.primaryTheme AS primaryTheme FROM puzzle_progress pp " +
            "CROSS JOIN puzzles p ON p.id = pp.puzzleId WHERE pp.solvedAt > 0 AND p.rating BETWEEN :lo AND :hi",
    )
    suspend fun solvedMeta(lo: Int, hi: Int): List<PuzzleMeta>

    /** Problèmes distincts portant au moins un des [themes] dans la tranche (lecture de la seule table de liaison). */
    @Query("SELECT COUNT(DISTINCT puzzleId) FROM puzzle_themes WHERE theme IN (:themes) AND rating BETWEEN :lo AND :hi")
    suspend fun countLinkedTotal(themes: List<String>, lo: Int, hi: Int): Int

    /** Problèmes d'une tranche dont la solution compte entre [minPly] et [maxPly] demi-coups (coup adverse compris). */
    @Query(
        "SELECT id, rating, themes, primaryTheme FROM puzzles WHERE rating BETWEEN :lo AND :hi " +
            "AND (length(moves) - length(replace(moves, ' ', '')) + 1) BETWEEN :minPly AND :maxPly",
    )
    suspend fun metaWithLength(lo: Int, hi: Int, minPly: Int, maxPly: Int): List<PuzzleMeta>

    @Query("SELECT COUNT(*) FROM puzzle_themes")
    suspend fun linkCount(): Int
}

@Dao
interface UserDao {
    @Query("SELECT lessonId FROM lesson_progress WHERE masteredAt > 0")
    fun masteredLessons(): Flow<List<Int>>

    @Query("SELECT * FROM lesson_progress")
    fun lessonProgress(): Flow<List<LessonProgress>>

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :id")
    suspend fun lessonProgress(id: Int): LessonProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLesson(p: LessonProgress)

    @Insert
    suspend fun insertAssessment(a: AcademyAssessment): Long

    @Query("SELECT * FROM academy_assessments ORDER BY completedAt DESC")
    fun assessments(): Flow<List<AcademyAssessment>>

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
    entities = [Puzzle::class, PuzzleProgress::class, PuzzleThemeLink::class, LessonProgress::class, GameRecord::class, AcademyAssessment::class],
    version = 5,
    exportSchema = true,
)
abstract class DuckDatabase : RoomDatabase() {
    abstract fun puzzles(): PuzzleDao
    abstract fun user(): UserDao

    companion object {
        const val PUZZLE_COUNT = 150_000

        /** Base de l'application ([name] : autre fichier, pour les tests qui simulent une ancienne installation). */
        fun create(context: Context, name: String = "chessduck.db"): DuckDatabase =
            Room.databaseBuilder(context, DuckDatabase::class.java, name)
                .createFromAsset("chessduck.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
                        // v5 : les liens vers les thèmes (principal et secondaires) des problèmes importés.
                        conn.execSQL(themeLinksSql("catalogue.puzzles"))
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

        /**
         * v4 : progression détaillée des leçons (étoiles, erreurs, indices, exercices, révisions), résultats des tests
         * de l'Académie et position de départ des parties. Les leçons déjà maîtrisées en v3 reçoivent 3 étoiles.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (c in listOf("stars", "errors", "hintsUsed", "practicePassed", "practiceTotal", "srsStage", "nextReviewAt", "lastReviewedAt")) {
                    db.execSQL("ALTER TABLE lesson_progress ADD COLUMN $c INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("UPDATE lesson_progress SET stars = 3")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `academy_assessments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `level` INTEGER NOT NULL, `score` INTEGER NOT NULL, `total` INTEGER NOT NULL, " +
                        "`passed` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE games ADD COLUMN startFen TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Remplit puzzle_themes depuis la colonne `themes` de [from] : un lien par thème officiel (PuzzleThemes, les 50
         * thèmes du catalogue) de chaque problème, découpé en SQL natif (CTE récursive, aucune boucle Kotlin).
         */
        fun themeLinksSql(from: String = "puzzles"): String {
            val keys = PuzzleThemes.all.joinToString(",") { "'${it.key}'" }
            return "WITH RECURSIVE split(id, rating, rest, tag) AS (SELECT id, rating, trim(themes) || ' ', '' FROM $from " +
                "UNION ALL SELECT id, rating, substr(rest, instr(rest, ' ') + 1), substr(rest, 1, instr(rest, ' ') - 1) FROM split WHERE rest <> '') " +
                "INSERT OR IGNORE INTO puzzle_themes (theme, rating, puzzleId) SELECT tag, rating, id FROM split WHERE tag IN ($keys)"
        }

        /** Table de liaison v5, compacte (WITHOUT ROWID : la clé primaire est l'unique arbre). Même schéma que l'entité. */
        const val PUZZLE_THEMES_SQL = "CREATE TABLE IF NOT EXISTS `puzzle_themes` (`theme` TEXT NOT NULL, `rating` INTEGER NOT NULL, " +
            "`puzzleId` TEXT NOT NULL, PRIMARY KEY(`theme`, `rating`, `puzzleId`)) WITHOUT ROWID"

        /**
         * v5 : suivi détaillé des problèmes (issue, essais, « À refaire », favoris), index couvrants et table de liaison
         * des thèmes. Les problèmes déjà résolus gardent leur date et deviennent des résolutions nettes ; aucun
         * problème n'est modifié.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (c in listOf("status", "attempts", "nextReviewAt", "lastTriedAt", "bookmarked")) {
                    db.execSQL("ALTER TABLE puzzle_progress ADD COLUMN $c INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("UPDATE puzzle_progress SET status = 1, attempts = 1, lastTriedAt = solvedAt WHERE solvedAt > 0")
                db.execSQL("DROP INDEX IF EXISTS `index_puzzles_rating`")
                db.execSQL("DROP INDEX IF EXISTS `index_puzzles_primaryTheme_rating`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_puzzles_rating_id` ON `puzzles` (`rating`, `id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_puzzles_primaryTheme_rating_id` ON `puzzles` (`primaryTheme`, `rating`, `id`)")
                db.execSQL(PUZZLE_THEMES_SQL)
                db.execSQL(themeLinksSql())
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
