@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.homeboundlabs.trace.ui.insights

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.HabitRepository
import com.homeboundlabs.trace.data.Streaks
import com.homeboundlabs.trace.share.Cards
import com.homeboundlabs.trace.share.RecapCard
import com.homeboundlabs.trace.share.RecapSlice
import com.homeboundlabs.trace.share.Share
import com.homeboundlabs.trace.traceApp
import com.homeboundlabs.trace.ui.components.PrimaryButton
import com.homeboundlabs.trace.ui.components.SecondaryButton
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Everything the recap card shows for [month], worked out from the expenses and "nothing spent" days. */
suspend fun buildRecap(context: Context, month: YearMonth): RecapCard = withContext(Dispatchers.Default) {
    val app = context.traceApp
    val expenses = app.repo.observeAll().first()
    val marks = app.habits.observeNoSpendDays().first().mapTo(HashSet()) { LocalDate.ofEpochDay(it) }
    val inMonth = expenses.filter { YearMonth.from(HabitRepository.dayOf(it.createdAt)) == month }
    val today = LocalDate.now()
    // This month so far is compared with the same days of last month (Nov 1–24 vs Oct 1–24), like Insights.
    val current = month == YearMonth.from(today)
    val upTo = if (current) today.dayOfMonth else 31
    val prev = expenses.filter {
        val d = HabitRepository.dayOf(it.createdAt)
        YearMonth.from(d) == month.minusMonths(1) && d.dayOfMonth <= upTo
    }.sumOf { it.amountMinor }
    val total = inMonth.sumOf { it.amountMinor }
    val spentDays = inMonth.mapTo(HashSet()) { HabitRepository.dayOf(it.createdAt) }
    val monthMarks = marks.filterTo(HashSet()) { YearMonth.from(it) == month && it !in spentDays }
    val firstEver = expenses.minOfOrNull { it.createdAt }?.let { HabitRepository.dayOf(it) }
    // Only compare with a month that was fully tracked, the same rule as Home and Insights.
    val fair = prev > 0 && firstEver != null && !firstEver.isAfter(month.minusMonths(1).atDay(1))
    RecapCard(
        monthLabel = month.month.getDisplayName(TextStyle.FULL, Locale.US),
        totalMinor = total,
        deltaPct = if (fair) ((total - prev) * 100.0 / prev).roundToInt() else null,
        soFar = current,
        slices = inMonth.groupBy { it.categoryId }.map { (id, list) ->
            val c = Category.of(id)
            RecapSlice(c.name, c.argb, list.sumOf { it.amountMinor })
        }.sortedByDescending { it.amountMinor },
        daysInPeriod = if (current) today.dayOfMonth else month.lengthOfMonth(),
        bestStreak = Streaks.best(spentDays + monthMarks),
        daysLogged = (spentDays + monthMarks).size,
        expenses = inMonth.size,
        currency = app.prefs.currency.value,
    )
}

@Composable
fun RecapSheet(month: YearMonth, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var caption by remember { mutableStateOf("") }
    LaunchedEffect(month) {
        val recap = buildRecap(context, month)
        caption = "My ${recap.monthLabel} on Trace 📊"
        bmp = withContext(Dispatchers.Default) { Cards(context).recap(recap) }
    }
    fun send(whatsApp: Boolean) {
        val image = bmp ?: return
        scope.launch { Share.send(context, Share.save(context, image, "trace-recap"), caption, whatsApp) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TraceColors.Sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Your ${month.month.getDisplayName(TextStyle.FULL, Locale.US)} recap", style = ts(18, 800))
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).aspectRatio(bmp?.let { it.width.toFloat() / it.height } ?: (1080f / 1300f)).clip(RoundedCornerShape(22.dp))
                    .border(1.dp, TraceColors.Border, RoundedCornerShape(22.dp)).background(TraceColors.Field),
                contentAlignment = Alignment.Center,
            ) { bmp?.let { Image(it.asImageBitmap(), "Recap card", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } }
            PrimaryButton("Share on WhatsApp", enabled = bmp != null, bg = androidx.compose.ui.graphics.Color(0xFF1FA855)) { send(true) }
            SecondaryButton("Other apps", Modifier.fillMaxWidth()) { send(false) }
        }
    }
}
