package com.smartspend.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smartspend.app.core.model.TransactionType
import com.smartspend.app.core.widget.WidgetSnapshot
import com.smartspend.app.core.widget.WidgetStateStore
import com.smartspend.app.ui.components.island.SystemIslandOverlayManager
import com.smartspend.app.ui.screens.budget.BudgetViewModel
import com.smartspend.app.ui.screens.fastadd.FastAddBottomSheet
import com.smartspend.app.ui.screens.fastadd.FastAddViewModel
import com.smartspend.app.ui.screens.features.FeaturesScreen
import com.smartspend.app.ui.screens.home.HomeScreen
import com.smartspend.app.ui.screens.home.HomeViewModel
import com.smartspend.app.ui.screens.reports.ReportsScreen
import com.smartspend.app.ui.screens.reports.ReportsViewModel
import com.smartspend.app.ui.screens.settings.SettingsScreen
import com.smartspend.app.ui.screens.settings.SettingsViewModel
import com.smartspend.app.ui.screens.transactions.TransactionsScreen
import com.smartspend.app.ui.screens.transactions.TransactionsViewModel
import com.smartspend.app.ui.theme.AccentBlue
import com.smartspend.app.ui.theme.ElectricLime
import com.smartspend.app.ui.theme.SmartSpendTheme

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val transactionsViewModel: TransactionsViewModel by viewModels()
    private val fastAddViewModel: FastAddViewModel by viewModels()
    private val reportsViewModel: ReportsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val budgetViewModel: BudgetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SmartSpendTheme {
                SmartSpendApp(
                    homeViewModel = homeViewModel,
                    transactionsViewModel = transactionsViewModel,
                    fastAddViewModel = fastAddViewModel,
                    reportsViewModel = reportsViewModel,
                    settingsViewModel = settingsViewModel,
                    budgetViewModel = budgetViewModel,
                    initialIntent = intent
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SystemIslandOverlayManager.onAppForeground(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        homeViewModel.refresh()
        settingsViewModel.loadData()
    }

    override fun onStop() {
        super.onStop()
        SystemIslandOverlayManager.onAppBackground(applicationContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

enum class NavDestination(val label: String, val icon: ImageVector) {
    HOME("Beranda", Icons.Default.Home),
    TRANSACTIONS("Transaksi", Icons.Default.ReceiptLong),
    REPORTS("Laporan", Icons.Default.BarChart),
    SETTINGS("Pengaturan", Icons.Default.Settings)
}

@Composable
fun SmartSpendApp(
    homeViewModel: HomeViewModel,
    transactionsViewModel: TransactionsViewModel,
    fastAddViewModel: FastAddViewModel,
    reportsViewModel: ReportsViewModel,
    settingsViewModel: SettingsViewModel,
    budgetViewModel: BudgetViewModel,
    initialIntent: Intent?
) {
    val context = LocalContext.current
    val snapshot by WidgetStateStore.getSnapshotFlow(context).collectAsState(initial = WidgetSnapshot())

    var currentDestination by remember { mutableStateOf(NavDestination.HOME) }
    var showFastAddModal by remember { mutableStateOf(false) }
    var openBudgetOnSettings by remember { mutableStateOf(false) }
    var showFeatures by remember { mutableStateOf(false) }

    // Request runtime permissions on Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }

        LaunchedEffect(Unit) {
            val needed = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_CONTACTS)
            }
            if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        }
    } else {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    // Handle initial deep links (PRD section 5.1)
    LaunchedEffect(initialIntent) {
        val uri = initialIntent?.data
        if (uri != null && uri.scheme == "smartspend") {
            when (uri.host) {
                "home" -> currentDestination = NavDestination.HOME
                "transactions" -> currentDestination = NavDestination.TRANSACTIONS
                "reports" -> currentDestination = NavDestination.REPORTS
                "settings" -> currentDestination = NavDestination.SETTINGS
                "debts", "plans" -> showFeatures = true
                "review" -> {
                    currentDestination = NavDestination.TRANSACTIONS
                    transactionsViewModel.setSelectedTab(1)
                }
                "add" -> {
                    val type = uri.getQueryParameter("type")
                    if (type == "income") fastAddViewModel.setType(TransactionType.INCOME)
                    else fastAddViewModel.setType(TransactionType.EXPENSE)
                    showFastAddModal = true
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showFeatures) {
            FeaturesScreen(
                viewModel = settingsViewModel,
                onBack = { showFeatures = false }
            )
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                bottomBar = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            // 1. Home
                            NavigationBarItem(
                                selected = currentDestination == NavDestination.HOME,
                                onClick = { currentDestination = NavDestination.HOME },
                                icon = { Icon(NavDestination.HOME.icon, contentDescription = NavDestination.HOME.label) },
                                label = { Text(NavDestination.HOME.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF0C0D12),
                                    selectedTextColor = ElectricLime,
                                    indicatorColor = ElectricLime
                                )
                            )

                            // 2. Transactions
                            NavigationBarItem(
                                selected = currentDestination == NavDestination.TRANSACTIONS,
                                onClick = { currentDestination = NavDestination.TRANSACTIONS },
                                icon = { Icon(NavDestination.TRANSACTIONS.icon, contentDescription = NavDestination.TRANSACTIONS.label) },
                                label = { Text(NavDestination.TRANSACTIONS.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF0C0D12),
                                    selectedTextColor = ElectricLime,
                                    indicatorColor = ElectricLime
                                )
                            )

                            // 3. Reports
                            NavigationBarItem(
                                selected = currentDestination == NavDestination.REPORTS,
                                onClick = { currentDestination = NavDestination.REPORTS },
                                icon = { Icon(NavDestination.REPORTS.icon, contentDescription = NavDestination.REPORTS.label) },
                                label = { Text(NavDestination.REPORTS.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF0C0D12),
                                    selectedTextColor = ElectricLime,
                                    indicatorColor = ElectricLime
                                )
                            )

                            // 4. Settings
                            NavigationBarItem(
                                selected = currentDestination == NavDestination.SETTINGS,
                                onClick = { currentDestination = NavDestination.SETTINGS },
                                icon = { Icon(NavDestination.SETTINGS.icon, contentDescription = NavDestination.SETTINGS.label) },
                                label = { Text(NavDestination.SETTINGS.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF0C0D12),
                                    selectedTextColor = ElectricLime,
                                    indicatorColor = ElectricLime
                                )
                            )
                        }

                        FloatingActionButton(
                            onClick = { showFastAddModal = true },
                            containerColor = ElectricLime,
                            contentColor = Color(0xFF0C0D12),
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-29).dp)
                                .size(58.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tambah Transaksi Cepat",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (currentDestination) {
                        NavDestination.HOME -> HomeScreen(
                            viewModel = homeViewModel,
                            budgetViewModel = budgetViewModel,
                            onNavigateToTransactions = { currentDestination = NavDestination.TRANSACTIONS },
                            onNavigateToReview = {
                                currentDestination = NavDestination.TRANSACTIONS
                                transactionsViewModel.setSelectedTab(1)
                            },
                            onNavigateToDebts = { showFeatures = true },
                            onNavigateToPlans = { showFeatures = true },
                            onNavigateToBudget = {
                                openBudgetOnSettings = true
                                currentDestination = NavDestination.SETTINGS
                            },
                            onOpenFastAdd = { type ->
                                fastAddViewModel.setType(type)
                                showFastAddModal = true
                            }
                        )
                        NavDestination.TRANSACTIONS -> TransactionsScreen(
                            viewModel = transactionsViewModel
                        )
                        NavDestination.REPORTS -> ReportsScreen(
                            viewModel = reportsViewModel,
                            budgetViewModel = budgetViewModel
                        )
                        NavDestination.SETTINGS -> SettingsScreen(
                            viewModel = settingsViewModel,
                            budgetViewModel = budgetViewModel,
                            openBudgetOnStart = openBudgetOnSettings,
                            onBudgetOpened = { openBudgetOnSettings = false }
                        )
                    }
                }

                if (showFastAddModal) {
                    FastAddBottomSheet(
                        viewModel = fastAddViewModel,
                        onDismiss = {
                            showFastAddModal = false
                            homeViewModel.refresh()
                        }
                    )
                }
            }
        }
    }
}
