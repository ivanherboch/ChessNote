package com.ivanherboch.chessduck.data

import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
            PlayDefaultsMigration,
        )
    },
)

/**
 * Jeu Libre (4.5.0) : une seule fois, écrit l'enjeu et les réglages effectifs. Installation vierge : partie libre, filet
 * actif, Ordinateur à 600. Installation existante : ce qu'elle avait jusqu'ici (partie classée sauf avec le filet, 1200
 * et sans filet si ces réglages n'avaient jamais été touchés), pour que rien ne change sans que l'utilisateur le choisisse.
 */
internal object PlayDefaultsMigration : androidx.datastore.core.DataMigration<Preferences> {
    private val rated = booleanPreferencesKey("play_rated_game")
    private val net = booleanPreferencesKey("play_safety_net")
    private val ai = intPreferencesKey("ai_elo")
    override suspend fun shouldMigrate(currentData: Preferences) = currentData[rated] == null
    override suspend fun migrate(currentData: Preferences): Preferences = migratePlayDefaults(currentData)
    override suspend fun cleanUp() = Unit

    fun migratePlayDefaults(p: Preferences): Preferences {
        val m = p.toMutablePreferences()
        if (p.asMap().isEmpty()) {
            m[rated] = false; m[net] = true; m[ai] = 600
        } else {
            val hadNet = p[net] ?: false
            m[net] = hadNet
            m[rated] = !hadNet
            if (p[ai] == null) m[ai] = 1200
        }
        return m.toPreferences()
    }
}

