package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.ui.BoardGeometry
import com.ivanherboch.chessduck.data.Settings
import com.ivanherboch.chessduck.chess.PieceLexiconFr
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.Context
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
import com.ivanherboch.chessduck.R
import com.ivanherboch.chessduck.chess.BLACK
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.fileOf
import com.ivanherboch.chessduck.chess.isWhite
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.rankOf
import com.ivanherboch.chessduck.chess.sq
import com.ivanherboch.chessduck.chess.sqName
import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.ui.BoardTheme
import com.ivanherboch.chessduck.ui.DuckColors
import com.ivanherboch.chessduck.ui.DuckPalette
import com.ivanherboch.chessduck.ui.PIECE_RES
import androidx.compose.ui.graphics.toArgb
import java.util.concurrent.ConcurrentHashMap

/** Format de l'image : portrait 4:5 (WhatsApp, fil Instagram) ou carré 1:1 (publication Instagram). */
enum class ShareFormat(val height: Int, val label: String) { PORTRAIT(1350, "Portrait 4:5"), SQUARE(1080, "Carré 1:1") }

/**
 * Réglages d'une image partagée : format et fond clair ou sombre (persistés dans DataStore, voir Prefs),
 * dans la palette et les couleurs d'échiquier choisies dans les Paramètres.
 */
