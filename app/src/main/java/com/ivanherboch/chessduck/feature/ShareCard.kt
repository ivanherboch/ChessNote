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

/** Format de l'image : portrait 4:5 (WhatsApp, fil Instagram) ou carré 1:1 (publication Instagram). */
enum class ShareFormat(val height: Int, val label: String) { PORTRAIT(1350, "Portrait 4:5"), SQUARE(1080, "Carré 1:1") }

/** Réglages d'une image partagée : format et fond clair ou sombre. */
data class ShareStyle(val format: ShareFormat = ShareFormat.PORTRAIT, val light: Boolean = false) {
    companion object {
        private const val FILE = "share_style"
        fun load(context: Context): ShareStyle {
            val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return ShareStyle(
                runCatching { ShareFormat.valueOf(p.getString("format", null) ?: "") }.getOrDefault(ShareFormat.PORTRAIT),
                p.getBoolean("light", false),
            )
        }
        fun save(context: Context, s: ShareStyle) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("format", s.format.name).putBoolean("light", s.light).apply()
        }
    }
}

/**
 * Cartes de partage PNG : l'énoncé d'un problème, ou sa solution illustrée étape par étape.
 * Style « Ardoise & Or » de l'application, en version sombre ou claire : sobre, sans publicité, centré sur le défi.
 * Dimensions fixes (1080 × 1350 ou 1080 × 1080) : WhatsApp et Instagram les affichent sans recadrage ni réduction,
 * et tout le texte reste lisible après compression. Les pièces sont exactement celles de l'échiquier en direct.
 */
object ShareCard {
    private const val W = 1080
    private const val M = 56f
    private val LIGHT_SQ = Color.parseColor("#F0D9B5")
    private val DARK_SQ = Color.parseColor("#B58863")
    private val GOLD = Color.parseColor("#F59E0B")
    private val GREEN = Color.parseColor("#10B981")
    private val RED = Color.parseColor("#EF4444")
    private val INK = Color.parseColor("#1C1917")
    private val BOLD: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    /** Palette d'une image, déclinée des jetons Nuit et Jour de l'application (contrastes ≥ 4,5:1). */
    private class Pal(
        val light: Boolean,
        val bgTop: Int, val bgBottom: Int, val texture: Int, val halo: Int,
        val text: Int, val body: Int, val soft: Int, val muted: Int, val faint: Int,
        val surface: Int, val stroke: Int, val dotOff: Int, val shadow: Int,
        val goldText: Int, val greenText: Int, val greenFill: Int, val onGreen: Int, val redText: Int, val blueText: Int,
    )

    private val NIGHT = Pal(
        false, Color.parseColor("#1E293B"), Color.parseColor("#0B1220"), Color.argb(9, 255, 255, 255), Color.argb(44, 245, 158, 11),
        Color.parseColor("#F8FAFC"), Color.parseColor("#E2E8F0"), Color.parseColor("#CBD5E1"), Color.parseColor("#94A3B8"), Color.parseColor("#64748B"),
        Color.parseColor("#1A2536"), Color.argb(28, 255, 255, 255), Color.parseColor("#334155"), Color.argb(140, 0, 0, 0),
        Color.parseColor("#FBBF24"), Color.parseColor("#34D399"), GREEN, Color.parseColor("#04221A"), Color.parseColor("#FCA5A5"), Color.parseColor("#7DD3FC"),
    )

    private val DAY = Pal(
        true, Color.parseColor("#FFFFFF"), Color.parseColor("#E8EDF3"), Color.argb(10, 15, 23, 42), Color.argb(46, 245, 158, 11),
        Color.parseColor("#0F172A"), Color.parseColor("#1E293B"), Color.parseColor("#334155"), Color.parseColor("#475569"), Color.parseColor("#64748B"),
        Color.WHITE, Color.argb(26, 15, 23, 42), Color.parseColor("#CBD5E1"), Color.argb(60, 15, 23, 42),
        Color.parseColor("#92400E"), Color.parseColor("#065F46"), Color.parseColor("#047857"), Color.WHITE, Color.parseColor("#B91C1C"), Color.parseColor("#075985"),
    )

