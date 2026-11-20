package com.ivanherboch.chessduck.vm

import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.data.AcademyAssessment
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.data.LessonProgress
import com.ivanherboch.chessduck.data.GameRepository
import com.ivanherboch.chessduck.data.LessonProgressRepository
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.data.PuzzleHit
import com.ivanherboch.chessduck.data.PuzzleRepository
import com.ivanherboch.chessduck.data.Settings
import com.ivanherboch.chessduck.data.SettingsRepository
import com.ivanherboch.chessduck.data.Tally
import com.ivanherboch.chessduck.data.ThemeCountRow
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.engine.PvLine
import com.ivanherboch.chessduck.engine.SearchResult
import com.ivanherboch.chessduck.ui.Narrator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/** Réglages en mémoire. */
class FakeSettings(initial: Settings = Settings()) : SettingsRepository {
    val flow = MutableStateFlow(initial)
    override val settings: Flow<Settings> = flow
    override suspend fun setNotationBest(v: Int) = flow.update { if (v > it.notationBest) it.copy(notationBest = v) else it }
    override suspend fun setNotationLevel(v: Int) = flow.update { it.copy(notationLevel = v) }
    override suspend fun setLesson(v: Int) = flow.update { it.copy(lessonId = v) }
    override suspend fun setAcademyLevel(v: Int) = flow.update { it.copy(academyLevel = v) }
    override suspend fun setPlacementDone(v: Boolean) = flow.update { it.copy(placementDone = v) }
    override suspend fun setElo(v: Int) = flow.update { it.copy(elo = v) }
    override suspend fun setGameSettings(twoPlayers: Boolean, color: Int, aiElo: Int) =
        flow.update { it.copy(twoPlayers = twoPlayers, colorChoice = color, aiElo = aiElo) }
    override suspend fun setPuzzleFilter(band: Int, theme: String) = flow.update { it.copy(puzzleBand = band, puzzleTheme = theme) }
    override suspend fun pushRecentPuzzle(id: String) = flow.update {
        val recent = (listOf(id) + it.recentPuzzles.filter { r -> r != id }).take(6)
        it.copy(recentPuzzles = recent, puzzleId = id, knownThemePuzzles = it.knownThemePuzzles.filter { k -> k in recent }.toSet())
    }
    override suspend fun markThemeKnown(id: String) = flow.update { it.copy(knownThemePuzzles = it.knownThemePuzzles + id) }
    override suspend fun setThemeMode(v: Int) = flow.update { it.copy(themeMode = v) }
    override suspend fun setPalette(v: Int) = flow.update { it.copy(palette = v) }
    override suspend fun setBoardTheme(v: Int) = flow.update { it.copy(boardTheme = v) }
    override suspend fun setCoords(v: Boolean) = flow.update { it.copy(coords = v) }
    override suspend fun setConfirmActions(v: Boolean) = flow.update { it.copy(confirmActions = v) }
    override suspend fun setHaptics(v: Boolean) = flow.update { it.copy(haptics = v) }
    override suspend fun setSound(v: Boolean) = flow.update { it.copy(sound = v) }
    override suspend fun setNarration(v: Boolean) = flow.update { it.copy(narration = v) }
    override suspend fun setKidNarration(v: Boolean) = flow.update { it.copy(kidNarration = v) }
    override suspend fun setKidMode(v: Boolean) = flow.update { it.copy(kidMode = v) }
    override suspend fun setPseudo(v: String) = flow.update { it.copy(pseudo = v) }
    override suspend fun setPseudo2(v: String) = flow.update { it.copy(pseudo2 = v) }
    override suspend fun setShareStyle(format: String, light: Boolean) = flow.update { it.copy(shareFormat = format, shareLight = light) }
}

