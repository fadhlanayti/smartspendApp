package com.smartspend.app.ui.screens.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspend.app.core.database.entity.AccountEntity
import com.smartspend.app.core.database.entity.TransactionEntity
import com.smartspend.app.core.model.ProviderIconBadge
import com.smartspend.app.ui.components.CurrencyFormatter
import com.smartspend.app.ui.components.MiniSpendChart
import com.smartspend.app.ui.components.NetCashflowCard
import com.smartspend.app.ui.components.SquircleCard
import com.smartspend.app.ui.components.SwipeableTransactionItem
import com.smartspend.app.ui.screens.accounts.AccountManagerDialog
import com.smartspend.app.core.model.TransactionType
import com.smartspend.app.ui.components.island.DynamicIslandManager
import com.smartspend.app.ui.screens.budget.BudgetUiState
import com.smartspend.app.ui.screens.budget.BudgetViewModel
import com.smartspend.app.ui.screens.budget.CategoryBudgetItem
import com.smartspend.app.ui.theme.AccentBlue
import com.smartspend.app.ui.theme.ColorExpenseDark
import com.smartspend.app.ui.theme.ColorIncomeDark
import com.smartspend.app.ui.theme.ColorWarning
import com.smartspend.app.ui.theme.DarkBorder
import com.smartspend.app.ui.theme.ElectricLime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    budgetViewModel: BudgetViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToDebts: () -> Unit,
    onNavigateToPlans: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onOpenFastAdd: ((TransactionType) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val budgetState by budgetViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAccountManager by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SmartSpend",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ringkasan Finansial",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Recording Health Indicator
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .border(0.8.dp, DarkBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isListenerActive) ColorIncomeDark else ColorExpenseDark)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isListenerActive) "Aktif" else "Mati",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Hide Balance Eye Toggle
                    IconButton(onClick = { viewModel.toggleHideBalance() }) {
                        Icon(
                            imageVector = if (uiState.isHideBalance) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Sembunyikan Saldo",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Live Clock & Weather Greeting Widget
            item {
                LiveClockWeatherCard()
            }

            // 0b. Ringkasan Kekayaan: Total Saldo | Utang | Piutang
            item {
                WealthSummaryCard(
                    accounts = uiState.accounts,
                    totalDebtOwe = uiState.totalDebtOwe,
                    totalDebtOwed = uiState.totalDebtOwed,
                    oweDetails = uiState.oweDetails,
                    owedDetails = uiState.owedDetails,
                    isHideBalance = uiState.isHideBalance,
                    onDebtsClick = onNavigateToDebts
                )
            }

            // 1. Net Cashflow Card
            item {
                NetCashflowCard(
                    remainingBudget = uiState.remainingBudget,
                    monthlyIncome = uiState.monthlyIncome,
                    monthlyExpense = uiState.monthlyExpense,
                    budgetLimit = uiState.budgetLimit,
                    isHideBalance = uiState.isHideBalance,
                    onPayClick = { onOpenFastAdd?.invoke(TransactionType.EXPENSE) },
                    onTransferClick = { onOpenFastAdd?.invoke(TransactionType.TRANSFER) },
                    onReceiveClick = { onOpenFastAdd?.invoke(TransactionType.INCOME) },
                    onAddClick = { onOpenFastAdd?.invoke(TransactionType.EXPENSE) }
                )
            }

            // 2. Alokasi budget per kategori (ke mana uang dialokasikan)
            item {
                BudgetAllocationHomeCard(
                    state = budgetState,
                    onClick = onNavigateToBudget
                )
            }

            // 3. Banner "Perlu Dicek" (if low confidence transactions exist)
            if (uiState.needsReviewCount > 0) {
                item {
                    SquircleCard(
                        backgroundColor = ColorWarning.copy(alpha = 0.15f),
                        borderColor = ColorWarning.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToReview() },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PendingActions,
                                    contentDescription = null,
                                    tint = ColorWarning
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "${uiState.needsReviewCount} Transaksi Perlu Dicek",
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Hasil auto-capture yang butuh konfirmasi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Buka",
                                tint = ColorWarning
                            )
                        }
                    }
                }
            }

            // 3. Mini 7-day spend chart
            item {
                SquircleCard {
                    MiniSpendChart(
                        data = uiState.weekSpends,
                        isHideBalance = uiState.isHideBalance
                    )
                }
            }

            // 4. Fitur Keuangan (Utang & Anti-FOMO)
            item {
                Column {
                    Text(
                        text = "Fitur Keuangan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Utang-piutang & Anti-FOMO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 5. Utang & Rencana Beli Cards Row (PRD Section 9.6)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Utang-Piutang Card
                    SquircleCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToDebts() }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Utang-Piutang",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Bayar: ${CurrencyFormatter.format(uiState.totalDebtOwe, uiState.isHideBalance)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorExpenseDark
                        )
                        Text(
                            text = "Tagih: ${CurrencyFormatter.format(uiState.totalDebtOwed, uiState.isHideBalance)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorIncomeDark
                        )
                    }

                    // Rencana Beli Card
                    SquircleCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToPlans() }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Rencana Beli",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = ColorWarning,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${uiState.activePlansCount} Rencana Menunggu",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Hemat: ${CurrencyFormatter.formatShort(uiState.savedMoneyThisMonth, uiState.isHideBalance)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorIncomeDark
                        )
                    }
                }
            }

            // 5. Account Balances Carousel
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ringkasan Akun",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = { showAccountManager = true }) {
                            Text("Kelola")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.accounts) { account ->
                            AccountCard(
                                account = account,
                                isHideBalance = uiState.isHideBalance,
                                onClick = { showAccountManager = true }
                            )
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .width(115.dp)
                                    .height(115.dp)
                                    .border(1.dp, AccentBlue.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                    .clickable { showAccountManager = true },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = AccentBlue.copy(alpha = 0.08f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = AccentBlue)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Kelola\nDompet",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentBlue,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Recent Transactions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaksi Terakhir",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = onNavigateToTransactions) {
                        Text(text = "Lihat Semua")
                    }
                }
            }

            if (uiState.recentTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Belum ada transaksi tercatat",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.recentTransactions, key = { it.id }) { tx ->
                    val cat = uiState.categoriesMap[tx.categoryId]
                    SwipeableTransactionItem(
                        transaction = tx,
                        categoryName = cat?.name ?: "Lain-lain",
                        isHideBalance = uiState.isHideBalance,
                        onDelete = {
                            viewModel.deleteTransaction(tx.id)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Transaksi berhasil dihapus",
                                    actionLabel = "Urungkan",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreTransaction(tx.id)
                                }
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) } // navigation padding
        }
    }

    if (showAccountManager) {
        AccountManagerDialog(
            accounts = uiState.accounts,
            onDismiss = { showAccountManager = false },
            onSaveAccount = { name, provider, balance, hint ->
                viewModel.addAccount(name, provider, balance, hint)
            },
            onUpdateAccount = { acc, newName, newBalance ->
                viewModel.updateAccount(acc, newName, newBalance)
            },
            onArchiveAccount = { acc ->
                viewModel.archiveAccount(acc)
            }
        )
    }
}

