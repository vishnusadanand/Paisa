package com.visa.paisa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visa.paisa.data.Txn
import com.visa.paisa.ui.AddManualSheet
import com.visa.paisa.ui.AnalysisScreen
import com.visa.paisa.ui.EditTxnSheet
import com.visa.paisa.ui.HomeScreen
import com.visa.paisa.ui.MainViewModel
import com.visa.paisa.ui.ReviewScreen
import com.visa.paisa.ui.SettingsScreen
import com.visa.paisa.ui.theme.PaisaTheme
import com.visa.paisa.util.Dates
import java.time.YearMonth

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openReview = intent?.getBooleanExtra(EXTRA_OPEN_REVIEW, false) ?: false
        setContent {
            PaisaTheme {
                PaisaRoot(vm = vm, startOnReview = openReview)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_OPEN_REVIEW = "open_review"
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Today", Icons.Filled.Home),
    Analysis("Analysis", Icons.Filled.DonutLarge),
    Review("Review", Icons.Outlined.HelpOutline),
    Settings("Settings", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaisaRoot(vm: MainViewModel, startOnReview: Boolean) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(if (startOnReview) Tab.Review else Tab.Home) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var addingManual by remember { mutableStateOf(false) }

    var hasSms by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasSms = result[Manifest.permission.READ_SMS] == true
        // First grant: pull in everything already sitting in the inbox.
        if (hasSms) vm.scanInbox(fullRescan = true)
    }

    val requestPermissions = {
        val wanted = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }

    // Catch up on anything that arrived while the app was closed.
    LaunchedEffect(hasSms) {
        if (hasSms) vm.scanInbox(fullRescan = false)
    }

    val reviewCount by vm.reviewCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            Tab.Home -> "Paisa"
                            Tab.Analysis -> "Analysis"
                            Tab.Review -> "Needs a category"
                            Tab.Settings -> "Settings"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            if (entry == Tab.Review && reviewCount > 0) {
                                BadgedBox(badge = { Badge { Text("$reviewCount") } }) {
                                    Icon(entry.icon, contentDescription = entry.label)
                                }
                            } else {
                                Icon(entry.icon, contentDescription = entry.label)
                            }
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.Home) {
                FloatingActionButton(onClick = { addingManual = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add an expense by hand")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                Tab.Home -> HomeScreen(
                    vm = vm,
                    onOpenTxn = { editing = it },
                    contentPadding = padding,
                )
                Tab.Analysis -> AnalysisScreen(vm = vm, contentPadding = padding)
                Tab.Review -> ReviewScreen(
                    vm = vm,
                    onOpenTxn = { editing = it },
                    contentPadding = padding,
                )
                Tab.Settings -> SettingsScreen(
                    vm = vm,
                    hasSmsPermission = hasSms,
                    onRequestPermission = requestPermissions,
                    contentPadding = padding,
                )
            }
        }
    }

    editing?.let { txn ->
        EditTxnSheet(
            txn = txn,
            onDismiss = { editing = null },
            // setCategory persists the whole row, so the field edits ride along with it.
            onSave = { updated, category, rememberMerchant ->
                vm.setCategory(updated, category, rememberMerchant)
                editing = null
            },
            onDelete = { id ->
                vm.deleteTxn(id)
                editing = null
            },
        )
    }

    if (addingManual) {
        AddManualSheet(
            onDismiss = { addingManual = false },
            onAdd = { amount, merchant, category, note ->
                vm.addManual(amount, merchant, category, System.currentTimeMillis(), note)
                // Jump the month view to wherever the entry landed.
                vm.setMonth(YearMonth.now(Dates.zone))
                addingManual = false
            },
        )
    }
}
