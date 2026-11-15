package com.ivanherboch.chessduck.ui

import com.ivanherboch.chessduck.chess.Line
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Navigation dans une suite de coups ([Line]) partagée par l'Académie (mode Guidé) et la revue de partie :
 * animation d'une transition de curseur et lecture automatique annulable (un seul travail à la fois).
 */
class LinePlaybackController(private val scope: CoroutineScope) {
    private var job: Job? = null

    /** Arrête le travail en cours (lecture automatique ou séquence lancée par [launch]). */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Lance [block] en remplaçant le travail en cours. */
    fun launch(block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch(block = block)
    }

    /**
     * Lecture automatique : avance d'un demi-coup à la fois ([advance] reçoit la cible) jusqu'à la fin de la ligne,
     * puis appelle [onEnd]. [cursor] renvoie null si la ligne n'est plus affichée (arrêt sans [onEnd]).
     * [checkBeforeDelay] : vrai = on vérifie la fin avant d'attendre (Académie), faux = après l'attente (revue).
     * [awaitReady] est attendu avant chaque pause (ex. fin de la lecture à voix haute du commentaire).
     */
    fun autoplay(
        cursor: () -> Int?,
        size: () -> Int,
        delayFor: (Int) -> Long,
        checkBeforeDelay: Boolean,
        advance: (Int) -> Unit,
        onEnd: () -> Unit,
        awaitReady: suspend () -> Unit = {},
    ) = launch {
        while (true) {
            val before = cursor() ?: return@launch
            if (checkBeforeDelay && before >= size()) break
            awaitReady()
            delay(delayFor(before))
            val now = cursor() ?: return@launch
            if (!checkBeforeDelay && now >= size()) break
            advance(now + 1)
        }
        onEnd()
    }

    companion object {
        /** Animation d'un passage de [from] à [to] : un coup en avant ou en arrière est animé, un saut ne l'est pas. */
        fun transition(line: Line, from: Int, to: Int): BoardAnim? = when (to) {
            from + 1 -> moveAnim(line.positions[from], line.moves[from])
            from - 1 -> undoAnim(line.positions[to], line.moves[to])
            else -> null
        }
    }
}
