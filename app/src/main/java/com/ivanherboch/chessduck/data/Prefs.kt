package com.ivanherboch.chessduck.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.migrations.SharedPreferencesView
import androidx.datastore.migrations.SharedPreferencesMigration
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Ancien fichier SharedPreferences des réglages d'export (format, fond), migré une fois dans DataStore. */
private const val LEGACY_SHARE_STYLE = "share_style"

private val Context.store by preferencesDataStore(
    name = "chessduck",
    produceMigrations = { ctx ->
        listOf(
            SharedPreferencesMigration(ctx, LEGACY_SHARE_STYLE, setOf("format", "light")) { old: SharedPreferencesView, prefs: Preferences ->
                val m = prefs.toMutablePreferences()
                old.getString("format")?.let { m[Prefs.SHARE_FORMAT] = it }
                if (old.contains("light")) m[Prefs.SHARE_LIGHT] = old.getBoolean("light", false)
                m.toPreferences()
            },
        )
    },
)

data class Settings(
    /** 0 = suivre le système, 1 = Jour, 2 = Nuit (déduit de la clé historique « dark » tant que l'utilisateur n'a rien choisi). */
    val themeMode: Int = 2,
    /** Palette de couleurs choisie (indice de DuckPalette ; 0 = Ardoise & Or). */
    val palette: Int = 0,
    /** Index de la matière d'échiquier dans BoardTheme. */
    val boardTheme: Int = 0,
    /** Coordonnées a–h / 1–8 affichées sur l'échiquier. */
    val coords: Boolean = true,
    /** Confirmation avant d'abandonner ou de recommencer une partie en cours. */
    val confirmActions: Boolean = true,
    /** Meilleure série de bonnes réponses en Notation. */
    val notationBest: Int = 0,
    val haptics: Boolean = true,
    val sound: Boolean = true,
    /** Lecture à voix haute des commentaires de l'Académie (mode Guidé). */
    val narration: Boolean = false,
    /** Lecture à voix haute des fiches de pièces du mode enfant (réglage parental). */
    val kidNarration: Boolean = false,
    val kidMode: Boolean = false,
    /** Identifiants des derniers problèmes joués, du plus récent au plus ancien. */
    val recentPuzzles: List<String> = emptyList(),
    /** Problèmes récents dont le thème était visible quand ils ont été joués (filtre précis ou thème révélé). */
    val knownThemePuzzles: Set<String> = emptySet(),
    val pseudo: String = "Joueur",
    /** Second joueur humain du mode 2 joueurs. */
    val pseudo2: String = "Joueur 2",
    val elo: Int = 1200,
    val twoPlayers: Boolean = false,
    /** 0 = Blancs, 1 = Aléatoire, 2 = Noirs. */
    val colorChoice: Int = 0,
    val aiElo: Int = 1200,
    /** 0 = Tous, 1 = Débutant, 2 = Intermédiaire, 3 = Avancé, 4 = Expert. */
    val puzzleBand: Int = 0,
    val puzzleTheme: String = "",
    val puzzleId: String = "",
    val notationLevel: Int = 0,
    val lessonId: Int = 1,
    /** Format des images exportées : nom de ShareFormat (« PORTRAIT » ou « SQUARE »). */
    val shareFormat: String = "PORTRAIT",
    /** Images exportées sur fond clair (sinon sombre). */
    val shareLight: Boolean = false,
)

/** Préférences persistées via Jetpack DataStore Preferences. */
class Prefs(private val context: Context) : SettingsRepository {
    companion object {
        internal val SHARE_FORMAT = stringPreferencesKey("share_format")
        internal val SHARE_LIGHT = booleanPreferencesKey("share_light")
    }

    private object K {
        val dark = booleanPreferencesKey("dark")
        val themeMode = intPreferencesKey("theme_mode")
        val palette = intPreferencesKey("palette")
        val boardTheme = intPreferencesKey("board_theme")
        val coords = booleanPreferencesKey("show_coords")
        val confirm = booleanPreferencesKey("confirm_actions")
        val notationBest = intPreferencesKey("notation_best_streak")
        val haptics = booleanPreferencesKey("haptics")
        val sound = booleanPreferencesKey("sound")
        val narration = booleanPreferencesKey("narration")
        val kidNarration = booleanPreferencesKey("kid_narration")
        val kid = booleanPreferencesKey("kid_mode")
        val recent = stringPreferencesKey("recent_puzzles")
        val knownThemes = stringPreferencesKey("recent_known_themes")
        val pseudo = stringPreferencesKey("pseudo")
        val pseudo2 = stringPreferencesKey("pseudo2")
        val elo = intPreferencesKey("elo")
        val twoPlayers = booleanPreferencesKey("two_players")
        val color = intPreferencesKey("color")
        val aiElo = intPreferencesKey("ai_elo")
        val band = intPreferencesKey("puzzle_band")
        val theme = stringPreferencesKey("puzzle_theme")
        val puzzleId = stringPreferencesKey("puzzle_id")
        val level = intPreferencesKey("notation_level")
        val lesson = intPreferencesKey("lesson_id")
    }

