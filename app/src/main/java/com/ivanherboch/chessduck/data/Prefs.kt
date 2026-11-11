package com.ivanherboch.chessduck.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("chessduck")

data class Settings(
    val dark: Boolean = true,
    val haptics: Boolean = true,
    val sound: Boolean = true,
    val kidMode: Boolean = false,
    /** Identifiants des derniers problèmes joués, du plus récent au plus ancien. */
    val recentPuzzles: List<String> = emptyList(),
    val pseudo: String = "Joueur",
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
)

/** Préférences persistées via Jetpack DataStore Preferences. */
class Prefs(private val context: Context) {
    private object K {
        val dark = booleanPreferencesKey("dark")
        val haptics = booleanPreferencesKey("haptics")
        val sound = booleanPreferencesKey("sound")
        val kid = booleanPreferencesKey("kid_mode")
        val recent = stringPreferencesKey("recent_puzzles")
        val pseudo = stringPreferencesKey("pseudo")
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

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            dark = p[K.dark] ?: true,
            haptics = p[K.haptics] ?: true,
            sound = p[K.sound] ?: true,
            kidMode = p[K.kid] ?: false,
            recentPuzzles = (p[K.recent] ?: "").split(',').filter { it.isNotBlank() },
            pseudo = p[K.pseudo] ?: "Joueur",
            elo = p[K.elo] ?: 1200,
            twoPlayers = p[K.twoPlayers] ?: false,
            colorChoice = p[K.color] ?: 0,
            aiElo = p[K.aiElo] ?: 1200,
            puzzleBand = p[K.band] ?: 0,
            puzzleTheme = p[K.theme] ?: "",
            puzzleId = p[K.puzzleId] ?: "",
            notationLevel = p[K.level] ?: 0,
            lessonId = p[K.lesson] ?: 1,
        )
    }

    suspend fun setDark(v: Boolean) = context.store.edit { it[K.dark] = v }
    suspend fun setHaptics(v: Boolean) = context.store.edit { it[K.haptics] = v }
    suspend fun setSound(v: Boolean) = context.store.edit { it[K.sound] = v }
    suspend fun setKidMode(v: Boolean) = context.store.edit { it[K.kid] = v }

    /** Place [id] en tête de l'historique des problèmes joués (6 conservés : le courant + 5 précédents). */
    suspend fun pushRecentPuzzle(id: String) = context.store.edit { p ->
        val old = (p[K.recent] ?: "").split(',').filter { it.isNotBlank() && it != id }
        p[K.recent] = (listOf(id) + old).take(6).joinToString(",")
        p[K.puzzleId] = id
    }
    suspend fun setPseudo(v: String) = context.store.edit { it[K.pseudo] = v }
    suspend fun setElo(v: Int) = context.store.edit { it[K.elo] = v }
    suspend fun setGameSettings(twoPlayers: Boolean, color: Int, aiElo: Int) = context.store.edit {
        it[K.twoPlayers] = twoPlayers; it[K.color] = color; it[K.aiElo] = aiElo
    }
    suspend fun setPuzzleFilter(band: Int, theme: String) = context.store.edit { it[K.band] = band; it[K.theme] = theme }
    suspend fun setPuzzleId(id: String) = context.store.edit { it[K.puzzleId] = id }
    suspend fun setNotationLevel(v: Int) = context.store.edit { it[K.level] = v }
    suspend fun setLesson(v: Int) = context.store.edit { it[K.lesson] = v }
}
