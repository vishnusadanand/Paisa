package com.visa.paisa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.visa.paisa.data.Txn
import com.visa.paisa.parse.Categories
import com.visa.paisa.util.Dates
import com.visa.paisa.util.Money

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTxnSheet(
    txn: Txn,
    onDismiss: () -> Unit,
    onSave: (Txn, String, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var category by remember(txn.id) { mutableStateOf(txn.category) }
    var merchant by remember(txn.id) { mutableStateOf(txn.merchant.orEmpty()) }
    var amountText by remember(txn.id) { mutableStateOf(String.format(java.util.Locale.ROOT, "%.2f", txn.amount)) }
    var note by remember(txn.id) { mutableStateOf(txn.note.orEmpty()) }
    var excluded by remember(txn.id) { mutableStateOf(txn.excluded) }
    var rememberMerchant by remember(txn.id) { mutableStateOf(true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text("Edit transaction", style = MaterialTheme.typography.titleMedium)
            Text(
                "${Dates.dayTitle(Dates.toLocalDate(txn.timestamp))} · ${Dates.timeOfDay(txn.timestamp)}" +
                    (txn.bank?.let { " · $it" } ?: "") +
                    (txn.accountTail?.let { " · ••$it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Payee") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount") },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Text("Category", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Categories.SPENDING.forEach { c ->
                    CategoryPill(category = c, selected = c == category, onClick = { category = c })
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "Remember this payee",
                subtitle = "Use this category for ${merchant.ifBlank { "this payee" }} in future",
                checked = rememberMerchant,
                onCheckedChange = { rememberMerchant = it },
            )
            ToggleRow(
                title = "Exclude from totals",
                subtitle = "For duplicate alerts, self-transfers or anything reimbursed",
                checked = excluded,
                onCheckedChange = { excluded = it },
            )

            if (txn.rawBody.isNotBlank() && !txn.manualEntry) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                Text("Original message", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    txn.rawBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onDelete(txn.id) }) { Text("Delete") }
                Button(
                    onClick = {
                        val updated = txn.copy(
                            merchant = merchant.ifBlank { null },
                            amount = amountText.toDoubleOrNull() ?: txn.amount,
                            note = note.ifBlank { null },
                            excluded = excluded,
                        )
                        onSave(updated, category, rememberMerchant)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddManualSheet(
    onDismiss: () -> Unit,
    onAdd: (Double, String, String, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var merchant by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Categories.SPENDING.first()) }
    var note by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull() ?: 0.0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text("Add an expense", style = MaterialTheme.typography.titleMedium)
            Text(
                "For cash, or anything your bank never texted you about",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount") },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Paid to") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Text("Category", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Categories.SPENDING.forEach { c ->
                    CategoryPill(category = c, selected = c == category, onClick = { category = c })
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onAdd(amount, merchant.trim(), category, note.ifBlank { null }) },
                enabled = amount > 0 && merchant.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add ${if (amount > 0) Money.rupees(amount) else "expense"}") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
