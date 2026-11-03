package com.smartspend.app.ui.screens.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspend.app.core.database.entity.AccountEntity
import com.smartspend.app.core.model.ProviderCatalog
import com.smartspend.app.core.model.ProviderIconBadge
import com.smartspend.app.core.model.ProviderInfo
import com.smartspend.app.ui.components.CurrencyFormatter
import com.smartspend.app.ui.theme.AccentBlue
import com.smartspend.app.ui.theme.ColorExpenseDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagerDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSaveAccount: (name: String, provider: ProviderInfo, balance: Long, hint: String?) -> Unit,
    onUpdateAccount: (account: AccountEntity, newName: String, newBalance: Long) -> Unit,
    onArchiveAccount: (account: AccountEntity) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var archivingAccount by remember { mutableStateOf<AccountEntity?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Kelola Dompet & Rekening",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Master akun bank, e-wallet, dan uang tunai",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tambah Dompet / Rekening Baru", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Account List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(accounts.filter { !it.isArchived }, key = { it.id }) { acc ->
                    AccountItemCard(
                        account = acc,
                        onEdit = { editingAccount = acc },
                        onArchive = { archivingAccount = acc }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditAccountDialog(
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, provider, balance, hint ->
                onSaveAccount(name, provider, balance, hint)
                showAddDialog = false
            }
        )
    }

    editingAccount?.let { acc ->
        AddEditAccountDialog(
            existing = acc,
            onDismiss = { editingAccount = null },
            onSave = { name, _, balance, _ ->
                onUpdateAccount(acc, name, balance)
                editingAccount = null
            }
        )
    }

    archivingAccount?.let { acc ->
        ModalBottomSheet(
            onDismissRequest = { archivingAccount = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Sembunyikan Dompet?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Dompet \"${acc.name}\" akan disembunyikan dari daftar pilihan. Riwayat transaksi lama tetap aman tersimpan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { archivingAccount = null },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = {
                            onArchiveAccount(acc)
                            archivingAccount = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorExpenseDark)
                    ) {
                        Text("Sembunyikan", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountItemCard(
    account: AccountEntity,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderIconBadge(
                providerCode = account.providerCode,
                accountName = account.name,
                customColorHex = account.colorHex,
                size = 44
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = CurrencyFormatter.format(account.currentBalance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentBlue
                )
                account.accountHint?.let { hint ->
                    Text(
                        text = "Hint: $hint",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onArchive) {
                Icon(Icons.Default.Archive, contentDescription = "Arsipkan", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditAccountDialog(
    existing: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, provider: ProviderInfo, balance: Long, hint: String?) -> Unit
) {
    var selectedProvider by remember {
        mutableStateOf(ProviderCatalog.find(existing?.providerCode))
    }
    var name by remember { mutableStateOf(existing?.name ?: selectedProvider.name) }
    var balanceStr by remember { mutableStateOf(existing?.currentBalance?.toString() ?: "0") }
    var hint by remember { mutableStateOf(existing?.accountHint.orEmpty()) }

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
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (existing == null) "Tambah Dompet / Rekening" else "Ubah Dompet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (existing == null) {
                Text(
                    text = "Pilih Bank / E-Wallet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ProviderCatalog.ALL) { p ->
                        val isSelected = selectedProvider.code == p.code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AccentBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = AccentBlue,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedProvider = p
                                    if (name.isBlank() || ProviderCatalog.ALL.any { it.name == name }) {
                                        name = p.name
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ProviderIconBadge(providerCode = p.code, size = 32)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = p.code,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("Nama Dompet / Rekening") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = balanceStr,
                onValueChange = { balanceStr = it.filter { c -> c.isDigit() }.take(12) },
                label = { Text(if (existing == null) "Saldo Awal (Rp)" else "Saldo Saat Ini (Rp)") },
                prefix = { Text("Rp ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (existing == null) {
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it.take(20) },
                    label = { Text("Nomor Rekening / 4 Digit Terakhir (Opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Batal")
                }
                Button(
                    onClick = {
                        val bal = balanceStr.toLongOrNull() ?: 0L
                        if (name.isNotBlank()) {
                            onSave(name.trim(), selectedProvider, bal, hint.ifBlank { null })
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Simpan", color = Color.White)
                }
            }
        }
    }
}
