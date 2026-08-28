package com.visa.paisa.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visa.paisa.parse.Categories
import com.visa.paisa.util.Money
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    hasSmsPermission: Boolean,
    onRequestPermission: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanStatus by vm.scanStatus.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    var editingBudget by remember { mutableStateOf<String?>(null) }
    var budgetInput by remember { mutableStateOf("") }

    LaunchedEffect(scanStatus) {
        if (scanStatus is ScanStatus.Done || scanStatus is ScanStatus.Failed) {
            kotlinx.coroutines.delay(6000)
            vm.clearScanStatus()
        }
    }

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
            SectionCard {
                SectionHeader("Message access")
                Spacer(Modifier.height(6.dp))
                Text(
                    if (hasSmsPermission) {
                        "Granted. New bank and card alerts are read the moment they arrive, and nothing leaves this phone."
                    } else {
                        "Paisa needs permission to read SMS. Without it, nothing can be tracked automatically — you can still add expenses by hand."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (!hasSmsPermission) {
                    Button(onClick = onRequestPermission) { Text("Grant SMS access") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { vm.scanInbox(fullRescan = false) },
                            enabled = scanStatus !is ScanStatus.Running,
                        ) { Text("Scan for new") }
                        OutlinedButton(
                            onClick = { vm.scanInbox(fullRescan = true) },
                            enabled = scanStatus !is ScanStatus.Running,
                        ) { Text("Full rescan") }
                    }
                }
                when (val status = scanStatus) {
                    is ScanStatus.Running -> StatusLine("Reading your inbox…")
                    is ScanStatus.Done -> StatusLine(
                        if (status.added == 0) "No new transactions found."
                        else "Added ${status.added} transaction${if (status.added == 1) "" else "s"}.",
                    )
                    is ScanStatus.Failed -> StatusLine(status.message, isError = true)
                    ScanStatus.Idle -> Unit
                }
            }
        }

        item {
            SectionCard {
                SectionHeader("Monthly budgets")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Set a cap and the Analysis tab shows how much of it is left.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Categories.SPENDING.forEach { category ->
                    val cap = budgets.firstOrNull { it.category == category }?.monthlyCap
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(category, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (editingBudget == category) {
                            OutlinedTextField(
                                value = budgetInput,
                                onValueChange = { budgetInput = it.filter { c -> c.isDigit() } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(120.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            TextButton(onClick = {
                                vm.setBudget(category, budgetInput.toDoubleOrNull() ?: 0.0)
                                editingBudget = null
                            }) { Text("Set") }
                        } else {
                            TextButton(onClick = {
                                editingBudget = category
                                budgetInput = cap?.toInt()?.toString().orEmpty()
                            }) {
                                Text(if (cap != null) Money.rupees(cap) else "Set cap")
                            }
                        }
                    }
                }
            }
        }

        if (rules.isNotEmpty()) {
            item {
                SectionCard {
                    SectionHeader("Learned payees", trailing = "${rules.size}")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Categories you pinned by hand. Remove one to let the engine guess again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    rules.forEach { rule ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    rule.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    rule.category,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { vm.deleteRule(rule.merchantKey) }) { Text("Remove") }
                        }
                    }
                }
            }
        }

        item {
            SectionCard {
                SectionHeader("Your data")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Everything lives in a database on this phone. No account, no server, no backup — " +
                        "which also means an uninstall takes it with it. Export now and then.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        val csv = vm.exportCsv()
                        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                        val file = File(dir, "paisa-export.csv")
                        file.writeText(csv)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Export transactions"))
                    }
                }) { Text("Export as CSV") }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, isError: Boolean = false) {
    Spacer(Modifier.height(10.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}
