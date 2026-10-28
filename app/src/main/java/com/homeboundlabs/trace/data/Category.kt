package com.homeboundlabs.trace.data

import androidx.compose.ui.graphics.Color
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A spending category. Built-ins and user-created ones are stored the same way. [icon] is a key into [CategoryIcons]. */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val argb: Long,
    val icon: String,
    val position: Int,
) {
    val label: String get() = name
    val color: Color get() = Color(argb)
    val colorInt: Int get() = argb.toInt()

    companion object {
        // Built-ins. Their ids match what expenses have always stored, so existing data keeps its category.
        val Food = Category("food", "Food", 0xFFFF7A45, "food", 0)
        val Travel = Category("travel", "Travel", 0xFF2F7BFF, "travel", 1)
        val Shopping = Category("shop", "Shopping", 0xFF8B5CF6, "shop", 2)
        val Bills = Category("bills", "Bills", 0xFF0F9F8F, "bills", 3)
        val Fun = Category("fun", "Fun", 0xFFEC4899, "ticket", 4)
        val defaults = listOf(Food, Travel, Shopping, Bills, Fun)

        /** Shown for an expense whose category no longer exists (shouldn't happen; deletes move expenses). */
        private val Other = Category("other", "Other", 0xFF6B7080, "tag", 99)

        /** Current categories, in display order. */
        val entries: List<Category> get() = Categories.all.value

        fun of(id: String): Category = entries.firstOrNull { it.id == id } ?: defaults.firstOrNull { it.id == id } ?: Other
    }
}

/** Live list of categories, kept in sync with the database by [Repository]. */
object Categories {
    private val _all = MutableStateFlow(Category.defaults)
    val all: StateFlow<List<Category>> = _all.asStateFlow()

    internal fun update(list: List<Category>) {
        _all.value = list.ifEmpty { Category.defaults }.sortedBy { it.position }
    }
}

/** Colours offered when creating a category. */
val CategoryColors: List<Long> = listOf(
    0xFFFF7A45, 0xFF2F7BFF, 0xFF8B5CF6, 0xFF0F9F8F, 0xFFEC4899,
    0xFFF5A524, 0xFF22A06B, 0xFFE5484D, 0xFF0EA5E9, 0xFF6B7080,
)

/** Icons offered when creating a category: 24×24 SVG path data drawn as a 2px stroke. */
val CategoryIcons: Map<String, String> = linkedMapOf(
    "food" to "M4 3v8a3 3 0 0 0 3 3v7M10 3v8M7 3v8M17 21V3c-2 1-3.5 3.5-3.5 7v4H17",
    "travel" to "M5 17h14v-5l-2-5H7l-2 5zM5 12h14M6.5 17a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0M14.5 17a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0",
    "shop" to "M5 8h14l-1 13H6zM9 8V6a3 3 0 0 1 6 0v2",
    "bills" to "M13 2L4 14h7l-1 8 9-12h-7z",
    "ticket" to "M3 7h18v3a2 2 0 0 0 0 4v3H3v-3a2 2 0 0 0 0-4zM14 7v10",
    "heart" to "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z",
    "home" to "M4 11l8-7 8 7v9H4zM10 20v-5h4v5",
    "gift" to "M4 10h16v4H4zM6 14v6h12v-6M12 10v10M12 10c-1.5-3-5-3.5-5-1s3.5 1 5 1zM12 10c1.5-3 5-3.5 5-1s-3.5 1-5 1z",
    "book" to "M5 4h9a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3zM5 17a3 3 0 0 1 3-3h9",
    "coffee" to "M5 9h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5zM16 10h1.5a2.5 2.5 0 0 1 0 5H16M8 3v3M12 3v3",
    "phone" to "M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM11 18h2",
    "tag" to "M3 12V4a1 1 0 0 1 1-1h8l9 9-9 9zM7 7.5a.5.5 0 1 0 1 0a.5.5 0 1 0 -1 0",
)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY position")
    fun observe(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: Category)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM categories")
    suspend fun nextPosition(): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :id")
    suspend fun expenseCount(id: String): Int

    @Query("UPDATE expenses SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveExpenses(from: String, to: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)

    /** Moves every expense out of [id] into [to], then removes [id], as one step. */
    @Transaction
    suspend fun deleteMoving(id: String, to: String) {
        moveExpenses(id, to)
        delete(id)
    }
}
