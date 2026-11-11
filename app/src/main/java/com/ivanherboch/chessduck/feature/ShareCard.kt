package com.ivanherboch.chessduck.feature

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.ivanherboch.chessduck.R
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.fileOf
import com.ivanherboch.chessduck.chess.rankOf
import com.ivanherboch.chessduck.chess.sq
import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.ui.PIECE_RES
import java.io.File

/** Carte de partage PNG d'un problème, avec exactement les mêmes pièces que l'échiquier en direct. */
object ShareCard {
    private const val W = 1080
    private const val H = 1600

    fun render(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?, preferredTheme: String = ""): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), Color.parseColor("#1E293B"), Color.parseColor("#0F172A"), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = null

        // En-tête : mascotte Cavalier-Canard + titre
        val mascot = BitmapFactory.decodeResource(context.resources, R.drawable.mascot)
        cv.drawBitmap(mascot, null, RectF(48f, 36f, 48f + 150f, 36f + 150f), paint)
        paint.color = Color.parseColor("#F8FAFC")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 76f
        cv.drawText("ChessDuck", 222f, 112f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 36f
        paint.color = Color.parseColor("#94A3B8")
        cv.drawText("Problème d'échecs  ·  Lichess #${puzzle.id}", 224f, 166f, paint)

        // Échiquier
        val side = 984f
        val left = (W - side) / 2
        val top = 220f
        val s = side / 8
        val flipped = !playerWhite
        fun x(q: Int) = left + (if (flipped) 7 - fileOf(q) else fileOf(q)) * s
        fun y(q: Int) = top + (if (flipped) rankOf(q) else 7 - rankOf(q)) * s
        val light = Color.parseColor("#F0D9B5")
        val dark = Color.parseColor("#B58863")
        for (q in 0 until 64) {
            paint.color = if ((fileOf(q) + rankOf(q)) % 2 == 1) light else dark
            cv.drawRect(x(q), y(q), x(q) + s, y(q) + s, paint)
        }
        lastMove?.let {
            paint.color = Color.parseColor("#66F7EC5E")
            cv.drawRect(x(it.from), y(it.from), x(it.from) + s, y(it.from) + s, paint)
            cv.drawRect(x(it.to), y(it.to), x(it.to) + s, y(it.to) + s, paint)
        }
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = s * 0.2f
        for (i in 0..7) {
            val fq = if (flipped) sq(i, 7) else sq(i, 0)
            paint.color = if ((fileOf(fq) + rankOf(fq)) % 2 == 1) dark else light
            cv.drawText(('a' + i).toString(), x(fq) + s * 0.82f, y(fq) + s * 0.94f, paint)
            val rq = if (flipped) sq(7, i) else sq(0, i)
            paint.color = if ((fileOf(rq) + rankOf(rq)) % 2 == 1) dark else light
            cv.drawText(('1' + i).toString(), x(rq) + s * 0.06f, y(rq) + s * 0.24f, paint)
        }
        val cache = HashMap<Int, Bitmap>()
        for (q in 0 until 64) {
            val piece = position[q]
            if (piece == 0) continue
            val b = cache.getOrPut(piece) { BitmapFactory.decodeResource(context.resources, PIECE_RES.getValue(piece)) }
            cv.drawBitmap(b, null, RectF(x(q), y(q), x(q) + s, y(q) + s), paint)
        }

        // Légende : trait, difficulté, type de problème et conseil (sans la solution)
        val theme = PuzzleThemes.primary(puzzle.themes, preferredTheme)
        val base = top + side
        paint.color = if (playerWhite) Color.WHITE else Color.parseColor("#111827")
        cv.drawRoundRect(RectF(48f, base + 44f, 94f, base + 90f), 8f, 8f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.parseColor("#94A3B8")
        cv.drawRoundRect(RectF(48f, base + 44f, 94f, base + 90f), 8f, 8f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#F8FAFC")
        paint.textSize = 54f
        cv.drawText(if (playerWhite) "Trait aux Blancs" else "Trait aux Noirs", 110f, base + 86f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 40f
        paint.color = Color.parseColor("#F59E0B")
        val band = PuzzleThemes.BANDS[PuzzleThemes.bandOf(puzzle.rating)]
        cv.drawText("Difficulté : $band (${puzzle.rating})", 48f, base + 150f, paint)
        paint.color = Color.parseColor("#38BDF8")
        cv.drawText("Type de problème : ${theme.name}", 48f, base + 206f, paint)
        paint.color = Color.parseColor("#10B981")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 36f
        cv.drawText("Conseil", 48f, base + 264f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#E2E8F0")
        var y = base + 264f
        var lineText = ""
        val indent = 48f + paint.measureText("Conseil  ") + 16f
        var first = true
        fun flush() {
            if (lineText.isEmpty()) return
            cv.drawText(lineText, if (first) indent else 48f, y, paint)
            first = false; y += 46f; lineText = ""
        }
        for (word in theme.tip.split(' ')) {
            val candidate = if (lineText.isEmpty()) word else "$lineText $word"
            val width = W - 48f - (if (first) indent else 48f)
            if (paint.measureText(candidate) > width) { flush(); lineText = word } else lineText = candidate
        }
        flush()
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 28f
        paint.textAlign = Paint.Align.RIGHT
        cv.drawText("© Ivan Herboch, 2026", W - 48f, H - 28f, paint)
        return bmp
    }

    fun share(context: Context, puzzle: Puzzle, position: Position, playerWhite: Boolean, lastMove: Move?, preferredTheme: String = "") {
        runCatching {
            val bmp = render(context, puzzle, position, playerWhite, lastMove, preferredTheme)
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "chessduck-${puzzle.id}.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Saurez-vous résoudre ce problème ? ${if (playerWhite) "Trait aux Blancs" else "Trait aux Noirs"} — ChessDuck")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Partager le problème").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
