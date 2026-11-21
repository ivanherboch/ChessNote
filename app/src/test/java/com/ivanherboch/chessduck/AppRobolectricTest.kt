package com.ivanherboch.chessduck

import androidx.compose.ui.test.onFirst
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivanherboch.chessduck.data.DuckDatabase
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.data.LessonProgress
import com.ivanherboch.chessduck.data.PuzzleProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Vérifie la base embarquée (schéma Room + 150 000 problèmes) et produit des captures de chaque écran. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class AppRobolectricTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** Les réglages (DataStore) survivent d'un test à l'autre : chaque test démarre hors du mode enfant. */
    @Before
    fun leaveKidMode() = runBlocking { ApplicationProvider.getApplicationContext<ChessDuckApp>().prefs.setKidMode(false) }

    /** Ajoute une courte partie terminée à l'historique de l'application (mat du berger). */
    /**
     * Partie perdue par le joueur (Noirs) sur une gaffe : le cavalier en f6 permet le mat en f7. L'analyse est
     * fournie, comme après la revue en tâche de fond : la gaffe désigne un thème de l'Académie.
     */
    private fun seedBlunder() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ChessDuckApp>()
        val a = listOf(
            com.ivanherboch.chessduck.feature.Analysis(30, 20, "e2e4"), com.ivanherboch.chessduck.feature.Analysis(30, 20, "e7e5"),
            com.ivanherboch.chessduck.feature.Analysis(30, 20, "g1f3"), com.ivanherboch.chessduck.feature.Analysis(30, 20, "b8c6"),
            com.ivanherboch.chessduck.feature.Analysis(20, 10, "g1f3"), com.ivanherboch.chessduck.feature.Analysis(0, -20, "g7g6"),
            com.ivanherboch.chessduck.feature.Analysis(com.ivanherboch.chessduck.engine.MATE - 1, null, "h5f7"),
            com.ivanherboch.chessduck.feature.Analysis(com.ivanherboch.chessduck.engine.MATE, null, ""),
        )
        app.db.user().insertGame(
            GameRecord(
                date = 1_764_000_000_000, opponent = "Ordinateur 1200", vsComputer = true, playerWhite = false, result = "1-0", outcome = -1,
                reason = "échec et mat", eloBefore = 1200, eloAfter = 1184, moves = "e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7",
                whiteName = "🤖 Ordinateur", blackName = "Joueur", opponentElo = 1200,
                analysis = com.ivanherboch.chessduck.feature.Analysis.encodeAll(a),
            ),
        )
    }

    /** Étape 3 : Vision des cases, pont Revue → Académie, bilan des priorités et calcul à l'aveugle. */
    @Test
    fun step3Screens() {
        settle(2500)
        rule.onNodeWithText("Niveau : Tous").performClick(); settle(500)
        rule.onNodeWithText("Vision des cases (exercice)").performClick(); settle(800); shot("1c_vision")
        rule.onNodeWithText("Vision des cases").assertExists()
        desc("Revenir à la notation").performClick(); settle(800)
        seedBlunder()
        rule.onNodeWithText("Jeu Libre").performClick(); settle(1500)
        rule.onNodeWithContentDescription("Historique des parties").performClick(); settle(1200)
        rule.onAllNodesWithText("Revue commentée").onFirst().performClick(); settle(600)
        repeat(6) { desc("Coup suivant").performClick(); settle(300) }
        settle(800); shot("4g_revue_pont")
        rule.onNodeWithText("Travailler ce point dans l'Académie").performClick(); settle(3000)
        shot("2q_pont_academie")
        rule.onNodeWithText("Méthode : le contrôle anti-gaffe", substring = true, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Entraînements", substring = true).performClick(); settle(1500)
        // Le bilan relit les parties en tâche de fond : on attend qu'il soit publié. S'il tarde (invalidation Room
        // sous Robolectric), on referme le tiroir plus de 5 s pour que le bilan soit recalculé à la réouverture.
        fun prioritiesShown(ms: Long) = runCatching {
            rule.waitUntil(ms) {
                shadowOf(Looper.getMainLooper()).idle()
                rule.onAllNodesWithText("Mat permis à l'adversaire", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        if (!prioritiesShown(15_000)) {
            shot("2r_priorites_echec")
            rule.onNodeWithContentDescription("Fermer").performClick(); settle(6000)
            rule.onNodeWithText("Entraînements", substring = true).performClick(); settle(1500)
            check(prioritiesShown(30_000)) { "Bilan des priorités absent" }
        }
        settle(500); shot("2r_priorites")
        rule.onNodeWithText("Calcul à l'aveugle (palier 5)").performScrollTo().performClick(); settle(1500)
        rule.waitUntil(30_000) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.onAllNodesWithText("Coups annoncés", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        settle(800); shot("2s_aveugle")
        // Tiroir rouvert pendant la séance : l'exercice en cours est surligné et annoncé « En cours ».
        rule.onNodeWithText("Entraînements", substring = true).performClick(); settle(1000)
        rule.onNodeWithText("Calcul à l'aveugle (palier 5) · En cours").performScrollTo()
            .assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Selected, true))
        settle(400); shot("2t_entrainement_en_cours")
    }

    private fun seedGame() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ChessDuckApp>()
        app.db.user().insertGame(
            GameRecord(
                date = 1_763_000_000_000, opponent = "Ordinateur 1200", vsComputer = true, playerWhite = true, result = "1-0", outcome = 1,
                reason = "échec et mat", eloBefore = 1200, eloAfter = 1216, moves = "e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7",
                whiteName = "Joueur", blackName = "🤖 Ordinateur", opponentElo = 1200,
            ),
        )
    }

    /**
     * Laisse l'application se stabiliser sans attente fixe : le temps virtuel avance de [ms] (animations,
     * délais du fil principal), puis on attend la condition « interface stable » — arbre sémantique inchangé
     * pendant [STABLE_WINDOW_MS], aucun indicateur de chargement et fil principal au repos — le temps que les travaux en arrière-plan
     * (base de données, DataStore, rendus) aient publié leurs résultats.
     */
    private fun settle(ms: Long = 1500) {
        val looper = shadowOf(Looper.getMainLooper())
        looper.idleFor(Duration.ofMillis(ms))
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
        var last = ""
        var since = System.nanoTime()
        rule.waitUntil(timeoutMillis = 15_000) {
            looper.idle()
            val tree = runCatching { rule.onRoot(useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE) }.getOrDefault("")
            if (tree != last) { last = tree; since = System.nanoTime() }
            // Aucun indicateur de progression indéterminé (chargement, réflexion, rendu d'aperçu) à l'écran.
            val busy = rule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
            !busy && looper.isIdle && (System.nanoTime() - since) / 1_000_000 >= STABLE_WINDOW_MS
        }
    }

    private fun shot(name: String) {
        val dir = File(System.getenv("SHOT_DIR") ?: "build/screenshots").apply { mkdirs() }
        rule.onRoot().captureRoboImage(File(dir, "$name.png").path)
    }

    @Test
    fun prepackagedDatabaseMatchesRoomSchema() = runBlocking {
        val db = DuckDatabase.create(ApplicationProvider.getApplicationContext())
        assertEquals(150_000, db.puzzles().count())
        val p = db.puzzles().byId("0000D")
        assertNotNull(p)
        db.puzzles().markSolved(PuzzleProgress(p!!.id, 1L))
        assertEquals(listOf(p.id), db.puzzles().solvedIds().first())
        val meta = db.puzzles().allMeta()
        assertEquals(150_000, meta.size)
        assertEquals(150_000, meta.map { it.id }.toSet().size) // aucun doublon
        // Au moins 300 problèmes par difficulté × thème, sauf les cases que la base Lichess entière ne peut pas remplir.
        // La règle optimisée de PuzzleThemes.primary donne exactement le même thème que la règle de référence.
        val byKey = com.ivanherboch.chessduck.content.PuzzleThemes.byKey
        for (pm in meta) {
            val set = pm.themes.trim().split(' ').toSet()
            val ref = REFERENCE_PRIORITY.firstOrNull { it in set && it in byKey } ?: "middlegame"
            assertEquals(pm.id, ref, com.ivanherboch.chessduck.content.PuzzleThemes.primary(pm.themes).key)
            // Colonne v3 précalculée dans la base livrée : même thème principal.
            assertEquals(pm.id, ref, pm.primaryTheme)
        }
        // Requêtes indexées (v3) : compteurs, tirage filtré et recherche d'identifiant.
        val repo = com.ivanherboch.chessduck.data.RoomPuzzleRepository(ApplicationProvider.getApplicationContext(), db)
        val counts = repo.themeCounts(0..4000)
        assertEquals(150_000, counts.sumOf { it.total })
        assertEquals(1, counts.sumOf { it.solved })
        assertEquals(meta.count { it.primaryTheme == "fork" && it.rating in 1200..1599 }, repo.themeCounts(1200..1599).first { it.theme == "fork" }.total)
        val rnd = kotlin.random.Random(7)
        repeat(20) {
            val id = repo.randomId(listOf("pin", "skewer"), 1600..1999, "", rnd)!!
            val pz = db.puzzles().byId(id)!!
            assertTrue(pz.primaryTheme in setOf("pin", "skewer") && pz.rating in 1600..1999)
        }
        assertNull(repo.randomId(listOf("fork"), 5000..6000, "", rnd))
        val (hits, total) = repo.searchIds("0000d", 5)
        assertEquals("0000D", hits.first().id)
        assertTrue(total >= hits.size)
        assertEquals(0, repo.searchIds("%_", 5).second)
        val cells = meta.groupingBy { com.ivanherboch.chessduck.content.PuzzleThemes.bandOf(it.rating) to com.ivanherboch.chessduck.content.PuzzleThemes.primary(it.themes).key }.eachCount()
        val shortCells = (1..4).flatMap { b -> com.ivanherboch.chessduck.content.PuzzleThemes.all.map { b to it.key } }.filter { (cells[it] ?: 0) < 300 }
        assertEquals(18, shortCells.size)
        assertEquals(182, 200 - shortCells.size)
        // Une base déjà complète n'est pas réimportée.
        assertEquals(false, DuckDatabase.syncPuzzles(ApplicationProvider.getApplicationContext(), db))
        assertEquals(30, db.puzzles().slice(100, 30).size)
        // v4 : seule une leçon maîtrisée (masteredAt > 0) compte ; étoiles et exercices sont conservés.
        db.user().saveLesson(LessonProgress(3, 1L, stars = 2, practicePassed = 4, practiceTotal = 5))
        db.user().saveLesson(LessonProgress(4, 0L, stars = 3))
        assertEquals(listOf(3), db.user().masteredLessons().first())
        assertEquals(4, db.user().lessonProgress(3)?.practicePassed)
        db.user().insertAssessment(com.ivanherboch.chessduck.data.AcademyAssessment(kind = "placement", level = 3, score = 9, total = 12, passed = true, completedAt = 1L))
        assertEquals(1, db.user().assessments().first().size)
        // La base embarquée (v1) est migrée en v2 : noms des joueurs et ELO de l'ordinateur.
        val id = db.user().insertGame(GameRecord(date = 1, opponent = "Ordinateur 1200", vsComputer = true, playerWhite = true, result = "1-0", outcome = 1, reason = "échec et mat", eloBefore = 1200, eloAfter = 1216, moves = "e2e4 e7e5", whiteName = "Ivan", blackName = "🤖 Ordinateur", opponentElo = 1200))
        assertEquals("Ivan", db.user().games().first().first().whiteName)
        assertEquals(1200, db.user().games().first().first().opponentElo)
        db.user().saveAnalysis(id, "10,5,e2e4")
        assertEquals(1, db.user().games().first().size)
        assertEquals(1, db.user().tally().first().first { it.outcome == 1 }.n)
        // Carte de partage : type de problème et conseil visibles.
        val line = com.ivanherboch.chessduck.chess.Line.fromUci(p.fen, p.moves)
        val bmp = com.ivanherboch.chessduck.feature.ShareCard.render(ApplicationProvider.getApplicationContext(), p, line.positions[1], line.positions[1].whiteToMove, line.moves[0])
        val dir = File(System.getenv("SHOT_DIR") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "6_partage.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val ctx: android.content.Context = ApplicationProvider.getApplicationContext()
        db.puzzles().byId("00WqT")?.let { pz ->
            val l = com.ivanherboch.chessduck.chess.Line.fromUci(pz.fen, pz.moves)
            val img = com.ivanherboch.chessduck.feature.ShareCard.render(ctx, pz, l.positions[1], l.positions[1].whiteToMove, l.moves[0])
            File(dir, "6b_partage_mat.png").outputStream().use { img.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        for ((name, id) in listOf("7_solution_courte" to "0000D", "8_solution_longue" to "005f3", "8b_solution_mat" to "00WqT")) {
            val pz = db.puzzles().byId(id) ?: continue
            val sol = com.ivanherboch.chessduck.feature.ShareCard.renderSolution(ctx, pz)
            File(dir, "$name.png").outputStream().use { sol.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        // Toutes les variantes : portrait / carré, sombre / clair.
        for (fmt in com.ivanherboch.chessduck.feature.ShareFormat.values()) for (light in listOf(false, true)) {
            val st = com.ivanherboch.chessduck.feature.ShareStyle(fmt, light)
            val tag = fmt.name.lowercase() + if (light) "_clair" else "_sombre"
            for (id in listOf("00WqT", "005f3", "0000D")) {
                val pz = db.puzzles().byId(id) ?: continue
                val l = com.ivanherboch.chessduck.chess.Line.fromUci(pz.fen, pz.moves)
                val a = com.ivanherboch.chessduck.feature.ShareCard.render(ctx, pz, l.positions[1], l.positions[1].whiteToMove, l.moves[0], st)
                val b = com.ivanherboch.chessduck.feature.ShareCard.renderSolution(ctx, pz, st)
                assertEquals(fmt.height, a.height); assertEquals(fmt.height, b.height)
                File(dir, "share_${tag}_${id}_pb.png").outputStream().use { a.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                File(dir, "share_${tag}_${id}_sol.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        // Les images reprennent la palette choisie dans les Paramètres.
        db.puzzles().byId("0000D")?.let { pz ->
            val l = com.ivanherboch.chessduck.chess.Line.fromUci(pz.fen, pz.moves)
            val shots = com.ivanherboch.chessduck.ui.DuckPalette.entries.map { pal ->
                val st = com.ivanherboch.chessduck.feature.ShareStyle(palette = pal)
                com.ivanherboch.chessduck.feature.ShareCard.renderSolution(ctx, pz, st).also { img ->
                    File(dir, "share_palette_${pal.name.lowercase()}.png").outputStream().use { img.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            for (i in shots.indices) for (j in i + 1 until shots.size) assertFalse(shots[i].sameAs(shots[j]))
            // … et les couleurs d'échiquier choisies : chaque matière donne une image différente.
            val boards = com.ivanherboch.chessduck.ui.BoardTheme.entries.map { b ->
                com.ivanherboch.chessduck.feature.ShareCard.render(ctx, pz, l.positions[1], l.positions[1].whiteToMove, l.moves[0], com.ivanherboch.chessduck.feature.ShareStyle(board = b)).also { img ->
                    File(dir, "share_board_${b.name.lowercase()}.png").outputStream().use { img.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            for (i in boards.indices) for (j in i + 1 until boards.size) assertFalse(boards[i].sameAs(boards[j]))
        }
        assertNotNull(db.puzzles().byIdIgnoreCase("0000d"))
        db.close()
    }

    /**
     * Migration 3 → 4 : une base v3 reconstruite à partir du schéma exporté (app/schemas) est ouverte par Room, qui
     * applique MIGRATION_3_4 puis vérifie le schéma v4. Leçons maîtrisées (3 étoiles) et historique sont conservés.
     */
    @Test
    fun migration3To4KeepsLessonsAndGames() = runBlocking {
        val ctx: android.content.Context = ApplicationProvider.getApplicationContext()
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val schema = org.json.JSONObject(File(root, "app/schemas/com.ivanherboch.chessduck.data.DuckDatabase/3.json").readText()).getJSONObject("database")
        val name = "chessduck-v3.db"
        ctx.deleteDatabase(name)
        val path = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null).use { sql ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val idx = e.optJSONArray("indices") ?: org.json.JSONArray()
                for (k in 0 until idx.length()) sql.execSQL(idx.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
            sql.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '${schema.getString("identityHash")}')")
            sql.execSQL("INSERT INTO lesson_progress (lessonId, masteredAt) VALUES (7, 123)")
            sql.execSQL(
                "INSERT INTO games (date, opponent, vsComputer, playerWhite, result, outcome, reason, eloBefore, eloAfter, moves, analysis, whiteName, blackName, opponentElo) " +
                    "VALUES (1, 'Ordinateur 1200', 1, 1, '1-0', 1, 'abandon', 1200, 1216, 'e2e4', '', 'Ivan', 'Ordinateur', 1200)",
            )
            sql.version = 3
        }
        val db = DuckDatabase.create(ctx, name)
        assertEquals(listOf(7), db.user().masteredLessons().first())
        assertEquals(3, db.user().lessonProgress(7)?.stars)
        val game = db.user().games().first().single()
        assertEquals("", game.startFen)
        assertEquals("Ivan", game.whiteName)
        db.user().insertAssessment(com.ivanherboch.chessduck.data.AcademyAssessment(kind = "exam", level = 2, score = 8, total = 10, passed = true, completedAt = 2L))
        assertEquals(1, db.user().assessments().first().size)
        db.close()
        ctx.deleteDatabase(name)
        Unit
    }

    @Test
    fun upgradeImportsNewPuzzlesAndKeepsProgress() = runBlocking {
        val old = File(System.getenv("OLD_DB") ?: "/opt/work/old50k.db")
        if (!old.exists()) return@runBlocking
        val ctx: android.content.Context = ApplicationProvider.getApplicationContext()
        // Simule une installation existante : l'ancienne base (v1, 50 000 problèmes) est déjà sur l'appareil.
        // Fichier propre au test : la base de l'application lancée par la règle peut déjà être ouverte.
        val name = "chessduck-ancienne-installation.db"
        ctx.deleteDatabase(name)
        val target = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        old.copyTo(target, overwrite = true)
        val db = DuckDatabase.create(ctx, name)
        assertEquals(50_000, db.puzzles().count())
        db.puzzles().markSolved(PuzzleProgress("0000D", 1L))
        assertEquals(true, DuckDatabase.syncPuzzles(ctx, db))
        assertEquals(150_000, db.puzzles().count())
        assertEquals(listOf("0000D"), db.puzzles().solvedIds().first())
        // Migrations 1 → 2 → 3 puis import SQL natif : chaque problème a le même thème principal que la règle Kotlin.
        for (pm in db.puzzles().allMeta()) assertEquals(pm.id, com.ivanherboch.chessduck.content.PuzzleThemes.primary(pm.themes).key, pm.primaryTheme)
        db.close()
    }

    @Test
    fun reviewSummaryLabelsAreNeverTruncated() {
        // Cas réel : « L'ordinateur (Blancs) » + 5 compteurs + « 1 mat manqué » sur un téléphone de 360 dp.
        rule.activity.setContent {
            com.ivanherboch.chessduck.ui.ChessDuckTheme(true) {
                val c = com.ivanherboch.chessduck.ui.LocalDuck.current
                val counts: (String) -> @androidx.compose.runtime.Composable () -> Unit = { missed -> {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                        for (n in listOf("!!", "!", "?!", "?", "??")) androidx.compose.material3.Text("$n 0", color = c.text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                        if (missed.isNotEmpty()) androidx.compose.material3.Text(missed, color = c.redText, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    }
                } }
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.width(360.dp).background(c.bg).padding(12.dp)) {
                    // 1) Cas réel : ne tient pas sur une ligne → les deux joueurs passent à la ligne, compteurs à gauche.
                    // 2) Noms courts : tout tient → une ligne chacun, compteurs alignés en colonne.
                    for (group in listOf(
                        listOf("L'ordinateur (Blancs)" to "1 mat manqué", "Guduche (Noirs)" to ""),
                        listOf("Ivan (Blancs)" to "", "Lu (Noirs)" to ""),
                        listOf("Un pseudo vraiment très long (Noirs)" to "2 mats manqués", "Moi (Blancs)" to ""),
                    )) {
                        com.ivanherboch.chessduck.ui.AlignedLabelRows(
                            group.map { (label, missed) -> com.ivanherboch.chessduck.ui.LabeledRow(label, label, counts(missed)) },
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = c.text,
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 14.dp),
                        )
                    }
                }
            }
        }
        settle(500)
        for (label in listOf("L'ordinateur (Blancs)", "Guduche (Noirs)", "Ivan (Blancs)", "Lu (Noirs)", "Un pseudo vraiment très long (Noirs)", "Moi (Blancs)")) {
            val node = rule.onNodeWithText(label).fetchSemanticsNode()
            val layouts = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
            node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val tl = layouts.first()
            // Tout le texte est mis en page et chaque ligne tient dans la largeur réellement affichée (aucune troncature).
            org.junit.Assert.assertEquals(label, label.length, tl.getLineEnd(tl.lineCount - 1))
            for (i in 0 until tl.lineCount) {
                org.junit.Assert.assertFalse(label, tl.isLineEllipsized(i))
                org.junit.Assert.assertTrue(label, tl.getLineRight(i) <= node.size.width + 1f)
            }
        }
        shot("11_resume_libelles")
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w1280dp-h1100dp-mdpi")
    fun paletteGallery() {
        // Chaque palette (Nuit puis Jour) sur de vrais composants : boutons, carte de message, échiquier et flèches.
        val pos = com.ivanherboch.chessduck.chess.Position.fromFen("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4")
        rule.activity.setContent {
            androidx.compose.foundation.layout.Column {
                for (dark in listOf(true, false)) androidx.compose.foundation.layout.Row {
                    for (p in com.ivanherboch.chessduck.ui.DuckPalette.entries) com.ivanherboch.chessduck.ui.ChessDuckTheme(dark, palette = p) {
                        val c = com.ivanherboch.chessduck.ui.LocalDuck.current
                        androidx.compose.foundation.layout.Column(
                            androidx.compose.ui.Modifier.width(240.dp).background(c.bg).padding(8.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                        ) {
                            androidx.compose.material3.Text(p.label, color = c.text, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(224.dp)) {
                                com.ivanherboch.chessduck.ui.ChessBoard(
                                    pos, androidx.compose.ui.Modifier.fillMaxSize(),
                                    arrows = listOf(
                                        com.ivanherboch.chessduck.ui.Arrow(com.ivanherboch.chessduck.chess.sqFromName("h5"), com.ivanherboch.chessduck.chess.sqFromName("f7"), c.green),
                                        com.ivanherboch.chessduck.ui.Arrow(com.ivanherboch.chessduck.chess.sqFromName("f6"), com.ivanherboch.chessduck.chess.sqFromName("h5"), c.red),
                                    ),
                                )
                            }
                            com.ivanherboch.chessduck.ui.FeedbackCard("Bien vu : le mat est imparable.", com.ivanherboch.chessduck.ui.Tone.GOOD)
                            com.ivanherboch.chessduck.ui.FeedbackCard("Ce coup laisse la dame en prise.", com.ivanherboch.chessduck.ui.Tone.BAD)
                            com.ivanherboch.chessduck.ui.DuckButton("Problème aléatoire", {}, androidx.compose.ui.Modifier.fillMaxWidth(), accent = c.gold, filled = true)
                            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                                com.ivanherboch.chessduck.ui.DuckButton("Indice", {}, androidx.compose.ui.Modifier.weight(1f), accent = c.green)
                                com.ivanherboch.chessduck.ui.DuckButton("Lecture", {}, androidx.compose.ui.Modifier.weight(1f), accent = c.blue, filled = true)
                            }
                            com.ivanherboch.chessduck.ui.DuckButton("Réessayer", {}, androidx.compose.ui.Modifier.fillMaxWidth(), accent = c.red, filled = true)
                        }
                    }
                }
            }
        }
        settle(600)
        shot("12_palettes")
    }

    @Test
    fun capturedBars() {
        // Pire cas : presque tout a été capturé des deux côtés ; la barre doit tout contenir sur 360 dp.
        val worst = com.ivanherboch.chessduck.chess.Position.fromFen("4k3/8/8/8/8/8/8/3QK3 w - - 0 1")
        val all = com.ivanherboch.chessduck.chess.Position.fromFen("4k3/8/8/8/8/8/8/4K3 w - - 0 1")
        val few = com.ivanherboch.chessduck.chess.Position.fromFen("r1b1kb1r/pppppp2/8/8/8/8/1PPP1PPP/RNBQKB2 w Qkq - 0 1")
        rule.activity.setContent {
            androidx.compose.foundation.layout.Column {
                for (dark in listOf(true, false)) com.ivanherboch.chessduck.ui.ChessDuckTheme(dark) {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(com.ivanherboch.chessduck.ui.LocalDuck.current.bg).padding(vertical = 8.dp)) {
                        for (p in listOf(few, worst, all)) com.ivanherboch.chessduck.ui.CapturedBar(p, androidx.compose.ui.Modifier.padding(vertical = 4.dp))
                    }
                }
                com.ivanherboch.chessduck.ui.ChessDuckTheme(dark = false, kid = true) {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(com.ivanherboch.chessduck.ui.LocalDuck.current.bg).padding(8.dp)) {
                        for (p in listOf(few, worst, all)) com.ivanherboch.chessduck.ui.CapturedBar(p, androidx.compose.ui.Modifier.padding(vertical = 4.dp), kid = true)
                    }
                }
            }
        }
        settle(800)
        shot("10_prises")
    }

    private fun desc(text: String) = rule.onNodeWithContentDescription(text, substring = true)
    private fun close() = rule.onAllNodesWithContentDescription("Fermer").onLast().performClick()

    /** Clavier de Notation : touches assez grandes pour le doigt (hauteurs 44 et 48 dp) sur un téléphone de 360 dp. */
    @Test
    fun notationKeysStayLargeEnough() {
        settle(2500)
        val density = rule.activity.resources.displayMetrics.density
        fun size(label: String) = rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot.let { it.width / density to it.height / density }
        for (key in listOf("colonne a", "rangée 8", "x, prise", "petit roque")) {
            val (w, h) = size(key)
            assertTrue("$key : hauteur $h dp", h >= 43.5f)
            assertTrue("$key : largeur $w dp", w >= 40f)
        }
        val (_, piece) = size("K, King, Roi")
        assertTrue("touche de pièce : $piece dp", piece >= 47.5f)
        val (_, erase) = size("Effacer")
        assertTrue("effacer : $erase dp", erase >= 47.5f)
    }

    /** Échiquier accessible : chaque case s'annonce, et un coup se joue par les seules actions TalkBack. */
    @Test
    fun boardCanBePlayedWithTalkBack() {
        settle(2500)
        rule.onNodeWithText("Jeu Libre").performClick(); settle(2000)
        rule.onNodeWithContentDescription("e2, Pion blanc").performSemanticsAction(SemanticsActions.OnClick); settle(300)
        rule.onNodeWithContentDescription("e4, case vide").assertExists().assertStateDescriptionContains("destination légale")
        rule.onNodeWithContentDescription("e4, case vide").performSemanticsAction(SemanticsActions.OnClick); settle(1500)
        rule.onNodeWithContentDescription("e4, Pion blanc").assertExists()
    }

    /**
     * Grande police : « Entraînements » devient une icône de l'en-tête, sans chevaucher le titre ChessDuck ni sortir de
     * l'écran ; aucune puce n'est posée entre la leçon et l'échiquier, la leçon conseillée est dans le tiroir.
     */
    @Test
    @Config(fontScale = 1.6f)
    fun academyBannerChipsDoNotOverlapWithLargeFont() {
        settle(2500)
        rule.onNodeWithText("Académie").performClick(); settle(1500)
        desc("Ouvrir le catalogue des leçons").performClick(); settle(1200)
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Opéra"); settle(1000)
        val opera = androidx.compose.ui.test.hasText("Morphy, Paris", substring = true)
        rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).fetchSemanticsNodes().indices.forEach { i ->
            runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[i].performScrollToNode(opera) }
        }; settle(600)
        rule.onAllNodes(opera)[0].performClick(); settle(1200)
        shot("2p_bandeau_grande_police")
        val trainings = rule.onNodeWithContentDescription("Entraînements", substring = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithText("ChessDuck").fetchSemanticsNode().boundsInRoot
        assertTrue("« Entraînements » dépasse de l'écran", trainings.right <= root.right + 0.5f)
        assertTrue("« Entraînements » chevauche le titre", !trainings.overlaps(title))
        // Plus aucun bandeau entre la leçon et l'échiquier : la leçon conseillée est proposée dans le tiroir.
        assertTrue(rule.onAllNodesWithText("Conseillée", substring = true).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithContentDescription("Entraînements", substring = true).performClick(); settle(1200)
        shot("2p_suggestions_grande_police")
        // On revient à la leçon conseillée (la leçon 1) : la leçon ouverte est mémorisée pour les tests suivants.
        rule.onNodeWithText("Ouvrir la leçon conseillée").performScrollTo().performClick(); settle(1200)
    }

    @Test
    fun screens() {
        settle(2500)
        shot("1_notation")
        desc("Mémo anglais").performClick(); settle(800); shot("1b_memo")
        close(); settle(800)
        rule.onNodeWithText("Académie").performClick(); settle(1500); shot("2_academie")
        // Guidé actif : la leçon 1 s'arrête avant le 5e demi-coup et demande « À vous : que jouez-vous ici ? ».
        repeat(4) { desc("Coup suivant").performClick(); settle(500) }
        rule.onNodeWithText(com.ivanherboch.chessduck.feature.ASK).assertExists(); shot("2d_a_vous")
        // Fin de la leçon : passerelle « Jouer la position » vers le Jeu Libre (partie d'entraînement).
        desc("Aller à la fin").performClick(); settle(800); shot("2e_fin_lecon")
        desc("Jouer cette position contre l'Ordinateur dans le Jeu Libre").performClick(); settle(2000)
        shot("2f_jouer_position")
        rule.onNodeWithText("Position de la leçon", substring = true).assertExists()
        rule.onNodeWithText("Académie").performClick(); settle(1200)
        rule.onNodeWithText("Défi").performClick(); settle(1500); shot("2b_defi")
        desc("Ouvrir le catalogue des leçons").performClick(); settle(1200); shot("2c_catalogue_lecons")
        close(); settle(800)
        // Étape 2 : entraînements (réflexes, finales jouables), variantes, QCM et partie modèle.
        rule.onNodeWithText("Entraînements", substring = true).performClick(); settle(1000); shot("2g_entrainements")
        rule.onNodeWithText("Quelle est la menace ?").performClick(); settle(1000)
        rule.waitUntil(20_000) { rule.onAllNodesWithText("Question 1 sur", substring = true).fetchSemanticsNodes().isNotEmpty() }
        settle(500); shot("2h_menace")
        desc("Revenir à la leçon").performClick(); settle(600)
        rule.onNodeWithText("Entraînements", substring = true).performClick(); settle(1000)
        rule.onNodeWithText("Puis-je prendre ?").performClick(); settle(1000)
        rule.waitUntil(20_000) { rule.onAllNodesWithText("Oui, je prends").fetchSemanticsNodes().isNotEmpty() }
        settle(500); shot("2i_prise")
        rule.onNodeWithText("Oui, je prends").performClick(); settle(600); shot("2j_prise_reponse")
        desc("Revenir à la leçon").performClick(); settle(600)
        rule.onNodeWithText("Entraînements", substring = true).performClick(); settle(1000)
        rule.onNodeWithText("Finales contre l'Ordinateur", substring = true).performScrollTo().performClick(); settle(500)
        rule.onNodeWithText("Gagnez : Roi et tour contre roi").performScrollTo().performClick(); settle(1500); shot("2k_finale")
        desc("Retourner l'échiquier").assertExists() // le retournement reste offert pendant une séance
        desc("Revenir à la leçon").performClick(); settle(600)
        rule.onNodeWithText("Guidé").performClick(); settle(800)
        desc("Leçon suivante").performClick(); settle(1200) // la partie italienne : variantes et QCM
        // Position de départ : résumé seul ; la fiche complète (idées, pièges) s'ouvre dans un tiroir.
        rule.onNodeWithText("Fiche complète", substring = true).performClick(); settle(800); shot("2u_fiche")
        rule.onNodeWithText("Les pièges").performScrollTo().assertExists()
        close(); settle(600)
        rule.onNodeWithText("Variantes").performClick(); settle(500)
        rule.onNodeWithText("Si 3… Nf6").performClick(); settle(1000); shot("2l_variante")
        desc("Aller à la fin").performClick(); settle(800)
        rule.onNodeWithText("Variante 1").performClick(); settle(500)
        rule.onNodeWithText("Ligne principale").performClick(); settle(600)
        desc("Aller à la fin").performClick(); settle(800)
        rule.onNodeWithText("Quel est le plan ?").performClick(); settle(800); shot("2m_qcm")
        rule.onNodeWithText("Jouer c3 puis d4 pour construire un centre de deux pions").performClick(); settle(600); shot("2n_qcm_reponse")
        rule.onNodeWithText("Revoir la leçon").performClick(); settle(600)
        desc("Ouvrir le catalogue des leçons").performClick(); settle(1200)
        rule.onNodeWithText("Filtres").performClick(); settle(500)
        rule.onNodeWithText("Parties modèles 0/8").performScrollTo().performClick(); settle(1000)
        rule.onNodeWithText("La partie de l'Opéra : Morphy, Paris 1858").performScrollTo().performClick(); settle(1200)
        rule.onNodeWithText("Devinez le coup").performClick(); settle(1500); shot("2o_devinez")
        // Partie modèle : « Jouer cette position » à chaque coup, avec le choix du camp.
        rule.onNodeWithText("Jouer cette position").performClick(); settle(800)
        rule.onNodeWithText("Jouer avec les Noirs").assertExists(); shot("2v_choix_camp")
        close(); settle(600)
        rule.onNodeWithText("Guidé").performClick(); settle(800)
        rule.onNodeWithText("Problèmes").performClick(); settle(2500); shot("3_problemes")
        desc("Catalogue des problèmes").performClick(); settle(2000); shot("3b_catalogue")
        close(); settle(800)
        rule.onNodeWithText("Jeu Libre").performClick(); settle(2000); shot("4_jeu")
        // Retour Android : on remonte les onglets visités (Problèmes, Académie, puis Notation).
        val back = { rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle(1200) }
        back(); desc("Catalogue des problèmes").assertExists()
        back(); desc("Ouvrir le catalogue des leçons").assertExists()
        back(); desc("Historique des parties").assertExists() // Jeu Libre, ouvert depuis l'Académie
        back(); desc("Mémo anglais").assertExists()
        rule.onNodeWithText("Jeu Libre").performClick(); settle(1500)
        rule.onNodeWithContentDescription("Paramètres").performClick(); settle(800); shot("5_parametres")
        // Le mode enfant est le premier réglage du tiroir.
        rule.onNodeWithText("Activer le mode enfant").performScrollTo(); settle(500); shot("5a_parametres_haut")
        // Matières d'échiquier : sélecteur visible et applicable, puis retour au bois classique.
        rule.onNodeWithText("Ardoise").performScrollTo().performClick(); settle(800); shot("5d_matieres")
        rule.onNodeWithText("Bois classique").performClick(); settle(500)
        rule.onNodeWithText("Vibrations").performScrollTo().performClick(); settle(500)
        rule.onNodeWithText("Bruit des pièces").performScrollTo().performClick(); settle(500)
        rule.onNodeWithText("Jour").performScrollTo().performClick(); settle(800)
        close(); settle(800); shot("4c_jeu_jour")
        // Mode enfant : écran épuré à deux joueurs, puis retour au Jeu Libre.
        rule.onNodeWithContentDescription("Paramètres").performClick(); settle(800); shot("5b_parametres_jour")
        // Section « Couleurs de l'application » (sous les pseudos) : 5 palettes avec aperçu, puis les couleurs de l'échiquier.
        rule.onNodeWithText("Bleu-Orange").performScrollTo(); settle(600); shot("5c_palettes_jour")
        rule.onNodeWithText("Réinitialiser mon ELO").performScrollTo(); settle(600); shot("5e_parametres_bas")
        rule.onNodeWithText("Activer le mode enfant").performScrollTo().performClick(); settle(1500); shot("9_enfant")
        // Leçons animées du mode enfant
        desc("Cavalier :").performClick(); settle(1400); shot("9b_lecon_cavalier")
        rule.onNodeWithText("Revenir à la partie").performClick(); settle(800)
        desc("Pion :").performClick(); settle(900); shot("9c_lecon_pion")
        // Retour Android dans une leçon : retour à la partie du mode enfant.
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle(900)
        rule.onNodeWithText("👆 Touche une pièce pour voir comment elle bouge !").assertExists()
        desc("Quitter le mode enfant").performClick(); settle(800); shot("9d_quitter")
        // « Non » referme la boîte et on reste en mode enfant ; « Oui » quitte.
        rule.onNodeWithText("Non, on continue à jouer").performClick(); settle(600)
        desc("Quitter le mode enfant").performClick(); settle(600)
        rule.onNodeWithText("Oui, quitter").performClick(); settle(1500)
        // Régression : le mode enfant doit pouvoir être relancé plusieurs fois de suite.
        repeat(3) {
            rule.onNodeWithContentDescription("Paramètres").performClick(); settle(800)
            rule.onNodeWithText("Activer le mode enfant").performScrollTo().performClick(); settle(1200)
            rule.onNodeWithText("👆 Touche une pièce pour voir comment elle bouge !").assertExists()
            desc("Quitter le mode enfant").performClick(); settle(600)
            rule.onNodeWithText("Oui, quitter").performClick(); settle(1200)
            rule.onNodeWithText("Jeu Libre").assertExists()
        }
        rule.onNodeWithText("Problèmes").performClick(); settle(1500)
        desc("Problèmes récents").assertExists()
        // Recherche instantanée par identifiant Lichess.
        desc("Catalogue des problèmes").performClick(); settle(1500)
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("0d"); settle(1500); shot("3c_recherche")
        rule.onNodeWithText("#0D1Z6").performClick(); settle(2500)
        desc("Problèmes récents").performClick(); settle(1000); shot("3d_recents")
        close(); settle(800)
        desc("Exporter en image").performClick(); settle(1000); shot("3e_exporter")
        close(); settle(800)
        // Thème choisi : chaque tirage aléatoire affiche ce thème (visible : le joueur l'a choisi).
        desc("Catalogue des problèmes").performClick(); settle(1500)
        rule.onNodeWithText("Mats").performClick(); settle(800) // la ligne déplie la famille
        rule.onNodeWithText("Mat en 1").performScrollTo().performClick(); settle(2500)
        repeat(8) {
            rule.onNodeWithText("Type de problème : Mat en 1").assertExists()
            desc("Problème aléatoire").performClick(); settle(2000)
        }
        shot("3f_theme_choisi")
        // Famille « Mats » : thème masqué (anti-spoiler) jusqu'à ce qu'on le révèle ; chaque tirage est un mat.
        desc("Catalogue des problèmes").performClick(); settle(1500); shot("3g_familles")
        // La famille du thème en cours est dépliée d'office : « Toute la famille » la choisit en bloc.
        rule.onNodeWithText("Toute la famille").performScrollTo().performClick(); settle(2500)
        rule.onNodeWithText("Thème masqué (mode libre)").assertExists()
        shot("3h_theme_masque")
        val mates = com.ivanherboch.chessduck.content.PuzzleThemes.all.filter { it.group == com.ivanherboch.chessduck.content.PuzzleThemes.G_MATE }.map { it.name }
        val seen = HashSet<String>()
        repeat(16) {
            // Un problème déjà révélé plus tôt (tirage au hasard) affiche directement son thème : rien à révéler.
            rule.waitUntil(20_000) {
                rule.onAllNodesWithText("Révéler le thème").fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Type de problème : ", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            if (rule.onAllNodesWithText("Révéler le thème").fetchSemanticsNodes().isNotEmpty()) {
                rule.onNodeWithText("Révéler le thème").performClick(); settle(600)
            }
            rule.waitUntil(20_000) { rule.onAllNodesWithText("Type de problème : ", substring = true).fetchSemanticsNodes().isNotEmpty() }
            val shown = mates.filter { name -> rule.onAllNodesWithText("Type de problème : $name").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, shown.size)
            seen.addAll(shown)
            desc("Problème aléatoire").performClick(); settle(2000)
        }
        org.junit.Assert.assertTrue("plusieurs catégories de mats attendues : $seen", seen.size >= 2)
        // Historique et revue : résumé de partie, puis retour Android vers la partie en cours.
        seedGame()
        rule.onNodeWithText("Jeu Libre").performClick(); settle(1200)
        rule.onNodeWithContentDescription("Historique des parties").performClick(); settle(1200); shot("4d_historique")
        rule.onAllNodesWithText("Revue commentée").onFirst().performClick(); settle(400); shot("4e_revue")
        rule.onNodeWithText("Lecture").assertExists() // ouverte en pause
        rule.onNodeWithContentDescription("Coup suivant").performClick(); settle(600); shot("4f_revue_coup")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle(1000)
        rule.onNodeWithContentDescription("Annuler mon dernier coup", substring = false).assertExists()
    }

    private companion object {
        /** Ordre de priorité de référence (copie figée de PuzzleThemes) pour vérifier la non-régression. */
        val REFERENCE_PRIORITY = listOf(
            "smotheredMate", "anastasiaMate", "arabianMate", "bodenMate", "doubleBishopMate", "dovetailMate", "hookMate", "backRankMate",
            "mateIn1", "mateIn2", "mateIn3", "mateIn4", "mateIn5",
            "underPromotion", "enPassant", "castling", "doubleCheck", "discoveredCheck", "zugzwang", "interference", "xRayAttack",
            "trappedPiece", "capturingDefender", "intermezzo", "clearance", "attraction", "deflection", "skewer", "pin", "fork",
            "discoveredAttack", "hangingPiece", "quietMove", "defensiveMove", "sacrifice", "promotion", "advancedPawn",
            "attackingF2F7", "exposedKing", "kingsideAttack", "queensideAttack",
            "pawnEndgame", "rookEndgame", "bishopEndgame", "knightEndgame", "queenEndgame", "queenRookEndgame",
            "opening", "endgame", "middlegame",
        )
    }
}

/** Durée pendant laquelle l'interface doit rester identique pour être considérée comme stable. */
private const val STABLE_WINDOW_MS = 250L

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertStateDescriptionContains(text: String) =
    this.assert(androidx.compose.ui.test.SemanticsMatcher("état contient « $text »") { node ->
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.StateDescription)?.contains(text) == true
    })
