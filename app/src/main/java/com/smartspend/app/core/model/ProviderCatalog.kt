package com.smartspend.app.core.model

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.smartspend.app.R

data class ProviderInfo(
    val code: String,
    val name: String,
    val type: String, // BANK, EWALLET, CASH, OTHER
    val colorHex: String,
    val monogram: String,
    val emoji: String,
    val defaultPackageName: String? = null,
    val logoUrl: String? = null,
    val localDrawableRes: Int? = null
)

object ProviderCatalog {
    private const val LOGO_BASE_URL = "https://raw.githubusercontent.com/fadhlanayti/smartspendApp/main/art/logos"

    val ALL: List<ProviderInfo> = listOf(
        ProviderInfo("BCA", "Bank Central Asia", "BANK", "#005EAA", "BCA", "🏦", "com.bca.mybca.omni.android", "$LOGO_BASE_URL/bca.png", R.drawable.ic_provider_bca),
        ProviderInfo("MANDIRI", "Bank Mandiri", "BANK", "#003D79", "MDR", "🏦", "id.bmri.livin", "$LOGO_BASE_URL/mandiri.png", R.drawable.ic_provider_mandiri),
        ProviderInfo("BRI", "Bank BRI", "BANK", "#00529C", "BRI", "🏦", "id.co.bri.brimo", "$LOGO_BASE_URL/bri.png", R.drawable.ic_provider_bri),
        ProviderInfo("BNI", "Bank BNI", "BANK", "#F15A22", "BNI", "🏦", "src.mwallet.bni.co.id", "$LOGO_BASE_URL/bni.png", R.drawable.ic_provider_bni),
        ProviderInfo("BSI", "Bank Syariah Indonesia", "BANK", "#00A39D", "BSI", "🏦", "com.bsi.mobile", "$LOGO_BASE_URL/bsi.png", R.drawable.ic_provider_bsi),
        ProviderInfo("CIMB", "CIMB Niaga (OCTO)", "BANK", "#ED1B2D", "OCTO", "🏦", "id.co.cimbniaga.octomobile", "$LOGO_BASE_URL/cimb.png", R.drawable.ic_provider_cimb),
        ProviderInfo("JAGO", "Bank Jago", "BANK", "#FF5722", "JAGO", "🏦", "com.jago.app", "$LOGO_BASE_URL/jago.png", R.drawable.ic_provider_jago),
        ProviderInfo("SEABANK", "SeaBank", "BANK", "#FF6B00", "SEA", "🏦", "com.searph.seabank", "$LOGO_BASE_URL/seabank.png", R.drawable.ic_provider_seabank),
        ProviderInfo("DANA", "DANA Dompet Digital", "EWALLET", "#118EEA", "DANA", "📱", "id.dana", "$LOGO_BASE_URL/dana.png", R.drawable.ic_provider_dana),
        ProviderInfo("GOPAY", "GoPay (Gojek)", "EWALLET", "#00AED6", "GOPAY", "📱", "com.gojek.app", "$LOGO_BASE_URL/gopay.png", R.drawable.ic_provider_gopay),
        ProviderInfo("OVO", "OVO", "EWALLET", "#4C2A86", "OVO", "📱", "ovo.id", "$LOGO_BASE_URL/ovo.png", R.drawable.ic_provider_ovo),
        ProviderInfo("SHOPEEPAY", "ShopeePay", "EWALLET", "#EE4D2D", "SPAY", "📱", "com.shopee.id", "$LOGO_BASE_URL/shopeepay.png", R.drawable.ic_provider_shopeepay),
        ProviderInfo("LINKAJA", "LinkAja", "EWALLET", "#ED1C24", "LINK", "📱", "com.telkom.tcash", null, null),
        ProviderInfo("CASH", "Uang Tunai (Dompet)", "CASH", "#2E7D32", "TUNAI", "💵", null, null, null),
        ProviderInfo("OTHER", "Lainnya / Kustom", "OTHER", "#6C757D", "LAIN", "💳", null, null, null)
    )

    fun find(code: String?, nameHint: String? = null): ProviderInfo {
        if (!code.isNullOrBlank() && !code.equals("OTHER", ignoreCase = true)) {
            val matched = ALL.firstOrNull { it.code.equals(code, ignoreCase = true) }
            if (matched != null) return matched
        }
        if (!nameHint.isNullOrBlank()) {
            val lower = nameHint.lowercase()
            when {
                lower.contains("bca") -> return ALL.first { it.code == "BCA" }
                lower.contains("mandiri") || lower.contains("livin") -> return ALL.first { it.code == "MANDIRI" }
                lower.contains("bri") -> return ALL.first { it.code == "BRI" }
                lower.contains("bni") -> return ALL.first { it.code == "BNI" }
                lower.contains("bsi") -> return ALL.first { it.code == "BSI" }
                lower.contains("cimb") || lower.contains("octo") -> return ALL.first { it.code == "CIMB" }
                lower.contains("jago") -> return ALL.first { it.code == "JAGO" }
                lower.contains("seabank") || lower.contains("sea") -> return ALL.first { it.code == "SEABANK" }
                lower.contains("dana") -> return ALL.first { it.code == "DANA" }
                lower.contains("gopay") || lower.contains("gojek") -> return ALL.first { it.code == "GOPAY" }
                lower.contains("ovo") -> return ALL.first { it.code == "OVO" }
                lower.contains("shopee") || lower.contains("spay") -> return ALL.first { it.code == "SHOPEEPAY" }
                lower.contains("tunai") || lower.contains("cash") || lower.contains("dompet") -> return ALL.first { it.code == "CASH" }
            }
        }
        return ALL.last()
    }
}

private fun parseColor(hex: String, fallback: Color = Color(0xFF0A84FF)): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    fallback
}

/**
 * Badge ikon provider resmi dari internet (Coil AsyncImage) dengan background squircle bersih.
 */
@Composable
fun ProviderIconBadge(
    providerCode: String?,
    accountName: String? = null,
    customColorHex: String? = null,
    size: Int = 36,
    modifier: Modifier = Modifier
) {
    val info = ProviderCatalog.find(providerCode, accountName)
    val brandColor = customColorHex?.let { parseColor(it, parseColor(info.colorHex)) } ?: parseColor(info.colorHex)
    val cornerRadius = (size * 0.28f).dp

    if (info.code.uppercase() == "CASH" || info.logoUrl == null && info.localDrawableRes == null) {
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(RoundedCornerShape(cornerRadius))
                .background(brandColor)
                .border(
                    width = 0.8.dp,
                    color = Color.White.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(cornerRadius)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (info.code.uppercase() == "CASH") "Rp" else info.monogram.take(3),
                color = Color.White,
                fontSize = (size * 0.38f).sp,
                fontWeight = FontWeight.Black
            )
        }
        return
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White)
            .border(
                width = 0.8.dp,
                color = brandColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(cornerRadius)
            ),
        contentAlignment = Alignment.Center
    ) {
        val context = LocalContext.current
        val imageModel = remember(info.logoUrl, info.localDrawableRes) {
            ImageRequest.Builder(context)
                .data(info.logoUrl)
                .crossfade(true)
                .apply {
                    info.localDrawableRes?.let {
                        placeholder(it)
                        error(it)
                    }
                }
                .build()
        }

        AsyncImage(
            model = imageModel,
            contentDescription = info.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding((size * 0.12f).dp)
        )
    }
}

