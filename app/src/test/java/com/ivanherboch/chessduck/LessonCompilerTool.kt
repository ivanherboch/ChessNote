package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.chess.sqFromName
import org.junit.Test
import java.io.File

/**
 * Outil d'auteur (inactif sans LESSON_COMPILE) : étape 2 de scripts/gen_lessons.py.
 * Lit app/build/lessons/lessons.tsv, convertit chaque coup (SAN ou UCI) en UCI avec le moteur de l'application
 * (une erreur arrête tout si un coup est illégal) et écrit les fichiers Kotlin typés du paquet content.
 */
class LessonCompilerTool {
    @Test
    fun run() {
        val arg = System.getenv("LESSON_COMPILE") ?: return
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val input = File(arg).let { if (it.isAbsolute) it else File(root, arg) }
        val outDir = File(root, "app/src/main/java/com/ivanherboch/chessduck/content")
        val files = LinkedHashMap<Pair<String, String>, StringBuilder>()
        for (row in input.readLines().filter { it.isNotBlank() }) {
            val f = row.split('|')
            require(f.size == 17) { "ligne invalide : $row" }
            val (variable, fileName, title, summary) = f
            val hero = f[4]
            val fen = f[5]
            val tokens = f[6].split(' ').filter { it.isNotBlank() }
            val texts = f[7].split('\u001f')
            val (id, level, order, theme, kind) = f.subList(8, 13)
            val pauses = f[13].split(',').filter { it.isNotBlank() }.map { it.toInt() }
            val takeaway = f[14]
            val conditions = f[15]
            val visuals = f[16].split('\u001f').filter { it.isNotBlank() }
            val line = Line.fromMoves(fen, tokens)
            require(line.size == texts.size) { "$title : ${line.size} coups pour ${texts.size} commentaires" }
            for (p in pauses) require(p < line.size && line.positions[p].whiteToMove == (hero == "w")) { "$title : arrêt $p hors du camp du joueur" }
            val sb = files.getOrPut(variable to fileName) { StringBuilder() }
            sb.append("    LessonData(\n")
            sb.append("        id = $id,\n")
            sb.append("        title = ${kt(title)},\n")
            sb.append("        summary = ${kt(summary)},\n")
            sb.append("        heroWhite = ${hero == "w"},\n")
            sb.append("        fen = ${if (fen == START_FEN) "START_FEN" else kt(fen)},\n")
            sb.append("        uci = ${kt(line.uciMoves())},\n")
            sb.append("        texts = listOf(\n")
            for (t in texts) sb.append("            ${kt(t)},\n")
            sb.append("        ),\n")
            if (level != "2") sb.append("        level = $level,\n")
            if (order != id) sb.append("        order = $order,\n")
            if (theme.isNotEmpty()) sb.append("        theme = ${kt(theme)},\n")
            if (kind != "line") sb.append("        kind = LessonKind.${kind.uppercase()},\n")
            if (pauses.isNotEmpty()) sb.append("        pauseSteps = setOf(${pauses.joinToString()}),\n")
            sb.append("        takeaway = ${kt(takeaway)},\n")
            if (conditions.isNotEmpty()) sb.append("        conditions = ${kt(conditions)},\n")
            if (visuals.isNotEmpty()) {
                sb.append("        stepVisuals = mapOf(\n")
                for (v in visuals) {
                    val (n, spec) = v.split(':', limit = 2)
                    val (marks, arrows) = spec.split('/')
                    val m = marks.split(',').filter { it.isNotBlank() }.map { sqFromName(it) }
                    val a = arrows.split(',').filter { it.isNotBlank() }.map { "${sqFromName(it.take(2))} to ${sqFromName(it.drop(2))}" }
                    require(n.toInt() <= line.size) { "$title : repère $n hors de la leçon" }
                    sb.append("            $n to StepVisuals(marks = listOf(${m.joinToString()}), arrows = listOf(${a.joinToString()})),\n")
                }
                sb.append("        ),\n")
            }
            sb.append("    ),\n")
        }
        for ((key, body) in files) {
            val (variable, fileName) = key
            File(outDir, fileName).writeText(
                "package com.ivanherboch.chessduck.content\n\n" +
                    "import com.ivanherboch.chessduck.chess.START_FEN\n\n" +
                    "// Fichier généré par scripts/gen_lessons.py + LessonCompilerTool — modifier scripts/lessons/*.txt.\n" +
                    "// Coups en UCI, validés à la génération : aucune analyse de texte ni de notation SAN au démarrage.\n" +
                    "// Cases des repères : 0 = a1 … 63 = h8.\n\n" +
                    "internal val $variable: List<LessonData> = listOf(\n" + body + ")\n",
            )
        }
    }

    private fun kt(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
}
