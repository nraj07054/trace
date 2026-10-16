package com.trace.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trace.app.data.Expense
import com.trace.app.data.Money
import com.trace.app.ui.theme.TraceColors
import com.trace.app.ui.theme.ts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Clickable with a soft bounded ripple, used for most tappables. */
fun Modifier.tap(role: Role = Role.Button, label: String? = null, onClick: () -> Unit): Modifier =
    this.clickable(role = role, onClickLabel = label, onClick = onClick)

fun Modifier.tapNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick),
)

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val dowFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.US)

fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val diff = ChronoUnit.DAYS.between(date, today)
    return when {
        diff == 0L -> "Today"
        diff == 1L -> "Yesterday"
        diff in 2..6 -> date.format(dowFmt)
        else -> date.format(dateFmt)
    }
}

/** "Today · 1:24 PM" */
fun whenLabel(millis: Long): String {
    val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return dayLabel(dt.toLocalDate()) + " · " + dt.format(timeFmt)
}

fun timeLabel(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFmt)

@Composable
fun CategoryTile(expense: Expense) {
    val c = expense.category
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.color.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) { TIcon(TraceIcons.category(c), c.color, 20.dp) }
}

@Composable
fun ExpenseRow(expense: Expense, currency: String, sub: String, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile(expense)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(expense.title, style = ts(15, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = ts(12, 400, TraceColors.Muted), maxLines = 1)
        }
        Text("−" + Money.compact(expense.amountMinor, currency), style = ts(15, 700), maxLines = 1)
    }
}

@Composable
fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(TraceColors.Fill).tap(label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { TIcon(icon, TraceColors.Ink, 18.dp, contentDescription = label) }
}
