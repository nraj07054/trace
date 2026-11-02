package com.homeboundlabs.trace.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homeboundlabs.trace.ui.theme.Motion
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.ui.components.tapNoRipple
import com.homeboundlabs.trace.ui.theme.CssEase
import com.homeboundlabs.trace.ui.theme.SpringEasing
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

// Same overall height as the bar chart, so switching themes doesn't move the page.
private const val CURVE_H = 214
private const val TOP = 46f // room for the amount bubble above the highest point
private const val BOTTOM = 16f

/**
 * The dark-mode chart: one smooth line through each day's (week's, month's) total, with a soft
 * fill under it. Tapping a column slides the marker along the curve to it; changing the period
 * grows the new curve up from the floor.
 */
@Composable
fun SpendCurve(
    buckets: List<Bucket>,
    selected: Int,
    filter: Category?,
    currency: String,
    reduceMotion: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = buckets.size
    // The line stops at today; days and months still to come are left blank.
    val live = max(1, buckets.count { !it.future })
    val totals = buckets.map { it.total(filter) }
    val maxTotal = max(1L, totals.maxOrNull() ?: 1L).toFloat()
    val haptics = LocalHapticFeedback.current

    // Each point's height as a share of the tallest (0 = floor, 1 = top).
    val targets = totals.map { it / maxTotal }
    val heights = remember(n) { List(n) { i -> Animatable(if (reduceMotion) targets[i] else 0f) } }
    LaunchedEffect(targets, heights) {
        heights.forEachIndexed { i, a ->
            launch {
                if (reduceMotion) a.snapTo(targets[i]) else a.animateTo(targets[i], tween(Motion.SLOW, easing = SpringEasing))
            }
        }
    }
    val sel = selected.coerceIn(0, live - 1)
    val marker by animateFloatAsState(sel.toFloat(), if (reduceMotion) snap() else tween(Motion.STANDARD, easing = CssEase), label = "marker")
    val line by animateColorAsState(filter?.color ?: TraceColors.Accent, tween(Motion.STANDARD), label = "line")

    val guide = TraceColors.Ghost
    val surface = TraceColors.Surface

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(CURVE_H.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                if (n == 0) return@Canvas
                val top = TOP.dp.toPx()
                val span = size.height - top - BOTTOM.dp.toPx()
                val pts = curvePoints(heights.map { it.value }, size.width, top, span).take(live)

                val curve = smoothPath(pts)
                val area = smoothPath(pts).apply {
                    lineTo(pts.last().x, size.height)
                    lineTo(pts.first().x, size.height)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.32f), line.copy(alpha = 0f)), startY = top, endY = size.height))
                drawPath(curve, line, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                val m = pointOnCurve(pts, marker)
                drawLine(
                    guide, m, Offset(m.x, size.height), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                )
                drawCircle(line.copy(alpha = 0.2f), radius = 14.dp.toPx(), center = m)
                drawCircle(surface, radius = 7.dp.toPx(), center = m)
                drawCircle(line, radius = 7.dp.toPx(), center = m, style = Stroke(width = 3.5.dp.toPx()))
            }

            if (n > 0) {
                // Amount bubble above the marker, kept inside the chart's edges.
                Text(
                    Money.whole(totals[sel], currency),
                    style = ts(12, 800, surface),
                    maxLines = 1,
                    modifier = Modifier.fillMaxSize()
                        .layout { measurable, constraints ->
                            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                val top = TOP.dp.toPx()
                                val span = constraints.maxHeight - top - BOTTOM.dp.toPx()
                                val m = pointOnCurve(curvePoints(heights.map { it.value }, constraints.maxWidth.toFloat(), top, span).take(live), marker)
                                val x = (m.x - p.width / 2f).coerceIn(0f, (constraints.maxWidth - p.width).toFloat())
                                val y = (m.y - p.height - 18.dp.toPx()).coerceAtLeast(0f)
                                p.place(x.roundToInt(), y.roundToInt())
                            }
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(line)
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }

            // One invisible tap column per point, the full chart height.
            Row(Modifier.fillMaxSize()) {
                buckets.forEachIndexed { i, b ->
                    val on = i == sel
                    val amount = Money.whole(totals[i], currency)
                    if (b.future) {
                        Box(Modifier.weight(1f).fillMaxHeight())
                        return@forEachIndexed
                    }
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .clearAndSetSemantics {
                                contentDescription = "${b.label}: $amount"
                                role = Role.Button
                                this.selected = on
                                onClick { onSelect(i); true }
                            }
                            .tapNoRipple {
                                if (i != sel) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(i)
                            },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            buckets.forEachIndexed { i, b ->
                val on = i == sel
                Text(
                    b.label,
                    modifier = Modifier.weight(1f),
                    style = ts(11, if (on) 800 else 500, if (on) TraceColors.Ink else if (b.future) TraceColors.Ghost else TraceColors.Faint),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Points at the middle of equal columns; [shares] are heights from 0 (floor) to 1 (top). */
private fun curvePoints(shares: List<Float>, width: Float, top: Float, span: Float): List<Offset> {
    val col = width / shares.size
    return shares.mapIndexed { i, s -> Offset(col * i + col / 2, top + span * (1f - s)) }
}

/** Bezier control points for the segment from [i] to [i]+1 (a Catmull-Rom curve through every point). */
private fun controls(p: List<Offset>, i: Int): Pair<Offset, Offset> {
    val p0 = p[max(0, i - 1)]
    val p1 = p[i]
    val p2 = p[i + 1]
    val p3 = p[minOf(p.lastIndex, i + 2)]
    // Keep each segment between its two points' heights, so the line never sags below the floor
    // (or bulges over a peak) on its way from a flat stretch to a rise.
    val lo = minOf(p1.y, p2.y)
    val hi = maxOf(p1.y, p2.y)
    return Offset(p1.x + (p2.x - p0.x) / 6, (p1.y + (p2.y - p0.y) / 6).coerceIn(lo, hi)) to
        Offset(p2.x - (p3.x - p1.x) / 6, (p2.y - (p3.y - p1.y) / 6).coerceIn(lo, hi))
}

private fun smoothPath(p: List<Offset>): Path = Path().apply {
    moveTo(p[0].x, p[0].y)
    for (i in 0 until p.lastIndex) {
        val (c1, c2) = controls(p, i)
        cubicTo(c1.x, c1.y, c2.x, c2.y, p[i + 1].x, p[i + 1].y)
    }
}

/** The spot on the curve at fractional index [at], so the marker rides the line between points. */
private fun pointOnCurve(p: List<Offset>, at: Float): Offset {
    if (p.size == 1) return p[0]
    val a = at.coerceIn(0f, p.lastIndex.toFloat())
    val i = floor(a).toInt().coerceAtMost(p.lastIndex - 1)
    val t = a - i
    val (c1, c2) = controls(p, i)
    val u = 1 - t
    fun bez(a0: Float, a1: Float, a2: Float, a3: Float) = u * u * u * a0 + 3 * u * u * t * a1 + 3 * u * t * t * a2 + t * t * t * a3
    return Offset(bez(p[i].x, c1.x, c2.x, p[i + 1].x), bez(p[i].y, c1.y, c2.y, p[i + 1].y))
}
