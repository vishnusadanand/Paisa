package com.visa.paisa.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Dates {

    val zone: ZoneId get() = ZoneId.systemDefault()

    fun monthRange(month: YearMonth): LongRange {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start..end
    }

    fun dayRange(date: LocalDate): LongRange {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start..end
    }

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private val monthLabel = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val dayLabel = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val timeLabel = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun monthTitle(month: YearMonth): String = month.format(monthLabel)

    fun dayTitle(date: LocalDate): String = when (date) {
        LocalDate.now(zone) -> "Today"
        LocalDate.now(zone).minusDays(1) -> "Yesterday"
        else -> date.format(dayLabel)
    }

    fun timeOfDay(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(timeLabel)

    /** "2026-08-14" -> 14, for the daily bar chart axis. */
    fun dayOfMonth(isoDay: String): Int = isoDay.takeLast(2).toIntOrNull() ?: 1
}

object Money {

    /**
     * Indian digit grouping: 12,34,567 rather than 1,234,567. Formatting this by hand is
     * shorter than dragging in a locale-dependent NumberFormat that varies by device.
     */
    fun group(value: Long): String {
        val negative = value < 0
        val digits = abs(value).toString()
        val grouped = if (digits.length <= 3) {
            digits
        } else {
            val last3 = digits.takeLast(3)
            val rest = digits.dropLast(3)
            val chunks = mutableListOf<String>()
            var remaining = rest
            while (remaining.length > 2) {
                chunks.add(0, remaining.takeLast(2))
                remaining = remaining.dropLast(2)
            }
            if (remaining.isNotEmpty()) chunks.add(0, remaining)
            chunks.joinToString(",") + "," + last3
        }
        return if (negative) "-$grouped" else grouped
    }

    /** Rounded rupees — paise are noise in a spending summary. */
    fun rupees(amount: Double): String = "₹" + group(amount.roundToLong())

    /** Exact, for the detail view and edit fields. */
    fun exact(amount: Double): String {
        val totalPaise = (amount * 100).roundToLong()
        return "₹" + group(totalPaise / 100) + String.format(Locale.ROOT, ".%02d", (totalPaise % 100).toInt())
    }

    fun compact(amount: Double): String = when {
        amount >= 10_000_000 -> String.format(Locale.ROOT, "₹%.1fCr", amount / 10_000_000)
        amount >= 100_000 -> String.format(Locale.ROOT, "₹%.1fL", amount / 100_000)
        amount >= 1_000 -> String.format(Locale.ROOT, "₹%.1fK", amount / 1_000)
        else -> "₹" + amount.roundToLong()
    }
}
