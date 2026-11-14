package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.content.Lessons
import org.junit.Test
import java.io.File

/** Outil (inactif sans LESSON_SNAPSHOT) : écrit un instantané des 113 leçons pour comparer deux versions. */
class LessonSnapshotTool {
    @Test
    fun run() {
        val out = File(System.getenv("LESSON_SNAPSHOT") ?: return)
        out.writeText(Lessons.all.joinToString("\n") { l ->
            listOf(l.id, l.category, l.title, l.summary, l.heroWhite, l.fen, l.line.uciMoves(), l.line.sans.joinToString(" "), l.texts.joinToString("¦")).joinToString("|")
        })
    }
}
