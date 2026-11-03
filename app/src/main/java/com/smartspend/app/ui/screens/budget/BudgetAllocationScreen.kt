package com.smartspend.app.ui.screens.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.smartspend.app.core.database.entity.CategoryEntity
import com.smartspend.app.ui.components.CurrencyFormatter
import com.smartspend.app.ui.components.SquircleCard
import com.smartspend.app.ui.theme.AccentBlue
import com.smartspend.app.ui.theme.ColorExpenseDark
import com.smartspend.app.ui.theme.ColorIncomeDark
import com.smartspend.app.ui.theme.ColorWarning

private val CategoryPalette = listOf(
    "#FF9500", "#0A84FF", "#AF52DE", "#FF3B30",
    "#34C759", "#5856D6", "#5AC8FA", "#FF2D55",
    "#8E8E93", "#FF9F0A"
)

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    AccentBlue
}

private fun kindLabel(kind: String): String = when (kind) {
    "EXPENSE" -> "Pengeluaran"
    "INCOME" -> "Pemasukan"
    "TRANSFER" -> "Transfer"
    else -> kind
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetAllocationScreen(
    viewModel: BudgetViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showCategoryManager by remember { mutableStateOf(false) }
    var showTotalDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<CategoryBudgetItem?>(null) }

    BackHandler(enabled = showCategoryManager) { showCategoryManager = false }
    BackHandler(enabled = !showCategoryManager, onBack = onBack)

    if (showCategoryManager) {
        CategoryManagerScreen(
            viewModel = viewModel,
            onBack = { showCategoryManager = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Alokasi Budget",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.monthLabel,
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
                actions = {
                    TextButton(onClick = { showCategoryManager = true }) {
                        Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kategori")
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
            item {
                BudgetSummaryCard(
                    state = state,
                    onEditBudget = { showTotalDialog = true }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alokasi per Kategori",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ketuk kategori untuk mengatur limitnya",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${state.allocatedItems.size}/${state.items.size} diatur",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.items.isEmpty()) {
                item {
                    SquircleCard {
                        Text(
                            text = "Belum ada kategori pengeluaran. Tambahkan kategori lewat tombol Kategori di kanan atas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(state.items.size, key = { state.items[it].category.id }) { index ->
                    val item = state.items[index]
                    CategoryBudgetRow(
                        item = item,
                        onClick = { editingItem = item }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    editingItem?.let { item ->
        CategoryLimitDialog(
            item = item,
            totalBudget = state.totalBudget,
            totalAllocated = state.totalAllocated,
            onDismiss = { editingItem = null },
            onSave = { limit ->
                viewModel.setCategoryLimit(item.category.id, limit)
                editingItem = null
            }
        )
    }

    if (showTotalDialog) {
        TotalBudgetDialog(
            currentLimit = state.totalBudget,
            currentThreshold = state.alertThresholdPct,
            totalAllocated = state.totalAllocated,
            onDismiss = { showTotalDialog = false },
            onSave = { limit, threshold ->
                viewModel.setTotalBudget(limit, threshold)
                showTotalDialog = false
            }
        )
    }
}

@Composable
private fun BudgetSummaryCard(
    state: BudgetUiState,
    onEditBudget: () -> Unit
) {
    val overBudget = state.unallocated < 0L
    SquircleCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Budget Bulan Ini",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onEditBudget) {
                Text("Ubah")
            }
        }

        Text(
            text = CurrencyFormatter.format(state.totalBudget),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { state.totalProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (state.totalProgress >= 1f) ColorExpenseDark else AccentBlue,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Terpakai ${CurrencyFormatter.format(state.totalSpent)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Sisa ${CurrencyFormatter.format(state.remaining)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.totalProgress >= 1f) ColorExpenseDark else ColorIncomeDark,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Dialokasikan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.format(state.totalAllocated),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (overBudget) "Kelebihan alokasi" else "Belum dialokasikan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.format(if (overBudget) -state.unallocated else state.unallocated),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (overBudget) ColorExpenseDark else ColorWarning
                )
            }
        }
    }
}

@Composable
private fun CategoryBudgetRow(
    item: CategoryBudgetItem,
    onClick: () -> Unit
) {
    val categoryColor = parseColor(item.category.colorHex)
    val progressColor = when {
        item.isOver -> ColorExpenseDark
        item.progress >= 0.8f -> ColorWarning
        else -> categoryColor
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (item.depth > 0) 16.dp else 0.dp)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(if (item.depth > 0) 30.dp else 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(categoryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (item.depth > 0) 8.dp else 10.dp)
                            .clip(CircleShape)
                            .background(categoryColor)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (item.limit > 0L) {
                            "${CurrencyFormatter.format(item.spent)} dari ${CurrencyFormatter.format(item.limit)}"
                        } else {
                            "Terpakai ${CurrencyFormatter.format(item.spent)} • limit belum diatur"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Atur limit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            if (item.limit > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (item.isOver) {
                            "Lewat ${CurrencyFormatter.format(item.spent - item.limit)}"
                        } else {
                            "Sisa ${CurrencyFormatter.format(item.limit - item.spent)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isOver) ColorExpenseDark else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(item.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = progressColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryLimitDialog(
    item: CategoryBudgetItem,
    totalBudget: Long,
    totalAllocated: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var amountText by remember(item.category.id, item.limit) {
        mutableStateOf(if (item.limit > 0L) item.limit.toString() else "")
    }
    val amount = amountText.toLongOrNull() ?: 0L
    val otherAllocated = totalAllocated - item.limit
    val available = totalBudget - otherAllocated

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
                text = item.category.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Terpakai bulan ini: ${CurrencyFormatter.format(item.spent)}",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { value ->
                    amountText = value.filter { it.isDigit() }.take(12)
                },
                label = { Text("Limit kategori (Rp)") },
                prefix = { Text("Rp ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "+100rb" to 100_000L,
                    "+250rb" to 250_000L,
                    "+500rb" to 500_000L,
                    "+1jt" to 1_000_000L
                ).forEach { (label, value) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                            .clickable {
                                val current = amountText.toLongOrNull() ?: 0L
                                amountText = (current + value).toString()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentBlue
                        )
                    }
                }
            }

            Text(
                text = "Sisa budget yang bisa dialokasikan: ${CurrencyFormatter.format(available)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (amount > available) ColorExpenseDark else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (amount > available) {
                Text(
                    text = "Limit ini melebihi sisa budget. Kamu masih bisa menyimpannya, tapi alokasi akan melebihi budget bulanan.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorWarning
                )
            }

            if (item.limit > 0L) {
                TextButton(
                    onClick = { onSave(0L) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Hapus limit kategori", color = ColorExpenseDark)
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
                    Text("Batal")
                }
                Button(
                    onClick = { onSave(amount) },
                    enabled = amount > 0L,
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
private fun TotalBudgetDialog(
    currentLimit: Long,
    currentThreshold: Int,
    totalAllocated: Long,
    onDismiss: () -> Unit,
    onSave: (Long, Int) -> Unit
) {
    var limitText by remember { mutableStateOf(currentLimit.toString()) }
    var thresholdText by remember { mutableStateOf(currentThreshold.toString()) }
    val limit = limitText.toLongOrNull() ?: 0L
    val threshold = thresholdText.toIntOrNull() ?: currentThreshold

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
                text = "Atur Budget Bulanan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it.filter { c -> c.isDigit() }.take(12) },
                label = { Text("Batas Budget (Rp)") },
                prefix = { Text("Rp ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = thresholdText,
                onValueChange = { thresholdText = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Ambang Peringatan (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Total alokasi kategori saat ini: ${CurrencyFormatter.format(totalAllocated)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (limit in 1 until totalAllocated) {
                Text(
                    text = "Budget bulanan lebih kecil dari total alokasi kategori.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorWarning
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
                    onClick = { onSave(limit, threshold) },
                    enabled = limit > 0L && threshold in 1..100,
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
private fun CategoryManagerScreen(
    viewModel: BudgetViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var archivingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Kelola Kategori",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah kategori")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        val roots = state.allCategories
            .filter { it.parentId == null }
            .sortedBy { it.sortOrder }
        val childrenByParent = state.allCategories
            .filter { it.parentId != null }
            .groupBy { it.parentId!! }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Kategori dipakai untuk mengelompokkan transaksi dan alokasi budget. Kategori yang diarsipkan disembunyikan, tetapi transaksi lama tetap aman.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            if (roots.isEmpty()) {
                item {
                    SquircleCard {
                        Text(
                            text = "Belum ada kategori. Ketuk tombol + untuk menambahkan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                roots.forEach { root ->
                    item(key = "root-${root.id}") {
                        CategoryManagerRow(
                            category = root,
                            depth = 0,
                            onEdit = { editingCategory = root },
                            onArchive = { archivingCategory = root }
                        )
                    }
                    childrenByParent[root.id]
                        .orEmpty()
                        .sortedBy { it.sortOrder }
                        .forEach { child ->
                            item(key = "child-${child.id}") {
                                CategoryManagerRow(
                                    category = child,
                                    depth = 1,
                                    onEdit = { editingCategory = child },
                                    onArchive = { archivingCategory = child }
                                )
                            }
                        }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAddDialog) {
        CategoryEditDialog(
            existing = null,
            allCategories = state.allCategories,
            onDismiss = { showAddDialog = false },
            onSave = { name, kind, colorHex, parentId ->
                viewModel.addCategory(name, kind, colorHex, parentId)
                showAddDialog = false
            }
        )
    }

    editingCategory?.let { category ->
        CategoryEditDialog(
            existing = category,
            allCategories = state.allCategories,
            onDismiss = { editingCategory = null },
            onSave = { name, _, colorHex, _ ->
                viewModel.updateCategory(category, name, colorHex)
                editingCategory = null
            }
        )
    }

    archivingCategory?.let { category ->
        ModalBottomSheet(
            onDismissRequest = { archivingCategory = null },
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
                    text = "Arsipkan kategori?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "\"${category.name}\" akan disembunyikan dari pilihan kategori. Transaksi lama yang memakai kategori ini tetap tersimpan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { archivingCategory = null },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = {
                            viewModel.archiveCategory(category)
                            archivingCategory = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorExpenseDark)
                    ) {
                        Text("Arsipkan", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryManagerRow(
    category: CategoryEntity,
    depth: Int,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    val categoryColor = parseColor(category.colorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (depth > 0) 16.dp else 0.dp)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (depth > 0) 28.dp else 34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (depth > 0) 8.dp else 10.dp)
                        .clip(CircleShape)
                        .background(categoryColor)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (depth > 0) "Sub • ${kindLabel(category.kind)}" else kindLabel(category.kind),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (category.isSystem) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Bawaan",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = AccentBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Ubah ${category.name}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onArchive, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = "Arsipkan ${category.name}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditDialog(
    existing: CategoryEntity?,
    allCategories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, kind: String, colorHex: String, parentId: Long?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: "EXPENSE") }
    var colorHex by remember { mutableStateOf(existing?.colorHex ?: CategoryPalette.first()) }
    var parentId by remember { mutableStateOf(existing?.parentId) }
    var parentMenuOpen by remember { mutableStateOf(false) }

    val parentOptions = allCategories.filter {
        it.parentId == null && it.kind == kind && it.id != existing?.id
    }
    val selectedParentName = parentOptions.firstOrNull { it.id == parentId }?.name

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
                text = if (existing == null) "Tambah Kategori" else "Ubah Kategori",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Nama Kategori") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (existing == null) {
                Text(
                    text = "Jenis Kategori",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("EXPENSE" to "Pengeluaran", "INCOME" to "Pemasukan").forEach { (value, label) ->
                        FilterChip(
                            selected = kind == value,
                            onClick = {
                                kind = value
                                parentId = null
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Text(
                text = "Warna",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryPalette.take(8).forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(parseColor(hex))
                            .border(
                                width = if (hex == colorHex) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { colorHex = hex }
                    )
                }
            }

            if (existing == null) {
                Text(
                    text = "Induk Kategori (opsional)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    OutlinedButton(
                        onClick = { parentMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedParentName ?: "Tanpa induk",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = parentMenuOpen,
                        onDismissRequest = { parentMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tanpa induk") },
                            onClick = {
                                parentId = null
                                parentMenuOpen = false
                            }
                        )
                        parentOptions.forEach { parent ->
                            DropdownMenuItem(
                                text = { Text(parent.name) },
                                onClick = {
                                    parentId = parent.id
                                    parentMenuOpen = false
                                }
                            )
                        }
                    }
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
                    Text("Batal")
                }
                Button(
                    onClick = { onSave(name, kind, colorHex, parentId) },
                    enabled = name.isNotBlank(),
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
