package com.smartspend.app.core.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.smartspend.app.MainActivity
import com.smartspend.app.ui.components.CurrencyFormatter

class SmartSpendGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmartSpendGlanceWidget()
}

class SmartSpendGlanceWidget : GlanceAppWidget() {

    companion object {
        private val SMALL_BOX = DpSize(110.dp, 110.dp)
        private val MEDIUM_BOX = DpSize(220.dp, 110.dp)
        private val LARGE_BOX = DpSize(220.dp, 220.dp)

        // Fintech Modern Palette
        private val BgDark = Color(0xFF0C0D12)
        private val CardDark = Color(0xFF161822)
        private val LimeAccent = Color(0xFFD8FF5E)
        private val MintAccent = Color(0xFF90C4B7)
        private val TextMuted = Color(0xFF8E95A8)
        private val ExpenseRed = Color(0xFFFF5252)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_BOX, MEDIUM_BOX, LARGE_BOX)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val snapshot by WidgetStateStore.getSnapshotFlow(context).collectAsState(initial = WidgetSnapshot())
            WidgetContent(context, snapshot)
        }
    }

    @Composable
    private fun WidgetContent(context: Context, snapshot: WidgetSnapshot) {
        val size = androidx.glance.LocalSize.current
        val isMedium = size.width >= 200.dp && size.height < 200.dp
        val isLarge = size.height >= 200.dp

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(BgDark)
                .clickable(actionStartActivity<MainActivity>())
                .padding(14.dp)
        ) {
            when {
                isLarge -> LargeWidgetLayout(context, snapshot)
                isMedium -> MediumWidgetLayout(context, snapshot)
                else -> SmallWidgetLayout(snapshot)
            }
        }
    }

    @Composable
    private fun SmallWidgetLayout(snapshot: WidgetSnapshot) {
        val todayStr = CurrencyFormatter.format(snapshot.todayExpense, snapshot.isHideBalance)
        val sisaHariStr = CurrencyFormatter.format(snapshot.remainingDailyBudget, snapshot.isHideBalance)

        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .size(6.dp)
                        .cornerRadius(3.dp)
                        .background(LimeAccent)
                ) {}
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "Hari Ini",
                    style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            Text(
                text = todayStr,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            Text(
                text = "Sisa: $sisaHariStr",
                style = TextStyle(
                    color = ColorProvider(if (snapshot.remainingDailyBudget > 0) MintAccent else ExpenseRed),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }

    @Composable
    private fun MediumWidgetLayout(context: Context, snapshot: WidgetSnapshot) {
        val monthStr = CurrencyFormatter.format(snapshot.monthExpense, snapshot.isHideBalance)
        val remainingMonth = maxOf(0L, snapshot.monthBudgetLimit - snapshot.monthExpense)
        val sisaStr = CurrencyFormatter.format(remainingMonth, snapshot.isHideBalance)

        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Left column (Info)
            Column(
                modifier = GlanceModifier.defaultWeight()
            ) {
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Box(
                        modifier = GlanceModifier
                            .size(7.dp)
                            .cornerRadius(4.dp)
                            .background(LimeAccent)
                    ) {}
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = "SmartSpend",
                        style = TextStyle(color = ColorProvider(LimeAccent), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                Text(
                    text = monthStr,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = "Sisa Bulan: $sisaStr",
                    style = TextStyle(
                        color = ColorProvider(if (remainingMonth > 0) MintAccent else ExpenseRed),
                        fontSize = 11.sp
                    )
                )
            }

            // Right column: Modern Quick Action CTA Pill
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.Horizontal.End,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(14.dp)
                        .background(LimeAccent)
                        .clickable(actionStartActivity<MainActivity>())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ Catat",
                        style = TextStyle(
                            color = ColorProvider(BgDark),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun LargeWidgetLayout(context: Context, snapshot: WidgetSnapshot) {
        val todayStr = CurrencyFormatter.format(snapshot.todayExpense, snapshot.isHideBalance)
        val monthStr = CurrencyFormatter.format(snapshot.monthExpense, snapshot.isHideBalance)
        val sisaHariStr = CurrencyFormatter.format(snapshot.remainingDailyBudget, snapshot.isHideBalance)

        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .cornerRadius(4.dp)
                        .background(LimeAccent)
                ) {}
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "SmartSpend Financial",
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = "Realtime",
                    style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp)
                )
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // 2-Column Stats
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(text = "Hari Ini", style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp))
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(text = todayStr, style = TextStyle(color = ColorProvider(ExpenseRed), fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(text = "Sisa Harian", style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp))
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = sisaHariStr,
                        style = TextStyle(
                            color = ColorProvider(if (snapshot.remainingDailyBudget > 0) MintAccent else ExpenseRed),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Total Month row
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(12.dp)
                    .background(CardDark)
                    .padding(10.dp)
            ) {
                Text(text = "Total Pengeluaran Bulan Ini", style = TextStyle(color = ColorProvider(TextMuted), fontSize = 10.5.sp))
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(text = monthStr, style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Quick actions
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .cornerRadius(12.dp)
                        .background(LimeAccent)
                        .clickable(actionStartActivity<MainActivity>())
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ Catat Transaksi",
                        style = TextStyle(color = ColorProvider(BgDark), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
