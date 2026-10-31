package com.homeboundlabs.trace.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Compact date picker in the app's style: quick picks plus a small month grid. Future days are disabled. */
@Composable
fun DatePickerCard(selected: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.from(selected).toString()) }
    val month = YearMonth.parse(monthKey)
    val canGoNext = month.isBefore(YearMonth.from(today))

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 340.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(TraceColors.Surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("When was it?", style = ts(16, 700))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickPick("Today", selected == today) { onPick(today) }
                QuickPick("Yesterday", selected == today.minusDays(1)) { onPick(today.minusDays(1)) }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    style = ts(14, 700), modifier = Modifier.weight(1f),
                )
                NavArrow(TraceIcons.Back, "Previous month", enabled = true) { monthKey = month.minusMonths(1).toString() }
                Spacer(Modifier.size(4.dp))
                NavArrow(TraceIcons.Back, "Next month", enabled = canGoNext, flip = true) {
                    if (canGoNext) monthKey = month.plusMonths(1).toString()
                }
            }

            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    val dir = if (targetState.isAfter(initialState)) 1 else -1
                    (slideInHorizontally(tween(220)) { it / 5 * dir } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(180)) { -it / 5 * dir } + fadeOut(tween(150)))
                },
                label = "month",
            ) { m -> MonthGrid(m, selected, today, onPick) }
        }
    }
}

@Composable
private fun QuickPick(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.height(36.dp).clip(shape).background(if (on) TraceColors.Ink else TraceColors.Surface)
            .border(1.dp, if (on) TraceColors.Ink else TraceColors.Border, shape)
            .tap(role = Role.Button, label = label, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = ts(13, 600, if (on) TraceColors.OnInk else TraceColors.Ink)) }
}

@Composable
private fun NavArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    flip: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(TraceColors.Fill)
            .then(if (enabled) Modifier.tap(label = label, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        TIcon(
            icon, if (enabled) TraceColors.Ink else TraceColors.Ghost, 14.dp,
            modifier = Modifier.graphicsLayer { scaleX = if (flip) -1f else 1f },
            contentDescription = label,
        )
    }
}

@Composable
private fun MonthGrid(month: YearMonth, selected: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    // Weeks start on Monday; leading blanks fill up to the first day.
    val lead = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value)
    val cells: List<LocalDate?> = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = ts(11, 600, TraceColors.Faint),
                    modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date -> DayCell(date, selected, today, onPick) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DayCell(
    date: LocalDate?,
    selected: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
        if (date == null) return@Box
        val future = date.isAfter(today)
        val isSel = date == selected
        val isToday = date == today
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(if (isSel) TraceColors.Ink else Color.Transparent)
                .then(if (isToday && !isSel) Modifier.border(1.dp, TraceColors.Ink, CircleShape) else Modifier)
                .then(if (!future) Modifier.tap(label = date.toString()) { onPick(date) } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = ts(
                    13, if (isSel || isToday) 700 else 500,
                    when {
                        isSel -> TraceColors.OnInk
                        future -> TraceColors.Border
                        else -> TraceColors.Ink
                    },
                ),
            )
        }
    }
}
