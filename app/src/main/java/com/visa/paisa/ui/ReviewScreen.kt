package com.visa.paisa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visa.paisa.data.Txn
import com.visa.paisa.parse.Categories
import com.visa.paisa.util.Dates
import com.visa.paisa.util.Money

/**
 * The "ask me" half of the deal. Anything the engine could not place lands here with the
 * original SMS visible, so the decision takes one tap and no guesswork.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    vm: MainViewModel,
    onOpenTxn: (Txn) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val pending by vm.needsReview.collectAsStateWithLifecycle()
    var rememberChoice by rememberSaveable { mutableStateOf(true) }

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
        if (pending.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing to review",
                    subtitle = "Every transaction has a category. New ones that can't be placed automatically will appear here.",
                )
            }
            return@LazyColumn
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                Column {
                    Text("Remember my choice", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Apply the same category to this payee from now on, and to past ones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(pending, key = { it.id }) { txn ->
            ReviewCard(
                txn = txn,
                onPick = { category -> vm.setCategory(txn, category, rememberChoice) },
                onSkip = { vm.setExcluded(txn, true) },
                onOpen = { onOpenTxn(txn) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(
    txn: Txn,
    onPick: (String) -> Unit,
    onSkip: () -> Unit,
    onOpen: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    txn.merchant ?: "Unknown payee",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${Dates.dayTitle(Dates.toLocalDate(txn.timestamp))} · ${Dates.timeOfDay(txn.timestamp)}" +
                        (txn.bank?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                Money.rupees(txn.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            txn.rawBody.take(180),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Categories.SPENDING.forEach { category ->
                CategoryPill(category = category, onClick = { onPick(category) })
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onOpen) { Text("Edit details") }
            TextButton(onClick = onSkip) { Text("Not an expense") }
        }
    }
}
