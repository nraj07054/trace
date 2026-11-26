package com.homeboundlabs.trace.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.homeboundlabs.trace.R
import com.homeboundlabs.trace.data.Money
import kotlin.math.abs
import kotlin.math.max

/** How a payment request reads. The card and the WhatsApp message both follow it. */
enum class RequestTone(val id: String, val label: String) {
    Casual("casual", "Casual"), Polite("polite", "Polite"), Neutral("neutral", "Neutral");

    fun greeting(name: String) = when (this) {
        Casual -> "Hey $name 👋"
        Polite -> "Hi $name"
        Neutral -> name
    }

    fun lead() = when (this) {
        Casual -> "Just a friendly nudge for"
        Polite -> "Whenever it suits you, a reminder about"
        Neutral -> "Pending amount"
    }

    fun message(name: String, amount: String, note: String, upiId: String?): String {
        val forWhat = if (note.isBlank()) "" else " for $note"
        val pay = upiId?.let { " Scan the QR or pay to $it." } ?: ""
        return when (this) {
            Casual -> "Hey $name! Just a reminder about the $amount$forWhat 🙂$pay Thanks!"
            Polite -> "Hi $name, hope you're doing well. Whenever it's convenient, could you send the $amount$forWhat?$pay Thank you!"
            Neutral -> "$name, $amount pending$forWhat.$pay"
        }
    }

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id } ?: Casual
    }
}

data class RequestCard(
    val personName: String,
    val amountMinor: Long,
    val note: String,
    val dateLabel: String,
    val tone: RequestTone,
    /** Null: no QR (no UPI ID set, or the currency isn't ₹). */
    val upiLink: String?,
    val upiId: String?,
    /** Recent movements, oldest first: + the user gave, − the user got. */
    val history: List<Long>,
    val lentMinor: Long,
    val returnedMinor: Long,
    val currency: String,
)

data class PaidBackCard(val personName: String, val amountMinor: Long, val note: String, val settled: Boolean, val currency: String)

data class RecapSlice(val name: String, val argb: Long, val amountMinor: Long)

data class RecapCard(
    val monthLabel: String,
    val totalMinor: Long,
    val deltaPct: Int?,
    val slices: List<RecapSlice>,
    /** Days in the month so far (or the whole month once it's over). */
    val daysInPeriod: Int,
    val bestStreak: Int,
    val daysLogged: Int,
    val expenses: Int,
    val currency: String,
    /** The month isn't over: the comparison is with the same days of last month. */
    val soFar: Boolean = false,
)

/**
 * Share cards, drawn straight onto a bitmap so the preview in the app and the image people receive
 * are the same pixels. Always light: the QR needs a white panel, and a light card reads well in any
 * chat theme.
 */
class Cards(private val context: Context) {
    private val jakarta: Typeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.DEFAULT

