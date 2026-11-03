package com.smartspend.app.ui.screens.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.app.core.database.SmartSpendDatabase
import com.smartspend.app.core.database.entity.CategoryEntity
import com.smartspend.app.core.database.entity.TransactionEntity
import com.smartspend.app.core.model.PeriodFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CategoryShare(
    val categoryId: Long,
    val categoryName: String,
    val amount: Long,
    val percentage: Float,
    val colorHex: String
)

data class MerchantShare(
    val merchantName: String,
    val amount: Long,
    val count: Int
)

data class ReportsUiState(
    val period: PeriodFilter = PeriodFilter.THIS_MONTH,
    val totalExpense: Long = 0L,
    val totalIncome: Long = 0L,
    val categoryShares: List<CategoryShare> = emptyList(),
    val topMerchants: List<MerchantShare> = emptyList(),
    val averageDailyExpense: Long = 0L,
    val budgetExhaustionDate: String? = null,
    val filteredTransactions: List<TransactionEntity> = emptyList()
)

class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartSpendDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadData(PeriodFilter.THIS_MONTH)
    }

    fun setPeriod(period: PeriodFilter) {
        _uiState.value = _uiState.value.copy(period = period)
        loadData(period)
    }

    private fun loadData(period: PeriodFilter) {
        viewModelScope.launch {
            val (start, end) = getPeriodBounds(period)
            val txs = db.transactionDao().getTransactionsBetween(start, end)
                .filter { !it.excludeFromReports }

            val expenses = txs.filter { it.type == "EXPENSE" }
            val incomes = txs.filter { it.type == "INCOME" }

            val totalExpense = expenses.sumOf { it.amount + it.fee }
            val totalIncome = incomes.sumOf { it.amount }

            val categories = db.categoryDao().getActiveCategoriesFlow()
            val catMap = db.categoryDao().findByName("Lain-lain") // fallback

            // Category Breakdown
            val catTotals = expenses.groupBy { it.categoryId ?: 0L }
                .mapValues { entry -> entry.value.sumOf { it.amount + it.fee } }

            val shares = mutableListOf<CategoryShare>()
            val colorPalette = listOf("#FF9500", "#0A84FF", "#AF52DE", "#FF3B30", "#34C759", "#5856D6", "#8E8E93")

            var colorIndex = 0
            for ((catId, amt) in catTotals.entries.sortedByDescending { it.value }) {
                val cat = db.categoryDao().getCategoryById(catId)
                val pct = if (totalExpense > 0) (amt.toFloat() / totalExpense) * 100f else 0f
                val color = cat?.colorHex ?: colorPalette[colorIndex % colorPalette.size]
                shares.add(
                    CategoryShare(
                        categoryId = catId,
                        categoryName = cat?.name ?: "Lain-lain",
                        amount = amt,
                        percentage = pct,
                        colorHex = color
                    )
                )
                colorIndex++
            }

            // Top Merchants
            val merchantMap = expenses.filter { !it.merchant.isNullOrBlank() }
                .groupBy { it.merchant!! }
                .map { (name, list) ->
                    MerchantShare(
                        merchantName = name,
                        amount = list.sumOf { it.amount + it.fee },
                        count = list.size
                    )
                }
                .sortedByDescending { it.amount }
                .take(5)

            // Burn Rate & Days calculation
            val daysInPeriod = maxOf(1, ((end - start) / (86_400_000L)).toInt())
            val avgDaily = totalExpense / daysInPeriod

            val budget = db.budgetDao().getTotalBudgetForMonth(
                SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            )
            val limit = budget?.limitAmount ?: 3_000_000L
            val remainingBudget = maxOf(0L, limit - totalExpense)

            val exhaustionDate = if (avgDaily > 0 && remainingBudget > 0) {
                val daysLeft = (remainingBudget / avgDaily).toInt()
                val exhaustionCal = Calendar.getInstance()
                exhaustionCal.add(Calendar.DAY_OF_YEAR, daysLeft)
                SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(exhaustionCal.time)
            } else if (remainingBudget <= 0) {
                "Sudah habis"
            } else null

            _uiState.value = _uiState.value.copy(
                totalExpense = totalExpense,
                totalIncome = totalIncome,
                categoryShares = shares,
                topMerchants = merchantMap,
                averageDailyExpense = avgDaily,
                budgetExhaustionDate = exhaustionDate,
                filteredTransactions = txs
            )
        }
    }

    fun generateCsvContent(): String {
        val txs = _uiState.value.filteredTransactions
        val sb = StringBuilder()
        sb.append("ID,Tanggal,Tipe,Nominal,Biaya,Merchant,Catatan\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        for (tx in txs) {
            sb.append("${tx.id},")
            sb.append("${sdf.format(Date(tx.timestamp))},")
            sb.append("${tx.type},")
            sb.append("${tx.amount},")
            sb.append("${tx.fee},")
            sb.append("\"${(tx.merchant ?: "").replace("\"", "\"\"")}\",")
            sb.append("\"${(tx.note ?: "").replace("\"", "\"\"")}\"\n")
        }
        return sb.toString()
    }

    private fun getPeriodBounds(period: PeriodFilter): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val end = cal.timeInMillis

        when (period) {
            PeriodFilter.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
            }
            PeriodFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
            }
            PeriodFilter.THREE_MONTHS -> {
                cal.add(Calendar.MONTH, -3)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
            }
            PeriodFilter.CUSTOM -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
            }
        }
        val start = cal.timeInMillis
        return Pair(start, end)
    }
}
