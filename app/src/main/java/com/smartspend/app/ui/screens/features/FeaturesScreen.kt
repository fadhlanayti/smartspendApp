package com.smartspend.app.ui.screens.features

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartspend.app.core.database.entity.DebtEntity
import com.smartspend.app.core.database.entity.PurchasePlanEntity
import com.smartspend.app.ui.components.CurrencyFormatter
import com.smartspend.app.ui.components.SquircleCard
import com.smartspend.app.ui.components.SwipeableDebtItem
import com.smartspend.app.ui.screens.settings.AddDebtDialog
import com.smartspend.app.ui.screens.settings.DebtCardItem
import com.smartspend.app.ui.screens.settings.PlanCardItem
import com.smartspend.app.ui.screens.settings.PlanDecisionDialog
import com.smartspend.app.ui.screens.settings.RecordPaymentDialog
import com.smartspend.app.ui.screens.settings.SendDebtReminderDialog
import com.smartspend.app.ui.screens.settings.SettingsViewModel
import com.smartspend.app.ui.theme.AccentBlue
import com.smartspend.app.ui.theme.ColorExpenseDark
import com.smartspend.app.ui.theme.ColorIncomeDark
import com.smartspend.app.ui.theme.ColorWarning
import kotlinx.coroutines.launch

/**
 * Halaman fitur non-pengaturan: Utang-Piutang dan Rencana Beli (Anti-FOMO).
 * Dipisah dari Pengaturan agar Pengaturan fokus ke konfigurasi aplikasi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showAddDebtDialog by remember { mutableStateOf(false) }
    var selectedDebtForPayment by remember { mutableStateOf<DebtEntity?>(null) }
    var selectedDebtForReminder by remember { mutableStateOf<DebtEntity?>(null) }
    var selectedPlanForDecision by remember { mutableStateOf<PurchasePlanEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val totalOwe = uiState.debts
        .filter { !it.isDeleted && it.direction == "OWE" && it.status != "PAID" }
        .sumOf { (it.principalAmount - it.paidAmount).coerceAtLeast(0L) }
    val totalOwed = uiState.debts
        .filter { !it.isDeleted && it.direction == "OWED" && it.status != "PAID" }
        .sumOf { (it.principalAmount - it.paidAmount).coerceAtLeast(0L) }

    BackHandler(onBack = onBack)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Fitur Keuangan",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Utang-piutang & Anti-FOMO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Ringkasan singkat
            item {
                SquircleCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryMini(
                            label = "Saya bayar",
                            value = CurrencyFormatter.format(totalOwe, uiState.isHideBalanceDefault),
                            color = ColorExpenseDark,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMini(
                            label = "Saya tagih",
                            value = CurrencyFormatter.format(totalOwed, uiState.isHideBalanceDefault),
                            color = ColorIncomeDark,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section: Utang-Piutang
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Utang-Piutang",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Saya berutang & piutang saya",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showAddDebtDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Catat", maxLines = 1)
                    }
                }
            }

            if (uiState.debts.none { !it.isDeleted }) {
                item {
                    SquircleCard {
                        Text(
                            text = "Belum ada catatan utang-piutang.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.debts.filter { !it.isDeleted }, key = { it.id }) { debt ->
                    SwipeableDebtItem(
                        debt = debt,
                        onPaymentClick = { selectedDebtForPayment = debt },
                        onShareClick = { selectedDebtForReminder = debt },
                        onDelete = {
                            viewModel.deleteDebt(debt.id)
                            scope.launch {
                                val res = snackbarHostState.showSnackbar(
                                    message = "Catatan utang-piutang dihapus",
                                    actionLabel = "Urungkan",
                                    duration = SnackbarDuration.Short
                                )
                                if (res == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreDebt(debt.id)
                                }
                            }
                        }
                    )
                }
            }

            // Section: Rencana Beli (Anti-FOMO)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = ColorWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Rencana Beli",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Anti-FOMO: tunda dulu sebelum beli",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.plans.isEmpty()) {
                item {
                    SquircleCard {
                        Text(
                            text = "Belum ada rencana beli. Gunakan tombol 'Tunda Dulu' di input transaksi untuk menahan impuls belanja.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.plans, key = { it.id }) { plan ->
                    PlanCardItem(
                        plan = plan,
                        onDecideClick = { selectedPlanForDecision = plan }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAddDebtDialog) {
        AddDebtDialog(
            accounts = uiState.accounts,
            onDismiss = { showAddDebtDialog = false },
            onSave = { dir, counterparty, amt, due, accId, rec, note ->
                viewModel.addDebt(dir, counterparty, amt, due, accId, rec, note)
                showAddDebtDialog = false
            }
        )
    }

    selectedDebtForPayment?.let { debt ->
        RecordPaymentDialog(
            debt = debt,
            onDismiss = { selectedDebtForPayment = null },
            onSave = { amount, note ->
                viewModel.recordDebtPayment(debt, amount, note)
                selectedDebtForPayment = null
            }
        )
    }

    selectedDebtForReminder?.let { debt ->
        SendDebtReminderDialog(
            debt = debt,
            onDismiss = { selectedDebtForReminder = null }
        )
    }

    selectedPlanForDecision?.let { plan ->
        PlanDecisionDialog(
            plan = plan,
            remainingBudget = uiState.monthlyBudgetLimit,
            onDismiss = { selectedPlanForDecision = null },
            onDecision = { decision ->
                viewModel.decidePurchasePlan(plan, decision) {
                    selectedPlanForDecision = null
                }
            }
        )
    }
}

@Composable
private fun SummaryMini(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
    }
}
