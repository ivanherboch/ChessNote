package com.ivanherboch.chessduck.feature

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.ivanherboch.chessduck.R
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.fileOf
import com.ivanherboch.chessduck.chess.isWhite
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.rankOf
import com.ivanherboch.chessduck.chess.sq
import com.ivanherboch.chessduck.chess.sqName
import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.ui.PIECE_RES
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cartes de partage PNG : l'énoncé d'un problème, ou sa solution illustrée étape par étape.
 * Les pièces sont exactement celles de l'échiquier en direct.
 */
object ShareCard {
    private const val W = 1080
    private val LIGHT = Color.parseColor("#F0D9B5")
    private val DARK = Color.parseColor("#B58863")
    private val TEXT = Color.parseColor("#F8FAFC")
    private val MUTED = Color.parseColor("#94A3B8")
    private val GOLD = Color.parseColor("#F59E0B")
    private val GREEN = Color.parseColor("#10B981")
    private val BLUE = Color.parseColor("#38BDF8")
    private val RED = Color.parseColor("#EF4444")
    private val BOLD: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    private class Painter(val context: Context, val cv: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val pieces = HashMap<Int, Bitmap>()
        fun piece(p: Int): Bitmap = pieces.getOrPut(p) { BitmapFactory.decodeResource(context.resources, PIECE_RES.getValue(p)) }

        fun text(s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
            paint.style = Paint.Style.FILL
            paint.typeface = if (bold) BOLD else Typeface.DEFAULT
            paint.textSize = size
            paint.color = color
            paint.textAlign = align
            cv.drawText(s, x, y, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        fun wrap(s: String, size: Float, width: Float, bold: Boolean = false): List<String> {
            paint.typeface = if (bold) BOLD else Typeface.DEFAULT
            paint.textSize = size
            val out = ArrayList<String>()
            var cur = ""
            for (w in s.split(' ')) {
                val cand = if (cur.isEmpty()) w else "$cur $w"
                if (paint.measureText(cand) > width && cur.isNotEmpty()) { out.add(cur); cur = w } else cur = cand
            }
            if (cur.isNotEmpty()) out.add(cur)
            return out
        }

        fun background(h: Int) {
            paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.parseColor("#1E293B"), Color.parseColor("#0F172A"), Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, W.toFloat(), h.toFloat(), paint)
            paint.shader = null
        }

        fun header(subtitle: String) {
            val mascot = BitmapFactory.decodeResource(context.resources, R.drawable.mascot)
            cv.drawBitmap(mascot, null, RectF(48f, 30f, 48f + 156f, 30f + 156f), paint)
            text("ChessDuck", 222f, 112f, 76f, TEXT, bold = true)
            text(subtitle, 224f, 166f, 36f, MUTED)
        }

        fun footer(h: Int) = text("© Ivan Herboch, 2026", W - 48f, h - 28f, 28f, Color.parseColor("#64748B"), align = Paint.Align.RIGHT)

        /** Dessine un échiquier ; renvoie la fonction donnant le coin supérieur gauche d'une case. */
        fun board(left: Float, top: Float, side: Float, position: Position, flipped: Boolean, lastMove: Move?): (Int) -> Pair<Float, Float> {
            val s = side / 8
            fun x(q: Int) = left + (if (flipped) 7 - fileOf(q) else fileOf(q)) * s
            fun y(q: Int) = top + (if (flipped) rankOf(q) else 7 - rankOf(q)) * s
            paint.style = Paint.Style.FILL
            for (q in 0 until 64) {
                paint.color = if ((fileOf(q) + rankOf(q)) % 2 == 1) LIGHT else DARK
                cv.drawRect(x(q), y(q), x(q) + s, y(q) + s, paint)
            }
            lastMove?.let {
                paint.color = Color.parseColor("#66F7EC5E")
                cv.drawRect(x(it.from), y(it.from), x(it.from) + s, y(it.from) + s, paint)
                cv.drawRect(x(it.to), y(it.to), x(it.to) + s, y(it.to) + s, paint)
            }
            paint.typeface = BOLD
            paint.textSize = s * 0.2f
            for (i in 0..7) {
                val fq = if (flipped) sq(i, 7) else sq(i, 0)
                paint.color = if ((fileOf(fq) + rankOf(fq)) % 2 == 1) DARK else LIGHT
                cv.drawText(('a' + i).toString(), x(fq) + s * 0.82f, y(fq) + s * 0.94f, paint)
                val rq = if (flipped) sq(7, i) else sq(0, i)
                paint.color = if ((fileOf(rq) + rankOf(rq)) % 2 == 1) DARK else LIGHT
                cv.drawText(('1' + i).toString(), x(rq) + s * 0.06f, y(rq) + s * 0.24f, paint)
            }
            for (q in 0 until 64) {
                val p = position[q]
                if (p != 0) cv.drawBitmap(piece(p), null, RectF(x(q), y(q), x(q) + s, y(q) + s), paint)
            }
            return { q -> x(q) to y(q) }
        }

        /** Flèche directionnelle, coudée en « L » pour un Cavalier. */
        fun arrow(m: Move, at: (Int) -> Pair<Float, Float>, s: Float, color: Int, widthFactor: Float) {
            fun c(q: Int) = at(q).let { (x, y) -> x + s / 2 to y + s / 2 }
            val df = abs(fileOf(m.to) - fileOf(m.from))
            val dr = abs(rankOf(m.to) - rankOf(m.from))
            val pts = arrayListOf(c(m.from))
            if ((df == 1 && dr == 2) || (df == 2 && dr == 1)) {
                pts.add(if (dr == 2) c(sq(fileOf(m.from), rankOf(m.to))) else c(sq(fileOf(m.to), rankOf(m.from))))
            }
            pts.add(c(m.to))
            fun unit(a: Pair<Float, Float>, b: Pair<Float, Float>): Pair<Float, Float> {
                val dx = b.first - a.first
                val dy = b.second - a.second
                val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                return dx / len to dy / len
            }
            val n = pts.size
            val u0 = unit(pts[0], pts[1])
            val u1 = unit(pts[n - 2], pts[n - 1])
            val head = s * 0.42f * (widthFactor / 0.17f).coerceAtMost(1f).coerceAtLeast(0.8f)
            val tipX = pts[n - 1].first + u1.first * s * 0.12f
            val tipY = pts[n - 1].second + u1.second * s * 0.12f
            val path = Path().apply {
                moveTo(pts[0].first + u0.first * s * 0.28f, pts[0].second + u0.second * s * 0.28f)
                for (i in 1 until n - 1) lineTo(pts[i].first, pts[i].second)
                lineTo(tipX - u1.first * head * 0.85f, tipY - u1.second * head * 0.85f)
            }
            paint.color = color
            paint.alpha = 220
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s * widthFactor
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            cv.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            val ang = atan2(u1.second, u1.first)
            val spread = 0.52f
            cv.drawPath(Path().apply {
                moveTo(tipX, tipY)
                lineTo(tipX - head * cos(ang - spread), tipY - head * sin(ang - spread))
                lineTo(tipX - head * cos(ang + spread), tipY - head * sin(ang + spread))
                close()
            }, paint)
            paint.alpha = 255
        }

        fun badge(label: String, cx: Float, cy: Float, r: Float, color: Int) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#55000000")
            cv.drawCircle(cx, cy + r * 0.12f, r * 1.08f, paint)
            paint.color = color
            cv.drawCircle(cx, cy, r, paint)
            paint.color = Color.WHITE
            paint.typeface = BOLD
            paint.textSize = r * 1.35f
            paint.textAlign = Paint.Align.CENTER
            cv.drawText(label, cx, cy + r * 0.48f, paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }

    // ---------------------------------------------------------------- Énoncé

    fun render(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?, chosen: String = ""): Bitmap {
        val h = 1600
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val g = Painter(context, Canvas(bmp))
        g.background(h)
        g.header("Problème d'échecs  ·  Lichess #${puzzle.id}")
        val side = 984f
        val top = 220f
        g.board((W - side) / 2, top, side, position, !playerWhite, lastMove)

        // Légende : trait, difficulté, type de problème et conseil (sans la solution)
        val theme = PuzzleThemes.shown(puzzle.themes, chosen)
        val base = top + side
        turnBox(g, 48f, base + 44f, playerWhite)
        g.text(if (playerWhite) "Trait aux Blancs" else "Trait aux Noirs", 110f, base + 86f, 54f, TEXT, bold = true)
        val band = PuzzleThemes.BANDS[PuzzleThemes.bandOf(puzzle.rating)]
        g.text("Difficulté : $band (${puzzle.rating})", 48f, base + 150f, 40f, GOLD)
        g.text("Type de problème : ${theme.name}", 48f, base + 206f, 40f, BLUE)
        var y = base + 264f
        for (l in g.wrap("Conseil : ${theme.tip}", 36f, W - 96f)) { g.text(l, 48f, y, 36f, Color.parseColor("#E2E8F0")); y += 46f }
        g.footer(h)
        return bmp
    }

    private fun turnBox(g: Painter, x: Float, y: Float, white: Boolean) {
        val r = RectF(x, y, x + 46f, y + 46f)
        g.paint.style = Paint.Style.FILL
        g.paint.color = if (white) Color.WHITE else Color.parseColor("#020617")
        g.cv.drawRoundRect(r, 8f, 8f, g.paint)
        g.paint.style = Paint.Style.STROKE
        g.paint.strokeWidth = 3f
        g.paint.color = MUTED
        g.cv.drawRoundRect(r, 8f, 8f, g.paint)
        g.paint.style = Paint.Style.FILL
    }

    // ---------------------------------------------------------------- Solution

    private val ARTICLE = arrayOf("", "Le pion", "Le cavalier", "Le fou", "La tour", "La dame", "Le roi")
    private val OBJECT = arrayOf("", "le pion", "le cavalier", "le fou", "la tour", "la dame", "le roi")
    private val PROMO = arrayOf("", "", "cavalier", "fou", "tour", "dame", "")

    /** Phrase décrivant un coup : pièce, prise éventuelle, case, promotion, échec ou mat. */
    fun describe(p: Position, m: Move, san: String): String {
        val sb = StringBuilder()
        val type = pieceType(p[m.from])
        when {
            p.isCastle(m) -> sb.append(if (fileOf(m.to) == 6) "Petit roque" else "Grand roque")
            p.isEnPassant(m) -> sb.append("Le pion capture le pion en passant, en ${sqName(m.to)}")
            p.isCapture(m) -> sb.append("${ARTICLE[type]} capture ${OBJECT[pieceType(p[m.to])]} en ${sqName(m.to)}")
            else -> sb.append("${ARTICLE[type]} va en ${sqName(m.to)}")
        }
        if (m.promo != 0) sb.append(" et se promeut en ${PROMO[m.promo]}")
        sb.append(when {
            san.endsWith("#") -> " : échec et mat !"
            san.endsWith("+") -> ", avec échec."
            else -> "."
        })
        return sb.toString()
    }

    private class Step(val before: Position, val move: Move, val san: String, val prev: Move?, val reply: Move?, val replySan: String?)

    /**
     * Solution illustrée : une grille par coup du joueur. Flèche verte = coup à jouer,
     * flèche rouge = réponse adverse, badges ✕ (prise), + (échec), # (mat) et pièce capturée en légende.
     */
    fun renderSolution(context: Context, puzzle: Puzzle, chosen: String = ""): Bitmap {
        val line = Line.fromUci(puzzle.fen, puzzle.moves)
        val playerWhite = line.positions[1].whiteToMove
        val steps = (1 until line.size step 2).map { i ->
            Step(line.positions[i], line.moves[i], line.sans[i], line.moves[i - 1], line.moves.getOrNull(i + 1), line.sans.getOrNull(i + 1))
        }
        val single = steps.size == 1
        val cols = if (single) 1 else 2
        val side = if (single) 984f else 492f
        val gap = 24f
        val textSize = if (single) 38f else 27f
        val lineH = textSize * 1.3f
        val capW = side
        val probe = Painter(context, Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)))
        // Légendes de chaque étape, calculées d'abord pour connaître la hauteur des rangées.
        val captions = steps.mapIndexed { k, st ->
            val head = "${k + 1}. ${st.san}"
            val body = probe.wrap(describe(st.before, st.move, st.san), textSize, capW)
            val tail = when {
                st.replySan != null && st.reply != null -> {
                    val after = st.before.play(st.move)
                    val d = describe(after, st.reply, st.replySan)
                    probe.wrap("Réponse adverse : ${st.replySan} — ${d.replaceFirstChar { it.lowercase() }}", textSize, capW)
                }
                st.san.endsWith("#") -> listOf("Échec et mat : problème résolu.")
                else -> listOf("Problème résolu : l'avantage est décisif.")
            }
            Triple(head, body, tail)
        }
        val rows = (steps.size + cols - 1) / cols
        val rowHeights = (0 until rows).map { r ->
            val lines = (0 until cols).mapNotNull { captions.getOrNull(r * cols + it) }.maxOf { it.second.size + it.third.size }
            side + textSize * 1.9f + lines * lineH + 34f
        }
        val top = 290f
        val legendH = 120f
        val h = (top + rowHeights.sum() + legendH + 50f).toInt()
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val g = Painter(context, Canvas(bmp))
        g.background(h)
        g.header("Solution du problème  ·  Lichess #${puzzle.id}")
        val theme = PuzzleThemes.shown(puzzle.themes, chosen)
        turnBox(g, 48f, 208f, playerWhite)
        g.text((if (playerWhite) "Trait aux Blancs" else "Trait aux Noirs") + "  ·  ${theme.name}  ·  ${puzzle.rating}", 110f, 246f, 38f, TEXT, bold = true)

        var y = top
        for (r in 0 until rows) {
            val inRow = (0 until cols).filter { r * cols + it < steps.size }
            val rowW = inRow.size * side + (inRow.size - 1) * gap
            var x = (W - rowW) / 2
            for (cIdx in inRow) {
                val k = r * cols + cIdx
                val st = steps[k]
                val at = g.board(x, y, side, st.before, !playerWhite, st.prev)
                val s = side / 8
                st.reply?.let { g.arrow(it, at, s, RED, 0.10f) }
                g.arrow(st.move, at, s, GREEN, 0.17f)
                // Badges sur la case d'arrivée : prise, échec, mat.
                val (dx, dy) = at(st.move.to)
                val labels = ArrayList<Pair<String, Int>>()
                if (st.before.isCapture(st.move)) labels.add("✕" to Color.parseColor("#475569"))
                if (st.san.endsWith("#")) labels.add("#" to RED) else if (st.san.endsWith("+")) labels.add("+" to GOLD)
                labels.forEachIndexed { i, (label, color) ->
                    g.badge(label, dx + s * (if (i == 0) 0.80f else 0.20f), dy + s * 0.20f, s * 0.20f, color)
                }
                // Légende de l'étape
                val (head, body, tail) = captions[k]
                var ty = y + side + textSize * 1.35f
                g.text(head, x, ty, textSize * 1.25f, GREEN, bold = true)
                // Pièce capturée, barrée d'une croix
                val victim = when {
                    st.before.isEnPassant(st.move) -> PAWN + (if (isWhite(st.before[st.move.from])) 8 else 0)
                    else -> st.before[st.move.to]
                }
                if (victim != 0) {
                    val ps = textSize * 1.7f
                    val px = x + side - ps
                    val py = ty - ps * 0.82f
                    g.paint.color = LIGHT
                    g.cv.drawRoundRect(RectF(px, py, px + ps, py + ps), 8f, 8f, g.paint)
                    g.cv.drawBitmap(g.piece(victim), null, RectF(px, py, px + ps, py + ps), g.paint)
                    g.badge("✕", px + ps, py, ps * 0.24f, RED)
                    g.text("pris", px - 10f, ty, textSize * 0.9f, MUTED, align = Paint.Align.RIGHT)
                }
                ty += textSize * 0.55f
                for (l in body) { ty += lineH; g.text(l, x, ty, textSize, Color.parseColor("#E2E8F0")) }
                for (l in tail) { ty += lineH; g.text(l, x, ty, textSize, if (st.replySan != null) Color.parseColor("#FCA5A5") else GOLD) }
                x += side + gap
            }
            y += rowHeights[r]
        }
        // Légende des symboles
        val ly = y + 30f
        g.paint.style = Paint.Style.FILL
        g.paint.color = GREEN; g.cv.drawRoundRect(RectF(48f, ly - 16f, 108f, ly - 2f), 7f, 7f, g.paint)
        g.text("votre coup", 120f, ly, 28f, MUTED)
        g.paint.color = RED; g.cv.drawRoundRect(RectF(290f, ly - 13f, 350f, ly - 5f), 4f, 4f, g.paint)
        g.text("réponse adverse", 362f, ly, 28f, MUTED)
        g.text("✕ prise    + échec    # échec et mat", 48f, ly + 44f, 28f, MUTED)
        g.footer(h)
        return bmp
    }

