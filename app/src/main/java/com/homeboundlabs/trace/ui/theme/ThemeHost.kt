package com.homeboundlabs.trace.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homeboundlabs.trace.data.ThemeMode
import com.homeboundlabs.trace.traceApp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Dark for [mode], given whether the phone itself is in dark mode. */
fun isDarkFor(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> systemDark
}

private const val DURATION_MS = 1100
private val Sky = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)

/**
 * Hosts the app and switches it between light and dark when the saved [ThemeMode] (or the
 * phone's own setting) changes.
 *
 * Going dark is a sunset: night comes down from the top and the light sinks away below it.
 * Going light is a sunrise: day comes up from the bottom. A soft orange glow rides the line
 * between the two. It works by taking a picture of the screen in the old colors, switching the
 * real screen underneath, then wiping the picture away along that line.
 *
 * [onDark] runs when the status bar should change: at the start of a sunset (the top goes
 * dark first) and at the end of a sunrise (the top goes light last).
 */
@Composable
fun ThemeHost(onDark: (Boolean) -> Unit, content: @Composable () -> Unit) {
    val app = LocalContext.current.traceApp
    val mode by app.prefs.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val reduceMotion = rememberReduceMotion()
    val onDarkNow by rememberUpdatedState(onDark)

    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(1f) }
    var before by remember { mutableStateOf<ImageBitmap?>(null) }
    var sunset by remember { mutableStateOf(true) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(mode, systemDark) {
        val dark = isDarkFor(mode, systemDark)
        if (dark == TraceColors.isDark) return@LaunchedEffect
        // A switch mid-animation finishes the old one at once and starts again from there.
        job?.cancel()
        before = null
        job = scope.launch {
            if (reduceMotion) {
                TraceColors.isDark = dark
                onDarkNow(dark)
                return@launch
            }
            val shot = layer.toImageBitmap()
            sunset = dark
            progress.snapTo(0f)
            before = shot
            TraceColors.isDark = dark
            if (dark) onDarkNow(true)
            progress.animateTo(1f, tween(DURATION_MS, easing = Sky))
            before = null
            if (!dark) onDarkNow(false)
        }
    }

    val glow = TraceColors.Accent
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                // Keep a recording of the screen, so there's always a "before" picture to take.
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        ) { content() }
        val shot = before
        if (shot != null) {
            // Draws only; it doesn't take touches, so the app stays usable during the second it runs.
            Canvas(Modifier.fillMaxSize()) { drawHorizon(shot, progress.value, sunset, glow) }
        }
    }
}

/**
 * The old screen [before], shown only on its side of a moving horizon.
 * [t] runs 0 → 1. Sunset: the horizon falls from above the top to below the bottom, and the old
 * (light) screen is what's left below it. Sunrise: it climbs from the bottom, and the old (dark)
 * screen is what's left above it.
 */
private fun DrawScope.drawHorizon(before: ImageBitmap, t: Float, sunset: Boolean, glow: Color) {
    val h = size.height
    val feather = 140f * density // soft edge, so it reads as dusk rather than a hard wipe
    val edge = if (sunset) -feather + (h + 2 * feather) * t else h + feather - (h + 2 * feather) * t

    // Old screen, faded out across the feather on the new theme's side.
    drawIntoCanvas { canvas ->
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), Paint())
        drawImage(before, dstSize = IntSize(size.width.roundToInt(), h.roundToInt()))
        val keep = if (sunset) listOf(Color.Transparent, Color.Black) else listOf(Color.Black, Color.Transparent)
        drawRect(
            Brush.verticalGradient(keep, startY = edge - feather / 2, endY = edge + feather / 2),
            blendMode = BlendMode.DstIn,
        )
        canvas.restore()
    }

    // The sun's glow along the horizon: strongest halfway, gone at both ends.
    val strength = 0.28f * sin(PI * t).toFloat()
    if (strength > 0.01f) {
        val band = 180f * density
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.5f to glow.copy(alpha = strength),
                1f to Color.Transparent,
                startY = edge - band,
                endY = edge + band,
            ),
            topLeft = Offset(0f, edge - band),
            size = Size(size.width, 2 * band),
        )
    }
}
