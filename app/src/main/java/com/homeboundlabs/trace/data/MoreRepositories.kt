package com.homeboundlabs.trace.data

import android.content.Context
import com.homeboundlabs.trace.reminders.DueReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Lend/borrow ledger. Writes outlive the screen that asked for them, like expense saves. */
class LedgerRepository(private val context: Context, private val dao: LedgerDao, private val scope: CoroutineScope) {
    fun observeBalances() = dao.observeBalances()
    fun observePerson(id: Long) = dao.observePerson(id)
    fun observeEntries(personId: Long) = dao.observeEntries(personId)

    suspend fun addPerson(name: String): Long = dao.insertPerson(Person(name = name.trim(), createdAt = System.currentTimeMillis()))

    fun rename(person: Person, name: String) = scope.launch { dao.updatePerson(person.copy(name = name.trim())) }

    fun deletePerson(id: Long) = scope.launch {
        dao.deletePerson(id)
        DueReminders.schedule(context)
    }

    fun add(personId: Long, amountMinor: Long, gave: Boolean, note: String, createdAt: Long, dueAt: Long?) = scope.launch {
        dao.insertAndTidy(LedgerEntry(personId = personId, amountMinor = amountMinor, gave = gave, note = note.trim(), createdAt = createdAt, dueAt = dueAt))
        DueReminders.schedule(context)
    }

    fun update(entry: LedgerEntry) = scope.launch {
        dao.updateEntry(entry)
        DueReminders.schedule(context)
    }

    fun deleteEntry(id: Long) = scope.launch {
        dao.deleteEntry(id)
        DueReminders.schedule(context)
    }

    /** Records whatever brings the balance back to zero, so the history stays and the person reads "settled". */
    fun settle(personId: Long, note: String = "Settled up") = scope.launch {
        val balance = dao.entries(personId).balance()
        if (balance != 0L) {
            dao.insertAndTidy(
                LedgerEntry(personId = personId, amountMinor = kotlin.math.abs(balance), gave = balance < 0, note = note, createdAt = System.currentTimeMillis()),
            )
        }
        DueReminders.schedule(context)
    }

    suspend fun person(id: Long) = dao.person(id)
    suspend fun entries(personId: Long) = dao.entries(personId)
    suspend fun withDueDates() = dao.withDueDates()
}

/** "Nothing spent today" marks. */
class HabitRepository(private val dao: HabitDao, private val scope: CoroutineScope) {
    fun observeNoSpendDays() = dao.observeNoSpendDays()

    suspend fun isCheckedIn(day: LocalDate) = dao.isMarked(day.toEpochDay())

    fun setNoSpend(day: LocalDate, on: Boolean) = scope.launch {
        if (on) dao.mark(NoSpendDay(day.toEpochDay())) else dao.unmark(day.toEpochDay())
    }

    companion object {
        fun dayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
            java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    }
}

class GoalRepository(private val dao: GoalDao, private val scope: CoroutineScope, private val expenses: Repository) {
    fun observe() = dao.observe()

    fun add(name: String, targetMinor: Long, argb: Long, icon: String) = scope.launch {
        dao.insert(Goal(name = name.trim(), targetMinor = targetMinor, argb = argb, icon = icon, createdAt = System.currentTimeMillis()))
    }

    fun edit(goal: Goal, name: String, targetMinor: Long, argb: Long, icon: String) = scope.launch {
        dao.update(stamp(goal.copy(name = name.trim(), targetMinor = targetMinor, argb = argb, icon = icon)))
    }

    /** Adds (or with a negative amount, takes out) money; the saved total never drops below zero. */
    fun move(goalId: Long, deltaMinor: Long) = scope.launch {
        val g = dao.get(goalId) ?: return@launch
        dao.update(stamp(g.copy(savedMinor = (g.savedMinor + deltaMinor).coerceAtLeast(0))))
    }

    fun delete(id: Long) = scope.launch { dao.delete(id) }

    /**
     * "I bought it": the goal moves to Completed. When [logAs] is given, the purchase is also logged
     * as a normal expense of [amountMinor], because buying the thing is real spending.
     */
    fun finish(goal: Goal, logAs: Category?, amountMinor: Long) = scope.launch {
        dao.update(goal.copy(doneAt = System.currentTimeMillis()))
        if (logAs != null && amountMinor > 0) expenses.addInBackground(amountMinor, logAs, goal.name, System.currentTimeMillis())
    }

    /** Brings a completed goal back to the active list. */
    fun reopen(goal: Goal) = scope.launch { dao.update(goal.copy(doneAt = null)) }

    private fun stamp(g: Goal): Goal = when {
        g.reached && g.reachedAt == null -> g.copy(reachedAt = System.currentTimeMillis())
        !g.reached && g.reachedAt != null -> g.copy(reachedAt = null)
        else -> g
    }
}
