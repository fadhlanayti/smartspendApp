package com.smartspend.app.ui.screens.reports

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspend.app.core.model.PeriodFilter
import com.smartspend.app.ui.components.CurrencyFormatter
import com.smartspend.app.ui.components.SquircleCard
import com.smartspend.app.ui.screens.budget.BudgetViewModel
import com.smartspend.app.ui.screens.transactions.FilterChipItem
import com.smartspend.app.ui.theme.AccentBlue
import com.smartspend.app.ui.theme.ColorExpenseDark
import com.smartspend.app.ui.theme.ColorIncomeDark
import com.smartspend.app.ui.theme.ColorWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    budgetViewModel: BudgetViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val budgetState by budgetViewModel.uiState.collectAsState()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(viewModel.generateCsvContent().toByteArray())
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Laporan & Analitik",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { exportLauncher.launch("smartspend_report.csv") }) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Ekspor CSV",
                            tint = AccentBlue
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
            // Period selector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChipItem(
                        label = "Minggu Ini",
                        isSelected = uiState.period == PeriodFilter.THIS_WEEK,
                        onClick = { viewModel.setPeriod(PeriodFilter.THIS_WEEK) }
                    )
                    FilterChipItem(
                        label = "Bulan Ini",
                        isSelected = uiState.period == PeriodFilter.THIS_MONTH,
                        onClick = { viewModel.setPeriod(PeriodFilter.THIS_MONTH) }
                    )
                    FilterChipItem(
                        label = "3 Bulan",
                        isSelected = uiState.period == PeriodFilter.THREE_MONTHS,
                        onClick = { viewModel.setPeriod(PeriodFilter.THREE_MONTHS) }
                    )
                }
            }

            // Summary Card
            item {
                SquircleCard {
                    Text(
                        text = "Ringkasan Cashflow",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Pengeluaran",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyFormatter.format(uiState.totalExpense),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ColorExpenseDark
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Pemasukan",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyFormatter.format(uiState.totalIncome),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ColorIncomeDark
                            )
                        }
                    }

                    if (budgetState.totalAllocated > 0L) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Alokasi kategori",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${CurrencyFormatter.format(budgetState.totalAllocated)} / ${CurrencyFormatter.format(budgetState.totalBudget)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Burn Rate Insight Card (PRD section 5.4)
            if (uiState.averageDailyExpense > 0) {
                item {
                    SquircleCard(
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        borderColor = AccentBlue.copy(alpha = 0.4f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Insights,
                                contentDescription = null,
                                tint = AccentBlue
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Insight Burn Rate",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Kamu rata-rata menghabiskan ${CurrencyFormatter.format(uiState.averageDailyExpense)} per hari.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!uiState.budgetExhaustionDate.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (uiState.budgetExhaustionDate == "Sudah habis") "⚠️ Budget bulan ini sudah terlampaui!"
                                else "Di laju ini, sisa budget diperkirakan habis pada ${uiState.budgetExhaustionDate}.",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.budgetExhaustionDate == "Sudah habis") ColorExpenseDark else ColorWarning,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Donut Chart & Category Breakdown
            item {
                SquircleCard {
                    Text(
                        text = "Pengeluaran per Kategori",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (uiState.categoryShares.isEmpty()) {
                        Text(
                            text = "Belum ada data pengeluaran pada periode ini",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Donut Chart Canvas
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            DonutChart(shares = uiState.categoryShares)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Category rows with percentage bar
                        for (share in uiState.categoryShares) {
                            val hexColor = try {
                                Color(android.graphics.Color.parseColor(share.colorHex))
                            } catch (_: Exception) {
                                AccentBlue
                            }

                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                val budgetItem = budgetState.items.firstOrNull { it.category.id == share.categoryId }
                                val limit = budgetItem?.limit ?: 0L
                                val isOver = limit > 0L && share.amount > limit

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
                                                .background(hexColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = share.categoryName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${CurrencyFormatter.format(share.amount)} (${"%.1f".format(share.percentage)}%)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                        if (limit > 0L) {
                                            Text(
                                                text = if (isOver) {
                                                    "lewat ${CurrencyFormatter.format(share.amount - limit)}"
                                                } else {
                                                    "dari ${CurrencyFormatter.format(limit)}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isOver) ColorExpenseDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (limit > 0L) {
                                            (share.amount.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                                        } else {
                                            (share.percentage / 100f).coerceIn(0f, 1f)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = when {
                                        isOver -> ColorExpenseDark
                                        limit > 0L && share.amount.toFloat() / limit.toFloat() >= 0.8f -> ColorWarning
                                        else -> hexColor
                                    },
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                )
                            }
                        }
                    }
                }
            }

            // Top 5 Merchants
            if (uiState.topMerchants.isNotEmpty()) {
                item {
                    SquircleCard {
                        Text(
                            text = "Top 5 Merchant / Tempat Belanja",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        for ((index, m) in uiState.topMerchants.withIndex()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = m.merchantName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${m.count} transaksi",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = CurrencyFormatter.format(m.amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorExpenseDark,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun DonutChart(shares: List<CategoryShare>) {
    Canvas(modifier = Modifier.size(150.dp)) {
        val strokeWidth = 24.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
        val arcSize = Size(diameter, diameter)

        var startAngle = -90f
        for (share in shares) {
            val sweepAngle = (share.percentage / 100f) * 360f
            val color = try {
                Color(android.graphics.Color.parseColor(share.colorHex))
            } catch (_: Exception) {
                AccentBlue
            }

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            startAngle += sweepAngle
        }
    }
}
