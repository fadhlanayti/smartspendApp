package com.smartspend.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartspend.app.core.database.dao.*
import com.smartspend.app.core.database.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        NotificationRuleEntity::class,
        BudgetEntity::class,
        RawEventEntity::class,
        MerchantCategoryMapEntity::class,
        DailySummaryEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class,
        ReminderEntity::class,
        PurchasePlanEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SmartSpendDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun notificationRuleDao(): NotificationRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun rawEventDao(): RawEventDao
    abstract fun merchantCategoryMapDao(): MerchantCategoryMapDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun debtDao(): DebtDao
    abstract fun debtPaymentDao(): DebtPaymentDao
    abstract fun reminderDao(): ReminderDao
    abstract fun purchasePlanDao(): PurchasePlanDao

    companion object {
        @Volatile
        private var INSTANCE: SmartSpendDatabase? = null

        fun getInstance(context: Context): SmartSpendDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): SmartSpendDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                SmartSpendDatabase::class.java,
                "smartspend.db"
            ).addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        seedDatabase(getInstance(context))
                    }
                }
            }).build()
        }

        private suspend fun seedDatabase(database: SmartSpendDatabase) {
            seedAccounts(database.accountDao())
            seedCategories(database.categoryDao())
            seedRules(database.notificationRuleDao())
        }

        private suspend fun seedAccounts(accountDao: AccountDao) {
            val accounts = listOf(
                AccountEntity(
                    id = 1,
                    name = "Tunai (Dompet)",
                    type = "CASH",
                    initialBalance = 150_000,
                    currentBalance = 150_000,
                    colorHex = "#30B350",
                    icon = "payments"
                ),
                AccountEntity(
                    id = 2,
                    name = "BCA Utama",
                    type = "BANK",
                    providerCode = "BCA",
                    packageName = "com.bca.mybca.omni.android",
                    initialBalance = 3_500_000,
                    currentBalance = 3_500_000,
                    colorHex = "#0A84FF",
                    icon = "account_balance"
                ),
                AccountEntity(
                    id = 3,
                    name = "DANA",
                    type = "EWALLET",
                    providerCode = "DANA",
                    packageName = "id.dana",
                    initialBalance = 250_000,
                    currentBalance = 250_000,
                    colorHex = "#118EEA",
                    icon = "account_balance_wallet"
                ),
                AccountEntity(
                    id = 4,
                    name = "GoPay",
                    type = "EWALLET",
                    providerCode = "GOPAY",
                    packageName = "com.gojek.app",
                    initialBalance = 120_000,
                    currentBalance = 120_000,
                    colorHex = "#00AED6",
                    icon = "wallet"
                )
            )
            accountDao.insertAll(accounts)
        }

        private suspend fun seedCategories(categoryDao: CategoryDao) {
            val categories = listOf(
                // Parent categories
                CategoryEntity(id = 1, parentId = null, name = "Makanan & Minuman", kind = "EXPENSE", icon = "restaurant", colorHex = "#FF9500", isSystem = true, sortOrder = 1),
                CategoryEntity(id = 2, parentId = null, name = "Transportasi", kind = "EXPENSE", icon = "directions_car", colorHex = "#0A84FF", isSystem = true, sortOrder = 2),
                CategoryEntity(id = 3, parentId = null, name = "Belanja", kind = "EXPENSE", icon = "shopping_bag", colorHex = "#AF52DE", isSystem = true, sortOrder = 3),
                CategoryEntity(id = 4, parentId = null, name = "Tagihan & Utilitas", kind = "EXPENSE", icon = "receipt_long", colorHex = "#FF3B30", isSystem = true, sortOrder = 4),
                CategoryEntity(id = 5, parentId = null, name = "Kesehatan", kind = "EXPENSE", icon = "medical_services", colorHex = "#34C759", isSystem = true, sortOrder = 5),
                CategoryEntity(id = 6, parentId = null, name = "Hiburan", kind = "EXPENSE", icon = "movie", colorHex = "#5856D6", isSystem = true, sortOrder = 6),
                CategoryEntity(id = 7, parentId = null, name = "Pendidikan", kind = "EXPENSE", icon = "school", colorHex = "#5AC8FA", isSystem = true, sortOrder = 7),
                CategoryEntity(id = 8, parentId = null, name = "Lain-lain", kind = "EXPENSE", icon = "more_horiz", colorHex = "#8E8E93", isSystem = true, sortOrder = 8),
                CategoryEntity(id = 9, parentId = null, name = "Gaji & Pendapatan", kind = "INCOME", icon = "payments", colorHex = "#30B350", isSystem = true, sortOrder = 9),
                CategoryEntity(id = 10, parentId = null, name = "Transfer", kind = "TRANSFER", icon = "sync_alt", colorHex = "#8E8E93", isSystem = true, sortOrder = 10),

                // Sub-categories for Makanan & Minuman
                CategoryEntity(id = 11, parentId = 1, name = "Makan di luar", kind = "EXPENSE", icon = "restaurant", colorHex = "#FF9500", isSystem = true),
                CategoryEntity(id = 12, parentId = 1, name = "Kopi & jajan", kind = "EXPENSE", icon = "coffee", colorHex = "#FF9500", isSystem = true),
                CategoryEntity(id = 13, parentId = 1, name = "Belanja bahan makanan", kind = "EXPENSE", icon = "shopping_cart", colorHex = "#FF9500", isSystem = true),
                CategoryEntity(id = 14, parentId = 1, name = "Pesan antar", kind = "EXPENSE", icon = "delivery_dining", colorHex = "#FF9500", isSystem = true),

                // Sub-categories for Transportasi
                CategoryEntity(id = 15, parentId = 2, name = "Ojek online", kind = "EXPENSE", icon = "two_wheeler", colorHex = "#0A84FF", isSystem = true),
                CategoryEntity(id = 16, parentId = 2, name = "BBM", kind = "EXPENSE", icon = "local_gas_station", colorHex = "#0A84FF", isSystem = true),
                CategoryEntity(id = 17, parentId = 2, name = "Parkir & tol", kind = "EXPENSE", icon = "local_parking", colorHex = "#0A84FF", isSystem = true),
                CategoryEntity(id = 18, parentId = 2, name = "Transportasi umum", kind = "EXPENSE", icon = "directions_bus", colorHex = "#0A84FF", isSystem = true),

                // Sub-categories for Belanja
                CategoryEntity(id = 19, parentId = 3, name = "Marketplace", kind = "EXPENSE", icon = "storefront", colorHex = "#AF52DE", isSystem = true),
                CategoryEntity(id = 20, parentId = 3, name = "Pakaian", kind = "EXPENSE", icon = "checkroom", colorHex = "#AF52DE", isSystem = true),
                CategoryEntity(id = 21, parentId = 3, name = "Elektronik", kind = "EXPENSE", icon = "devices", colorHex = "#AF52DE", isSystem = true),
                CategoryEntity(id = 22, parentId = 3, name = "Kebutuhan rumah", kind = "EXPENSE", icon = "home", colorHex = "#AF52DE", isSystem = true),

                // Sub-categories for Tagihan
                CategoryEntity(id = 23, parentId = 4, name = "Listrik", kind = "EXPENSE", icon = "bolt", colorHex = "#FF3B30", isSystem = true),
                CategoryEntity(id = 24, parentId = 4, name = "Air", kind = "EXPENSE", icon = "water_drop", colorHex = "#FF3B30", isSystem = true),
                CategoryEntity(id = 25, parentId = 4, name = "Internet & pulsa", kind = "EXPENSE", icon = "wifi", colorHex = "#FF3B30", isSystem = true),
                CategoryEntity(id = 26, parentId = 4, name = "Langganan digital", kind = "EXPENSE", icon = "subscriptions", colorHex = "#FF3B30", isSystem = true),

                // Sub-categories for Gaji & Pendapatan
                CategoryEntity(id = 27, parentId = 9, name = "Gaji", kind = "INCOME", icon = "work", colorHex = "#30B350", isSystem = true),
                CategoryEntity(id = 28, parentId = 9, name = "Freelance", kind = "INCOME", icon = "laptop", colorHex = "#30B350", isSystem = true),
                CategoryEntity(id = 29, parentId = 9, name = "Bonus", kind = "INCOME", icon = "card_giftcard", colorHex = "#30B350", isSystem = true),
                CategoryEntity(id = 30, parentId = 9, name = "Pemasukan lain", kind = "INCOME", icon = "add_circle", colorHex = "#30B350", isSystem = true)
            )
            categoryDao.insertAll(categories)
        }

        private suspend fun seedRules(ruleDao: NotificationRuleDao) {
            val rules = listOf(
                NotificationRuleEntity(
                    id = 1,
                    providerCode = "BCA",
                    packageName = "com.bca.mybca.omni.android",
                    ruleName = "BCA - Transfer keluar",
                    bodyPattern = """(?i)transfer\s+rp\.?\s?(?<amt>[\d.,]+)\s+ke\s+(?<mrc>.+?)\s+berhasil""",
                    txType = "EXPENSE",
                    groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                    priority = 10
                ),
                NotificationRuleEntity(
                    id = 2,
                    providerCode = "BCA",
                    packageName = "com.bca.mybca.omni.android",
                    ruleName = "BCA - Pembayaran QRIS",
                    bodyPattern = """(?i)pembayaran\s+qris\s+rp\.?\s?(?<amt>[\d.,]+)\s+di\s+(?<mrc>.+?)\s+berhasil""",
                    txType = "EXPENSE",
                    groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                    priority = 20
                ),
                NotificationRuleEntity(
                    id = 3,
                    providerCode = "BCA",
                    packageName = "com.bca.mybca.omni.android",
                    ruleName = "BCA - Dana masuk",
                    bodyPattern = """(?i)menerima\s+rp\.?\s?(?<amt>[\d.,]+)\s+dari\s+(?<mrc>.+?)(?:\.|$|\s+saldo)""",
                    txType = "INCOME",
                    groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                    priority = 30
                ),
                NotificationRuleEntity(
                    id = 4,
                    providerCode = "DANA",
                    packageName = "id.dana",
                    ruleName = "DANA - Pembayaran merchant",
                    bodyPattern = """(?i)membayar\s+rp\.?\s?(?<amt>[\d.,]+)\s+ke\s+(?<mrc>.+)""",
                    txType = "EXPENSE",
                    groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                    priority = 10
                ),
                NotificationRuleEntity(
                    id = 5,
                    providerCode = "DANA",
                    packageName = "id.dana",
                    ruleName = "DANA - Uang masuk",
                    bodyPattern = """(?i)menerima\s+rp\.?\s?(?<amt>[\d.,]+)\s+dari\s+(?<mrc>.+)""",
                    txType = "INCOME",
                    groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                    priority = 20
                ),
                NotificationRuleEntity(
                    id = 6,
                    providerCode = "DANA",
                    packageName = "id.dana",
                    ruleName = "DANA - Top up berhasil",
                    bodyPattern = """(?i)top\s+up\s+dana\s+sebesar\s+rp\.?\s?(?<amt>[\d.,]+)\s+berhasil""",
                    txType = "INCOME",
                    groupMapJson = """{"amount":"amt"}""",
                    priority = 30
                ),
                NotificationRuleEntity(
                    id = 7,
                    providerCode = "GOPAY",
                    packageName = "com.gojek.app",
                    ruleName = "GoPay - Pembayaran",
                    bodyPattern = """(?i)(?:kamu\s+bayar|pembayaran\s+gopay)\s+rp\.?\s?(?<amt>[\d.,]+)(?:\s+(?:ke|pakai\s+gopay\s+untuk)\s+(?<mrc>.+?))?(?:\s+berhasil)?$""",
                    txType = "EXPENSE",
                    groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                    priority = 10
                ),
                NotificationRuleEntity(
                    id = 8,
                    providerCode = "GOPAY",
                    packageName = "com.gojek.app",
                    ruleName = "GoPay - Dana masuk",
                    bodyPattern = """(?i)menerima\s+rp\.?\s?(?<amt>[\d.,]+)\s+dari\s+(?<mrc>.+)""",
                    txType = "INCOME",
                    groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                    priority = 20
                )
            )
            ruleDao.insertAll(rules)
        }
    }
}
