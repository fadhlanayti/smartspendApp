package com.smartspend.app.core.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.smartspend.app.core.database.SmartSpendDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object WidgetUpdater {
    private var updateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun notifyDataChanged(context: Context) {
        // Fast responsive debounce 200ms (near real-time)
        updateJob?.cancel()
        updateJob = scope.launch {
            delay(200L)
            refreshSnapshot(context.applicationContext)
        }
    }

    suspend fun refreshSnapshot(context: Context) {
        val db = SmartSpendDatabase.getInstance(context)
        val transactionDao = db.transactionDao()
        val budgetDao = db.budgetDao()
        val accountDao = db.accountDao()

        // Today's bounds
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val endOfToday = cal.timeInMillis

        val todayExpenses = transactionDao.getExpenseTransactionsBetween(startOfToday, endOfToday)
        val todayTotal = todayExpenses.sumOf { it.amount + it.fee }

        // Month bounds
        val monthCal = Calendar.getInstance()
        monthCal.set(Calendar.DAY_OF_MONTH, 1)
        monthCal.set(Calendar.HOUR_OF_DAY, 0)
        monthCal.set(Calendar.MINUTE, 0)
        monthCal.set(Calendar.SECOND, 0)
        val startOfMonth = monthCal.timeInMillis

        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val maxDays = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)
        val remainingDays = maxOf(1, maxDays - currentDay + 1)

        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val periodMonth = monthFormat.format(Calendar.getInstance().time)
        val budget = budgetDao.getTotalBudgetForMonth(periodMonth)
        val monthLimit = budget?.limitAmount ?: 3_000_000L

        monthCal.set(Calendar.DAY_OF_MONTH, maxDays)
        monthCal.set(Calendar.HOUR_OF_DAY, 23)
        monthCal.set(Calendar.MINUTE, 59)
        val endOfMonth = monthCal.timeInMillis

        val monthExpenses = transactionDao.getExpenseTransactionsBetween(startOfMonth, endOfMonth)
        val monthTotal = monthExpenses.sumOf { it.amount + it.fee }

        // Sisa Hari Ini calculation:
        // 1. Budget bulan ini yang tersedia untuk sisa hari = total limit - pengeluaran sebelum hari ini
        val priorMonthExpenses = maxOf(0L, monthTotal - todayTotal)
        val remainingBudgetForMonth = maxOf(0L, monthLimit - priorMonthExpenses)
        // 2. Jatah harian dasar = sisa budget bulan dibagi sisa hari
        val dailyQuota = if (remainingDays > 0) remainingBudgetForMonth / remainingDays else 0L
        // 3. Sisa hari ini = jatah harian dasar dikurangi pengeluaran hari ini
        val remainingDaily = maxOf(0L, dailyQuota - todayTotal)

        // Total saldo semua akun aktif (yang tidak diarsipkan)
        val activeAccounts = accountDao.getActiveAccountsFlow().first()
        val totalBalance = activeAccounts.sumOf { it.currentBalance }

        // Utang & piutang aktif
        val activeDebts = db.debtDao().getAllDebtsFlow().first().filter { it.status == "ACTIVE" && !it.isDeleted }
        val totalDebtOwe = activeDebts.filter { it.direction == "OWE" }.sumOf { it.principalAmount - it.paidAmount }
        val totalDebtOwed = activeDebts.filter { it.direction == "OWED" }.sumOf { it.principalAmount - it.paidAmount }

        // Top 3 categories
        val categoryTotals = monthExpenses.groupBy { it.categoryId ?: 0L }
            .mapValues { entry -> entry.value.sumOf { it.amount + it.fee } }
            .toList()
            .sortedByDescending { it.second }
            .take(3)

        val catArray = JSONArray()
        for ((catId, amt) in categoryTotals) {
            val cat = db.categoryDao().getCategoryById(catId)
            val obj = JSONObject().apply {
                put("name", cat?.name ?: "Lain-lain")
                put("amount", amt)
                put("icon", cat?.icon ?: "tag")
            }
            catArray.put(obj)
        }

        // 3 Recent Transactions
        val recentTxs = transactionDao.getTransactionsBetween(startOfMonth, System.currentTimeMillis()).take(3)
        val txArray = JSONArray()
        for (tx in recentTxs) {
            val obj = JSONObject().apply {
                put("id", tx.id)
                put("merchant", tx.merchant ?: "Transaksi")
                put("amount", tx.amount)
                put("type", tx.type)
            }
            txArray.put(obj)
        }

        val existingSnapshot = WidgetStateStore.getSnapshot(context)
        val newSnapshot = existingSnapshot.copy(
            todayExpense = todayTotal,
            remainingDailyBudget = remainingDaily,
            monthExpense = monthTotal,
            monthBudgetLimit = monthLimit,
            topCategoriesJson = catArray.toString(),
            recentTransactionsJson = txArray.toString(),
            totalBalance = totalBalance,
            totalDebtOwe = totalDebtOwe,
            totalDebtOwed = totalDebtOwed
        )

        WidgetStateStore.updateSnapshot(context, newSnapshot)

        // Update Dynamic Island overlay in real-time
        try {
            com.smartspend.app.ui.components.island.SystemIslandOverlayManager.onSnapshotUpdated(context, newSnapshot)
        } catch (_: Throwable) {
        }

        // Request Glance refresh
        try {
            SmartSpendGlanceWidget().updateAll(context)
        } catch (_: Exception) {
            // Glance update fallback
        }
    }
}
