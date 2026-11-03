package com.smartspend.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspend.app.ui.theme.DarkBorder
import com.smartspend.app.ui.theme.ElectricLime
import com.smartspend.app.ui.theme.ColorExpenseDark
import com.smartspend.app.ui.theme.ColorIncomeDark

@Composable
fun SquircleCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

@Composable
fun NetCashflowCard(
    remainingBudget: Long,
    monthlyIncome: Long,
    monthlyExpense: Long,
    budgetLimit: Long,
    isHideBalance: Boolean,
    modifier: Modifier = Modifier,
    onPayClick: (() -> Unit)? = null,
    onTransferClick: (() -> Unit)? = null,
    onReceiveClick: (() -> Unit)? = null,
    onAddClick: (() -> Unit)? = null
) {
    val progress = if (budgetLimit > 0) (monthlyExpense.toFloat() / budgetLimit).coerceIn(0f, 1f) else 0f
    val progressColor = when {
        progress >= 1f -> ColorExpenseDark
        progress >= 0.8f -> Color(0xFFFFB800)
        else -> ElectricLime
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Sisa Anggaran & % Indicator Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(ElectricLime)
                    )
                    Text(
                        text = "Sisa Anggaran Bulanan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(progressColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}% terpakai",
                        style = MaterialTheme.typography.labelSmall,
                        color = progressColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hero Giant Balance Number
            Text(
                text = CurrencyFormatter.format(remainingBudget, isHideBalance),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 36.sp,
                    letterSpacing = (-0.5).sp
                ),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Thin Modern Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Income / Expense Stat Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Income Badge
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ColorIncomeDark.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = ColorIncomeDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Masuk",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "+${CurrencyFormatter.formatShort(monthlyIncome, isHideBalance)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorIncomeDark
                        )
                    }
                }

                // Expense Badge
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ColorExpenseDark.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = ColorExpenseDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Keluar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "-${CurrencyFormatter.formatShort(monthlyExpense, isHideBalance)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorExpenseDark
                        )
                    }
                }
            }

            // Quick Pill Actions (Inspired by Fintech Design: Pay, Transfer, Receive, Add)
            if (onPayClick != null || onTransferClick != null || onReceiveClick != null || onAddClick != null) {
                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FintechActionButton(
                        icon = Icons.Default.NorthEast,
                        label = "Bayar",
                        onClick = { onPayClick?.invoke() }
                    )
                    FintechActionButton(
                        icon = Icons.Default.SyncAlt,
                        label = "Transfer",
                        onClick = { onTransferClick?.invoke() }
                    )
                    FintechActionButton(
                        icon = Icons.Default.SouthWest,
                        label = "Masuk",
                        onClick = { onReceiveClick?.invoke() }
                    )
                    FintechActionButton(
                        icon = Icons.Default.Add,
                        label = "Catat",
                        isHighlight = true,
                        onClick = { onAddClick?.invoke() }
                    )
                }
            }
        }
    }
}

@Composable
fun FintechActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isHighlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    if (isHighlight) ElectricLime else MaterialTheme.colorScheme.surfaceVariant
                )
                .border(
                    width = 1.dp,
                    color = if (isHighlight) ElectricLime else DarkBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isHighlight) Color(0xFF0C0D12) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
            color = if (isHighlight) ElectricLime else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp
        )
    }
}

@Composable
fun CustomNumpad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("000", "0", "<")
    )

    Column(modifier = modifier.fillMaxWidth()) {
        for (row in keys) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (key in row) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                            .border(0.8.dp, DarkBorder, RoundedCornerShape(18.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (key == "<") onBackspaceClick() else onDigitClick(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "<") {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Hapus",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickChipsRow(
    onAddAmount: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val chips = listOf(
        Pair("+10rb", 10_000L),
        Pair("+20rb", 20_000L),
        Pair("+50rb", 50_000L),
        Pair("+100rb", 100_000L)
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for ((label, value) in chips) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(0.8.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .clickable { onAddAmount(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ElectricLime
                )
            }
        }
    }
}

@Composable
fun CategoryIcon(
    iconKey: String?,
    categoryName: String = "",
    tint: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val key = (iconKey?.lowercase() ?: "").ifBlank { categoryName.lowercase() }
    val icon: ImageVector = when {
        key.contains("cafe") || key.contains("kopi") || key.contains("jajan") -> Icons.Default.LocalCafe
        key.contains("restaurant") || key.contains("makan") || key.contains("food") -> Icons.Default.Restaurant
        key.contains("fastfood") -> Icons.Default.Fastfood
        key.contains("tol") || key.contains("parkir") -> Icons.Default.LocalParking
        key.contains("bus") || key.contains("transportasi umum") || key.contains("kereta") -> Icons.Default.DirectionsBus
        key.contains("car") || key.contains("mobil") || key.contains("transport") -> Icons.Default.DirectionsCar
        key.contains("motor") || key.contains("two_wheeler") || key.contains("ojek") -> Icons.Default.TwoWheeler
        key.contains("bensin") || key.contains("gas") || key.contains("bbm") -> Icons.Default.LocalGasStation
        key.contains("market") || key.contains("toko") -> Icons.Default.Storefront
        key.contains("pakaian") || key.contains("baju") || key.contains("fashion") -> Icons.Default.Checkroom
        key.contains("elektronik") || key.contains("gadget") || key.contains("device") -> Icons.Default.Devices
        key.contains("belanja") || key.contains("shop") -> Icons.Default.ShoppingBag
        key.contains("air") || key.contains("water") || key.contains("pdam") -> Icons.Default.WaterDrop
        key.contains("pulsa") || key.contains("internet") || key.contains("wifi") || key.contains("kuota") -> Icons.Default.Wifi
        key.contains("langganan") || key.contains("subscri") || key.contains("streaming") -> Icons.Default.Subscriptions
        key.contains("receipt") || key.contains("tagihan") || key.contains("bill") -> Icons.AutoMirrored.Filled.ReceiptLong
        key.contains("listrik") || key.contains("bolt") || key.contains("pln") -> Icons.Default.Bolt
        key.contains("kesehatan") || key.contains("obat") || key.contains("med") || key.contains("hospital") || key.contains("dokter") -> Icons.Default.LocalHospital
        key.contains("hiburan") || key.contains("movie") || key.contains("bioskop") || key.contains("film") -> Icons.Default.Movie
        key.contains("game") || key.contains("esport") -> Icons.Default.SportsEsports
        key.contains("pendidikan") || key.contains("school") || key.contains("kuliah") || key.contains("buku") -> Icons.Default.School
        key.contains("gaji") || key.contains("payment") || key.contains("income") -> Icons.Default.Payments
        key.contains("transfer") || key.contains("sync") -> Icons.Default.SyncAlt
        key.contains("rumah") || key.contains("home") -> Icons.Default.Home
        key.contains("gym") || key.contains("fitness") -> Icons.Default.FitnessCenter
        key.contains("flight") || key.contains("liburan") || key.contains("travel") || key.contains("pesawat") -> Icons.Default.Flight
        else -> Icons.Default.Category
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}
