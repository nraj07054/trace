package com.homeboundlabs.trace.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.goals.GoalsViewModel
import com.homeboundlabs.trace.ui.goals.Gullak
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import com.homeboundlabs.trace.ui.habits.HabitsViewModel
import com.homeboundlabs.trace.ui.people.PeopleViewModel
import com.homeboundlabs.trace.ui.theme.CapTextScale
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts

/**
 * Home's habit strip: just the logging streak, kept quiet, plus a one-time banner at each milestone.
 * Nothing here rewards spending less; only keeping the record.
 */
@Composable
fun HabitStrip(onOpen: () -> Unit, modifier: Modifier = Modifier, vm: HabitsViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    if (!ui.loaded) return
    val shape = RoundedCornerShape(20.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    ui.newMilestone?.let { m -> MilestoneBanner(m) { vm.celebrated(m) } }
    Row(
        Modifier.fillMaxWidth().clip(shape).border(1.dp, TraceColors.Border, shape)
            .tap(label = "Open habits", onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (ui.streak.current > 0) TraceColors.AccentBg else TraceColors.Fill),
            contentAlignment = Alignment.Center,
        ) { CapTextScale { Text(if (ui.streak.current > 0) "🔥" else "🌱", style = ts(18, 400)) } }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (ui.streak.current == 0) "Start a logging streak" else "${ui.streak.current}-day streak",
                style = ts(15, 700),
            )
            // Wraps rather than cutting off on narrow phones or with big text.
            Text(
                when {
                    ui.streak.current == 0 && ui.streak.best > 0 -> "Start a new one today"
                    ui.streak.todayOpen -> "Log today to keep it going"
                    ui.streak.best > ui.streak.current -> "Best ${ui.streak.best} days"
                    else -> "Your best yet"
                },
                style = ts(12, 500, TraceColors.Muted),
            )
        }
        // A small, neutral tick once today has anything logged; nothing to tap here.
        if (!ui.streak.todayOpen) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(TraceColors.Fill), contentAlignment = Alignment.Center) {
                TIcon(TraceIcons.Check, TraceColors.Muted, 13.dp, contentDescription = "Logged today")
            }
        }
    }
    }
}

/** Shown once when the logging streak reaches 7, 30, 100 or 365 days. */
@Composable
private fun MilestoneBanner(days: Int, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TraceColors.AccentBg).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CapTextScale { Text("🎉", style = ts(24, 400)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$days-day streak!", style = ts(15, 800))
            Text(com.homeboundlabs.trace.data.Milestones.message(days), style = ts(13, 500, TraceColors.Ink))
        }
        Box(Modifier.size(40.dp).clip(CircleShape).tap(label = "Dismiss", onClick = onDismiss), contentAlignment = Alignment.Center) {
            TIcon(TraceIcons.Close, TraceColors.Muted, 14.dp, contentDescription = "Dismiss")
        }
    }
}

/** Two tiles side by side: what's owed either way, and the top savings goal. */
@Composable
fun MoneyTiles(
    onPeople: () -> Unit,
    onGoals: () -> Unit,
    modifier: Modifier = Modifier,
    people: PeopleViewModel = viewModel(),
    goals: GoalsViewModel = viewModel(),
) {
    val p by people.ui.collectAsStateWithLifecycle()
    val g by goals.ui.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(20.dp)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().clip(shape).border(1.dp, TraceColors.Border, shape)
                .tap(label = "Lend and borrow", onClick = onPeople).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Text("Lend & borrow", style = ts(13, 600, TraceColors.Muted))
            if (p.owedToYou == 0L && p.youOwe == 0L) {
                Text("Track who owes what", style = ts(14, 700))
            } else {
                if (p.owedToYou > 0) Line(Money.whole(p.owedToYou, p.currency), "to get", TraceColors.Positive)
                if (p.youOwe > 0) Line(Money.whole(p.youOwe, p.currency), "to give", TraceColors.Negative)
            }
        }
        val top = g.goals.firstOrNull { !it.reached && !it.done } ?: g.goals.firstOrNull { !it.done }
        Row(
            Modifier.weight(1f).fillMaxHeight().clip(shape).border(1.dp, TraceColors.Border, shape)
                .tap(label = "Savings goals", onClick = onGoals).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Goals", style = ts(13, 600, TraceColors.Muted))
                if (top == null) {
                    Text("Save up for something", style = ts(14, 700))
                } else {
                    Text(top.name, style = ts(14, 700), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(top.fraction * 100).toInt()}% there", style = ts(12, 600, top.color))
                }
            }
            if (top != null) Gullak(top, size = 40.dp)
        }
    }
}

/** "₹1,500 to get" on one line, so the tile stays short. */
@Composable
private fun Line(amount: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append(amount)
            withStyle(androidx.compose.ui.text.SpanStyle(color = TraceColors.Muted, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)) {
                append(" $label")
            }
        },
        style = ts(15, 800, color),
    )
}
