package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.data.UserBackup
import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ivanherboch.chessduck.data.DuckDatabase
import com.ivanherboch.chessduck.data.GameRepository
import com.ivanherboch.chessduck.data.LessonProgressRepository
import com.ivanherboch.chessduck.data.Prefs
import com.ivanherboch.chessduck.data.PuzzleRepository
import com.ivanherboch.chessduck.data.RoomGameRepository
import com.ivanherboch.chessduck.data.RoomLessonProgressRepository
import com.ivanherboch.chessduck.data.RoomPuzzleRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.engine.Stockfish
import com.ivanherboch.chessduck.feature.AcademyViewModel
import com.ivanherboch.chessduck.feature.GameAnalysisService
import com.ivanherboch.chessduck.feature.GameReviewViewModel
import com.ivanherboch.chessduck.feature.KidViewModel
import com.ivanherboch.chessduck.feature.NotationViewModel
import com.ivanherboch.chessduck.feature.PlayViewModel
import com.ivanherboch.chessduck.feature.PuzzlesViewModel
import com.ivanherboch.chessduck.ui.Feedback
import com.ivanherboch.chessduck.ui.TtsNarrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Conteneur de dépendances (injection manuelle, sans framework) : une seule instance de chaque service,
 * créée à la demande. Les ViewModels ne reçoivent que des interfaces ([SettingsRepository], [PuzzleRepository],
 * [GameRepository], [LessonProgressRepository], [ChessEngine]) : ils sont testables avec des faux.
 */
class AppContainer(context: Context, val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    val db: DuckDatabase by lazy { DuckDatabase.create(appContext) }
    val prefs: Prefs by lazy { Prefs(appContext) }
    val settings: SettingsRepository get() = prefs
    val puzzles: PuzzleRepository by lazy { RoomPuzzleRepository(appContext, db) }
    val games: GameRepository by lazy { RoomGameRepository(db) }
    val lessons: LessonProgressRepository by lazy { RoomLessonProgressRepository(db) }
    /** Export et import de la progression (Paramètres, « Sauvegarde hors-ligne »). */
    val backup: UserBackup by lazy { UserBackup(db, prefs) }
    private val engineLazy = lazy { Stockfish(appContext) }
    val engine: ChessEngine by engineLazy
    private val analysisLazy = lazy { GameAnalysisService(engine, games, scope) }
    val analysis: GameAnalysisService by analysisLazy
    private val feedbackLazy = lazy { Feedback(appContext) }
    val feedback: Feedback by feedbackLazy
    private val narratorLazy = lazy { TtsNarrator(appContext) }
    val narrator: TtsNarrator by narratorLazy

    /**
     * L'utilisateur quitte l'application (fin de l'activité) : le processus natif de Stockfish, la synthèse vocale et
     * les sons sont libérés. Chaque service redémarre de lui-même s'il resert (nouvelle activité dans le même
     * processus). Seuls les services déjà créés sont touchés ; une analyse de partie en cours garde son moteur.
     */
    fun release() {
        if (engineLazy.isInitialized() && !(analysisLazy.isInitialized() && analysisLazy.value.busy)) engineLazy.value.close()
        if (narratorLazy.isInitialized()) narratorLazy.value.close()
        if (feedbackLazy.isInitialized()) feedbackLazy.value.close()
    }
}

class ChessDuckApp : Application() {
    /** Portée applicative : les écritures de réglages ne sont jamais annulées par la fermeture d'un tiroir. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val container by lazy { AppContainer(this, scope) }

    val db get() = container.db
    val prefs get() = container.prefs
    val feedback get() = container.feedback
}

/** Fabrique unique des ViewModels de l'application, alimentée par l'[AppContainer]. */
object DuckViewModels {
    private fun CreationExtras.container(): AppContainer =
        (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ChessDuckApp).container

    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { container().let { NotationViewModel(it.settings, it.puzzles) } }
        initializer { container().let { AcademyViewModel(it.settings, it.lessons, it.narrator, it.engine, it.puzzles, createSavedStateHandle(), games = it.games, work = kotlinx.coroutines.Dispatchers.Default) } }
        initializer { container().let { PuzzlesViewModel(it.settings, it.puzzles, it.engine, createSavedStateHandle()) } }
        initializer { container().let { PlayViewModel(it.settings, it.games, it.engine, it.analysis, saved = createSavedStateHandle()) } }
        initializer { GameReviewViewModel(container().analysis) }
        initializer { container().let { KidViewModel(it.settings, it.narrator) } }
    }
}
