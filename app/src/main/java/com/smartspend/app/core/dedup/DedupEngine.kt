package com.smartspend.app.core.dedup

import com.smartspend.app.core.database.dao.TransactionDao
import com.smartspend.app.core.database.entity.TransactionEntity
import com.smartspend.app.core.parser.ParsedTransaction
import java.security.MessageDigest

class DedupEngine(
    private val transactionDao: TransactionDao
) {
    // LRU cache for recent notification keys to drop rapid re-posts
    private val memoryLru = object : LinkedHashMap<String, Long>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > 200
        }
    }

    /**
     * Checks if notification key was already handled in memory.
     */
    @Synchronized
    fun isDuplicateNotificationKey(key: String, postTime: Long): Boolean {
        val entryKey = "$key-$postTime"
        if (memoryLru.containsKey(entryKey)) {
            return true
        }
        memoryLru[entryKey] = postTime
        return false
    }

    /**
     * Compute SHA-256 hash according to PRD section 4.4:
     * SHA-256( package_name | type | amount | normalize(merchant) | account_hint )
     */
    fun computeHash(parsed: ParsedTransaction): String {
        val normMerchant = normalizeMerchant(parsed.merchant)
        val hint = parsed.accountHint ?: ""
        val rawString = "${parsed.packageName}|${parsed.type.name}|${parsed.amount}|$normMerchant|$hint"

        val bytes = MessageDigest.getInstance("SHA-256").digest(rawString.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Normalize merchant for deduplication: lowercase, strip punctuation, collapse whitespace.
     */
    fun normalizeMerchant(merchant: String?): String {
        if (merchant.isNullOrBlank()) return ""
        return merchant.lowercase()
            .replace("[^a-z0-9\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    /**
     * Checks against database within a time window (default 45 seconds).
     * Returns the duplicate transaction if found, or null otherwise.
     */
    suspend fun findDatabaseDuplicate(
        hash: String,
        timestamp: Long,
        windowSeconds: Long = 45L
    ): TransactionEntity? {
        val startWindow = timestamp - (windowSeconds * 1000L)
        val endWindow = timestamp + (windowSeconds * 1000L)
        return transactionDao.findDuplicate(hash, startWindow, endWindow)
    }

    /**
     * Checks if a recent MANUAL transaction matches this auto-captured transaction
     * within 5 minutes (300 seconds), as described in PRD section 4.4.
     */
    suspend fun findRecentManualMatch(
        amount: Long,
        accountId: Long,
        timestamp: Long,
        windowSeconds: Long = 300L
    ): TransactionEntity? {
        val startWindow = timestamp - (windowSeconds * 1000L)
        val endWindow = timestamp + (windowSeconds * 1000L)
        return transactionDao.findRecentManualMatch(amount, accountId, startWindow, endWindow)
    }

    /**
     * Reverse direction of [findRecentManualMatch]: an auto INCOME arriving on [accountId]
     * right after the user manually logged a TRANSFER into that same account.
     * This is the second leg of "pindah uang antar rekening sendiri": the bank fires
     * "DANA masuk 10000" but the user already recorded TRANSFER BCA -> DANA 10000.
     */
    suspend fun findRecentManualTransferInto(
        amount: Long,
        accountId: Long,
        timestamp: Long,
        windowSeconds: Long = 300L
    ): TransactionEntity? {
        val startWindow = timestamp - (windowSeconds * 1000L)
        val endWindow = timestamp + (windowSeconds * 1000L)
        return transactionDao.findRecentManualTransferInto(amount, accountId, startWindow, endWindow)
    }
}
