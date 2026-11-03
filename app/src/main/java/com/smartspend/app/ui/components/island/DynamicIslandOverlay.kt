package com.smartspend.app.ui.components.island

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspend.app.ui.components.CurrencyFormatter
import com.smartspend.app.ui.theme.ElectricLime
import com.smartspend.app.ui.theme.ColorExpenseDark
import com.smartspend.app.ui.theme.ColorIncomeDark

@Composable
fun DynamicIslandOverlay(
    todayExpense: Long,
    remainingDailyBudget: Long,
    monthlyExpense: Long,
    monthlyBudgetLimit: Long,
    onOpenFastAdd: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val islandState by DynamicIslandManager.state.collectAsState()

    AnimatedVisibility(
        visible = islandState !is DynamicIslandState.Hidden,
        enter = fadeIn(spring(stiffness = 400f)) + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)) + expandVertically(spring(stiffness = 400f)),
        exit = fadeOut(spring(stiffness = 400f)) + scaleOut(targetScale = 0.8f, animationSpec = spring(stiffness = 400f)) + shrinkVertically(spring(stiffness = 400f))
    ) {
        val targetWidth = when (islandState) {
            is DynamicIslandState.Hidden -> 0.dp
            is DynamicIslandState.Alert -> 320.dp
            is DynamicIslandState.Expanded -> 340.dp
        }

        val targetHeight = when (islandState) {
            is DynamicIslandState.Hidden -> 0.dp
            is DynamicIslandState.Alert -> 58.dp
            is DynamicIslandState.Expanded -> 200.dp
        }

        val targetCorner = when (islandState) {
            is DynamicIslandState.Hidden -> 20.dp
            is DynamicIslandState.Alert -> 24.dp
            is DynamicIslandState.Expanded -> 28.dp
        }

        val animatedWidth by animateDpAsState(
            targetValue = targetWidth,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
            label = "islandWidth"
        )

        val animatedHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
            label = "islandHeight"
        )

        val animatedCorner by animateDpAsState(
            targetValue = targetCorner,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
            label = "islandCorner"
        )

        Box(
            modifier = modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 6.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .width(animatedWidth)
                    .height(animatedHeight)
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(animatedCorner))
                    .clip(RoundedCornerShape(animatedCorner))
                    .clickable {
                        when (islandState) {
                            is DynamicIslandState.Alert -> DynamicIslandManager.expand()
                            is DynamicIslandState.Expanded -> DynamicIslandManager.collapse()
                            is DynamicIslandState.Hidden -> {}
                        }
                    },
                shape = RoundedCornerShape(animatedCorner),
                color = Color(0xFF0C0D12),
                border = BorderStroke(1.2.dp, Color(0xFF262938))
            ) {
                Crossfade(
                    targetState = islandState,
                    label = "islandCrossfade"
                ) { state ->
                    when (state) {
                        is DynamicIslandState.Hidden -> {
                            Box(modifier = Modifier.size(0.dp))
                        }
                        is DynamicIslandState.Alert -> {
                            AlertView(state = state)
                        }
                        is DynamicIslandState.Expanded -> {
                            ExpandedView(
                                todayExpense = todayExpense,
                                remainingDailyBudget = remainingDailyBudget,
                                monthlyExpense = monthlyExpense,
                                monthlyBudgetLimit = monthlyBudgetLimit,
                                onClose = { DynamicIslandManager.collapse() },
                                onFastAdd = {
                                    DynamicIslandManager.collapse()
                                    onOpenFastAdd()
                                },
                                onTransactions = {
                                    DynamicIslandManager.collapse()
                                    onNavigateToTransactions()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertView(
    state: DynamicIslandState.Alert
) {
    val isExpense = state.type == "EXPENSE"
    val iconColor = if (isExpense) ColorExpenseDark else ColorIncomeDark
    val icon = if (isExpense) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${if (isExpense) "-" else "+"} ${CurrencyFormatter.format(state.amount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = iconColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ElectricLime.copy(alpha = 0.18f))
                .border(0.8.dp, ElectricLime.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Tercatat",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricLime,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ExpandedView(
    todayExpense: Long,
    remainingDailyBudget: Long,
    monthlyExpense: Long,
    monthlyBudgetLimit: Long,
    onClose: () -> Unit,
    onFastAdd: () -> Unit,
    onTransactions: () -> Unit
) {
    val progress = if (monthlyBudgetLimit > 0) {
        (monthlyExpense.toFloat() / monthlyBudgetLimit).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ElectricLime)
                )
                Text(
                    text = "SmartSpend Island",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tutup",
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Hari ini",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = CurrencyFormatter.format(todayExpense),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = ColorExpenseDark
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Sisa Harian",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = CurrencyFormatter.format(remainingDailyBudget),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (remainingDailyBudget > 0) ColorIncomeDark else ColorExpenseDark
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Budget Bulanan",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricLime,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = ElectricLime,
                trackColor = Color(0xFF242838)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onFastAdd,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricLime),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF0C0D12),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Catat",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF0C0D12),
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onTransactions,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFF33384C)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Riwayat",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
