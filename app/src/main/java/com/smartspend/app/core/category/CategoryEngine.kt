package com.smartspend.app.core.category

import com.smartspend.app.core.database.dao.CategoryDao
import com.smartspend.app.core.database.dao.MerchantCategoryMapDao
import com.smartspend.app.core.database.entity.MerchantCategoryMapEntity
import com.smartspend.app.core.model.TransactionType

class CategoryEngine(
    private val categoryDao: CategoryDao,
    private val merchantCategoryMapDao: MerchantCategoryMapDao
) {
    /**
     * Determine best category for a merchant and transaction type.
     */
    suspend fun suggestCategory(merchant: String?, type: TransactionType): Long? {
        if (type == TransactionType.TRANSFER) {
            return categoryDao.findByName("Transfer")?.id
        }

        if (type == TransactionType.INCOME) {
            return categoryDao.findByName("Gaji")?.id
                ?: categoryDao.findByName("Gaji & Pendapatan")?.id
        }

        if (merchant.isNullOrBlank()) {
            return categoryDao.findByName("Lain-lain")?.id
        }

        val normKey = merchant.lowercase().trim()

        // 1. Check user-learned mapping from merchant_category_map
        val mapping = merchantCategoryMapDao.getMapping(normKey)
        if (mapping != null) {
            return mapping.categoryId
        }

        // 2. Keyword heuristic matching
        val matchedName = when {
            normKey.containsAny("kopi", "cafe", "coffee", "kenangan", "janji jiwa", "starbucks", "chatime", "teh", "boba") -> "Kopi & jajan"
            normKey.containsAny("makan", "resto", "warung", "warteg", "bakso", "mie", "ayam", "nasgor", "soto", "burger", "pizza", "kfc", "mcdonald", "hokben") -> "Makan di luar"
            normKey.containsAny("indomaret", "alfamart", "superindo", "hypermart", "sayur", "pasar", "buah") -> "Belanja bahan makanan"
            normKey.containsAny("gofood", "grabfood", "shopeefood") -> "Pesan antar"
            normKey.containsAny("goride", "gocar", "grabcar", "ojek", "maxim", "indrive") -> "Ojek online"
            normKey.containsAny("pertamina", "spbu", "shell", "bensin", "solar", "bbm") -> "BBM"
            normKey.containsAny("parkir", "tol", "jasamarga", "parking") -> "Parkir & tol"
            normKey.containsAny("kereta", "krl", "mrt", "transjakarta", "busway", "kai") -> "Transportasi umum"
            normKey.containsAny("tokopedia", "shopee", "lazada", "blibli", "tiktok shop") -> "Marketplace"
            normKey.containsAny("pln", "listrik") -> "Listrik"
            normKey.containsAny("pdam", "air") -> "Air"
            normKey.containsAny("pulsa", "kuota", "telkomsel", "indosat", "xl", "smartfren", "tri") -> "Internet & pulsa"
            normKey.containsAny("netflix", "spotify", "youtube", "disney", "prime") -> "Langganan digital"
            normKey.containsAny("apotek", "obat", "kimia farma", "century", "halodoc", "alodokter") -> "Obat & apotek"
            normKey.containsAny("xxi", "cgv", "cinepolis", "bioskop", "cinema") -> "Hiburan"
            else -> "Lain-lain"
        }

        val cat = categoryDao.findByName(matchedName)
        return cat?.id ?: categoryDao.findByName("Lain-lain")?.id
    }

    /**
     * Learn from user manual correction.
     */
    suspend fun recordUserCorrection(merchant: String, categoryId: Long) {
        val normKey = merchant.lowercase().trim()
        val current = merchantCategoryMapDao.getMapping(normKey)
        val hitCount = (current?.hitCount ?: 0) + 1
        merchantCategoryMapDao.upsert(
            MerchantCategoryMapEntity(
                merchantKey = normKey,
                categoryId = categoryId,
                hitCount = hitCount
            )
        )
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