    // ---------------------------------------------------------------- Envoi

    private fun send(context: Context, bmp: Bitmap, name: String, text: String, chooser: String) {
        runCatching {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, name)
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooser).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Enregistre l'image dans la galerie (Images/ChessDuck). Renvoie un message de confirmation, ou null en cas d'échec.
     * Android 10+ : MediaStore, sans aucune permission. Versions antérieures : dossier d'images de l'application.
     */
    private fun save(context: Context, bmp: Bitmap, name: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ChessDuck")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return null
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Image enregistrée dans la galerie (Images/ChessDuck)."
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ChessDuck").apply { mkdirs() }
            val file = File(dir, name)
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            "Image enregistrée : ${file.absolutePath}"
        }
    }.getOrNull()

    fun saveProblem(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?, chosen: String = ""): String? =
        save(context, render(context, puzzle, position, playerWhite, lastMove, chosen), "chessduck-${puzzle.id}.png")

    fun saveSolution(context: Context, puzzle: Puzzle, chosen: String = ""): String? =
        save(context, renderSolution(context, puzzle, chosen), "chessduck-${puzzle.id}-solution.png")

    fun share(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?, chosen: String = "") =
        send(
            context, render(context, puzzle, position, playerWhite, lastMove, chosen), "chessduck-${puzzle.id}.png",
            "Saurez-vous résoudre ce problème ? ${if (playerWhite) "Trait aux Blancs" else "Trait aux Noirs"} — ChessDuck", "Partager le problème",
        )

    fun shareSolution(context: Context, puzzle: Puzzle, chosen: String = "") =
        send(context, renderSolution(context, puzzle, chosen), "chessduck-${puzzle.id}-solution.png", "Solution du problème Lichess #${puzzle.id} — ChessDuck", "Partager la solution")
}