    private fun paint(size: Float, weight: Int, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = jakarta
        textSize = size
        this.color = color
        fontVariationSettings = "'wght' $weight"
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun fit(text: String, p: TextPaint, width: Float) = TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString()

    /** Shrinks [p] until [text] fits in [width] (for big amounts). */
    private fun shrinkToFit(text: String, p: TextPaint, width: Float, min: Float) {
        while (p.measureText(text) > width && p.textSize > min) p.textSize -= 4f
    }

    /* ---------------- payment request ---------------- */

    fun request(c: RequestCard): Bitmap {
        val hasQr = c.upiLink != null
        val h = if (hasQr) 1640 else 1000
        val bmp = createBitmap(W, h)
        val cv = Canvas(bmp)
        background(cv, h, ACCENT)

        var y = 150f
        cv.drawText(fit(c.tone.greeting(c.personName), paint(58f, 800, INK), CONTENT), PAD, y, paint(58f, 800, INK))
        y += 66f
        cv.drawText(c.tone.lead(), PAD, y, paint(36f, 500, MUTED))

        y += 170f
        val amount = Money.compact(c.amountMinor, c.currency)
        val big = paint(156f, 800, INK).apply { letterSpacing = -0.03f }
        shrinkToFit(amount, big, CONTENT, 80f)
        cv.drawText(amount, PAD - 6f, y, big)

        y += 44f
        val chip = listOfNotNull(c.note.takeIf { it.isNotBlank() }, c.dateLabel).joinToString("  ·  ")
        y = chipRow(cv, chip, y)

        y += 40f
        historyStrip(cv, c, y)
        y += 150f

        if (hasQr) {
            val panel = RectF((W - 680) / 2f, y, (W + 680) / 2f, y + 800f)
            shadow(cv, panel, 56f)
            cv.drawRoundRect(panel, 56f, 56f, fill(WHITE))
            val qr = 600f
            QrArt.draw(context, cv, c.upiLink!!, panel.centerX() - qr / 2, panel.top + 30f, qr)
            val cap = paint(34f, 700, INK)
            val capText = "Scan with any UPI app"
            cv.drawText(capText, panel.centerX() - cap.measureText(capText) / 2, panel.top + 680f, cap)
            c.upiId?.let {
                val idp = paint(30f, 600, MUTED)
                val t = fit(it, idp, panel.width() - 80f)
                cv.drawText(t, panel.centerX() - idp.measureText(t) / 2, panel.top + 730f, idp)
            }
            y = panel.bottom
        }
        footer(cv, h)
        return bmp
    }

    /** Mini chart of what was lent (orange, up) and paid back (green, down), plus how much is back. */
    private fun historyStrip(cv: Canvas, c: RequestCard, top: Float) {
        val box = RectF(PAD, top, W - PAD, top + 120f)
        cv.drawRoundRect(box, 32f, 32f, fill(0xB3FFFFFF.toInt()))
        val bars = c.history.takeLast(10)
        val maxAbs = bars.maxOfOrNull { abs(it) }?.takeIf { it > 0 } ?: 1L
        val mid = box.centerY()
        val barW = 22f
        val gap = 14f
        var x = box.left + 36f
        bars.forEach { v ->
            val hh = max(8f, abs(v).toFloat() / maxAbs * 40f)
            val r = RectF(x, if (v > 0) mid - hh else mid, x + barW, if (v > 0) mid else mid + hh)
            cv.drawRoundRect(r, 8f, 8f, fill(if (v > 0) ACCENT else GREEN))
            x += barW + gap
        }
        cv.drawRect(box.left + 30f, mid - 1f, x - gap + 6f, mid + 1f, fill(0x220E0F12))

        val right = box.right - 36f
        val title = paint(30f, 800, INK)
        val back = "${Money.whole(c.returnedMinor, c.currency)} of ${Money.whole(c.lentMinor, c.currency)} back"
        shrinkToFit(back, title, right - x - 20f, 20f)
        cv.drawText(back, right - title.measureText(back), mid - 4f, title)
        val sub = paint(24f, 600, MUTED)
        val pct = if (c.lentMinor > 0) (c.returnedMinor * 100 / c.lentMinor).toInt() else 0
        val s = "$pct% settled so far"
        cv.drawText(s, right - sub.measureText(s), mid + 32f, sub)
    }

    /* ---------------- paid back ---------------- */

    fun paidBack(c: PaidBackCard): Bitmap {
        val h = 1000
        val bmp = createBitmap(W, h)
        val cv = Canvas(bmp)
        background(cv, h, GREEN)

        var y = 150f
        cv.drawText(fit("Hey ${c.personName} 👋", paint(58f, 800, INK), CONTENT), PAD, y, paint(58f, 800, INK))
        y += 66f
        cv.drawText("Paid you back", PAD, y, paint(36f, 500, MUTED))
        y += 170f
        val amount = Money.compact(c.amountMinor, c.currency)
        val big = paint(156f, 800, INK).apply { letterSpacing = -0.03f }
        shrinkToFit(amount, big, CONTENT - 130f, 80f)
        cv.drawText(amount, PAD - 6f, y, big)
        // A round green tick beside the amount.
        val tx = PAD + big.measureText(amount) + 70f
        cv.drawCircle(tx, y - 54f, 48f, fill(GREEN))
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE; style = Paint.Style.STROKE; strokeWidth = 12f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        cv.drawPath(android.graphics.Path().apply { moveTo(tx - 20f, y - 54f); lineTo(tx - 4f, y - 38f); lineTo(tx + 22f, y - 70f) }, tick)

        y += 44f
        if (c.note.isNotBlank()) y = chipRow(cv, c.note, y) + 30f
        y += 60f
        val line = if (c.settled) "All settled between us 🙌" else "Thanks for your patience!"
        cv.drawText(line, PAD, y, paint(40f, 700, INK))
        footer(cv, h)
        return bmp
    }

    /* ---------------- monthly recap ---------------- */

    fun recap(c: RecapCard): Bitmap {
        // Sized to what's on it: header, total, split bar, up to four rows, then the habit tiles.
        val rows = c.slices.size.coerceAtMost(4)
        val chipSpace = if (c.deltaPct != null) 84f else 0f
        val tileTop = 576f + chipSpace + 62f * rows + 10f
        val h = (tileTop + 220f + 170f).toInt()
        val bmp = createBitmap(W, h)
        val cv = Canvas(bmp)
        background(cv, h, INFO)

        var y = 150f
        cv.drawText("${c.monthLabel} recap", PAD, y, paint(54f, 800, INK))
        y += 62f
        cv.drawText(if (c.soFar) "Where my money went so far" else "Where my money went", PAD, y, paint(34f, 500, MUTED))

        y += 180f
        val total = Money.whole(c.totalMinor, c.currency)
        val big = paint(150f, 800, INK).apply { letterSpacing = -0.03f }
        shrinkToFit(total, big, CONTENT, 80f)
        cv.drawText(total, PAD - 6f, y, big)
        c.deltaPct?.let { d ->
            val down = d <= 0
            val label = (if (down) "↓ " else "↑ ") + "${abs(d)}% vs last month" + if (c.soFar) " so far" else ""
            val p = paint(28f, 800, if (down) GREEN_TEXT else RED_TEXT)
            val w = p.measureText(label) + 44f
            val r = RectF(PAD, y + 30f, PAD + w, y + 86f)
            cv.drawRoundRect(r, 28f, 28f, fill(if (down) 0xFFE8F7EE.toInt() else 0xFFFFF1EA.toInt()))
            cv.drawText(label, r.left + 22f, r.centerY() + 10f, p)
        }

        // Category split: one stacked bar, then the top four with amounts.
        y += 70f + chipSpace
        val bar = RectF(PAD, y, W - PAD, y + 44f)
        val sum = c.slices.sumOf { it.amountMinor }.coerceAtLeast(1)
        cv.save()
        cv.clipPath(android.graphics.Path().apply { addRoundRect(bar, 22f, 22f, android.graphics.Path.Direction.CW) })
        cv.drawRect(bar, fill(0xFFE6E8EE.toInt()))
        var x = bar.left
        c.slices.forEach { s ->
            val w = bar.width() * s.amountMinor / sum
            cv.drawRect(x, bar.top, x + w, bar.bottom, fill(s.argb.toInt()))
            x += w
        }
        cv.restore()
        y = bar.bottom + 70f
        c.slices.take(4).forEach { s ->
            cv.drawCircle(PAD + 14f, y - 12f, 14f, fill(s.argb.toInt()))
            val name = paint(34f, 700, INK)
            cv.drawText(fit(s.name, name, 500f), PAD + 48f, y, name)
            val amt = paint(34f, 800, INK)
            val a = Money.whole(s.amountMinor, c.currency)
            val pct = paint(28f, 600, MUTED)
            val p = "  ${(s.amountMinor * 100 / sum)}%"
            cv.drawText(p, W - PAD - pct.measureText(p), y, pct)
            cv.drawText(a, W - PAD - pct.measureText(p) - amt.measureText(a), y, amt)
            y += 62f
        }

        // Habit tiles.
        val tiles = listOf(
            "${c.daysLogged}/${c.daysInPeriod}" to "days logged",
            "${c.bestStreak}" to "day best streak",
            "${c.expenses}" to "expenses logged",
        )
        val tw = (CONTENT - 2 * 24f) / 3
        tiles.forEachIndexed { i, (num, label) ->
            val r = RectF(PAD + i * (tw + 24f), tileTop, PAD + i * (tw + 24f) + tw, tileTop + 220f)
            cv.drawRoundRect(r, 40f, 40f, fill(0xCCFFFFFF.toInt()))
            val np = paint(76f, 800, listOf(ACCENT_TEXT, GREEN_TEXT, INFO_TEXT)[i])
            shrinkToFit(num, np, tw - 50f, 40f)
            cv.drawText(num, r.left + 32f, r.top + 110f, np)
            val lp = paint(26f, 600, MUTED)
            cv.drawText(fit(label, lp, tw - 50f), r.left + 32f, r.top + 165f, lp)
        }
        footer(cv, h)
        return bmp
    }

    /* ---------------- shared pieces ---------------- */

    /** Soft tinted wash with a few playful shapes, kept to the edges so they never sit behind the QR. */
    private fun background(cv: Canvas, h: Int, tint: Int) {
        cv.drawRect(0f, 0f, W.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), blend(tint, 0.10f), WHITE, Shader.TileMode.CLAMP)
        })
        cv.drawCircle(W - 90f, 70f, 230f, fill(withAlpha(tint, 0x24)))
        cv.drawCircle(W - 210f, 250f, 46f, fill(withAlpha(tint, 0x30)))
        cv.drawCircle(-40f, h * 0.48f, 120f, fill(withAlpha(INFO, 0x14)))
        // Confetti in the category colours.
        val bits = listOf(
            Triple(0.86f, 0.30f, 0xFF8B5CF6), Triple(0.93f, 0.40f, 0xFF0F9F8F), Triple(0.05f, 0.62f, 0xFFF5A524),
            Triple(0.95f, 0.66f, 0xFFEC4899), Triple(0.06f, 0.80f, 0xFF2F7BFF),
        )
        bits.forEachIndexed { i, (fx, fy, col) ->
            cv.save()
            cv.rotate(i * 37f + 15f, W * fx, h * fy)
            cv.drawRoundRect(RectF(W * fx - 14f, h * fy - 6f, W * fx + 14f, h * fy + 6f), 6f, 6f, fill(withAlpha(col.toInt(), 0x99)))
            cv.restore()
        }
    }

    private fun chipRow(cv: Canvas, text: String, top: Float): Float {
        val p = paint(32f, 700, INK)
        val t = fit(text, p, CONTENT - 60f)
        val r = RectF(PAD, top, PAD + p.measureText(t) + 56f, top + 72f)
        cv.drawRoundRect(r, 36f, 36f, fill(0xCCFFFFFF.toInt()))
        cv.drawText(t, r.left + 28f, r.centerY() + 11f, p)
        return r.bottom
    }

    private fun footer(cv: Canvas, h: Int) {
        val p = paint(28f, 600, MUTED)
        val made = "Made with "
        val brand = paint(30f, 800, INK)
        val logo = 44f
        val total = logo + 14f + p.measureText(made) + brand.measureText("Trace") + 14f
        var x = (W - total) / 2
        val base = h - 64f
        QrArt.drawLogo(context, cv, RectF(x, base - logo + 8f, x + logo, base + 8f))
        x += logo + 14f
        cv.drawText(made, x, base, p)
        x += p.measureText(made)
        cv.drawText("Trace", x, base, brand)
        x += brand.measureText("Trace") + 7f
        cv.drawCircle(x, base - 6f, 6f, fill(ACCENT))
    }

    private fun shadow(cv: Canvas, r: RectF, radius: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1F0E0F12; maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL) }
        cv.drawRoundRect(RectF(r.left, r.top + 18f, r.right, r.bottom + 18f), radius, radius, p)
    }

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    private fun blend(c: Int, f: Float): Int {
        fun ch(shift: Int) = ((((c shr shift) and 0xFF) * f) + 255 * (1 - f)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    companion object {
        const val W = 1080
        private const val PAD = 80f
        private const val CONTENT = W - 2 * PAD
        private const val INK = 0xFF0E0F12.toInt()
        private const val MUTED = 0xFF6B7080.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val ACCENT = 0xFFFF7A45.toInt()
        private const val ACCENT_TEXT = 0xFFC2410C.toInt()
        private const val GREEN = 0xFF22A06B.toInt()
        private const val GREEN_TEXT = 0xFF12804A.toInt()
        private const val RED_TEXT = 0xFFC2410C.toInt()
        private const val INFO = 0xFF5B6CFF.toInt()
        private const val INFO_TEXT = 0xFF3949AB.toInt()
    }
}
