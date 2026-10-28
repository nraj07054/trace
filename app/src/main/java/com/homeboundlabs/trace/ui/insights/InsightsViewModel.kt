package com.homeboundlabs.trace.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homeboundlabs.trace.data.Categories
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Expense
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.traceApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A friendly one-line observation. [tone] picks the card colour. */
data class Highlight(val text: String, val emoji: String, val tone: Tone) {
    enum class Tone { Good, Warn, Bad, Neutral }
}

/** One category this month vs the same span last month. [pct] is null when there was nothing last month. */
data class Change(val category: Category, val nowMinor: Long, val beforeMinor: Long, val pct: Int?) {
    val diffMinor: Long get() = nowMinor - beforeMinor
}

/** Budget progress for one category in the chosen month. */
data class BudgetLine(
    val category: Category,
    val limitMinor: Long,
    val spentMinor: Long,
    /** Days left in the month including today (0 for past months). */
    val daysLeft: Int,
    /** Set when, at the current pace, the limit is reached before the month ends. */
    val runsOutOn: LocalDate?,
) {
    val leftMinor: Long get() = limitMinor - spentMinor
    val fraction: Float get() = if (limitMinor > 0) spentMinor.toFloat() / limitMinor else 0f
    val status: Status get() = when {
        spentMinor > limitMinor -> Status.Over
        fraction >= 0.8f || runsOutOn != null -> Status.Close
        else -> Status.Ok
    }
    enum class Status { Ok, Close, Over }
}

data class InsightsUi(
    val month: YearMonth = YearMonth.now(),
    val isCurrentMonth: Boolean = true,
    val canGoBack: Boolean = false,
    val previousMonthName: String = "",
    val highlights: List<Highlight> = emptyList(),
    val changes: List<Change> = emptyList(),
    val budgets: List<BudgetLine> = emptyList(),
    /** Categories without a budget yet, offered as "Set a budget". */
    val unbudgeted: List<Category> = emptyList(),
    /** Last month's total per category, shown as a hint when setting a budget. */
    val lastMonthByCategory: Map<String, Long> = emptyMap(),
    val currency: String = "₹",
    val hasData: Boolean = false,
    /** Nothing at all was logged in the comparison span of the previous month. */
    val nothingBefore: Boolean = true,
    val loaded: Boolean = false,
)

class InsightsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.traceApp.repo
    private val prefs = app.traceApp.prefs
    private val month = MutableStateFlow(YearMonth.now())

    val ui: StateFlow<InsightsUi> =
        combine(repo.observeAll(), month, Categories.all, prefs.currency, prefs.budgets) { all, m, cats, cur, budgets ->
            Insights.compute(all, m, cats, cur, budgets, LocalDate.now(), ZoneId.systemDefault())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUi())

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { if (it < YearMonth.now()) it.plusMonths(1) else it }
    fun setBudget(categoryId: String, limitMinor: Long?) = prefs.setBudget(categoryId, limitMinor)
}

