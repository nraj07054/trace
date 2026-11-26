package com.homeboundlabs.trace.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

/**
 * A day the user checked in with "nothing to log" (from the evening notification). It counts for the
 * streak just like logging an expense, so an honest empty day doesn't break it. It's never celebrated
 * or counted on its own: the streak rewards keeping the record, not spending less.
 */
@Entity(tableName = "no_spend_days")
data class NoSpendDay(@PrimaryKey val day: Long)

@Dao
interface HabitDao {
    @Query("SELECT day FROM no_spend_days")
    fun observeNoSpendDays(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun mark(d: NoSpendDay)

    @Query("SELECT EXISTS(SELECT 1 FROM no_spend_days WHERE day = :day)")
    suspend fun isMarked(day: Long): Boolean

    @Query("DELETE FROM no_spend_days WHERE day = :day")
    suspend fun unmark(day: Long)
}

/** How a day looks on the habit calendar. */
enum class DayKind {
    /** At least one expense logged. */
    Logged,
    /** Checked in with "nothing to log", and no expenses. Shown like a logged day. */
    CheckedIn,
    /** Nothing logged, but forgiven so the streak carried on. */
    Forgiven,
    /** Nothing logged. */
    Missed,
    /** Before the first thing was ever logged, or still to come. */
    Blank,
}

data class StreakInfo(
    /** Days in the streak (a forgiven day isn't counted, it only bridges the gap). */
    val current: Int,
    val best: Int,
    /** True when today has nothing logged yet, so the streak needs today's entry to keep going. */
    val todayOpen: Boolean,
    /** Days that were forgiven inside the current streak. */
    val forgiven: Set<LocalDate>,
)

/**
 * Streak rules, kept free of Android so they can be unit tested.
 *
 * A day counts when anything was logged: an expense, or a "nothing spent" mark. Today never breaks
 * a streak (the day isn't over). One missed day is forgiven at most once every 7 days, as long as the
 * day before it was logged, so a single forgotten evening doesn't wipe out weeks of habit.
 */
object Streaks {
    const val FORGIVE_EVERY_DAYS = 7L

    fun compute(logged: Set<LocalDate>, today: LocalDate): StreakInfo {
        val todayOpen = today !in logged
        var day = if (todayOpen) today.minusDays(1) else today
        var count = 0
        var lastForgiven: LocalDate? = null
        val forgiven = HashSet<LocalDate>()
        while (true) {
            if (day in logged) {
                count++
            } else {
                // A gap inside the streak, or yesterday while today is still open, bridged by a logged day before it.
                val bridges = count > 0 || (todayOpen && day == today.minusDays(1))
                val window = lastForgiven == null || lastForgiven.toEpochDay() - day.toEpochDay() >= FORGIVE_EVERY_DAYS
                if (bridges && window && day.minusDays(1) in logged) {
                    lastForgiven = day
                    forgiven += day
                } else {
                    break
                }
            }
            day = day.minusDays(1)
        }
        return StreakInfo(current = count, best = maxOf(best(logged), count), todayOpen = todayOpen, forgiven = if (count > 0) forgiven else emptySet())
    }

    /** Longest streak ever, with the same forgiving rule. */
    fun best(logged: Set<LocalDate>): Int {
        if (logged.isEmpty()) return 0
        val first = logged.min()
        val last = logged.max()
        var day = first
        var run = 0
        var best = 0
        var lastForgiven: LocalDate? = null
        while (!day.isAfter(last)) {
            if (day in logged) {
                run++
                best = maxOf(best, run)
            } else {
                val window = lastForgiven == null || day.toEpochDay() - lastForgiven.toEpochDay() >= FORGIVE_EVERY_DAYS
                if (run > 0 && window && day.plusDays(1) in logged) lastForgiven = day else run = 0
            }
            day = day.plusDays(1)
        }
        return best
    }

    /** Each day of [month] on the calendar. */
    fun month(
        month: YearMonth,
        spentDays: Set<LocalDate>,
        noSpendMarks: Set<LocalDate>,
        forgiven: Set<LocalDate>,
        today: LocalDate,
        trackedFrom: LocalDate?,
    ): List<Pair<LocalDate, DayKind>> = (1..month.lengthOfMonth()).map { d ->
        val date = month.atDay(d)
        date to when {
            date in spentDays -> DayKind.Logged
            date in noSpendMarks -> DayKind.CheckedIn
            date.isAfter(today) || date == today -> DayKind.Blank
            trackedFrom == null || date.isBefore(trackedFrom) -> DayKind.Blank
            date in forgiven -> DayKind.Forgiven
            else -> DayKind.Missed
        }
    }
}

/** Logging-streak milestones, celebrated once each, to keep people coming back while the habit forms. */
object Milestones {
    val days = listOf(7, 30, 100, 365)

    /** The biggest milestone [streak] has reached, if any. */
    fun reached(streak: Int): Int? = days.lastOrNull { it <= streak }

    /** The next one to aim for, or null past the last. */
    fun next(streak: Int): Int? = days.firstOrNull { it > streak }

    fun message(m: Int): String = when (m) {
        7 -> "A full week of keeping every log. Nice start!"
        30 -> "30 days of keeping every log. That's a clean record."
        100 -> "100 days in a row. Your record is spotless."
        else -> "A whole year of logging every day. Legendary."
    }
}
