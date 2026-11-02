package com.homeboundlabs.trace.ui.insights

import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class InsightsTest {
    private val zone = ZoneOffset.UTC
    private val food = Category.Food
    private val shopping = Category.Shopping
    private val bills = Category.Bills
    private val travel = Category.Travel

    private fun spend(date: String, rupees: Long, c: Category = food) =
        Expense(amountMinor = rupees * 100, categoryId = c.id, note = "", createdAt = LocalDate.parse(date).atTime(12, 0).toInstant(zone).toEpochMilli())

    private fun insights(
        today: String,
        all: List<Expense>,
        budgets: Map<Category, Long> = emptyMap(),
        month: String? = null,
        dismissed: Set<String> = emptySet(),
        auto: Boolean = false,
    ): InsightsUi {
        val d = LocalDate.parse(today)
        return Insights.compute(
            all, month?.let { YearMonth.parse(it) } ?: if (auto) null else YearMonth.from(d), Category.defaults, "₹",
            budgets.map { (c, r) -> c.id to r * 100 }.toMap(), d, zone, dismissed,
        )
    }

    /** The mid-month mockup: Nov 1–14 against Oct 1–14, tracked since September. */
    private val midMonth = listOf(
        spend("2026-09-10", 100),
        spend("2026-11-03", 1850), spend("2026-11-05", 1200, shopping), spend("2026-11-06", 900, bills), spend("2026-11-08", 550, travel),
        spend("2026-10-03", 3100), spend("2026-10-05", 400, shopping), spend("2026-10-06", 900, bills), spend("2026-10-08", 700, travel),
        spend("2026-10-20", 5000), // after Oct 14: must not count in the comparison
    )
    private val midBudgets = mapOf(food to 5000L, bills to 3000L, travel to 3000L)

    @Test fun `mid-month numbers all agree`() {
        val ui = insights("2026-11-14", midMonth, midBudgets)
        assertEquals("Nov 1–14", ui.span!!.label)
        assertEquals(4500_00L, ui.totalMinor)
        assertEquals("Oct 1–14", ui.comparison!!.span.label)
        assertEquals(5100_00L, ui.comparison!!.totalMinor)
        assertEquals(listOf(food, shopping, bills, travel), ui.slices.map { it.category })
        assertEquals(listOf(41, 27, 20, 12), ui.slices.map { it.sharePct })
        assertEquals(100, ui.slices.sumOf { it.sharePct })
        assertEquals(listOf(3100_00L, 400_00L, 900_00L, 700_00L), ui.slices.map { it.beforeMinor })
        assertEquals("Shopping is 3× last month", ui.warning!!.title)
        assertEquals("₹1,200 now, ₹400 by Oct 14", ui.warning!!.detail)
        // Budget counts Food, Bills and Travel only: 3,300 of 11,000, 7,700 over 16 days.
        assertEquals(11000_00L, ui.budget!!.limitMinor)
        assertEquals(3300_00L, ui.budget!!.spentMinor)
        assertEquals(16, ui.budget!!.daysAfterToday)
        assertEquals(481_25L, ui.budget!!.perDayMinor)
        assertEquals("up-${shopping.id}", ui.tip!!.id)
        assertEquals("Shopping is up ₹800 on October and has no budget. Set one?", ui.tip!!.text)
    }

    @Test fun `no expenses at all`() {
        val ui = insights("2026-11-14", emptyList())
        assertFalse(ui.hasAnyData)
        assertNull(ui.span)
        assertNull(ui.tip)
        assertFalse(ui.allGood)
    }

    @Test fun `started this month on the 2nd`() {
        val ui = insights("2026-12-03", listOf(spend("2026-12-02", 300), spend("2026-12-03", 200, travel)))
        assertEquals("Dec 2–3", ui.span!!.label)
        assertEquals(LocalDate.parse("2026-12-02"), ui.trackedSince)
        assertNull(ui.comparison)
        assertEquals(YearMonth.of(2027, 1), ui.comparisonsFrom)
        assertTrue(ui.slices.isEmpty())
        assertEquals(LocalDate.parse("2026-12-08"), ui.categoriesFrom)
        assertNull(ui.warning)
    }

    @Test fun `started on the 2nd compares next month with honest dates`() {
        val ui = insights("2027-01-10", listOf(spend("2026-12-02", 300), spend("2026-12-05", 200), spend("2027-01-04", 900)))
        assertEquals("Jan 1–10", ui.span!!.label)
        assertEquals("Dec 2–10", ui.comparison!!.span.label)
        assertEquals(500_00L, ui.comparison!!.totalMinor)
    }

    @Test fun `started late last month has no comparison yet`() {
        val all = listOf(spend("2026-10-26", 200), spend("2026-10-31", 303), spend("2026-11-30", 1000))
        val nov = insights("2026-11-30", all)
        assertNull(nov.comparison)
        assertNull(nov.trackedSince)
        assertEquals(YearMonth.of(2026, 12), nov.comparisonsFrom)
        assertTrue(nov.slices.all { it.beforeMinor == null })
        // December is the first compared month.
        assertNotNull(insights("2026-12-10", all + spend("2026-12-02", 100)).comparison)
    }

    @Test fun `past month started partway shows only its tracked days`() {
        val ui = insights("2026-11-20", listOf(spend("2026-10-26", 200), spend("2026-10-31", 303)), month = "2026-10")
        assertEquals("Oct 26–31", ui.span!!.label)
        assertEquals(LocalDate.parse("2026-10-26"), ui.trackedSince)
        assertNull(ui.comparison)
        assertNull(ui.tip) // tips are for the current month only
        assertFalse(ui.canGoBack)
    }

    @Test fun `short previous month is compared up to its last day`() {
        val all = listOf(spend("2027-01-05", 100), spend("2027-02-28", 400), spend("2027-03-30", 500))
        val ui = insights("2027-03-30", all)
        assertEquals("Feb 1–28", ui.comparison!!.span.label)
        assertEquals(400_00L, ui.comparison!!.totalMinor)
    }

    @Test fun `first days of a month open last month's full report`() {
        val all = listOf(
            spend("2026-09-15", 100),
            spend("2026-10-03", 4500), spend("2026-10-09", 2500, bills), spend("2026-10-12", 2000, shopping), spend("2026-10-20", 1000, travel),
            spend("2026-11-01", 97),
        )
        val nov1 = insights("2026-11-01", all, midBudgets, auto = true)
        assertEquals(YearMonth.of(2026, 10), nov1.month)
        assertEquals("Oct 1–31", nov1.span!!.label)
        assertEquals(10000_00L, nov1.totalMinor)
        assertTrue(nov1.canGoForward)
        assertEquals(97_00L, nov1.soFar!!.totalMinor)
        assertEquals(LocalDate.parse("2026-11-07"), nov1.soFar!!.fullFrom)
        assertEquals(8000_00L, nov1.budget!!.spentMinor)
        assertEquals(3000_00L, nov1.budget!!.leftMinor)
        // Planning November from October's unbudgeted spending.
        assertEquals("Shopping was ₹2,000 in October with no budget. Set ₹2,000 for November?", nov1.tip!!.text)
        // From day 7 Insights opens on the current month.
        val nov7 = insights("2026-11-07", all, midBudgets, auto = true)
        assertEquals(YearMonth.of(2026, 11), nov7.month)
        assertNull(nov7.soFar)
    }

    @Test fun `the current month is only compared after a week`() {
        val all = listOf(spend("2026-09-15", 100), spend("2026-10-01", 621), spend("2026-11-01", 97))
        val nov1 = insights("2026-11-01", all)
        assertNull(nov1.comparison)
        assertEquals(LocalDate.parse("2026-11-07"), nov1.comparesFrom)
        assertTrue(nov1.slices.isEmpty())
        assertEquals(LocalDate.parse("2026-11-07"), nov1.categoriesFrom)
        assertNotNull(insights("2026-11-07", all).comparison)
    }

    @Test fun `started this month opens on this month even in its first days`() {
        val ui = insights("2026-12-03", listOf(spend("2026-12-02", 300)), auto = true)
        assertEquals(YearMonth.of(2026, 12), ui.month)
        assertNull(ui.soFar)
    }

    @Test fun `over budget is the warning and the budget never shows a negative day amount`() {
        val ui = insights("2026-11-14", listOf(spend("2026-10-01", 10), spend("2026-11-02", 5300)), mapOf(food to 5000L))
        assertEquals("Food is over budget", ui.warning!!.title)
        assertEquals("₹5,300 of ₹5,000 · ₹300 over", ui.warning!!.detail)
        assertEquals(-300_00L, ui.budget!!.leftMinor)
        assertNull(ui.budget!!.perDayMinor)
    }

    @Test fun `no pace or rise warnings in the first week`() {
        val all = listOf(spend("2026-09-01", 10), spend("2026-10-02", 100, shopping), spend("2026-11-02", 3000, shopping), spend("2026-11-02", 4000))
        val early = insights("2026-11-03", all, mapOf(food to 5000L))
        assertNull(early.warning)
        assertNotNull(insights("2026-11-07", all, mapOf(food to 5000L)).warning)
    }

    @Test fun `going fast once settled`() {
        val ui = insights("2026-11-08", listOf(spend("2026-09-01", 10), spend("2026-11-02", 4000)), mapOf(food to 5000L))
        assertEquals("Food is going fast", ui.warning!!.title)
        assertEquals("₹4,000 of ₹5,000 used · day 8 of 30", ui.warning!!.detail)
    }

    @Test fun `last day of the month`() {
        val ui = insights("2026-11-30", listOf(spend("2026-09-01", 10), spend("2026-11-02", 2000)), mapOf(food to 5000L))
        assertEquals(0, ui.budget!!.daysAfterToday)
        assertNull(ui.budget!!.perDayMinor)
    }

    @Test fun `no budgets suggests setting them`() {
        val ui = insights("2026-11-14", listOf(spend("2026-11-01", 100), spend("2026-11-02", 100), spend("2026-11-03", 100)))
        assertNull(ui.budget)
        assertEquals(TipAction.OpenBudgets, ui.tip!!.action)
    }

    @Test fun `all good only when something was checked`() {
        val noBudgetFirstMonth = insights("2026-11-14", listOf(spend("2026-11-02", 100)))
        assertFalse(noBudgetFirstMonth.allGood)
        val calm = insights("2026-11-14", listOf(spend("2026-09-01", 10), spend("2026-11-02", 1000)), mapOf(food to 5000L))
        assertTrue(calm.allGood)
    }

    @Test fun `nothing spent yet this month`() {
        val ui = insights("2026-11-10", listOf(spend("2026-09-01", 100), spend("2026-10-03", 500)))
        assertEquals(0L, ui.totalMinor)
        assertEquals(500_00L, ui.comparison!!.totalMinor)
        assertFalse(ui.allGood)
    }

    @Test fun `not now hides a tip for its month only`() {
        val ui = insights("2026-11-14", midMonth, midBudgets)
        val hidden = insights("2026-11-14", midMonth, midBudgets, dismissed = setOf(Insights.dismissKey(ui.month, ui.tip!!)))
        assertEquals("pace", hidden.tip!!.id)
    }

    @Test fun `a partly tracked month gets no budget result`() {
        val all = listOf(spend("2026-10-28", 308), spend("2026-10-30", 56, travel), spend("2026-11-01", 303))
        val budgets = mapOf(food to 5000L)
        val nov1 = insights("2026-11-01", all, budgets, auto = true)
        assertEquals("Oct 28–31", nov1.span!!.label)
        assertEquals(LocalDate.parse("2026-10-28"), nov1.trackedSince)
        assertEquals(364_00L, nov1.totalMinor)
        assertEquals(85, nov1.slices.first().sharePct)
        assertNull(nov1.budget)
        assertNull(nov1.comparison)
        assertFalse(nov1.allGood)
        assertEquals(161_96L, insights("2026-11-01", all, budgets).budget!!.perDayMinor)
    }

    @Test fun `shares always add to 100`() {
        assertEquals(listOf(34, 33, 33), Insights.largestRemainder(listOf(1L, 1L, 1L)))
        assertEquals(100, Insights.largestRemainder(listOf(1850L, 1200L, 900L, 550L, 7L)).sum())
        assertEquals(listOf(0, 0), Insights.largestRemainder(listOf(0L, 0L)))
    }
}
