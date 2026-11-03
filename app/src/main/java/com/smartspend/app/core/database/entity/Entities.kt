package com.smartspend.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["package_name", "account_hint"], name = "idx_accounts_pkg")
    ]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = "OTHER", // BANK, EWALLET, CASH, CARD, OTHER
    @ColumnInfo(name = "provider_code") val providerCode: String? = null,
    @ColumnInfo(name = "package_name") val packageName: String? = null,
    @ColumnInfo(name = "account_hint") val accountHint: String? = null,
    @ColumnInfo(name = "initial_balance") val initialBalance: Long = 0L,
    @ColumnInfo(name = "current_balance") val currentBalance: Long = 0L,
    @ColumnInfo(name = "color_hex") val colorHex: String = "#0A84FF",
    val icon: String = "wallet",
    @ColumnInfo(name = "include_in_total") val includeInTotal: Boolean = true,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["parent_id"], name = "idx_categories_parent"),
        Index(value = ["parent_id", "name", "kind"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    val name: String,
    val kind: String = "EXPENSE", // EXPENSE, INCOME, TRANSFER
    val icon: String = "tag",
    @ColumnInfo(name = "color_hex") val colorHex: String = "#8E8E93",
    @ColumnInfo(name = "is_system") val isSystem: Boolean = false,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["type", "is_deleted", "timestamp"], name = "idx_tx_type_time"),
        Index(value = ["timestamp", "is_deleted"], name = "idx_tx_time"),
        Index(value = ["category_id", "timestamp"], name = "idx_tx_cat_time"),
        Index(value = ["category_id"]),
        Index(value = ["source_account_id", "timestamp"], name = "idx_tx_src"),
        Index(value = ["destination_account_id", "timestamp"], name = "idx_tx_dst"),
        Index(value = ["hash_dedup", "timestamp"], name = "idx_tx_hash"),
        Index(value = ["review_status"], name = "idx_tx_review"),
        Index(value = ["debt_id"], name = "idx_tx_debt")
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["destination_account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // EXPENSE, INCOME, TRANSFER
    val amount: Long,
    val fee: Long = 0L,
    @ColumnInfo(name = "source_account_id") val sourceAccountId: Long? = null,
    @ColumnInfo(name = "destination_account_id") val destinationAccountId: Long? = null,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    val merchant: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "raw_notification") val rawNotification: String? = null,
    @ColumnInfo(name = "source_package") val sourcePackage: String? = null,
    @ColumnInfo(name = "hash_dedup") val hashDedup: String? = null,
    @ColumnInfo(name = "capture_source") val captureSource: String = "MANUAL", // AUTO, MANUAL
    @ColumnInfo(name = "review_status") val reviewStatus: String = "CONFIRMED", // CONFIRMED, NEEDS_REVIEW
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "debt_id") val debtId: Long? = null,
    @ColumnInfo(name = "plan_id") val planId: Long? = null,
    @ColumnInfo(name = "exclude_from_reports") val excludeFromReports: Boolean = false
)

@Entity(
    tableName = "notification_rules",
    indices = [
        Index(value = ["package_name", "is_enabled", "priority"], name = "idx_rules_pkg")
    ]
)
data class NotificationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "provider_code") val providerCode: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "rule_name") val ruleName: String,
    @ColumnInfo(name = "title_pattern") val titlePattern: String? = null,
    @ColumnInfo(name = "body_pattern") val bodyPattern: String,
    @ColumnInfo(name = "tx_type") val txType: String, // EXPENSE, INCOME, TRANSFER
    @ColumnInfo(name = "group_map_json") val groupMapJson: String = "{}",
    @ColumnInfo(name = "amount_format") val amountFormat: String = "AUTO",
    val priority: Int = 100,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = true,
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean = true,
    val version: Int = 1,
    @ColumnInfo(name = "match_count") val matchCount: Int = 0,
    @ColumnInfo(name = "fail_count") val failCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["period_month", "category_id"], unique = true),
        Index(value = ["period_month"], name = "idx_budgets_period")
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "period_month") val periodMonth: String, // 'YYYY-MM'
    @ColumnInfo(name = "category_id") val categoryId: Long? = null, // null = overall monthly budget
    @ColumnInfo(name = "limit_amount") val limitAmount: Long,
    @ColumnInfo(name = "alert_threshold_pct") val alertThresholdPct: Int = 80,
    val rollover: Boolean = false,
    @ColumnInfo(name = "last_alert_level") val lastAlertLevel: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "raw_events")
