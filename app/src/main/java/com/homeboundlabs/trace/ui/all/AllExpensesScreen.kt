package com.homeboundlabs.trace.ui.all

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.traceApp
import com.homeboundlabs.trace.ui.components.CircleButton
import com.homeboundlabs.trace.ui.components.ExpenseRow
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.dayLabel
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.components.timeLabel
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts
import java.time.Instant
import java.time.ZoneId

@Composable
fun AllExpensesScreen(onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val app = LocalContext.current.traceApp
    val all by app.repo.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val currency by app.prefs.currency.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val byDay = all.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }

    Column(Modifier.fillMaxSize().background(TraceColors.Surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(TraceIcons.Back, "Back", onBack)
            Text("All expenses", style = ts(16, 700), modifier = Modifier.weight(1f).padding(end = 44.dp), textAlign = TextAlign.Center)
        }
        Text(
            "Tap an expense to edit or delete it",
            style = ts(12, 500, TraceColors.Faint),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp),
        ) {
            byDay.forEach { (day, items) ->
                item(key = day.toEpochDay()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(dayLabel(day), style = ts(13, 700, TraceColors.Muted))
                        Text(Money.compact(items.sumOf { it.amountMinor }, currency), style = ts(13, 700, TraceColors.Muted))
                    }
                }
                items(items, key = { it.id }) { e ->
                    ExpenseRow(
                        e, currency, timeLabel(e.createdAt) + " · " + e.category.label,
                        Modifier.clip(RoundedCornerShape(14.dp)).tap(label = "Edit ${e.title}") { onEdit(e.id) },
                    )
                }
            }
        }
    }
}
