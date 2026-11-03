package com.smartspend.app.core.parser

import kotlin.math.roundToLong

object AmountNormalizer {
    /**
     * Normalizes currency string into whole Rupiah (Long).
     * Follows PRD section 4.2 algorithm.
     */
    fun normalize(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        
        // 1. Remove spaces and optional 'rp', 'rp.'
        var clean = raw.trim().replace("\\s+".toRegex(), "")
        if (clean.startsWith("rp.", ignoreCase = true)) {
            clean = clean.substring(3)
        } else if (clean.startsWith("rp", ignoreCase = true)) {
            clean = clean.substring(2)
        }
        clean = clean.trim()
        if (clean.isEmpty()) return null

        val hasDot = clean.contains('.')
        val hasComma = clean.contains(',')

        val parsedLong: Long = try {
            if (hasDot && hasComma) {
                // 2. Both dot and comma exist (e.g. 1.250.000,50 or 1,250,000.50)
                val lastDot = clean.lastIndexOf('.')
                val lastComma = clean.lastIndexOf(',')
                if (lastComma > lastDot) {
                    // Indonesian standard: dot = thousand, comma = decimal
                    val thousandPart = clean.substring(0, lastComma).replace(".", "")
                    val decimalPart = clean.substring(lastComma + 1)
                    val doubleVal = "$thousandPart.$decimalPart".toDouble()
                    doubleVal.roundToLong()
                } else {
                    // US standard: comma = thousand, dot = decimal
                    val thousandPart = clean.substring(0, lastDot).replace(",", "")
                    val decimalPart = clean.substring(lastDot + 1)
                    val doubleVal = "$thousandPart.$decimalPart".toDouble()
                    doubleVal.roundToLong()
                }
            } else if (hasDot && !hasComma) {
                val occurrences = clean.count { it == '.' }
                if (occurrences > 1) {
                    // 3. Multiple dots: all thousands (e.g. 1.250.000)
                    clean.replace(".", "").toLong()
                } else {
                    val afterDot = clean.substringAfter('.')
                    if (afterDot.length == 3) {
                        // 4. Exactly 3 digits: thousand (e.g. 150.000)
                        clean.replace(".", "").toLong()
                    } else {
                        // 5. 1-2 digits: decimal, round to nearest rupiah (e.g. 12.50)
                        clean.toDouble().roundToLong()
                    }
                }
            } else if (hasComma && !hasDot) {
                val occurrences = clean.count { it == ',' }
                if (occurrences > 1) {
                    // Multiple commas: all thousands (e.g. 1,250,000)
                    clean.replace(",", "").toLong()
                } else {
                    val afterComma = clean.substringAfter(',')
                    if (afterComma.length == 3) {
                        // Exactly 3 digits: thousand (e.g. 1,500)
                        clean.replace(",", "").toLong()
                    } else {
                        // 1-2 digits: decimal (e.g. 50,5)
                        clean.replace(',', '.').toDouble().roundToLong()
                    }
                }
            } else {
                // Pure digits
                clean.toLong()
            }
        } catch (_: Exception) {
            return null
        }

        // 7. Sanity check: must be > 0 and <= 10,000,000,000
        if (parsedLong <= 0 || parsedLong > 10_000_000_000L) {
            return null
        }

        return parsedLong
    }
}
