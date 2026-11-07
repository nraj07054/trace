package com.homeboundlabs.trace.ui.insights

import com.homeboundlabs.trace.ui.theme.ButtonPadding
import androidx.compose.animation.core.Animatable
import com.homeboundlabs.trace.ui.theme.EmphasizedEasing
import com.homeboundlabs.trace.ui.theme.Motion
import com.homeboundlabs.trace.ui.theme.axisOut
import com.homeboundlabs.trace.ui.theme.axisIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.ui.components.Keypad
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion
import com.homeboundlabs.trace.ui.theme.ts
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Which budget editor is open: the list of all budgets, or one category (with an optional suggested amount). */
private sealed interface Editing {
    data object List : Editing
    data class One(val categoryId: String, val suggested: Long?) : Editing
}

@Composable
fun InsightsScreen(vm: InsightsViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()
    var editing by remember { mutableStateOf<Editing?>(null) }

    Column(
        Modifier.fillMaxSize().background(TraceColors.Surface).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 120.dp), // room for the tab bar
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthSwitcher(ui, vm::previousMonth, vm::nextMonth)
        Spacer(Modifier.height(2.dp))
        // Months slide along a shared axis: back from the left, forward from the right.
        AnimatedContent(
            targetState = ui,
            contentKey = { it.month },
            transitionSpec = {
                val d = if (targetState.month > initialState.month) 1 else -1
                when {
                    !initialState.loaded -> EnterTransition.None togetherWith ExitTransition.None
                    reduceMotion -> fadeIn(tween(Motion.QUICK)) togetherWith fadeOut(tween(Motion.QUICK))
                    else -> axisIn(d) togetherWith axisOut(d)
                }
            },
            label = "month",
        ) { shown ->
            MonthBody(
                shown, reduceMotion,
                onOpenThisMonth = vm::showCurrentMonth,
                onTip = { action ->
                    when (action) {
                        is TipAction.SetBudget -> editing = Editing.One(action.category.id, action.amountMinor)
                        TipAction.OpenBudgets -> editing = Editing.List
                    }
                },
                onDismissTip = vm::dismissTip,
                onBudgets = { editing = Editing.List },
            )
        }
    }

    when (val e = editing) {
        Editing.List -> BudgetsSheet(ui, onPick = { editing = Editing.One(it.id, null) }, onDismiss = { editing = null })
        is Editing.One -> {
            val row = ui.budgetRows.firstOrNull { it.category.id == e.categoryId }
            if (row == null) editing = null
            else BudgetSheet(
                category = row.category,
                current = row.limitMinor,
                suggested = e.suggested,
                lastMonth = ui.lastMonthByCategory[row.category.id] ?: 0L,
                previousMonthName = ui.previousMonthName,
                currency = ui.currency,
                onSave = { vm.setBudget(row.category.id, it); editing = null },
                onDismiss = { editing = null },
            )
        }
        null -> {}
    }
}

/** Everything below the month switcher, for one month. */
@Composable
private fun MonthBody(
    ui: InsightsUi,
    reduceMotion: Boolean,
    onOpenThisMonth: () -> Unit,
    onTip: (TipAction) -> Unit,
    onDismissTip: (Tip) -> Unit,
    onBudgets: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ui.loaded && !ui.hasAnyData) {
            Note("Your insights start with your first expense.")
        }
        ui.soFar?.let { SoFarLine(it, ui, onOpenThisMonth) }
        ui.tip?.let { tip ->
            TipCard(tip, ui.currency, onAct = { tip.action?.let(onTip) }, onDismiss = { onDismissTip(tip) })
        }
        if (ui.span != null) {
            if (ui.totalMinor == 0L) Note("Nothing spent yet in ${monthName(ui.month)}.")
            else TotalCard(ui)
        }
        if (ui.slices.any { it.nowMinor > 0 }) CategoryCard(ui)
        ui.warning?.let { WarningCard(it) }
        if (ui.allGood) AllGoodCard()
        // Hidden for a past month that was only partly tracked: it has budgets, but no fair result.
        if (ui.hasAnyData && (ui.budget != null || ui.budgetRows.none { it.limitMinor != null })) {
            BudgetCard(ui, reduceMotion, onBudgets)
        }
    }
}

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private fun monthName(m: YearMonth) = m.month.getDisplayName(TextStyle.FULL, Locale.US)
private fun shortMonth(m: YearMonth) = m.month.getDisplayName(TextStyle.SHORT, Locale.US)

