package com.trace.app.data

import android.icu.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

val CURRENCIES = listOf("₹", "$", "€", "£")

/** Formats amounts as in the designs: symbol + grouped whole number, Indian grouping (1,23,456) for ₹. */
object Money {
    private val inFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN"))
    private val usFormat = NumberFormat.getIntegerInstance(Locale.US)

    fun group(whole: Long, currency: String): String =
        (if (currency == "₹") inFormat else usFormat).format(whole)

    /** Rounded to whole units, e.g. "₹2,140". */
    fun whole(minor: Long, currency: String): String = currency + group((minor / 100.0).roundToLong(), currency)

    fun whole(minor: Double, currency: String): String = currency + group((minor / 100.0).roundToLong(), currency)

    /** Whole units, plus ".xx" only when there are cents. */
    fun compact(minor: Long, currency: String): String {
        val cents = abs(minor % 100)
        return currency + group(minor / 100, currency) + if (cents != 0L) ".%02d".format(cents) else ""
    }

    /** Splits into ("₹12,345", ".00") so the cents can be drawn in the ghost color. */
    fun split(minor: Long, currency: String): Pair<String, String> =
        currency + group(minor / 100, currency) to ".%02d".format(abs(minor % 100))

    /** Parses keypad input like "450.5" into minor units. */
    fun parseMinor(text: String): Long {
        val clean = text.trim().trimEnd('.')
        if (clean.isEmpty()) return 0
        val parts = clean.split('.')
        val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return 0
        val frac = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0
        return whole * 100 + frac
    }

    /** Minor units back to keypad text: 45000 → "450", 45050 → "450.5", 45005 → "450.05". */
    fun keypadInput(minor: Long): String {
        val cents = minor % 100
        return (minor / 100).toString() + when {
            cents == 0L -> ""
            cents % 10 == 0L -> "." + (cents / 10)
            else -> ".%02d".format(cents)
        }
    }

    /** Formats raw keypad text with grouping on the whole part, keeping typed decimals ("1,240.5"). */
    fun keypadText(raw: String, currency: String): String {
        val amt = raw.ifEmpty { "0" }
        val parts = amt.split('.')
        val whole = group(parts[0].ifEmpty { "0" }.toLong(), currency)
        return whole + if (parts.size > 1) "." + parts[1] else ""
    }

    /** Keypad rules from the handoff: ≤2 decimals, ≤8 chars, a leading "0" is replaced. */
    fun press(current: String, key: String): String = when (key) {
        "del" -> current.dropLast(1)
        "." -> if ('.' in current) current else current.ifEmpty { "0" } + "."
        else -> {
            val dot = current.indexOf('.')
            when {
                dot >= 0 && current.length - dot > 2 -> current
                current.length >= 8 -> current
                current == "0" -> key
                else -> current + key
            }
        }
    }
}
