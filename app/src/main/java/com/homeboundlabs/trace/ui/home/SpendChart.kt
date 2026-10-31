package com.homeboundlabs.trace.ui.home

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.layout
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.ui.components.tapNoRipple
import com.homeboundlabs.trace.ui.theme.CssEase
import com.homeboundlabs.trace.ui.theme.SpringEasing
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts
import kotlin.math.max
import kotlin.math.roundToInt

private const val CHART_H = 214
private const val BAR_MAX = 168f
private const val BAR_MIN = 12f
private const val FADED = 0.3f

private fun barWidth(count: Int) = when (count) { 7 -> 30; 4 -> 52; 6 -> 36; 12 -> 18; else -> 24 }

@Composable
fun SpendChart(
    buckets: List<Bucket>,
    categories: List<Category>,
    selected: Int,
    filter: Category?,
    currency: String,
    reduceMotion: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Dark mode shows the week as a line instead of bars.
    if (TraceColors.isDark) {
        SpendCurve(buckets, selected, filter, currency, reduceMotion, onSelect, modifier)
        return
    }
    val totals = buckets.map { it.total(filter) }
    val maxTotal = max(1L, totals.maxOrNull() ?: 1L).toFloat()
    val avg = if (totals.isEmpty()) 0f else totals.sum().toFloat() / totals.size
    val avgH = (avg / maxTotal * BAR_MAX).roundToInt().toFloat()
    val w = barWidth(buckets.size)
    val haptics = LocalHapticFeedback.current

    val avgAnim by animateFloatAsState(avgH, if (reduceMotion) snap() else tween(500, easing = CssEase), label = "avg")

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(CHART_H.dp)) {
            Canvas(
                Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomStart)
                    .offset { IntOffset(0, -avgAnim.dp.roundToPx()) },
            ) {
                drawLine(
                    TraceColors.Dash, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                buckets.forEachIndexed { i, b ->
                    key(i) {
                        val t = totals[i]
                        val h = max(BAR_MIN, (t / maxTotal * BAR_MAX).roundToInt().toFloat())
                        Bar(
                            bucket = b, categories = categories, index = i, heightDp = h, width = w, selected = i == selected,
                            filter = filter, amountText = Money.whole(t, currency), reduceMotion = reduceMotion,
                            onClick = {
                                if (i != selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(i)
                            },
                        )
                    }
                }
            }
            // On top of the bars (it takes no taps), on the left: today's bubble is usually on the right.
            AvgTag(
                "avg " + Money.whole(avg.toDouble(), currency), visible = avg > 0f, reduceMotion = reduceMotion,
                modifier = Modifier.align(Alignment.BottomStart).offset { IntOffset(0, -(avgAnim + 4).dp.roundToPx()) },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            buckets.forEachIndexed { i, b ->
                val on = i == selected
                Text(
                    b.label,
                    modifier = Modifier.width(w.dp).wrapContentWidth(unbounded = true),
                    style = ts(11, if (on) 800 else 500, if (on) TraceColors.Ink else TraceColors.Faint),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The "avg ₹1,901" label for both charts: a small pill in the page color, so it stays readable
 * where it crosses a bar or the line. Fades out when there's nothing to average (a filter with
 * no spending), instead of sitting on the floor as "avg ₹0".
 */
@Composable
internal fun AvgTag(text: String, visible: Boolean, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, if (reduceMotion) snap() else tween(200), label = "avgTag")
    Text(
        text,
        style = ts(10, 700, TraceColors.Muted),
        maxLines = 1,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(6.dp))
            .background(TraceColors.Surface.copy(alpha = 0.88f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun Bar(
    bucket: Bucket,
    categories: List<Category>,
    index: Int,
    heightDp: Float,
    width: Int,
    selected: Boolean,
    filter: Category?,
    amountText: String,
    reduceMotion: Boolean,
    onClick: () -> Unit,
) {
    // Grows in from zero on first show, then springs between heights.
    val height = remember { Animatable(if (reduceMotion) heightDp else 0f) }
    LaunchedEffect(heightDp) {
        if (reduceMotion) height.snapTo(heightDp) else height.animateTo(heightDp, tween(600, easing = SpringEasing))
    }
    val scaleX by animateFloatAsState(
        if (selected) 1.12f else 1f,
        if (reduceMotion) snap() else tween(350, easing = SpringEasing), label = "scale",
    )
    val h = { height.value.coerceAtLeast(0f) }
    val segs = categories.filter { bucket.of(it) > 0 && (filter == null || it.id == filter.id) }

    Box(
        Modifier.width(width.dp).fillMaxHeight().zIndex(if (selected) 1f else 0f)
            .clearAndSetSemantics {
                contentDescription = "${bucket.label}: $amountText"
                role = Role.Button
                this.selected = selected
                onClick { onClick(); true }
            }
            .tapNoRipple(onClick),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (selected) {
            Text(
                amountText,
                style = ts(12, 700, TraceColors.OnInk),
                maxLines = 1,
                modifier = Modifier
                    .wrapContentWidth(unbounded = true)
                    .offset { IntOffset(0, -(h() + 10).dp.roundToPx()) }
                    .clip(RoundedCornerShape(12.dp))
                    .background(TraceColors.Ink)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Column(
            Modifier
                .layout { measurable, constraints ->
                    // Height read during layout: springing re-measures the bar without recomposing it.
                    val px = h().dp.roundToPx()
                    val p = measurable.measure(constraints.copy(minHeight = px, maxHeight = px))
                    layout(p.width, px) { p.place(0, 0) }
                }
                .width(width.dp)
                .graphicsLayer {
                    this.scaleX = scaleX
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(RoundedCornerShape(50))
                .background(TraceColors.Fill),
        ) {
            if (segs.isEmpty()) {
                Box(Modifier.fillMaxSize().background(TraceColors.EmptyBar))
            } else {
                segs.forEach { c ->
                    key(c.id) {
                        val alpha by animateFloatAsState(
                            if (selected) 1f else FADED,
                            if (reduceMotion) snap() else tween(300, easing = CssEase), label = "alpha",
                        )
                        Box(
                            Modifier.fillMaxWidth().weight(bucket.of(c).toFloat())
                                .graphicsLayer { this.alpha = alpha }.background(c.color),
                        )
                    }
                }
            }
        }
    }
}
