package com.smartspend.app.ui.screens.fastadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspend.app.core.model.ProviderIconBadge
import com.smartspend.app.core.model.TransactionType
import com.smartspend.app.ui.components.CurrencyFormatter
import com.smartspend.app.ui.components.CustomNumpad
import com.smartspend.app.ui.components.QuickChipsRow
import com.smartspend.app.ui.theme.AccentBlue
import com.smartspend.app.ui.theme.ColorExpenseDark
import com.smartspend.app.ui.theme.ColorIncomeDark
import com.smartspend.app.ui.theme.ColorWarning
import com.smartspend.app.ui.theme.DarkBorder
import com.smartspend.app.ui.theme.ElectricLime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastAddBottomSheet(
    viewModel: FastAddViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp)
        ) {
            // Segmented Control: Pengeluaran / Pemasukan / Transfer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TabChip(
                    title = "Pengeluaran",
                    isSelected = uiState.type == TransactionType.EXPENSE,
                    selectedColor = ColorExpenseDark,
                    onClick = { viewModel.setType(TransactionType.EXPENSE) },
                    modifier = Modifier.weight(1f)
                )
                TabChip(
                    title = "Pemasukan",
                    isSelected = uiState.type == TransactionType.INCOME,
                    selectedColor = ColorIncomeDark,
                    onClick = { viewModel.setType(TransactionType.INCOME) },
                    modifier = Modifier.weight(1f)
                )
                TabChip(
                    title = "Transfer",
                    isSelected = uiState.type == TransactionType.TRANSFER,
                    selectedColor = AccentBlue,
                    onClick = { viewModel.setType(TransactionType.TRANSFER) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Big Amount Display (48sp)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = CurrencyFormatter.format(uiState.amount, isHidden = false),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 38.sp),
                    fontWeight = FontWeight.Bold,
                    color = when (uiState.type) {
                        TransactionType.EXPENSE -> ColorExpenseDark
                        TransactionType.INCOME -> ColorIncomeDark
                        TransactionType.TRANSFER -> AccentBlue
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Chips
            QuickChipsRow(onAddAmount = { viewModel.addQuickAmount(it) })

            Spacer(modifier = Modifier.height(12.dp))

            // Custom Numpad
            CustomNumpad(
                onDigitClick = { viewModel.onDigitClick(it) },
                onBackspaceClick = { viewModel.onBackspace() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Account Selector
            Text(
                text = if (uiState.type == TransactionType.TRANSFER) "Dari Akun:" else "Akun:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.accounts) { acc ->
                    val isSelected = uiState.selectedAccountId == acc.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) ElectricLime else MaterialTheme.colorScheme.surfaceVariant)
                            .border(0.8.dp, if (isSelected) ElectricLime else DarkBorder, RoundedCornerShape(12.dp))
                            .clickable { viewModel.selectAccount(acc.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ProviderIconBadge(
                                providerCode = acc.providerCode,
                                customColorHex = acc.colorHex,
                                size = 20
                            )
                            Text(
                                text = acc.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Color(0xFF0C0D12) else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            if (uiState.type == TransactionType.TRANSFER) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Ke Akun:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.accounts.filter { it.id != uiState.selectedAccountId }) { acc ->
                        val isSelected = uiState.destinationAccountId == acc.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) ElectricLime else MaterialTheme.colorScheme.surfaceVariant)
                                .border(0.8.dp, if (isSelected) ElectricLime else DarkBorder, RoundedCornerShape(12.dp))
                                .clickable { viewModel.selectDestinationAccount(acc.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ProviderIconBadge(
                                    providerCode = acc.providerCode,
                                    customColorHex = acc.colorHex,
                                    size = 20
                                )
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) Color(0xFF0C0D12) else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Kategori:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.categories) { cat ->
                        val isSelected = uiState.selectedCategoryId == cat.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) ElectricLime else MaterialTheme.colorScheme.surfaceVariant)
                                .border(0.8.dp, if (isSelected) ElectricLime else DarkBorder, RoundedCornerShape(12.dp))
                                .clickable { viewModel.selectCategory(cat.id) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Color(0xFF0C0D12) else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Merchant / Penerima field
            OutlinedTextField(
                value = uiState.merchant,
                onValueChange = { viewModel.updateMerchant(it) },
                label = { Text("Nama Merchant / Penerima (Opsional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Catatan field
            OutlinedTextField(
                value = uiState.note,
                onValueChange = { viewModel.updateNote(it) },
                label = { Text("Catatan (Opsional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Modern Pill Action Button
            Button(
                onClick = { viewModel.saveTransaction(andAddAnother = false, onDismiss) },
                enabled = uiState.amount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricLime,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "Simpan Transaksi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (uiState.amount > 0) Color(0xFF0C0D12) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // "Tunda Dulu" button (PRD section 9.4: FOMO purchase plan redirect)
            if (uiState.type == TransactionType.EXPENSE && uiState.amount > 0) {
                OutlinedButton(
                    onClick = { viewModel.delayAsPlan(onDismiss) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorWarning)
                ) {
                    Icon(imageVector = Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tunda Dulu (Buat Rencana Beli)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun TabChip(
    title: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) selectedColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
