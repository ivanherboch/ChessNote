package com.ivanherboch.chessduck

import android.app.Application
import com.ivanherboch.chessduck.data.DuckDatabase
import com.ivanherboch.chessduck.data.Prefs
import com.ivanherboch.chessduck.engine.Stockfish
import com.ivanherboch.chessduck.ui.Feedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ChessDuckApp : Application() {
    val db by lazy { DuckDatabase.create(this) }
    val prefs by lazy { Prefs(this) }
    val engine by lazy { Stockfish(this) }
    val feedback by lazy { Feedback(this) }

    /** Portée applicative : les écritures de réglages ne sont jamais annulées par la fermeture d'un tiroir. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
