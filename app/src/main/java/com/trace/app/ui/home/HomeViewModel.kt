package com.trace.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trace.app.data.Categories
import com.trace.app.data.Category
import com.trace.app.data.Expense
import com.trace.app.traceApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

enum class Period(val id: String, val label: String) {
    Week("1W", "this week"), Month("1M", "this month"), HalfYear("6M", "in 6 months"), Year("1Y", "this year")
}

/** One chart bar. [byCat] maps category id → amount, in minor units. */
data class Bucket(val label: String, val byCat: MutableMap<String, Long> = HashMap()) {
    fun total(filter: Category?) = if (filter == null) byCat.values.sum() else byCat[filter.id] ?: 0L
    fun of(c: Category) = byCat[c.id] ?: 0L
}

data class HomeUi(
    val period: Period = Period.Week,
    val filter: Category? = null,
    val selected: Int = 6,
    val buckets: List<Bucket> = emptyList(),
    val periodTotal: Long = 0,
    val deltaPct: Int? = null,
    val categories: List<Category> = Category.defaults,
    val chipTotals: List<Long> = emptyList(), // All, then each of [categories]
    val recent: List<Expense> = emptyList(),
    val currency: String = "₹",
    val name: String = "",
    val loaded: Boolean = false,
)

private data class Selection(val period: Period = Period.Week, val filter: Category? = null, val selected: Int = 6)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.traceApp.prefs
    private val repo = app.traceApp.repo
    private val selection = MutableStateFlow(Selection())
    private val now = MutableStateFlow(ZonedDateTime.now())

    val ui: StateFlow<HomeUi> = now.flatMapLatest { n ->
        // The furthest back any comparison needs: the year before the 1Y window.
        val since = n.toLocalDate().withDayOfMonth(1).minusMonths(11).minusYears(1)
            .atStartOfDay(n.zone).toInstant().toEpochMilli()
        combine(repo.observeSince(since), selection, prefs.currency, prefs.name, Categories.all) { list, sel, cur, name, cats ->
            compute(n, list, sel, cur, name, cats)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    fun refreshClock() { now.value = ZonedDateTime.now() }

    fun selectPeriod(p: Period) = selection.update { it.copy(period = p, selected = bucketCount(p) - 1) }
    fun selectBar(i: Int) = selection.update { it.copy(selected = i) }
    fun selectFilter(c: Category?) = selection.update { it.copy(filter = c) }

    private fun bucketCount(p: Period) = when (p) { Period.Week -> 7; Period.Month -> 4; Period.HalfYear -> 6; Period.Year -> 12 }

    private fun compute(now: ZonedDateTime, all: List<Expense>, sel: Selection, cur: String, name: String, cats: List<Category>): HomeUi {
        val zone = now.zone
        val today = now.toLocalDate()
        fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

        // Bucket boundaries (start-inclusive, end-exclusive), oldest first.
        val ranges: List<Triple<String, LocalDate, LocalDate>> = when (sel.period) {
            Period.Week -> (6 downTo 0).map {
                val d = today.minusDays(it.toLong())
                Triple(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US), d, d.plusDays(1))
            }
            Period.Month -> (0..3).map { j ->
                val start = today.minusDays(27L - 7 * j)
                Triple("W${j + 1}", start, start.plusDays(7))
            }
            Period.HalfYear -> (5 downTo 0).map {
                val m = today.withDayOfMonth(1).minusMonths(it.toLong())
                Triple(m.format(DateTimeFormatter.ofPattern("MMM", Locale.US)), m, m.plusMonths(1))
            }
            Period.Year -> (11 downTo 0).map {
                val m = today.withDayOfMonth(1).minusMonths(it.toLong())
                Triple(m.format(DateTimeFormatter.ofPattern("MMMMM", Locale.US)), m, m.plusMonths(1))
            }
        }
        val bounds = ranges.map { ms(it.second) to ms(it.third) }
        val buckets = ranges.map { Bucket(it.first) }
        val periodStart = bounds.first().first
        val periodEnd = bounds.last().second
        for (e in all) {
            if (e.createdAt < periodStart || e.createdAt >= periodEnd) continue
            val i = bounds.indexOfFirst { e.createdAt >= it.first && e.createdAt < it.second }
            if (i >= 0) buckets[i].byCat.merge(e.categoryId, e.amountMinor, Long::plus)
        }

        // A filter on a category that was since deleted falls back to "All".
        val filter = sel.filter?.let { f -> cats.firstOrNull { it.id == f.id } }
        fun matches(e: Expense) = filter == null || e.categoryId == filter.id
        val periodTotal = buckets.sumOf { it.total(filter) }

        // Delta: same span shifted back one period, cut at the same point in time.
        val nowMs = now.toInstant().toEpochMilli()
        val (prevStart, prevEnd) = when (sel.period) {
            Period.Week -> ms(ranges.first().second.minusDays(7)) to now.minusDays(7).toInstant().toEpochMilli()
            Period.Month -> ms(ranges.first().second.minusDays(28)) to now.minusDays(28).toInstant().toEpochMilli()
            Period.HalfYear -> ms(ranges.first().second.minusMonths(6)) to now.minusMonths(6).toInstant().toEpochMilli()
            Period.Year -> ms(ranges.first().second.minusYears(1)) to now.minusYears(1).toInstant().toEpochMilli()
        }
        val prev = all.filter { it.createdAt in prevStart until prevEnd && matches(it) }.sumOf { it.amountMinor }
        val current = all.filter { it.createdAt in periodStart..nowMs && matches(it) }.sumOf { it.amountMinor }
        val delta = if (prev > 0) ((current - prev) * 100.0 / prev).roundToInt() else null

        val chips = listOf(buckets.sumOf { it.total(null) }) +
            cats.map { c -> buckets.sumOf { it.of(c) } }

        return HomeUi(
            period = sel.period,
            filter = filter,
            selected = sel.selected.coerceIn(0, buckets.size - 1),
            buckets = buckets,
            periodTotal = periodTotal,
            deltaPct = delta,
            categories = cats,
            chipTotals = chips,
            recent = all.asSequence().filter { it.createdAt <= nowMs + 60_000 && matches(it) }.take(4).toList(),
            currency = cur,
            name = name,
            loaded = true,
        )
    }
}

