package com.smartspend.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspend.app.ui.theme.AccentBlue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DaySpend(
    val dayLabel: String,
    val amount: Long,
    val isToday: Boolean = false
)

@Composable
fun MiniSpendChart(
    data: List<DaySpend>,
    isHideBalance: Boolean,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val maxAmount = maxOf(1L, data.maxOfOrNull { it.amount } ?: 1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pengeluaran 7 Hari Terakhir",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val selected = selectedIndex?.let { data.getOrNull(it) }
            if (selected != null) {
                Text(
                    text = "${selected.dayLabel}: ${CurrencyFormatter.format(selected.amount, isHideBalance)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentBlue,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            val barColor = MaterialTheme.colorScheme.surfaceVariant
            val activeColor = AccentBlue
            val todayColor = Color(0xFF32D74B)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(data) {
                        detectTapGestures { offset ->
                            val slotWidth = size.width / maxOf(1, data.size)
                            val index = (offset.x / slotWidth).toInt().coerceIn(0, data.size - 1)
                            selectedIndex = index
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height - 20.dp.toPx() // leave space for bottom labels
                val count = data.size
                if (count == 0) return@Canvas

                val slotWidth = canvasWidth / count
                val barWidth = slotWidth * 0.45f

                for (i in data.indices) {
                    val item = data[i]
                    val barHeight = ((item.amount.toFloat() / maxAmount) * canvasHeight).coerceAtLeast(4.dp.toPx())
                    val x = (i * slotWidth) + (slotWidth - barWidth) / 2
                    val y = canvasHeight - barHeight

                    val isSelected = selectedIndex == i
                    val color = when {
                        isSelected -> activeColor
                        item.isToday -> todayColor
                        else -> barColor
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }
            }
        }

        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            for ((index, item) in data.withIndex()) {
                val isSelected = selectedIndex == index
                Text(
                    text = item.dayLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = if (isSelected) AccentBlue else if (item.isToday) Color(0xFF32D74B) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected || item.isToday) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
