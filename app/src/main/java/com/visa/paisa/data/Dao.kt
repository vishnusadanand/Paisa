package com.visa.paisa.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TxnDao {

    /** Ignores rather than replaces: the same alert arriving twice must not double-count. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(txn: Txn): Long

    @Update
    suspend fun update(txn: Txn)

    @Query("DELETE FROM txns WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM txns WHERE id = :id")
    suspend fun byId(id: Long): Txn?

    @Query("SELECT * FROM txns WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun observeRange(start: Long, end: Long): Flow<List<Txn>>

    @Query("SELECT * FROM txns ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Txn>>

    @Query(
        """
        SELECT * FROM txns
        WHERE category = 'Uncategorized' AND excluded = 0 AND direction = 'DEBIT'
        ORDER BY timestamp DESC
        """
    )
    fun observeUncategorized(): Flow<List<Txn>>

    @Query("SELECT COUNT(*) FROM txns WHERE category = 'Uncategorized' AND excluded = 0 AND direction = 'DEBIT'")
    fun observeUncategorizedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM txns WHERE category = 'Uncategorized' AND excluded = 0 AND direction = 'DEBIT'")
    suspend fun uncategorizedCountNow(): Int

    @Query(
        """
        SELECT category AS category, SUM(amount) AS total, COUNT(*) AS count
        FROM txns
        WHERE direction = 'DEBIT' AND excluded = 0
          AND category NOT IN (:skip)
          AND timestamp BETWEEN :start AND :end
        GROUP BY category
        ORDER BY total DESC
        """
    )
    fun observeCategoryTotals(start: Long, end: Long, skip: List<String>): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day,
               SUM(amount) AS total
        FROM txns
        WHERE direction = 'DEBIT' AND excluded = 0
          AND category NOT IN (:skip)
          AND timestamp BETWEEN :start AND :end
        GROUP BY day
        ORDER BY day ASC
        """
    )
    fun observeDayTotals(start: Long, end: Long, skip: List<String>): Flow<List<DayTotal>>

    @Query(
        """
        SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS month,
               SUM(amount) AS total
        FROM txns
        WHERE direction = 'DEBIT' AND excluded = 0
          AND category NOT IN (:skip)
        GROUP BY month
        ORDER BY month DESC
        LIMIT :limit
        """
    )
    fun observeMonthTotals(skip: List<String>, limit: Int): Flow<List<MonthTotal>>

    @Query(
        """
        SELECT COALESCE(merchant, 'Unknown') AS merchant, SUM(amount) AS total, COUNT(*) AS count
        FROM txns
        WHERE direction = 'DEBIT' AND excluded = 0
          AND category NOT IN (:skip)
          AND timestamp BETWEEN :start AND :end
        GROUP BY merchantKey
        ORDER BY total DESC
        LIMIT :limit
        """
    )
    fun observeTopMerchants(start: Long, end: Long, skip: List<String>, limit: Int): Flow<List<MerchantTotal>>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM txns
        WHERE direction = :direction AND excluded = 0
          AND category NOT IN (:skip)
          AND timestamp BETWEEN :start AND :end
        """
    )
    fun observeTotal(start: Long, end: Long, direction: String, skip: List<String>): Flow<Double>

    /** Used to skip re-parsing the inbox from the beginning of time on every scan. */
    @Query("SELECT COALESCE(MAX(timestamp), 0) FROM txns WHERE manualEntry = 0")
    suspend fun latestImportedTimestamp(): Long

    @Query("SELECT COUNT(*) FROM txns")
    suspend fun count(): Int

    /** Applies a newly created merchant rule retroactively to past transactions. */
    @Query(
        """
        UPDATE txns SET category = :category, autoCategorized = 0
        WHERE merchantKey = :merchantKey AND userConfirmed = 0
        """
    )
    suspend fun applyRuleRetroactively(merchantKey: String, category: String)

    @Query("SELECT * FROM txns ORDER BY timestamp DESC")
    suspend fun allForExport(): List<Txn>
}

@Dao
interface RuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRule)

    @Query("SELECT category FROM merchant_rules WHERE merchantKey = :key")
    suspend fun categoryFor(key: String): String?

    @Query("SELECT * FROM merchant_rules ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MerchantRule>>

    @Query("DELETE FROM merchant_rules WHERE merchantKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: Budget)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun delete(category: String)

    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<Budget>>
}
