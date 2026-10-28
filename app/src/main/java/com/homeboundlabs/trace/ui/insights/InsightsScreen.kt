package com.homeboundlabs.trace.ui.insights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.ui.components.CircleButton
import com.homeboundlabs.trace.ui.components.Keypad
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.SpringEasing
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion
import com.homeboundlabs.trace.ui.theme.ts
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val Amber = Color(0xFFB45309)
private val AmberBg = Color(0xFFFFF4E0)
private val NeutralBg = Color(0xFFEEF1FF)
private val NeutralFg = Color(0xFF3949AB)

@Composable
fun InsightsScreen(onBack: () -> Unit, vm: InsightsViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(TraceColors.Surface).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(TraceIcons.Back, "Back", onBack)
            Text("Insights", style = ts(16, 700), modifier = Modifier.weight(1f).padding(end = 44.dp), textAlign = TextAlign.Center)
        }
        MonthSwitcher(ui, vm::previousMonth, vm::nextMonth, Modifier.padding(horizontal = 22.dp))

        // Keyed on the month so switching months replays the animations.
        key(ui.month) {
            Highlights(ui.highlights, reduceMotion)
            Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                WhatChanged(ui)
                Budgets(ui, reduceMotion, onEdit = { editingId = it.id })
            }
        }
    }

    editingId?.let { id ->
        val c = (ui.budgets.map { it.category } + ui.unbudgeted).firstOrNull { it.id == id }
        if (c == null) {
            editingId = null
        } else {
            BudgetSheet(
                category = c,
                current = ui.budgets.firstOrNull { it.category.id == id }?.limitMinor,
                lastMonth = ui.lastMonthByCategory[id] ?: 0L,
                previousMonthName = ui.previousMonthName,
                currency = ui.currency,
                onSave = { vm.setBudget(id, it); editingId = null },
                onDismiss = { editingId = null },
            )
        }
    }
}

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

@Composable
private fun MonthSwitcher(ui: InsightsUi, onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Arrow("Previous month", ui.canGoBack, flip = false, onPrev)
        Text(ui.month.format(monthFmt), style = ts(18, 800), textAlign = TextAlign.Center, modifier = Modifier.weight(1f).semantics { heading() })
        Arrow("Next month", !ui.isCurrentMonth, flip = true, onNext)
    }
}

@Composable
private fun Arrow(label: String, enabled: Boolean, flip: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(TraceColors.Fill)
            .then(if (enabled) Modifier.tap(label = label, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        TIcon(
            TraceIcons.Back, if (enabled) TraceColors.Ink else TraceColors.Ghost, 16.dp,
            modifier = Modifier.graphicsLayer { scaleX = if (flip) -1f else 1f }, contentDescription = label,
        )
    }
}

@Composable
private fun SectionTitle(text: String, sub: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, style = ts(17, 800), modifier = Modifier.semantics { heading() })
        if (sub != null) Text(sub, style = ts(13, 500, TraceColors.Muted))
    }
}

/* ---------- 1. Highlights ---------- */

@Composable
private fun Highlights(items: List<Highlight>, reduceMotion: Boolean) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(items) { i, h ->
            // Cards pop in one after another.
            val pop = remember { Animatable(if (reduceMotion) 1f else 0f) }
            LaunchedEffect(Unit) {
                if (!reduceMotion) {
                    delay(70L * i)
                    pop.animateTo(1f, tween(500, easing = SpringEasing))
                }
            }
            val (bg, fg) = when (h.tone) {
                Highlight.Tone.Good -> TraceColors.PositiveBg to TraceColors.Positive
                Highlight.Tone.Warn -> AmberBg to Amber
                Highlight.Tone.Bad -> TraceColors.NegativeBg to TraceColors.Negative
                Highlight.Tone.Neutral -> NeutralBg to NeutralFg
            }
            Column(
                Modifier.width(if (items.size == 1) 316.dp else 250.dp).height(132.dp)
                    .graphicsLayer {
                        val p = pop.value
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.9f + 0.1f * p
                        scaleY = 0.9f + 0.1f * p
                    }
                    .clip(RoundedCornerShape(24.dp)).background(bg).padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(h.emoji, style = ts(24, 400))
                Text(h.text, style = ts(16, 700, fg), maxLines = 3)
            }
        }
    }
}

/* ---------- 2. What changed ---------- */

@Composable
private fun WhatChanged(ui: InsightsUi) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(
            "What changed",
            if (ui.isCurrentMonth) "So far this month vs the same days in ${ui.previousMonthName}"
            else "${ui.month.month.name.lowercase().replaceFirstChar { it.uppercase() }} vs ${ui.previousMonthName}",
        )
        when {
            // With nothing logged last month every row would just say "new", so explain instead.
            ui.nothingBefore -> Text(
                if (ui.hasData) "This is your first month on Trace. Next month you'll see what changed here."
                else "Nothing to compare yet. Log a few expenses first.",
                style = ts(14, 500, TraceColors.Muted),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TraceColors.Field).padding(16.dp),
            )
            ui.changes.isEmpty() -> Text("Spending is the same as last month.", style = ts(14, 500, TraceColors.Muted))
            else -> ui.changes.forEach { ch -> ChangeRow(ch, ui.currency) }
        }
    }
}

