package com.smartspend.app.ui.screens.fastadd

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.app.core.database.SmartSpendDatabase
import com.smartspend.app.core.database.entity.AccountEntity
import com.smartspend.app.core.database.entity.CategoryEntity
import com.smartspend.app.core.database.entity.PurchasePlanEntity
import com.smartspend.app.core.database.entity.TransactionEntity
import com.smartspend.app.core.model.CaptureSource
import com.smartspend.app.core.model.ReviewStatus
import com.smartspend.app.core.model.TransactionType
import com.smartspend.app.core.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FastAddUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountString: String = "0",
    val amount: Long = 0L,
    val selectedAccountId: Long? = null,
    val destinationAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val isSuccess: Boolean = false
)

class FastAddViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartSpendDatabase.getInstance(application)
    private val repo = TransactionRepository(application, db)

    private val _uiState = MutableStateFlow(FastAddUiState())
    val uiState: StateFlow<FastAddUiState> = _uiState.asStateFlow()

    init {
        loadMetadata()
    }

    private fun loadMetadata() {
        viewModelScope.launch {
            db.accountDao().getActiveAccountsFlow().collect { accounts ->
                val current = _uiState.value
                val defaultAccount = current.selectedAccountId ?: accounts.firstOrNull()?.id
                val defaultDest = current.destinationAccountId ?: accounts.getOrNull(1)?.id
                _uiState.value = current.copy(
                    accounts = accounts,
                    selectedAccountId = defaultAccount,
                    destinationAccountId = defaultDest
                )
            }
        }

        viewModelScope.launch {
            db.categoryDao().getActiveCategoriesFlow().collect { categories ->
                val current = _uiState.value
                val defaultCat = current.selectedCategoryId ?: categories.firstOrNull { it.parentId == null }?.id
                _uiState.value = current.copy(
                    categories = categories,
                    selectedCategoryId = defaultCat
                )
            }
        }
    }

    fun setType(type: TransactionType) {
        _uiState.value = _uiState.value.copy(type = type)
    }

    fun onDigitClick(digit: String) {
        val current = _uiState.value.amountString
        val newString = if (current == "0") digit else current + digit
        val parsed = newString.toLongOrNull() ?: _uiState.value.amount
        _uiState.value = _uiState.value.copy(amountString = newString, amount = parsed)
    }

    fun onBackspace() {
        val current = _uiState.value.amountString
        val newString = if (current.length <= 1) "0" else current.dropLast(1)
        val parsed = newString.toLongOrNull() ?: 0L
        _uiState.value = _uiState.value.copy(amountString = newString, amount = parsed)
    }

    fun addQuickAmount(delta: Long) {
        val newAmount = _uiState.value.amount + delta
        _uiState.value = _uiState.value.copy(
            amount = newAmount,
            amountString = newAmount.toString()
        )
    }

    fun selectAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
    }

    fun selectDestinationAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(destinationAccountId = accountId)
    }

    fun selectCategory(categoryId: Long) {
        _uiState.value = _uiState.value.copy(selectedCategoryId = categoryId)
    }

    fun updateMerchant(merchant: String) {
        _uiState.value = _uiState.value.copy(merchant = merchant)
    }

    fun updateNote(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    fun saveTransaction(andAddAnother: Boolean = false, onComplete: () -> Unit) {
        val state = _uiState.value
        if (state.amount <= 0) return

        viewModelScope.launch {
            val tx = TransactionEntity(
                type = state.type.name,
                amount = state.amount,
                sourceAccountId = if (state.type == TransactionType.EXPENSE || state.type == TransactionType.TRANSFER) state.selectedAccountId else null,
                destinationAccountId = if (state.type == TransactionType.INCOME || state.type == TransactionType.TRANSFER) state.destinationAccountId else null,
                categoryId = if (state.type != TransactionType.TRANSFER) state.selectedCategoryId else null,
                merchant = state.merchant.ifBlank { null },
                note = state.note.ifBlank { null },
                captureSource = CaptureSource.MANUAL.name,
                reviewStatus = ReviewStatus.CONFIRMED.name,
                confidence = 1.0f,
                timestamp = System.currentTimeMillis()
            )

            repo.insertTransaction(tx)

            com.smartspend.app.ui.components.island.DynamicIslandManager.showTransactionEvent(
                title = state.merchant.ifBlank { if (state.type == TransactionType.EXPENSE) "Pengeluaran Cepat" else "Pemasukan Cepat" },
                amount = state.amount,
                type = state.type.name
            )

            if (andAddAnother) {
                _uiState.value = state.copy(
                    amount = 0L,
                    amountString = "0",
                    merchant = "",
                    note = ""
                )
            } else {
                onComplete()
            }
        }
    }

    fun delayAsPlan(onComplete: () -> Unit) {
        val state = _uiState.value
        if (state.amount <= 0) return

        val coolingDays = when {
            state.amount < 100_000 -> 1
            state.amount < 1_000_000 -> 3
            state.amount < 5_000_000 -> 7
            else -> 14
        }

        viewModelScope.launch {
            val plan = PurchasePlanEntity(
                name = state.merchant.ifBlank { "Rencana Belanja" },
                estimatedPrice = state.amount,
                categoryId = state.selectedCategoryId,
                coolingOffDays = coolingDays,
                decideAt = System.currentTimeMillis() + (coolingDays * 86_400_000L),
                status = "WAITING"
            )
            db.purchasePlanDao().insert(plan)
            onComplete()
        }
    }
}
