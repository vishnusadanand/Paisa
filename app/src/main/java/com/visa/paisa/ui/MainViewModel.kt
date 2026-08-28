package com.visa.paisa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.visa.paisa.PaisaApp
import com.visa.paisa.data.Budget
import com.visa.paisa.data.CategoryTotal
import com.visa.paisa.data.DayTotal
import com.visa.paisa.data.MerchantTotal
import com.visa.paisa.data.MerchantRule
import com.visa.paisa.data.MonthTotal
import com.visa.paisa.data.Txn
import com.visa.paisa.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PaisaApp.repo(app)

    private val _month = MutableStateFlow(YearMonth.now(Dates.zone))
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    private val monthRange = _month

    // ---------------------------------------------------------------- today

    val todaySpend: StateFlow<Double> = repo
        .observeSpend(Dates.dayRange(LocalDate.now(Dates.zone)).first, Dates.dayRange(LocalDate.now(Dates.zone)).last)
        .stateInVm(0.0)

    val recent: StateFlow<List<Txn>> = repo.observeRecent(120).stateInVm(emptyList())

    // ------------------------------------------------------------- selected month

    val monthSpend: StateFlow<Double> = monthRange
        .flatMapLatest { m -> Dates.monthRange(m).let { repo.observeSpend(it.first, it.last) } }
        .stateInVm(0.0)

    val categoryTotals: StateFlow<List<CategoryTotal>> = monthRange
        .flatMapLatest { m -> Dates.monthRange(m).let { repo.observeCategoryTotals(it.first, it.last) } }
        .stateInVm(emptyList())

    val dayTotals: StateFlow<List<DayTotal>> = monthRange
        .flatMapLatest { m -> Dates.monthRange(m).let { repo.observeDayTotals(it.first, it.last) } }
        .stateInVm(emptyList())

    val topMerchants: StateFlow<List<MerchantTotal>> = monthRange
        .flatMapLatest { m -> Dates.monthRange(m).let { repo.observeTopMerchants(it.first, it.last) } }
        .stateInVm(emptyList())

    val monthTransactions: StateFlow<List<Txn>> = monthRange
        .flatMapLatest { m -> Dates.monthRange(m).let { repo.observeRange(it.first, it.last) } }
        .stateInVm(emptyList())

    val monthHistory: StateFlow<List<MonthTotal>> = repo.observeMonthTotals(6).stateInVm(emptyList())

    // ---------------------------------------------------------------- review & config

    val needsReview: StateFlow<List<Txn>> = repo.observeUncategorized().stateInVm(emptyList())
    val reviewCount: StateFlow<Int> = repo.observeUncategorizedCount().stateInVm(0)
    val budgets: StateFlow<List<Budget>> = repo.observeBudgets().stateInVm(emptyList())
    val rules: StateFlow<List<MerchantRule>> = repo.observeRules().stateInVm(emptyList())

    // ------------------------------------------------------------------- actions

    fun previousMonth() { _month.value = _month.value.minusMonths(1) }

    fun nextMonth() {
        val next = _month.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now(Dates.zone))) _month.value = next
    }

    fun setMonth(m: YearMonth) { _month.value = m }

    /** Full import on first run; incremental afterwards. */
    fun scanInbox(fullRescan: Boolean = false) {
        if (_scanStatus.value is ScanStatus.Running) return
        viewModelScope.launch {
            _scanStatus.value = ScanStatus.Running
            _scanStatus.value = try {
                val since = if (fullRescan) 0L else repo.lastImported()
                ScanStatus.Done(repo.scanInbox(since))
            } catch (e: SecurityException) {
                ScanStatus.Failed("SMS permission is not granted")
            } catch (e: Exception) {
                ScanStatus.Failed(e.message ?: "Could not read the inbox")
            }
        }
    }

    fun clearScanStatus() { _scanStatus.value = ScanStatus.Idle }

    fun setCategory(txn: Txn, category: String, remember: Boolean) =
        viewModelScope.launch { repo.setCategory(txn, category, remember) }

    fun updateTxn(txn: Txn) = viewModelScope.launch { repo.updateTxn(txn) }

    fun setExcluded(txn: Txn, excluded: Boolean) =
        viewModelScope.launch { repo.setExcluded(txn, excluded) }

    fun deleteTxn(id: Long) = viewModelScope.launch { repo.deleteTxn(id) }

    fun addManual(amount: Double, merchant: String, category: String, timestamp: Long, note: String?) =
        viewModelScope.launch { repo.addManual(amount, merchant, category, timestamp, note) }

    fun setBudget(category: String, cap: Double) =
        viewModelScope.launch { repo.setBudget(category, cap) }

    fun deleteRule(key: String) = viewModelScope.launch { repo.deleteRule(key) }

    suspend fun exportCsv(): String = repo.exportCsv()

    private fun <T> Flow<T>.stateInVm(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}

sealed interface ScanStatus {
    data object Idle : ScanStatus
    data object Running : ScanStatus
    data class Done(val added: Int) : ScanStatus
    data class Failed(val message: String) : ScanStatus
}