@Composable
private fun MonthSwitcher(ui: InsightsUi, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Arrow("Previous month", ui.canGoBack, flip = false, onPrev)
        Text(ui.month.format(monthFmt), style = ts(18, 800), textAlign = TextAlign.Center, modifier = Modifier.weight(1f).semantics { heading() })
        Arrow("Next month", ui.canGoForward, flip = true, onNext)
    }
}

@Composable
private fun Arrow(label: String, enabled: Boolean, flip: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(TraceColors.RoundButton)
            .then(if (enabled) Modifier.tap(label = label, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        TIcon(
            TraceIcons.Back, if (enabled) TraceColors.Ink else TraceColors.Ghost, 16.dp,
            modifier = Modifier.graphicsLayer { scaleX = if (flip) -1f else 1f },
            contentDescription = label,
        )
    }
}

/* ---------- building blocks ---------- */

@Composable
private fun Card(modifier: Modifier = Modifier, bg: Color = TraceColors.Surface, border: Color = TraceColors.Border, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(bg).border(1.dp, border, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun Note(text: String) {
    Card { Text(text, style = ts(14, 500, TraceColors.Muted)) }
}

/** A small rounded tag: [fg] text on [bg]. */
@Composable
private fun Tag(text: String, fg: Color, bg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(11.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { TIcon(it, fg, 11.dp) }
        Text(text, style = ts(12, 700, fg))
    }
}

private val WarnBg get() = if (TraceColors.isDark) Color(0xFF2A1F10) else Color(0xFFFFF4E5)
private val WarnBorder get() = if (TraceColors.isDark) Color(0xFF4A3410) else Color(0xFFFDE3BF)
private val WarnTitle get() = if (TraceColors.isDark) Color(0xFFF8D08A) else Color(0xFF7A3E00)
private val WarnText get() = if (TraceColors.isDark) Color(0xFFC9A866) else Color(0xFF8A4B00)
private val WarnIcon get() = if (TraceColors.isDark) Color(0xFFF5B547) else Color(0xFFB45309)
private val Amber = Color(0xFFF59E0B)

/* ---------- 1. tip ---------- */

@Composable
private fun TipCard(tip: Tip, currency: String, onAct: () -> Unit, onDismiss: () -> Unit) {
    // A dark card in light mode; a raised one in dark mode, so it always stands out at the top.
    val bg = if (TraceColors.isDark) Color(0xFF1F2228) else TraceColors.Ink
    val fg = if (TraceColors.isDark) TraceColors.Ink else TraceColors.OnInk
    val sub = if (TraceColors.isDark) TraceColors.Muted else Color(0xFFB8BCC6)
    Card(bg = bg, border = bg) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(TraceColors.Accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                TIcon(TraceIcons.Bulb, TraceColors.Accent, 18.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tip.heading, style = ts(12, 600, sub))
                Text(tip.text, style = ts(15, 700, fg))
                if (tip.action != null) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val label = when (val a = tip.action) {
                            is TipAction.SetBudget -> a.amountMinor?.let { "Set ${Money.whole(it, currency)}" } ?: "Set budget"
                            TipAction.OpenBudgets -> "Set budgets"
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(fg).tap(label = label, onClick = onAct)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text(label, style = ts(13, 700, bg)) }
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).tap(label = "Not now", onClick = onDismiss).padding(horizontal = 8.dp, vertical = 8.dp)) {
                            Text("Not now", style = ts(13, 600, sub))
                        }
                    }
                }
            }
        }
    }
}

/* ---------- 2. total + comparison ---------- */

