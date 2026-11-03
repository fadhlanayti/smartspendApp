package com.smartspend.app.core

import com.smartspend.app.core.database.entity.NotificationRuleEntity
import com.smartspend.app.core.model.ReviewStatus
import com.smartspend.app.core.model.TransactionType
import com.smartspend.app.core.parser.AmountNormalizer
import com.smartspend.app.core.parser.ParserEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserAndNormalizerTest {

    @Test
    fun testAmountNormalizerIndonesianFormats() {
        assertEquals(150_000L, AmountNormalizer.normalize("150.000"))
        assertEquals(150_000L, AmountNormalizer.normalize("Rp 150.000"))
        assertEquals(32_000L, AmountNormalizer.normalize("Rp32.000"))
        assertEquals(47_500L, AmountNormalizer.normalize("Rp 47.500"))
        assertEquals(2_500_000L, AmountNormalizer.normalize("Rp 2.500.000"))
        assertEquals(1_250_000L, AmountNormalizer.normalize("Rp 1.250.000,00"))
        assertEquals(1_250_001L, AmountNormalizer.normalize("Rp 1.250.000,50"))
        assertEquals(51L, AmountNormalizer.normalize("50,5"))
        assertEquals(100_000L, AmountNormalizer.normalize("Rp100.000"))
    }

    @Test
    fun testBcaTransferParsing() {
        val rules = listOf(
            NotificationRuleEntity(
                id = 1,
                providerCode = "BCA",
                packageName = "com.bca.mybca.omni.android",
                ruleName = "BCA Transfer keluar",
                bodyPattern = """(?i)transfer\s+rp\.?\s?(?<amt>[\d.,]+)\s+ke\s+(?<mrc>.+?)\s+berhasil""",
                txType = "EXPENSE"
            )
        )
        val engine = ParserEngine()
        val result = engine.parse(
            packageName = "com.bca.mybca.omni.android",
            title = "m-Transfer",
            text = "Transfer Rp 150.000 ke BUDI SANTOSO berhasil. 02/11 14:32",
            rules = rules
        )

        assertNotNull(result)
        assertEquals(TransactionType.EXPENSE, result!!.type)
        assertEquals(150_000L, result.amount)
        assertEquals("Budi Santoso", result.merchant)
        assertEquals(ReviewStatus.CONFIRMED, result.reviewStatus)
        assertEquals(1.0f, result.confidence, 0.01f)
    }

    @Test
    fun testBcaQrisParsing() {
        val rules = listOf(
            NotificationRuleEntity(
                id = 2,
                providerCode = "BCA",
                packageName = "com.bca.mybca.omni.android",
                ruleName = "BCA QRIS",
                bodyPattern = """(?i)pembayaran\s+qris\s+rp\.?\s?(?<amt>[\d.,]+)\s+di\s+(?<mrc>.+?)\s+berhasil""",
                txType = "EXPENSE"
            )
        )
        val engine = ParserEngine()
        val result = engine.parse(
            packageName = "com.bca.mybca.omni.android",
            title = "Pembayaran",
            text = "Pembayaran QRIS Rp 47.500 di KOPI KENANGAN berhasil",
            rules = rules
        )

        assertNotNull(result)
        assertEquals(TransactionType.EXPENSE, result!!.type)
        assertEquals(47_500L, result.amount)
        assertEquals("Kopi Kenangan", result.merchant)
    }

    @Test
    fun testDanaPaymentParsing() {
        val rules = listOf(
            NotificationRuleEntity(
                id = 4,
                providerCode = "DANA",
                packageName = "id.dana",
                ruleName = "DANA bayar",
                bodyPattern = """(?i)membayar\s+rp\.?\s?(?<amt>[\d.,]+)\s+ke\s+(?<mrc>.+)""",
                txType = "EXPENSE"
            )
        )
        val engine = ParserEngine()
        val result = engine.parse(
            packageName = "id.dana",
            title = "Pembayaran Berhasil",
            text = "Kamu berhasil membayar Rp32.000 ke Indomaret",
            rules = rules
        )

        assertNotNull(result)
        assertEquals(TransactionType.EXPENSE, result!!.type)
        assertEquals(32_000L, result.amount)
        assertEquals("Indomaret", result.merchant)
    }

    @Test
    fun testGenericFallbackParser() {
        val engine = ParserEngine()
        val result = engine.parse(
            packageName = "com.unknown.bank",
            title = "Notifikasi",
            text = "Anda telah membayar Rp 85.000 di toko",
            rules = emptyList()
        )

        assertNotNull(result)
        assertEquals(TransactionType.EXPENSE, result!!.type)
        assertEquals(85_000L, result.amount)
        assertEquals(ReviewStatus.NEEDS_REVIEW, result.reviewStatus)
        assertEquals(0.5f, result.confidence, 0.01f)
    }
}
