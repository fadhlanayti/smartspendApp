package com.smartspend.app.ui.screens.transactions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.app.core.database.SmartSpendDatabase
import com.smartspend.app.core.database.entity.CategoryEntity
import com.smartspend.app.core.database.entity.TransactionEntity
import com.smartspend.app.core.model.ReviewStatus
import com.smartspend.app.core.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TransactionsUiState(
    val selectedTab: Int = 0, // 0 = Semua, 1 = Perlu Dicek
    val filterType: String? = null, // null, EXPENSE, INCOME, TRANSFER
    val searchQuery: String = "",
    val allTransactions: List<TransactionEntity> = emptyList(),
    val needsReviewTransactions: List<TransactionEntity> = emptyList(),
    val categoriesMap: Map<Long, CategoryEntity> = emptyMap(),
    val editingTransaction: TransactionEntity? = null
)

class TransactionsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartSpendDatabase.getInstance(application)
    private val repo = TransactionRepository(application, db)

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                repo.allTransactions,
                repo.getTransactionsByReviewStatus(ReviewStatus.NEEDS_REVIEW.name),
                db.categoryDao().getAllCategoriesFlow()
            ) { all, review, categories ->
                val catMap = categories.associateBy { it.id }
                _uiState.value.copy(
                    allTransactions = all,
                    needsReviewTransactions = review,
                    categoriesMap = catMap
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun setSelectedTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun setFilterType(type: String?) {
        _uiState.value = _uiState.value.copy(filterType = type)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun confirmReviewTransaction(id: Long) {
        viewModelScope.launch {
            val tx = db.transactionDao().getTransactionById(id) ?: return@launch
            repo.updateTransaction(tx.copy(reviewStatus = ReviewStatus.CONFIRMED.name))
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

    fun setEditingTransaction(tx: TransactionEntity?) {
        _uiState.value = _uiState.value.copy(editingTransaction = tx)
    }

    fun updateTransaction(updatedTx: TransactionEntity) {
        viewModelScope.launch {
            repo.updateTransaction(updatedTx)
            _uiState.value = _uiState.value.copy(editingTransaction = null)
        }
    }
}
