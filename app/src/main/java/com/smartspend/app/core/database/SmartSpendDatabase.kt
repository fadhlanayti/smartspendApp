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
                INSTANCE ?: buildDatabase(context).also { db ->
                    INSTANCE = db
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching {
                            ensureEssentialRules(db.notificationRuleDao())
                            ensureEssentialAccounts(db.accountDao())
                        }
                    }
                }
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
                    name = "BCA Utama",
                    type = "BANK",
                    providerCode = "BCA",
                    packageName = "com.bca.mybca.omni.android",
                    initialBalance = 3_500_000,
                    currentBalance = 3_500_000,
                    colorHex = "#005EAA",
                    icon = "bca"
                ),
                AccountEntity(
                    id = 2,
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
                    id = 3,
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
                    id = 4,
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
                    id = 5,
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
                    id = 6,
                    name = "DANA",
                    type = "EWALLET",
                    providerCode = "DANA",
                    packageName = "id.dana",
                    initialBalance = 180_000,
                    currentBalance = 180_000,
                    colorHex = "#118EEA",
                    icon = "dana"
                ),
                AccountEntity(
                    id = 7,
                    name = "Tunai (Dompet)",
                    type = "CASH",
                    providerCode = "CASH",
                    initialBalance = 350_000,
                    currentBalance = 350_000,
                    colorHex = "#1E7E34",
                    icon = "cash"
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

        fun getDefaultRules(): List<NotificationRuleEntity> = listOf(
            // BCA Mobile (com.bca)
            NotificationRuleEntity(
                providerCode = "BCA",
                packageName = "com.bca",
                ruleName = "BCA Mobile - Transfer & Bayar",
                bodyPattern = """(?i)(?:transfer\s+ke|transfer\s+keluar|pembayaran|qris|bayar).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:ke|di)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "BCA",
                packageName = "com.bca",
                ruleName = "BCA Mobile - Dana Masuk",
                bodyPattern = """(?i)(?:transfer\s+masuk|menerima|dana\s+masuk).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            // myBCA (com.bca.mybca.omni.android)
            NotificationRuleEntity(
                providerCode = "BCA",
                packageName = "com.bca.mybca.omni.android",
                ruleName = "myBCA - Transfer keluar",
                bodyPattern = """(?i)(?:transfer\s+ke|transfer\s+keluar|pembayaran|qris|bayar).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:ke|di)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "BCA",
                packageName = "com.bca.mybca.omni.android",
                ruleName = "myBCA - Dana masuk",
                bodyPattern = """(?i)(?:transfer\s+masuk|menerima|dana\s+masuk).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            // blu by BCA Digital
            NotificationRuleEntity(
                providerCode = "BCA",
                packageName = "com.bcadigital.blu",
                ruleName = "blu BCA - Pengeluaran & Bayar",
                bodyPattern = """(?i)(?:transfer\s+ke|transfer\s+keluar|pembayaran|qris|bayar).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:ke|di)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "BCA",
                packageName = "com.bcadigital.blu",
                ruleName = "blu BCA - Dana Masuk",
                bodyPattern = """(?i)(?:transfer\s+masuk|menerima|dana\s+masuk).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            // DANA
            NotificationRuleEntity(
                providerCode = "DANA",
                packageName = "id.dana",
                ruleName = "DANA - Pembayaran merchant",
                bodyPattern = """(?i)(?:membayar|bayar\s+ke).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:ke)\s+(?<mrc>.+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "DANA",
                packageName = "id.dana",
                ruleName = "DANA - Uang masuk",
                bodyPattern = """(?i)(?:menerima|top\s+up).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>.+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            // GoPay (Gojek app)
            NotificationRuleEntity(
                providerCode = "GOPAY",
                packageName = "com.gojek.app",
                ruleName = "GoPay - Pembayaran",
                bodyPattern = """(?i)(?:kamu\s+bayar|pembayaran\s+gopay|bayar).*?rp\.?\s?(?<amt>[\d.,]+)(?:\s+(?:ke|pakai\s+gopay\s+untuk)\s+(?<mrc>.+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "GOPAY",
                packageName = "com.gojek.app",
                ruleName = "GoPay - Dana masuk",
                bodyPattern = """(?i)(?:menerima|top\s+up).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>.+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            // GoPay Standalone App
            NotificationRuleEntity(
                providerCode = "GOPAY",
                packageName = "com.gojek.gopay",
                ruleName = "GoPay App - Pembayaran",
                bodyPattern = """(?i)(?:kamu\s+bayar|pembayaran\s+gopay|bayar|transfer).*?rp\.?\s?(?<amt>[\d.,]+)(?:\s+(?:ke)\s+(?<mrc>.+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "GOPAY",
                packageName = "com.gojek.gopay",
                ruleName = "GoPay App - Dana Masuk",
                bodyPattern = """(?i)(?:menerima|top\s+up|transfer\s+masuk).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>.+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            // SeaBank
            NotificationRuleEntity(
                providerCode = "SEABANK",
                packageName = "id.co.bankbkemobile.digitalbank",
                ruleName = "SeaBank - Transfer & Bayar",
                bodyPattern = """(?i)(?:transfer\s+ke|transfer\s+keluar|pembayaran|qris|bayar|senilai).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:ke|di)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "SEABANK",
                packageName = "id.co.bankbkemobile.digitalbank",
                ruleName = "SeaBank - Transfer Masuk",
                bodyPattern = """(?i)(?:transfer\s+masuk|menerima|dana\s+masuk|top\s+up).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            // Livin' Mandiri
            NotificationRuleEntity(
                providerCode = "MANDIRI",
                packageName = "id.bmri.livin",
                ruleName = "Mandiri - Pembayaran & Transfer",
                bodyPattern = """(?i)(?:transaksi|transfer|pembayaran|qris|bayar).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:di|ke)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "MANDIRI",
                packageName = "id.bmri.livin",
                ruleName = "Mandiri - Dana Masuk",
                bodyPattern = """(?i)(?:transfer\s+masuk|dana\s+masuk|menerima).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            // BRImo
            NotificationRuleEntity(
                providerCode = "BRI",
                packageName = "id.co.bri.brimo",
                ruleName = "BRImo - Transaksi & QRIS",
                bodyPattern = """(?i)(?:transaksi|transfer|pembayaran|qris|debit).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:di|ke)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "BRI",
                packageName = "id.co.bri.brimo",
                ruleName = "BRImo - Dana Masuk",
                bodyPattern = """(?i)(?:transfer\s+masuk|dana\s+masuk|menerima).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            // Shopee & ShopeePay
            NotificationRuleEntity(
                providerCode = "SHOPEEPAY",
                packageName = "com.shopee.id",
                ruleName = "Shopee - Pembayaran",
                bodyPattern = """(?i)(?:pembayaran|kamu\s+membayar|bayar\s+ke).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:di|ke)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "SHOPEEPAY",
                packageName = "com.shopee.id",
                ruleName = "Shopee - Uang Masuk",
                bodyPattern = """(?i)(?:top\s+up|transfer\s+masuk|menerima).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "SHOPEEPAY",
                packageName = "com.shopeepay.id",
                ruleName = "ShopeePay App - Pembayaran",
                bodyPattern = """(?i)(?:pembayaran|kamu\s+membayar|bayar\s+ke).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:di|ke)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "SHOPEEPAY",
                packageName = "com.shopeepay.id",
                ruleName = "ShopeePay App - Uang Masuk",
                bodyPattern = """(?i)(?:top\s+up|transfer\s+masuk|menerima).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            // BNI (BNI Mobile & wondr)
            NotificationRuleEntity(
                providerCode = "BNI",
                packageName = "src.mwallet.bni.co.id",
                ruleName = "BNI Mobile - Transaksi Keluar",
                bodyPattern = """(?i)(?:transaksi|transfer|pembayaran|qris|debit).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:di|ke)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "BNI",
                packageName = "src.mwallet.bni.co.id",
                ruleName = "BNI Mobile - Dana Masuk",
                bodyPattern = """(?i)(?:transfer\s+masuk|dana\s+masuk|menerima).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "BNI",
                packageName = "id.bni.wondr",
                ruleName = "wondr BNI - Transaksi Keluar",
                bodyPattern = """(?i)(?:transaksi|transfer|pembayaran|qris|debit).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:di|ke)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "BNI",
                packageName = "id.bni.wondr",
                ruleName = "wondr BNI - Dana Masuk",
                bodyPattern = """(?i)(?:transfer\s+masuk|dana\s+masuk|menerima).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            // Bank Jago
            NotificationRuleEntity(
                providerCode = "JAGO",
                packageName = "com.bankjago.digitalbanking",
                ruleName = "Bank Jago - Pengeluaran",
                bodyPattern = """(?i)(?:transfer\s+ke|pembayaran|qris|kantong).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:ke|di)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "JAGO",
                packageName = "com.bankjago.digitalbanking",
                ruleName = "Bank Jago - Dana Masuk",
                bodyPattern = """(?i)(?:terima\s+uang|transfer\s+masuk|dana\s+masuk).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            ),
            // OVO
            NotificationRuleEntity(
                providerCode = "OVO",
                packageName = "check.ovo.id",
                ruleName = "OVO - Pembayaran",
                bodyPattern = """(?i)(?:berhasil\s+bayar|pembayaran|transfer\s+ke).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:ke|di)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "EXPENSE",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 10,
                isEnabled = true,
                isBuiltin = true
            ),
            NotificationRuleEntity(
                providerCode = "OVO",
                packageName = "check.ovo.id",
                ruleName = "OVO - Dana Masuk",
                bodyPattern = """(?i)(?:top\s+up|transfer\s+masuk|menerima).*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)""",
                txType = "INCOME",
                groupMapJson = """{"amount":"amt","merchant":"mrc"}""",
                priority = 20,
                isEnabled = true,
                isBuiltin = true
            )
        )

        private suspend fun seedRules(ruleDao: NotificationRuleDao) {
            ruleDao.insertAll(getDefaultRules())
        }

        private suspend fun ensureEssentialRules(ruleDao: NotificationRuleDao) {
            val existing = ruleDao.getAllRulesList()
            val existingPkgs = existing.map { it.packageName }.toSet()
            val missing = getDefaultRules().filter { it.packageName !in existingPkgs }
            if (missing.isNotEmpty()) {
                ruleDao.insertAll(missing)
            }
        }

        private suspend fun ensureEssentialAccounts(accountDao: AccountDao) {
            val existing = accountDao.getAllAccountsList()
            val hasSeaBank = existing.any { it.providerCode == "SEABANK" || it.packageName == "id.co.bankbkemobile.digitalbank" }
            if (!hasSeaBank) {
                accountDao.insert(
                    AccountEntity(
                        name = "SeaBank",
                        type = "BANK",
                        providerCode = "SEABANK",
                        packageName = "id.co.bankbkemobile.digitalbank",
                        initialBalance = 1_000_000,
                        currentBalance = 1_000_000,
                        colorHex = "#FF5722",
                        icon = "seabank"
                    )
                )
            }
        }
    }
}
