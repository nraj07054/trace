package com.homeboundlabs.trace.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Someone the user lends money to or borrows from. This is a personal ledger: only the user's own
 * side of the record, kept on the phone. Nothing here is spending or income, so none of it touches
 * budgets, Home totals or Insights.
 */
@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/**
 * One movement of money with a person. [gave] true: the user handed money over (a loan, or paying
 * back what they owed). False: the user received it (borrowing, or being paid back).
 * [amountMinor] is always positive.
 */
@Entity(
    tableName = "ledger",
    indices = [Index("personId"), Index("createdAt")],
    foreignKeys = [ForeignKey(entity = Person::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)],
)
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val amountMinor: Long,
    val gave: Boolean,
    val note: String,
    val createdAt: Long,
    /** When the user expects it back (or to pay it back), as epoch millis; null for no date. */
    val dueAt: Long? = null,
) {
    /** + when it moves the balance toward "they owe you". */
    val signedMinor: Long get() = if (gave) amountMinor else -amountMinor
}

/** A person with their running balance: positive means they owe the user, negative means the user owes them. */
data class PersonBalance(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val balanceMinor: Long,
    val lastAt: Long?,
    val nextDueAt: Long?,
) {
    val person: Person get() = Person(id, name, createdAt)
}

@Dao
interface LedgerDao {
    @Query(
        "SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt, " +
            "COALESCE(SUM(CASE WHEN l.gave THEN l.amountMinor ELSE -l.amountMinor END), 0) AS balanceMinor, " +
            "MAX(l.createdAt) AS lastAt, MIN(l.dueAt) AS nextDueAt " +
            "FROM people p LEFT JOIN ledger l ON l.personId = p.id GROUP BY p.id " +
            "ORDER BY COALESCE(MAX(l.createdAt), p.createdAt) DESC",
    )
    fun observeBalances(): Flow<List<PersonBalance>>

    @Query("SELECT * FROM people WHERE id = :id")
    fun observePerson(id: Long): Flow<Person?>

    @Query("SELECT * FROM ledger WHERE personId = :personId ORDER BY createdAt DESC, id DESC")
    fun observeEntries(personId: Long): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger WHERE dueAt IS NOT NULL")
    suspend fun withDueDates(): List<LedgerEntry>

    @Query("SELECT * FROM ledger WHERE personId = :personId")
    suspend fun entries(personId: Long): List<LedgerEntry>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun person(id: Long): Person?

    @Insert
    suspend fun insertPerson(p: Person): Long

    @Update
    suspend fun updatePerson(p: Person)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deletePersonRow(id: Long)

    @Query("DELETE FROM ledger WHERE personId = :personId")
    suspend fun deleteEntriesOf(personId: Long)

    /** Removes a person and their whole history (done by hand, so it never depends on SQLite's foreign-key setting). */
    @Transaction
    suspend fun deletePerson(id: Long) {
        deleteEntriesOf(id)
        deletePersonRow(id)
    }

    @Insert
    suspend fun insertEntry(e: LedgerEntry): Long

    @Update
    suspend fun updateEntry(e: LedgerEntry)

    @Query("DELETE FROM ledger WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    /** Once a balance is back to zero, nothing is waiting any more, so its due dates (and reminders) clear. */
    @Query("UPDATE ledger SET dueAt = NULL WHERE personId = :personId")
    suspend fun clearDueDates(personId: Long)

    @Transaction
    suspend fun insertAndTidy(e: LedgerEntry): Long {
        val id = insertEntry(e)
        if (entries(e.personId).sumOf { it.signedMinor } == 0L) clearDueDates(e.personId)
        return id
    }
}

/** Balance of a list of entries: positive means they owe the user. */
fun List<LedgerEntry>.balance(): Long = sumOf { it.signedMinor }
