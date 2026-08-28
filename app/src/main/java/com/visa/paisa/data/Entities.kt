package com.visa.paisa.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "txns",
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["category"]),
    ],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    /** "DEBIT" or "CREDIT". */
    val direction: String,
    val merchant: String?,
    val merchantKey: String?,
    val category: String,
    /** "UPI", "CREDIT_CARD", "ATM"… */
    val channel: String,
    val bank: String?,
    val accountTail: String?,
    val refNo: String?,
    val timestamp: Long,
    val sender: String?,
    val rawBody: String,
    /** False once you have picked or confirmed the category yourself. */
    val autoCategorized: Boolean = true,
    val userConfirmed: Boolean = false,
    /** Hidden from all totals — for duplicate alerts, self-transfers, reimbursed items. */
    val excluded: Boolean = false,
    val note: String? = null,
    val manualEntry: Boolean = false,
    val dedupeKey: String,
)

/** "Every time I pay SREE LAKSHMI STORES, call it Groceries." Learned from your overrides. */
@Entity(tableName = "merchant_rules")
data class MerchantRule(
    @PrimaryKey val merchantKey: String,
    val displayName: String,
    val category: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: String,
    val monthlyCap: Double,
)

/** Projection rows for the analysis screen. */
data class CategoryTotal(val category: String, val total: Double, val count: Int)

data class DayTotal(val day: String, val total: Double)

data class MonthTotal(val month: String, val total: Double)

data class MerchantTotal(val merchant: String, val total: Double, val count: Int)
