package com.smartspend.app.service

import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.smartspend.app.core.category.CategoryEngine
import com.smartspend.app.core.database.SmartSpendDatabase
import com.smartspend.app.core.database.entity.RawEventEntity
import com.smartspend.app.core.database.entity.TransactionEntity
import com.smartspend.app.core.dedup.DedupEngine
import com.smartspend.app.core.notification.NotificationHelper
import com.smartspend.app.core.parser.ParserEngine
import com.smartspend.app.core.repository.TransactionRepository
import com.smartspend.app.ui.components.island.DynamicIslandManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpendNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: SmartSpendDatabase
    private lateinit var parserEngine: ParserEngine
    private lateinit var dedupEngine: DedupEngine
    private lateinit var categoryEngine: CategoryEngine
    private lateinit var transactionRepository: TransactionRepository

    override fun onCreate() {
        super.onCreate()
        database = SmartSpendDatabase.getInstance(this)
        parserEngine = ParserEngine()
        dedupEngine = DedupEngine(database.transactionDao())
        categoryEngine = CategoryEngine(database.categoryDao(), database.merchantCategoryMapDao())
        transactionRepository = TransactionRepository(this, database)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // 1. Filter out ongoing, self, and invalid notifications
        if (sbn.isOngoing || sbn.packageName == packageName) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: text

        val notifPackage = sbn.packageName
        val postTime = sbn.postTime

        // 2. Fast LRU check on notification key
        if (dedupEngine.isDuplicateNotificationKey(sbn.key, postTime)) {
            return
        }

        serviceScope.launch {
            processNotification(notifPackage, title, bigText, postTime)
        }
    }

    private suspend fun processNotification(
        pkg: String,
        title: String?,
        text: String?,
        postTime: Long
    ) {
        // Ignored internal packages to prevent loop
        if (pkg == packageName) return

        // Check rules
        val rules = database.notificationRuleDao().getEnabledRulesForPackage(pkg)

        // Record raw event for developer debugging & transparency (PRD section 2.1 & 3.6)
        val rawEventId = database.rawEventDao().insert(
            RawEventEntity(
                packageName = pkg,
                title = title,
                text = text,
                postTime = postTime,
                status = if (rules.isEmpty()) "NO_RULE" else "PENDING"
            )
        )

        // If no matching rule for this package, stop processing
        if (rules.isEmpty()) return

        // Run regex parser
        val parsed = parserEngine.parse(pkg, title, text, rules)
        if (parsed == null) {
            database.rawEventDao().updateStatus(rawEventId, "FAILED")
            return
        }

        // Deduplication check
        val hash = dedupEngine.computeHash(parsed)
        val duplicate = dedupEngine.findDatabaseDuplicate(hash, postTime, windowSeconds = 45L)
        if (duplicate != null) {
            parsed.matchedRuleId?.let { database.notificationRuleDao().incrementMatch(it) }
            database.rawEventDao().updateStatus(rawEventId, "PARSED_DUPLICATE")
            return
        }

        // Find corresponding account or fallback
        var account = database.accountDao().findAccountByPackageAndHint(pkg, parsed.accountHint)
        if (account == null) {
            account = database.accountDao().findAccountByPackageAndHint(pkg, null)
        }
        if (account == null) {
            account = database.accountDao().findAccountByProviderCode("CASH")
        }
        val accountId = account?.id

        // Auto categorize
        val categoryId = categoryEngine.suggestCategory(parsed.merchant, parsed.type)

        // Insert transaction in DB
        val tx = TransactionEntity(
            type = parsed.type.name,
            amount = parsed.amount,
            fee = parsed.fee,
            sourceAccountId = if (parsed.type.name == "EXPENSE" || parsed.type.name == "TRANSFER") accountId else null,
            destinationAccountId = if (parsed.type.name == "INCOME" || parsed.type.name == "TRANSFER") accountId else null,
            categoryId = categoryId,
            merchant = parsed.merchant,
            rawNotification = parsed.rawText,
            sourcePackage = pkg,
            hashDedup = hash,
            captureSource = "AUTO",
            reviewStatus = parsed.reviewStatus.name,
            confidence = parsed.confidence,
            timestamp = postTime
        )

        val insertedId = transactionRepository.insertTransaction(tx)
        val finalTx = tx.copy(id = insertedId)
        parsed.matchedRuleId?.let { database.notificationRuleDao().incrementMatch(it) }
        database.rawEventDao().updateStatus(rawEventId, "PARSED")

        // Trigger local push notification confirmation
        NotificationHelper.sendTransactionNotification(this, finalTx, account?.name)

        // Trigger Dynamic Island HUD animation (in-app and system-wide floating overlay)
        DynamicIslandManager.showTransactionEvent(
            context = applicationContext,
            title = finalTx.merchant ?: "Transaksi Otomatis",
            amount = finalTx.amount,
            type = finalTx.type,
            accountName = account?.name
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Anti-kill layer 1: request rebind on disconnect (PRD section 2.3)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, SpendNotificationListener::class.java))
        }
    }
}
