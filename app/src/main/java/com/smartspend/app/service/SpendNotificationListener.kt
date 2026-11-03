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
import com.smartspend.app.core.model.TransactionType
import com.smartspend.app.core.parser.ParserEngine
import com.smartspend.app.core.repository.TransactionRepository
import com.smartspend.app.ui.components.island.DynamicIslandManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpendNotificationListener : NotificationListenerService() {

    companion object {
        private val KNOWN_FINANCIAL_PACKAGES = setOf(
            "com.bca",
            "com.bca.mybca.omni.android",
            "com.bcadigital.blu",
            "id.bmri.livin",
            "id.co.bri.brimo",
            "src.mwallet.bni.co.id",
            "id.bni.wondr",
            "id.co.bankbkemobile.digitalbank",
            "com.bankjago.digitalbanking",
            "com.jago.digitalBanking",
            "com.btpn.jenius",
            "id.dana",
            "com.gojek.app",
            "com.gojek.gopay",
            "com.gojek.gopaymerchant",
            "com.shopee.id",
            "com.shopeepay.id",
            "check.ovo.id",
            "com.telkom.linkaja",
            "com.aladin.bank"
        )
    }

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

    override fun onListenerConnected() {
        super.onListenerConnected()
        android.util.Log.i("SpendNotifListener", "SpendNotificationListener connected and active")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // 1. Filter out ongoing, self, and invalid notifications
        if (sbn.isOngoing || sbn.packageName == packageName) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        val subText = extras.getCharSequence("android.subText")?.toString()
        val textLines = (extras.get("android.textLines") as? Array<*>)
            ?.mapNotNull { it?.toString() }
            ?.joinToString(" ")

        val combinedText = listOfNotNull(bigText, text, textLines, subText)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .ifBlank { text ?: "" }

        val notifPackage = sbn.packageName
        val postTime = sbn.postTime

        // 2. Fast LRU check on notification key
        if (dedupEngine.isDuplicateNotificationKey(sbn.key, postTime)) {
            return
        }

        serviceScope.launch {
            processNotification(notifPackage, title, combinedText, postTime)
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

        // 1. Check specific rules enabled for this package
        val rules = database.notificationRuleDao().getEnabledRulesForPackage(pkg)

        // 2. Check if this is a known financial package or registered account
        val isKnownFintech = pkg in KNOWN_FINANCIAL_PACKAGES ||
            database.accountDao().findAccountByPackageAndHint(pkg, null) != null

        // 3. Record raw event for developer debugging & transparency (PRD section 2.1 & 3.6)
        val rawEventId = database.rawEventDao().insert(
            RawEventEntity(
                packageName = pkg,
                title = title,
                text = text,
                postTime = postTime,
                status = if (rules.isEmpty() && !isKnownFintech) "NO_RULE" else "PENDING"
            )
        )

        // If no matching rule AND not a known fintech app, ignore notification
        if (rules.isEmpty() && !isKnownFintech) return

        // 4. Run regex parser (tries package rules first, then fallback generic if rules don't match or are empty)
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

        // Anti-redundancy: the user already logged this exact move by hand
        // (PRD section 4.4). Covers "notifikasi BCA keluar 10.000 / DANA masuk 10.000
        // tidak boleh dobel setelah transfer BCA -> DANA 10.000 dicatat manual".
        if (accountId != null) {
            val manualMatch = when (parsed.type) {
                // Money left this account by hand already
                TransactionType.EXPENSE ->
                    dedupEngine.findRecentManualMatch(parsed.amount, accountId, postTime)
                // Money arrived here because the user logged the originating TRANSFER
                TransactionType.INCOME ->
                    dedupEngine.findRecentManualTransferInto(parsed.amount, accountId, postTime)
                else -> null
            }
            if (manualMatch != null) {
                parsed.matchedRuleId?.let { database.notificationRuleDao().incrementMatch(it) }
                database.rawEventDao().updateStatus(rawEventId, "SKIPPED_MANUAL_DUP")
                return
            }
        }

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
