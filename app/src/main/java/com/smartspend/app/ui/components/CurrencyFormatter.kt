package com.smartspend.app.ui.components

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    private val localeID = Locale("id", "ID")
    private val formatter = NumberFormat.getCurrencyInstance(localeID).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    fun format(amount: Long, isHidden: Boolean = false): String {
        if (isHidden) return "Rp ••••••"
        return formatter.format(amount).replace(",00", "")
    }

    fun formatShort(amount: Long, isHidden: Boolean = false): String {
        if (isHidden) return "••••••"
        return when {
            amount >= 1_000_000_000 -> "%.1f M".format(amount / 1_000_000_000.0)
            amount >= 1_000_000 -> "%.1f jt".format(amount / 1_000_000.0)
            amount >= 1_000 -> "%d rb".format(amount / 1_000)
            else -> amount.toString()
        }
    }
}
