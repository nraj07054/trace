package com.homeboundlabs.trace.ui.habits

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeboundlabs.trace.data.DayKind
import com.homeboundlabs.trace.data.Milestones
import com.homeboundlabs.trace.data.HabitRepository
import com.homeboundlabs.trace.data.StreakInfo
import com.homeboundlabs.trace.data.Streaks
import com.homeboundlabs.trace.traceApp
import com.homeboundlabs.trace.ui.components.ScreenHeader
import com.homeboundlabs.trace.ui.components.TCard
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.CapTextScale
import com.homeboundlabs.trace.ui.theme.Motion
import com.homeboundlabs.trace.ui.theme.SpringEasing
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion
import com.homeboundlabs.trace.ui.theme.ts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class HabitsUi(
    val today: LocalDate = LocalDate.now(),
    val streak: StreakInfo = StreakInfo(0, 0, true, emptySet()),
    val month: YearMonth = YearMonth.now(),
    val days: List<Pair<LocalDate, DayKind>> = emptyList(),
    val trackedFrom: LocalDate? = null,
    /** Biggest milestone reached and not yet celebrated on Home (null when there's nothing new). */
    val newMilestone: Int? = null,
    val loaded: Boolean = false,
) {
    val canGoBack: Boolean get() = trackedFrom != null && month > YearMonth.from(trackedFrom)
    val canGoForward: Boolean get() = month < YearMonth.from(today)
}

class HabitsViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app.traceApp
    private val month = MutableStateFlow(YearMonth.now())

    val ui: StateFlow<HabitsUi> = combine(a.repo.observeAll(), a.habits.observeNoSpendDays(), month, a.prefs.celebratedMilestone) { expenses, marked, m, celebrated ->
        val today = LocalDate.now()
        val spent = expenses.mapTo(HashSet()) { HabitRepository.dayOf(it.createdAt) }
        val checkedIn = marked.mapTo(HashSet()) { LocalDate.ofEpochDay(it) }
        val logged = spent + checkedIn
        val streak = Streaks.compute(logged, today)
        val trackedFrom = logged.minOrNull()
        HabitsUi(
            today = today,
            streak = streak,
            month = m,
            days = Streaks.month(m, spent, checkedIn, streak.forgiven, today, trackedFrom),
            trackedFrom = trackedFrom,
            newMilestone = Milestones.reached(streak.current)?.takeIf { it > celebrated },
            loaded = true,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUi())

    fun celebrated(m: Int) = a.prefs.setCelebratedMilestone(m)
    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }
}

@Composable
fun HabitsScreen(onBack: () -> Unit, vm: HabitsViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().background(TraceColors.Surface).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader("Habits", onBack)
        StreakHero(ui)
        NextMilestone(ui)
        TCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonthArrow(TraceIcons.Back, "Previous month", ui.canGoBack, vm::previousMonth)
                Text(
                    ui.month.month.getDisplayName(TextStyle.FULL, Locale.US) + " " + ui.month.year,
                    style = ts(15, 700), modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
                MonthArrow(TraceIcons.Back, "Next month", ui.canGoForward, vm::nextMonth, flip = true)
            }
            Calendar(ui)
            Legend()
        }
        Text(
            "A day counts when you log an expense, or tap \"Nothing today\" on the evening reminder. " +
                "One missed day is forgiven once a week, so a forgotten evening doesn't undo weeks of habit.",
            style = ts(12, 400, TraceColors.Faint),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StreakHero(ui: HabitsUi) {
    val reduceMotion = rememberReduceMotion()
    val pop by animateFloatAsState(if (ui.loaded) 1f else 0.6f, if (reduceMotion) tween(0) else tween(Motion.STANDARD, easing = SpringEasing), label = "pop")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(96.dp).graphicsLayer { scaleX = pop; scaleY = pop }.clip(CircleShape)
                .background(if (ui.streak.current > 0) TraceColors.AccentBg else TraceColors.Fill),
            contentAlignment = Alignment.Center,
        ) { CapTextScale { Text(if (ui.streak.current > 0) "🔥" else "🌱", style = ts(44, 400)) } }
        Text(
            if (ui.streak.current == 1) "1-day streak" else "${ui.streak.current}-day streak",
            style = ts(30, 800, letterSpacing = -0.8f),
        )
        Text(
            when {
                // No guilt when a streak ends: just an easy restart.
                ui.streak.current == 0 && ui.streak.best > 0 -> "Start a new one today"
                ui.streak.current == 0 -> "Log something today to start one"
                ui.streak.todayOpen -> "Log something today to keep it going"
                else -> "Today's logged"
            } + if (ui.streak.best > 0) "  ·  Best ${ui.streak.best}" else "",
            style = ts(13, 600, TraceColors.Muted), textAlign = TextAlign.Center,
        )
    }
}

/** Progress toward the next milestone (7, 30, 100, 365 days). */
@Composable
private fun NextMilestone(ui: HabitsUi) {
    val next = Milestones.next(ui.streak.current) ?: return
    val from = Milestones.reached(ui.streak.current) ?: 0
    val frac = ((ui.streak.current - from).toFloat() / (next - from)).coerceIn(0f, 1f)
    TCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Next: $next days", style = ts(15, 700), modifier = Modifier.weight(1f))
            Text("${next - ui.streak.current} to go", style = ts(13, 600, TraceColors.Muted))
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(TraceColors.Fill)) {
            Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(4.dp)).background(TraceColors.Accent))
        }
    }
}

@Composable
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, flip: Boolean = false) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(TraceColors.RoundButton).tap(label = label) { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) { TIcon(icon, if (enabled) TraceColors.Ink else TraceColors.Ghost, 16.dp, Modifier.graphicsLayer { if (flip) scaleX = -1f }) }
}

/** Monday-first month grid; colour shows what each day was. */
@Composable
private fun Calendar(ui: HabitsUi) {
    val lead = ui.month.atDay(1).dayOfWeek.value - 1
    val cells: List<Pair<LocalDate, DayKind>?> = List(lead) { null } + ui.days
    CapTextScale {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, style = ts(11, 700, TraceColors.Faint), modifier = Modifier.weight(1f), textAlign = TextAlign.Center) } }
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { cell -> DayCell(cell, ui) }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(cell: Pair<LocalDate, DayKind>?, ui: HabitsUi) {
    if (cell == null) { Spacer(Modifier.weight(1f)); return }
    val (date, kind) = cell
    val shape = RoundedCornerShape(10.dp)
    val (bg, fg) = when (kind) {
        DayKind.Logged -> TraceColors.Accent to Color.White
        // A "nothing to log" check-in is a kept day, so it looks like any logged day.
        DayKind.CheckedIn -> TraceColors.Accent to Color.White
        DayKind.Forgiven -> TraceColors.AccentBg to TraceColors.Accent
        DayKind.Missed -> TraceColors.Fill to TraceColors.Faint
        DayKind.Blank -> Color.Transparent to TraceColors.Ghost
    }
    val isToday = date == ui.today
    var m = Modifier.weight(1f).aspectRatio(1f).clip(shape).background(bg)
    if (isToday) m = m.border(2.dp, TraceColors.Ink, shape)
    Box(m, contentAlignment = Alignment.Center) { Text("${date.dayOfMonth}", style = ts(12, 700, fg)) }
}

@Composable
private fun Legend() {
    CapTextScale {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(TraceColors.Accent to "Logged", TraceColors.AccentBg to "Forgiven", TraceColors.Fill to "Missed").forEach { (c, l) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    Text(l, style = ts(11, 600, TraceColors.Muted))
                }
            }
        }
    }
}
