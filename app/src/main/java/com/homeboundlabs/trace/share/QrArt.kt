package com.homeboundlabs.trace.share

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.ContextCompat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.homeboundlabs.trace.R

/**
 * Trace's QR style: soft rounded dots that flow into each other, rounded corner "eyes" with an
 * orange-brown centre, and the app logo in the middle.
 *
 * Scanning comes first, so the rules are fixed: highest error correction (H, about 30% can be lost),
 * dark marks on a white panel, a quiet zone of [QUIET] modules all round, the logo no wider than
 * [LOGO_SHARE] of the code with the modules under it removed (not covered), and colours that stay
 * dark when a scanner turns the image grey.
 */
object QrArt {
    private const val QUIET = 4
    private const val LOGO_SHARE = 0.22f

    // All three stay below ~40% brightness in greyscale, so binarisation always reads them as dark.
    private const val INK = 0xFF0E0F12.toInt()
    private const val INK_WARM = 0xFF4A1F0C.toInt()
    private const val PUPIL = 0xFFA8360A.toInt()

    /** Module grid for [text]; true = dark. */
    fun modules(text: String): Array<BooleanArray> {
        val code = Encoder.encode(text, ErrorCorrectionLevel.H, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
        val m = code.matrix
        return Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y).toInt() == 1 } }
    }

    /** Draws the code, quiet zone included, filling the square at ([left], [top]) of side [size]. */
    fun draw(context: Context, canvas: Canvas, text: String, left: Float, top: Float, size: Float) {
        val grid = modules(text)
        val n = grid.size
        val cell = size / (n + QUIET * 2)
        val ox = left + QUIET * cell
        val oy = top + QUIET * cell

        // Centre square kept clear for the logo: an odd number of modules, so it sits dead centre.
        var logoMods = (n * LOGO_SHARE).toInt()
        if (logoMods % 2 == 0) logoMods++
        val logoStart = (n - logoMods) / 2
        val logoEnd = logoStart + logoMods

        fun inFinder(x: Int, y: Int) = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
        fun inLogo(x: Int, y: Int) = x in logoStart until logoEnd && y in logoStart until logoEnd
        fun dark(x: Int, y: Int) = x in 0 until n && y in 0 until n && grid[y][x] && !inFinder(x, y) && !inLogo(x, y)

        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(ox, oy, ox + n * cell, oy + n * cell, INK, INK_WARM, Shader.TileMode.CLAMP)
        }
        // Each dark module is a dot; neighbours are joined by a bridge, so runs read as smooth rounded bars.
        val r = cell * 0.48f
        for (y in 0 until n) for (x in 0 until n) {
            if (!dark(x, y)) continue
            val cx = ox + (x + 0.5f) * cell
            val cy = oy + (y + 0.5f) * cell
            canvas.drawCircle(cx, cy, r, dot)
            if (dark(x + 1, y)) canvas.drawRect(cx, cy - r, cx + cell, cy + r, dot)
            if (dark(x, y + 1)) canvas.drawRect(cx - r, cy, cx + r, cy + cell, dot)
        }

        listOf(0 to 0, n - 7 to 0, 0 to n - 7).forEach { (fx, fy) -> drawEye(canvas, ox + fx * cell, oy + fy * cell, cell) }

        // Logo: the app icon itself (white T, orange dot on ink), on a white pad one module wide.
        val pad = RectF(ox + logoStart * cell, oy + logoStart * cell, ox + logoEnd * cell, oy + logoEnd * cell)
        val badge = RectF(pad).apply { inset(cell * 0.7f, cell * 0.7f) }
        drawLogo(context, canvas, badge)
    }

    private fun drawEye(canvas: Canvas, x: Float, y: Float, cell: Float) {
        val outer = RectF(x, y, x + 7 * cell, y + 7 * cell)
        val inner = RectF(outer).apply { inset(cell, cell) }
        val ring = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRoundRect(outer, cell * 2.3f, cell * 2.3f, Path.Direction.CW)
            addRoundRect(inner, cell * 1.4f, cell * 1.4f, Path.Direction.CW)
        }
        canvas.drawPath(ring, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK })
        val pupil = RectF(outer).apply { inset(cell * 2, cell * 2) }
        canvas.drawRoundRect(pupil, cell * 1.1f, cell * 1.1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PUPIL })
    }

    /** The launcher icon (ink square, white T, orange dot) drawn into [rect] with rounded corners. */
    fun drawLogo(context: Context, canvas: Canvas, rect: RectF) {
        val radius = rect.width() * 0.28f
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK })
        val fg = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground) ?: return
        // The icon's artwork spans about 46 of its 108 units; scale so it fills ~70% of the badge.
        val scale = rect.width() * 0.70f / 46f
        val full = 108f * scale
        val cx = rect.centerX() + (54f - 56.5f) * scale
        val cy = rect.centerY() + (54f - 54.5f) * scale
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        fg.setBounds((cx - full / 2).toInt(), (cy - full / 2).toInt(), (cx + full / 2).toInt(), (cy + full / 2).toInt())
        fg.draw(canvas)
        canvas.restore()
    }
}