@Composable
private fun ChangeRow(ch: Change, currency: String) {
    val up = ch.diffMinor > 0
    val fg = if (up) TraceColors.Negative else TraceColors.Positive
    val bg = if (up) TraceColors.NegativeBg else TraceColors.PositiveBg
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CatTile(ch.category)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ch.category.name, style = ts(15, 700))
            Text("${Money.whole(ch.nowMinor, currency)} vs ${Money.whole(ch.beforeMinor, currency)}", style = ts(12, 500, TraceColors.Muted))
        }
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TIcon(if (up) TraceIcons.ArrowUp else TraceIcons.ArrowDown, fg, 11.dp)
            val pct = ch.pct?.let { " · ${abs(it)}%" } ?: " · new"
            Text(Money.whole(abs(ch.diffMinor), currency) + pct, style = ts(12, 800, fg))
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

/* ---------- 3. Budgets ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Budgets(ui: InsightsUi, reduceMotion: Boolean, onEdit: (Category) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Budgets", if (ui.budgets.isEmpty()) "Set a monthly limit and see how much is left each day" else "Tap one to change it")
        ui.budgets.forEach { b -> BudgetRow(b, ui, reduceMotion) { onEdit(b.category) } }
        if (ui.unbudgeted.isNotEmpty()) {
            if (ui.budgets.isNotEmpty()) Spacer(Modifier.height(2.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.unbudgeted.forEach { c ->
                    Row(
                        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, TraceColors.Border, RoundedCornerShape(20.dp))
                            .tap(role = Role.Button, label = "Set a ${c.name} budget") { onEdit(c) }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TIcon(TraceIcons.Plus, c.color, 12.dp)
                        Text(c.name, style = ts(14, 600))
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(b: BudgetLine, ui: InsightsUi, reduceMotion: Boolean, onClick: () -> Unit) {
    val grow = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduceMotion) grow.animateTo(1f, tween(800, easing = SpringEasing)) }
    val barColor = when (b.status) {
        BudgetLine.Status.Ok -> b.category.color
        BudgetLine.Status.Close -> Color(0xFFF59E0B)
        BudgetLine.Status.Over -> TraceColors.Negative
    }
    val cur = ui.currency
    val sub = when {
        b.status == BudgetLine.Status.Over -> "Over by ${Money.whole(-b.leftMinor, cur)}"
        !ui.isCurrentMonth -> "${Money.whole(b.leftMinor, cur)} was left"
        b.daysLeft > 0 -> {
            val perDay = Money.whole(b.leftMinor / b.daysLeft, cur)
            "${Money.whole(b.leftMinor, cur)} left for ${b.daysLeft} ${if (b.daysLeft == 1) "day" else "days"}, about $perDay a day"
        }
        else -> "${Money.whole(b.leftMinor, cur)} left"
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, TraceColors.Border, RoundedCornerShape(20.dp))
            .tap(label = "Change ${b.category.name} budget", onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CatTile(b.category)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(b.category.name, style = ts(15, 700))
                Text(sub, style = ts(12, 600, if (b.status == BudgetLine.Status.Over) TraceColors.Negative else TraceColors.Muted))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.whole(b.spentMinor, cur), style = ts(15, 800))
                Text("of ${Money.whole(b.limitMinor, cur)}", style = ts(12, 500, TraceColors.Muted))
            }
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(TraceColors.Fill)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((b.fraction * grow.value).coerceIn(0f, 1f)).clip(CircleShape).background(barColor))
        }
    }
}

/** Set, change or remove one category's monthly budget, with last month's spend as a hint. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSheet(
    category: Category,
    current: Long?,
    lastMonth: Long,
    previousMonthName: String,
    currency: String,
    onSave: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(current?.let { Money.keypadInput(it) } ?: "") }
    val minor = Money.parseMinor(amount)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TraceColors.Surface,
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
            Keypad(onKey = { amount = Money.press(amount, it) }, keyColor = TraceColors.Field, textColor = TraceColors.Ink, keyHeight = 50)
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp))
                    .background(if (minor > 0) TraceColors.Ink else TraceColors.Ink.copy(alpha = 0.25f))
                    .tap(label = "Save budget") { if (minor > 0) onSave(minor) },
                contentAlignment = Alignment.Center,
            ) { Text(if (current == null) "Set budget" else "Save budget", style = ts(16, 700, Color.White)) }
            if (current != null) {
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).tap(label = "Remove budget") { onSave(null) },
                    contentAlignment = Alignment.Center,
                ) { Text("Remove budget", style = ts(15, 700, TraceColors.Negative)) }
            }
        }
    }
}