data class ShareStyle(
    val format: ShareFormat = ShareFormat.PORTRAIT,
    val light: Boolean = false,
    val palette: DuckPalette = DuckPalette.SLATE_GOLD,
    /** Couleurs des cases : celles de l'échiquier choisies dans les Paramètres. */
    val board: BoardTheme = BoardTheme.WOOD,
) {
    /** Jetons de couleur de l'image : palette (fond sombre ou clair) et cases de l'échiquier choisi. */
    val colors: DuckColors get() = board.applyTo(palette.colors(dark = !light))

    companion object {
        /** Style enregistré dans les réglages (format inconnu : portrait). */
        fun from(settings: Settings) = ShareStyle(
            ShareFormat.entries.firstOrNull { it.name == settings.shareFormat } ?: ShareFormat.PORTRAIT,
            settings.shareLight,
            DuckPalette.of(settings.palette),
            BoardTheme.of(settings.boardTheme),
        )
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
    private const val M = 44f
    /** Hauteur du pied de page compact. */
    private const val FOOTER_H = 62f
    private val BOLD: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    /** Couleurs d'une image en entiers ARGB (Canvas), tirées des jetons de la palette choisie dans les Paramètres. */
    private class Pal(c: DuckColors) {
        val light = !c.dark
        val bgTop = c.surface.toArgb()
        val bgBottom = (if (c.dark) c.bg else c.surfaceSubtle).toArgb()
        val texture = if (c.dark) Color.argb(9, 255, 255, 255) else Color.argb(10, 15, 23, 42)
        val halo = c.gold.copy(alpha = 0.18f).toArgb()
        val text = c.text.toArgb()
        val muted = c.muted.toArgb()
        /** Mentions discrètes du pied de page (contraste ≥ 3:1). */
        val faint = c.outlineStrong.toArgb()
        val surface = (if (c.dark) c.surfaceSubtle else c.surface).toArgb()
        val stroke = if (c.dark) Color.argb(28, 255, 255, 255) else Color.argb(26, 15, 23, 42)
        val dotOff = (if (c.dark) c.surfaceHi else c.outline).toArgb()
        val shadow = if (c.dark) Color.argb(140, 0, 0, 0) else Color.argb(60, 15, 23, 42)
        val sqLight = c.squareLight.toArgb()
        val sqDark = c.squareDark.toArgb()
        val gold = c.gold.toArgb()
        val green = c.green.toArgb()
        val red = c.red.toArgb()
        val goldText = c.goldText.toArgb()
        val goldFill = c.goldRole.fill.toArgb()
        val onGold = c.goldRole.onFill.toArgb()
        val greenText = c.greenText.toArgb()
        val greenFill = c.greenRole.fill.toArgb()
        val onGreen = c.greenRole.onFill.toArgb()
        val redText = c.redText.toArgb()
        val redFill = c.redRole.fill.toArgb()
        val onRed = c.redRole.onFill.toArgb()
    }

    /** Mascotte décodée une seule fois puis réutilisée par toutes les images. */
    @Volatile private var mascotCache: Bitmap? = null

    private fun mascot(context: Context): Bitmap =
        mascotCache ?: synchronized(this) {
            mascotCache ?: BitmapFactory.decodeResource(context.resources, R.drawable.mascot).also { mascotCache = it }
        }

    /** Les 12 pièces, décodées une seule fois puis partagées par toutes les images (jamais recyclées). */
    private val pieceCache = ConcurrentHashMap<Int, Bitmap>()

    private fun pieceBitmap(context: Context, p: Int): Bitmap =
        pieceCache.getOrPut(p) { BitmapFactory.decodeResource(context.resources, PIECE_RES.getValue(p)) }

    /** Mesure de texte seule, sans canevas : sert à dimensionner les images avant de les dessiner. */
    private open class TextMetrics {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

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
    }

    private class Painter(val context: Context, val cv: Canvas, val pal: Pal) : TextMetrics() {
        fun piece(p: Int): Bitmap = pieceBitmap(context, p)

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
                paint.color = if (i <= band) pal.gold else pal.dotOff
                cv.drawCircle(x, y - 11f, r, paint)
                x -= 2 * r + 8f
            }
            text("${PuzzleThemes.BANDS[band]} · $rating", x - 6f, y, 30f, pal.muted, align = Paint.Align.RIGHT)
        }

        /** Pastille ronde du camp au trait (comme dans l'application). */
        fun sideDot(cx: Float, cy: Float, r: Float, white: Boolean) {
            paint.style = Paint.Style.FILL
            paint.color = if (white) Color.WHITE else Color.parseColor("#020617")
            cv.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = pal.muted
            cv.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.FILL
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
            val y = h - 24f
            val mascot = mascot(context)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            cv.drawBitmap(mascot, null, RectF(M, y - 34f, M + 42f, y + 8f), paint)
            text("ChessDuck", M + 52f, y, 26f, pal.muted, bold = true)
            text("Lichess #$id", M + 52f + width("ChessDuck", 26f, bold = true) + 16f, y, 24f, pal.faint)
            text("© Ivan Herboch, 2026", W - M, y, 24f, pal.faint, align = Paint.Align.RIGHT)
        }

        /**
         * Échiquier aux coins arrondis, avec ombre portée ; [glow] fait rayonner des cases en rouge (roi en échec ou maté).
         * Renvoie la fonction donnant le coin supérieur gauche d'une case.
         */
        fun board(left: Float, top: Float, side: Float, position: Position, flipped: Boolean, lastMove: Move?, glow: List<Int> = emptyList()): (Int) -> Pair<Float, Float> {
            val s = side / 8
            val radius = side * 0.018f
            fun x(q: Int) = left + BoardGeometry.column(q, flipped) * s
            fun y(q: Int) = top + BoardGeometry.row(q, flipped) * s
            val rect = RectF(left, top, left + side, top + side)
            paint.style = Paint.Style.FILL
            paint.color = pal.sqDark
            paint.setShadowLayer(side * 0.03f, 0f, side * 0.012f, pal.shadow)
            cv.drawRoundRect(rect, radius, radius, paint)
            paint.clearShadowLayer()
            cv.save()
            cv.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
            for (q in 0 until 64) {
                paint.color = if ((fileOf(q) + rankOf(q)) % 2 == 1) pal.sqLight else pal.sqDark
                cv.drawRect(x(q), y(q), x(q) + s, y(q) + s, paint)
            }
            lastMove?.let {
                paint.color = Color.argb(120, 247, 236, 94)
                cv.drawRect(x(it.from), y(it.from), x(it.from) + s, y(it.from) + s, paint)
                cv.drawRect(x(it.to), y(it.to), x(it.to) + s, y(it.to) + s, paint)
            }
            for (q in glow) {
                if (q < 0) continue
                val cx = x(q) + s / 2
                val cy = y(q) + s / 2
                // Case du roi franchement rougie, puis halo qui déborde légèrement : lisible même sur les petits échiquiers.
                paint.color = Color.argb(150, 220, 38, 38)
                cv.drawRect(x(q), y(q), x(q) + s, y(q) + s, paint)
                paint.shader = RadialGradient(cx, cy, s * 0.8f, intArrayOf(Color.argb(230, 239, 68, 68), Color.argb(120, 239, 68, 68), Color.TRANSPARENT), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                cv.drawRect(cx - s, cy - s, cx + s, cy + s, paint)
                paint.shader = null
            }
            // Coordonnées : discrètes sur les grands échiquiers, un peu plus grosses (en proportion) sur les
            // mini-échiquiers de la solution ; omises seulement quand les cases sont trop petites pour être lues.
            if (s >= 40f) {
                paint.typeface = BOLD
                paint.textSize = if (s >= 70f) s * 0.2f else s * 0.25f
                for (i in 0..7) {
                    val fq = if (flipped) sq(7 - i, 7) else sq(i, 0)
                    paint.color = if ((fileOf(fq) + rankOf(fq)) % 2 == 1) pal.sqDark else pal.sqLight
                    cv.drawText(('a' + fileOf(fq)).toString(), x(fq) + s * 0.80f, y(fq) + s * 0.92f, paint)
                    val rq = if (flipped) sq(7, 7 - i) else sq(0, i)
                    paint.color = if ((fileOf(rq) + rankOf(rq)) % 2 == 1) pal.sqDark else pal.sqLight
                    cv.drawText(('1' + rankOf(rq)).toString(), x(rq) + s * 0.07f, y(rq) + s * 0.25f, paint)
                }
            }
            // Pièces toujours parfaitement opaques, quelle que soit la couleur utilisée juste avant.
            paint.shader = null
            paint.color = Color.WHITE
            paint.alpha = 255
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
            // Même géométrie que l'échiquier à l'écran (BoardGeometry) : coude du Cavalier, décalages, pointe.
            val squares = BoardGeometry.arrowSquares(m.from, m.to)
            val cx = FloatArray(squares.size) { at(squares[it]).first + s / 2 }
            val cy = FloatArray(squares.size) { at(squares[it]).second + s / 2 }
            val head = s * 0.42f * (widthFactor / 0.17f).coerceAtMost(1f).coerceAtLeast(0.8f)
            val shape = BoardGeometry.arrowShape(cx, cy, s, head)
            val path = Path().apply {
                moveTo(shape.shaftX[0], shape.shaftY[0])
                for (i in 1 until shape.shaftX.size) lineTo(shape.shaftX[i], shape.shaftY[i])
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
            cv.drawPath(Path().apply {
                moveTo(shape.headX[0], shape.headY[0])
                lineTo(shape.headX[1], shape.headY[1])
                lineTo(shape.headX[2], shape.headY[2])
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

    private const val EYEBROW_Y = 64f

    /** Titre sur une ligne si possible (taille réduite jusqu'à 44 px), sinon sur plusieurs lignes. */
    private fun fitTitle(p: TextMetrics, text: String, max: Float): Pair<List<String>, Float> {
        val width = W - 2 * M - 56f
        var size = max
        while (size > 44f && p.width(text, size, bold = true) > width) size -= 2f
        return p.wrap(text, size, width, bold = true) to size
    }

    /** Hauteur de l'en-tête compact (sur-titre et titre) : l'échiquier garde le maximum de place. */
    private fun headerHeight(lines: Int, size: Float) = EYEBROW_Y + size * 1.3f + (lines - 1) * size * 1.15f + size * 0.32f

    /** En-tête commun énoncé / solution : sur-titre (avec l'objectif chiffré), difficulté et défi. */
    private fun header(g: Painter, eyebrow: String, eyebrowColor: Int, puzzle: Puzzle, title: List<String>, size: Float, playerWhite: Boolean) {
        g.text(eyebrow.uppercase(), M, EYEBROW_Y, 28f, eyebrowColor, bold = true, spacing = 0.12f)
        g.difficulty(puzzle.rating, W - M, EYEBROW_Y)
        var y = EYEBROW_Y + size * 1.3f
        g.sideDot(M + 18f, y - size * 0.36f, 18f, playerWhite)
        title.forEachIndexed { i, l ->
            if (i > 0) y += size * 1.15f
            g.text(l, M + 54f, y, size, g.pal.text, bold = true)
        }
    }

    // ---------------------------------------------------------------- Énoncé

    /**
     * Énoncé : l'échiquier occupe presque toute la largeur ; au-dessus, le défi ; en dessous, l'indice
     * (avec le thème) dans une carte compacte, puis un pied de page discret.
     */
    fun render(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?, style: ShareStyle = ShareStyle(), includeTip: Boolean = true): Bitmap {
        val pal = Pal(style.colors)
        val square = style.format == ShareFormat.SQUARE
        val h = style.format.height
        val line = Line.fromUci(puzzle.fen, puzzle.moves)
        val theme = PuzzleThemes.primary(puzzle.themes)
        val mate = line.sans.lastOrNull()?.endsWith("#") == true
        val n = playerMoves(line)
        val probe = TextMetrics()
        val (title, titleSize) = fitTitle(probe, challenge(puzzle, line, playerWhite), if (square) 52f else 58f)
        val eyebrow = if (mate) "Problème d'échecs" else "Problème · ${coups(n)} à trouver"
        val tipSize = 28f
        val tipLineH = tipSize * 1.32f
        val label = if (mate || theme.tip.isEmpty()) "Indice :" else "Indice · ${theme.name} :"
        // « Inclure l'indice » décoché : l'énoncé seul, sans le thème ni son conseil.
        val tipText = if (includeTip && theme.tip.isNotEmpty()) "$label ${theme.tip}" else ""
        val tip = if (tipText.isNotEmpty()) probe.wrap(tipText, tipSize, W - 2 * M - 48f) else emptyList()
        val tipH = if (tip.isEmpty()) 0f else 28f + tip.size * tipLineH
        val top = headerHeight(title.size, titleSize) + 6f
        val bottom = (if (tip.isEmpty()) 0f else tipH + 16f) + FOOTER_H + 4f
        val side = minOf(W - 2 * M, h - top - 16f - bottom)

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val g = Painter(context, Canvas(bmp), pal)
        g.background(h)
        header(g, eyebrow, pal.goldText, puzzle, title, titleSize, playerWhite)
        g.board((W - side) / 2, top, side, position, !playerWhite, lastMove, listOfNotNull(position.kingSquare(position.whiteToMove).takeIf { position.inCheck }))

        if (tip.isNotEmpty()) {
            val y = top + side + 16f
            g.panel(RectF(M, y, W - M, y + tipH), accent = pal.gold)
            var ty = y + 14f + tipSize
            tip.forEachIndexed { i, l ->
                if (i == 0 && l.startsWith(label)) {
                    g.text(label, M + 26f, ty, tipSize, pal.goldText, bold = true)
                    g.text(l.removePrefix(label), M + 26f + g.width(label, tipSize, bold = true), ty, tipSize, pal.text)
                } else g.text(l, M + 26f, ty, tipSize, pal.text)
                ty += tipLineH
            }
        }
        g.footer(h, puzzle.id)
        return bmp
    }

    // ---------------------------------------------------------------- Solution

    /** Phrase décrivant un coup : pièce, prise éventuelle, case, promotion, échec ou mat. */
    fun describe(p: Position, m: Move, san: String): String {
        val sb = StringBuilder()
        val type = pieceType(p[m.from])
        when {
            p.isCastle(m) -> sb.append(if (fileOf(m.to) == 6) "Petit roque" else "Grand roque")
            p.isEnPassant(m) -> sb.append("Le pion capture le pion en passant, en ${sqName(m.to)}")
            p.isCapture(m) -> sb.append("${PieceLexiconFr.definiteCapitalized(type)} capture ${PieceLexiconFr.definite(pieceType(p[m.to]))} en ${sqName(m.to)}")
            else -> sb.append("${PieceLexiconFr.definiteCapitalized(type)} va en ${sqName(m.to)}")
        }
        if (m.promo != 0) sb.append(" et se promeut en ${PieceLexiconFr.name(m.promo)}")
        sb.append(when {
            san.endsWith("#") -> " : échec et mat !"
            san.endsWith("+") -> ", avec échec."
            else -> "."
        })
        return sb.toString()
    }

    private class Step(val before: Position, val move: Move, val san: String, val prev: Move?, val reply: Move?, val replySan: String?)

    /**
     * Solution illustrée, toujours sur une seule image au format choisi : une carte par coup du joueur,
     * avec des grilles aussi grandes que possible (marges et légendes réduites au strict nécessaire).
     * Une ou deux étapes : phrases complètes ; au-delà, notation seule « coup — réponse » sur une ligne.
     * Flèche verte = coup à jouer, flèche rouge = réponse adverse, badges +, # et x (prise), roi en échec ou maté rougi.
     */
    fun renderSolution(context: Context, puzzle: Puzzle, style: ShareStyle = ShareStyle()): Bitmap {
        val pal = Pal(style.colors)
        val square = style.format == ShareFormat.SQUARE
        val h = style.format.height
        val line = Line.fromUci(puzzle.fen, puzzle.moves)
        val playerWhite = line.positions[1].whiteToMove
        val steps = (1 until line.size step 2).map { i ->
            Step(line.positions[i], line.moves[i], line.sans[i], line.moves[i - 1], line.moves.getOrNull(i + 1), line.sans.getOrNull(i + 1))
        }
        val n = steps.size
        val probe = TextMetrics()
        val (title, titleSize) = fitTitle(probe, challenge(puzzle, line, playerWhite), if (square) 50f else 56f)
        val top = headerHeight(title.size, titleSize) + 4f
        val legendH = 44f
        val margin = 16f
        val gap = 12f
        val gridH = h - top - legendH - FOOTER_H - 6f

        // Choix de la grille qui donne les plus grands échiquiers.
        val detailed = n <= 2
        val titleSz = if (n == 1) 40f else if (n <= 4) 34f else if (n <= 6) 30f else 26f
        val textSize = if (n == 1) 30f else if (n <= 2) 26f else 24f
        val lineH = textSize * 1.28f
        val pad = if (n == 1) 18f else 10f
        fun layout(cols: Int): Triple<Int, Float, Float> {
            val rows = (n + cols - 1) / cols
            val panelW = (W - 2 * margin - (cols - 1) * gap) / cols
            return Triple(rows, panelW, panelW - 2 * pad)
        }
        val textW0 = { cols: Int -> layout(cols).third }
        fun captionH(cols: Int): Float {
            val w = textW0(cols)
            return (0 until n).maxOf { k ->
                val st = steps[k]
                var hh = titleSz * 1.2f + 6f
                // Grille compacte : la réponse adverse qui ne tient pas à côté du coup passe sur sa propre ligne.
                if (!detailed && st.replySan != null && !replyInline(probe, st, titleSz, w)) hh += titleSz * 1.05f
                if (detailed) {
                    hh += probe.wrap(describe(st.before, st.move, st.san), textSize, w).size * lineH
                    if (st.reply != null && st.replySan != null) hh += probe.wrap(replyText(st), textSize, w - textSize * 0.8f).size * lineH
                }
                if (k == n - 1) hh += textSize * 1.7f + 10f
                hh
            }
        }
        fun sideFor(cols: Int): Float {
            val (rows, _, textW) = layout(cols)
            val panelH = (gridH - (rows - 1) * gap) / rows
            return minOf(textW, panelH - 2 * pad - 10f - captionH(cols))
        }
        // Longues solutions (jusqu'à 10 coups) : jusqu'à 5 colonnes, pour que tout tienne sans déborder de l'image.
        val cols = (1..minOf(n, if (n > 6) 5 else 3)).maxByOrNull { sideFor(it) } ?: 1
        val (rows, panelW, textW) = layout(cols)
        // Jamais de taille minimale imposée qui pousserait les cartes hors de l'image.
        val side = sideFor(cols).coerceAtLeast(1f)
        val capH = captionH(cols)
        val panelH = 2 * pad + side + 10f + capH
        val gridTop = top + ((gridH - rows * panelH - (rows - 1) * gap) / 2).coerceAtLeast(0f)

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val g = Painter(context, Canvas(bmp), pal)
        g.background(h)
        header(g, "Solution · ${coups(n)}", pal.greenText, puzzle, title, titleSize, playerWhite)

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
                val by = y + pad
                // Roi en échec (ou maté) rougi : après le coup du joueur, puis après la réponse adverse.
                val after = st.before.play(st.move)
                val after2 = st.reply?.let { after.play(it) }
                val glow = listOfNotNull(
                    after.kingSquare(after.whiteToMove).takeIf { after.inCheck },
                    after2?.let { a -> a.kingSquare(a.whiteToMove).takeIf { a.inCheck } },
                )
                val at = g.board(x + (panelW - side) / 2, by, side, st.before, !playerWhite, st.prev, glow)
                val s = side / 8
                st.reply?.let { g.arrow(it, at, s, pal.red, 0.10f) }
                g.arrow(st.move, at, s, pal.green, 0.17f)
                val (dx, dy) = at(st.move.to)
                when {
                    st.san.endsWith("#") -> g.badge("#", dx + s * 0.80f, dy + s * 0.20f, s * 0.22f, pal.redFill, pal.onRed)
                    st.san.endsWith("+") -> g.badge("+", dx + s * 0.80f, dy + s * 0.20f, s * 0.22f, pal.goldFill, pal.onGold)
                }
                // « x » : prise, en haut à gauche de la case d'arrivée (coup du joueur et réponse adverse).
                val took = st.before.isCapture(st.move)
                if (took) g.badge("x", dx + s * 0.20f, dy + s * 0.20f, s * 0.22f, pal.text, pal.bgTop)
                // Reprise sur la même case : un seul badge (deux se superposeraient).
                st.reply?.takeIf { after.isCapture(it) && !(took && it.to == st.move.to) }?.let { r ->
                    val (rx, ry) = at(r.to)
                    g.badge("x", rx + s * 0.20f, ry + s * 0.20f, s * 0.22f, pal.text, pal.bgTop)
                }
                // Ligne de titre : numéro, coup, réponse (grille compacte) et pièce prise.
                val ty0 = by + side + 10f + titleSz * 0.95f
                val br = titleSz * 0.5f
                g.badge("${k + 1}", bx + br, ty0 - titleSz * 0.34f, br, pal.greenFill, pal.onGreen)
                val sanX = bx + 2 * br + 10f
                g.text(st.san, sanX, ty0, titleSz, pal.text, bold = true)
                // Pièce prise, colorée à l'opposé de celle qui joue.
                val victim = st.before.capturedType(st.move).let { t -> if (t == 0) 0 else t + if (isWhite(st.before[st.move.from])) BLACK else 0 }
                val ps = titleSz * 1.05f
                var replyBelow = false
                if (!detailed && st.replySan != null) {
                    val reply = "— ${st.replySan}"
                    val full = titleSz * 0.85f
                    if (replyInline(probe, st, titleSz, textW)) {
                        // À côté du coup, sans jamais chevaucher la vignette de la pièce prise.
                        val rx = sanX + g.width(st.san, titleSz, bold = true) + 12f
                        val room = bx + textW - (if (victim != 0) ps + 8f else 0f) - rx
                        g.text(reply, rx, ty0, (full * room / g.width(reply, full, bold = true)).coerceAtMost(full), pal.redText, bold = true)
                    } else {
                        // Sur sa propre ligne, sous le coup : la réponse adverse n'est jamais omise.
                        replyBelow = true
                        val size = (full * textW / g.width(reply, full, bold = true)).coerceAtMost(full)
                        g.text(reply, bx, ty0 + titleSz * 1.05f, size, pal.redText, bold = true)
                    }
                }
                if (victim != 0) {
                    val px = bx + textW - ps
                    val py = ty0 - ps * 0.8f
                    g.paint.style = Paint.Style.FILL
                    g.paint.color = pal.sqLight
                    g.cv.drawRoundRect(RectF(px, py, px + ps, py + ps), 10f, 10f, g.paint)
                    g.paint.color = Color.WHITE
                    g.cv.drawBitmap(g.piece(victim), null, RectF(px, py, px + ps, py + ps), g.paint)
                    if (detailed) g.text("prend", px - 10f, ty0 - titleSz * 0.12f, textSize * 0.9f, pal.muted, align = Paint.Align.RIGHT)
                }
                var ty = ty0 + titleSz * 0.25f + if (replyBelow) titleSz * 1.05f else 0f
                if (detailed) {
                    for (l in probe.wrap(describe(st.before, st.move, st.san), textSize, textW)) { ty += lineH; g.text(l, bx, ty, textSize, pal.text) }
                    if (st.reply != null && st.replySan != null) {
                        probe.wrap(replyText(st), textSize, textW - textSize * 0.8f).forEachIndexed { i, l ->
                            ty += lineH
                            if (i == 0) { g.paint.color = pal.red; g.cv.drawCircle(bx + textSize * 0.25f, ty - textSize * 0.34f, textSize * 0.2f, g.paint) }
                            g.text(l, bx + textSize * 0.8f, ty, textSize, pal.redText)
                        }
                    }
                }
                if (last) {
                    ty += 10f
                    val label = if (st.san.endsWith("#")) "Échec et mat !" else "Avantage décisif"
                    // La pastille rétrécit si la carte est étroite (5 colonnes) : elle ne déborde jamais de sa carte.
                    val pillSize = (textSize * textW / (g.width(label, textSize, bold = true) + textSize * 1.4f)).coerceAtMost(textSize)
                    if (st.san.endsWith("#")) g.pill(label, bx, ty, pillSize, pal.goldFill, pal.onGold)
                    else g.pill(label, bx, ty, pillSize, pal.greenFill, pal.onGreen)
                }
                x += panelW + gap
            }
            y += panelH + gap
        }
        // Légende des symboles
        val ly = h - FOOTER_H - 18f
        lastSolutionGrid = SolutionGrid(cols, rows, side, gridTop, gridTop + rows * panelH + (rows - 1) * gap, (W - minOf(n, cols) * panelW - (minOf(n, cols) - 1) * gap) / 2, ly - 15f, h.toFloat())
        g.paint.style = Paint.Style.FILL
        g.paint.color = pal.green; g.cv.drawRoundRect(RectF(M, ly - 15f, M + 48f, ly - 3f), 6f, 6f, g.paint)
        g.text("votre coup", M + 58f, ly, 25f, pal.muted)
        g.paint.color = pal.red; g.cv.drawRoundRect(RectF(M + 230f, ly - 13f, M + 278f, ly - 5f), 4f, 4f, g.paint)
        g.text("réponse adverse", M + 288f, ly, 25f, pal.muted)
        g.badge("+", M + 560f, ly - 9f, 14f, pal.goldFill, pal.onGold)
        g.text("échec", M + 582f, ly, 25f, pal.muted)
        g.badge("#", M + 680f, ly - 9f, 14f, pal.redFill, pal.onRed)
        g.text("mat", M + 702f, ly, 25f, pal.muted)
        g.badge("x", M + 790f, ly - 9f, 14f, pal.text, pal.bgTop)
        g.text("prise", M + 812f, ly, 25f, pal.muted)
        g.footer(h, puzzle.id)
        return bmp
    }

    /**
     * Grille compacte : la réponse « — Cxd5 » tient-elle à côté du coup (au moins aux deux tiers de sa taille), en
     * laissant la place de la vignette de la pièce prise ?
     */
    private fun replyInline(probe: TextMetrics, st: Step, titleSz: Float, textW: Float): Boolean {
        val reply = "— ${st.replySan ?: return true}"
        val br = titleSz * 0.5f
        val used = 2 * br + 10f + probe.width(st.san, titleSz, bold = true) + 12f +
            (if (st.before.isCapture(st.move)) titleSz * 1.05f + 8f else 0f)
        val full = titleSz * 0.85f
        return (full * (textW - used) / probe.width(reply, full, bold = true)) >= full * 0.67f
    }

    /** Géométrie de la dernière solution rendue (tests : aucune carte ne déborde de l'image ni ne couvre la légende). */
    internal class SolutionGrid(val cols: Int, val rows: Int, val side: Float, val top: Float, val bottom: Float, val left: Float, val legendTop: Float, val height: Float)

    @Volatile internal var lastSolutionGrid: SolutionGrid? = null

    private fun replyText(st: Step) =
        "Réponse : ${st.replySan} — ${describe(st.before.play(st.move), st.reply!!, st.replySan!!).replaceFirstChar { it.lowercase() }}"

    // ---------------------------------------------------------------- Envoi (voir ShareExporter)

    /** Image de l'énoncé : réutilise [ready] (aperçu déjà calculé) sinon la génère sur Dispatchers.Default. */
    private suspend fun problemBitmap(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?, style: ShareStyle, ready: Bitmap?, includeTip: Boolean) =
        ready ?: withContext(Dispatchers.Default) { render(context, puzzle, position, playerWhite, lastMove, style, includeTip) }

    private suspend fun solutionBitmap(context: Context, puzzle: Puzzle, style: ShareStyle, ready: Bitmap?) =
        ready ?: withContext(Dispatchers.Default) { renderSolution(context, puzzle, style) }

    suspend fun saveProblem(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?, style: ShareStyle = ShareStyle(), ready: Bitmap? = null, includeTip: Boolean = true): String? =
        ShareExporter.save(context, problemBitmap(context, puzzle, position, playerWhite, lastMove, style, ready, includeTip), "chessduck-${puzzle.id}.png")

    suspend fun saveSolution(context: Context, puzzle: Puzzle, style: ShareStyle = ShareStyle(), ready: Bitmap? = null): String? =
        ShareExporter.save(context, solutionBitmap(context, puzzle, style, ready), "chessduck-${puzzle.id}-solution.png")

    suspend fun share(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?, style: ShareStyle = ShareStyle(), ready: Bitmap? = null, includeTip: Boolean = true): Boolean =
        ShareExporter.send(
            context, problemBitmap(context, puzzle, position, playerWhite, lastMove, style, ready, includeTip), "chessduck-${puzzle.id}.png",
            challenge(puzzle, Line.fromUci(puzzle.fen, puzzle.moves), playerWhite) + ". Saurez-vous trouver la solution ?", "Partager le problème",
        )

    suspend fun shareSolution(context: Context, puzzle: Puzzle, style: ShareStyle = ShareStyle(), ready: Bitmap? = null): Boolean =
        ShareExporter.send(context, solutionBitmap(context, puzzle, style, ready), "chessduck-${puzzle.id}-solution.png", "Solution du problème Lichess #${puzzle.id}", "Partager la solution")
}
