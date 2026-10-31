package com.homeboundlabs.trace.ui.home

import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.homeboundlabs.trace.traceApp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion
import com.homeboundlabs.trace.ui.theme.ts

@Composable
fun HomeScreen(onAdd: () -> Unit, onSettings: () -> Unit, onSeeAll: () -> Unit, onEdit: (Long) -> Unit, onInsights: () -> Unit, vm: HomeViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        vm.refreshClock()
        onPauseOrDispose { }
    }

    val scroll = rememberScrollState()

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
                SpendChart(
                    buckets = ui.buckets, categories = ui.categories, selected = ui.selected, filter = ui.filter, currency = ui.currency,
                    reduceMotion = reduceMotion, onSelect = vm::selectBar,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
            } else {
                Spacer(Modifier.height(244.dp))
            }
            PeriodPills(ui.period, vm::selectPeriod)
            CategoryChips(ui, vm::selectFilter)
            Recent(ui, onSeeAll, onEdit, Modifier.padding(horizontal = 22.dp))
        }
        StatusBarFade(visible = scroll.value > 0, reduceMotion = reduceMotion)
        BottomBar(
            onAdd = onAdd,
            onInsights = onInsights,
            onSettings = onSettings,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun Header(name: String, onAvatar: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                append("Trace")
                withStyle(SpanStyle(color = TraceColors.Accent)) { append(".") }
            },
            style = ts(22, 800, letterSpacing = -0.6f),
            modifier = Modifier.semantics { heading() },
        )
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
                Text(name.trim().firstOrNull()?.uppercase() ?: "N", style = ts(13, 700, TraceColors.AvatarText))
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
        )
    }
}

@Composable
private fun PeriodPills(current: Period, onPick: (Period) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        Period.entries.forEach { p ->
            val on = p == current
            Box(
                Modifier.widthIn(min = 52.dp).height(36.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (on) TraceColors.Fill else Color.Transparent)
                    .tap(role = Role.Tab, label = p.label) { onPick(p) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(p.id, style = ts(13, if (on) 800 else 600, if (on) TraceColors.Ink else TraceColors.Faint))
            }
        }
    }
}

@Composable
private fun CategoryChips(ui: HomeUi, onPick: (Category?) -> Unit) {
    val items: List<Category?> = listOf(null) + ui.categories
    LazyRow(
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items) { i, c ->
            val on = c == ui.filter
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.widthIn(min = 96.dp).clip(shape)
                    .background(if (on) TraceColors.Ink else TraceColors.Surface)
                    .border(1.dp, if (on) TraceColors.Ink else TraceColors.Border, shape)
                    .tap(role = Role.Tab) { onPick(c) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val dot = if (c == null) (if (on) TraceColors.OnInk else TraceColors.Ink) else c.color
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                    Text(c?.label ?: "All", style = ts(12, 600, if (on) TraceColors.ChipSubOn else TraceColors.Muted))
                }
                Text(
                    Money.whole(ui.chipTotals.getOrElse(i) { 0L }, ui.currency),
                    style = ts(15, 800, if (on) TraceColors.OnInk else TraceColors.Ink, letterSpacing = -0.3f),
                )
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

@Composable
private fun BottomBar(onAdd: () -> Unit, onInsights: () -> Unit, onSettings: () -> Unit, modifier: Modifier) {
    // An (empty) pointer handler makes the whole bar a touch target, so taps between the
    // buttons stop here instead of falling through to the expense rows scrolled behind it.
    Column(modifier.fillMaxWidth().pointerInput(Unit) {}) {
        // Short fade so list rows dissolve above the bar instead of showing through it.
        Spacer(
            Modifier.fillMaxWidth().height(28.dp)
                .background(Brush.verticalGradient(listOf(TraceColors.Surface.copy(alpha = 0f), TraceColors.Surface))),
        )
        Row(
            Modifier.fillMaxWidth().background(TraceColors.Surface).navigationBarsPadding().height(72.dp).padding(horizontal = 12.dp),
            // Each item gets an equal share of the width, so there's no empty stretch before the + button.
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavIcon(TraceIcons.Home, "Home", TraceColors.Ink, 26) {}
            NavIcon(TraceIcons.Insights, "Insights", TraceColors.NavInactive, 24, onInsights)
            NavIcon(TraceIcons.Settings, "Settings", TraceColors.NavInactive, 24, onSettings)
            Box(
                Modifier.size(58.dp)
                    .shadow(20.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.28f), spotColor = Color.Black.copy(alpha = 0.28f))
                    .clip(CircleShape).background(TraceColors.Ink).tap(label = "Add expense", onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { TIcon(TraceIcons.Plus, TraceColors.OnInk, 24.dp, contentDescription = "Add expense") }
        }
    }
}

@Composable
private fun NavIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, size: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).tap(label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { TIcon(icon, tint, size.dp, contentDescription = label) }
}

/** Solid page color behind the clock/battery bar that spreads into a soft fade, shown once the page is scrolled. */
@Composable
private fun StatusBarFade(visible: Boolean, reduceMotion: Boolean) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, if (reduceMotion) snap() else tween(200), label = "fade")
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
