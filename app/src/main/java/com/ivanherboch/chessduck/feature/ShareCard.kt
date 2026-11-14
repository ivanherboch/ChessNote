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
import android.graphics.RadialGradient
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
 * Style « Ardoise & Or » de l'application : sobre, sans publicité, centré sur le défi à relever.
 * Les pièces sont exactement celles de l'échiquier en direct.
 */
object ShareCard {
    private const val W = 1080
    private const val M = 56f
    private val LIGHT = Color.parseColor("#F0D9B5")
    private val DARK = Color.parseColor("#B58863")
    private val TEXT = Color.parseColor("#F8FAFC")
    private val BODY = Color.parseColor("#E2E8F0")
    private val SOFT = Color.parseColor("#CBD5E1")
    private val MUTED = Color.parseColor("#94A3B8")
    private val FAINT = Color.parseColor("#64748B")
    private val SURFACE = Color.parseColor("#1A2536")
    private val OUTLINE = Color.parseColor("#334155")
    private val GOLD = Color.parseColor("#F59E0B")
    private val GOLD_TEXT = Color.parseColor("#FBBF24")
    private val GREEN = Color.parseColor("#10B981")
    private val GREEN_TEXT = Color.parseColor("#34D399")
    private val RED = Color.parseColor("#EF4444")
    private val RED_TEXT = Color.parseColor("#FCA5A5")
    private val BOLD: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    private class Painter(val context: Context, val cv: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val pieces = HashMap<Int, Bitmap>()
        fun piece(p: Int): Bitmap = pieces.getOrPut(p) { BitmapFactory.decodeResource(context.resources, PIECE_RES.getValue(p)) }

        fun text(s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f) {
            paint.style = Paint.Style.FILL
            paint.typeface = if (bold) BOLD else Typeface.DEFAULT
            paint.textSize = size
            paint.color = color
            paint.textAlign = align
            paint.letterSpacing = spacing
            cv.drawText(s, x, y, paint)
            paint.textAlign = Paint.Align.LEFT
            paint.letterSpacing = 0f
        }

        fun width(s: String, size: Float, bold: Boolean = false, spacing: Float = 0f): Float {
            paint.typeface = if (bold) BOLD else Typeface.DEFAULT
            paint.textSize = size
            paint.letterSpacing = spacing
            return paint.measureText(s).also { paint.letterSpacing = 0f }
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

        /** Fond ardoise, motif d'échiquier à peine visible et halo doré derrière le titre. */
        fun background(h: Int) {
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.parseColor("#1E293B"), Color.parseColor("#0B1220"), Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, W.toFloat(), h.toFloat(), paint)
            paint.shader = null
            val s = 72f
            paint.color = Color.argb(9, 255, 255, 255)
            var row = 0
            var y = 0f
            while (y < h) {
                var x = if (row % 2 == 0) 0f else s
                while (x < W) { cv.drawRect(x, y, x + s, y + s, paint); x += 2 * s }
                y += s; row++
            }
            paint.shader = RadialGradient(W * 0.5f, 110f, 720f, Color.argb(44, 245, 158, 11), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, W.toFloat(), h.toFloat(), paint)
            paint.shader = null
        }

        /** Sur-titre en petites capitales espacées (ex. « PROBLÈME D'ÉCHECS »). */
        fun eyebrow(label: String, y: Float, color: Int) = text(label.uppercase(), M, y, 30f, color, bold = true, spacing = 0.14f)

        /** Difficulté : tranche, classement et 4 points dorés, alignés à droite. */
        fun difficulty(rating: Int, right: Float, y: Float) {
            val band = PuzzleThemes.bandOf(rating)
            val r = 9f
            var x = right - r
            paint.style = Paint.Style.FILL
            for (i in 4 downTo 1) {
                paint.color = if (i <= band) GOLD else OUTLINE
                cv.drawCircle(x, y - 11f, r, paint)
                x -= 2 * r + 8f
            }
            text("${PuzzleThemes.BANDS[band]} · $rating", x - 6f, y, 30f, SOFT, align = Paint.Align.RIGHT)
        }

        /** Pastille ronde du camp au trait (comme dans l'application). */
        fun sideDot(cx: Float, cy: Float, r: Float, white: Boolean) {
            paint.style = Paint.Style.FILL
            paint.color = if (white) Color.WHITE else Color.parseColor("#020617")
            cv.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = MUTED
            cv.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.FILL
        }

        /** Pastille contour (ex. « 2 coups à trouver ») ; renvoie sa largeur. */
        fun chip(label: String, x: Float, y: Float, color: Int): Float {
            val size = 30f
            val w = width(label, size, bold = true) + 44f
            val r = RectF(x, y, x + w, y + 56f)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(30, Color.red(color), Color.green(color), Color.blue(color))
            cv.drawRoundRect(r, 28f, 28f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = Color.argb(150, Color.red(color), Color.green(color), Color.blue(color))
            cv.drawRoundRect(r, 28f, 28f, paint)
            text(label, x + 22f, y + 39f, size, color, bold = true)
            return w
        }

        fun panel(r: RectF, accent: Int? = null) {
            paint.style = Paint.Style.FILL
            paint.color = SURFACE
            paint.setShadowLayer(18f, 0f, 6f, Color.argb(90, 0, 0, 0))
            cv.drawRoundRect(r, 28f, 28f, paint)
            paint.clearShadowLayer()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb(28, 255, 255, 255)
            cv.drawRoundRect(r, 28f, 28f, paint)
            paint.style = Paint.Style.FILL
            if (accent != null) {
                cv.save()
                cv.clipPath(Path().apply { addRoundRect(r, 28f, 28f, Path.Direction.CW) })
                paint.color = accent
                cv.drawRect(r.left, r.top, r.left + 8f, r.bottom, paint)
                cv.restore()
            }
        }

        /** Pied de page discret : mascotte, nom et référence Lichess ; aucune URL ni appel à installer. */
        fun footer(h: Int, id: String) {
            val y = h - 46f
            paint.color = Color.argb(30, 255, 255, 255)
            cv.drawRect(M, h - 104f, W - M, h - 102f, paint)
            val mascot = BitmapFactory.decodeResource(context.resources, R.drawable.mascot)
            paint.color = Color.WHITE
            cv.drawBitmap(mascot, null, RectF(M, y - 44f, M + 56f, y + 12f), paint)
            text("ChessDuck", M + 68f, y, 30f, SOFT, bold = true)
            text("Lichess #$id", M + 68f + width("ChessDuck", 30f, bold = true) + 20f, y, 28f, FAINT)
            text("© Ivan Herboch, 2026", W - M, y, 26f, FAINT, align = Paint.Align.RIGHT)
        }

        /**
         * Échiquier aux coins arrondis, avec ombre portée ; [glow] fait rayonner une case (roi maté).
         * Renvoie la fonction donnant le coin supérieur gauche d'une case.
         */
        fun board(left: Float, top: Float, side: Float, position: Position, flipped: Boolean, lastMove: Move?, glow: Int = -1): (Int) -> Pair<Float, Float> {
            val s = side / 8
            val radius = side * 0.018f
            fun x(q: Int) = left + (if (flipped) 7 - fileOf(q) else fileOf(q)) * s
            fun y(q: Int) = top + (if (flipped) rankOf(q) else 7 - rankOf(q)) * s
            val rect = RectF(left, top, left + side, top + side)
            paint.style = Paint.Style.FILL
            paint.color = DARK
            paint.setShadowLayer(side * 0.03f, 0f, side * 0.012f, Color.argb(150, 0, 0, 0))
            cv.drawRoundRect(rect, radius, radius, paint)
            paint.clearShadowLayer()
            cv.save()
            cv.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
            for (q in 0 until 64) {
                paint.color = if ((fileOf(q) + rankOf(q)) % 2 == 1) LIGHT else DARK
                cv.drawRect(x(q), y(q), x(q) + s, y(q) + s, paint)
            }
            lastMove?.let {
                paint.color = Color.argb(120, 247, 236, 94)
                cv.drawRect(x(it.from), y(it.from), x(it.from) + s, y(it.from) + s, paint)
                cv.drawRect(x(it.to), y(it.to), x(it.to) + s, y(it.to) + s, paint)
            }
            if (glow >= 0) {
                val cx = x(glow) + s / 2
                val cy = y(glow) + s / 2
                paint.shader = RadialGradient(cx, cy, s * 0.8f, intArrayOf(Color.argb(230, 239, 68, 68), Color.argb(120, 239, 68, 68), Color.TRANSPARENT), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                cv.drawRect(cx - s, cy - s, cx + s, cy + s, paint)
                paint.shader = null
            }
            paint.typeface = BOLD
            paint.textSize = s * 0.2f
            for (i in 0..7) {
                val fq = if (flipped) sq(7 - i, 7) else sq(i, 0)
                paint.color = if ((fileOf(fq) + rankOf(fq)) % 2 == 1) DARK else LIGHT
                cv.drawText(('a' + fileOf(fq)).toString(), x(fq) + s * 0.80f, y(fq) + s * 0.92f, paint)
                val rq = if (flipped) sq(7, 7 - i) else sq(0, i)
                paint.color = if ((fileOf(rq) + rankOf(rq)) % 2 == 1) DARK else LIGHT
                cv.drawText(('1' + rankOf(rq)).toString(), x(rq) + s * 0.07f, y(rq) + s * 0.25f, paint)
            }
            for (q in 0 until 64) {
                val p = position[q]
                if (p != 0) cv.drawBitmap(piece(p), null, RectF(x(q), y(q), x(q) + s, y(q) + s), paint)
            }
            cv.restore()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.argb(50, 255, 255, 255)
            cv.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
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
            paint.alpha = 225
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s * widthFactor
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.setShadowLayer(s * 0.06f, 0f, s * 0.02f, Color.argb(90, 0, 0, 0))
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
            paint.clearShadowLayer()
            paint.alpha = 255
        }

        fun badge(label: String, cx: Float, cy: Float, r: Float, color: Int, textColor: Int = Color.WHITE) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#55000000")
            cv.drawCircle(cx, cy + r * 0.12f, r * 1.08f, paint)
            paint.color = color
            cv.drawCircle(cx, cy, r, paint)
            paint.color = textColor
            paint.typeface = BOLD
            paint.textSize = r * 1.3f
            paint.textAlign = Paint.Align.CENTER
            cv.drawText(label, cx, cy + r * 0.46f, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        /** Pastille pleine de conclusion (« Échec et mat ! »). */
        fun pill(label: String, x: Float, y: Float, size: Float, bg: Int, fg: Int): Float {
            val w = width(label, size, bold = true) + size * 1.4f
            paint.style = Paint.Style.FILL
            paint.color = bg
            cv.drawRoundRect(RectF(x, y, x + w, y + size * 1.7f), size * 0.85f, size * 0.85f, paint)
            text(label, x + size * 0.7f, y + size * 1.2f, size, fg, bold = true)
            return w
        }
    }

    // ---------------------------------------------------------------- Défi commun

    /** Nombre de coups du joueur dans la solution. */
    private fun playerMoves(line: Line) = (line.size / 2).coerceAtLeast(1)

    /** Énoncé classique d'un problème : « Les Blancs jouent et font mat en 2 ». */
    fun challenge(puzzle: Puzzle, line: Line, playerWhite: Boolean): String {
        val who = if (playerWhite) "Les Blancs" else "Les Noirs"
        val mate = line.sans.lastOrNull()?.endsWith("#") == true
        return when {
            mate -> "$who jouent et font mat en\u00A0${playerMoves(line)}"
            " equality " in " ${puzzle.themes} " -> "$who jouent et sauvent la partie"
            else -> "$who jouent et gagnent"
        }
    }

    private fun coups(n: Int) = if (n > 1) "$n coups" else "1 coup"

    /** Titre sur une ligne si possible (taille réduite jusqu'à 48 px), sinon sur plusieurs lignes. */
    private fun fitTitle(p: Painter, text: String, max: Float): Pair<List<String>, Float> {
        val width = W - 2 * M - 60f
        var size = max
        while (size > 48f && p.width(text, size, bold = true) > width) size -= 2f
        return p.wrap(text, size, width, bold = true) to size
    }

    /** En-tête commun énoncé / solution ; renvoie l'ordonnée sous le bloc. */
    private fun header(g: Painter, eyebrow: String, eyebrowColor: Int, puzzle: Puzzle, title: List<String>, titleSize: Float, sub: String, playerWhite: Boolean): Float {
        g.eyebrow(eyebrow, 96f, eyebrowColor)
        g.difficulty(puzzle.rating, W - M, 96f)
        var y = 96f + titleSize * 1.45f
        g.sideDot(M + 20f, y - titleSize * 0.36f, 20f, playerWhite)
        for (l in title) { g.text(l, M + 60f, y, titleSize, TEXT, bold = true); y += titleSize * 1.18f }
        y += 4f
        g.text(sub, M, y, 36f, MUTED)
        return y + 44f
    }

    // ---------------------------------------------------------------- Énoncé

    fun render(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?): Bitmap {
        val line = Line.fromUci(puzzle.fen, puzzle.moves)
        val theme = PuzzleThemes.primary(puzzle.themes)
        val mate = line.sans.lastOrNull()?.endsWith("#") == true
        val n = playerMoves(line)
        val probe = Painter(context, Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)))
        val (title, titleSize) = fitTitle(probe, challenge(puzzle, line, playerWhite), 62f)
        val sub = when {
            mate && n == 1 -> "Un seul coup suffit. Le voyez-vous ?"
            mate -> "Le mat est forcé. Saurez-vous le trouver ?"
            else -> "Saurez-vous trouver le coup gagnant ?"
        }
        val tipW = W - 2 * M - 64f
        val tip = if (theme.tip.isNotEmpty()) probe.wrap(theme.tip, 32f, tipW) else emptyList()
        val side = W - 2 * M
        val headH = 96f + titleSize * 1.45f + (title.size - 1) * titleSize * 1.18f + titleSize * 1.18f + 4f + 44f
        val top = headH + 16f
        val chipsY = top + side + 40f
        val tipTop = chipsY + 56f + 28f
        val tipH = if (tip.isEmpty()) 0f else 30f + 40f + tip.size * 42f + 22f
        val h = (tipTop + tipH + (if (tip.isEmpty()) 0f else 40f) + 104f).toInt()

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val g = Painter(context, Canvas(bmp))
        g.background(h)
        header(g, "Problème d'échecs", GOLD_TEXT, puzzle, title, titleSize, sub, playerWhite)
        g.board(M, top, side, position, !playerWhite, lastMove)

        // Pastilles : objectif chiffré et thème (le thème « Mat en N » est déjà dans le titre).
        var x = M
        x += g.chip(if (mate) "Mat en ${coups(n)}" else "${coups(n)} à trouver", x, chipsY, GREEN_TEXT) + 14f
        if (!mate) g.chip("Thème : ${theme.name}", x, chipsY, Color.parseColor("#7DD3FC"))

        if (tip.isNotEmpty()) {
            g.panel(RectF(M, tipTop, W - M, tipTop + tipH), accent = GOLD)
            g.text("Indice", M + 32f, tipTop + 30f + 28f, 30f, GOLD_TEXT, bold = true, spacing = 0.04f)
            var y = tipTop + 30f + 40f + 30f
            for (l in tip) { g.text(l, M + 32f, y, 32f, BODY); y += 42f }
        }
        g.footer(h, puzzle.id)
        return bmp
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

    private class Caption(val body: List<String>, val reply: List<String>, val final: String?)

    /**
     * Solution illustrée : une carte par coup du joueur. Flèche verte = coup à jouer,
     * flèche rouge = réponse adverse, badges + (échec) et # (mat), roi maté mis en lumière.
     */
    fun renderSolution(context: Context, puzzle: Puzzle): Bitmap {
        val line = Line.fromUci(puzzle.fen, puzzle.moves)
        val playerWhite = line.positions[1].whiteToMove
        val steps = (1 until line.size step 2).map { i ->
            Step(line.positions[i], line.moves[i], line.sans[i], line.moves[i - 1], line.moves.getOrNull(i + 1), line.sans.getOrNull(i + 1))
        }
        val theme = PuzzleThemes.primary(puzzle.themes)
        val mate = line.sans.lastOrNull()?.endsWith("#") == true
        val single = steps.size == 1
        val cols = if (single) 1 else 2
        val gap = 24f
        val margin = 40f
        val pad = if (single) 24f else 16f
        val panelW = if (single) W - 2 * margin else (W - 2 * margin - gap) / 2
        val side = panelW - 2 * pad
        val titleSz = if (single) 44f else 34f
        val textSize = if (single) 34f else 26f
        val lineH = textSize * 1.32f
        val probe = Painter(context, Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)))
        val captions = steps.map { st ->
            val body = probe.wrap(describe(st.before, st.move, st.san), textSize, side)
            if (st.replySan != null && st.reply != null) {
                val d = describe(st.before.play(st.move), st.reply, st.replySan)
                Caption(body, probe.wrap("Réponse : ${st.replySan} — ${d.replaceFirstChar { it.lowercase() }}", textSize, side - textSize * 0.8f), null)
            } else {
                Caption(body, emptyList(), if (st.san.endsWith("#")) "Échec et mat !" else "Avantage décisif")
            }
        }
        fun panelH(c: Caption) = pad + side + 22f + titleSz * 1.2f + 10f + (c.body.size + c.reply.size) * lineH +
            (if (c.final != null) 16f + textSize * 1.7f else 0f) + pad + 8f
        val rows = (steps.size + cols - 1) / cols
        val rowHeights = (0 until rows).map { r -> (0 until cols).mapNotNull { captions.getOrNull(r * cols + it) }.maxOf { panelH(it) } }

        val (title, titleSize) = fitTitle(probe, challenge(puzzle, line, playerWhite), 60f)
        val sub = "Solution en ${coups(steps.size)}" + if (mate) "" else "  ·  Thème : ${theme.name}"
        val headH = 96f + titleSize * 1.45f + (title.size - 1) * titleSize * 1.18f + titleSize * 1.18f + 4f + 44f
        val top = headH + 12f
        val legendH = 96f
        val h = (top + rowHeights.sum() + (rows - 1) * gap + legendH + 104f).toInt()
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val g = Painter(context, Canvas(bmp))
        g.background(h)
        header(g, "Solution", GREEN_TEXT, puzzle, title, titleSize, sub, playerWhite)

        var y = top
        for (r in 0 until rows) {
            val inRow = (0 until cols).filter { r * cols + it < steps.size }
            val rowW = inRow.size * panelW + (inRow.size - 1) * gap
            var x = (W - rowW) / 2
            for (cIdx in inRow) {
                val k = r * cols + cIdx
                val st = steps[k]
                val cap = captions[k]
                val last = k == steps.size - 1
                g.panel(RectF(x, y, x + panelW, y + rowHeights[r]))
                val bx = x + pad
                val by = y + pad
                val glow = if (last && st.san.endsWith("#")) {
                    val after = st.before.play(st.move)
                    (0 until 64).firstOrNull { q -> after[q] != 0 && pieceType(after[q]) == KING && isWhite(after[q]) != playerWhite } ?: -1
                } else -1
                val at = g.board(bx, by, side, st.before, !playerWhite, st.prev, glow)
                val s = side / 8
                st.reply?.let { g.arrow(it, at, s, RED, 0.10f) }
                g.arrow(st.move, at, s, GREEN, 0.17f)
                val (dx, dy) = at(st.move.to)
                when {
                    st.san.endsWith("#") -> g.badge("#", dx + s * 0.80f, dy + s * 0.20f, s * 0.20f, RED)
                    st.san.endsWith("+") -> g.badge("+", dx + s * 0.80f, dy + s * 0.20f, s * 0.20f, GOLD, Color.parseColor("#1C1917"))
                }
                // Titre de l'étape : numéro, coup, pièce prise.
                var ty = by + side + 22f + titleSz * 0.95f
                val br = titleSz * 0.52f
                g.badge("${k + 1}", bx + br, ty - titleSz * 0.34f, br, GREEN)
                g.text(st.san, bx + 2 * br + 14f, ty, titleSz, TEXT, bold = true)
                val victim = when {
                    st.before.isEnPassant(st.move) -> PAWN + (if (isWhite(st.before[st.move.from])) 8 else 0)
                    else -> st.before[st.move.to]
                }
                if (victim != 0) {
                    val ps = titleSz * 1.15f
                    val px = bx + side - ps
                    val py = ty - ps * 0.82f
                    g.paint.style = Paint.Style.FILL
                    g.paint.color = LIGHT
                    g.cv.drawRoundRect(RectF(px, py, px + ps, py + ps), 10f, 10f, g.paint)
                    g.cv.drawBitmap(g.piece(victim), null, RectF(px, py, px + ps, py + ps), g.paint)
                    g.text("prend", px - 12f, ty - titleSz * 0.12f, textSize * 0.9f, MUTED, align = Paint.Align.RIGHT)
                }
                ty += titleSz * 0.25f + 10f
                for (l in cap.body) { ty += lineH; g.text(l, bx, ty, textSize, BODY) }
                cap.reply.forEachIndexed { i, l ->
                    ty += lineH
                    if (i == 0) { g.paint.color = RED; g.cv.drawCircle(bx + textSize * 0.25f, ty - textSize * 0.34f, textSize * 0.2f, g.paint) }
                    g.text(l, bx + textSize * 0.8f, ty, textSize, RED_TEXT)
                }
                if (cap.final != null) {
                    ty += 16f
                    if (cap.final.startsWith("Échec")) g.pill(cap.final, bx, ty, textSize, GOLD, Color.parseColor("#1C1917"))
                    else g.pill(cap.final, bx, ty, textSize, GREEN, Color.parseColor("#04221A"))
                }
                x += panelW + gap
            }
            y += rowHeights[r] + gap
        }
        // Légende des symboles
        val ly = y + 34f
        g.paint.style = Paint.Style.FILL
        g.paint.color = GREEN; g.cv.drawRoundRect(RectF(M, ly - 17f, M + 56f, ly - 3f), 7f, 7f, g.paint)
        g.text("votre coup", M + 68f, ly, 28f, MUTED)
        g.paint.color = RED; g.cv.drawRoundRect(RectF(M + 260f, ly - 14f, M + 316f, ly - 6f), 4f, 4f, g.paint)
        g.text("réponse adverse", M + 328f, ly, 28f, MUTED)
        g.badge("+", M + 640f, ly - 10f, 15f, GOLD, Color.parseColor("#1C1917"))
        g.text("échec", M + 664f, ly, 28f, MUTED)
        g.badge("#", M + 780f, ly - 10f, 15f, RED)
        g.text("mat", M + 804f, ly, 28f, MUTED)
        g.footer(h, puzzle.id)
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

    fun saveProblem(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?): String? =
        save(context, render(context, puzzle, position, playerWhite, lastMove), "chessduck-${puzzle.id}.png")

    fun saveSolution(context: Context, puzzle: Puzzle): String? =
        save(context, renderSolution(context, puzzle), "chessduck-${puzzle.id}-solution.png")

    fun share(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?) =
        send(
            context, render(context, puzzle, position, playerWhite, lastMove), "chessduck-${puzzle.id}.png",
            Line.fromUci(puzzle.fen, puzzle.moves).let { challenge(puzzle, it, playerWhite) } + ". Saurez-vous trouver la solution ?", "Partager le problème",
        )

    fun shareSolution(context: Context, puzzle: Puzzle) =
        send(context, renderSolution(context, puzzle), "chessduck-${puzzle.id}-solution.png", "Solution du problème Lichess #${puzzle.id}", "Partager la solution")
}
