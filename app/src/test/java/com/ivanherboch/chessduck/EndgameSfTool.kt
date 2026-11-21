package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.engine.Stockfish
import com.ivanherboch.chessduck.feature.EndgameFamily
import com.ivanherboch.chessduck.feature.EndgameTrainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Outil (pas un test) : avec un vrai Stockfish (REVIEW_SF=/usr/games/stockfish), l'élève joue dans chaque finale le
 * meilleur coup du moteur ; aucune position ne doit être jugée perdue. Rapport : app/build/endgame-sf.txt.
 */
class EndgameSfTool {
    @Test
    fun bestMovesAreNeverRefused() = runBlocking {
        val sf = System.getenv("REVIEW_SF") ?: return@runBlocking
        val engine = Stockfish(File(sf))
        val oracle = Stockfish(File(sf))
        val report = StringBuilder()
        val only = System.getenv("FAMILY")
        for (f in EndgameFamily.entries.filter { only == null || it.name == only }) {
            val trainer = EndgameTrainer(this, engine, Random(11), movetimeMs = 150)
            trainer.start(f, total = (System.getenv("TOTAL") ?: "3").toInt())
            var guard = 0
            while (guard++ < 4000) {
                val s = trainer.state.value ?: break
                if (s.finished) break
                if (s.over) {
                    if (s.results.lastOrNull() == false) report.append("  ÉCHEC ${f.name} : ${s.start?.toFen()} → ${s.position?.toFen()} → ${s.message.replace('\n', ' ')}\n")
                    trainer.next(); continue
                }
                if (s.heroToMove) {
                    val best = oracle.search(s.position!!.toFen(), 600).best ?: break
                    trainer.move(best)
                }
                delay(50)
            }
            val s = trainer.state.value!!
            report.append("${f.name} : ${s.results} — ${s.message.lines().first()}\n")
            trainer.stop()
        }
        engine.close(); oracle.close()
        File("build/endgame-sf.txt").writeText(report.toString())
    }
}
