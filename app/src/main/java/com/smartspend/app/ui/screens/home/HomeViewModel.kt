package com.smartspend.app.ui.screens.home

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.app.core.database.SmartSpendDatabase
import com.smartspend.app.core.database.entity.AccountEntity
import com.smartspend.app.core.database.entity.CategoryEntity
import com.smartspend.app.core.database.entity.TransactionEntity
import com.smartspend.app.core.repository.TransactionRepository
import com.smartspend.app.core.widget.WidgetStateStore
import com.smartspend.app.ui.components.DaySpend
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class PrimaryHomeData(
    val recentTx: List<TransactionEntity>,
    val needsReview: Int,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>
)

data class SecondaryHomeData(
    val snapshot: com.smartspend.app.core.widget.WidgetSnapshot,
    val debts: List<com.smartspend.app.core.database.entity.DebtEntity>,
    val plans: List<com.smartspend.app.core.database.entity.PurchasePlanEntity>
)

data class HomeUiState(
    val remainingBudget: Long = 0L,
    val monthlyIncome: Long = 0L,
    val monthlyExpense: Long = 0L,
    val budgetLimit: Long = 3_000_000L,
    val isHideBalance: Boolean = false,
    val isListenerActive: Boolean = false,
    val needsReviewCount: Int = 0,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val categoriesMap: Map<Long, CategoryEntity> = emptyMap(),
    val weekSpends: List<DaySpend> = emptyList(),
    val totalDebtOwe: Long = 0L,
    val totalDebtOwed: Long = 0L,
    val oweDetails: List<com.smartspend.app.core.database.entity.DebtEntity> = emptyList(),
    val owedDetails: List<com.smartspend.app.core.database.entity.DebtEntity> = emptyList(),
    val activePlansCount: Int = 0,
    val savedMoneyThisMonth: Long = 0L
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SmartSpendDatabase.getInstance(application)
    private val repo = TransactionRepository(application, db)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        val app = getApplication<Application>()

        // Check listener status
        val enabledListeners = Settings.Secure.getString(app.contentResolver, "enabled_notification_listeners")
        val isListenerActive = enabledListeners?.contains(app.packageName) == true

        viewModelScope.launch {
            val existing = db.accountDao().getAllAccountsList()
            for (acc in existing) {
                if (acc.name.contains("Tunai", ignoreCase = true) && acc.providerCode != "CASH") {
                    db.accountDao().update(acc.copy(providerCode = "CASH", colorHex = "#1E7E34"))
                }
                if (acc.name.contains("BCA", ignoreCase = true) && acc.providerCode != "BCA") {
                    db.accountDao().update(acc.copy(providerCode = "BCA", colorHex = "#005EAA"))
                }
            }
            if (existing.none { it.providerCode == "MANDIRI" }) {
                db.accountDao().insertAll(
                    listOf(
                        AccountEntity(
                            name = "Mandiri Livin",
                            type = "BANK",
                            providerCode = "MANDIRI",
                            packageName = "id.bmri.livin",
                            initialBalance = 1_850_000,
                            currentBalance = 1_850_000,
                            colorHex = "#003D79",
                            icon = "mandiri"
                        ),
                        AccountEntity(
                            name = "BRI Mo",
                            type = "BANK",
                            providerCode = "BRI",
                            packageName = "id.co.bri.brimo",
                            initialBalance = 1_200_000,
                            currentBalance = 1_200_000,
                            colorHex = "#00529C",
                            icon = "bri"
                        ),
                        AccountEntity(
                            name = "BNI Mobile",
                            type = "BANK",
                            providerCode = "BNI",
                            packageName = "src.mwallet.bni.co.id",
                            initialBalance = 750_000,
                            currentBalance = 750_000,
                            colorHex = "#F15A22",
                            icon = "bni"
                        ),
                        AccountEntity(
                            name = "GoPay",
                            type = "EWALLET",
                            providerCode = "GOPAY",
                            packageName = "com.gojek.app",
                            initialBalance = 240_000,
                            currentBalance = 240_000,
                            colorHex = "#00AED6",
                            icon = "gopay"
                        ),
                        AccountEntity(
                            name = "DANA",
                            type = "EWALLET",
                            providerCode = "DANA",
                            packageName = "id.dana",
                            initialBalance = 180_000,
                            currentBalance = 180_000,
                            colorHex = "#118EEA",
                            icon = "dana"
                        )
                    )
                )
            }

            val flowPrimary = combine(
                repo.allTransactions,
                repo.needsReviewCount,
                db.accountDao().getActiveAccountsFlow(),
                db.categoryDao().getActiveCategoriesFlow()
            ) { allTx, needsReview, accounts, categories ->
                PrimaryHomeData(allTx.take(5), needsReview, accounts, categories)
            }

            val flowSecondary = combine(
                WidgetStateStore.getSnapshotFlow(app),
                db.debtDao().getAllDebtsFlow(),
                db.purchasePlanDao().getAllPlansFlow()
            ) { snapshot, debts, plans ->
                SecondaryHomeData(snapshot, debts, plans)
            }

            flowPrimary.combine(flowSecondary) { primary, secondary ->
                val catMap = primary.categories.associateBy { it.id }

                // Month calculation
                val (startOfMonth, endOfMonth) = getMonthBounds()
                val monthTxs = db.transactionDao().getTransactionsBetween(startOfMonth, endOfMonth)
                val income = monthTxs.filter { it.type == "INCOME" && !it.excludeFromReports }.sumOf { it.amount }
                val expense = monthTxs.filter { it.type == "EXPENSE" && !it.excludeFromReports }.sumOf { it.amount + it.fee }
                val budget = db.budgetDao().getTotalBudgetForMonth(
                    SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                )
                val limit = budget?.limitAmount ?: 3_000_000L
                val remaining = maxOf(0L, limit - expense)

                // 7 days spend
                val weekData = calculateWeekSpends()

                // Debts
                val activeDebts = secondary.debts.filter { it.status == "ACTIVE" && !it.isDeleted }
                val oweList = activeDebts.filter { it.direction == "OWE" }.sortedByDescending { it.principalAmount - it.paidAmount }
                val owedList = activeDebts.filter { it.direction == "OWED" }.sortedByDescending { it.principalAmount - it.paidAmount }
                val owe = oweList.sumOf { it.principalAmount - it.paidAmount }
                val owed = owedList.sumOf { it.principalAmount - it.paidAmount }

                // Plans
                val activePlans = secondary.plans.filter { it.status == "WAITING" || it.status == "READY" }
                val savedMoney = secondary.plans.filter { it.status == "SKIPPED" && it.createdAt in startOfMonth..endOfMonth }
                    .sumOf { it.estimatedPrice }

                HomeUiState(
                    remainingBudget = remaining,
                    monthlyIncome = income,
                    monthlyExpense = expense,
                    budgetLimit = limit,
                    isHideBalance = secondary.snapshot.isHideBalance,
                    isListenerActive = isListenerActive,
                    needsReviewCount = primary.needsReview,
                    recentTransactions = primary.recentTx,
                    accounts = primary.accounts,
                    categoriesMap = catMap,
                    weekSpends = weekData,
                    totalDebtOwe = owe,
                    totalDebtOwed = owed,
                    oweDetails = oweList,
                    owedDetails = owedList,
                    activePlansCount = activePlans.size,
                    savedMoneyThisMonth = savedMoney
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun toggleHideBalance() {
        viewModelScope.launch {
            WidgetStateStore.toggleHideBalance(getApplication())
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repo.deleteTransaction(id)
        }
    }

    fun restoreTransaction(id: Long) {
        viewModelScope.launch {
            repo.restoreTransaction(id)
        }
    }

    fun addAccount(name: String, provider: com.smartspend.app.core.model.ProviderInfo, balance: Long, hint: String?) {
        viewModelScope.launch {
            db.accountDao().insert(
                AccountEntity(
                    name = name,
                    type = provider.type,
                    providerCode = provider.code,
                    packageName = provider.defaultPackageName,
                    accountHint = hint,
                    initialBalance = balance,
                    currentBalance = balance,
                    colorHex = provider.colorHex,
                    sortOrder = (uiState.value.accounts.maxOfOrNull { it.sortOrder } ?: 0) + 1
                )
            )
        }
    }

    fun updateAccount(account: AccountEntity, newName: String, newBalance: Long) {
        viewModelScope.launch {
            db.accountDao().update(
                account.copy(
                    name = newName,
                    currentBalance = newBalance,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun archiveAccount(account: AccountEntity) {
        viewModelScope.launch {
            db.accountDao().archiveAccount(account.id)
        }
    }

    private suspend fun calculateWeekSpends(): List<DaySpend> {
        val result = mutableListOf<DaySpend>()
        val cal = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEE", Locale("id", "ID"))

        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance()
            dayCal.add(Calendar.DAY_OF_YEAR, -i)
            dayCal.set(Calendar.HOUR_OF_DAY, 0)
            dayCal.set(Calendar.MINUTE, 0)
            dayCal.set(Calendar.SECOND, 0)
            dayCal.set(Calendar.MILLISECOND, 0)
            val start = dayCal.timeInMillis

            dayCal.set(Calendar.HOUR_OF_DAY, 23)
            dayCal.set(Calendar.MINUTE, 59)
            dayCal.set(Calendar.SECOND, 59)
            val end = dayCal.timeInMillis

            val txs = db.transactionDao().getExpenseTransactionsBetween(start, end)
            val total = txs.sumOf { it.amount + it.fee }

            val isToday = i == 0
            val label = if (isToday) "Hari Ini" else dayFormat.format(dayCal.time)
            result.add(DaySpend(dayLabel = label, amount = total, isToday = isToday))
        }

        return result
    }

    private fun getMonthBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val start = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val end = cal.timeInMillis

        return Pair(start, end)
    }
}
