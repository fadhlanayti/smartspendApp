package com.smartspend.app.core.model

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ProviderInfo(
    val code: String,
    val name: String,
    val type: String, // BANK, EWALLET, CASH, OTHER
    val colorHex: String,
    val monogram: String,
    val emoji: String,
    val defaultPackageName: String? = null
)

object ProviderCatalog {
    val ALL: List<ProviderInfo> = listOf(
        ProviderInfo("BCA", "Bank Central Asia", "BANK", "#005EAA", "BCA", "🏦", "com.bca.mybca.omni.android"),
        ProviderInfo("MANDIRI", "Bank Mandiri", "BANK", "#003D79", "MDR", "🏦", "id.bmri.livin"),
        ProviderInfo("BRI", "Bank BRI", "BANK", "#00529C", "BRI", "🏦", "id.co.bri.brimo"),
        ProviderInfo("BNI", "Bank BNI", "BANK", "#F15A22", "BNI", "🏦", "src.mwallet.bni.co.id"),
        ProviderInfo("BSI", "Bank Syariah Indonesia", "BANK", "#00A39D", "BSI", "🏦", "com.bsi.mobile"),
        ProviderInfo("CIMB", "CIMB Niaga (OCTO)", "BANK", "#ED1B2D", "OCTO", "🏦", "id.co.cimbniaga.octomobile"),
        ProviderInfo("JAGO", "Bank Jago", "BANK", "#FF5722", "JAGO", "🏦", "com.jago.app"),
        ProviderInfo("SEABANK", "SeaBank", "BANK", "#FF6B00", "SEA", "🏦", "com.searph.seabank"),
        ProviderInfo("DANA", "DANA Dompet Digital", "EWALLET", "#118EEA", "DANA", "📱", "id.dana"),
        ProviderInfo("GOPAY", "GoPay (Gojek)", "EWALLET", "#00AED6", "GOPAY", "📱", "com.gojek.app"),
        ProviderInfo("OVO", "OVO", "EWALLET", "#4C2A86", "OVO", "📱", "ovo.id"),
        ProviderInfo("SHOPEEPAY", "ShopeePay", "EWALLET", "#EE4D2D", "SPAY", "📱", "com.shopee.id"),
        ProviderInfo("LINKAJA", "LinkAja", "EWALLET", "#ED1C24", "LINK", "📱", "com.telkom.tcash"),
        ProviderInfo("CASH", "Uang Tunai (Dompet)", "CASH", "#2E7D32", "TUNAI", "💵", null),
        ProviderInfo("OTHER", "Lainnya / Kustom", "OTHER", "#6C757D", "LAIN", "💳", null)
    )

    fun find(code: String?): ProviderInfo {
        if (code == null) return ALL.last()
        return ALL.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ALL.last()
    }
}

private fun parseColor(hex: String, fallback: Color = Color(0xFF0A84FF)): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    fallback
}

/**
 * Badge ikon provider buatan sendiri: warna khas bank/e-wallet + monogram/emoji.
 */
@Composable
fun ProviderIconBadge(
    providerCode: String?,
    customColorHex: String? = null,
    size: Int = 36,
    modifier: Modifier = Modifier
) {
    val info = ProviderCatalog.find(providerCode)
    val color = customColorHex?.let { parseColor(it, parseColor(info.colorHex)) } ?: parseColor(info.colorHex)

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.28f).dp))
            .background(color.copy(alpha = 0.18f))
            .border(
                width = 0.5.dp,
                color = color.copy(alpha = 0.4f),
                shape = RoundedCornerShape((size * 0.28f).dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = info.emoji,
                fontSize = (size * 0.38f).sp
            )
            if (size >= 36) {
                Text(
                    text = info.monogram,
                    fontSize = (size * 0.22f).sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1
                )
            }
        }
    }
}
