package com.homeboundlabs.trace.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.homeboundlabs.trace.ui.theme.SpringEasing
import kotlin.math.PI
import kotlin.math.sin

/**
 * Light-mode budget fill: the chip fills from the bottom to [pct]% of the month's budget, like
 * a tank. When the level changes (opening Home, switching to 1M, adding a spend) the surface
 * sloshes for a couple of seconds, then settles flat, so nothing keeps moving on screen. A change
 * of [replay] (the chip being selected or deselected) sloshes it again.
 */
@Composable
fun WaveFill(pct: Int, color: Color, replay: Any, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(
        pct.coerceIn(0, 100) / 100f,
        if (reduceMotion) snap() else tween(1200, easing = SpringEasing),
        label = "level",
    )
    // 0 → 1 over the slosh; the wave's height fades out as it runs.
    val slosh = remember { Animatable(1f) }
    LaunchedEffect(pct, replay, reduceMotion) {
        if (reduceMotion) { slosh.snapTo(1f); return@LaunchedEffect }
        slosh.snapTo(0f)
        slosh.animateTo(1f, tween(2400, easing = LinearEasing))
    }
    Canvas(modifier) {
        if (level <= 0f) return@Canvas
        val t = slosh.value
        val amp = 3.dp.toPx() * (1f - t)
        val wavelength = size.width / 1.5f
        val phase = t * 4f * PI.toFloat()
        val top = size.height * (1f - level)
        val path = Path().apply {
            moveTo(0f, size.height)
            var x = 0f
            val step = 4.dp.toPx()
            while (x <= size.width + step) {
                lineTo(x, top + amp * sin(2f * PI.toFloat() * x / wavelength + phase))
                x += step
            }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, color)
    }
}
