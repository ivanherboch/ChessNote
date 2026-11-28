package com.ivanherboch.chessduck

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivanherboch.chessduck.data.AcademyAssessment
import com.ivanherboch.chessduck.data.BackupFormatException
import com.ivanherboch.chessduck.data.DuckDatabase
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.data.LessonProgress
import com.ivanherboch.chessduck.data.PuzzleOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Sauvegarde hors-ligne : une progression complète exportée, la base et les réglages vidés, puis réimportés, revient à
 * l'identique ; un fichier invalide est refusé sans rien modifier. Le catalogue des problèmes n'est jamais touché.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UserBackupTest {
    private val app get() = ApplicationProvider.getApplicationContext<ChessDuckApp>()

    @Test
    fun exportWipeImportRestoresEverything(): Unit = runBlocking {
        val db = app.db
        val prefs = app.prefs
        val puzzles = com.ivanherboch.chessduck.data.RoomPuzzleRepository(app, db)
        val ids = db.puzzles().slice(1, 3).map { it.id }
        val catalogue = db.puzzles().count()
        // Une progression variée : leçons (maîtrise, étoiles, révisions), problèmes (résolu, favori, à refaire),
        // parties (dont une hors ELO), tests de l'Académie, et des réglages de tous les types.
        db.user().saveLesson(LessonProgress(1, masteredAt = 111, stars = 3, errors = 2, hintsUsed = 1, practicePassed = 4, practiceTotal = 5, srsStage = 2, nextReviewAt = 999, lastReviewedAt = 555))
        db.user().saveLesson(LessonProgress(115, masteredAt = 0, stars = 1))
        puzzles.recordAttempt(ids[0], PuzzleOutcome.CLEAN, 1_000)
        puzzles.recordAttempt(ids[1], PuzzleOutcome.GAVE_UP, 2_000)
        puzzles.setBookmark(ids[2], true)
        db.user().insertGame(GameRecord(date = 5, opponent = "Ordinateur 1200", vsComputer = true, playerWhite = true, result = "1-0", outcome = 1, reason = "échec et mat", eloBefore = 1200, eloAfter = 1216, moves = "e2e4 e7e5", analysis = "a|b", whiteName = "Joueur", blackName = "Ordinateur", opponentElo = 1200))
        db.user().insertGame(GameRecord(date = 6, opponent = "Ordinateur 600", vsComputer = true, playerWhite = false, result = "1/2-1/2", outcome = 0, reason = "pat", eloBefore = 1216, eloAfter = 0, moves = "d2d4", startFen = "8/8/8/8/8/8/8/K6k w - - 0 1"))
        db.user().insertAssessment(AcademyAssessment(kind = "exam", level = 2, score = 8, total = 10, passed = true, completedAt = 77))
        prefs.setElo(1337)
        prefs.setPuzzleElo(1450)
        prefs.setRatedGame(true)
        prefs.markUnderstood(117)
        prefs.markDailyStep("lesson", 20_000, setOf("lesson"))
        prefs.setPseudo("Ivan")

        val before = snapshot(db, prefs)
        val json = app.container.backup.exportJson("test", now = 42)
        assertTrue(json.contains("\"format\":\"chessduck-backup\""))

        // Téléphone « neuf » : progression et réglages effacés.
        db.user().clearLessons(); db.user().clearGames(); db.user().clearAssessments(); db.puzzles().clearProgress()
        prefs.restore(emptyMap())
        assertTrue(db.user().allLessons().isEmpty() && prefs.settings.first().elo != 1337)

        val c = app.container.backup.importJson(json)
        assertEquals("2 leçons, 3 problèmes, 2 parties, 1 test", c.summary())
        assertEquals(before, snapshot(db, prefs))
        val st = prefs.settings.first()
        assertTrue(st.elo == 1337 && st.puzzleElo == 1450 && st.ratedGame && 117 in st.understoodIds && st.pseudo == "Ivan")
        assertTrue(st.daily.streak > 0)
        assertEquals(catalogue, db.puzzles().count()) // le catalogue n'a pas bougé
    }

    @Test
    fun invalidFilesAreRefusedWithoutChangingAnything(): Unit = runBlocking {
        val db = app.db
        db.user().saveLesson(LessonProgress(3, masteredAt = 5, stars = 2))
        app.prefs.setElo(1500)
        val before = snapshot(db, app.prefs)
        for (bad in listOf(
            "", "pas du json", "[]", "{\"format\":\"autre\"}",
            "{\"format\":\"chessduck-backup\",\"version\":99}",
            // Champ manquant dans une leçon : tout le fichier est refusé, même si le reste est valide.
            "{\"format\":\"chessduck-backup\",\"version\":1,\"lessons\":[{\"lessonId\":1}],\"puzzles\":[],\"games\":[],\"assessments\":[],\"settings\":{}}",
            "{\"format\":\"chessduck-backup\",\"version\":1,\"lessons\":[],\"puzzles\":[],\"games\":[],\"assessments\":[],\"settings\":{\"x\":{\"t\":\"?\",\"v\":1}}}",
        )) {
            try {
                app.container.backup.importJson(bad)
                fail("accepté : $bad")
            } catch (e: BackupFormatException) {
                assertTrue(e.message.orEmpty().isNotEmpty())
            }
        }
        assertEquals(before, snapshot(db, app.prefs))
    }

    private suspend fun snapshot(db: DuckDatabase, prefs: com.ivanherboch.chessduck.data.Prefs) = listOf(
        db.user().allLessons().sortedBy { it.lessonId },
        db.puzzles().allProgress().sortedBy { it.puzzleId },
        db.user().allGames(),
        db.user().allAssessments(),
        prefs.snapshot(),
    )
}
