package com.ivanherboch.chessduck.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Fichier de sauvegarde illisible ou d'un autre format : rien n'a été modifié. */
class BackupFormatException(message: String) : Exception(message)

/**
 * Sauvegarde hors-ligne de toute la progression, en JSON (sans dépendance supplémentaire : org.json d'Android).
 *
 * Contenu : leçons (étoiles, maîtrise, révisions), problèmes (résolus, favoris, à refaire), parties et leurs analyses,
 * tests de l'Académie, et toutes les préférences (ELO, ELO tactique, séance du jour, réglages). Jamais le catalogue
 * des problèmes (tables puzzles et puzzle_themes), qui vient de l'application.
 *
 * L'import vérifie d'abord tout le fichier ; s'il est valide, il remplace la progression actuelle en une transaction
 * (rien n'est modifié si le fichier est refusé).
 */
class UserBackup(private val db: DuckDatabase, private val prefs: Prefs) {

    /** Progression lue dans un fichier de sauvegarde, entièrement vérifiée. */
    class Content(
        val lessons: List<LessonProgress>,
        val puzzles: List<PuzzleProgress>,
        val games: List<GameRecord>,
        val assessments: List<AcademyAssessment>,
        val settings: Map<String, Any>,
    ) {
        /** Résumé en une phrase, pour l'utilisateur. */
        fun summary(): String =
            "${lessons.size} leçon${s(lessons.size)}, ${puzzles.size} problème${s(puzzles.size)}, ${games.size} partie${s(games.size)}, " +
                "${assessments.size} test${s(assessments.size)}"

        private fun s(n: Int) = if (n > 1) "s" else ""
    }

    /** Toute la progression actuelle, en JSON. */
    suspend fun exportJson(appVersion: String, now: Long = System.currentTimeMillis()): String {
        val user = db.user()
        val content = Content(user.allLessons(), db.puzzles().allProgress(), user.allGames(), user.allAssessments(), prefs.snapshot())
        return encode(content, appVersion, now)
    }

    /** Vérifie [text] puis remplace la progression par son contenu ; lève [BackupFormatException] sans rien modifier. */
    suspend fun importJson(text: String): Content {
        val c = decode(text)
        db.withTransaction {
            val user = db.user()
            user.clearLessons(); user.insertLessons(c.lessons)
            user.clearGames(); user.insertGames(c.games)
            user.clearAssessments(); user.insertAssessments(c.assessments)
            db.puzzles().clearProgress(); db.puzzles().insertProgress(c.puzzles)
        }
        prefs.restore(c.settings)
        return c
    }

