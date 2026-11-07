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
import androidx.compose.foundation.layout.height
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
import com.homeboundlabs.trace.ui.theme.EmphasizedEasing
import com.homeboundlabs.trace.ui.theme.Motion
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.SpringEasing
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion
import com.homeboundlabs.trace.ui.theme.ts
import com.homeboundlabs.trace.ui.theme.CapTextScale
import com.homeboundlabs.trace.ui.theme.scaledWithText
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Card colors from the dark "B" Home design.
private val CardBg = Color(0xFF121418)
private val CardBgOn = Color(0xFF1F2228)
private val CardBorder = Color(0xFF1C1F25)
private val Track = Color(0xFF23262D)
private val Label = Color(0xFFC9CDD6)
private val DashedRing = Color(0xFF3A3E47)

/** Ring color for budget use: the category's own color, amber near the limit, red over it. */
internal fun budgetColor(pct: Int, base: Color): Color = when {
    pct > 100 -> TraceColors.Negative
    pct >= 85 -> Color(0xFFF59E0B)
    else -> base
}

/** A category's monthly budget, shown on Home only on 1M (the one period a monthly budget fits). */
internal fun HomeUi.monthBudget(c: Category): Long? =
    if (period == Period.Month) budgets[c.id]?.takeIf { it > 0 } else null

/** "Budgets · Nov" on 1M when any budget is set, otherwise "Categories". */
internal fun HomeUi.categoriesTitle(): String {
    val anyBudget = categories.any { (budgets[it.id] ?: 0L) > 0L }
    if (period != Period.Month || !anyBudget) return "Categories"
    return "Budgets · " + LocalDate.now().month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}

/**
 * Dark-mode category row: an All card, then one card per category with what was spent in the
 * chosen period. A filled ring always means a budget: on 1M, a category with a monthly budget
 * shows how much of it is used ("of ₹5,000"); one without shows its icon in a dashed circle and
 * a "+ Budget" pill. Other periods show just the icon, since budgets are monthly.
 * Tap a card to filter the chart; All (or the lit card again) shows everything.
 */
@Composable
fun BudgetRings(ui: HomeUi, onPick: (Category?) -> Unit, onSetBudget: (Category) -> Unit) {
    val all = ui.chipTotals.firstOrNull() ?: 0L
    val month = ui.period == Period.Month
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(ui.categoriesTitle(), style = ts(16, 700), modifier = Modifier.weight(1f, fill = false).semantics { heading() })
            Text("Tap to filter", style = ts(12, 500, TraceColors.Muted))
        }
        CapTextScale {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all") {
                    CategoryCard(
                        label = "All", color = TraceColors.Ink, icon = TraceIcons.Wallet, amount = Money.whole(all, ui.currency),
                        on = ui.filter == null, pct = null, footer = Footer.None, onFooter = {},
                    ) { onPick(null) }
                }
                itemsIndexed(ui.categories, key = { _, c -> c.id }) { i, c ->
                    val spent = ui.chipTotals.getOrElse(i + 1) { 0L }
                    val budget = ui.monthBudget(c)
                    val footer = when {
                        budget != null -> Footer.Of(shortAmount(budget))
                        month -> Footer.AddBudget
                        else -> Footer.None
                    }
                    CategoryCard(
                        label = c.label, color = c.color, icon = TraceIcons.category(c), amount = Money.whole(spent, ui.currency),
                        on = c == ui.filter, pct = budget?.let { (spent * 100.0 / it).roundToInt() },
                        footer = footer, onFooter = { onSetBudget(c) },
                    ) { onPick(if (c == ui.filter) null else c) }
                }
            }
        }
    }
}

/** "5k", "12.5k", "800": a budget short enough to share the amount line. */
internal fun shortAmount(minor: Long): String {
    val whole = minor / 100
    if (whole < 1000) return whole.toString()
    val k = whole / 100 / 10.0
    return (if (k % 1.0 == 0.0) k.toLong().toString() else "%.1f".format(Locale.US, k)) + "k"
}

private sealed interface Footer {
    data object None : Footer
    data object AddBudget : Footer
    data class Of(val text: String) : Footer
}

@Composable
private fun CategoryCard(
    label: String, color: Color, icon: ImageVector, amount: String, on: Boolean,
    pct: Int?, footer: Footer, onFooter: () -> Unit, onClick: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val sweep by animateFloatAsState((pct ?: 0).coerceIn(0, 100) / 100f, if (reduceMotion) snap() else tween(Motion.SLOW, easing = EmphasizedEasing), label = "ring")
    val scale by animateFloatAsState(if (on) 1.06f else 1f, if (reduceMotion) snap() else tween(Motion.STANDARD, easing = SpringEasing), label = "lift")
    val shape = RoundedCornerShape(22.dp)
    val what = when {
        pct != null -> "$pct% of budget used"
        footer == Footer.AddBudget -> "no budget"
        else -> ""
    }
    val ringColor = budgetColor(pct ?: 0, color)
    Box(
        Modifier.widthIn(min = 84.dp).clip(shape)
            .background(if (on) CardBgOn else CardBg)
            .border(1.dp, if (on) color else CardBorder, shape)
            .tap(label = "$label, $amount, $what", role = Role.Tab, onClick = onClick),
    ) {
    Column(
        Modifier.padding(top = 12.dp, bottom = 10.dp).align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(scaledWithText(58.dp)).graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                if (pct != null) {
                    val w = 7.dp.toPx()
                    val arc = Size(size.width - w, size.height - w)
                    val tl = Offset(w / 2, w / 2)
                    drawArc(Track, 0f, 360f, false, tl, arc, style = Stroke(w))
                    if (sweep > 0f) drawArc(ringColor, -90f, 360f * sweep, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
                } else if (footer == Footer.AddBudget) {
                    // Dashed and empty: nothing to fill until a budget is set.
                    val w = 1.5.dp.toPx()
                    drawCircle(
                        DashedRing, radius = size.minDimension / 2 - 3.dp.toPx(),
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))),
                    )
                } else {
                    drawCircle(color.copy(alpha = 0.14f), radius = size.minDimension / 2 - 6.dp.toPx())
                }
            }
            if (pct != null) Text("$pct%", style = ts(12, 800, if (pct > 100) ringColor else TraceColors.Ink))
            else TIcon(icon, color, scaledWithText(20.dp))
            if (footer == Footer.AddBudget) {
                // A small "+" badge on the circle; its own tap sets a budget instead of filtering.
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(22.dp).clip(CircleShape)
                        .background(if (on) CardBgOn else CardBg).padding(2.dp).clip(CircleShape).background(color)
                        .tap(label = "Set a $label budget", role = Role.Button, onClick = onFooter),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(10.dp)) {
                        val w = 2.dp.toPx()
                        drawLine(Color.White, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w, StrokeCap.Round)
                        drawLine(Color.White, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w, StrokeCap.Round)
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 2.dp)) {
            Text(label, style = ts(12, 600, Label), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp))
            // A budget rides on the amount line ("₹611 / 5k"), so budget cards stay as short as the rest.
            Text(
                buildAnnotatedString {
                    append(amount)
                    if (footer is Footer.Of) withStyle(SpanStyle(color = TraceColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)) { append(" / " + footer.text) }
                },
                style = ts(14, 800, TraceColors.Ink, letterSpacing = -0.3f), maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
    }
    }
}
