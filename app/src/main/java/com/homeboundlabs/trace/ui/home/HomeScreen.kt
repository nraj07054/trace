package com.homeboundlabs.trace.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import com.homeboundlabs.trace.ui.theme.CapTextScale
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeboundlabs.trace.ui.theme.Motion
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.ui.components.ExpenseRow
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.ThemeToggle
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.settings.KOFI_URL
import com.homeboundlabs.trace.ui.settings.openLink
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.components.whenLabel
import com.homeboundlabs.trace.ui.theme.SpringEasing
import com.homeboundlabs.trace.ui.components.Wordmark
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion
import com.homeboundlabs.trace.ui.theme.ts
import com.homeboundlabs.trace.ui.insights.BudgetSheet
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun HomeScreen(onSettings: () -> Unit, onSeeAll: () -> Unit, onEdit: (Long) -> Unit, vm: HomeViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        vm.refreshClock()
        onPauseOrDispose { }
    }

    val scroll = rememberScrollState()
    var budgetFor by rememberSaveable { mutableStateOf<String?>(null) }
    val setBudget: (Category) -> Unit = { budgetFor = it.id }

    Box(Modifier.fillMaxSize().background(TraceColors.Surface)) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(scroll).statusBarsPadding()
                .navigationBarsPadding().padding(top = 16.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Header(ui.name, onSettings, Modifier.padding(horizontal = 22.dp))
            Total(ui, Modifier.padding(horizontal = 22.dp))
            if (ui.loaded) {
                CapTextScale {
                    SpendChart(
                        buckets = ui.buckets, categories = ui.categories, selected = ui.selected, filter = ui.filter, currency = ui.currency,
                        reduceMotion = reduceMotion, onSelect = vm::selectBar,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                }
            } else {
                Spacer(Modifier.height(244.dp))
            }
            PeriodPills(ui.period, vm::selectPeriod)
            if (TraceColors.isDark) BudgetRings(ui, vm::selectFilter, setBudget) else CategoryChips(ui, vm::selectFilter, setBudget)
            Recent(ui, onSeeAll, onEdit, Modifier.padding(horizontal = 22.dp))
        }
        StatusBarFade(visible = scroll.value > 0, reduceMotion = reduceMotion)
    }

    budgetFor?.let { id ->
        val c = ui.categories.firstOrNull { it.id == id }
        if (c == null) {
            budgetFor = null
        } else {
            BudgetSheet(
                category = c,
                current = ui.budgets[id],
                lastMonth = ui.lastMonthByCategory[id] ?: 0L,
                previousMonthName = ui.previousMonthName,
                currency = ui.currency,
                onSave = { vm.setBudget(id, it); budgetFor = null },
                onDismiss = { budgetFor = null },
            )
        }
    }
}

@Composable
private fun Header(name: String, onAvatar: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Wordmark(22, Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ThemeToggle()
            // Small, optional tip link. Opens Ko-fi in the browser (the app itself has no internet access).
            val context = LocalContext.current
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(TraceColors.Fill)
                    .tap(label = "Support Trace on Ko-fi") { openLink(context, KOFI_URL) },
                contentAlignment = Alignment.Center,
            ) { TIcon(TraceIcons.Heart, Color(0xFFE5484D), 15.dp, contentDescription = "Support Trace on Ko-fi") }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(TraceColors.Fill).tap(label = "Settings", onClick = onAvatar),
                contentAlignment = Alignment.Center,
            ) {
                CapTextScale { Text(name.trim().firstOrNull()?.uppercase() ?: "N", style = ts(13, 700, TraceColors.AvatarText)) }
            }
        }
    }
}

@Composable
private fun Total(ui: HomeUi, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Spent ${ui.period.label}", style = ts(14, 400, TraceColors.Muted))
            ui.deltaPct?.let { d ->
                val down = d <= 0
                val fg = if (down) TraceColors.Positive else TraceColors.Negative
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(if (down) TraceColors.PositiveBg else TraceColors.NegativeBg)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    TIcon(if (down) TraceIcons.ArrowDown else TraceIcons.ArrowUp, fg, 11.dp)
                    Text("${kotlin.math.abs(d)}%", style = ts(12, 700, fg))
                }
            }
        }
        val (whole, cents) = Money.split(ui.periodTotal, ui.currency)
        Text(
            buildAnnotatedString {
                append(whole)
                withStyle(SpanStyle(color = TraceColors.Ghost)) { append(cents) }
            },
            style = ts(50, 800, letterSpacing = -2f, tight = true),
            maxLines = 1,
            // Large amounts or a big font size shrink to fit instead of running off the screen.
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 50.sp),
        )
    }
}

