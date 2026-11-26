package com.homeboundlabs.trace.ui.goals

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeboundlabs.trace.data.Goal
import com.homeboundlabs.trace.ui.home.WaveFill
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion

/**
 * The pot's body in a unit square: a near-round belly with a short narrow neck on top and a small
 * flat foot, the shape of a clay gullak.
 */
private fun gullakBody(w: Float, h: Float) = Path().apply {
    moveTo(0.42f * w, 0.20f * h)                                                    // neck, left
    lineTo(0.42f * w, 0.25f * h)
    cubicTo(0.18f * w, 0.29f * h, 0.06f * w, 0.45f * h, 0.06f * w, 0.60f * h)    // shoulder to belly
    cubicTo(0.06f * w, 0.80f * h, 0.22f * w, 0.93f * h, 0.36f * w, 0.95f * h)    // belly to foot
    lineTo(0.38f * w, 0.99f * h)
    lineTo(0.62f * w, 0.99f * h)
    lineTo(0.64f * w, 0.95f * h)
    cubicTo(0.78f * w, 0.93f * h, 0.94f * w, 0.80f * h, 0.94f * w, 0.60f * h)
    cubicTo(0.94f * w, 0.45f * h, 0.82f * w, 0.29f * h, 0.58f * w, 0.25f * h)
    lineTo(0.58f * w, 0.20f * h)                                                    // neck, right
    close()
}

private val GullakShape = GenericShape { size, _ -> addPath(gullakBody(size.width, size.height)) }

private val Coin = Color(0xFFF5B82E)
private val CoinEdge = Color(0xFFC98A0B)

/**
 * A gullak (clay money pot) for a savings goal: it fills with the goal's colour like water, and a coin
 * drops into the slot each time money is added.
 */
@Composable
fun Gullak(goal: Goal, size: Dp = 72.dp, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val color = goal.color
    val deep = lerp(color, Color.Black, 0.35f)

    // 0 → 1 while a coin falls in; only after money is added, never on first show.
    val coin = remember { Animatable(1f) }
    val last = remember { longArrayOf(goal.savedMinor) }
    LaunchedEffect(goal.savedMinor) {
        val added = goal.savedMinor > last[0]
        last[0] = goal.savedMinor
        if (added && !reduceMotion) {
            coin.snapTo(0f)
            coin.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }

    // Full pots twinkle: a few sparkles fade in and out around them.
    val twinkle = if (goal.reached && !reduceMotion) {
        val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "twinkle")
        inf.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween<Float>(1800, easing = androidx.compose.animation.core.LinearEasing)), label = "t").value
    } else 0.25f

    Box(modifier.size(size).semantics { contentDescription = "${goal.name}, ${(goal.fraction * 100).toInt()}% saved" }) {
        // Inside of the pot: a soft tint, then the water level.
        Box(Modifier.fillMaxSize().clip(GullakShape)) {
            Canvas(Modifier.fillMaxSize()) { drawRect(color.copy(alpha = 0.10f)) }
            WaveFill(waterLevelPct(goal.fraction), color.copy(alpha = 0.55f), goal.savedMinor, reduceMotion, Modifier.fillMaxSize())
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val body = gullakBody(w, h)
            val line = (w * 0.045f).coerceAtLeast(1.5f)

            drawPath(body, color, style = Stroke(width = line))

            // A small lip and a round knob on the neck.
            drawRoundRect(
                color, topLeft = Offset(0.38f * w, 0.155f * h), size = Size(0.24f * w, 0.055f * h),
                cornerRadius = CornerRadius(0.03f * h),
            )
            drawCircle(color, radius = 0.055f * w, center = Offset(0.5f * w, 0.115f * h))

            // Coin slot on the upper belly, and a glaze highlight that makes the pot look round.
            drawLine(deep, Offset(0.42f * w, 0.36f * h), Offset(0.58f * w, 0.36f * h), strokeWidth = line * 1.4f, cap = StrokeCap.Round)
            drawArc(
                Color.White.copy(alpha = 0.6f), startAngle = 195f, sweepAngle = 60f, useCenter = false,
                topLeft = Offset(0.16f * w, 0.38f * h), size = Size(0.5f * w, 0.5f * h),
                style = Stroke(width = line * 1.2f, cap = StrokeCap.Round),
            )

            if (goal.reached) {
                // Full to the brim: a coin sticks out of the slot...
                val r = 0.085f * w
                val c = Offset(0.5f * w, 0.36f * h - r * 0.35f)
                clipRect(bottom = 0.36f * h) {
                    drawCircle(Coin, radius = r, center = c)
                    drawCircle(CoinEdge, radius = r, center = c, style = Stroke(width = line * 0.8f))
                }
                // ...and sparkles glint around the pot, each on its own beat.
                listOf(Triple(0.08f, 0.22f, 0f), Triple(0.92f, 0.30f, 0.33f), Triple(0.86f, 0.86f, 0.66f)).forEach { (x, y, phase) ->
                    val k = kotlin.math.sin(((twinkle + phase) % 1f) * Math.PI).toFloat()
                    sparkle(Offset(x * w, y * h), 0.07f * w * (0.5f + 0.5f * k), Coin.copy(alpha = 0.35f + 0.65f * k))
                }
            }

            // The falling coin: from above the pot into the slot, fading as it goes in.
            val t = coin.value
            if (t < 1f) {
                val r = 0.09f * w
                val cy = -0.2f * h + (0.36f * h + 0.2f * h) * t
                val a = if (t < 0.8f) 1f else (1f - t) / 0.2f
                drawCircle(Coin.copy(alpha = a), radius = r, center = Offset(0.5f * w, cy))
                drawCircle(CoinEdge.copy(alpha = a), radius = r, center = Offset(0.5f * w, cy), style = Stroke(width = line * 0.8f))
            }
        }
    }
}

/** A four-pointed glint. */
private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color)
}

/**
 * How high the water stands, as a percent of the pot's box, for [fraction] of the goal saved. The pot is
 * round, widest in the middle, so the level follows how much it holds: half full sits mid-belly, not near
 * the top. The belly is treated as a circle from y = 0.22 to 0.98 of the box.
 */
internal fun waterLevelPct(fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    if (f <= 0f) return 0
    if (f >= 1f) return 100
    // Filled share of a circle up to height t (0..1 of its diameter): (θ - sin θ) / 2π, θ = 2·acos(1 - 2t).
    fun share(t: Double): Double { val th = 2 * kotlin.math.acos(1 - 2 * t); return (th - kotlin.math.sin(th)) / (2 * Math.PI) }
    var lo = 0.0
    var hi = 1.0
    repeat(30) { val mid = (lo + hi) / 2; if (share(mid) < f) lo = mid else hi = mid }
    val top = 0.98 - lo * 0.76 // y of the surface, in box units
    return ((1 - top) * 100).toInt().coerceIn(0, 100)
}
