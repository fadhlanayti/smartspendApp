package com.smartspend.app.core.repository

import android.content.Context
import com.smartspend.app.core.budget.BudgetEvaluator
import com.smartspend.app.core.database.SmartSpendDatabase
import com.smartspend.app.core.database.entity.DailySummaryEntity
import com.smartspend.app.core.database.entity.TransactionEntity
import com.smartspend.app.core.model.TransactionType
import com.smartspend.app.core.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionRepository(
    private val context: Context,
    private val database: SmartSpendDatabase
) {
    private val transactionDao = database.transactionDao()
    private val accountDao = database.accountDao()
    private val dailySummaryDao = database.dailySummaryDao()
    private val budgetEvaluator = BudgetEvaluator(context, database.budgetDao(), transactionDao)

    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactionsFlow()
    val needsReviewCount: Flow<Int> = transactionDao.getNeedsReviewCountFlow()

    fun getRecentTransactions(limit: Int = 5): Flow<List<TransactionEntity>> =
        transactionDao.getRecentTransactionsFlow(limit)

    fun getTransactionsByReviewStatus(status: String): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsByReviewStatusFlow(status)

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        val id = transactionDao.insert(transaction)
        val inserted = transaction.copy(id = id)

        // 1. Update account balance
        applyBalanceChange(inserted, isReversal = false)

        // 2. Update daily summary if not excluded from reports
        if (!inserted.excludeFromReports) {
            updateDailySummary(inserted, isReversal = false)
        }

        // 3. Evaluate budget
        if (inserted.type == TransactionType.EXPENSE.name && !inserted.excludeFromReports) {
            budgetEvaluator.evaluateMonthlyBudget()
        }

        // 4. Update widget
        WidgetUpdater.notifyDataChanged(context)

        return id
    }

    suspend fun updateTransaction(newTx: TransactionEntity) {
        val oldTx = transactionDao.getTransactionById(newTx.id) ?: return

        // Reverse old balance impact
        applyBalanceChange(oldTx, isReversal = true)
        if (!oldTx.excludeFromReports) {
            updateDailySummary(oldTx, isReversal = true)
        }

        // Apply new balance impact
        transactionDao.update(newTx)
        applyBalanceChange(newTx, isReversal = false)
        if (!newTx.excludeFromReports) {
            updateDailySummary(newTx, isReversal = false)
        }

        if (newTx.type == TransactionType.EXPENSE.name) {
            budgetEvaluator.evaluateMonthlyBudget()
        }

        WidgetUpdater.refreshSnapshot(context)
    }

    suspend fun deleteTransaction(id: Long) {
        val tx = transactionDao.getTransactionById(id) ?: return
        transactionDao.softDelete(id)

        // Reverse balance impact
        applyBalanceChange(tx, isReversal = true)
        if (!tx.excludeFromReports) {
            updateDailySummary(tx, isReversal = true)
        }

        WidgetUpdater.refreshSnapshot(context)
    }

    suspend fun restoreTransaction(id: Long) {
        val tx = transactionDao.getTransactionById(id) ?: return
        transactionDao.restore(id)

        // Re-apply balance impact
        applyBalanceChange(tx, isReversal = false)
        if (!tx.excludeFromReports) {
            updateDailySummary(tx, isReversal = false)
        }

        WidgetUpdater.refreshSnapshot(context)
    }

    private suspend fun applyBalanceChange(tx: TransactionEntity, isReversal: Boolean) {
        val multiplier = if (isReversal) -1 else 1

        when (tx.type) {
            TransactionType.EXPENSE.name -> {
                tx.sourceAccountId?.let { accId ->
                    val acc = accountDao.getAccountById(accId) ?: return@let
                    val delta = (tx.amount + tx.fee) * multiplier
                    accountDao.updateBalance(accId, acc.currentBalance - delta)
                }
            }
            TransactionType.INCOME.name -> {
                tx.destinationAccountId?.let { accId ->
                    val acc = accountDao.getAccountById(accId) ?: return@let
                    val delta = tx.amount * multiplier
                    accountDao.updateBalance(accId, acc.currentBalance + delta)
                }
            }
            TransactionType.TRANSFER.name -> {
                tx.sourceAccountId?.let { srcId ->
                    val srcAcc = accountDao.getAccountById(srcId) ?: return@let
                    val delta = (tx.amount + tx.fee) * multiplier
                    accountDao.updateBalance(srcId, srcAcc.currentBalance - delta)
                }
                tx.destinationAccountId?.let { dstId ->
                    val dstAcc = accountDao.getAccountById(dstId) ?: return@let
                    val delta = tx.amount * multiplier
                    accountDao.updateBalance(dstId, dstAcc.currentBalance + delta)
                }
            }
        }
    }

    private suspend fun updateDailySummary(tx: TransactionEntity, isReversal: Boolean) {
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val day = dayFormat.format(Date(tx.timestamp))
        val accountId = tx.sourceAccountId ?: tx.destinationAccountId ?: 0L

        val multiplier = if (isReversal) -1L else 1L
        val expenseDelta = if (tx.type == TransactionType.EXPENSE.name) (tx.amount + tx.fee) * multiplier else 0L
        val incomeDelta = if (tx.type == TransactionType.INCOME.name) tx.amount * multiplier else 0L
        val countDelta = if (isReversal) -1 else 1

        dailySummaryDao.upsert(
            DailySummaryEntity(
                day = day,
                accountId = accountId,
                expenseTotal = maxOf(0L, expenseDelta),
                incomeTotal = maxOf(0L, incomeDelta),
                txCount = maxOf(0, countDelta)
            )
        )
    }
}
