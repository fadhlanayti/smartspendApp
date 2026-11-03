package com.smartspend.app.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspend.app.core.database.entity.DebtEntity
import com.smartspend.app.core.database.entity.NotificationRuleEntity
import com.smartspend.app.core.database.entity.PurchasePlanEntity
import com.smartspend.app.core.parser.AmountNormalizer
import com.smartspend.app.ui.components.CurrencyFormatter
import com.smartspend.app.ui.components.SquircleCard
import com.smartspend.app.ui.screens.accounts.AccountManagerDialog
import com.smartspend.app.ui.screens.budget.BudgetAllocationScreen
import com.smartspend.app.ui.screens.budget.BudgetViewModel
import com.smartspend.app.ui.screens.transactions.FilterChipItem
import com.smartspend.app.ui.theme.AccentBlue
import com.smartspend.app.ui.theme.ColorExpenseDark
import com.smartspend.app.ui.theme.ColorIncomeDark
import com.smartspend.app.ui.theme.ColorWarning
import com.smartspend.app.ui.theme.DarkBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    budgetViewModel: BudgetViewModel,
    openBudgetOnStart: Boolean = false,
    onBudgetOpened: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showBudgetDialog by remember { mutableStateOf(false) }
    var showOemGuideDialog by remember { mutableStateOf(false) }
    var showRuleTesterDialog by remember { mutableStateOf(false) }
    var showRuleBuilderDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showBudgetAllocation by remember { mutableStateOf(false) }
    var showAccountManagerDialog by remember { mutableStateOf(false) }
    var showNotificationDebugger by remember { mutableStateOf(false) }
    var exportDialogData by remember { mutableStateOf<Pair<String, String>?>(null) }
    var importDialogType by remember { mutableStateOf<String?>(null) }

    var devTapCount by remember { mutableIntStateOf(0) }
    var showPermissionDetails by remember { mutableStateOf(false) }
    var showEasyRuleTrainer by remember { mutableStateOf(false) }
    var showRuleListSheet by remember { mutableStateOf(false) }
    var ruleTesterInitialData by remember { mutableStateOf<Pair<String, String>?>(null) }
    var ruleBuilderInitialData by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(openBudgetOnStart) {
        if (openBudgetOnStart) {
            showBudgetAllocation = true
            onBudgetOpened()
        }
    }

    if (showBudgetAllocation) {
        BudgetAllocationScreen(
            viewModel = budgetViewModel,
            onBack = { showBudgetAllocation = false }
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pengaturan",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
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
            val allPermissionsGranted = uiState.isListenerActive && uiState.isIslandActive

            if (!allPermissionsGranted || showPermissionDetails) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Layanan & Izin Sistem",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (allPermissionsGranted) {
                            TextButton(onClick = { showPermissionDetails = false }) {
                                Text("Sembunyikan", fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (!uiState.isListenerActive || showPermissionDetails) {
                    item {
                        SquircleCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Akses Notifikasi Bank",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (uiState.isListenerActive) "Aktif • Membaca notifikasi lokal" else "Belum Diizinkan • Ketuk untuk aktifkan",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (uiState.isListenerActive) ColorIncomeDark else ColorWarning
                                    )
                                }

                                Button(
                                    onClick = {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (uiState.isListenerActive) MaterialTheme.colorScheme.surfaceVariant else AccentBlue
                                    )
                                ) {
                                    Text(
                                        text = if (uiState.isListenerActive) "Cek" else "Aktifkan",
                                        color = if (uiState.isListenerActive) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                                    )
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            SettingsRowItem(
                                icon = Icons.Default.Shield,
                                iconTint = AccentBlue,
                                title = "Panduan Anti-Kill Infinix / OEM",
                                subtitle = "Cegah sistem mematikan SmartSpend di latar belakang",
                                onClick = { showOemGuideDialog = true }
                            )
                        }
                    }
                }

                if (!uiState.isIslandActive || showPermissionDetails) {
                    item {
                        SquircleCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Dynamic Island di Layar Utama",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (uiState.isIslandActive) {
                                            "Aktif • Tampil di atas layar utama"
                                        } else {
                                            "Belum Diizinkan • Butuh izin Tampilkan di atas aplikasi lain"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (uiState.isIslandActive) ColorIncomeDark else ColorWarning
                                    )
                                }

                                Button(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (uiState.isIslandActive) MaterialTheme.colorScheme.surfaceVariant else AccentBlue
                                    )
                                ) {
                                    Text(
                                        text = if (uiState.isIslandActive) "Cek" else "Aktifkan",
                                        color = if (uiState.isIslandActive) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "SmartSpend memakai izin standar \u201cTampilkan di atas aplikasi lain\u201d " +
                                    "untuk menggambar kartu ringkasan pintar (Dynamic Island) di layar depan. " +
                                    "SmartSpend TIDAK memakai izin Aksesibilitas sehingga aman dan tidak memicu " +
                                    "peringatan risiko di aplikasi perbankan (SeaBank, dsb). Seluruh data transaksi tetap aman & lokal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 2: Budget & Master Accounts
            item {
                Text(
                    text = "Anggaran & Rekening",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SquircleCard {
                    SettingsRowItem(
                        icon = Icons.Default.AccountBalanceWallet,
                        iconTint = AccentBlue,
                        title = "Batas Anggaran Bulanan",
                        subtitle = "Batas: ${CurrencyFormatter.format(uiState.monthlyBudgetLimit)} (Peringatan di ${uiState.alertThresholdPct}%)",
                        onClick = { showBudgetDialog = true }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsRowItem(
                        icon = Icons.Default.PieChart,
                        iconTint = ColorIncomeDark,
                        title = "Alokasi Budget per Kategori",
                        subtitle = "Atur batas maksimal belanja, makan, tagihan, dll",
                        onClick = { showBudgetAllocation = true }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsRowItem(
                        icon = Icons.Default.CreditCard,
                        iconTint = ColorIncomeDark,
                        title = "Kelola Dompet & Rekening (${uiState.accounts.size})",
                        subtitle = "Master bank, e-wallet, dan uang tunai",
                        onClick = { showAccountManagerDialog = true }
                    )
                }
            }

            // Section: Notifikasi & Otomatisasi Bank (Accessible to all users without Dev Mode)
            item {
                Text(
                    text = "Notifikasi & Otomatisasi Bank",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SquircleCard {
                    SettingsRowItem(
                        icon = Icons.Default.AutoFixHigh,
                        iconTint = ColorIncomeDark,
                        title = "Latih Notifikasi Aplikasi Bank & Dompet",
                        subtitle = "Ajari SmartSpend membaca pemasukan & pengeluaran dari SeaBank, BCA, DANA, GoPay, dll tanpa rumus",
                        onClick = { showEasyRuleTrainer = true }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsRowItem(
                        icon = Icons.Default.Tune,
                        iconTint = AccentBlue,
                        title = "Kelola Aturan Notifikasi (${uiState.rules.size})",
                        subtitle = "Aktifkan atau matikan deteksi per aplikasi",
                        onClick = { showRuleListSheet = true }
                    )

                    if (allPermissionsGranted) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        SettingsRowItem(
                            icon = Icons.Default.CheckCircle,
                            iconTint = ColorIncomeDark,
                            title = "Status Izin Sistem: Semua Aktif",
                            subtitle = "Akses Notifikasi & Dynamic Island berjalan normal. Ketuk untuk kelola.",
                            onClick = { showPermissionDetails = true }
                        )
                    }
                }
            }

            // Section 3: DEV MODE SECTION (Hidden by default, unlocks with 7 taps)
            if (uiState.isDevModeEnabled) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mode Pengembang (Aktif)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentBlue.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "DEV UNLOCKED",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                item {
                    SquircleCard {
                        SettingsRowItem(
                            icon = Icons.Default.BugReport,
                            iconTint = AccentBlue,
                            title = "Notification Event Debugger",
                            subtitle = "Pantau raw notification, package name & regex matching",
                            onClick = { showNotificationDebugger = true }
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        SettingsRowItem(
                            icon = Icons.Default.NotificationsActive,
                            iconTint = ColorIncomeDark,
                            title = "Kirim Notifikasi Uji Coba",
                            subtitle = "Uji heads-up banner & push notifikasi lokal di HP ini",
                            onClick = {
                                viewModel.sendTestPushNotification()
                                Toast.makeText(context, "Notifikasi uji coba dikirim!", Toast.LENGTH_SHORT).show()
                            }
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        SettingsRowItem(
                            icon = Icons.Default.Science,
                            iconTint = AccentBlue,
                            title = "Uji Coba Rule Notifikasi",
                            subtitle = "Tempel teks notifikasi nyata untuk mengetes parsing",
                            onClick = {
                                ruleTesterInitialData = null
                                showRuleTesterDialog = true
                            }
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        SettingsRowItem(
                            icon = Icons.Default.AddModerator,
                            iconTint = ColorIncomeDark,
                            title = "Ajari Aplikasi (Rule Builder)",
                            subtitle = "Tambahkan aturan regex kustom untuk bank/aplikasi baru",
                            onClick = {
                                ruleBuilderInitialData = null
                                showRuleBuilderDialog = true
                            }
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        SettingsRowItem(
                            icon = Icons.Default.Upload,
                            iconTint = AccentBlue,
                            title = "Export Aturan Notifikasi (JSON)",
                            subtitle = "Bagikan aturan regex yang telah diajari ke HP lain",
                            onClick = {
                                viewModel.exportRulesJson { json ->
                                    exportDialogData = Pair("Export Aturan Notifikasi", json)
                                }
                            }
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        SettingsRowItem(
                            icon = Icons.Default.Download,
                            iconTint = ColorIncomeDark,
                            title = "Import Aturan Notifikasi (JSON)",
                            subtitle = "Impor aturan regex tanpa perlu konfigurasi ulang",
                            onClick = {
                                importDialogType = "RULES"
                            }
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        SettingsRowItem(
                            icon = Icons.Default.Lock,
                            iconTint = ColorExpenseDark,
                            title = "Kunci Mode Pengembang",
                            subtitle = "Sembunyikan kembali menu developer dan debugger",
                            onClick = {
                                viewModel.setDevMode(false)
                                devTapCount = 0
                                Toast.makeText(context, "Mode Pengembang dinonaktifkan", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // Section 4: Privacy & Data
            item {
                Text(
                    text = "Privasi & Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SquircleCard {
                    SettingsRowItem(
                        icon = Icons.Default.Backup,
                        iconTint = AccentBlue,
                        title = "Backup Database (JSON)",
                        subtitle = "Simpan seluruh transaksi, akun, dan budget lokal",
                        onClick = {
                            viewModel.backupDatabaseJson { json ->
                                exportDialogData = Pair("Backup Database SmartSpend", json)
                            }
                        }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsRowItem(
                        icon = Icons.Default.Restore,
                        iconTint = ColorIncomeDark,
                        title = "Restore Database (JSON)",
                        subtitle = "Pulihkan data dari file/teks backup JSON",
                        onClick = {
                            importDialogType = "DATABASE"
                        }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsRowItem(
                        icon = Icons.Default.DeleteForever,
                        iconTint = ColorExpenseDark,
                        title = "Hapus Seluruh Data",
                        subtitle = "Menghapus database lokal SQLCipher SmartSpend",
                        onClick = { showClearDataDialog = true }
                    )
                }
            }

            // Version footer with 7-tap developer unlock
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (uiState.isDevModeEnabled) {
                                Toast.makeText(context, "Mode Pengembang sudah aktif", Toast.LENGTH_SHORT).show()
                            } else {
                                devTapCount++
                                if (devTapCount in 3..6) {
                                    Toast.makeText(context, "Tinggal ${7 - devTapCount} langkah lagi untuk membuka Mode Pengembang.", Toast.LENGTH_SHORT).show()
                                } else if (devTapCount >= 7) {
                                    viewModel.setDevMode(true)
                                    devTapCount = 0
                                    Toast.makeText(context, "🎉 Mode Pengembang Aktif!", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SmartSpend v1.0.0 (Build 20261103)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (uiState.isDevModeEnabled) "Mode Pengembang Aktif • Zero-Internet Local DB" else "Zero-Internet Offline-First Local DB",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (uiState.isDevModeEnabled) AccentBlue else MaterialTheme.colorScheme.outline
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // Modal Bottom Sheets (iOS style)
    if (showNotificationDebugger) {
        NotificationDebuggerSheet(
            viewModel = viewModel,
            onDismiss = { showNotificationDebugger = false },
            onOpenRuleBuilderWith = { pkg, text ->
                ruleBuilderInitialData = Pair(pkg, text)
                showNotificationDebugger = false
                showEasyRuleTrainer = true
            },
            onOpenRuleTesterWith = { pkg, text ->
                ruleTesterInitialData = Pair(pkg, text)
                showNotificationDebugger = false
                showRuleTesterDialog = true
            }
        )
    }

    if (showEasyRuleTrainer) {
        EasyRuleTrainerSheet(
            viewModel = viewModel,
            initialPackage = ruleBuilderInitialData?.first,
            initialSampleText = ruleBuilderInitialData?.second,
            onDismiss = {
                showEasyRuleTrainer = false
                ruleBuilderInitialData = null
            }
        )
    }

    if (showRuleListSheet) {
        RuleListSheet(
            viewModel = viewModel,
            onDismiss = { showRuleListSheet = false },
            onAddRuleClick = {
                showRuleListSheet = false
                showEasyRuleTrainer = true
            }
        )
    }

    if (showBudgetDialog) {
        BudgetDialog(
            currentLimit = uiState.monthlyBudgetLimit,
            currentThreshold = uiState.alertThresholdPct,
            onDismiss = { showBudgetDialog = false },
            onSave = { limit, threshold ->
                viewModel.updateMonthlyBudget(limit, threshold)
                showBudgetDialog = false
            }
        )
    }

    if (showOemGuideDialog) {
        OemGuideDialog(onDismiss = { showOemGuideDialog = false })
    }

    if (showRuleTesterDialog) {
        RuleTesterDialog(
            viewModel = viewModel,
            initialPackage = ruleTesterInitialData?.first,
            initialText = ruleTesterInitialData?.second,
            onDismiss = {
                showRuleTesterDialog = false
                ruleTesterInitialData = null
            }
        )
    }

    if (showRuleBuilderDialog) {
        RuleBuilderDialog(
            viewModel = viewModel,
            initialPackage = ruleBuilderInitialData?.first,
            initialText = ruleBuilderInitialData?.second,
            onDismiss = {
                showRuleBuilderDialog = false
                ruleBuilderInitialData = null
            }
        )
    }

    if (showClearDataDialog) {
        ClearDataSheet(
            onDismiss = { showClearDataDialog = false },
            onConfirm = {
                viewModel.clearAllData()
                showClearDataDialog = false
            }
        )
    }

    if (showAccountManagerDialog) {
        AccountManagerDialog(
            accounts = uiState.accounts,
            onDismiss = { showAccountManagerDialog = false },
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

    exportDialogData?.let { (title, json) ->
        JsonExportDialog(
            title = title,
            json = json,
            onDismiss = { exportDialogData = null }
        )
    }

    importDialogType?.let { type ->
        val isRules = type == "RULES"
        JsonImportDialog(
            title = if (isRules) "Import Aturan Notifikasi (JSON)" else "Restore Database (JSON)",
            hint = if (isRules) "Tempel JSON aturan notifikasi dari HP lain:" else "PENTING: Restore akan menambahkan data akun, transaksi, dan utang dari backup JSON:",
            onDismiss = { importDialogType = null },
            onApply = { json ->
                if (isRules) {
                    viewModel.importRulesJson(json) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (success) importDialogType = null
                    }
                } else {
                    viewModel.restoreDatabaseJson(json) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (success) importDialogType = null
                    }
                }
            }
        )
    }
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun DebtCardItem(
    debt: DebtEntity,
    onPaymentClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val isOwe = debt.direction == "OWE"
    val remaining = debt.principalAmount - debt.paidAmount
    val isPaid = debt.status == "PAID"

    SquircleCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isOwe) ColorExpenseDark else ColorIncomeDark)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${if (isOwe) "Saya Berutang ke " else "Berutang ke Saya: "}${debt.counterpartyName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isPaid) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LUNAS",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorIncomeDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Sisa: ${CurrencyFormatter.format(remaining)} (dari ${CurrencyFormatter.format(debt.principalAmount)})",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        debt.dueDate?.let { due ->
            val dueStr = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(Date(due))
            Text(
                text = "Jatuh tempo: $dueStr",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!isPaid) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isOwe) {
                    OutlinedButton(
                        onClick = onShareClick,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Kirim Tagihan", maxLines = 1)
                    }
                }
                Button(
                    onClick = onPaymentClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Catat Bayar", color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun PlanCardItem(
    plan: PurchasePlanEntity,
    onDecideClick: () -> Unit
) {
    val isReady = plan.status == "READY"
    val isWaiting = plan.status == "WAITING"

    SquircleCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = plan.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when (plan.status) {
                            "READY" -> ColorIncomeDark.copy(alpha = 0.2f)
                            "WAITING" -> AccentBlue.copy(alpha = 0.2f)
                            "BOUGHT" -> ColorIncomeDark.copy(alpha = 0.2f)
                            "SKIPPED" -> ColorExpenseDark.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = when (plan.status) {
                        "READY" -> "SIAP DIPUTUSKAN"
                        "WAITING" -> "MASA TUNGGU"
                        "BOUGHT" -> "DIBELI"
                        "SKIPPED" -> "HEMAT"
                        "POSTPONED" -> "DITUNDA"
                        else -> plan.status
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (plan.status) {
                        "READY" -> ColorIncomeDark
                        "WAITING" -> AccentBlue
                        "BOUGHT" -> ColorIncomeDark
                        "SKIPPED" -> ColorExpenseDark
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Estimasi: ${CurrencyFormatter.format(plan.estimatedPrice)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        val decideDateStr = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(Date(plan.decideAt))
        Text(
            text = if (isReady) "Masa tunggu berakhir. Tentukan keputusanmu!" else "Dapat diputuskan pada: $decideDateStr",
            style = MaterialTheme.typography.labelSmall,
            color = if (isReady) ColorIncomeDark else MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isReady || isWaiting) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onDecideClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReady) ColorIncomeDark else AccentBlue
                )
            ) {
                Text(if (isReady) "Tentukan Sekarang (Beli / Skip)" else "Tinjau Rencana Beli", color = Color.White)
            }
        }
    }
}

// iOS Modal Bottom Sheet Implementations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDialog(
    currentLimit: Long,
    currentThreshold: Int,
    onDismiss: () -> Unit,
    onSave: (Long, Int) -> Unit
) {
    var limitStr by remember { mutableStateOf(currentLimit.toString()) }
    var thresholdStr by remember { mutableStateOf(currentThreshold.toString()) }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Atur Batas Budget Bulanan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = limitStr,
                onValueChange = { limitStr = it.filter { c -> c.isDigit() } },
                label = { Text("Batas Budget (Rp)") },
                prefix = { Text("Rp ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = thresholdStr,
                onValueChange = { thresholdStr = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Ambang Peringatan Notifikasi (%)") },
                suffix = { Text("%") },
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
                        val l = limitStr.toLongOrNull() ?: currentLimit
                        val t = thresholdStr.toIntOrNull() ?: currentThreshold
                        onSave(l, t)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OemGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

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
                text = "Panduan Anti-Kill OEM",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sistem Android bawaan (Infinix, Xiaomi, Samsung, dsb) mematikan service latar belakang secara agresif. Ikuti langkah ini agar transaksi perbankan selalu otomatis tercatat:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Infinix / Tecno (XOS): Buka aplikasi Phone Master -> Auto-start Management -> Aktifkan SmartSpend. Di Pengaturan Baterai, pilih SmartSpend -> Jangan Optimalkan.", style = MaterialTheme.typography.bodySmall)
                    Text("• Xiaomi (MIUI / HyperOS): Pengaturan -> Aplikasi -> Kelola Aplikasi -> SmartSpend -> Autostart ON, Penghemat Baterai -> Tanpa Batasan.", style = MaterialTheme.typography.bodySmall)
                    Text("• Samsung: Pengaturan Baterai -> Batas Penggunaan Latar Belakang -> Tambahkan ke Aplikasi yang Tidak Pernah Tidur.", style = MaterialTheme.typography.bodySmall)
                    Text("• OPPO / Vivo / Realme: Izinkan Auto-launch dan Background activity di Info Aplikasi.", style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Tutup")
                }
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Buka Pengaturan", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleTesterDialog(
    viewModel: SettingsViewModel,
    initialPackage: String? = null,
    initialText: String? = null,
    onDismiss: () -> Unit
) {
    var pkg by remember { mutableStateOf(initialPackage ?: "com.bca.mybca.omni.android") }
    var sampleText by remember { mutableStateOf(initialText ?: "Transfer Rp 150.000 ke BUDI SANTOSO berhasil.") }
    val uiState by viewModel.uiState.collectAsState()

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
                text = "Uji Coba Rule Notifikasi",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = pkg,
                onValueChange = { pkg = it },
                label = { Text("Package Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = sampleText,
                onValueChange = { sampleText = it },
                label = { Text("Teks Notifikasi Sampel") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { viewModel.testParse(pkg, sampleText) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Jalankan Parser", color = Color.White)
            }

            uiState.testParseResult?.let { res ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Status: Berhasil Dicocokkan!", fontWeight = FontWeight.Bold, color = ColorIncomeDark)
                        Text("Tipe: ${res.type}")
                        Text("Nominal: ${CurrencyFormatter.format(res.amount)}")
                        Text("Merchant: ${res.merchant ?: "-"}")
                        Text("Tingkat Keyakinan: ${(res.confidence * 100).toInt()}%")
                    }
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Tutup")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyRuleTrainerSheet(
    viewModel: SettingsViewModel,
    initialPackage: String? = null,
    initialSampleText: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val rawEvents by viewModel.rawEvents.collectAsState(initial = emptyList())
    var showAppPickerDialog by remember { mutableStateOf(false) }

    val presetApps = remember {
        listOf(
            Triple("SeaBank", "SEABANK", "id.co.bankbkemobile.digitalbank"),
            Triple("BCA Mobile", "BCA", "com.bca"),
            Triple("myBCA", "BCA", "com.bca.mybca.omni.android"),
            Triple("Livin Mandiri", "MANDIRI", "id.bmri.livin"),
            Triple("BRImo", "BRI", "id.co.bri.brimo"),
            Triple("Wondr BNI", "BNI", "id.bni.wondr"),
            Triple("BNI Mobile", "BNI", "src.mwallet.bni.co.id"),
            Triple("Bank Jago", "JAGO", "com.jago.app"),
            Triple("Bank Saqu", "SAQU", "id.bankastra.mobilebanking"),
            Triple("Allo Bank", "ALLO", "com.allobank.indo"),
            Triple("DANA", "DANA", "id.dana"),
            Triple("GoPay (App)", "GOPAY", "id.gopay"),
            Triple("Gojek", "GOPAY", "com.gojek.app"),
            Triple("OVO", "OVO", "id.ovo"),
            Triple("ShopeePay", "SHOPEEPAY", "com.shopee.id"),
            Triple("Ketik Manual", "CUSTOM", "")
        )
    }

    var selectedApp by remember {
        mutableStateOf(presetApps.find { it.third == initialPackage } ?: presetApps.first())
    }
    var customAppName by remember { mutableStateOf("") }
    var customPackageName by remember { mutableStateOf(initialPackage ?: "") }

    var txType by remember { mutableStateOf("EXPENSE") }

    val defaultExpenseKeywords = listOf("Transfer ke", "QRIS", "Pembayaran", "Transaksi Berhasil", "Bayar")
    val defaultIncomeKeywords = listOf("Transfer Masuk", "Terima Saldo", "Top Up", "Dana Masuk", "Menerima")

    var selectedKeyword by remember { mutableStateOf("Transfer ke") }
    var customKeyword by remember { mutableStateOf("") }

    var sampleText by remember { mutableStateOf(initialSampleText ?: "") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(txType) {
        selectedKeyword = if (txType == "EXPENSE") defaultExpenseKeywords.first() else defaultIncomeKeywords.first()
    }

    LaunchedEffect(sampleText, selectedApp, txType, selectedKeyword, customKeyword) {
        if (sampleText.isBlank()) {
            testResult = null
            isTestSuccess = false
            return@LaunchedEffect
        }
        val effectiveKeyword = customKeyword.trim().ifEmpty { selectedKeyword }
        val pattern = """(?i).*?${Regex.escape(effectiveKeyword)}.*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:di|ke|dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)"""
        try {
            val reg = Regex(pattern)
            val match = reg.find(sampleText)
            if (match != null) {
                val rawAmt = match.groups["amt"]?.value ?: ""
                val amt = AmountNormalizer.normalize(rawAmt) ?: 0L
                val mrc = match.groups["mrc"]?.value?.trim() ?: "Umum"
                testResult = "✓ Terdeteksi: Rp ${CurrencyFormatter.format(amt)} • $mrc (${if (txType == "EXPENSE") "Pengeluaran" else "Pemasukan"})"
                isTestSuccess = true
            } else {
                val fallbackMatch = Regex("""(?i)rp\.?\s?(?<amt>[\d.,]+)""").find(sampleText)
                if (fallbackMatch != null) {
                    val rawAmt = fallbackMatch.groups["amt"]?.value ?: ""
                    val amt = AmountNormalizer.normalize(rawAmt) ?: 0L
                    testResult = "⚠️ Nominal terdeteksi: Rp ${CurrencyFormatter.format(amt)}, tapi belum mengandung kata kunci \"$effectiveKeyword\""
                    isTestSuccess = false
                } else {
                    testResult = "❌ Nominal nominal Rp tidak ditemukan dalam teks sampel"
                    isTestSuccess = false
                }
            }
        } catch (_: Exception) {
            testResult = null
            isTestSuccess = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        if (showAppPickerDialog) {
            AppPickerDialog(
                onDismiss = { showAppPickerDialog = false },
                onAppSelected = { name, pkg ->
                    customAppName = name
                    customPackageName = pkg
                    selectedApp = Triple(name, "CUSTOM", pkg)
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Latih Notifikasi Bank",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ajari SmartSpend membaca notifikasi tanpa rumus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            // Step 1: Pilih Aplikasi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1. Pilih Aplikasi Bank / Dompet",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { showAppPickerDialog = true }) {
                    Icon(imageVector = Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pilih dari HP", fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button to pick installed apps
                FilterChip(
                    selected = false,
                    onClick = { showAppPickerDialog = true },
                    label = { Text("📱 Semua App di HP...", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = AccentBlue.copy(alpha = 0.15f),
                        labelColor = AccentBlue
                    )
                )

                presetApps.forEach { app ->
                    val isSelected = (selectedApp.second == app.second && selectedApp.first == app.first) ||
                            (selectedApp.second != "CUSTOM" && selectedApp.third == app.third)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedApp = app
                            if (app.second != "CUSTOM") {
                                customAppName = ""
                                customPackageName = app.third
                            }
                        },
                        label = { Text(app.first, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                            selectedLabelColor = AccentBlue
                        )
                    )
                }
            }

            if (selectedApp.second == "CUSTOM") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customAppName,
                        onValueChange = { customAppName = it },
                        label = { Text("Nama Aplikasi (misal: Blu BCA / Bank Jago)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customPackageName,
                        onValueChange = { customPackageName = it },
                        label = { Text("Package Name (otomatis terisi bila pilih dari HP)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Quick Auto-fill from recent notifications
            if (rawEvents.isNotEmpty()) {
                Text(
                    text = "⚡ Notifikasi Terakhir Masuk di HP (Ketuk untuk otomatis isi):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentBlue
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rawEvents.take(4).forEach { event ->
                        val textContent = event.text ?: event.title ?: ""
                        val appLabel = runCatching {
                            val pm = context.packageManager
                            val ai = pm.getApplicationInfo(event.packageName, 0)
                            pm.getApplicationLabel(ai).toString()
                        }.getOrDefault(event.packageName.substringAfterLast("."))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkBorder),
                            modifier = Modifier
                                .widthIn(max = 240.dp)
                                .clickable {
                                    customAppName = appLabel
                                    customPackageName = event.packageName
                                    selectedApp = Triple(appLabel, "CUSTOM", event.packageName)
                                    sampleText = textContent
                                    // Auto infer txType from keywords
                                    val lower = textContent.lowercase()
                                    if (lower.contains("masuk") || lower.contains("menerima") || lower.contains("top up") || lower.contains("terima")) {
                                        txType = "INCOME"
                                    } else {
                                        txType = "EXPENSE"
                                    }
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = appLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = textContent,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // Step 2: Jenis Transaksi
            Text(
                text = "2. Tentukan Aliran Uang",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isExpense = txType == "EXPENSE"
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { txType = "EXPENSE" }
                        .border(
                            width = if (isExpense) 2.dp else 1.dp,
                            color = if (isExpense) ColorExpenseDark else DarkBorder,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    color = if (isExpense) ColorExpenseDark.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ColorExpenseDark.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.ArrowOutward, contentDescription = null, tint = ColorExpenseDark)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Pengeluaran",
                            fontWeight = FontWeight.Bold,
                            color = if (isExpense) ColorExpenseDark else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Belanja, QRIS, Bayar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                val isIncome = txType == "INCOME"
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { txType = "INCOME" }
                        .border(
                            width = if (isIncome) 2.dp else 1.dp,
                            color = if (isIncome) ColorIncomeDark else DarkBorder,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    color = if (isIncome) ColorIncomeDark.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ColorIncomeDark.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.CallReceived, contentDescription = null, tint = ColorIncomeDark)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Pemasukan",
                            fontWeight = FontWeight.Bold,
                            color = if (isIncome) ColorIncomeDark else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Transfer Masuk, Gaji",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Step 3: Kata Kunci Pemicu
            Text(
                text = "3. Kata Kunci Pemicu Notifikasi",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            val activeKeywords = if (txType == "EXPENSE") defaultExpenseKeywords else defaultIncomeKeywords
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeKeywords.forEach { kw ->
                    val isSelected = selectedKeyword == kw && customKeyword.isBlank()
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedKeyword = kw
                            customKeyword = ""
                        },
                        label = { Text(kw, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (txType == "EXPENSE") ColorExpenseDark.copy(alpha = 0.2f) else ColorIncomeDark.copy(alpha = 0.2f),
                            selectedLabelColor = if (txType == "EXPENSE") ColorExpenseDark else ColorIncomeDark
                        )
                    )
                }
            }

            OutlinedTextField(
                value = customKeyword,
                onValueChange = { customKeyword = it },
                label = { Text("Atau ketik kata kunci sendiri") },
                placeholder = { Text("misal: debit, potongan, transfer masuk") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Step 4: Kotak Tes / Tempel Notifikasi
            Text(
                text = "4. Uji Coba (Tempel Contoh Notifikasi Bank)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = sampleText,
                onValueChange = { sampleText = it },
                placeholder = {
                    Text(
                        if (txType == "EXPENSE") "Contoh: Transfer keluar sebesar Rp 50.000 ke Shopee berhasil"
                        else "Contoh: Transfer masuk sebesar Rp 100.000 dari BUDI berhasil"
                    )
                },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            testResult?.let { res ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isTestSuccess) ColorIncomeDark.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = res,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isTestSuccess) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isTestSuccess) ColorIncomeDark else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Simpan Button
            val effectiveAppName = if (selectedApp.second == "CUSTOM") customAppName.trim().ifEmpty { "Aplikasi Kustom" } else selectedApp.first
            val effectivePkg = if (selectedApp.second == "CUSTOM") customPackageName.trim() else selectedApp.third
            val effectiveProvider = if (selectedApp.second == "CUSTOM") "CUSTOM" else selectedApp.second
            val effectiveKeyword = customKeyword.trim().ifEmpty { selectedKeyword }

            val canSave = effectivePkg.isNotBlank() && effectiveKeyword.isNotBlank()

            Button(
                onClick = {
                    val pattern = """(?i).*?${Regex.escape(effectiveKeyword)}.*?rp\.?\s?(?<amt>[\d.,]+)(?:.*?(?:di|ke|dari)\s+(?<mrc>[a-zA-Z0-9\s.]+?))?(?:\s+berhasil|\s+sukses|$)"""
                    val ruleName = "$effectiveAppName - ${if (txType == "EXPENSE") "Pengeluaran" else "Pemasukan"} ($effectiveKeyword)"
                    viewModel.addCustomRule(
                        providerCode = effectiveProvider,
                        packageName = effectivePkg,
                        ruleName = ruleName,
                        pattern = pattern,
                        txType = txType
                    )
                    Toast.makeText(context, "Aturan untuk $effectiveAppName berhasil disimpan dan aktif!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan & Aktifkan Aturan", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListSheet(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onAddRuleClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

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
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Aturan Notifikasi Aktif",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.rules.size} aturan terdaftar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onAddRuleClick) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Aturan", tint = AccentBlue)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.rules, key = { it.id }) { rule ->
                    val isIncome = rule.txType == "INCOME"
                    SquircleCard(
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isIncome) ColorIncomeDark else ColorExpenseDark)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = rule.ruleName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${rule.providerCode} • ${if (isIncome) "Pemasukan" else "Pengeluaran"} • Cocok: ${rule.matchCount}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { viewModel.toggleRule(rule) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onAppSelected: (appName: String, packageName: String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val installedApps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        resolveInfos.map { ri ->
            Pair(ri.loadLabel(pm).toString(), ri.activityInfo.packageName)
        }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.first.contains(searchQuery, ignoreCase = true) ||
            it.second.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Pilih Aplikasi dari HP",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pilih aplikasi bank atau e-wallet yang terpasang di HP ini",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama aplikasi...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    }
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredApps, key = { it.second }) { (name, pkg) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onAppSelected(name, pkg)
                                    onDismiss()
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(AccentBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name.firstOrNull()?.uppercase() ?: "A",
                                        fontWeight = FontWeight.Bold,
                                        color = AccentBlue,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = pkg,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.5.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleBuilderDialog(
    viewModel: SettingsViewModel,
    initialPackage: String? = null,
    initialText: String? = null,
    onDismiss: () -> Unit
) {
    var provider by remember { mutableStateOf("MANDIRI") }
    var pkg by remember { mutableStateOf(initialPackage ?: "id.bmri.livin") }
    var ruleName by remember { mutableStateOf("Livin Pembayaran") }
    var regexPattern by remember {
        mutableStateOf(
            if (!initialText.isNullOrBlank()) """(?i)transaksi\s+sebesar\s+rp\.?\s?(?<amt>[\d.,]+)\s+di\s+(?<mrc>.+?)\s+berhasil"""
            else """(?i)transaksi\s+sebesar\s+rp\.?\s?(?<amt>[\d.,]+)\s+di\s+(?<mrc>.+?)\s+berhasil"""
        )
    }
    var txType by remember { mutableStateOf("EXPENSE") }

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Ajari Aplikasi (Rule Builder)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text("Provider Code (BCA, MANDIRI, DANA, dll)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = pkg,
                onValueChange = { pkg = it },
                label = { Text("Package Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = ruleName,
                onValueChange = { ruleName = it },
                label = { Text("Nama Aturan") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = regexPattern,
                onValueChange = { regexPattern = it },
                label = { Text("Pola Regex (?<amt>, ?<mrc>)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipItem(
                    label = "Pengeluaran",
                    isSelected = txType == "EXPENSE",
                    onClick = { txType = "EXPENSE" },
                    modifier = Modifier.weight(1f)
                )
                FilterChipItem(
                    label = "Pemasukan",
                    isSelected = txType == "INCOME",
                    onClick = { txType = "INCOME" },
                    modifier = Modifier.weight(1f)
                )
            }

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
                        viewModel.addCustomRule(provider.trim(), pkg.trim(), ruleName.trim(), regexPattern.trim(), txType)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Simpan Rule", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearDataSheet(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Hapus Semua Data?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ColorExpenseDark
            )
            Text(
                text = "Tindakan ini akan menghapus semua riwayat transaksi, akun, utang, dan rencana secara permanen dari perangkat lokal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorExpenseDark)
                ) {
                    Text("Ya, Hapus Semua", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDebtDialog(
    accounts: List<com.smartspend.app.core.database.entity.AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Long?, Long?, Boolean, String?) -> Unit
) {
    val context = LocalContext.current
    var direction by remember { mutableStateOf("OWE") }
    var counterparty by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var recordToBalance by remember { mutableStateOf(true) }
    var selectedAccId by remember { mutableStateOf(accounts.firstOrNull()?.id) }

    val pickContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        if (nameIdx != -1) {
                            val name = c.getString(nameIdx)
                            if (!name.isNullOrBlank()) counterparty = name
                        }
                        val hasPhoneIdx = c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                        val hasPhone = if (hasPhoneIdx != -1) c.getInt(hasPhoneIdx) else 0
                        if (hasPhone > 0) {
                            val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                            if (idIdx != -1) {
                                val contactId = c.getString(idIdx)
                                val phoneCur = context.contentResolver.query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    null,
                                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                    arrayOf(contactId),
                                    null
                                )
                                phoneCur?.use { pc ->
                                    if (pc.moveToFirst()) {
                                        val pIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                        if (pIdx != -1) {
                                            phoneNumber = pc.getString(pIdx).replace(" ", "").replace("-", "")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Catat Utang-Piutang Baru",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipItem(
                    label = "Saya Berutang",
                    isSelected = direction == "OWE",
                    onClick = { direction = "OWE" },
                    modifier = Modifier.weight(1f)
                )
                FilterChipItem(
                    label = "Piutang Saya",
                    isSelected = direction == "OWED",
                    onClick = { direction = "OWED" },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    label = { Text("Nama Pihak Lawan") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { pickContactLauncher.launch(null) },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .background(AccentBlue.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Contacts, contentDescription = "Pilih Kontak", tint = AccentBlue)
                }
            }

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Nomor HP / WA (Opsional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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
                value = note,
                onValueChange = { note = it },
                label = { Text("Catatan (Opsional)") },
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
                        val amt = amountStr.toLongOrNull() ?: 0L
                        if (amt > 0 && counterparty.isNotBlank()) {
                            val due = System.currentTimeMillis() + (7 * 86_400_000L)
                            val finalNote = if (phoneNumber.isNotBlank()) {
                                if (note.isNotBlank()) "WA: $phoneNumber | $note" else "WA: $phoneNumber"
                            } else {
                                note.ifBlank { null }
                            }
                            onSave(direction, counterparty.trim(), amt, due, selectedAccId, recordToBalance, finalNote)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendDebtReminderDialog(
    debt: DebtEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val remaining = debt.principalAmount - debt.paidAmount
    val remainingStr = CurrencyFormatter.format(remaining)
    val dateStr = debt.dueDate?.let { SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(Date(it)) } ?: "segera"

    val extractedPhone = remember(debt.note) {
        val note = debt.note.orEmpty()
        val regex = Regex("""WA:\s*([0-9+\s\-]+)""")
        regex.find(note)?.groupValues?.get(1)?.replace(" ", "")?.replace("-", "") ?: ""
    }

    var targetPhone by remember { mutableStateOf(extractedPhone) }

    val templates = remember(debt.counterpartyName, remainingStr, dateStr) {
        listOf(
            "Santai" to "Halo ${debt.counterpartyName}, cuma mau ngingetin titipan/pinjaman sebesar $remainingStr yang jatuh tempo $dateStr ya. Nanti kalau senggang boleh ditransfer. Makasih banyak! 🙏",
            "Sopan" to "Halo Kak ${debt.counterpartyName}, semoga harimu menyenangkan. Sekadar mengingatkan kewajiban pinjaman sebesar $remainingStr dengan jatuh tempo $dateStr. Terima kasih sebelumnya atas kerjasamanya 🙏",
            "Formal" to "Yth. ${debt.counterpartyName}, pemberitahuan pengingat perihal pinjaman sebesar $remainingStr yang jatuh tempo pada $dateStr. Mohon kesediaannya untuk melakukan pembayaran. Terima kasih.",
            "Singkat" to "Halo ${debt.counterpartyName}, jangan lupa transfer $remainingStr ya. Makasih!"
        )
    }

    var selectedTemplateIndex by remember { mutableIntStateOf(0) }
    var customMessage by remember { mutableStateOf(templates[0].second) }

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
                text = "Kirim Pengingat Tagihan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Pilih Template Pesan:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(templates.indices.toList()) { idx ->
                    val isSelected = selectedTemplateIndex == idx
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTemplateIndex = idx
                            customMessage = templates[idx].second
                        },
                        label = { Text(templates[idx].first) }
                    )
                }
            }

            OutlinedTextField(
                value = targetPhone,
                onValueChange = { targetPhone = it },
                label = { Text("Nomor WhatsApp (Opsional)") },
                placeholder = { Text("Contoh: 08123456789") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = customMessage,
                onValueChange = { customMessage = it },
                label = { Text("Isi Pesan") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 150.dp),
                textStyle = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = {
                    val normalizedPhone = targetPhone.trim().let {
                        if (it.startsWith("0")) "62" + it.substring(1)
                        else if (it.startsWith("+62")) it.substring(1)
                        else it
                    }
                    val url = if (normalizedPhone.isNotBlank()) {
                        "https://api.whatsapp.com/send?phone=$normalizedPhone&text=${Uri.encode(customMessage)}"
                    } else {
                        "https://api.whatsapp.com/send?text=${Uri.encode(customMessage)}"
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, customMessage)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Kirim Pengingat"))
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorIncomeDark)
            ) {
                Text("💬 Kirim via WhatsApp", color = Color.White)
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, customMessage)
                    }
                    context.startActivity(Intent.createChooser(intent, "Kirim Pengingat"))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("📤 Bagikan / SMS")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonExportDialog(
    title: String,
    json: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Salin data JSON ini atau klik Bagikan untuk mengirim ke HP lain:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = json,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText(title, json)
                        clipboard.setPrimaryClip(clip)
                        copied = true
                        Toast.makeText(context, "Tersalin ke clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (copied) "✓ Tersalin" else "Salin")
                }

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, title)
                            putExtra(Intent.EXTRA_TEXT, json)
                        }
                        context.startActivity(Intent.createChooser(intent, title))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Bagikan", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonImportDialog(
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val context = LocalContext.current
    var inputJson by remember { mutableStateOf("") }

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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val item = clipboard.primaryClip?.getItemAt(0)
                    val text = item?.text?.toString()
                    if (!text.isNullOrBlank()) {
                        inputJson = text
                    } else {
                        Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("📋 Tempel dari Clipboard", color = MaterialTheme.colorScheme.onSurface)
            }

            OutlinedTextField(
                value = inputJson,
                onValueChange = { inputJson = it },
                label = { Text("Teks JSON") },
                placeholder = { Text("Tempel JSON di sini...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 240.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
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
                        if (inputJson.isNotBlank()) {
                            onApply(inputJson)
                        }
                    },
                    enabled = inputJson.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Terapkan", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPaymentDialog(
    debt: DebtEntity,
    onDismiss: () -> Unit,
    onSave: (Long, String?) -> Unit
) {
    val remaining = debt.principalAmount - debt.paidAmount
    var amountStr by remember { mutableStateOf(remaining.toString()) }
    var note by remember { mutableStateOf("") }

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
                text = "Catat Pembayaran",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Sisa utang: ${CurrencyFormatter.format(remaining)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it.filter { c -> c.isDigit() } },
                label = { Text("Nominal Bayar (Rp)") },
                prefix = { Text("Rp ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Catatan (Opsional)") },
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
                        val amt = amountStr.toLongOrNull() ?: 0L
                        if (amt > 0 && amt <= remaining) {
                            onSave(amt, note.ifBlank { null })
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Simpan Pembayaran", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDecisionDialog(
    plan: PurchasePlanEntity,
    remainingBudget: Long,
    onDismiss: () -> Unit,
    onDecision: (String) -> Unit
) {
    var q1 by remember { mutableStateOf(false) }
    var q2 by remember { mutableStateOf(false) }
    var q3 by remember { mutableStateOf(false) }

    val budgetAfter = remainingBudget - plan.estimatedPrice

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
                text = "Layar Keputusan Rencana Beli",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Barang: ${plan.name} (${CurrencyFormatter.format(plan.estimatedPrice)})",
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Dampak ke budget: Sisa setelah beli ${CurrencyFormatter.format(budgetAfter)}",
                color = if (budgetAfter < 0) ColorExpenseDark else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Text(
                text = "3 Pertanyaan Refleksi:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = q1, onCheckedChange = { q1 = it })
                Text("1. Masih menginginkannya?", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = q2, onCheckedChange = { q2 = it })
                Text("2. Belum punya fungsi serupa?", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = q3, onCheckedChange = { q3 = it })
                Text("3. Tidak mengganggu utang/tagihan?", style = MaterialTheme.typography.bodySmall)
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Button(
                onClick = { onDecision("SKIP") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorIncomeDark)
            ) {
                Text("Tidak Jadi (Simpan Uang!)", color = Color.White)
            }

            OutlinedButton(
                onClick = { onDecision("POSTPONE") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Tunda 3 Hari Lagi")
            }

            Button(
                onClick = { onDecision("BUY") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Beli Sekarang", color = Color.White)
            }
        }
    }
}