/** Classement de départ (et après réinitialisation). */
const val DEFAULT_ELO = 1200

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
    /** Variantes d'ouverture réussies en Défi (« leçon:variante »). */
    val variantsDone: Set<String> = emptySet(),
    /** Ouvertures (identifiants de leçon) que l'élève a mises dans « Mon répertoire ». */
    val repertoireIds: Set<Int> = emptySet(),
    /** Dernière pratique réussie de chaque leçon (identifiant → horodatage) : un point faible travaillé recule. */
    val lessonWorkedAt: Map<Int, Long> = emptyMap(),
    /** Leçons dont la démonstration « Comprendre » a été suivie jusqu'au bout. */
    val understoodIds: Set<Int> = emptySet(),
    /** Séance du jour : étapes faites aujourd'hui et série de jours. */
    val daily: DailyLog = DailyLog(),
    /** Jeu Libre : filet de sécurité (l'Ordinateur arrête une gaffe nette avant qu'elle soit jouée). Actif d'emblée. */
    val safetyNet: Boolean = true,
    /** Jeu Libre : partie classée (compte pour l'ELO) ; par défaut, partie libre, sans enjeu. */
    val ratedGame: Boolean = false,
    /** Gaffes arrêtées par le filet (elles comptent dans « À travailler »). */
    val netEvents: List<NetEvent> = emptyList(),
    val pseudo: String = "Joueur",
    /** Second joueur humain du mode 2 joueurs. */
    val pseudo2: String = "Joueur 2",
    val elo: Int = DEFAULT_ELO,
    val twoPlayers: Boolean = false,
    /** 0 = Blancs, 1 = Aléatoire, 2 = Noirs. */
    val colorChoice: Int = 0,
    /** Niveau de l'Ordinateur : 600 (débutant) par défaut, comme le palier 1 de l'Académie. */
    val aiElo: Int = 600,
    /** 0 = Tous, 1 = Débutant, 2 = Intermédiaire, 3 = Avancé, 4 = Expert. */
    val puzzleBand: Int = 0,
    val puzzleTheme: String = "",
    /** ELO tactique des Problèmes (« Mon niveau ») ; 0 : pas encore calculé (on part alors de l'ELO du Jeu Libre). */
    val puzzleElo: Int = 0,
    /** Catalogue : longueur de la solution (0 toutes, 1 un coup, 2 courte, 3 longue ; PuzzleThemes.LENGTHS). */
    val puzzleLength: Int = 0,
    val puzzleId: String = "",
    val notationLevel: Int = 0,
    /** Leçon ouverte : au premier lancement, la première du cursus (« L'échiquier et la notation »). */
    val lessonId: Int = 115,
    /** Palier de l'Académie choisi ou atteint (1 Les bases … 5 Confirmé) : un nouvel élève commence aux bases. */
    val academyLevel: Int = 1,
    /** Le test de placement de l'Académie a été passé (ou écarté). */
    val placementDone: Boolean = false,
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
        val variantsDone = stringPreferencesKey("variants_done")
        val repertoireIds = stringPreferencesKey("academy_repertoire_ids")
        val workedAt = stringPreferencesKey("academy_lesson_worked_at")
        val understood = stringPreferencesKey("academy_understood_ids")
        val daily = stringPreferencesKey("academy_daily")
        val safetyNet = booleanPreferencesKey("play_safety_net")
        val ratedGame = booleanPreferencesKey("play_rated_game")
        val netEvents = stringPreferencesKey("play_net_events")
        val pseudo = stringPreferencesKey("pseudo")
        val pseudo2 = stringPreferencesKey("pseudo2")
        val elo = intPreferencesKey("elo")
        val twoPlayers = booleanPreferencesKey("two_players")
        val color = intPreferencesKey("color")
        val aiElo = intPreferencesKey("ai_elo")
        val band = intPreferencesKey("puzzle_band")
        val puzzleElo = intPreferencesKey("puzzle_elo")
        val puzzleLength = intPreferencesKey("puzzle_length")
        val theme = stringPreferencesKey("puzzle_theme")
        val puzzleId = stringPreferencesKey("puzzle_id")
        val level = intPreferencesKey("notation_level")
        val lesson = intPreferencesKey("lesson_id")
        val academyLevel = intPreferencesKey("academy_level")
        val placementDone = booleanPreferencesKey("academy_placement_done")
    }

    /** Sauvegarde hors-ligne : toutes les préférences telles qu'enregistrées (nom → valeur). */
    suspend fun snapshot(): Map<String, Any> = context.store.data.first().asMap().mapKeys { it.key.name }

    /** Restauration d'une sauvegarde : les préférences sont remplacées par [values] (types déjà vérifiés). */
    suspend fun restore(values: Map<String, Any>) {
        context.store.edit { p ->
            p.clear()
            for ((k, v) in values) when (v) {
                is Boolean -> p[booleanPreferencesKey(k)] = v
                is Int -> p[intPreferencesKey(k)] = v
                is Long -> p[longPreferencesKey(k)] = v
                is Float -> p[floatPreferencesKey(k)] = v
                is Double -> p[doublePreferencesKey(k)] = v
                is String -> p[stringPreferencesKey(k)] = v
                is Set<*> -> p[stringSetPreferencesKey(k)] = v.filterIsInstance<String>().toSet()
            }
        }
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
            variantsDone = (p[K.variantsDone] ?: "").split(',').filter { it.isNotBlank() }.toSet(),
            repertoireIds = (p[K.repertoireIds] ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
            lessonWorkedAt = decodeWorked(p[K.workedAt]),
            understoodIds = (p[K.understood] ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
            daily = DailyLog.decode(p[K.daily]),
            safetyNet = p[K.safetyNet] ?: true,
            ratedGame = p[K.ratedGame] ?: false,
            netEvents = decodeNetEvents(p[K.netEvents]),
            pseudo = p[K.pseudo] ?: "Joueur",
            pseudo2 = p[K.pseudo2] ?: "Joueur 2",
            elo = p[K.elo] ?: DEFAULT_ELO,
            twoPlayers = p[K.twoPlayers] ?: false,
            colorChoice = p[K.color] ?: 0,
            aiElo = p[K.aiElo] ?: 600,
            puzzleBand = p[K.band] ?: 0,
            puzzleTheme = p[K.theme] ?: "",
            puzzleElo = p[K.puzzleElo] ?: 0,
            puzzleLength = p[K.puzzleLength] ?: 0,
            puzzleId = p[K.puzzleId] ?: "",
            notationLevel = p[K.level] ?: 0,
            lessonId = p[K.lesson] ?: 115,
            academyLevel = p[K.academyLevel] ?: 1,
            placementDone = p[K.placementDone] ?: false,
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
    override suspend fun setAcademyLevel(v: Int) { context.store.edit { it[K.academyLevel] = v.coerceIn(1, 5) } }
    override suspend fun setPlacementDone(v: Boolean) { context.store.edit { it[K.placementDone] = v } }
    override suspend fun setNarration(v: Boolean) { context.store.edit { it[K.narration] = v } }
    override suspend fun setKidNarration(v: Boolean) { context.store.edit { it[K.kidNarration] = v } }
    override suspend fun setKidMode(v: Boolean) { context.store.edit { it[K.kid] = v } }

    /** Place [id] en tête de l'historique des problèmes joués (51 conservés : le courant + 50 précédents). */
    override suspend fun pushRecentPuzzle(id: String) { context.store.edit { p ->
        val old = (p[K.recent] ?: "").split(',').filter { it.isNotBlank() && it != id }
        val recent = (listOf(id) + old).take(RECENT_PUZZLES + 1)
        p[K.recent] = recent.joinToString(",")
        p[K.puzzleId] = id
        // Les thèmes « connus » ne concernent que les problèmes encore dans l'historique.
        p[K.knownThemes] = (p[K.knownThemes] ?: "").split(',').filter { it.isNotBlank() && it in recent }.joinToString(",")
    } }

    override suspend fun markThemeKnown(id: String) { context.store.edit { p ->
        val known = (p[K.knownThemes] ?: "").split(',').filter { it.isNotBlank() }.toMutableSet()
        if (known.add(id)) p[K.knownThemes] = known.joinToString(",")
    } }
    override suspend fun markVariantDone(key: String) { context.store.edit { p ->
        val done = (p[K.variantsDone] ?: "").split(',').filter { it.isNotBlank() }.toMutableSet()
        if (done.add(key)) p[K.variantsDone] = done.joinToString(",")
    } }
    override suspend fun markDailyStep(step: String, today: Long, required: Set<String>) { context.store.edit { p ->
        p[K.daily] = DailyLog.decode(p[K.daily]).mark(step, today, required).encode()
    } }
    // Le filet de sécurité est une aide : l'activer repasse en partie libre (une partie classée se joue sans filet).
    override suspend fun setSafetyNet(on: Boolean) { context.store.edit { it[K.safetyNet] = on; if (on) it[K.ratedGame] = false } }
    // Partie classée : le filet de sécurité est coupé en même temps (une partie classée se joue sans aide).
    override suspend fun setRatedGame(on: Boolean) { context.store.edit { it[K.ratedGame] = on; if (on) it[K.safetyNet] = false } }
    override suspend fun addNetEvent(topic: String, at: Long) { context.store.edit { p ->
        p[K.netEvents] = encodeNetEvents((decodeNetEvents(p[K.netEvents]) + NetEvent(topic, at)).takeLast(NET_EVENTS_MAX))
    } }
    override suspend fun markUnderstood(lessonId: Int) { context.store.edit { p ->
        val ids = (p[K.understood] ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }.toMutableSet()
        if (ids.add(lessonId)) p[K.understood] = ids.sorted().joinToString(",")
    } }
    override suspend fun markLessonWorked(lessonId: Int, at: Long) { context.store.edit { p ->
        p[K.workedAt] = (decodeWorked(p[K.workedAt]) + (lessonId to at)).entries.joinToString(",") { "${it.key}:${it.value}" }
    } }

    /** « id:horodatage,… » → table ; les entrées illisibles sont ignorées. */
    private fun decodeWorked(v: String?): Map<Int, Long> = (v ?: "").split(',').mapNotNull { e ->
        val (id, at) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        (id.trim().toIntOrNull() ?: return@mapNotNull null) to (at.trim().toLongOrNull() ?: return@mapNotNull null)
    }.toMap()

    override suspend fun toggleRepertoire(lessonId: Int) { context.store.edit { p ->
        val ids = (p[K.repertoireIds] ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }.toMutableSet()
        if (!ids.remove(lessonId)) ids.add(lessonId)
        p[K.repertoireIds] = ids.sorted().joinToString(",")
    } }
    override suspend fun setPseudo(v: String) { context.store.edit { it[K.pseudo] = v } }
    override suspend fun setPseudo2(v: String) { context.store.edit { it[K.pseudo2] = v } }
    override suspend fun setElo(v: Int) { context.store.edit { it[K.elo] = v } }
    override suspend fun setGameSettings(twoPlayers: Boolean, color: Int, aiElo: Int) { context.store.edit {
        it[K.twoPlayers] = twoPlayers; it[K.color] = color; it[K.aiElo] = aiElo
    } }
    override suspend fun setPuzzleFilter(band: Int, theme: String) { context.store.edit { it[K.band] = band; it[K.theme] = theme } }
    override suspend fun setPuzzleElo(v: Int) { context.store.edit { it[K.puzzleElo] = v } }
    override suspend fun setPuzzleLength(v: Int) { context.store.edit { it[K.puzzleLength] = v } }
    override suspend fun setNotationLevel(v: Int) { context.store.edit { it[K.level] = v } }
    override suspend fun setLesson(v: Int) { context.store.edit { it[K.lesson] = v } }
    override suspend fun setShareStyle(format: String, light: Boolean) { context.store.edit { it[SHARE_FORMAT] = format; it[SHARE_LIGHT] = light } }
}

/** Problèmes récents gardés dans l'historique (en plus du problème courant). */
const val RECENT_PUZZLES = 50
