package com.visa.paisa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visa.paisa.ui.charts.BudgetBar
import com.visa.paisa.ui.charts.ChartLegend
import com.visa.paisa.ui.charts.DailyBarChart
import com.visa.paisa.ui.charts.DonutChart
import com.visa.paisa.ui.charts.Slice
import com.visa.paisa.ui.charts.TrendBars
import com.visa.paisa.ui.theme.CategoryColors
import com.visa.paisa.util.Dates
import com.visa.paisa.util.Money
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AnalysisScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val month by vm.month.collectAsStateWithLifecycle()
    val totals by vm.categoryTotals.collectAsStateWithLifecycle()
    val spend by vm.monthSpend.collectAsStateWithLifecycle()
    val days by vm.dayTotals.collectAsStateWithLifecycle()
    val merchants by vm.topMerchants.collectAsStateWithLifecycle()
    val history by vm.monthHistory.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()

    val slices = totals.map { Slice(it.category, it.total, CategoryColors[it.category]) }
    val daysInMonth = month.lengthOfMonth()
    val budgetByCategory = budgets.associate { it.category to it.monthlyCap }

    // Same month last period, for the "vs last month" line.
    val historyAsc = history.sortedBy { it.month }
    val currentKey = "%04d-%02d".format(month.year, month.monthValue)
    val previousKey = month.minusMonths(1).let { "%04d-%02d".format(it.year, it.monthValue) }
    val previousTotal = history.firstOrNull { it.month == previousKey }?.total

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MonthSwitcher(
                month = month,
                onPrevious = vm::previousMonth,
                onNext = vm::nextMonth,
            )
        }

        item {
            SectionCard {
                DonutChart(
                    slices = slices,
                    centerLabel = "Total spent",
                    centerValue = Money.rupees(spend),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))
                if (previousTotal != null && previousTotal > 0) {
                    val delta = spend - previousTotal
                    val pct = (abs(delta) / previousTotal * 100).roundToInt()
                    Text(
                        text = if (delta >= 0) "↑ $pct% more than ${Dates.monthTitle(month.minusMonths(1))}"
                        else "↓ $pct% less than ${Dates.monthTitle(month.minusMonths(1))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (slices.isEmpty()) {
                    Text(
                        "No spending recorded for this month yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ChartLegend(slices = slices)
                }
            }
        }

        if (slices.isNotEmpty()) {
            item {
                SectionCard {
                    SectionHeader(
                        "Day by day",
                        trailing = "avg ${Money.rupees(spend / daysInMonth)}/day",
                    )
                    Spacer(Modifier.height(14.dp))
                    DailyBarChart(
                        values = days.map { Dates.dayOfMonth(it.day) to it.total },
                        daysInMonth = daysInMonth,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$daysInMonth", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    days.maxByOrNull { it.total }?.let { peak ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Heaviest day: ${peak.day.takeLast(2).trimStart('0')} ${Dates.monthTitle(month).substringBefore(' ')} · ${Money.rupees(peak.total)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (budgetByCategory.isNotEmpty()) {
            item {
                SectionCard {
                    SectionHeader("Budgets")
                    Spacer(Modifier.height(12.dp))
                    budgetByCategory.entries.sortedBy { it.key }.forEach { (category, cap) ->
                        val spent = totals.firstOrNull { it.category == category }?.total ?: 0.0
                        Column(Modifier.padding(bottom = 14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(category, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${Money.rupees(spent)} / ${Money.rupees(cap)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (spent > cap) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            BudgetBar(spent = spent, cap = cap, color = CategoryColors[category])
                        }
                    }
                }
            }
        }

        if (historyAsc.size > 1) {
            item {
                SectionCard {
                    SectionHeader("Last ${historyAsc.size} months")
                    Spacer(Modifier.height(14.dp))
                    TrendBars(
                        values = historyAsc.map { m ->
                            YearMonth.parse(m.month).let { ym ->
                                Dates.monthTitle(ym).take(3) to m.total
                            }
                        },
                        highlightIndex = historyAsc.indexOfFirst { it.month == currentKey },
                    )
                }
            }
        }

        if (merchants.isNotEmpty()) {
            item {
                SectionCard {
                    SectionHeader("Where it went", trailing = "top ${merchants.size}")
                    Spacer(Modifier.height(10.dp))
                    merchants.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                m.merchant,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${m.count}×",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(0.dp))
                            Box(Modifier.padding(start = 12.dp)) {
                                Text(
                                    Money.rupees(m.total),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val atCurrentMonth = !month.isBefore(YearMonth.now(Dates.zone))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(Dates.monthTitle(month), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext, enabled = !atCurrentMonth) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = if (atCurrentMonth) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
