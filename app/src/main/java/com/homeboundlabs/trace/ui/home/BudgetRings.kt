package com.homeboundlabs.trace.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.SpringEasing
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion
import com.homeboundlabs.trace.ui.theme.ts
import kotlin.math.roundToInt

// Card colors from the dark "B" Home design.
private val CardBg = Color(0xFF121418)
private val CardBgOn = Color(0xFF1F2228)
private val CardBorder = Color(0xFF1C1F25)
private val Track = Color(0xFF23262D)
private val Label = Color(0xFFC9CDD6)

/** Monthly budget scaled to the span the chart shows. */
private fun budgetFor(monthly: Long, period: Period): Double = when (period) {
    Period.Week -> monthly * 7 / 30.44
    Period.Month -> monthly * 28 / 30.44 // the 1M chart is the last four weeks
    Period.HalfYear -> monthly * 6.0
    Period.Year -> monthly * 12.0
}

/**
 * Dark-mode category row, holding the same things as the light chips: an All card, then one
 * card per category, each with what was spent this period. The ring shows how much of the
 * category's budget is used, or, without a budget, its share of the period's spending.
 * Tap to filter the chart; All (or the lit card again) shows everything.
 */
@Composable
fun BudgetRings(ui: HomeUi, onPick: (Category?) -> Unit) {
    val anyBudget = ui.categories.any { (ui.budgets[it.id] ?: 0L) > 0L }
    val all = ui.chipTotals.firstOrNull() ?: 0L
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(if (anyBudget) "Budgets" else "Categories", style = ts(16, 700), modifier = Modifier.semantics { heading() })
            Text("Tap to filter", style = ts(12, 500, TraceColors.Muted))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all") {
                RingCard(
                    label = "All", color = TraceColors.Ink, pct = if (all > 0) 100 else 0, isBudget = false,
                    amount = Money.whole(all, ui.currency), on = ui.filter == null,
                ) { onPick(null) }
            }
            itemsIndexed(ui.categories, key = { _, c -> c.id }) { i, c ->
                val spent = ui.chipTotals.getOrElse(i + 1) { 0L }
                val budget = ui.budgets[c.id]?.takeIf { it > 0 }
                val pct = when {
                    budget != null -> (spent * 100 / budgetFor(budget, ui.period)).roundToInt()
                    all > 0 -> (spent * 100.0 / all).roundToInt()
                    else -> 0
                }
                RingCard(
                    label = c.label, color = c.color, pct = pct, isBudget = budget != null,
                    amount = Money.whole(spent, ui.currency), on = c == ui.filter,
                ) { onPick(if (c == ui.filter) null else c) }
            }
        }
    }
}

@Composable
private fun RingCard(label: String, color: Color, pct: Int, isBudget: Boolean, amount: String, on: Boolean, onClick: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val sweep by animateFloatAsState(pct.coerceIn(0, 100) / 100f, if (reduceMotion) snap() else tween(600), label = "ring")
    val scale by animateFloatAsState(if (on) 1.06f else 1f, if (reduceMotion) snap() else tween(300, easing = SpringEasing), label = "lift")
    val shape = RoundedCornerShape(22.dp)
    val what = if (isBudget) "$pct% of budget" else "$pct% of spending"
    Column(
        Modifier.widthIn(min = 84.dp).clip(shape)
            .background(if (on) CardBgOn else CardBg)
            .border(1.dp, if (on) color else CardBorder, shape)
            .tap(label = "$label, $amount, $what", role = Role.Tab, onClick = onClick)
            .padding(top = 12.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(58.dp).graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 7.dp.toPx()
                val inset = w / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(Track, 0f, 360f, false, tl, arcSize, style = Stroke(w))
                if (sweep > 0f) drawArc(color, -90f, 360f * sweep, false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
            // Over budget reads in the category color; the ring itself stays full.
            Text("$pct%", style = ts(12, 800, if (pct > 100) color else TraceColors.Ink))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 2.dp)) {
            Text(label, style = ts(12, 600, Label), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
            Text(amount, style = ts(14, 800, TraceColors.Ink, letterSpacing = -0.3f), maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}