    private class Painter(val context: Context, val cv: Canvas, val pal: Pal = NIGHT) {
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

        /** Fond dégradé, motif d'échiquier à peine visible et halo doré derrière le titre. */
        fun background(h: Int) {
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), pal.bgTop, pal.bgBottom, Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, W.toFloat(), h.toFloat(), paint)
            paint.shader = null
            val s = 72f
            paint.color = pal.texture
            var row = 0
            var y = 0f
            while (y < h) {
                var x = if (row % 2 == 0) 0f else s
                while (x < W) { cv.drawRect(x, y, x + s, y + s, paint); x += 2 * s }
                y += s; row++
            }
            paint.shader = RadialGradient(W * 0.5f, 100f, 720f, pal.halo, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, W.toFloat(), h.toFloat(), paint)
            paint.shader = null
        }

        /** Difficulté : tranche, classement et 4 points dorés, alignés à droite. */
        fun difficulty(rating: Int, right: Float, y: Float) {
            val band = PuzzleThemes.bandOf(rating)
            val r = 9f
            var x = right - r
            paint.style = Paint.Style.FILL
            for (i in 4 downTo 1) {
                paint.color = if (i <= band) GOLD else pal.dotOff
                cv.drawCircle(x, y - 11f, r, paint)
                x -= 2 * r + 8f
            }
            text("${PuzzleThemes.BANDS[band]} · $rating", x - 6f, y, 30f, pal.soft, align = Paint.Align.RIGHT)
        }

        /** Pastille ronde du camp au trait (comme dans l'application). */
        fun sideDot(cx: Float, cy: Float, r: Float, white: Boolean) {
            paint.style = Paint.Style.FILL
            paint.color = if (white) Color.WHITE else Color.parseColor("#020617")
            cv.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = if (pal.light) Color.parseColor("#475569") else pal.muted
            cv.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.FILL
        }

        /** Pastille contour (ex. « 2 coups à trouver ») ; renvoie sa largeur. */
        fun chip(label: String, x: Float, y: Float, color: Int): Float {
            val size = 30f
            val w = width(label, size, bold = true) + 44f
            val r = RectF(x, y, x + w, y + 56f)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(if (pal.light) 22 else 30, Color.red(color), Color.green(color), Color.blue(color))
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
            paint.color = pal.surface
            paint.setShadowLayer(18f, 0f, 6f, Color.argb(if (pal.light) 40 else 90, 0, 0, 0))
            cv.drawRoundRect(r, 28f, 28f, paint)
            paint.clearShadowLayer()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = pal.stroke
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
            val y = h - 38f
            paint.style = Paint.Style.FILL
            paint.color = pal.stroke
            cv.drawRect(M, h - 92f, W - M, h - 90f, paint)
            val mascot = BitmapFactory.decodeResource(context.resources, R.drawable.mascot)
            paint.color = Color.WHITE
            cv.drawBitmap(mascot, null, RectF(M, y - 42f, M + 52f, y + 10f), paint)
            text("ChessDuck", M + 64f, y, 30f, pal.soft, bold = true)
            text("Lichess #$id", M + 64f + width("ChessDuck", 30f, bold = true) + 20f, y, 28f, pal.faint)
            text("© Ivan Herboch, 2026", W - M, y, 26f, pal.faint, align = Paint.Align.RIGHT)
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
            paint.color = DARK_SQ
            paint.setShadowLayer(side * 0.03f, 0f, side * 0.012f, pal.shadow)
            cv.drawRoundRect(rect, radius, radius, paint)
            paint.clearShadowLayer()
            cv.save()
            cv.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
            for (q in 0 until 64) {
                paint.color = if ((fileOf(q) + rankOf(q)) % 2 == 1) LIGHT_SQ else DARK_SQ
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
            // Coordonnées : seulement si les cases sont assez grandes pour rester lisibles après compression.
            if (s >= 70f) {
                paint.typeface = BOLD
                paint.textSize = s * 0.2f
                for (i in 0..7) {
                    val fq = if (flipped) sq(7 - i, 7) else sq(i, 0)
                    paint.color = if ((fileOf(fq) + rankOf(fq)) % 2 == 1) DARK_SQ else LIGHT_SQ
                    cv.drawText(('a' + fileOf(fq)).toString(), x(fq) + s * 0.80f, y(fq) + s * 0.92f, paint)
                    val rq = if (flipped) sq(7, 7 - i) else sq(0, i)
                    paint.color = if ((fileOf(rq) + rankOf(rq)) % 2 == 1) DARK_SQ else LIGHT_SQ
                    cv.drawText(('1' + rankOf(rq)).toString(), x(rq) + s * 0.07f, y(rq) + s * 0.25f, paint)
                }
            }
            for (q in 0 until 64) {
                val p = position[q]
                if (p != 0) cv.drawBitmap(piece(p), null, RectF(x(q), y(q), x(q) + s, y(q) + s), paint)
            }
            cv.restore()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = if (pal.light) Color.argb(40, 15, 23, 42) else Color.argb(50, 255, 255, 255)
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
            mate -> "$who jouent et font mat en ${playerMoves(line)}"
            " equality " in " ${puzzle.themes} " -> "$who jouent et sauvent la partie"
            else -> "$who jouent et gagnent"
        }
    }

    private fun coups(n: Int) = if (n > 1) "$n coups" else "1 coup"

    private const val EYEBROW_Y = 80f

    /** Titre sur une ligne si possible (taille réduite jusqu'à 46 px), sinon sur plusieurs lignes. */
    private fun fitTitle(p: Painter, text: String, max: Float): Pair<List<String>, Float> {
        val width = W - 2 * M - 60f
        var size = max
        while (size > 46f && p.width(text, size, bold = true) > width) size -= 2f
        return p.wrap(text, size, width, bold = true) to size
    }

    /** Hauteur de l'en-tête (sur-titre, titre, sous-titre éventuel). */
    private fun headerHeight(lines: Int, size: Float, sub: Boolean) =
        EYEBROW_Y + size * 1.35f + (lines - 1) * size * 1.15f + (if (sub) size * 0.2f + 44f else 0f) + 14f

    /** En-tête commun énoncé / solution. */
    private fun header(g: Painter, eyebrow: String, eyebrowColor: Int, puzzle: Puzzle, title: List<String>, size: Float, sub: String?, playerWhite: Boolean) {
        g.text(eyebrow.uppercase(), M, EYEBROW_Y, 30f, eyebrowColor, bold = true, spacing = 0.14f)
        g.difficulty(puzzle.rating, W - M, EYEBROW_Y)
        var y = EYEBROW_Y + size * 1.35f
        g.sideDot(M + 20f, y - size * 0.36f, 20f, playerWhite)
        title.forEachIndexed { i, l ->
            if (i > 0) y += size * 1.15f
            g.text(l, M + 60f, y, size, g.pal.text, bold = true)
        }
        if (sub != null) g.text(sub, M, y + size * 0.2f + 40f, 34f, g.pal.muted)
    }

    // ---------------------------------------------------------------- Énoncé

    fun render(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?, style: ShareStyle = ShareStyle()): Bitmap {
        val pal = if (style.light) DAY else NIGHT
        val square = style.format == ShareFormat.SQUARE
        val h = style.format.height
        val line = Line.fromUci(puzzle.fen, puzzle.moves)
        val theme = PuzzleThemes.primary(puzzle.themes)
        val mate = line.sans.lastOrNull()?.endsWith("#") == true
        val n = playerMoves(line)
        val probe = Painter(context, Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)), pal)
        val (title, titleSize) = fitTitle(probe, challenge(puzzle, line, playerWhite), if (square) 54f else 62f)
        val sub = if (square) null else when {
            mate && n == 1 -> "Un seul coup suffit. Le voyez-vous ?"
            mate -> "Le mat est forcé. Saurez-vous le trouver ?"
            else -> "Saurez-vous trouver le coup gagnant ?"
        }
        // Indice : « Indice : » en doré puis le conseil, dans une carte compacte.
        val tipSize = if (square) 28f else 30f
        val tipLineH = tipSize * 1.34f
        val tipText = if (theme.tip.isNotEmpty()) "Indice : ${theme.tip}" else ""
        val tip = if (tipText.isNotEmpty()) probe.wrap(tipText, tipSize, W - 2 * M - 56f) else emptyList()
        val tipH = if (tip.isEmpty()) 0f else 40f + tip.size * tipLineH
        val chipsH = if (square) 0f else 56f + 22f
        val footerH = 92f
        val top = headerHeight(title.size, titleSize, sub != null) + 22f
        val bottom = chipsH + (if (tip.isEmpty()) 0f else tipH + 22f) + footerH + 8f
        val side = minOf(W - 2 * M, h - top - 28f - bottom)

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val g = Painter(context, Canvas(bmp), pal)
        g.background(h)
        header(g, "Problème d'échecs", pal.goldText, puzzle, title, titleSize, sub, playerWhite)
        g.board((W - side) / 2, top, side, position, !playerWhite, lastMove)

        var y = top + side + 28f
        if (!square) {
            var x = M
            x += g.chip(if (mate) "Mat en ${coups(n)}" else "${coups(n)} à trouver", x, y, pal.greenText) + 14f
            if (!mate) g.chip("Thème : ${theme.name}", x, y, pal.blueText)
            y += chipsH
        }
        if (tip.isNotEmpty()) {
            g.panel(RectF(M, y, W - M, y + tipH), accent = GOLD)
            var ty = y + 20f + tipSize
            tip.forEachIndexed { i, l ->
                if (i == 0 && l.startsWith("Indice :")) {
                    g.text("Indice :", M + 30f, ty, tipSize, pal.goldText, bold = true)
                    g.text(l.removePrefix("Indice :"), M + 30f + g.width("Indice :", tipSize, bold = true), ty, tipSize, pal.body)
                } else g.text(l, M + 30f, ty, tipSize, pal.body)
                ty += tipLineH
            }
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

    /**
     * Solution illustrée, toujours sur une seule image au format choisi : une carte par coup du joueur.
     * Une ou deux étapes : grandes grilles et phrases complètes ; au-delà, grille compacte (notation seule).
     * Flèche verte = coup à jouer, flèche rouge = réponse adverse, badges + et #, roi maté mis en lumière.
     */
    fun renderSolution(context: Context, puzzle: Puzzle, style: ShareStyle = ShareStyle()): Bitmap {
        val pal = if (style.light) DAY else NIGHT
        val square = style.format == ShareFormat.SQUARE
        val h = style.format.height
        val line = Line.fromUci(puzzle.fen, puzzle.moves)
        val playerWhite = line.positions[1].whiteToMove
        val steps = (1 until line.size step 2).map { i ->
            Step(line.positions[i], line.moves[i], line.sans[i], line.moves[i - 1], line.moves.getOrNull(i + 1), line.sans.getOrNull(i + 1))
        }
        val theme = PuzzleThemes.primary(puzzle.themes)
        val mate = line.sans.lastOrNull()?.endsWith("#") == true
        val n = steps.size
        val cols = when { n == 1 -> 1; n <= 4 -> 2; else -> 3 }
        val rows = (n + cols - 1) / cols
        val detailed = rows == 1
        val margin = 40f
        val gap = 20f
        val pad = if (cols == 1) 22f else 16f
        val titleSz = when (cols) { 1 -> 42f; 2 -> 36f; else -> 32f }
        val textSize = when (cols) { 1 -> 32f; 2 -> 27f; else -> 25f }
        val lineH = textSize * 1.3f
        val panelW = (W - 2 * margin - (cols - 1) * gap) / cols
        val textW = panelW - 2 * pad

        val probe = Painter(context, Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)), pal)
        val (title, titleSize) = fitTitle(probe, challenge(puzzle, line, playerWhite), if (square) 52f else 58f)
        val sub = if (square) null else "Solution en ${coups(n)}" + if (mate) "" else "  ·  Thème : ${theme.name}"
        // Légendes : phrases complètes (1 rangée) ou notation seule (grille compacte).
        val bodies = steps.map { st -> if (detailed) probe.wrap(describe(st.before, st.move, st.san), textSize, textW) else emptyList() }
        val replies = steps.map { st ->
            when {
                st.replySan == null || st.reply == null -> emptyList()
                detailed -> probe.wrap("Réponse : ${st.replySan} — ${describe(st.before.play(st.move), st.reply, st.replySan).replaceFirstChar { it.lowercase() }}", textSize, textW - textSize * 0.8f)
                else -> listOf("Réponse : ${st.replySan}")
            }
        }
        val pillH = textSize * 1.7f + 12f
        val captionH = (0 until n).maxOf { k ->
            titleSz * 1.25f + 8f + (bodies[k].size + replies[k].size) * lineH + (if (k == n - 1) pillH else 0f)
        }
        val top = headerHeight(title.size, titleSize, sub != null) + 18f
        val legendH = 62f
        val footerH = 92f
        val gridH = h - top - legendH - footerH - 10f
        val maxPanelH = (gridH - (rows - 1) * gap) / rows
        val side = minOf(textW, maxPanelH - 2 * pad - 14f - captionH).coerceAtLeast(120f)
        // Cartes ajustées à leur contenu, grille centrée verticalement dans l'espace disponible.
        val panelH = minOf(maxPanelH, 2 * pad + side + 14f + captionH + 6f)
        val gridTop = top + ((gridH - rows * panelH - (rows - 1) * gap) / 2).coerceAtLeast(0f)

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val g = Painter(context, Canvas(bmp), pal)
        g.background(h)
        header(g, "Solution", pal.greenText, puzzle, title, titleSize, sub, playerWhite)

        var y = gridTop
        for (r in 0 until rows) {
            val inRow = (0 until cols).filter { r * cols + it < n }
            val rowW = inRow.size * panelW + (inRow.size - 1) * gap
            var x = (W - rowW) / 2
            for (cIdx in inRow) {
                val k = r * cols + cIdx
                val st = steps[k]
                val last = k == n - 1
                g.panel(RectF(x, y, x + panelW, y + panelH))
                val bx = x + pad
                val boardX = x + (panelW - side) / 2
                val by = y + pad
                val glow = if (last && st.san.endsWith("#")) {
                    val after = st.before.play(st.move)
                    (0 until 64).firstOrNull { q -> after[q] != 0 && pieceType(after[q]) == KING && isWhite(after[q]) != playerWhite } ?: -1
                } else -1
                val at = g.board(boardX, by, side, st.before, !playerWhite, st.prev, glow)
                val s = side / 8
                st.reply?.let { g.arrow(it, at, s, RED, 0.10f) }
                g.arrow(st.move, at, s, GREEN, 0.17f)
                val (dx, dy) = at(st.move.to)
                when {
                    st.san.endsWith("#") -> g.badge("#", dx + s * 0.80f, dy + s * 0.20f, s * 0.22f, RED)
                    st.san.endsWith("+") -> g.badge("+", dx + s * 0.80f, dy + s * 0.20f, s * 0.22f, GOLD, INK)
                }
                // Titre de l'étape : numéro, coup, pièce prise.
                var ty = by + side + 14f + titleSz * 0.95f
                val br = titleSz * 0.52f
                g.badge("${k + 1}", bx + br, ty - titleSz * 0.34f, br, pal.greenFill, pal.onGreen)
                g.text(st.san, bx + 2 * br + 12f, ty, titleSz, pal.text, bold = true)
                val victim = when {
                    st.before.isEnPassant(st.move) -> PAWN + (if (isWhite(st.before[st.move.from])) 8 else 0)
                    else -> st.before[st.move.to]
                }
                if (victim != 0) {
                    val ps = titleSz * 1.1f
                    val px = bx + textW - ps
                    val py = ty - ps * 0.8f
                    g.paint.style = Paint.Style.FILL
                    g.paint.color = LIGHT_SQ
                    g.cv.drawRoundRect(RectF(px, py, px + ps, py + ps), 10f, 10f, g.paint)
                    g.cv.drawBitmap(g.piece(victim), null, RectF(px, py, px + ps, py + ps), g.paint)
                    if (cols < 3) g.text("prend", px - 10f, ty - titleSz * 0.12f, textSize * 0.9f, pal.muted, align = Paint.Align.RIGHT)
                }
                ty += titleSz * 0.3f
                for (l in bodies[k]) { ty += lineH; g.text(l, bx, ty, textSize, pal.body) }
                replies[k].forEachIndexed { i, l ->
                    ty += lineH
                    if (i == 0) { g.paint.color = RED; g.cv.drawCircle(bx + textSize * 0.25f, ty - textSize * 0.34f, textSize * 0.2f, g.paint) }
                    g.text(l, bx + textSize * 0.8f, ty, textSize, pal.redText)
                }
                if (last) {
                    ty += 12f
                    if (st.san.endsWith("#")) g.pill("Échec et mat !", bx, ty, textSize, GOLD, INK)
                    else g.pill("Avantage décisif", bx, ty, textSize, pal.greenFill, pal.onGreen)
                }
                x += panelW + gap
            }
            y += panelH + gap
        }
        // Légende des symboles
        val ly = h - footerH - 28f
        g.paint.style = Paint.Style.FILL
        g.paint.color = GREEN; g.cv.drawRoundRect(RectF(M, ly - 17f, M + 56f, ly - 3f), 7f, 7f, g.paint)
        g.text("votre coup", M + 68f, ly, 28f, pal.muted)
        g.paint.color = RED; g.cv.drawRoundRect(RectF(M + 260f, ly - 14f, M + 316f, ly - 6f), 4f, 4f, g.paint)
        g.text("réponse adverse", M + 328f, ly, 28f, pal.muted)
        g.badge("+", M + 640f, ly - 10f, 15f, GOLD, INK)
        g.text("échec", M + 664f, ly, 28f, pal.muted)
        g.badge("#", M + 780f, ly - 10f, 15f, RED)
        g.text("mat", M + 804f, ly, 28f, pal.muted)
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

    fun saveProblem(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?, style: ShareStyle = ShareStyle()): String? =
        save(context, render(context, puzzle, position, playerWhite, lastMove, style), "chessduck-${puzzle.id}.png")

    fun saveSolution(context: Context, puzzle: Puzzle, style: ShareStyle = ShareStyle()): String? =
        save(context, renderSolution(context, puzzle, style), "chessduck-${puzzle.id}-solution.png")

    fun share(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?, style: ShareStyle = ShareStyle()) =
        send(
            context, render(context, puzzle, position, playerWhite, lastMove, style), "chessduck-${puzzle.id}.png",
            Line.fromUci(puzzle.fen, puzzle.moves).let { challenge(puzzle, it, playerWhite) } + ". Saurez-vous trouver la solution ?", "Partager le problème",
        )

    fun shareSolution(context: Context, puzzle: Puzzle, style: ShareStyle = ShareStyle()) =
        send(context, renderSolution(context, puzzle, style), "chessduck-${puzzle.id}-solution.png", "Solution du problème Lichess #${puzzle.id}", "Partager la solution")
}
