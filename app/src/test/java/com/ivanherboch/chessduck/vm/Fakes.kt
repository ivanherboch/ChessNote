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
/** Réglages en mémoire. Par défaut, ceux d'un élève déjà engagé (leçon 1, palier 2) : les tests visent la logique. */
class FakeSettings(initial: Settings = Settings(lessonId = 1, academyLevel = 2)) : SettingsRepository {
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
    override suspend fun setPuzzleElo(v: Int) = flow.update { it.copy(puzzleElo = v) }
    override suspend fun setPuzzleSecondary(v: Boolean) = flow.update { it.copy(puzzleSecondary = v) }
    override suspend fun setPuzzleLength(v: Int) = flow.update { it.copy(puzzleLength = v) }
    override suspend fun pushRecentPuzzle(id: String) = flow.update {
        val recent = (listOf(id) + it.recentPuzzles.filter { r -> r != id }).take(com.ivanherboch.chessduck.data.RECENT_PUZZLES + 1)
        it.copy(recentPuzzles = recent, puzzleId = id, knownThemePuzzles = it.knownThemePuzzles.filter { k -> k in recent }.toSet())
    }
    override suspend fun markThemeKnown(id: String) = flow.update { it.copy(knownThemePuzzles = it.knownThemePuzzles + id) }
    override suspend fun markVariantDone(key: String) = flow.update { it.copy(variantsDone = it.variantsDone + key) }
    override suspend fun markDailyStep(step: String, today: Long, required: Set<String>) = flow.update { it.copy(daily = it.daily.mark(step, today, required)) }
    override suspend fun setSafetyNet(on: Boolean) = flow.update { it.copy(safetyNet = on) }
    override suspend fun addNetEvent(topic: String, at: Long) = flow.update {
        it.copy(netEvents = (it.netEvents + com.ivanherboch.chessduck.data.NetEvent(topic, at)).takeLast(com.ivanherboch.chessduck.data.NET_EVENTS_MAX))
    }
    override suspend fun markUnderstood(lessonId: Int) = flow.update { it.copy(understoodIds = it.understoodIds + lessonId) }
    override suspend fun markLessonWorked(lessonId: Int, at: Long) = flow.update { it.copy(lessonWorkedAt = it.lessonWorkedAt + (lessonId to at)) }
    override suspend fun toggleRepertoire(lessonId: Int) = flow.update {
        it.copy(repertoireIds = if (lessonId in it.repertoireIds) it.repertoireIds - lessonId else it.repertoireIds + lessonId)
    }
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
    /** Suivi détaillé (v5) : même règle que la base ([com.ivanherboch.chessduck.data.PuzzleProgress.after]). */
    val progress = MutableStateFlow<Map<String, com.ivanherboch.chessduck.data.PuzzleProgress>>(emptyMap())
    override suspend fun recordAttempt(id: String, outcome: Int, at: Long) {
        val old = progress.value[id] ?: com.ivanherboch.chessduck.data.PuzzleProgress(id, 0)
        val next = com.ivanherboch.chessduck.data.PuzzleProgress.after(old, outcome, at)
        progress.update { it + (id to next) }
        if (next.solvedAt > 0) solved.update { it + id }
    }
    override suspend fun setBookmark(id: String, on: Boolean) = progress.update {
        it + (id to (it[id] ?: com.ivanherboch.chessduck.data.PuzzleProgress(id, 0)).copy(bookmarked = on))
    }
    override fun bookmarked(id: String): Flow<Boolean> = progress.map { it[id]?.bookmarked == true }
    override fun bookmarks(): Flow<List<Puzzle>> = progress.map { m -> m.values.filter { it.bookmarked }.mapNotNull { p -> puzzles.firstOrNull { it.id == p.puzzleId } } }
    override fun toRedo(): Flow<List<Pair<Puzzle, Long>>> = progress.map { m ->
        m.values.filter { it.nextReviewAt > 0 }.sortedBy { it.nextReviewAt }.mapNotNull { p -> puzzles.firstOrNull { it.id == p.puzzleId }?.let { it to p.nextReviewAt } }
    }
    override fun attempts(): Flow<List<com.ivanherboch.chessduck.data.AttemptRow>> = progress.map { m ->
        m.values.filter { it.status > 0 }.mapNotNull { p -> puzzles.firstOrNull { it.id == p.puzzleId }?.let { com.ivanherboch.chessduck.data.AttemptRow(p.status, p.attempts, p.lastTriedAt, it.primaryTheme) } }
    }
    override fun solvedIds(): Flow<List<String>> = solved.map { it.toList() }
    override fun solvedCount(): Flow<Int> = solved.map { it.size }
    override fun solvedAmong(ids: List<String>): Flow<List<String>> = solved.map { s -> ids.filter { it in s } }
    override suspend fun syncCatalogue() = false
    private fun tags(p: Puzzle) = p.themes.trim().split(' ').toSet()
    override suspend fun randomId(themes: List<String>?, range: IntRange, exclude: String, random: Random, secondary: Boolean, length: Int): String? {
        val (la, lb) = com.ivanherboch.chessduck.content.PuzzleThemes.lengthPlies(length)
        fun lengthOk(p: Puzzle) = la == 0 || p.moves.trim().split(' ').size in la..lb
        val pool = puzzles.filter {
            (themes == null || it.primaryTheme in themes || (secondary && tags(it).any { t -> t in themes })) && it.rating in range && it.id != exclude && lengthOk(it)
        }
        return (pool.filter { it.id !in solved.value }.ifEmpty { pool }).randomOrNull(random)?.id
    }
    override suspend fun lengthCounts(range: IntRange, length: Int, secondary: Boolean, groups: Map<String, List<String>>): Map<String, ThemeCountRow> {
        val (la, lb) = com.ivanherboch.chessduck.content.PuzzleThemes.lengthPlies(length)
        val metas = puzzles.filter { p -> p.rating in range && p.moves.trim().split(' ').size in la..lb }
            .map { com.ivanherboch.chessduck.data.PuzzleMeta(it.id, it.rating, it.themes, it.primaryTheme) }
        return com.ivanherboch.chessduck.data.countByLength(metas, solved.value, secondary, groups)
    }
    override suspend fun linkedCount(themes: List<String>, range: IntRange): ThemeCountRow {
        val l = puzzles.filter { it.rating in range && tags(it).any { t -> t in themes } }
        return ThemeCountRow("", l.size, l.count { it.id in solved.value })
    }
    override suspend fun themeCounts(range: IntRange, secondary: Boolean): List<ThemeCountRow> {
        val inRange = puzzles.filter { it.rating in range }
        if (!secondary) return inRange.groupBy { it.primaryTheme }.map { (t, l) -> ThemeCountRow(t, l.size, l.count { it.id in solved.value }) }
        val keys = com.ivanherboch.chessduck.content.PuzzleThemes.all.map { it.key }.toSet()
        return inRange.flatMap { p -> tags(p).filter { it in keys }.map { it to p } }.groupBy({ it.first }, { it.second })
            .map { (t, l) -> ThemeCountRow(t, l.size, l.count { it.id in solved.value }) }
    }
    /** Nombre de recherches d'identifiant envoyées à la base (recherche différée du catalogue). */
    var searches = 0
    override suspend fun searchIds(query: String, limit: Int): Pair<List<PuzzleHit>, Int> {
        searches++
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
