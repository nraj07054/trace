package com.homeboundlabs.trace.data

import androidx.compose.ui.graphics.Color
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.homeboundlabs.trace.ui.theme.TraceColors
import kotlinx.coroutines.flow.Flow

/**
 * Something the user is saving up for. Money put into a goal is a note to self, not spending, so it
 * never shows in totals or budgets. [savedMinor] can't go below zero.
 */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMinor: Long,
    val savedMinor: Long = 0,
    val argb: Long,
    val icon: String,
    val createdAt: Long,
    /** Set when [savedMinor] first reaches [targetMinor]. */
    val reachedAt: Long? = null,
    /** Set when the user finished the goal ("I bought it"); it then moves to Completed. */
    val doneAt: Long? = null,
) {
    val done: Boolean get() = doneAt != null
    val color: Color get() = TraceColors.category(argb)
    val fraction: Float get() = if (targetMinor <= 0) 0f else (savedMinor.toFloat() / targetMinor).coerceIn(0f, 1f)
    val reached: Boolean get() = targetMinor in 1..savedMinor
    val leftMinor: Long get() = (targetMinor - savedMinor).coerceAtLeast(0)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY (doneAt IS NOT NULL), doneAt DESC, createdAt DESC")
    fun observe(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: Long): Goal?

    @Insert
    suspend fun insert(g: Goal): Long

    @Update
    suspend fun update(g: Goal)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)
}
