package com.smartspend.app.ui.screens.budget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.app.core.database.SmartSpendDatabase
import com.smartspend.app.core.database.entity.BudgetEntity
import com.smartspend.app.core.database.entity.CategoryEntity
import com.smartspend.app.core.database.entity.TransactionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CategoryBudgetItem(
    val category: CategoryEntity,
    val limit: Long,
    val spent: Long,
    val depth: Int
) {
    val remaining: Long get() = (limit - spent).coerceAtLeast(0L)
    val progress: Float
        get() = if (limit > 0L) (spent.toFloat() / limit.toFloat()).coerceIn(0f, 1f) else 0f
    val isOver: Boolean get() = limit > 0L && spent > limit
}

data class BudgetUiState(
    val monthLabel: String = "",
    val totalBudget: Long = DEFAULT_MONTHLY_BUDGET,
    val totalSpent: Long = 0L,
    val totalAllocated: Long = 0L,
    val alertThresholdPct: Int = 80,
    val items: List<CategoryBudgetItem> = emptyList(),
    val allCategories: List<CategoryEntity> = emptyList()
) {
    val unallocated: Long get() = totalBudget - totalAllocated
    val remaining: Long get() = (totalBudget - totalSpent).coerceAtLeast(0L)
    val totalProgress: Float
        get() = if (totalBudget > 0L) (totalSpent.toFloat() / totalBudget.toFloat()).coerceIn(0f, 1f) else 0f

    /** Kategori dengan limit, diurutkan dari pemakaian tertinggi. */
    val allocatedItems: List<CategoryBudgetItem>
        get() = items.filter { it.limit > 0L }.sortedByDescending { it.spent }

    companion object {
        const val DEFAULT_MONTHLY_BUDGET = 3_000_000L
    }
}

class BudgetViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SmartSpendDatabase.getInstance(application)

    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val monthLabelFormat = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
    private val currentMonth: String = monthFormat.format(Date())

    private val _uiState = MutableStateFlow(
        BudgetUiState(
            monthLabel = monthLabelFormat.format(Date()),
            totalBudget = BudgetUiState.DEFAULT_MONTHLY_BUDGET
        )
    )
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        val (start, end) = monthBounds()
        viewModelScope.launch {
            combine(
                db.categoryDao().getActiveCategoriesFlow(),
                db.budgetDao().getBudgetsForMonthFlow(currentMonth),
                db.transactionDao().getTransactionsBetweenFlow(start, end)
            ) { categories, budgets, transactions ->
                buildState(categories, budgets, transactions)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun buildState(
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        transactions: List<TransactionEntity>
    ): BudgetUiState {
        val expenses = transactions.filter { it.type == "EXPENSE" && !it.excludeFromReports }
        val totalSpent = expenses.sumOf { it.amount + it.fee }

        val spentByCategory = expenses
            .filter { it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { entry -> entry.value.sumOf { it.amount + it.fee } }

        val limitByCategory = budgets
            .filter { it.categoryId != null }
            .associate { it.categoryId!! to it.limitAmount }

        val overall = budgets.firstOrNull { it.categoryId == null }
        val totalBudget = overall?.limitAmount ?: BudgetUiState.DEFAULT_MONTHLY_BUDGET

        val expenseCategories = categories.filter { it.kind == "EXPENSE" }
        val roots = expenseCategories.filter { it.parentId == null }.sortedBy { it.sortOrder }
        val childrenByParent = expenseCategories
            .filter { it.parentId != null }
            .groupBy { it.parentId!! }

        val ordered = mutableListOf<CategoryBudgetItem>()
        for (root in roots) {
            val children = childrenByParent[root.id].orEmpty().sortedBy { it.sortOrder }
            val childSpent = children.sumOf { spentByCategory[it.id] ?: 0L }
            ordered.add(
                CategoryBudgetItem(
                    category = root,
                    limit = limitByCategory[root.id] ?: 0L,
                    spent = (spentByCategory[root.id] ?: 0L) + childSpent,
                    depth = 0
                )
            )
            for (child in children) {
                ordered.add(
                    CategoryBudgetItem(
                        category = child,
                        limit = limitByCategory[child.id] ?: 0L,
                        spent = spentByCategory[child.id] ?: 0L,
                        depth = 1
                    )
                )
            }
        }

        // Kategori yatim (parent sudah diarsipkan) tetap ditampilkan agar limitnya bisa diubah.
        val shownIds = ordered.map { it.category.id }.toSet()
        expenseCategories
            .filter { it.id !in shownIds }
            .sortedBy { it.name }
            .forEach { orphan ->
                ordered.add(
                    CategoryBudgetItem(
                        category = orphan,
                        limit = limitByCategory[orphan.id] ?: 0L,
                        spent = spentByCategory[orphan.id] ?: 0L,
                        depth = 0
                    )
                )
            }

        val totalAllocated = ordered.sumOf { it.limit }

        return BudgetUiState(
            monthLabel = monthLabelFormat.format(Date()),
            totalBudget = totalBudget,
            totalSpent = totalSpent,
            totalAllocated = totalAllocated,
            alertThresholdPct = overall?.alertThresholdPct ?: 80,
            items = ordered,
            allCategories = categories
        )
    }

    fun setCategoryLimit(categoryId: Long, limit: Long) {
        viewModelScope.launch {
            val existing = db.budgetDao().getBudgetForCategory(currentMonth, categoryId)
            if (limit <= 0L) {
                if (existing != null) {
                    db.budgetDao().deleteCategoryBudget(currentMonth, categoryId)
                }
            } else {
                db.budgetDao().insert(
                    BudgetEntity(
                        id = existing?.id ?: 0L,
                        periodMonth = currentMonth,
                        categoryId = categoryId,
                        limitAmount = limit,
                        alertThresholdPct = existing?.alertThresholdPct ?: 80,
                        lastAlertLevel = existing?.lastAlertLevel ?: 0,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun setTotalBudget(limit: Long, thresholdPct: Int) {
        viewModelScope.launch {
            val existing = db.budgetDao().getTotalBudgetForMonth(currentMonth)
            if (existing != null) {
                db.budgetDao().update(
                    existing.copy(
                        limitAmount = limit,
                        alertThresholdPct = thresholdPct.coerceIn(1, 100),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                db.budgetDao().insert(
                    BudgetEntity(
                        periodMonth = currentMonth,
                        categoryId = null,
                        limitAmount = limit,
                        alertThresholdPct = thresholdPct.coerceIn(1, 100)
                    )
                )
            }
        }
    }

    fun addCategory(name: String, kind: String, colorHex: String, parentId: Long?) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val order = db.categoryDao().maxSortOrder() + 1
            db.categoryDao().insert(
                CategoryEntity(
                    parentId = parentId,
                    name = cleanName,
                    kind = kind,
                    icon = "label",
                    colorHex = colorHex,
                    isSystem = false,
                    sortOrder = order
                )
            )
        }
    }

    fun updateCategory(category: CategoryEntity, name: String, colorHex: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            db.categoryDao().update(
                category.copy(name = cleanName, colorHex = colorHex)
            )
        }
    }

    fun archiveCategory(category: CategoryEntity) {
        viewModelScope.launch {
            db.categoryDao().archive(category.id)
            db.categoryDao().archiveChildren(category.id)
            db.budgetDao().deleteCategoryBudget(currentMonth, category.id)
        }
    }

    private fun monthBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return Pair(start, end)
    }
}
