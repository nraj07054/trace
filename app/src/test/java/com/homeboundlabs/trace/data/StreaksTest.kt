package com.homeboundlabs.trace.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreaksTest {
    private val today = LocalDate.of(2026, 11, 24)
    private fun days(vararg back: Int) = back.map { today.minusDays(it.toLong()) }.toSet()

    @Test fun emptyHasNoStreak() {
        val s = Streaks.compute(emptySet(), today)
        assertEquals(0, s.current)
        assertEquals(0, s.best)
        assertTrue(s.todayOpen)
    }

    @Test fun countsConsecutiveDaysIncludingToday() {
        val s = Streaks.compute(days(0, 1, 2, 3), today)
        assertEquals(4, s.current)
        assertFalse(s.todayOpen)
    }

    @Test fun todayNotLoggedYetDoesNotBreakIt() {
        val s = Streaks.compute(days(1, 2, 3), today)
        assertEquals(3, s.current)
        assertTrue(s.todayOpen)
    }

    @Test fun oneMissedDayIsForgiven() {
        // Logged today, 1, 3, 4: day 2 is a single gap.
        val s = Streaks.compute(days(0, 1, 3, 4), today)
        assertEquals(4, s.current)
        assertEquals(setOf(today.minusDays(2)), s.forgiven)
    }

    @Test fun twoMissedDaysInARowEndIt() {
        val s = Streaks.compute(days(0, 1, 4, 5), today)
        assertEquals(2, s.current)
    }

    @Test fun onlyOneForgivenessPerWeek() {
        // Gaps at 2 and 4, three days apart: the second can't be forgiven.
        val s = Streaks.compute(days(0, 1, 3, 5, 6), today)
        assertEquals(3, s.current)
    }

    @Test fun forgivenessComesBackAfterAWeek() {
        // Gaps at 2 and 10, eight days apart: both forgiven.
        val logged = days(0, 1, 3, 4, 5, 6, 7, 8, 9, 11, 12)
        assertEquals(11, Streaks.compute(logged, today).current)
    }

    @Test fun missedYesterdayWithTodayOpenIsForgiven() {
        val s = Streaks.compute(days(2, 3, 4), today)
        assertEquals(3, s.current)
        assertTrue(s.todayOpen)
    }

    @Test fun bestRemembersAnOlderLongerRun() {
        val logged = days(0, 1) + (20..29).map { today.minusDays(it.toLong()) }
        val s = Streaks.compute(logged, today)
        assertEquals(2, s.current)
        assertEquals(10, s.best)
    }

    @Test fun milestonesStepUp() {
        assertEquals(null, Milestones.reached(6))
        assertEquals(7, Milestones.reached(7))
        assertEquals(30, Milestones.reached(99))
        assertEquals(100, Milestones.next(30))
        assertEquals(null, Milestones.next(400))
    }

    @Test fun calendarMarksEachKindOfDay() {
        val month = java.time.YearMonth.of(2026, 11)
        val spent = setOf(LocalDate.of(2026, 11, 20))
        val marks = setOf(LocalDate.of(2026, 11, 21))
        val forgiven = setOf(LocalDate.of(2026, 11, 22))
        val days = Streaks.month(month, spent, marks, forgiven, today, LocalDate.of(2026, 11, 18)).toMap()
        assertEquals(DayKind.Blank, days[LocalDate.of(2026, 11, 17)])
        assertEquals(DayKind.Missed, days[LocalDate.of(2026, 11, 19)])
        assertEquals(DayKind.Logged, days[LocalDate.of(2026, 11, 20)])
        assertEquals(DayKind.CheckedIn, days[LocalDate.of(2026, 11, 21)])
        assertEquals(DayKind.Forgiven, days[LocalDate.of(2026, 11, 22)])
        assertEquals(DayKind.Blank, days[today])
        assertEquals(DayKind.Blank, days[LocalDate.of(2026, 11, 30)])
    }
}