/** Catalogue de problèmes en mémoire (mêmes règles de filtre que la base). */
class FakePuzzles(private val puzzles: List<Puzzle>) : PuzzleRepository {
    val solved = MutableStateFlow<Set<String>>(emptySet())
    override suspend fun byId(id: String) = puzzles.firstOrNull { it.id == id }
    override suspend fun byIds(ids: List<String>) = puzzles.filter { it.id in ids }
    override suspend fun byIdIgnoreCase(id: String) = puzzles.firstOrNull { it.id.equals(id, ignoreCase = true) }
    override suspend fun slice(start: Int, n: Int) = emptyList<Puzzle>()
    override suspend fun sliceThemes(start: Int, themes: List<String>, n: Int) = emptyList<Puzzle>()
    override suspend fun sliceCastling(start: Int, n: Int) = emptyList<Puzzle>()
    override suspend fun markSolved(id: String, at: Long) = solved.update { it + id }
    override fun solvedIds(): Flow<List<String>> = solved.map { it.toList() }
    override suspend fun syncCatalogue() = false
    override suspend fun randomId(themes: List<String>?, range: IntRange, exclude: String, random: Random): String? {
        val pool = puzzles.filter { (themes == null || it.primaryTheme in themes) && it.rating in range && it.id != exclude }
        return (pool.filter { it.id !in solved.value }.ifEmpty { pool }).randomOrNull(random)?.id
    }
    override suspend fun themeCounts(range: IntRange) = puzzles.filter { it.rating in range }.groupBy { it.primaryTheme }
        .map { (t, l) -> ThemeCountRow(t, l.size, l.count { it.id in solved.value }) }
    override suspend fun searchIds(query: String, limit: Int): Pair<List<PuzzleHit>, Int> {
        val hits = puzzles.filter { it.id.contains(query, ignoreCase = true) }
        return hits.take(limit).map { PuzzleHit(it.id, it.rating, it.primaryTheme) } to hits.size
    }
}

class FakeGames : GameRepository {
    val stored = MutableStateFlow<List<GameRecord>>(emptyList())
    val analyses = HashMap<Long, String>()
    override fun games(): Flow<List<GameRecord>> = stored
    override fun tally(): Flow<List<Tally>> = stored.map { l -> l.groupBy { it.outcome }.map { (o, g) -> Tally(o, g.size) } }
    override suspend fun insertGame(g: GameRecord): Long {
        val id = (stored.value.maxOfOrNull { it.id } ?: 0) + 1
        stored.update { it + g.copy(id = id) }
        return id
    }
    override suspend fun saveAnalysis(id: Long, analysis: String) { analyses[id] = analysis }
    override suspend fun deleteGame(id: Long) = stored.update { l -> l.filter { it.id != id } }
}

class FakeLessons : LessonProgressRepository {
    val rows = MutableStateFlow<Map<Int, LessonProgress>>(emptyMap())
    val tests = MutableStateFlow<List<AcademyAssessment>>(emptyList())
    override fun masteredLessons(): Flow<List<Int>> = rows.map { m -> m.values.filter { it.mastered }.map { it.lessonId } }
    override fun progress(): Flow<List<LessonProgress>> = rows.map { it.values.toList() }
    override suspend fun progress(id: Int): LessonProgress? = rows.value[id]
    override suspend fun save(p: LessonProgress) = rows.update { it + (p.lessonId to p) }
    override suspend fun saveAssessment(a: AcademyAssessment): Long { tests.update { it + a }; return tests.value.size.toLong() }
    override fun assessments(): Flow<List<AcademyAssessment>> = tests
}

/** Faux moteur : joue toujours le premier coup légal, évaluation nulle ; compte les recherches. */
class FakeEngine : ChessEngine {
    var searches = 0
    /** Réponses imposées, par position (les 4 premiers champs de la FEN) ; null : moteur indisponible. */
    val scripted = HashMap<String, SearchResult?>()
    override suspend fun search(fen: String, movetimeMs: Int, multiPv: Int, elo: Int?, depth: Int?): SearchResult {
        searches++
        val key = fen.split(' ').take(4).joinToString(" ")
        if (scripted.containsKey(key)) return scripted[key] ?: SearchResult(null, emptyList())
        val best = Position.fromFen(fen).legalMoves.firstOrNull() ?: return SearchResult(null, emptyList())
        return SearchResult(best, listOf(PvLine(best, 0)))
    }
}

/** Problème réel de la base Lichess (#0000D) : les Blancs jouent d'abord, le joueur a les Noirs. */
val PUZZLE_0000D = Puzzle(
    "0000D", "5rk1/1p3ppp/pq3b2/8/8/1P1Q1N2/P4PPP/3R2K1 w - - 2 27", "d3d6 f8d8 d6d8 f6d8",
    1433, 95, 1000, " advantage endgame short ", PuzzleThemes.primary(" advantage endgame short ").key,
)

/** Narrateur en mémoire : note les phrases lues ; une phrase dure jusqu'à [finish]. */
class FakeNarrator : Narrator {
    val spoken = ArrayList<String>()
    override val available = MutableStateFlow<Boolean?>(true)
    override val speaking = MutableStateFlow(false)
    override fun prepare() = Unit
    var paused: String? = null
    override fun speak(text: String) { spoken.add(text); speaking.value = true; paused = null }
    override fun stop() { speaking.value = false; paused = null }
    override fun pause() { if (speaking.value) { paused = spoken.last(); speaking.value = false } }
    override fun resume(): Boolean = paused?.let { speak(it); true } ?: false
    fun finish() { speaking.value = false }
}
