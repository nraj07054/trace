package com.trace.app.data

import android.content.Context
import com.trace.app.quickadd.QuickAdd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class Repository(
    private val context: Context,
    private val dao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val scope: CoroutineScope,
) {
    init {
        // Keep the app-wide category list in step with the database.
        scope.launch { categoryDao.observe().collect { Categories.update(it) } }
    }

    fun observeSince(since: Long) = dao.observeSince(since)

    /* ---- categories ---- */

    /** Creates a category and returns it (so the caller can select it right away). */
    suspend fun addCategory(name: String, argb: Long, icon: String): Category {
        val c = Category(
            id = "c_" + java.util.UUID.randomUUID().toString().take(8),
            name = name.trim(), argb = argb, icon = icon, position = categoryDao.nextPosition(),
        )
        categoryDao.upsert(c)
        return c
    }

    suspend fun updateCategory(c: Category) = categoryDao.upsert(c.copy(name = c.name.trim()))

    suspend fun expenseCount(categoryId: String) = categoryDao.expenseCount(categoryId)

    /** Deletes [id], moving its expenses into [moveTo] so totals don't change. */
    suspend fun deleteCategory(id: String, moveTo: String) {
        categoryDao.deleteMoving(id, moveTo)
        val prefs = (context.applicationContext as com.trace.app.TraceApp).prefs
        if (prefs.quickCategory.id == id) prefs.quickCategory = Category.of(moveTo)
    }
    fun observeAll() = dao.observeAll()

    suspend fun add(amountMinor: Long, category: Category, note: String, createdAt: Long): Expense {
        val e = Expense(amountMinor = amountMinor, categoryId = category.id, note = note.trim(), createdAt = createdAt)
        val saved = e.copy(id = dao.insert(e))
        return saved
    }

    /** Saves from the UI; outlives the screen so navigating away can't cancel it. */
    fun addInBackground(amountMinor: Long, category: Category, note: String, createdAt: Long) {
        scope.launch {
            add(amountMinor, category, note, createdAt)
            QuickAdd.refresh(context)
        }
    }

    suspend fun get(id: Long) = dao.get(id)

    fun updateInBackground(expense: Expense) {
        scope.launch {
            dao.update(expense)
            QuickAdd.refresh(context)
        }
    }

    suspend fun deleteSampleRows() = dao.deleteSampleRows()


    fun delete(id: Long) {
        scope.launch {
            dao.delete(id)
            QuickAdd.refresh(context)
        }
    }

    suspend fun todayTotal(): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return dao.sumBetween(
            today.atStartOfDay(zone).toInstant().toEpochMilli(),
            today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    suspend fun latest() = dao.latest()
}
