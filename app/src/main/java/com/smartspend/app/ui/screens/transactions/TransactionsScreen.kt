package com.smartspend.app.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspend.app.core.database.entity.TransactionEntity
import com.smartspend.app.ui.components.CurrencyFormatter
import com.smartspend.app.ui.components.SquircleCard
import com.smartspend.app.ui.components.SwipeableTransactionItem
import com.smartspend.app.ui.screens.home.TransactionRowItem
import com.smartspend.app.ui.theme.AccentBlue
import com.smartspend.app.ui.theme.ColorExpenseDark
import com.smartspend.app.ui.theme.ColorIncomeDark
import com.smartspend.app.ui.theme.ColorWarning
import com.smartspend.app.ui.theme.DarkBorder
import com.smartspend.app.ui.theme.ElectricLime
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Riwayat Transaksi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Cari merchant atau catatan...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Hapus")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tabs: Semua vs Perlu Dicek
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp)
            ) {
                TabButton(
                    title = "Semua Transaksi",
                    isSelected = uiState.selectedTab == 0,
                    badgeCount = 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    title = "Perlu Dicek",
                    isSelected = uiState.selectedTab == 1,
                    badgeCount = uiState.needsReviewTransactions.size,
                    onClick = { viewModel.setSelectedTab(1) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter chips (Semua, Pengeluaran, Pemasukan, Transfer)
            if (uiState.selectedTab == 0) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChipItem(
                            label = "Semua",
                            isSelected = uiState.filterType == null,
                            onClick = { viewModel.setFilterType(null) }
                        )
                    }
                    item {
                        FilterChipItem(
                            label = "Pengeluaran",
                            isSelected = uiState.filterType == "EXPENSE",
                            onClick = { viewModel.setFilterType("EXPENSE") }
                        )
                    }
                    item {
                        FilterChipItem(
                            label = "Pemasukan",
                            isSelected = uiState.filterType == "INCOME",
                            onClick = { viewModel.setFilterType("INCOME") }
                        )
                    }
                    item {
                        FilterChipItem(
                            label = "Transfer",
                            isSelected = uiState.filterType == "TRANSFER",
                            onClick = { viewModel.setFilterType("TRANSFER") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // List of Transactions
            val displayList = if (uiState.selectedTab == 1) {
                uiState.needsReviewTransactions
            } else {
                uiState.allTransactions.filter { tx ->
                    val matchesType = uiState.filterType == null || tx.type == uiState.filterType
                    val matchesQuery = uiState.searchQuery.isBlank() ||
                            (tx.merchant?.contains(uiState.searchQuery, ignoreCase = true) == true) ||
                            (tx.note?.contains(uiState.searchQuery, ignoreCase = true) == true)
                    matchesType && matchesQuery
                }
            }

            if (displayList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (uiState.selectedTab == 1) Icons.Default.CheckCircleOutline else Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.selectedTab == 1) "Semua transaksi auto-capture sudah dikonfirmasi!" else "Tidak ada transaksi yang cocok",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val grouped = remember(displayList) {
                    displayList.groupBy { tx ->
                        val cal = Calendar.getInstance()
                        val today = Calendar.getInstance()
                        cal.timeInMillis = tx.timestamp
                        when {
                            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Hari Ini"
                            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> "Kemarin"
                            else -> SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(Date(tx.timestamp))
                        }
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (uiState.selectedTab == 1) {
                        items(displayList, key = { it.id }) { tx ->
                            ReviewTransactionCard(
                                transaction = tx,
                                onConfirm = { viewModel.confirmReviewTransaction(tx.id) },
                                onEdit = { viewModel.setEditingTransaction(tx) },
                                onDelete = { viewModel.deleteTransaction(tx.id) }
                            )
                        }
                    } else {
                        grouped.forEach { (dateHeader, txs) ->
                            item(key = "header_$dateHeader") {
                                Text(
                                    text = dateHeader,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
                                )
                            }
                            items(txs, key = { it.id }) { tx ->
                                val cat = uiState.categoriesMap[tx.categoryId]
                                SwipeableTransactionItem(
                                    transaction = tx,
                                    categoryName = cat?.name ?: "Lain-lain",
                                    isHideBalance = false,
                                    onDelete = {
                                        viewModel.deleteTransaction(tx.id)
                                        scope.launch {
                                            val res = snackbarHostState.showSnackbar(
                                                message = "Transaksi dihapus",
                                                actionLabel = "Urungkan",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (res == SnackbarResult.ActionPerformed) {
                                                viewModel.restoreTransaction(tx.id)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Edit Transaction Dialog
    uiState.editingTransaction?.let { tx ->
        EditTransactionDialog(
            transaction = tx,
            onDismiss = { viewModel.setEditingTransaction(null) },
            onSave = { viewModel.updateTransaction(it) }
        )
    }
}

@Composable
fun TabButton(
    title: String,
    isSelected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (badgeCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(ColorWarning),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) ElectricLime else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) Color(0xFF0C0D12) else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
        )
    }
}

@Composable
fun ReviewTransactionCard(
    transaction: TransactionEntity,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    SquircleCard(
        backgroundColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = transaction.merchant ?: "Perlu Dikonfirmasi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = CurrencyFormatter.format(transaction.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (transaction.type == "EXPENSE") ColorExpenseDark else ColorIncomeDark
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Raw notification display (PRD section 4.5)
        if (!transaction.rawNotification.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Text(
                    text = "\"${transaction.rawNotification}\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(10.dp)) {
                Text("Buang", color = ColorExpenseDark)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(10.dp)) {
                Text("Ubah")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricLime)
            ) {
                Text("Konfirmasi", color = Color(0xFF0C0D12), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity) -> Unit
) {
    var merchant by remember { mutableStateOf(transaction.merchant ?: "") }
    var note by remember { mutableStateOf(transaction.note ?: "") }
    var amountStr by remember { mutableStateOf(transaction.amount.toString()) }

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
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Ubah Transaksi",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it.filter { c -> c.isDigit() } },
                label = { Text("Nominal (Rp)") },
                prefix = { Text("Rp ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant / Penerima") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Catatan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

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
                        val amt = amountStr.toLongOrNull() ?: transaction.amount
                        onSave(
                            transaction.copy(
                                amount = amt,
                                merchant = merchant.ifBlank { null },
                                note = note.ifBlank { null },
                                reviewStatus = "CONFIRMED"
                            )
                        )
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
