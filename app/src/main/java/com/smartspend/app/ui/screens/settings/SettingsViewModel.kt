package com.smartspend.app.ui.screens.settings

import android.app.Application
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.app.core.database.SmartSpendDatabase
import com.smartspend.app.core.database.entity.*
import com.smartspend.app.core.parser.AmountNormalizer
import com.smartspend.app.core.parser.ParsedTransaction
import com.smartspend.app.core.parser.ParserEngine
import com.smartspend.app.core.repository.TransactionRepository
import com.smartspend.app.core.widget.WidgetStateStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsUiState(
    val isListenerActive: Boolean = false,
    val isIslandActive: Boolean = false,
    val isDevModeEnabled: Boolean = false,
    val monthlyBudgetLimit: Long = 3_000_000L,
    val alertThresholdPct: Int = 80,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val rules: List<NotificationRuleEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val plans: List<PurchasePlanEntity> = emptyList(),
    val testParseResult: ParsedTransaction? = null,
    val isHideBalanceDefault: Boolean = false,
    val savedMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartSpendDatabase.getInstance(application)
    private val parserEngine = ParserEngine()
    private val repo = TransactionRepository(application, db)
    private val prefs = application.getSharedPreferences("smartspend_prefs", android.content.Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState(isDevModeEnabled = prefs.getBoolean("dev_mode_enabled", false)))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val rawEvents: Flow<List<RawEventEntity>> = db.rawEventDao().getRecentRawEventsFlow(100)

    init {
        loadData()
    }

    fun setDevMode(enabled: Boolean) {
        prefs.edit().putBoolean("dev_mode_enabled", enabled).apply()
        _uiState.value = _uiState.value.copy(isDevModeEnabled = enabled)
    }

    fun clearRawEvents() {
        viewModelScope.launch {
            db.rawEventDao().clearAll()
        }
    }

    fun sendTestPushNotification() {
        com.smartspend.app.core.notification.NotificationHelper.sendTestNotification(getApplication())
    }

    fun loadData() {
        val app = getApplication<Application>()
        val enabledListeners = Settings.Secure.getString(app.contentResolver, "enabled_notification_listeners")
        val isListenerActive = enabledListeners?.contains(app.packageName) == true

        // The island renders through standard TYPE_APPLICATION_OVERLAY (SYSTEM_ALERT_WINDOW),
        // completely avoiding Accessibility Service detection by banking apps.
        val isIslandActive = Settings.canDrawOverlays(app)

        viewModelScope.launch {
            combine(
                db.accountDao().getAllAccountsFlow(),
                db.categoryDao().getAllCategoriesFlow(),
                db.notificationRuleDao().getAllRulesFlow(),
                db.debtDao().getAllDebtsFlow(),
                db.purchasePlanDao().getAllPlansFlow()
            ) { accounts, categories, rules, debts, plans ->
                val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                val currentMonth = monthFormat.format(Date())
                val budget = db.budgetDao().getTotalBudgetForMonth(currentMonth)

                _uiState.value.copy(
                    isListenerActive = isListenerActive,
                    isIslandActive = isIslandActive,
                    accounts = accounts,
                    categories = categories,
                    rules = rules,
                    debts = debts,
                    plans = plans,
                    monthlyBudgetLimit = budget?.limitAmount ?: 3_000_000L,
                    alertThresholdPct = budget?.alertThresholdPct ?: 80
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun updateMonthlyBudget(limit: Long, thresholdPct: Int) {
        viewModelScope.launch {
            val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val currentMonth = monthFormat.format(Date())
            val existing = db.budgetDao().getTotalBudgetForMonth(currentMonth)
            if (existing != null) {
                db.budgetDao().update(
                    existing.copy(limitAmount = limit, alertThresholdPct = thresholdPct, updatedAt = System.currentTimeMillis())
                )
            } else {
                db.budgetDao().insert(
                    BudgetEntity(
                        periodMonth = currentMonth,
                        limitAmount = limit,
                        alertThresholdPct = thresholdPct
                    )
                )
            }
            _uiState.value = _uiState.value.copy(monthlyBudgetLimit = limit, alertThresholdPct = thresholdPct)
        }
    }

    fun toggleRule(rule: NotificationRuleEntity) {
        viewModelScope.launch {
            db.notificationRuleDao().update(rule.copy(isEnabled = !rule.isEnabled))
        }
    }

    fun testParse(packageName: String, text: String) {
        viewModelScope.launch {
            val rules = db.notificationRuleDao().getEnabledRulesForPackage(packageName)
            val result = parserEngine.parse(packageName, null, text, rules)
            _uiState.value = _uiState.value.copy(testParseResult = result)
        }
    }

    fun addCustomRule(
        providerCode: String,
        packageName: String,
        ruleName: String,
        pattern: String,
        txType: String
    ) {
        viewModelScope.launch {
            db.notificationRuleDao().insert(
                NotificationRuleEntity(
                    providerCode = providerCode,
                    packageName = packageName,
                    ruleName = ruleName,
                    bodyPattern = pattern,
                    txType = txType,
                    groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                    isBuiltin = false,
                    priority = 50
                )
            )

            // Auto-create Account if not exists so transactions link directly to this bank/wallet!
            val existingAccount = db.accountDao().findAccountByPackageAndHint(packageName, null)
            if (existingAccount == null) {
                val cleanName = ruleName.substringBefore("-").trim().ifEmpty { providerCode }
                db.accountDao().insert(
                    com.smartspend.app.core.database.entity.AccountEntity(
                        name = cleanName,
                        type = "BANK",
                        providerCode = providerCode,
                        packageName = packageName,
                        initialBalance = 0L,
                        currentBalance = 0L,
                        colorHex = "#005EAA",
                        icon = "bank"
                    )
                )
            }
        }
    }

    // Debts operations
    fun addDebt(
        direction: String,
        counterparty: String,
        amount: Long,
        dueDate: Long?,
        accountId: Long?,
        recordToBalance: Boolean,
        note: String?
    ) {
        viewModelScope.launch {
            val debtId = db.debtDao().insert(
                DebtEntity(
                    direction = direction,
                    counterpartyName = counterparty,
                    principalAmount = amount,
                    dueDate = dueDate,
                    accountId = accountId,
                    recordToBalance = recordToBalance,
                    note = note
                )
            )

            // If recordToBalance, create loan disbursement transaction
            if (recordToBalance && accountId != null) {
                val txType = if (direction == "OWE") "INCOME" else "EXPENSE"
                val tx = TransactionEntity(
                    type = txType,
                    amount = amount,
                    sourceAccountId = if (txType == "EXPENSE") accountId else null,
                    destinationAccountId = if (txType == "INCOME") accountId else null,
                    merchant = counterparty,
                    note = "Pencairan Pinjaman: $counterparty",
                    debtId = debtId,
                    excludeFromReports = true
                )
                repo.insertTransaction(tx)
            }

            // Schedule default reminder (H-3, H-1, day-0)
            if (dueDate != null) {
                db.reminderDao().insert(
                    ReminderEntity(
                        debtId = debtId,
                        offsetDays = 0,
                        nextFireAt = dueDate,
                        repeatEveryDays = 3,
                        maxRepeats = 3
                    )
                )
            }
        }
    }

    fun recordDebtPayment(debt: DebtEntity, paymentAmount: Long, note: String?) {
        viewModelScope.launch {
            val newPaid = debt.paidAmount + paymentAmount
            val isPaidOff = newPaid >= debt.principalAmount
            val newStatus = if (isPaidOff) "PAID" else "ACTIVE"

            var txId: Long? = null
            if (debt.recordToBalance && debt.accountId != null) {
                val txType = if (debt.direction == "OWE") "EXPENSE" else "INCOME"
                val tx = TransactionEntity(
                    type = txType,
                    amount = paymentAmount,
                    sourceAccountId = if (txType == "EXPENSE") debt.accountId else null,
                    destinationAccountId = if (txType == "INCOME") debt.accountId else null,
                    merchant = debt.counterpartyName,
                    note = "Pembayaran Utang: ${debt.counterpartyName}",
                    debtId = debt.id,
                    excludeFromReports = true
                )
                txId = repo.insertTransaction(tx)
            }

            db.debtPaymentDao().insert(
                DebtPaymentEntity(
                    debtId = debt.id,
                    amount = paymentAmount,
                    transactionId = txId,
                    note = note
                )
            )

            db.debtDao().update(
                debt.copy(
                    paidAmount = newPaid,
                    status = newStatus,
                    updatedAt = System.currentTimeMillis()
                )
            )

            if (isPaidOff) {
                db.reminderDao().disableForDebt(debt.id)
            }
        }
    }

    // Purchase Plan decision operations (PRD section 9.4)
    fun decidePurchasePlan(plan: PurchasePlanEntity, decision: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            when (decision) {
                "BUY" -> {
                    val tx = TransactionEntity(
                        type = "EXPENSE",
                        amount = plan.estimatedPrice,
                        categoryId = plan.categoryId,
                        merchant = plan.name,
                        note = "Rencana Beli Terealisasi",
                        planId = plan.id,
                        captureSource = "MANUAL",
                        timestamp = System.currentTimeMillis()
                    )
                    val txId = repo.insertTransaction(tx)
                    db.purchasePlanDao().update(
                        plan.copy(
                            status = "BOUGHT",
                            boughtTransactionId = txId,
                            decidedAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                "SKIP" -> {
                    db.purchasePlanDao().update(
                        plan.copy(
                            status = "SKIPPED",
                            decidedAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                "POSTPONE" -> {
                    val newCount = plan.postponeCount + 1
                    if (newCount <= 3) {
                        val newDecideAt = System.currentTimeMillis() + (3 * 86_400_000L) // 3 days
                        db.purchasePlanDao().update(
                            plan.copy(
                                status = "WAITING",
                                postponeCount = newCount,
                                decideAt = newDecideAt,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
            onComplete()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            db.clearAllTables()
            loadData()
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
                    sortOrder = (_uiState.value.accounts.maxOfOrNull { it.sortOrder } ?: 0) + 1
                )
            )
            loadData()
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
            loadData()
        }
    }

    fun archiveAccount(account: AccountEntity) {
        viewModelScope.launch {
            db.accountDao().archiveAccount(account.id)
            loadData()
        }
    }

    fun exportRulesJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val rules = db.notificationRuleDao().getAllRulesList()
            val arr = JSONArray()
            for (r in rules) {
                val obj = JSONObject().apply {
                    put("providerCode", r.providerCode)
                    put("packageName", r.packageName)
                    put("ruleName", r.ruleName)
                    put("titlePattern", r.titlePattern ?: "")
                    put("bodyPattern", r.bodyPattern)
                    put("txType", r.txType)
                    put("groupMapJson", r.groupMapJson)
                    put("priority", r.priority)
                    put("isEnabled", r.isEnabled)
                }
                arr.put(obj)
            }
            onResult(arr.toString(2))
        }
    }

    fun importRulesJson(jsonStr: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val arr = JSONArray(jsonStr.trim())
                val imported = mutableListOf<NotificationRuleEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    imported.add(
                        NotificationRuleEntity(
                            id = 0,
                            providerCode = obj.optString("providerCode", "OTHER"),
                            packageName = obj.getString("packageName"),
                            ruleName = obj.getString("ruleName"),
                            titlePattern = obj.optString("titlePattern").ifBlank { null },
                            bodyPattern = obj.getString("bodyPattern"),
                            txType = obj.optString("txType", "EXPENSE"),
                            groupMapJson = obj.optString("groupMapJson", "{}"),
                            priority = obj.optInt("priority", 100),
                            isEnabled = obj.optBoolean("isEnabled", true),
                            isBuiltin = false
                        )
                    )
                }
                if (imported.isNotEmpty()) {
                    db.notificationRuleDao().insertAll(imported)
                    loadData()
                    onComplete(true, "Berhasil mengimpor ${imported.size} aturan notifikasi!")
                } else {
                    onComplete(false, "Format JSON kosong atau tidak sesuai.")
                }
            } catch (e: Exception) {
                onComplete(false, "Gagal mengimpor: ${e.localizedMessage}")
            }
        }
    }

    fun backupDatabaseJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val root = JSONObject()
            root.put("version", 1)
            root.put("timestamp", System.currentTimeMillis())

            val accArr = JSONArray()
            for (a in db.accountDao().getAllAccountsList()) {
                accArr.put(JSONObject().apply {
                    put("id", a.id)
                    put("name", a.name)
                    put("type", a.type)
                    put("providerCode", a.providerCode ?: "")
                    put("packageName", a.packageName ?: "")
                    put("accountHint", a.accountHint ?: "")
                    put("initialBalance", a.initialBalance)
                    put("currentBalance", a.currentBalance)
                    put("colorHex", a.colorHex)
                    put("sortOrder", a.sortOrder)
                })
            }
            root.put("accounts", accArr)

            val txArr = JSONArray()
            for (t in db.transactionDao().getAllTransactionsList()) {
                txArr.put(JSONObject().apply {
                    put("id", t.id)
                    put("sourceAccountId", t.sourceAccountId ?: 0L)
                    put("type", t.type)
                    put("amount", t.amount)
                    put("categoryId", t.categoryId ?: 0L)
                    put("merchant", t.merchant ?: "")
                    put("note", t.note ?: "")
                    put("timestamp", t.timestamp)
                    put("captureSource", t.captureSource)
                    put("reviewStatus", t.reviewStatus)
                })
            }
            root.put("transactions", txArr)

            val bArr = JSONArray()
            for (b in db.budgetDao().getAllBudgetsList()) {
                bArr.put(JSONObject().apply {
                    put("id", b.id)
                    put("periodMonth", b.periodMonth)
                    put("categoryId", b.categoryId ?: -1L)
                    put("limitAmount", b.limitAmount)
                    put("alertThresholdPct", b.alertThresholdPct)
                })
            }
            root.put("budgets", bArr)

            val dArr = JSONArray()
            for (d in db.debtDao().getAllDebtsList()) {
                dArr.put(JSONObject().apply {
                    put("id", d.id)
                    put("direction", d.direction)
                    put("counterpartyName", d.counterpartyName)
                    put("principalAmount", d.principalAmount)
                    put("paidAmount", d.paidAmount)
                    put("status", d.status)
                    put("startDate", d.startDate)
                    put("dueDate", d.dueDate ?: 0L)
                    put("note", d.note ?: "")
                })
            }
            root.put("debts", dArr)

            val pArr = JSONArray()
            for (p in db.purchasePlanDao().getAllPlansList()) {
                pArr.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("estimatedPrice", p.estimatedPrice)
                    put("status", p.status)
                    put("decideAt", p.decideAt)
                    put("linkOrNote", p.linkOrNote ?: "")
                })
            }
            root.put("plans", pArr)

            onResult(root.toString(2))
        }
    }

    fun restoreDatabaseJson(jsonStr: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val root = JSONObject(jsonStr.trim())
                val accounts = mutableListOf<AccountEntity>()
                val accArr = root.optJSONArray("accounts")
                if (accArr != null) {
                    for (i in 0 until accArr.length()) {
                        val o = accArr.getJSONObject(i)
                        accounts.add(
                            AccountEntity(
                                id = o.optLong("id", 0L),
                                name = o.getString("name"),
                                type = o.optString("type", "OTHER"),
                                providerCode = o.optString("providerCode").ifBlank { null },
                                packageName = o.optString("packageName").ifBlank { null },
                                accountHint = o.optString("accountHint").ifBlank { null },
                                initialBalance = o.optLong("initialBalance", 0L),
                                currentBalance = o.optLong("currentBalance", 0L),
                                colorHex = o.optString("colorHex", "#0A84FF"),
                                sortOrder = o.optInt("sortOrder", 0)
                            )
                        )
                    }
                }

                val transactions = mutableListOf<TransactionEntity>()
                val txArr = root.optJSONArray("transactions")
                if (txArr != null) {
                    for (i in 0 until txArr.length()) {
                        val o = txArr.getJSONObject(i)
                        val catId = o.optLong("categoryId", 0L)
                        val accId = if (o.has("sourceAccountId")) o.optLong("sourceAccountId", 0L) else o.optLong("accountId", 0L)
                        transactions.add(
                            TransactionEntity(
                                id = o.optLong("id", 0L),
                                sourceAccountId = if (accId > 0) accId else null,
                                type = o.getString("type"),
                                amount = o.getLong("amount"),
                                categoryId = if (catId > 0) catId else null,
                                merchant = o.optString("merchant").ifBlank { null },
                                note = o.optString("note").ifBlank { null },
                                timestamp = o.getLong("timestamp"),
                                captureSource = o.optString("captureSource", "MANUAL"),
                                reviewStatus = o.optString("reviewStatus", "CONFIRMED")
                            )
                        )
                    }
                }

                val debts = mutableListOf<DebtEntity>()
                val dArr = root.optJSONArray("debts")
                if (dArr != null) {
                    for (i in 0 until dArr.length()) {
                        val o = dArr.getJSONObject(i)
                        val due = o.optLong("dueDate", 0L)
                        debts.add(
                            DebtEntity(
                                id = o.optLong("id", 0L),
                                direction = o.getString("direction"),
                                counterpartyName = o.getString("counterpartyName"),
                                principalAmount = o.getLong("principalAmount"),
                                paidAmount = o.optLong("paidAmount", 0L),
                                status = o.optString("status", "ACTIVE"),
                                startDate = o.optLong("startDate", System.currentTimeMillis()),
                                dueDate = if (due > 0) due else null,
                                note = o.optString("note").ifBlank { null }
                            )
                        )
                    }
                }

                if (accounts.isNotEmpty()) db.accountDao().insertAll(accounts)
                if (transactions.isNotEmpty()) db.transactionDao().insertAll(transactions)
                if (debts.isNotEmpty()) db.debtDao().insertAll(debts)

                loadData()
                onComplete(true, "Data berhasil direstore (${accounts.size} akun, ${transactions.size} transaksi, ${debts.size} utang)!")
            } catch (e: Exception) {
                onComplete(false, "Gagal restore database: ${e.localizedMessage}")
            }
        }
    }
}
