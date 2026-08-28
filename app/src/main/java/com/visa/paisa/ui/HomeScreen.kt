package com.visa.paisa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visa.paisa.data.Txn
import com.visa.paisa.util.Dates
import com.visa.paisa.util.Money
import java.time.LocalDate

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenTxn: (Txn) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val today by vm.todaySpend.collectAsStateWithLifecycle()
    val monthSpend by vm.monthSpend.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val reviewCount by vm.reviewCount.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()

    val grouped: List<Pair<LocalDate, List<Txn>>> = recent
        .groupBy { Dates.toLocalDate(it.timestamp) }
        .toList()
        .sortedByDescending { it.first }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionCard {
                Text(
                    "Spent today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    Money.rupees(today),
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            Dates.monthTitle(month),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            Money.rupees(monthSpend),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (reviewCount > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Needs a category",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "$reviewCount",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        if (grouped.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing tracked yet",
                    subtitle = "Grant SMS access in Settings and tap Rescan inbox — your existing bank alerts will be read and sorted in a few seconds.",
                )
            }
        }

        grouped.forEach { (date, dayTxns) ->
            item(key = "header-$date") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        Dates.dayTitle(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Money.rupees(
                            dayTxns.filter { !it.excluded && it.direction == "DEBIT" }.sumOf { it.amount },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(dayTxns, key = { it.id }) { txn ->
                TxnRow(txn = txn, onClick = { onOpenTxn(txn) })
            }
        }
    }
}