/** Everything on the Insights screen, worked out from the raw expenses. */
object Insights {
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.US)

    fun compute(
        all: List<Expense>,
        month: YearMonth,
        cats: List<Category>,
        currency: String,
        budgets: Map<String, Long>,
        today: LocalDate,
        zone: ZoneId,
    ): InsightsUi {
        fun dateOf(e: Expense): LocalDate = Instant.ofEpochMilli(e.createdAt).atZone(zone).toLocalDate()
        fun money(minor: Long) = Money.whole(minor, currency)

        val isCurrent = month == YearMonth.from(today)
        val first = month.atDay(1)
        val lastCounted = if (isCurrent) today else month.atEndOfMonth()
        val daysCounted = lastCounted.dayOfMonth

        // Compare like with like: the 1st up to the same day number of the previous month.
        val prev = month.minusMonths(1)
        val prevEnd = prev.atDay(minOf(lastCounted.dayOfMonth, prev.lengthOfMonth()))
        val prevName = prev.month.name.lowercase().replaceFirstChar { it.uppercase() }

        fun inRange(e: Expense, from: LocalDate, to: LocalDate) = dateOf(e).let { !it.isBefore(from) && !it.isAfter(to) }
        val now = all.filter { inRange(it, first, lastCounted) }
        val before = all.filter { inRange(it, prev.atDay(1), prevEnd) }
        val nowBy = now.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
        val beforeBy = before.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
        val lastMonthFull = all.filter { inRange(it, prev.atDay(1), prev.atEndOfMonth()) }
            .groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }

        fun cat(id: String) = cats.firstOrNull { it.id == id } ?: Category.of(id)

        // ---- what changed ----
        val changes = (nowBy.keys + beforeBy.keys).map { id ->
            val n = nowBy[id] ?: 0L
            val b = beforeBy[id] ?: 0L
            Change(cat(id), n, b, if (b > 0) ((n - b) * 100.0 / b).roundToInt() else null)
        }.filter { it.diffMinor != 0L }.sortedByDescending { abs(it.diffMinor) }

        // ---- budgets ----
        val daysLeft = if (isCurrent) month.lengthOfMonth() - today.dayOfMonth + 1 else 0
        val budgetLines = cats.mapNotNull { c ->
            val limit = budgets[c.id] ?: return@mapNotNull null
            val spent = nowBy[c.id] ?: 0L
            // Project when the limit is hit at this month's average daily pace.
            val runsOut = if (isCurrent && spent in 1 until limit) {
                val perDay = spent.toDouble() / daysCounted
                val daysToLimit = ceil((limit - spent) / perDay).toLong()
                today.plusDays(daysToLimit).takeIf { !it.isAfter(month.atEndOfMonth()) }
            } else null
            BudgetLine(c, limit, spent, daysLeft, runsOut)
        }

        // ---- highlights (only the ones that apply, most useful first) ----
        val hl = mutableListOf<Highlight>()
        budgetLines.filter { it.status == BudgetLine.Status.Over }.maxByOrNull { -it.leftMinor }?.let {
            hl += Highlight("You're ${money(-it.leftMinor)} over your ${it.category.name} budget", "⚠️", Highlight.Tone.Bad)
        }
        budgetLines.filter { it.runsOutOn != null }.minByOrNull { it.runsOutOn!! }?.let {
            hl += Highlight("At this pace your ${it.category.name} budget runs out around ${it.runsOutOn!!.format(shortDate)}", "⏳", Highlight.Tone.Warn)
        }
        changes.filter { it.diffMinor < 0 && it.beforeMinor > 0 && -it.diffMinor >= 100_00 && (it.pct ?: 0) <= -10 }
            .minByOrNull { it.diffMinor }?.let {
                val verb = if (isCurrent) "You've spent" else "You spent"
                hl += Highlight("$verb ${money(-it.diffMinor)} less on ${it.category.name} than in $prevName", "🎉", Highlight.Tone.Good)
            }
        changes.filter { it.diffMinor > 0 && it.beforeMinor > 0 && it.diffMinor >= 200_00 && (it.pct ?: 0) >= 20 }
            .maxByOrNull { it.diffMinor }?.let {
                hl += Highlight("${it.category.name} is up ${it.pct}% compared with $prevName", "📈", Highlight.Tone.Warn)
            }
        weekdayHighlight(now.map { dateOf(it) to it.amountMinor }, first, lastCounted)?.let { hl += it }
        if (isCurrent) {
            // All expenses, not just this month's: the week can start in the previous month.
            val loggedDays = all.mapTo(HashSet()) { dateOf(it) }
            val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val quiet = (0..(today.dayOfWeek.value - 1)).count { weekStart.plusDays(it.toLong()) !in loggedDays }
            // A quiet day only means "spent nothing" for someone who logs regularly:
            // require logging on at least 5 of the last 7 days before calling it a no-spend day.
            val loggedLastWeek = (0..6).count { today.minusDays(it.toLong()) in loggedDays }
            if (quiet >= 2 && loggedLastWeek >= 5) hl += Highlight("$quiet no-spend days this week", "🌱", Highlight.Tone.Good)
        }
        if (hl.isEmpty()) {
            hl += Highlight(
                if (now.isEmpty()) "Nothing logged yet this month. Highlights show up as you add expenses."
                else "Keep logging. Highlights appear once there's more to compare.",
                "✨", Highlight.Tone.Neutral,
            )
        }

        val earliest = all.minOfOrNull { dateOf(it) }
        return InsightsUi(
            month = month,
            isCurrentMonth = isCurrent,
            canGoBack = earliest != null && YearMonth.from(earliest) < month,
            previousMonthName = prevName,
            highlights = hl.take(4),
            changes = changes,
            budgets = budgetLines,
            unbudgeted = cats.filter { it.id !in budgets },
            lastMonthByCategory = lastMonthFull,
            currency = currency,
            hasData = now.isNotEmpty() || before.isNotEmpty(),
            nothingBefore = before.isEmpty(),
            loaded = true,
        )
    }

    /** "Saturdays cost you 3× a normal day": only when one weekday clearly stands out. */
    private fun weekdayHighlight(spends: List<Pair<LocalDate, Long>>, from: LocalDate, to: LocalDate): Highlight? {
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        if (days.size < 14 || spends.isEmpty()) return null
        val totalByDow = LongArray(7); spends.forEach { (d, a) -> totalByDow[d.dayOfWeek.value - 1] += a }
        val countByDow = IntArray(7); days.forEach { countByDow[it.dayOfWeek.value - 1]++ }
        val avg = DoubleArray(7) { if (countByDow[it] > 0) totalByDow[it].toDouble() / countByDow[it] else 0.0 }
        val top = avg.indices.maxByOrNull { avg[it] } ?: return null
        val rest = avg.filterIndexed { i, _ -> i != top }.average()
        val name = DayOfWeek.of(top + 1).name.lowercase().replaceFirstChar { it.uppercase() }
        val spendDays = spends.map { it.first }.toSet().size
        // A lopsided month with very few spending days gives silly ratios ("23×"); say it plainly instead.
        if (rest <= 0 || spendDays < 6) {
            return if (totalByDow[top] * 2 > spends.sumOf { it.second }) {
                Highlight("Most of your spending happens on ${name}s", "📅", Highlight.Tone.Neutral)
            } else null
        }
        val ratio = avg[top] / rest
        return when {
            ratio < 2.0 -> null
            ratio <= 5.0 -> Highlight("${name}s cost you ${ratio.roundToInt()}× a normal day", "📅", Highlight.Tone.Neutral)
            else -> Highlight("Most of your spending happens on ${name}s", "📅", Highlight.Tone.Neutral)
        }
    }
}
