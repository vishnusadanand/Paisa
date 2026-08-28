package com.visa.paisa.parse

/**
 * The spending buckets. "Uncategorized" is deliberately a real category: anything the
 * engine is not confident about lands there and shows up in the Review tab, rather than
 * being quietly guessed into the wrong bucket.
 */
object Categories {

    const val UNCATEGORIZED = "Uncategorized"
    const val TRANSFERS = "Transfers"
    const val CASH_ATM = "Cash & ATM"

    /** Order here is the order shown in pickers and legends. */
    val SPENDING = listOf(
        "Food & Dining",
        "Groceries",
        "Transport",
        "Bills & Utilities",
        "Entertainment",
        "Shopping",
        "Subscriptions",
        "Health",
        "Education",
        "Travel",
        CASH_ATM,
        TRANSFERS,
        "Investments",
        "Other",
    )

    val ALL: List<String> = SPENDING + UNCATEGORIZED

    /**
     * Categories excluded from "what did I spend" totals by default. Moving money between
     * your own accounts or into an investment is not consumption, and counting it wrecks
     * the monthly picture.
     */
    val NON_SPEND = setOf(TRANSFERS, "Investments")

    fun isKnown(name: String): Boolean = ALL.contains(name)
}
