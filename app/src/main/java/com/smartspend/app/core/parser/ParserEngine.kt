package com.smartspend.app.core.parser

import com.smartspend.app.core.database.entity.NotificationRuleEntity
import com.smartspend.app.core.model.CaptureSource
import com.smartspend.app.core.model.ReviewStatus
import com.smartspend.app.core.model.TransactionType

data class ParsedTransaction(
    val type: TransactionType,
    val amount: Long,
    val fee: Long = 0L,
    val merchant: String? = null,
    val accountHint: String? = null,
    val packageName: String,
    val rawText: String,
    val confidence: Float,
    val reviewStatus: ReviewStatus,
    val matchedRuleId: Long? = null
)

class ParserEngine {

    companion object {
        private val GENERIC_AMOUNT_REGEX = Regex(
            """(?i)rp\.?\s?(?<amt>\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?|\d+)"""
        )

        private val CREDIT_KEYWORDS = listOf(
            "menerima", "dana masuk", "uang masuk", "kredit", "refund", "cashback", "diterima", "masuk sebesar"
        )

        private val DEBIT_KEYWORDS = listOf(
            "membayar", "pembayaran", "transfer ke", "debit", "belanja", "top up", "pembelian", "bayar"
        )
    }

    /**
     * Parse notification body and title against rules or fallback generic parser.
     */
    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        rules: List<NotificationRuleEntity>
    ): ParsedTransaction? {
        val fullContent = listOfNotNull(title, text).joinToString(" ").trim()
        if (fullContent.isBlank()) return null

        // 1. Try matching against ordered rules for this package
        for (rule in rules) {
            try {
                val regex = Regex(rule.bodyPattern)
                val matchResult = regex.find(fullContent)
                if (matchResult != null) {
                    val groups = matchResult.groups

                    // Extract amt
                    val rawAmt = try {
                        matchResult.groups["amt"]?.value
                    } catch (_: IllegalArgumentException) {
                        null
                    } ?: groups[1]?.value

                    val amount = AmountNormalizer.normalize(rawAmt) ?: continue

                    // Extract merchant
                    var merchant = try {
                        matchResult.groups["mrc"]?.value
                    } catch (_: IllegalArgumentException) {
                        null
                    }

                    // Extract account hint if present
                    val accHint = try {
                        matchResult.groups["acc"]?.value
                    } catch (_: IllegalArgumentException) {
                        null
                    }

                    merchant = cleanMerchant(merchant)

                    val txType = when (rule.txType.uppercase()) {
                        "INCOME" -> TransactionType.INCOME
                        "TRANSFER" -> TransactionType.TRANSFER
                        else -> TransactionType.EXPENSE
                    }

                    val confidence = if (!merchant.isNullOrBlank()) 1.0f else 0.8f
                    val reviewStatus = if (confidence >= 0.7f) ReviewStatus.CONFIRMED else ReviewStatus.NEEDS_REVIEW

                    return ParsedTransaction(
                        type = txType,
                        amount = amount,
                        merchant = merchant,
                        accountHint = accHint,
                        packageName = packageName,
                        rawText = fullContent,
                        confidence = confidence,
                        reviewStatus = reviewStatus,
                        matchedRuleId = rule.id
                    )
                }
            } catch (_: Exception) {
                // Rule regex error, continue to next rule
            }
        }

        // 2. Fallback Generic Parser (PRD section 4.5)
        return parseFallbackGeneric(packageName, fullContent)
    }

    private fun parseFallbackGeneric(packageName: String, text: String): ParsedTransaction? {
        val match = GENERIC_AMOUNT_REGEX.find(text) ?: return null
        val rawAmt = match.groups["amt"]?.value ?: return null
        val amount = AmountNormalizer.normalize(rawAmt) ?: return null

        val lower = text.lowercase()
        val isCredit = CREDIT_KEYWORDS.any { lower.contains(it) }
        val isDebit = DEBIT_KEYWORDS.any { lower.contains(it) }

        val (type, confidence) = when {
            isCredit && !isDebit -> Pair(TransactionType.INCOME, 0.5f)
            isDebit && !isCredit -> Pair(TransactionType.EXPENSE, 0.5f)
            else -> Pair(TransactionType.EXPENSE, 0.4f) // Ambiguous type
        }

        return ParsedTransaction(
            type = type,
            amount = amount,
            merchant = null,
            accountHint = null,
            packageName = packageName,
            rawText = text,
            confidence = confidence,
            reviewStatus = ReviewStatus.NEEDS_REVIEW,
            matchedRuleId = null
        )
    }

    fun cleanMerchant(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var clean = raw.trim()

        // Remove prefixes like "ke ", "di ", "dari "
        val prefixes = listOf("ke ", "di ", "dari ")
        for (prefix in prefixes) {
            if (clean.startsWith(prefix, ignoreCase = true)) {
                clean = clean.substring(prefix.length).trim()
                break
            }
        }

        // Remove common suffixes like " berhasil", " sukses", trailing dots
        clean = clean.replace("""(?i)\s+(berhasil|sukses)$""".toRegex(), "").trim()
        clean = clean.trimEnd('.', ',', '!', '?').trim()

        // Title case capitalize words if uppercase or all lowercase
        if (clean.all { it.isUpperCase() || !it.isLetter() } || clean.all { it.isLowerCase() || !it.isLetter() }) {
            clean = clean.split(" ").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }

        return clean.ifBlank { null }
    }
}
