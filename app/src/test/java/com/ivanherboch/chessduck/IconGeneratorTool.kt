package com.ivanherboch.chessduck

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Test
import java.io.File

/**
 * Outil (inactif sans ICON_GEN) : génère ui/icons/DuckIcons.kt avec les seules icônes Material « étendues »
 * utilisées par l'application, recopiées depuis la bibliothèque officielle (dépendance de test uniquement).
 * L'application n'embarque ainsi que material-icons-core et ces quelques vecteurs.
 * Entrée : ICON_GEN = liste « famille.Nom » séparée par des espaces (ex. rounded.Flag automirrored.rounded.Undo).
 */
class IconGeneratorTool {
    @Test
    fun run() {
        val spec = System.getenv("ICON_GEN") ?: return
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val out = File(root, "app/src/main/java/com/ivanherboch/chessduck/ui/icons/DuckIcons.kt")
        val sb = StringBuilder()
        sb.append("package com.ivanherboch.chessduck.ui.icons\n\n")
        sb.append("import androidx.compose.material.icons.Icons\n")
        sb.append("import androidx.compose.material.icons.materialIcon\n")
        sb.append("import androidx.compose.ui.graphics.Color\n")
        sb.append("import androidx.compose.ui.graphics.PathFillType\n")
        sb.append("import androidx.compose.ui.graphics.SolidColor\n")
        sb.append("import androidx.compose.ui.graphics.vector.ImageVector\n")
        sb.append("import androidx.compose.ui.graphics.vector.addPathNodes\n\n")
        sb.append("// Fichier généré par IconGeneratorTool (Material Icons, Apache 2.0) — ne pas modifier à la main.\n")
        sb.append("// Seules les icônes « étendues » utilisées sont embarquées ; les autres viennent de material-icons-core.\n")
        for (entry in spec.split(' ').filter { it.isNotBlank() }.sorted()) {
            val family = entry.substringBeforeLast('.')
            val name = entry.substringAfterLast('.')
            val receiver = when (family) {
                "rounded" -> Icons.Rounded
                "outlined" -> Icons.Outlined
                "automirrored.rounded" -> Icons.AutoMirrored.Rounded
                else -> error("famille inconnue : $family")
            }
            val receiverType = when (family) {
                "rounded" -> "Icons.Rounded"
                "outlined" -> "Icons.Outlined"
                else -> "Icons.AutoMirrored.Rounded"
            }
            val cls = Class.forName("androidx.compose.material.icons.$family.${name}Kt")
            val v = cls.getMethod("get$name", receiver.javaClass).invoke(null, receiver) as ImageVector
            val cache = "_" + family.replace(".", "_") + "_" + name
            sb.append("\nprivate var $cache: ImageVector? = null\n\n")
            sb.append("val $receiverType.$name: ImageVector\n")
            sb.append("    get() = $cache ?: materialIcon(name = \"${v.name}\", autoMirror = ${v.autoMirror}) {\n")
            val paths = ArrayList<VectorPath>()
            fun collect(g: VectorGroup) {
                for (i in 0 until g.size) when (val n = g[i]) {
                    is VectorPath -> paths.add(n)
                    is VectorGroup -> {
                        require(n.rotation == 0f && n.translationX == 0f && n.translationY == 0f && n.scaleX == 1f && n.scaleY == 1f) { "groupe transformé : $entry" }
                        collect(n)
                    }
                }
            }
            collect(v.root)
            for (p in paths) {
                require(p.fill is SolidColor? && p.stroke == null) { "tracé non pris en charge : $entry" }
                val type = if (p.pathFillType == PathFillType.EvenOdd) "PathFillType.EvenOdd" else "PathFillType.NonZero"
                sb.append("        addPath(\n")
                sb.append("            addPathNodes(\"${svg(p.pathData)}\"),\n")
                sb.append("            pathFillType = $type, fill = SolidColor(Color.Black), fillAlpha = ${p.fillAlpha}f,\n")
                sb.append("        )\n")
            }
            sb.append("    }.also { $cache = it }\n")
        }
        out.parentFile?.mkdirs()
        out.writeText(sb.toString())
    }

    private fun f(x: Float): String = x.toString().removeSuffix(".0")

    private fun svg(nodes: List<PathNode>): String = nodes.joinToString(" ") { n ->
        when (n) {
            is PathNode.MoveTo -> "M${f(n.x)},${f(n.y)}"
            is PathNode.RelativeMoveTo -> "m${f(n.dx)},${f(n.dy)}"
            is PathNode.LineTo -> "L${f(n.x)},${f(n.y)}"
            is PathNode.RelativeLineTo -> "l${f(n.dx)},${f(n.dy)}"
            is PathNode.HorizontalTo -> "H${f(n.x)}"
            is PathNode.RelativeHorizontalTo -> "h${f(n.dx)}"
            is PathNode.VerticalTo -> "V${f(n.y)}"
            is PathNode.RelativeVerticalTo -> "v${f(n.dy)}"
            is PathNode.CurveTo -> "C${f(n.x1)},${f(n.y1)} ${f(n.x2)},${f(n.y2)} ${f(n.x3)},${f(n.y3)}"
            is PathNode.RelativeCurveTo -> "c${f(n.dx1)},${f(n.dy1)} ${f(n.dx2)},${f(n.dy2)} ${f(n.dx3)},${f(n.dy3)}"
            is PathNode.ReflectiveCurveTo -> "S${f(n.x1)},${f(n.y1)} ${f(n.x2)},${f(n.y2)}"
            is PathNode.RelativeReflectiveCurveTo -> "s${f(n.dx1)},${f(n.dy1)} ${f(n.dx2)},${f(n.dy2)}"
            is PathNode.QuadTo -> "Q${f(n.x1)},${f(n.y1)} ${f(n.x2)},${f(n.y2)}"
            is PathNode.RelativeQuadTo -> "q${f(n.dx1)},${f(n.dy1)} ${f(n.dx2)},${f(n.dy2)}"
            is PathNode.ReflectiveQuadTo -> "T${f(n.x)},${f(n.y)}"
            is PathNode.RelativeReflectiveQuadTo -> "t${f(n.dx)},${f(n.dy)}"
            is PathNode.ArcTo -> "A${f(n.horizontalEllipseRadius)},${f(n.verticalEllipseRadius)} ${f(n.theta)} ${if (n.isMoreThanHalf) 1 else 0},${if (n.isPositiveArc) 1 else 0} ${f(n.arcStartX)},${f(n.arcStartY)}"
            is PathNode.RelativeArcTo -> "a${f(n.horizontalEllipseRadius)},${f(n.verticalEllipseRadius)} ${f(n.theta)} ${if (n.isMoreThanHalf) 1 else 0},${if (n.isPositiveArc) 1 else 0} ${f(n.arcStartDx)},${f(n.arcStartDy)}"
            PathNode.Close -> "Z"
        }
    }
}
