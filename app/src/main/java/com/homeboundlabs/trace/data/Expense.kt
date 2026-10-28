package com.homeboundlabs.trace.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Amounts are stored in minor units (paise / cents) to avoid floating-point drift. */
@Entity(tableName = "expenses", indices = [Index("createdAt")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val categoryId: String,
    val note: String,
    val createdAt: Long,
) {
    val category: Category get() = Category.of(categoryId)
    val title: String get() = note.ifBlank { category.label }
}

/** A note and the category it was last saved under. */
data class NoteCategory(val note: String, val categoryId: String)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE createdAt >= :since ORDER BY createdAt DESC")
    fun observeSince(since: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM expenses WHERE createdAt >= :start AND createdAt < :end")
    suspend fun sumBetween(start: Long, end: Long): Long

    @Query("SELECT * FROM expenses ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): Expense?

    /** Notes you've used with the category you last filed them under, most recent first. Helps voice learn your words. */
    @Query(
        "SELECT e.note AS note, e.categoryId AS categoryId FROM expenses e " +
            "JOIN (SELECT note, MAX(createdAt) AS latest FROM expenses WHERE note != '' GROUP BY note) l " +
            "ON e.note = l.note AND e.createdAt = l.latest ORDER BY l.latest DESC LIMIT :limit",
    )
    suspend fun recentNotes(limit: Int): List<NoteCategory>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun count(): Int

    @Insert
    suspend fun insert(expense: Expense): Long

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: Long): Expense?

    @Update
    suspend fun update(expense: Expense)

    /** Old debug sample rows were stamped at exact minutes; anything the user adds carries real seconds/millis. */
    @Query("DELETE FROM expenses WHERE createdAt % 60000 = 0")
    suspend fun deleteSampleRows(): Int

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [Expense::class, Category::class], version = 4, exportSchema = true)
abstract class TraceDatabase : RoomDatabase() {
    abstract fun expenses(): ExpenseDao
    abstract fun categories(): CategoryDao
}

/** Built-in categories as SQL inserts, used by the v4 migration and by fresh installs. */
internal fun insertDefaultCategories(db: SupportSQLiteDatabase) {
    Category.defaults.forEach { c ->
        db.execSQL(
            "INSERT OR IGNORE INTO `categories` (`id`, `name`, `argb`, `icon`, `position`) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any>(c.id, c.name, c.argb, c.icon, c.position),
        )
    }
}

/** Fresh installs start with the built-ins (existing installs get them from [MIGRATION_3_4]). */
val SeedCategories = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = insertDefaultCategories(db)
}

/** v1 → v2 added a detected-payments table (UPI auto-log, since removed). Expenses untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`amountMinor` INTEGER NOT NULL, `payee` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "`detectedAt` INTEGER NOT NULL, `status` TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_payments_detectedAt` ON `pending_payments` (`detectedAt`)")
    }
}

/** v2 → v3 removes that table again; expenses untouched. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `pending_payments`")
    }
}

/** v3 → v4 stores categories in the database (built-ins plus a new "Fun"), so people can add their own. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`argb` INTEGER NOT NULL, `icon` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        insertDefaultCategories(db)
    }
}
