package com.homeboundlabs.trace.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homeboundlabs.trace.data.Categories
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Expense
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.traceApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/** A stretch of days within one month, both ends included. */
data class Span(val from: LocalDate, val to: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
    fun has(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)

    /** "Nov 1–14", or "Nov 1" for a single day. */
    val label: String
        get() {
            val m = from.month.getDisplayName(TextStyle.SHORT, Locale.US)
            return if (from == to) "$m ${from.dayOfMonth}" else "$m ${from.dayOfMonth}–${to.dayOfMonth}"
        }
}

/** One category's spend in the shown span. [beforeMinor] is the same days last month, or null when not compared. */
data class Slice(val category: Category, val nowMinor: Long, val beforeMinor: Long?, val sharePct: Int)

/** Last month's total over the days that are compared with [Span] this month. */
data class Comparison(val span: Span, val totalMinor: Long)

data class Warning(val title: String, val detail: String)

sealed interface TipAction {
    /** Opens the budget editor for [category], pre-filled with [amountMinor] when there's a sensible suggestion. */
    data class SetBudget(val category: Category, val amountMinor: Long?) : TipAction
    data object OpenBudgets : TipAction
}

/** The one suggestion at the top. [id] is what "Not now" hides for the month. */
data class Tip(val id: String, val heading: String, val text: String, val action: TipAction?)

/** Budgeted categories only. [todayFraction], [daysAfterToday] and [perDayMinor] are for the current month. */
data class BudgetSummary(
    val limitMinor: Long,
    val spentMinor: Long,
    val categories: List<Category>,
    val todayFraction: Float?,
    val daysAfterToday: Int?,
    val perDayMinor: Long?,
) {
    val leftMinor: Long get() = limitMinor - spentMinor
}

/** A row in the budgets sheet: every category, with or without a limit. */
data class BudgetRow(val category: Category, val limitMinor: Long?, val spentMinor: Long)

/** The new month so far, shown above last month's report in a month's first days. */
data class SoFar(val span: Span, val totalMinor: Long, val fullFrom: LocalDate)

data class InsightsUi(
    val month: YearMonth = YearMonth.now(),
    val isCurrentMonth: Boolean = true,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    /** The days shown: from the 1st (or the first expense ever, if later) to today or the month's end. Null before tracking began. */
    val span: Span? = null,
    /** Set when tracking began inside this month, so the month is only partly covered. */
    val trackedSince: LocalDate? = null,
    /** When there's no comparison because of when tracking began: the first month that will have one. */
    val comparisonsFrom: YearMonth? = null,
    /** This month is compared with last month from this day on (a few days are too few to compare). */
    val comparesFrom: LocalDate? = null,
    /** The category breakdown appears from this day on; until then [slices] is empty. */
    val categoriesFrom: LocalDate? = null,
    val totalMinor: Long = 0,
    val comparison: Comparison? = null,
    val slices: List<Slice> = emptyList(),
    val tip: Tip? = null,
    val warning: Warning? = null,
    val allGood: Boolean = false,
    val budget: BudgetSummary? = null,
    val budgetRows: List<BudgetRow> = emptyList(),
    /** Set while last month's report is shown in a new month's first days. */
    val soFar: SoFar? = null,
    /** Last month's full spend per category, the hint when setting a budget. */
    val lastMonthByCategory: Map<String, Long> = emptyMap(),
    val previousMonthName: String = "",
    val currency: String = "₹",
    /** Anything logged at all, in any month. */
    val hasAnyData: Boolean = false,
    val loaded: Boolean = false,
)

class InsightsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.traceApp.repo
    private val prefs = app.traceApp.prefs
    /** Null until the person picks a month: then Insights chooses (last month's report in a month's first days). */
    private val month = MutableStateFlow<YearMonth?>(null)
    private val settings = combine(prefs.currency, prefs.budgets, prefs.dismissedTips) { cur, budgets, dismissed -> Triple(cur, budgets, dismissed) }

    val ui: StateFlow<InsightsUi> =
        combine(repo.observeAll(), month, Categories.all, settings) { all, m, cats, (cur, budgets, dismissed) ->
            Insights.compute(all, m, cats, cur, budgets, LocalDate.now(), ZoneId.systemDefault(), dismissed)
        }.flowOn(Dispatchers.Default) // worked out off the main thread, so screens never wait on it
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUi())

    fun previousMonth() { month.value = ui.value.month.minusMonths(1) }
    fun nextMonth() { if (ui.value.canGoForward) month.value = ui.value.month.plusMonths(1) }
    fun showCurrentMonth() { month.value = YearMonth.now() }
    fun setBudget(categoryId: String, limitMinor: Long?) = prefs.setBudget(categoryId, limitMinor)
    fun dismissTip(tip: Tip) = prefs.dismissTip(Insights.dismissKey(ui.value.month, tip))
}