    companion object {
        const val FORMAT = "chessduck-backup"
        const val VERSION = 1

        fun encode(c: Content, appVersion: String, now: Long): String {
            val o = JSONObject()
            o.put("format", FORMAT).put("version", VERSION).put("appVersion", appVersion).put("exportedAt", now)
            o.put("lessons", JSONArray(c.lessons.map {
                JSONObject().put("lessonId", it.lessonId).put("masteredAt", it.masteredAt).put("stars", it.stars).put("errors", it.errors)
                    .put("hintsUsed", it.hintsUsed).put("practicePassed", it.practicePassed).put("practiceTotal", it.practiceTotal)
                    .put("srsStage", it.srsStage).put("nextReviewAt", it.nextReviewAt).put("lastReviewedAt", it.lastReviewedAt)
            }))
            o.put("puzzles", JSONArray(c.puzzles.map {
                JSONObject().put("puzzleId", it.puzzleId).put("solvedAt", it.solvedAt).put("status", it.status).put("attempts", it.attempts)
                    .put("nextReviewAt", it.nextReviewAt).put("lastTriedAt", it.lastTriedAt).put("bookmarked", it.bookmarked)
            }))
            o.put("games", JSONArray(c.games.map {
                JSONObject().put("id", it.id).put("date", it.date).put("opponent", it.opponent).put("vsComputer", it.vsComputer)
                    .put("playerWhite", it.playerWhite).put("result", it.result).put("outcome", it.outcome).put("reason", it.reason)
                    .put("eloBefore", it.eloBefore).put("eloAfter", it.eloAfter).put("moves", it.moves).put("analysis", it.analysis)
                    .put("whiteName", it.whiteName).put("blackName", it.blackName).put("opponentElo", it.opponentElo).put("startFen", it.startFen)
            }))
            o.put("assessments", JSONArray(c.assessments.map {
                JSONObject().put("id", it.id).put("kind", it.kind).put("level", it.level).put("score", it.score).put("total", it.total)
                    .put("passed", it.passed).put("completedAt", it.completedAt)
            }))
            // Préférences typées : le type est gardé pour les relire à l'identique.
            val st = JSONObject()
            for ((k, v) in c.settings) {
                val (t, value: Any) = when (v) {
                    is Boolean -> "b" to v
                    is Int -> "i" to v
                    is Long -> "l" to v
                    is Float -> "f" to v.toDouble()
                    is Double -> "d" to v
                    is String -> "s" to v
                    is Set<*> -> "ss" to JSONArray(v.filterIsInstance<String>().sorted())
                    else -> continue
                }
                st.put(k, JSONObject().put("t", t).put("v", value))
            }
            o.put("settings", st)
            return o.toString()
        }

        /** Lit et vérifie toute la sauvegarde ; lève [BackupFormatException] au premier problème. */
        fun decode(text: String): Content = try {
            val o = JSONObject(text)
            if (o.optString("format") != FORMAT) throw BackupFormatException("Ce fichier n'est pas une sauvegarde ChessDuck.")
            val version = o.getInt("version")
            if (version < 1 || version > VERSION) {
                throw BackupFormatException("Cette sauvegarde vient d'une version plus récente de ChessDuck : mettez l'application à jour.")
            }
            Content(
                lessons = objects(o, "lessons").map {
                    LessonProgress(
                        it.getInt("lessonId"), it.getLong("masteredAt"), it.getInt("stars"), it.getInt("errors"), it.getInt("hintsUsed"),
                        it.getInt("practicePassed"), it.getInt("practiceTotal"), it.getInt("srsStage"), it.getLong("nextReviewAt"), it.getLong("lastReviewedAt"),
                    )
                },
                puzzles = objects(o, "puzzles").map {
                    PuzzleProgress(
                        it.getString("puzzleId"), it.getLong("solvedAt"), it.getInt("status"), it.getInt("attempts"),
                        it.getLong("nextReviewAt"), it.getLong("lastTriedAt"), it.getBoolean("bookmarked"),
                    )
                },
                games = objects(o, "games").map {
                    GameRecord(
                        id = it.getLong("id"), date = it.getLong("date"), opponent = it.getString("opponent"), vsComputer = it.getBoolean("vsComputer"),
                        playerWhite = it.getBoolean("playerWhite"), result = it.getString("result"), outcome = it.getInt("outcome"),
                        reason = it.getString("reason"), eloBefore = it.getInt("eloBefore"), eloAfter = it.getInt("eloAfter"), moves = it.getString("moves"),
                        analysis = it.getString("analysis"), whiteName = it.getString("whiteName"), blackName = it.getString("blackName"),
                        opponentElo = it.getInt("opponentElo"), startFen = it.getString("startFen"),
                    )
                },
                assessments = objects(o, "assessments").map {
                    AcademyAssessment(
                        it.getLong("id"), it.getString("kind"), it.getInt("level"), it.getInt("score"), it.getInt("total"),
                        it.getBoolean("passed"), it.getLong("completedAt"),
                    )
                },
                settings = o.getJSONObject("settings").let { st ->
                    st.keys().asSequence().associateWith { k ->
                        val e = st.getJSONObject(k)
                        when (val t = e.getString("t")) {
                            "b" -> e.getBoolean("v")
                            "i" -> e.getInt("v")
                            "l" -> e.getLong("v")
                            "f" -> e.getDouble("v").toFloat()
                            "d" -> e.getDouble("v")
                            "s" -> e.getString("v")
                            "ss" -> e.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                            else -> throw BackupFormatException("Réglage « $k » d'un type inconnu ($t).")
                        }
                    }
                },
            )
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: JSONException) {
            throw BackupFormatException("Fichier de sauvegarde illisible ou incomplet : rien n'a été modifié.")
        }

        private fun objects(o: JSONObject, key: String): List<JSONObject> {
            val a = o.getJSONArray(key)
            return (0 until a.length()).map { a.getJSONObject(it) }
        }
    }
}