@Composable
private fun TotalCard(ui: InsightsUi) {
    val span = ui.span ?: return
    val name = monthName(ui.month)
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (ui.isCurrentMonth) "Spent so far in $name" else "Spent in $name",
                style = ts(13, 600, TraceColors.Muted),
            )
            Text(Money.whole(ui.totalMinor, ui.currency), style = ts(32, 800, letterSpacing = -0.8f))
            Text(span.label, style = ts(12, 500, TraceColors.Muted))
        }
        val c = ui.comparison
        when {
            c != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val diff = ui.totalMinor - c.totalMinor
                when {
                    diff < 0 -> Tag("${Money.whole(-diff, ui.currency)} less", TraceColors.Positive, TraceColors.PositiveBg, TraceIcons.ArrowDown)
                    diff > 0 -> Tag("${Money.whole(diff, ui.currency)} more", TraceColors.Negative, TraceColors.NegativeBg, TraceIcons.ArrowUp)
                    else -> Tag("Same", TraceColors.Muted, TraceColors.Fill)
                }
                Text("than ${c.span.label} (${Money.whole(c.totalMinor, ui.currency)})", style = ts(12, 500, TraceColors.Muted))
            }
            ui.trackedSince != null -> Text(
                "Tracking since ${shortMonth(ui.month)} ${ui.trackedSince.dayOfMonth}" +
                    (ui.categoriesFrom?.let { " · more insights on ${shortMonth(ui.month)} ${it.dayOfMonth}" } ?: ""),
                style = ts(12, 500, TraceColors.Muted),
            )
            ui.comparesFrom != null -> Text(
                "More insights after your first week, on ${shortMonth(ui.month)} ${ui.comparesFrom.dayOfMonth}.",
                style = ts(12, 500, TraceColors.Muted),
            )
            ui.isCurrentMonth && ui.comparisonsFrom != null ->
                Text("From ${monthName(ui.comparisonsFrom)}, you'll see how each month compares.", style = ts(12, 500, TraceColors.Muted))
            ui.categoriesFrom != null ->
                Text("More insights after your first week, on ${shortMonth(ui.month)} ${ui.categoriesFrom.dayOfMonth}.", style = ts(12, 500, TraceColors.Muted))
        }
    }
}

/** "November so far: ₹97 · more on Nov 7", above last month's report; tap to open this month. */
@Composable
private fun SoFarLine(f: SoFar, ui: InsightsUi, onOpen: () -> Unit) {
    val m = YearMonth.from(f.span.to)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TraceColors.Fill)
            .tap(label = "Open ${monthName(m)}", onClick = onOpen).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${monthName(m)} so far: ${Money.whole(f.totalMinor, ui.currency)} · more on ${shortMonth(m)} ${f.fullFrom.dayOfMonth}",
            style = ts(12, 600, TraceColors.Muted), modifier = Modifier.weight(1f),
        )
        TIcon(TraceIcons.Back, TraceColors.Muted, 12.dp, modifier = Modifier.graphicsLayer { scaleX = -1f })
    }
}

/* ---------- 3. by category ---------- */

@Composable
private fun CategoryCard(ui: InsightsUi) {
    val span = ui.span ?: return
    val c = ui.comparison
    // One scale for every bar on the card, so lengths compare across rows.
    val top = ui.slices.maxOf { maxOf(it.nowMinor, it.beforeMinor ?: 0) }.coerceAtLeast(1)
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("By category", style = ts(15, 800), modifier = Modifier.semantics { heading() })
            Text(if (c != null) "${span.label} vs ${c.span.label}" else span.label, style = ts(12, 500, TraceColors.Muted))
        }
        ui.slices.forEach { s ->
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(s.category.color))
                    Text(s.category.name, style = ts(14, 700))
                    if (s.nowMinor > 0) Text("${s.sharePct}%", style = ts(12, 500, TraceColors.Muted))
                    Spacer(Modifier.weight(1f))
                    val b = s.beforeMinor
                    if (b == null) Text(Money.whole(s.nowMinor, ui.currency), style = ts(13, 700))
                    else {
                        val d = s.nowMinor - b
                        Text(
                            when { d > 0 -> "${Money.whole(d, ui.currency)} more"; d < 0 -> "${Money.whole(-d, ui.currency)} less"; else -> "same" },
                            style = ts(13, 700, when { d > 0 -> TraceColors.Negative; d < 0 -> TraceColors.Positive; else -> TraceColors.Muted }),
                        )
                    }
                }
                val b = s.beforeMinor
                if (b == null) Bar(s.nowMinor.toFloat() / top, s.category.color)
                else {
                    // Each bar carries its own month and amount, so no key is needed.
                    LabelledBar(s.nowMinor.toFloat() / top, s.category.color, shortMonth(ui.month), Money.whole(s.nowMinor, ui.currency), strong = true)
                    LabelledBar(b.toFloat() / top, TraceColors.Fill, shortMonth(ui.month.minusMonths(1)), Money.whole(b, ui.currency), strong = false)
                }
            }
        }
    }
}