@Composable
private fun PeriodPills(current: Period, onPick: (Period) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        Period.entries.forEach { p ->
            val on = p == current
            Box(
                Modifier.widthIn(min = 52.dp).heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (on) TraceColors.Fill else Color.Transparent)
                    .tap(role = Role.Tab, label = p.label) { onPick(p) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(p.id, style = ts(13, if (on) 800 else 600, if (on) TraceColors.Ink else TraceColors.Faint))
            }
        }
    }
}

@Composable
private fun CategoryChips(ui: HomeUi, onPick: (Category?) -> Unit, onSetBudget: (Category) -> Unit) {
    val items: List<Category?> = listOf(null) + ui.categories
    val month = ui.period == Period.Month
    val reduceMotion = rememberReduceMotion()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
        ui.categoriesTitle(), style = ts(16, 700),
        modifier = Modifier.padding(start = 22.dp).semantics { heading() },
    )
    CapTextScale {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items) { i, c ->
                val on = c == ui.filter
                val shape = RoundedCornerShape(18.dp)
                val spent = ui.chipTotals.getOrElse(i) { 0L }
                // On 1M a chip with a budget fills up like a tank; one without shows a "+". The label row
                // has a fixed height, so every chip is the same size either way.
                val budget = c?.let { ui.monthBudget(it) }
                val pct = budget?.let { (spent * 100.0 / it).roundToInt() }
                // Selected: a 2dp border in the category's own color and a small lift, the same as the
                // dark cards, so the white chip and its fill stay visible.
                val accent = c?.color ?: TraceColors.Ink
                val lift by animateFloatAsState(if (on) 1.05f else 1f, if (reduceMotion) snap() else tween(Motion.STANDARD, easing = SpringEasing), label = "lift")
                Box(
                    Modifier.graphicsLayer { scaleX = lift; scaleY = lift }
                        .widthIn(min = 96.dp).clip(shape)
                        .background(TraceColors.Surface)
                        .border(if (on) 2.dp else 1.dp, if (on) accent else TraceColors.Border, shape)
                        .tap(role = Role.Tab, label = listOfNotNull(c?.label ?: "All", pct?.let { "$it% of budget used" }).joinToString(", ")) { onPick(c) },
                ) {
                    // Selecting replays the wave.
                    if (pct != null) {
                        WaveFill(pct, budgetColor(pct, c.color).copy(alpha = 0.16f), on, reduceMotion, Modifier.matchParentSize())
                    }
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                            Text(c?.label ?: "All", style = ts(12, if (on) 700 else 600, if (on) TraceColors.Ink else TraceColors.Muted))
                            if (month && c != null && budget == null) {
                                Spacer(Modifier.weight(1f))
                                Box(
                                    Modifier.size(20.dp).clip(CircleShape).border(1.dp, TraceColors.Border, CircleShape)
                                        .tap(label = "Set a ${c.label} budget", role = Role.Button) { onSetBudget(c) },
                                    contentAlignment = Alignment.Center,
                                ) { TIcon(TraceIcons.Plus, TraceColors.Muted, 11.dp) }
                            }
                        }
                        Text(
                            buildAnnotatedString {
                                append(Money.whole(spent, ui.currency))
                                if (budget != null) withStyle(SpanStyle(color = TraceColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)) {
                                    append(" / " + shortAmount(budget))
                                }
                            },
                            style = ts(15, 800, TraceColors.Ink, letterSpacing = -0.3f),
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun Recent(ui: HomeUi, onSeeAll: () -> Unit, onEdit: (Long) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recent", style = ts(16, 700), modifier = Modifier.semantics { heading() })
            Text(
                "See all", style = ts(13, 600, TraceColors.Muted),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).tap(onClick = onSeeAll).padding(vertical = 12.dp, horizontal = 4.dp),
            )
        }
        if (ui.loaded && ui.recent.isEmpty()) {
            Text(
                "No expenses yet. Tap + to add one.",
                style = ts(14, 500, TraceColors.Muted),
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        ui.recent.forEach { e ->
            ExpenseRow(
                e, ui.currency, whenLabel(e.createdAt) + " · " + e.category.label,
                Modifier.clip(RoundedCornerShape(14.dp)).tap(label = "Edit ${e.title}") { onEdit(e.id) },
            )
        }
    }
}

/** Solid page color behind the clock/battery bar that spreads into a soft fade, shown once the page is scrolled. */
@Composable
private fun StatusBarFade(visible: Boolean, reduceMotion: Boolean) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, if (reduceMotion) snap() else tween(Motion.QUICK), label = "fade")
    Column(Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }) {
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(TraceColors.Surface))
        Spacer(
            Modifier.fillMaxWidth().height(36.dp).background(
                Brush.verticalGradient(
                    0f to TraceColors.Surface,
                    0.45f to TraceColors.Surface.copy(alpha = 0.75f),
                    1f to TraceColors.Surface.copy(alpha = 0f),
                ),
            ),
        )
    }
}
