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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.lerp
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
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Dark for [mode], given whether the phone itself is in dark mode. */
fun isDarkFor(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> systemDark
}

/**
 * Where on screen the next light/dark switch starts, in window coordinates. Set by whatever was
 * tapped (the sun/moon toggle, a Settings option) just before it changes the theme; read once by
 * [ThemeHost]. A switch with no origin (the phone's own setting) starts from the top-right corner.
 */
object ThemeOrigin {
    internal var next: Offset? = null

    fun set(center: Offset) {
        if (center.isSpecified) next = center
    }
}

/** Remembers this element's center, so a tap on it can start the theme switch from there. */
fun Modifier.themeOrigin(onCenter: (Offset) -> Unit): Modifier =
    this.onGloballyPositioned { onCenter(it.boundsInRoot().center) }

private const val DURATION_MS = 1100
// How far through each animation the new color has covered the status bar.
private const val SUNSET_STATUS_AT = 0.45f
private const val SUNRISE_STATUS_AT = 0.55f
private val Sky = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)

/**
 * Hosts the app and switches it between light and dark when the saved [ThemeMode] (or the
 * phone's own setting) changes.
 *
 * Going dark is a sunset: night spreads out of whatever was tapped (the sun/moon toggle, or
 * the Settings option) in a widening circle. Going light is the sunrise, its exact reverse: the
 * night shrinks back into that spot and the day is left behind. The sun's glow rides the edge either way. It works by taking a picture of the screen in the old colors, switching the
 * real screen underneath, then wiping the picture away along that line.
 *
 * [onDark] runs when the status bar should change: once the new color has covered the top edge.
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
    var origin by remember { mutableStateOf<Offset?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(mode, systemDark) {
        val dark = isDarkFor(mode, systemDark)
        if (dark == TraceColors.isDark) {
            ThemeOrigin.next = null // nothing to animate, so don't keep a stale start point
            return@LaunchedEffect
        }
        // A switch mid-animation finishes the old one at once and starts again from there.
        job?.cancel()
        before = null
        job = scope.launch {
            if (reduceMotion) {
                ThemeOrigin.next = null
                TraceColors.isDark = dark
                onDarkNow(dark)
                return@launch
            }
            val shot = layer.toImageBitmap()
            sunset = dark
            origin = ThemeOrigin.next
            ThemeOrigin.next = null
            progress.snapTo(0f)
            before = shot
            TraceColors.isDark = dark
            // The circle has crossed the whole top edge about here, so the clock and battery
            // icons switch then, both ways.
            var flipped = false
            progress.animateTo(1f, tween(DURATION_MS, easing = Sky)) {
                if (!flipped && value >= (if (dark) SUNSET_STATUS_AT else SUNRISE_STATUS_AT)) { flipped = true; onDarkNow(dark) }
            }
            before = null
            if (!flipped) onDarkNow(dark)
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
            Canvas(Modifier.fillMaxSize()) { drawHorizon(shot, progress.value, sunset, glow, origin) }
        }
    }
}

/**
 * The night as a circle around [origin] (the tapped toggle; the top-right corner if none).
 * [t] runs 0 → 1. Sunset: the circle grows out of the origin until it covers the screen, and the
 * old (light) screen [before] shows only outside it. Sunrise: the circle starts over the whole
 * screen and shrinks back into the origin, and the old (dark) screen shows only inside it.
 * The edge is crisp while the circle is small, so it clearly comes out of the icon, and softens
 * as it grows.
 */
private fun DrawScope.drawHorizon(before: ImageBitmap, t: Float, sunset: Boolean, glow: Color, origin: Offset?) {
    val w = size.width
    val h = size.height
    val center = origin ?: Offset(w, 0f)
    val maxFeather = 120f * density
    // Far enough to pass the farthest corner with the soft edge included.
    val reach = maxOf(
        hypot(center.x, center.y), hypot(w - center.x, center.y),
        hypot(center.x, h - center.y), hypot(w - center.x, h - center.y),
    ) + maxFeather
    val grow = if (sunset) t else 1f - t
    val r = reach * grow
    val feather = (12f * density + r * 0.15f).coerceAtMost(maxFeather)

    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        drawImage(before, dstSize = IntSize(w.roundToInt(), h.roundToInt()))
        val outer = (r + feather / 2).coerceAtLeast(1f)
        val inner = ((r - feather / 2) / outer).coerceIn(0f, 0.999f)
        // Keep the old screen outside the night (sunset) or inside it (sunrise).
        val (inside, outside) = if (sunset) Color.Transparent to Color.Black else Color.Black to Color.Transparent
        drawRect(
            Brush.radialGradient(0f to inside, inner to inside, 1f to outside, center = center, radius = outer),
            blendMode = BlendMode.DstIn,
        )
        canvas.restore()
    }

    // The sun's glow along the edge: strongest halfway, gone at both ends.
    val strength = (if (sunset) 0.30f else 0.22f) * sin(PI * t).toFloat()
    if (strength > 0.01f && r > 0f) {
        val band = (24f * density + r * 0.2f).coerceAtMost(160f * density)
        val outer = r + band
        val mid = (r / outer).coerceIn(0f, 1f)
        val inner = ((r - band) / outer).coerceIn(0f, mid)
        val tint = if (sunset) glow else lerp(glow, Color(0xFFFFD27A), 0.5f)
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent,
                inner to Color.Transparent,
                mid to tint.copy(alpha = strength),
                1f to Color.Transparent,
                center = center,
                radius = outer,
            ),
        )
    }
}