/** A bar with "Oct ₹10,811" beside it. The label column has a fixed width, so bars line up across rows. */
@Composable
private fun LabelledBar(fraction: Float, color: Color, month: String, amount: String, strong: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { Bar(fraction, color) }
        Row(Modifier.width(96.dp), horizontalArrangement = Arrangement.End) {
            Text("$month ", style = ts(12, 500, TraceColors.Muted), maxLines = 1)
            Text(amount, style = ts(12, if (strong) 700 else 500, if (strong) TraceColors.Ink else TraceColors.Muted), maxLines = 1)
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, height: Dp = 7.dp) {
    Box(Modifier.fillMaxWidth().height(height)) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(height).clip(CircleShape).background(color))
    }
}

/* ---------- 4. warning / all good ---------- */

@Composable
private fun WarningCard(w: Warning) {
    Card(bg = WarnBg, border = WarnBorder) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TIcon(TraceIcons.Alert, WarnIcon, 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(w.title, style = ts(14, 700, WarnTitle))
                Text(w.detail, style = ts(12, 500, WarnText))
            }
        }
    }
}

@Composable
private fun AllGoodCard() {
    Card(bg = TraceColors.PositiveBg, border = TraceColors.PositiveBg) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TIcon(TraceIcons.CheckCircle, TraceColors.Positive, 20.dp)
            Text("All good this month", style = ts(14, 700, TraceColors.Positive))
        }
    }
}

/* ---------- 5. budget ---------- */

@Composable
private fun BudgetCard(ui: InsightsUi, reduceMotion: Boolean, onOpen: () -> Unit) {
    val b = ui.budget
    Box(Modifier.clip(RoundedCornerShape(20.dp)).tap(label = "Budgets", onClick = onOpen)) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Budget", style = ts(15, 800), modifier = Modifier.weight(1f).semantics { heading() })
                TIcon(TraceIcons.Back, TraceColors.Muted, 14.dp, modifier = Modifier.graphicsLayer { scaleX = -1f })
            }
            if (b == null) {
                Text("Set a monthly limit for each category to see how you're doing.", style = ts(13, 500, TraceColors.Muted))
                return@Card
            }
            val used = b.spentMinor.toFloat() / b.limitMinor
            val fill = when {
                b.leftMinor < 0 -> TraceColors.Negative
                used >= 0.9f -> Amber
                else -> TraceColors.Accent
            }
            val grow = remember { Animatable(if (reduceMotion) 1f else 0f) }
            LaunchedEffect(Unit) { if (!reduceMotion) grow.animateTo(1f, tween(Motion.SLOW, easing = EmphasizedEasing)) }
            BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(TraceColors.Fill)) {
                    if (used > 0f) Box(Modifier.fillMaxWidth((used * grow.value).coerceIn(0f, 1f)).height(10.dp).clip(CircleShape).background(fill))
                }
                // Where an even pace would be today.
                b.todayFraction?.let { t ->
                    Box(Modifier.offset(x = maxWidth * t - 1.dp).width(2.dp).height(18.dp).clip(CircleShape).background(TraceColors.Ink))
                }
            }
            val money = { m: Long -> Money.whole(m, ui.currency) }
            val status = when {
                b.leftMinor < 0 -> "${money(-b.leftMinor)} over"
                !ui.isCurrentMonth -> "${money(b.leftMinor)} left over"
                b.daysAfterToday == 0 -> "${money(b.leftMinor)} left today"
                b.perDayMinor != null -> "${money(b.perDayMinor)}/day left"
                else -> "${money(b.leftMinor)} left"
            }
            Row {
                Text("${money(b.spentMinor)} of ${money(b.limitMinor)} · $status", style = ts(12, 600, if (b.leftMinor < 0) TraceColors.Negative else TraceColors.Muted), modifier = Modifier.weight(1f))
                if (b.todayFraction != null) Text("│ today", style = ts(12, 500, TraceColors.Muted))
            }
            Text(names(b.categories), style = ts(11, 500, TraceColors.Muted))
        }
    }
}