    override val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            themeMode = p[K.themeMode] ?: if (p[K.dark] ?: true) 2 else 1,
            palette = p[K.palette] ?: 0,
            boardTheme = p[K.boardTheme] ?: 0,
            coords = p[K.coords] ?: true,
            confirmActions = p[K.confirm] ?: true,
            notationBest = p[K.notationBest] ?: 0,
            haptics = p[K.haptics] ?: true,
            sound = p[K.sound] ?: true,
            narration = p[K.narration] ?: false,
            kidNarration = p[K.kidNarration] ?: false,
            kidMode = p[K.kid] ?: false,
            recentPuzzles = (p[K.recent] ?: "").split(',').filter { it.isNotBlank() },
            knownThemePuzzles = (p[K.knownThemes] ?: "").split(',').filter { it.isNotBlank() }.toSet(),
            pseudo = p[K.pseudo] ?: "Joueur",
            pseudo2 = p[K.pseudo2] ?: "Joueur 2",
            elo = p[K.elo] ?: 1200,
            twoPlayers = p[K.twoPlayers] ?: false,
            colorChoice = p[K.color] ?: 0,
            aiElo = p[K.aiElo] ?: 1200,
            puzzleBand = p[K.band] ?: 0,
            puzzleTheme = p[K.theme] ?: "",
            puzzleId = p[K.puzzleId] ?: "",
            notationLevel = p[K.level] ?: 0,
            lessonId = p[K.lesson] ?: 1,
            shareFormat = p[SHARE_FORMAT] ?: "PORTRAIT",
            shareLight = p[SHARE_LIGHT] ?: false,
        )
    }

    /** 0 = système, 1 = Jour, 2 = Nuit ; la clé historique « dark » reste cohérente. */
    override suspend fun setThemeMode(v: Int) { context.store.edit {
        it[K.themeMode] = v
        if (v != 0) it[K.dark] = v == 2
    } }
    override suspend fun setPalette(v: Int) { context.store.edit { it[K.palette] = v } }
    override suspend fun setBoardTheme(v: Int) { context.store.edit { it[K.boardTheme] = v } }
    override suspend fun setCoords(v: Boolean) { context.store.edit { it[K.coords] = v } }
    override suspend fun setConfirmActions(v: Boolean) { context.store.edit { it[K.confirm] = v } }
    override suspend fun setNotationBest(v: Int) { context.store.edit { if (v > (it[K.notationBest] ?: 0)) it[K.notationBest] = v } }
    override suspend fun setHaptics(v: Boolean) { context.store.edit { it[K.haptics] = v } }
    override suspend fun setSound(v: Boolean) { context.store.edit { it[K.sound] = v } }
    override suspend fun setNarration(v: Boolean) { context.store.edit { it[K.narration] = v } }
    override suspend fun setKidNarration(v: Boolean) { context.store.edit { it[K.kidNarration] = v } }
    override suspend fun setKidMode(v: Boolean) { context.store.edit { it[K.kid] = v } }

    /** Place [id] en tête de l'historique des problèmes joués (6 conservés : le courant + 5 précédents). */
    override suspend fun pushRecentPuzzle(id: String) { context.store.edit { p ->
        val old = (p[K.recent] ?: "").split(',').filter { it.isNotBlank() && it != id }
        val recent = (listOf(id) + old).take(6)
        p[K.recent] = recent.joinToString(",")
        p[K.puzzleId] = id
        // Les thèmes « connus » ne concernent que les problèmes encore dans l'historique.
        p[K.knownThemes] = (p[K.knownThemes] ?: "").split(',').filter { it.isNotBlank() && it in recent }.joinToString(",")
    } }

    override suspend fun markThemeKnown(id: String) { context.store.edit { p ->
        val known = (p[K.knownThemes] ?: "").split(',').filter { it.isNotBlank() }.toMutableSet()
        if (known.add(id)) p[K.knownThemes] = known.joinToString(",")
    } }
    override suspend fun setPseudo(v: String) { context.store.edit { it[K.pseudo] = v } }
    override suspend fun setPseudo2(v: String) { context.store.edit { it[K.pseudo2] = v } }
    override suspend fun setElo(v: Int) { context.store.edit { it[K.elo] = v } }
    override suspend fun setGameSettings(twoPlayers: Boolean, color: Int, aiElo: Int) { context.store.edit {
        it[K.twoPlayers] = twoPlayers; it[K.color] = color; it[K.aiElo] = aiElo
    } }
    override suspend fun setPuzzleFilter(band: Int, theme: String) { context.store.edit { it[K.band] = band; it[K.theme] = theme } }
    override suspend fun setNotationLevel(v: Int) { context.store.edit { it[K.level] = v } }
    override suspend fun setLesson(v: Int) { context.store.edit { it[K.lesson] = v } }
    override suspend fun setShareStyle(format: String, light: Boolean) { context.store.edit { it[SHARE_FORMAT] = format; it[SHARE_LIGHT] = light } }
}
