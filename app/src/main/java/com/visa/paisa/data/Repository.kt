package com.visa.paisa.data

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import com.visa.paisa.parse.Categories
import com.visa.paisa.parse.Categorizer
import com.visa.paisa.parse.Direction
import com.visa.paisa.parse.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Locale

enum class IngestOutcome {
    /** Not a transaction message at all (OTP, promo, balance alert, statement reminder). */
    IGNORED,

    /** A second alert for a purchase already recorded. */
    DUPLICATE,
    ADDED,

    /** Stored, but the category could not be worked out — it goes to the Review tab. */
    ADDED_NEEDS_REVIEW;

    val stored: Boolean get() = this == ADDED || this == ADDED_NEEDS_REVIEW
}

class Repository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val txns = db.txnDao()
    private val rules = db.ruleDao()
    private val budgets = db.budgetDao()

    private val skip get() = Categories.NON_SPEND.toList()

    // ------------------------------------------------------------------ ingestion

    /** Parse one SMS and store it if it is a real money movement. */
    suspend fun ingest(body: String, sender: String?, timestamp: Long): IngestOutcome =
        withContext(Dispatchers.IO) {
            val parsed = SmsParser.parse(body, sender) ?: return@withContext IngestOutcome.IGNORED

            val userRule = parsed.merchantKey?.let { rules.categoryFor(it) }
            val category = if (parsed.direction == Direction.CREDIT) {
                Categories.TRANSFERS
            } else {
                Categorizer.categorize(parsed, body, userRule)
            }

            val txn = Txn(
                amount = parsed.amount,
                direction = parsed.direction.name,
                merchant = parsed.merchant,
                merchantKey = parsed.merchantKey,
                category = category,
                channel = parsed.channel.name,
                bank = parsed.bank,
                accountTail = parsed.accountTail,
                refNo = parsed.refNo,
                timestamp = timestamp,
                sender = sender,
                rawBody = body,
                autoCategorized = userRule == null,
                userConfirmed = false,
                dedupeKey = dedupeKey(parsed.refNo, parsed.amount, parsed.merchantKey, timestamp),
            )
            when {
                txns.insert(txn) == -1L -> IngestOutcome.DUPLICATE
                category == Categories.UNCATEGORIZED -> IngestOutcome.ADDED_NEEDS_REVIEW
                else -> IngestOutcome.ADDED
            }
        }

    /**
     * Banks often fire two alerts for one purchase (account SMS + card SMS). When a
     * reference number is present it is the reliable identity; otherwise fall back to
     * amount + merchant inside a 5-minute window.
     */
    private fun dedupeKey(ref: String?, amount: Double, merchantKey: String?, ts: Long): String =
        if (!ref.isNullOrBlank()) {
            "ref:${ref.lowercase(Locale.ROOT)}|amt:${"%.2f".format(Locale.ROOT, amount)}"
        } else {
            "amt:${"%.2f".format(Locale.ROOT, amount)}|m:${merchantKey ?: "?"}|w:${ts / 300_000L}"
        }

    /**
     * Reads the SMS inbox and imports anything that looks like a transaction.
     * Called once after permission is granted, and from the "Rescan inbox" button.
     *
     * @param sinceMillis only look at messages newer than this (0 = everything).
     * @return number of new transactions added.
     */
    suspend fun scanInbox(sinceMillis: Long): Int = withContext(Dispatchers.IO) {
        var added = 0
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val selection = if (sinceMillis > 0) "${Telephony.Sms.DATE} > ?" else null
        val args = if (sinceMillis > 0) arrayOf(sinceMillis.toString()) else null

        val cursor: Cursor? = appContext.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            args,
            "${Telephony.Sms.DATE} DESC",
        )
        cursor?.use { c ->
            val addressIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val body = c.getString(bodyIdx) ?: continue
                val sender = c.getString(addressIdx)
                val date = c.getLong(dateIdx)
                if (ingest(body, sender, date).stored) added++
            }
        }
        added
    }

    suspend fun lastImported(): Long = txns.latestImportedTimestamp()

    suspend fun transactionCount(): Int = txns.count()

    suspend fun uncategorizedCountNow(): Int = txns.uncategorizedCountNow()

    // -------------------------------------------------------------------- editing

    /**
     * Manual override. [remember] creates a merchant rule so the same payee is never
     * asked about again — and back-fills every earlier transaction from that payee.
     */
    suspend fun setCategory(txn: Txn, category: String, remember: Boolean) =
        withContext(Dispatchers.IO) {
            txns.update(txn.copy(category = category, autoCategorized = false, userConfirmed = true))
            val key = txn.merchantKey
            if (remember && !key.isNullOrBlank()) {
                rules.upsert(
                    MerchantRule(
                        merchantKey = key,
                        displayName = txn.merchant ?: key,
                        category = category,
                    ),
                )
                txns.applyRuleRetroactively(key, category)
            }
        }

    suspend fun updateTxn(txn: Txn) = withContext(Dispatchers.IO) { txns.update(txn) }

    suspend fun setExcluded(txn: Txn, excluded: Boolean) =
        withContext(Dispatchers.IO) { txns.update(txn.copy(excluded = excluded)) }

    suspend fun deleteTxn(id: Long) = withContext(Dispatchers.IO) { txns.delete(id) }

    suspend fun addManual(
        amount: Double,
        merchant: String,
        category: String,
        timestamp: Long,
        note: String?,
    ) = withContext(Dispatchers.IO) {
        val key = SmsParser.normalizeKey(merchant)
        txns.insert(
            Txn(
                amount = amount,
                direction = Direction.DEBIT.name,
                merchant = merchant,
                merchantKey = key,
                category = category,
                channel = "MANUAL",
                bank = null,
                accountTail = null,
                refNo = null,
                timestamp = timestamp,
                sender = null,
                rawBody = note ?: "Added by hand",
                autoCategorized = false,
                userConfirmed = true,
                manualEntry = true,
                note = note,
                dedupeKey = "manual:${System.nanoTime()}",
            ),
        )
    }

    // --------------------------------------------------------------------- reads

    fun observeRange(start: Long, end: Long): Flow<List<Txn>> = txns.observeRange(start, end)
    fun observeRecent(limit: Int = 50): Flow<List<Txn>> = txns.observeRecent(limit)
    fun observeUncategorized(): Flow<List<Txn>> = txns.observeUncategorized()
    fun observeUncategorizedCount(): Flow<Int> = txns.observeUncategorizedCount()

    fun observeCategoryTotals(start: Long, end: Long): Flow<List<CategoryTotal>> =
        txns.observeCategoryTotals(start, end, skip)

    fun observeDayTotals(start: Long, end: Long): Flow<List<DayTotal>> =
        txns.observeDayTotals(start, end, skip)

    fun observeMonthTotals(limit: Int = 12): Flow<List<MonthTotal>> =
        txns.observeMonthTotals(skip, limit)

    fun observeTopMerchants(start: Long, end: Long, limit: Int = 8): Flow<List<MerchantTotal>> =
        txns.observeTopMerchants(start, end, skip, limit)

    fun observeSpend(start: Long, end: Long): Flow<Double> =
        txns.observeTotal(start, end, Direction.DEBIT.name, skip)

    fun observeBudgets(): Flow<List<Budget>> = budgets.observeAll()
    fun observeRules(): Flow<List<MerchantRule>> = rules.observeAll()

    suspend fun setBudget(category: String, cap: Double) = withContext(Dispatchers.IO) {
        if (cap <= 0.0) budgets.delete(category) else budgets.upsert(Budget(category, cap))
    }

    suspend fun deleteRule(key: String) = withContext(Dispatchers.IO) { rules.delete(key) }

    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder("date,amount,direction,merchant,category,channel,bank,account,ref,note\n")
        txns.allForExport().forEach { t ->
            sb.append(
                listOf(
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(java.util.Date(t.timestamp)),
                    "%.2f".format(Locale.ROOT, t.amount),
                    t.direction,
                    t.merchant.orEmpty(),
                    t.category,
                    t.channel,
                    t.bank.orEmpty(),
                    t.accountTail.orEmpty(),
                    t.refNo.orEmpty(),
                    t.note.orEmpty(),
                ).joinToString(",") { field -> "\"" + field.replace("\"", "\"\"") + "\"" },
            ).append('\n')
        }
        sb.toString()
    }
}