/** "Food", "Food and Bills", "Food, Bills and Travel". */
private fun names(cats: List<Category>): String = when (cats.size) {
    0 -> ""
    1 -> cats[0].name
    else -> cats.dropLast(1).joinToString { it.name } + " and " + cats.last().name
}

/* ---------- budgets sheet ---------- */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BudgetsSheet(ui: InsightsUi, onPick: (Category) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TraceColors.Sheet,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Monthly budgets", style = ts(18, 800))
                Text("Tap one to change it", style = ts(13, 500, TraceColors.Muted))
            }
            val money = { m: Long -> Money.whole(m, ui.currency) }
            ui.budgetRows.filter { it.limitMinor != null }.forEach { r ->
                val limit = r.limitMinor!!
                val used = r.spentMinor.toFloat() / limit
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).tap(label = "Change ${r.category.name} budget") { onPick(r.category) }
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row {
                        Text(r.category.name, style = ts(14, 700), modifier = Modifier.weight(1f))
                        Text(
                            "${money(r.spentMinor)} of ${money(limit)}",
                            style = ts(13, 600, when { r.spentMinor > limit -> TraceColors.Negative; used >= 0.9f -> Amber; else -> TraceColors.Muted }),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(TraceColors.Fill)) {
                        if (used > 0f) Box(
                            Modifier.fillMaxWidth(used.coerceIn(0f, 1f)).height(8.dp).clip(CircleShape)
                                .background(if (r.spentMinor > limit) TraceColors.Negative else r.category.color),
                        )
                    }
                }
            }
            val unset = ui.budgetRows.filter { it.limitMinor == null }
            if (unset.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    unset.forEach { r ->
                        val shape = RoundedCornerShape(18.dp)
                        Row(
                            Modifier.heightIn(min = 40.dp).clip(shape).border(1.dp, TraceColors.Border, shape)
                                .tap(role = Role.Button, label = "Set a ${r.category.name} budget") { onPick(r.category) }.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TIcon(TraceIcons.Plus, r.category.color, 12.dp)
                            Text(r.category.name, style = ts(14, 600))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Set, change or remove one category's monthly budget. [suggested] pre-fills a new budget
 * (from a tip); last month's spend is shown as a hint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BudgetSheet(
    category: Category,
    current: Long?,
    lastMonth: Long,
    previousMonthName: String,
    currency: String,
    onSave: (Long?) -> Unit,
    onDismiss: () -> Unit,
    suggested: Long? = null,
) {
    var amount by rememberSaveable { mutableStateOf((current ?: suggested)?.let { Money.keypadInput(it) } ?: "") }
    val minor = Money.parseMinor(amount)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TraceColors.Sheet,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CatTile(category)
                Text("${category.name} budget", style = ts(18, 800))
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Per month", style = ts(13, 500, TraceColors.Muted))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(currency + Money.keypadText(amount, currency), style = ts(44, 800, letterSpacing = -1.5f, tight = true), maxLines = 1)
                    Spacer(Modifier.width(3.dp))
                    Box(Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(category.color))
                }
                if (lastMonth > 0) {
                    Text("You spent ${Money.whole(lastMonth, currency)} on ${category.name} in $previousMonthName", style = ts(13, 500, TraceColors.Muted))
                }
            }
            Keypad(onKey = { amount = Money.press(amount, it) }, keyColor = TraceColors.Key, textColor = TraceColors.Ink, keyHeight = 50)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(27.dp))
                    .background(if (minor > 0) TraceColors.Ink else TraceColors.DisabledBg)
                    .tap(label = "Save budget") { if (minor > 0) onSave(minor) }.padding(ButtonPadding),
                contentAlignment = Alignment.Center,
            ) { Text(if (current == null) "Set budget" else "Save budget", style = ts(16, 700, if (minor > 0) TraceColors.OnInk else TraceColors.DisabledText)) }
            if (current != null) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).tap(label = "Remove budget") { onSave(null) }.padding(ButtonPadding),
                    contentAlignment = Alignment.Center,
                ) { Text("Remove budget", style = ts(15, 700, TraceColors.Negative)) }
            }
        }
    }
}

@Composable
private fun CatTile(c: Category) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(c.color.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) { TIcon(TraceIcons.category(c), c.color, 18.dp) }
}