@Composable
fun AccountCard(
    account: AccountEntity,
    isHideBalance: Boolean,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .width(170.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProviderIconBadge(
                    providerCode = account.providerCode,
                    accountName = account.name,
                    customColorHex = account.colorHex,
                    size = 36
                )
                account.accountHint?.let { hint ->
                    Text(
                        text = hint.takeLast(4),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = account.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = CurrencyFormatter.format(account.currentBalance, isHideBalance),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun WealthSummaryCard(
    accounts: List<AccountEntity>,
    totalDebtOwe: Long,
    totalDebtOwed: Long,
    oweDetails: List<com.smartspend.app.core.database.entity.DebtEntity>,
    owedDetails: List<com.smartspend.app.core.database.entity.DebtEntity>,
    isHideBalance: Boolean,
    onDebtsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDebtDetail by remember { mutableStateOf(false) }

    val totalBalance = accounts.sumOf { it.currentBalance }
    val netWorth = totalBalance + totalDebtOwed - totalDebtOwe
    val bankCount = accounts.count { it.type == "BANK" }
    val walletCount = accounts.count { it.type == "EWALLET" }

    SquircleCard(modifier = modifier, backgroundColor = Color(0xFF101216), borderColor = DarkBorder) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Total Saldo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = CurrencyFormatter.format(totalBalance, isHideBalance),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp, letterSpacing = (-0.5).sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "$bankCount rekening bank • $walletCount e-wallet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Kekayaan Bersih",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = CurrencyFormatter.format(netWorth, isHideBalance),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (netWorth >= 0) ColorIncomeDark else ColorExpenseDark
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DebtTile(
                label = "Utang Saya",
                subtitle = "${oweDetails.size} orang",
                amount = totalDebtOwe,
                accent = ColorExpenseDark,
                icon = Icons.Default.CallMade,
                isHideBalance = isHideBalance,
                modifier = Modifier.weight(1f)
            )
            DebtTile(
                label = "Piutang Saya",
                subtitle = "${owedDetails.size} orang",
                amount = totalDebtOwed,
                accent = ColorIncomeDark,
                icon = Icons.Default.CallReceived,
                isHideBalance = isHideBalance,
                modifier = Modifier.weight(1f)
            )
        }

        if (oweDetails.isNotEmpty() || owedDetails.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showDebtDetail = !showDebtDetail }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showDebtDetail) "Sembunyikan rincian" else "Lihat utang ke siapa",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentBlue
                )
                Icon(
                    imageVector = if (showDebtDetail) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (showDebtDetail) {
                Spacer(modifier = Modifier.height(6.dp))
                if (oweDetails.isNotEmpty()) {
                    Text(
                        text = "Kamu berutang ke:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ColorExpenseDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    oweDetails.take(5).forEach { d ->
                        DebtDetailRow(debt = d, accent = ColorExpenseDark, isHideBalance = isHideBalance)
                    }
                }
                if (owedDetails.isNotEmpty()) {
                    if (oweDetails.isNotEmpty()) Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Orang berutang ke kamu:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ColorIncomeDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    owedDetails.take(5).forEach { d ->
                        DebtDetailRow(debt = d, accent = ColorIncomeDark, isHideBalance = isHideBalance)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = onDebtsClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Kelola Utang-Piutang", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DebtTile(
    label: String,
    subtitle: String,
    amount: Long,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isHideBalance: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = CurrencyFormatter.format(amount, isHideBalance),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.5.sp
        )
    }
}

@Composable
private fun DebtDetailRow(
    debt: com.smartspend.app.core.database.entity.DebtEntity,
    accent: Color,
    isHideBalance: Boolean
) {
    val remaining = debt.principalAmount - debt.paidAmount
    val dueStr = debt.dueDate?.let {
        SimpleDateFormat("d MMM yy", Locale("id", "ID")).format(Date(it))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = debt.counterpartyName.trim().take(1).uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = debt.counterpartyName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dueStr?.let { "Jatuh tempo $it" } ?: "Tanpa jatuh tempo",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = CurrencyFormatter.format(remaining, isHideBalance),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1
        )
    }
}

@Composable
fun AnimatedCelestialGraphic(
    isDaytime: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "celestial")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension / 4.4f

        if (isDaytime) {
            // Ambient outer soft glow
            drawCircle(
                color = accentColor.copy(alpha = 0.14f * pulse),
                radius = baseRadius * 1.9f,
                center = center
            )
            // Mid glow halo
            drawCircle(
                color = accentColor.copy(alpha = 0.22f),
                radius = baseRadius * 1.35f,
                center = center
            )

            // Rotating geometric sun rays
            rotate(degrees = rotation, pivot = center) {
                val rayCount = 8
                val innerRayDist = baseRadius * 1.25f
                val outerRayDist = baseRadius * 1.7f * pulse
                for (i in 0 until rayCount) {
                    val angleDeg = (i * 360f / rayCount)
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val cosA = Math.cos(angleRad).toFloat()
                    val sinA = Math.sin(angleRad).toFloat()

                    val start = Offset(center.x + cosA * innerRayDist, center.y + sinA * innerRayDist)
                    val end = Offset(center.x + cosA * outerRayDist, center.y + sinA * outerRayDist)

                    drawLine(
                        color = accentColor.copy(alpha = 0.85f),
                        start = start,
                        end = end,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Solid inner solar core
            drawCircle(
                color = accentColor,
                radius = baseRadius,
                center = center
            )
        } else {
            // Nighttime: Glowing crescent moon & star sparkle
            drawCircle(
                color = accentColor.copy(alpha = 0.12f * pulse),
                radius = baseRadius * 1.8f,
                center = center
            )

            val moonPath = Path().apply {
                val r = baseRadius * 1.1f
                addOval(Rect(center.x - r, center.y - r, center.x + r, center.y + r))
            }
            val cutoutPath = Path().apply {
                val r = baseRadius * 1.05f
                val offsetCut = baseRadius * 0.55f
                addOval(Rect(center.x - r + offsetCut, center.y - r - offsetCut * 0.35f, center.x + r + offsetCut, center.y + r - offsetCut * 0.35f))
            }
            val crescent = Path.combine(
                PathOperation.Difference,
                moonPath,
                cutoutPath
            )
            drawPath(path = crescent, color = accentColor)

            // Pulsing mini star
            val starAlpha = ((pulse - 0.88f) / 0.24f).coerceIn(0.2f, 1f)
            drawCircle(
                color = accentColor.copy(alpha = starAlpha),
                radius = 2.dp.toPx(),
                center = Offset(center.x + baseRadius * 1.1f, center.y - baseRadius * 0.7f)
            )
        }
    }
}

@Composable
fun LiveClockWeatherCard(modifier: Modifier = Modifier) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val cal = Calendar.getInstance().apply { timeInMillis = currentTime }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val dayFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID"))
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale("id", "ID"))
    val dayString = dayFormat.format(Date(currentTime))
    val timeString = timeFormat.format(Date(currentTime))

    val isDaytime = hour in 5..18
    val (greeting, accentColor) = when (hour) {
        in 5..10 -> Pair("Selamat Pagi", ElectricLime)
        in 11..14 -> Pair("Selamat Siang", Color(0xFFFFD54F))
        in 15..18 -> Pair("Selamat Sore", Color(0xFFFFB74D))
        else -> Pair("Selamat Malam", Color(0xFF80D8FF))
    }

    val livePulse = rememberInfiniteTransition(label = "live_pulse")
    val dotAlpha by livePulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 0.8.dp,
                color = DarkBorder,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF101216)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1D24).copy(alpha = 0.45f),
                            Color(0xFF101216)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AnimatedCelestialGraphic(
                        isDaytime = isDaytime,
                        accentColor = accentColor,
                        modifier = Modifier.size(38.dp)
                    )

                    Column {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dayString,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF16181F),
                    border = BorderStroke(0.8.dp, DarkBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = dotAlpha))
                        )
                        Text(
                            text = "$timeString WIB",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionRowItem(
    transaction: TransactionEntity,
    categoryName: String,
    isHideBalance: Boolean,
    onDelete: () -> Unit
) {
    val isExpense = transaction.type == "EXPENSE"
    val isIncome = transaction.type == "INCOME"

    val amountColor = when {
        isExpense -> ColorExpenseDark
        isIncome -> ColorIncomeDark
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val amountPrefix = when {
        isExpense -> "-"
        isIncome -> "+"
        else -> ""
    }

    val timeStr = SimpleDateFormat("dd MMM, HH:mm", Locale("id", "ID")).format(Date(transaction.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category / Merchant Avatar Box
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (isExpense) ColorExpenseDark.copy(alpha = 0.12f)
                    else if (isIncome) ColorIncomeDark.copy(alpha = 0.12f)
                    else ElectricLime.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isExpense) Icons.Default.NorthEast
                else if (isIncome) Icons.Default.SouthWest
                else Icons.Default.SyncAlt,
                contentDescription = null,
                tint = if (isExpense) ColorExpenseDark
                else if (isIncome) ColorIncomeDark
                else ElectricLime,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.merchant?.ifBlank { null } ?: categoryName,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$categoryName • $timeStr",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "$amountPrefix${CurrencyFormatter.format(transaction.amount, isHideBalance)}",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            fontWeight = FontWeight.ExtraBold,
            color = amountColor,
            maxLines = 1
        )
    }
}

@Composable
private fun BudgetAllocationHomeCard(
    state: BudgetUiState,
    onClick: () -> Unit
) {
    val allocatedProgress = if (state.totalBudget > 0L) {
        (state.totalAllocated.toFloat() / state.totalBudget.toFloat()).coerceIn(0f, 1f)
    } else 0f

    SquircleCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Alokasi Budget",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Ke mana uangmu dialokasikan bulan ini",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Buka alokasi budget",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Dialokasikan",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${CurrencyFormatter.format(state.totalAllocated)} / ${CurrencyFormatter.format(state.totalBudget)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { allocatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = AccentBlue,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        if (state.allocatedItems.isEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Belum ada limit kategori. Ketuk untuk mengatur, misalnya jajan.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            state.allocatedItems.take(3).forEach { item ->
                CategoryMiniProgress(item = item)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CategoryMiniProgress(item: CategoryBudgetItem) {
    val progressColor = when {
        item.isOver -> ColorExpenseDark
        item.progress >= 0.8f -> ColorWarning
        else -> AccentBlue
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(progressColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                com.smartspend.app.ui.components.CategoryIcon(
                    iconKey = item.category.icon,
                    categoryName = item.category.name,
                    tint = progressColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.category.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${CurrencyFormatter.format(item.spent)} / ${CurrencyFormatter.format(item.limit)}",
            style = MaterialTheme.typography.labelSmall,
            color = if (item.isOver) ColorExpenseDark else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = { item.progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = progressColor,
        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    )
}