/** Everything on the Insights screen, worked out from the raw expenses. */
object Insights {
    /** Rises and drops smaller than this aren't worth a warning or a tip. */
    private const val NOTABLE = 500_00L

    /** Days of a month needed before it's compared, warned about or opened by default. */
    private const val SETTLED_DAYS = 7

    /** Tracking that began on the 1st–3rd of last month still counts as a comparable month (labelled with its real dates). */
    private const val GRACE_DAYS = 2L

    fun dismissKey(month: YearMonth, tip: Tip) = "$month:${tip.id}"

    fun compute(
        all: List<Expense>,
        requested: YearMonth?,
        cats: List<Category>,
        currency: String,
        budgets: Map<String, Long>,
        today: LocalDate,
        zone: ZoneId,
        dismissed: Set<String> = emptySet(),
    ): InsightsUi {
        fun money(minor: Long) = Money.whole(minor, currency)
        fun monthName(m: YearMonth) = m.month.getDisplayName(TextStyle.FULL, Locale.US)
        fun cat(id: String) = cats.firstOrNull { it.id == id } ?: Category.of(id)

        val dated = all.map { it to Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
        fun spend(span: Span): Map<String, Long> = dated.filter { span.has(it.second) }
            .groupBy { it.first.categoryId }.mapValues { (_, l) -> l.sumOf { it.first.amountMinor } }

        val trackingStart = dated.minOfOrNull { it.second }
        val thisMonth = YearMonth.from(today)
        // In a month's first days there's too little to say, so Insights opens on last month's
        // full report, if last month was tracked at all.
        val early = today.dayOfMonth < SETTLED_DAYS
        val month = requested ?: if (early && trackingStart != null && !trackingStart.isAfter(thisMonth.minusMonths(1).atEndOfMonth())) {
            thisMonth.minusMonths(1)
        } else thisMonth
        val isCurrent = month == thisMonth
        val earlyView = early && month == thisMonth.minusMonths(1)
        val prev = month.minusMonths(1)
        val limits = cats.mapNotNull { c -> budgets[c.id]?.takeIf { it > 0 }?.let { c to it } }

        fun budgetSummary(spent: Map<String, Long>, current: Boolean): BudgetSummary? {
            if (limits.isEmpty()) return null
            val limit = limits.sumOf { it.second }
            val used = limits.sumOf { (c, _) -> spent[c.id] ?: 0L }
            if (!current) return BudgetSummary(limit, used, limits.map { it.first }, null, null, null)
            val len = month.lengthOfMonth()
            val after = len - today.dayOfMonth
            val left = limit - used
            return BudgetSummary(
                limit, used, limits.map { it.first },
                todayFraction = today.dayOfMonth.toFloat() / len,
                daysAfterToday = after,
                perDayMinor = if (left > 0 && after > 0) left / after else null,
            )
        }

        val lastMonthFull = spend(Span(prev.atDay(1), prev.atEndOfMonth()))
        val base = InsightsUi(
            month = month,
            isCurrentMonth = isCurrent,
            canGoBack = trackingStart != null && YearMonth.from(trackingStart) < month,
            canGoForward = month < thisMonth,
            lastMonthByCategory = lastMonthFull,
            previousMonthName = monthName(prev),
            currency = currency,
            hasAnyData = trackingStart != null,
            budgetRows = budgetRows(cats, budgets, emptyMap()),
            budget = budgetSummary(emptyMap(), isCurrent),
            loaded = true,
        )
        val monthEnd = if (isCurrent) today else month.atEndOfMonth()
        if (trackingStart == null || trackingStart.isAfter(monthEnd)) {
            return base.copy(tip = tips(base, all.size, null, null, emptyMap(), false, dismissed, ::money, ::monthName))
        }

        // ---- the shown span and its comparison ----
        val span = Span(maxOf(month.atDay(1), trackingStart), monthEnd)
        val now = spend(span)
        val total = now.values.sum()
        val prevTo = prev.atDay(minOf(monthEnd.dayOfMonth, prev.lengthOfMonth()))
        val settled = span.days >= SETTLED_DAYS
        val tracked = span.from == month.atDay(1) && !trackingStart.isAfter(prev.atDay(1).plusDays(GRACE_DAYS))
        // A few days against a few days says nothing, so the current month waits for its first week.
        val comparable = tracked && (!isCurrent || settled)
        val compareSpan = Span(maxOf(prev.atDay(1), trackingStart), prevTo)
        val before = if (comparable) spend(compareSpan) else null

        val ids = now.filterValues { it > 0 }.keys + (before?.filterValues { it > 0 }?.keys ?: emptySet())
        val nowIds = ids.filter { (now[it] ?: 0) > 0 }
        val shares = nowIds.zip(largestRemainder(nowIds.map { now.getValue(it) })).toMap()
        val slices = ids.map { id -> Slice(cat(id), now[id] ?: 0, before?.let { it[id] ?: 0 }, shares[id] ?: 0) }
            .sortedWith(compareByDescending<Slice> { it.nowMinor }.thenByDescending { it.beforeMinor ?: 0 })

        // A past month tracked for only part of its days can't be judged against a whole month's budget.
        val partial = span.from != month.atDay(1)
        val budget = if (partial && !isCurrent) null else budgetSummary(now, isCurrent)

        // ---- the one warning, most serious first ----
        val warning = run {
            limits.map { (c, limit) -> Triple(c, limit, now[c.id] ?: 0L) }
                .filter { it.third > it.second }.maxByOrNull { it.third - it.second }
                ?.let { (c, limit, spent) ->
                    return@run Warning("${c.name} is over budget", "${money(spent)} of ${money(limit)} · ${money(spent - limit)} over")
                }
            if (isCurrent && comparable && settled) {
                slices.filter { s -> val b = s.beforeMinor ?: 0; b > 0 && s.nowMinor >= 2 * b && s.nowMinor - b >= NOTABLE }
                    .maxByOrNull { it.nowMinor - (it.beforeMinor ?: 0) }
                    ?.let { s ->
                        val b = s.beforeMinor!!
                        return@run Warning(
                            "${s.category.name} is ${times(s.nowMinor.toDouble() / b)} last month",
                            "${money(s.nowMinor)} now, ${money(b)} by ${compareSpan.to.month.getDisplayName(TextStyle.SHORT, Locale.US)} ${compareSpan.to.dayOfMonth}",
                        )
                    }
            }
            if (isCurrent && settled && budget?.todayFraction != null) {
                limits.map { (c, limit) -> Triple(c, limit, now[c.id] ?: 0L) }
                    .filter { (_, limit, spent) ->
                        val used = spent.toFloat() / limit
                        used >= 0.5f && used >= budget.todayFraction + 0.25f
                    }
                    .maxByOrNull { it.third.toFloat() / it.second }
                    ?.let { (c, limit, spent) ->
                        return@run Warning(
                            "${c.name} is going fast",
                            "${money(spent)} of ${money(limit)} used · day ${today.dayOfMonth} of ${month.lengthOfMonth()}",
                        )
                    }
            }
            null
        }

        // ---- the new month so far, above last month's report ----
        val soFar = if (earlyView) {
            val sSpan = Span(maxOf(thisMonth.atDay(1), trackingStart), today)
            SoFar(sSpan, spend(sSpan).values.sum(), thisMonth.atDay(SETTLED_DAYS))
        } else null

        val ui = base.copy(
            span = span,
            trackedSince = span.from.takeIf { partial },
            comparisonsFrom = if (tracked) null else firstComparableMonth(trackingStart),
            comparesFrom = if (tracked && !comparable) month.atDay(SETTLED_DAYS) else null,
            totalMinor = total,
            comparison = if (comparable) Comparison(compareSpan, before!!.values.sum()) else null,
            // The first days of a month are too few for a breakdown worth reading.
            slices = if (isCurrent && !settled) emptyList() else slices,
            categoriesFrom = if (isCurrent && !settled) span.from.plusDays(SETTLED_DAYS - 1L) else null,
            warning = warning,
            allGood = warning == null && total > 0 && settled && (comparable || budget != null),
            budget = budget,
            budgetRows = budgetRows(cats, budgets, now),
            soFar = soFar,
        )
        return ui.copy(tip = tips(ui, all.size, now, before, budgets, earlyView, dismissed, ::money, ::monthName))
    }

    /** The one tip to show, by priority, skipping any hidden this month. */
    private fun tips(
        ui: InsightsUi,
        count: Int,
        now: Map<String, Long>?,
        before: Map<String, Long>?,
        budgets: Map<String, Long>,
        earlyView: Boolean,
        dismissed: Set<String>,
        money: (Long) -> String,
        monthName: (YearMonth) -> String,
    ): Tip? {
        // Tips are for planning ahead: the current month, or last month's report in a new month's first days.
        if (!ui.isCurrentMonth && !earlyView) return null
        val candidates = mutableListOf<Tip>()
        fun budgeted(id: String) = (budgets[id] ?: 0L) > 0
        fun cat(id: String) = ui.budgetRows.firstOrNull { it.category.id == id }?.category ?: Category.of(id)

        if (ui.budget == null && count >= 3) {
            candidates += Tip("budgets", "Tip for you", "Set monthly budgets to see how you're doing", TipAction.OpenBudgets)
        }

        // Planning the next month: from last month's report in the first days, or in a month's last days.
        val daysAfter = ui.month.lengthOfMonth() - (ui.span?.to?.dayOfMonth ?: 0)
        val plan: Pair<Map<String, Long>, String>? = when {
            earlyView -> (now ?: emptyMap()) to "in ${monthName(ui.month)}"
            ui.span != null && daysAfter <= 4 -> (now ?: emptyMap()) to "this month"
            else -> null
        }
        if (plan != null) {
            val (spent, period) = plan
            val next = monthName(ui.month.plusMonths(1))
            spent.filter { !budgeted(it.key) && it.value >= NOTABLE }.maxByOrNull { it.value }?.let { (id, amount) ->
                val c = cat(id)
                candidates += Tip(
                    "plan-$id", "Plan $next",
                    "${c.name} was ${money(amount)} $period with no budget. Set ${money(roundUp(amount))} for $next?",
                    TipAction.SetBudget(c, roundUp(amount)),
                )
            }
            spent.filter { budgeted(it.key) && it.value > budgets.getValue(it.key) }.maxByOrNull { it.value - budgets.getValue(it.key) }
                ?.let { (id, amount) ->
                    val c = cat(id)
                    candidates += Tip(
                        "raise-$id", "Plan $next",
                        "${c.name} went ${money(amount - budgets.getValue(id))} over. Raise it to ${money(roundUp(amount))} for $next?",
                        TipAction.SetBudget(c, roundUp(amount)),
                    )
                }
        }

        if (ui.isCurrentMonth && now != null && before != null && (ui.span?.days ?: 0) >= SETTLED_DAYS) {
            now.filter { !budgeted(it.key) && it.value - (before[it.key] ?: 0) >= NOTABLE }
                .maxByOrNull { it.value - (before[it.key] ?: 0) }
                ?.let { (id, amount) ->
                    val c = cat(id)
                    candidates += Tip(
                        "up-$id", "Tip for you",
                        "${c.name} is up ${money(amount - (before[id] ?: 0))} on ${ui.previousMonthName} and has no budget. Set one?",
                        TipAction.SetBudget(c, null),
                    )
                }
        }

        ui.budget?.perDayMinor?.let { candidates += Tip("pace", "Tip for you", "Up to ${money(it)} a day keeps you on budget", null) }

        return candidates.firstOrNull { "${ui.month}:${it.id}" !in dismissed }
    }

    private fun budgetRows(cats: List<Category>, budgets: Map<String, Long>, spent: Map<String, Long>): List<BudgetRow> =
        cats.map { BudgetRow(it, budgets[it.id]?.takeIf { l -> l > 0 }, spent[it.id] ?: 0) }
            .sortedWith(compareBy<BudgetRow> { it.limitMinor == null }.thenByDescending { it.spentMinor })

    /** The first month compared with a fully (or almost fully) tracked month before it. */
    private fun firstComparableMonth(trackingStart: LocalDate): YearMonth {
        val next = YearMonth.from(trackingStart).plusMonths(1)
        return if (trackingStart.dayOfMonth <= 1 + GRACE_DAYS) next else next.plusMonths(1)
    }

    /** Whole percentages that always add up to exactly 100 (largest-remainder rounding). */
    internal fun largestRemainder(values: List<Long>): List<Int> {
        val total = values.sum()
        if (total <= 0) return values.map { 0 }
        val raw = values.map { it * 100.0 / total }
        val floors = raw.map { floor(it).toInt() }.toMutableList()
        val missing = 100 - floors.sum()
        raw.indices.sortedByDescending { raw[it] - floors[it] }.take(missing).forEach { floors[it]++ }
        return floors
    }

    /** "3×", "2.5×", "12×". */
    private fun times(ratio: Double): String {
        if (ratio >= 10) return "${ratio.toInt()}×"
        val r = (ratio * 10).roundToInt() / 10.0
        return if (r % 1.0 == 0.0) "${r.toInt()}×" else "$r×"
    }

    /** Up to the next ₹100. */
    private fun roundUp(minor: Long) = (minor + 99_99) / 100_00 * 100_00
}