data class RawEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    val title: String? = null,
    val text: String? = null,
    @ColumnInfo(name = "post_time") val postTime: Long,
    val status: String = "PENDING", // PENDING, PARSED, FAILED
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "merchant_category_map")
data class MerchantCategoryMapEntity(
    @PrimaryKey @ColumnInfo(name = "merchant_key") val merchantKey: String,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "hit_count") val hitCount: Int = 1
)

@Entity(
    tableName = "daily_summary",
    primaryKeys = ["day", "account_id"]
)
data class DailySummaryEntity(
    val day: String, // 'YYYY-MM-DD'
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "expense_total") val expenseTotal: Long = 0L,
    @ColumnInfo(name = "income_total") val incomeTotal: Long = 0L,
    @ColumnInfo(name = "tx_count") val txCount: Int = 0
)

@Entity(
    tableName = "debts",
    indices = [
        Index(value = ["direction", "status", "due_date"], name = "idx_debts_dir_status"),
        Index(value = ["account_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: String, // OWE, OWED
    @ColumnInfo(name = "counterparty_name") val counterpartyName: String,
    @ColumnInfo(name = "principal_amount") val principalAmount: Long,
    @ColumnInfo(name = "paid_amount") val paidAmount: Long = 0L,
    val status: String = "ACTIVE", // ACTIVE, PAID, CANCELLED
    @ColumnInfo(name = "start_date") val startDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "due_date") val dueDate: Long? = null,
    @ColumnInfo(name = "installment_amount") val installmentAmount: Long? = null,
    @ColumnInfo(name = "installment_period") val installmentPeriod: String? = null, // WEEKLY, MONTHLY
    @ColumnInfo(name = "account_id") val accountId: Long? = null,
    @ColumnInfo(name = "record_to_balance") val recordToBalance: Boolean = true,
    val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false
)

@Entity(
    tableName = "debt_payments",
    indices = [
        Index(value = ["debt_id", "paid_at"], name = "idx_payments_debt"),
        Index(value = ["transaction_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debt_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class DebtPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "debt_id") val debtId: Long,
    val amount: Long,
    @ColumnInfo(name = "paid_at") val paidAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "transaction_id") val transactionId: Long? = null,
    val note: String? = null
)

@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["is_enabled", "next_fire_at"], name = "idx_reminders_next"),
        Index(value = ["debt_id"]),
        Index(value = ["plan_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debt_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PurchasePlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "debt_id") val debtId: Long? = null,
    @ColumnInfo(name = "plan_id") val planId: Long? = null,
    @ColumnInfo(name = "offset_days") val offsetDays: Int = 0,
    @ColumnInfo(name = "time_of_day_min") val timeOfDayMin: Int = 540, // 09:00
    @ColumnInfo(name = "repeat_every_days") val repeatEveryDays: Int? = null,
    @ColumnInfo(name = "max_repeats") val maxRepeats: Int = 0,
    @ColumnInfo(name = "fired_count") val firedCount: Int = 0,
    @ColumnInfo(name = "next_fire_at") val nextFireAt: Long? = null,
    @ColumnInfo(name = "snooze_until") val snoozeUntil: Long? = null,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = true
)

@Entity(
    tableName = "purchase_plans",
    indices = [
        Index(value = ["status", "decide_at"], name = "idx_plans_status_decide"),
        Index(value = ["category_id"]),
        Index(value = ["bought_transaction_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["bought_transaction_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class PurchasePlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "estimated_price") val estimatedPrice: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "need_level") val needLevel: String = "WANT", // NEED, WANT
    val reason: String? = null,
    @ColumnInfo(name = "link_or_note") val linkOrNote: String? = null,
    @ColumnInfo(name = "cooling_off_days") val coolingOffDays: Int = 3,
    @ColumnInfo(name = "decide_at") val decideAt: Long,
    @ColumnInfo(name = "postpone_count") val postponeCount: Int = 0,
    val status: String = "WAITING", // WAITING, READY, BOUGHT, SKIPPED, EXPIRED
    @ColumnInfo(name = "reflection_json") val reflectionJson: String? = null,
    @ColumnInfo(name = "bought_before_ready") val boughtBeforeReady: Boolean = false,
    @ColumnInfo(name = "bought_transaction_id") val boughtTransactionId: Long? = null,
    @ColumnInfo(name = "decided_at") val decidedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
