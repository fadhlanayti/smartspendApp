package com.smartspend.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.smartspend.app.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE is_archived = 0 ORDER BY sort_order ASC, id ASC")
    fun getActiveAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY is_archived ASC, sort_order ASC, id ASC")
    fun getAllAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE package_name = :pkg AND (:hint IS NULL OR account_hint = :hint) AND is_archived = 0 LIMIT 1")
    suspend fun findAccountByPackageAndHint(pkg: String, hint: String?): AccountEntity?

    @Query("SELECT * FROM accounts WHERE provider_code = :providerCode AND is_archived = 0 LIMIT 1")
    suspend fun findAccountByProviderCode(providerCode: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET current_balance = :balance, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateBalance(id: Long, balance: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE accounts SET is_archived = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun archiveAccount(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("SELECT * FROM accounts")
    suspend fun getAllAccountsList(): List<AccountEntity>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE is_archived = 0 ORDER BY sort_order ASC, name ASC")
    fun getActiveCategoriesFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sort_order ASC, name ASC")
    fun getAllCategoriesFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE parent_id IS NULL AND is_archived = 0 ORDER BY sort_order ASC")
    fun getParentCategoriesFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE parent_id = :parentId AND is_archived = 0")
    fun getSubCategoriesFlow(parentId: Long): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Query("UPDATE categories SET is_archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE categories SET is_archived = 1 WHERE parent_id = :parentId")
    suspend fun archiveChildren(parentId: Long)

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM categories")
    suspend fun maxSortOrder(): Int

    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesList(): List<CategoryEntity>
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE is_deleted = 0 ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactionsFlow(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE review_status = :status AND is_deleted = 0 ORDER BY timestamp DESC")
    fun getTransactionsByReviewStatusFlow(status: String): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE review_status = 'NEEDS_REVIEW' AND is_deleted = 0")
    fun getNeedsReviewCountFlow(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 AND timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun getTransactionsBetweenFlow(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 AND timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getTransactionsBetween(start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE type = 'EXPENSE' AND is_deleted = 0 AND exclude_from_reports = 0 AND timestamp BETWEEN :start AND :end")
    suspend fun getExpenseTransactionsBetween(start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE (source_account_id = :accountId OR destination_account_id = :accountId) AND is_deleted = 0 ORDER BY timestamp DESC")
    fun getTransactionsByAccountFlow(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE hash_dedup = :hash AND timestamp BETWEEN :startWindow AND :endWindow AND is_deleted = 0 LIMIT 1")
    suspend fun findDuplicate(hash: String, startWindow: Long, endWindow: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE capture_source = 'MANUAL' AND amount = :amount AND source_account_id = :accountId AND timestamp BETWEEN :startWindow AND :endWindow AND is_deleted = 0 LIMIT 1")
    suspend fun findRecentManualMatch(amount: Long, accountId: Long, startWindow: Long, endWindow: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("UPDATE transactions SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET is_deleted = 0, updated_at = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsList(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)
}

@Dao
interface NotificationRuleDao {
    @Query("SELECT * FROM notification_rules ORDER BY priority ASC, id ASC")
    fun getAllRulesFlow(): Flow<List<NotificationRuleEntity>>

    @Query("SELECT * FROM notification_rules WHERE package_name = :pkg AND is_enabled = 1 ORDER BY priority ASC")
    suspend fun getEnabledRulesForPackage(pkg: String): List<NotificationRuleEntity>

    @Query("SELECT DISTINCT package_name FROM notification_rules WHERE is_enabled = 1")
    suspend fun getWhitelistedPackages(): List<String>

    @Query("SELECT * FROM notification_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: Long): NotificationRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: NotificationRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<NotificationRuleEntity>)

    @Update
    suspend fun update(rule: NotificationRuleEntity)

    @Query("UPDATE notification_rules SET match_count = match_count + 1 WHERE id = :id")
    suspend fun incrementMatch(id: Long)

    @Query("UPDATE notification_rules SET fail_count = fail_count + 1 WHERE id = :id")
    suspend fun incrementFail(id: Long)

    @Query("DELETE FROM notification_rules WHERE id = :id AND is_builtin = 0")
    suspend fun deleteCustomRule(id: Long)

    @Query("SELECT * FROM notification_rules")
    suspend fun getAllRulesList(): List<NotificationRuleEntity>
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE period_month = :periodMonth")
    fun getBudgetsForMonthFlow(periodMonth: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE period_month = :periodMonth AND category_id IS NULL LIMIT 1")
    suspend fun getTotalBudgetForMonth(periodMonth: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE period_month = :periodMonth AND category_id = :categoryId LIMIT 1")
    suspend fun getBudgetForCategory(periodMonth: String, categoryId: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE period_month = :periodMonth AND category_id IS NOT NULL")
    suspend fun getCategoryBudgets(periodMonth: String): List<BudgetEntity>

    @Query("DELETE FROM budgets WHERE period_month = :periodMonth AND category_id = :categoryId")
    suspend fun deleteCategoryBudget(periodMonth: String, categoryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgetsList(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)
}

@Dao
interface RawEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rawEvent: RawEventEntity): Long

    @Query("UPDATE raw_events SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM raw_events WHERE created_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT * FROM raw_events ORDER BY id DESC LIMIT :limit")
    fun getRecentRawEventsFlow(limit: Int = 50): Flow<List<RawEventEntity>>

    @Query("DELETE FROM raw_events")
    suspend fun clearAll()
}

@Dao
interface MerchantCategoryMapDao {
    @Query("SELECT * FROM merchant_category_map WHERE merchant_key = :merchantKey LIMIT 1")
    suspend fun getMapping(merchantKey: String): MerchantCategoryMapEntity?

    @Upsert
    suspend fun upsert(mapping: MerchantCategoryMapEntity)
}

@Dao
interface DailySummaryDao {
    @Query("SELECT * FROM daily_summary WHERE day BETWEEN :startDay AND :endDay ORDER BY day ASC")
    fun getDailySummariesBetweenFlow(startDay: String, endDay: String): Flow<List<DailySummaryEntity>>

    @Upsert
    suspend fun upsert(summary: DailySummaryEntity)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts WHERE is_deleted = 0 ORDER BY CASE WHEN due_date IS NULL THEN 1 ELSE 0 END, due_date ASC, created_at DESC")
    fun getAllDebtsFlow(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE direction = :direction AND status = 'ACTIVE' AND is_deleted = 0 ORDER BY CASE WHEN due_date IS NULL THEN 1 ELSE 0 END, due_date ASC")
    fun getActiveDebtsFlow(direction: String): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getDebtById(id: Long): DebtEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(debt: DebtEntity): Long

    @Update
    suspend fun update(debt: DebtEntity)

    @Query("UPDATE debts SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM debts")
    suspend fun getAllDebtsList(): List<DebtEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(debts: List<DebtEntity>)
}

@Dao
interface DebtPaymentDao {
    @Query("SELECT * FROM debt_payments WHERE debt_id = :debtId ORDER BY paid_at DESC")
    fun getPaymentsForDebtFlow(debtId: Long): Flow<List<DebtPaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: DebtPaymentEntity): Long
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE is_enabled = 1 AND next_fire_at IS NOT NULL AND next_fire_at <= :time")
    suspend fun getDueReminders(time: Long): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("UPDATE reminders SET is_enabled = 0 WHERE debt_id = :debtId")
    suspend fun disableForDebt(debtId: Long)

    @Query("UPDATE reminders SET is_enabled = 0 WHERE plan_id = :planId")
    suspend fun disableForPlan(planId: Long)
}

@Dao
interface PurchasePlanDao {
    @Query("SELECT * FROM purchase_plans ORDER BY created_at DESC")
    fun getAllPlansFlow(): Flow<List<PurchasePlanEntity>>

    @Query("SELECT * FROM purchase_plans WHERE status = :status ORDER BY decide_at ASC")
    fun getPlansByStatusFlow(status: String): Flow<List<PurchasePlanEntity>>

    @Query("SELECT * FROM purchase_plans WHERE id = :id LIMIT 1")
    suspend fun getPlanById(id: Long): PurchasePlanEntity?

    @Query("SELECT * FROM purchase_plans WHERE status = 'WAITING' AND decide_at <= :currentTime")
    suspend fun getPlansReadyForDecision(currentTime: Long): List<PurchasePlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: PurchasePlanEntity): Long

    @Update
    suspend fun update(plan: PurchasePlanEntity)

    @Query("SELECT SUM(estimated_price) FROM purchase_plans WHERE status = 'SKIPPED' AND created_at BETWEEN :startOfMonth AND :endOfMonth")
    suspend fun getMoneySavedThisMonth(startOfMonth: Long, endOfMonth: Long): Long?

    @Query("SELECT * FROM purchase_plans")
    suspend fun getAllPlansList(): List<PurchasePlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<PurchasePlanEntity>)
}
