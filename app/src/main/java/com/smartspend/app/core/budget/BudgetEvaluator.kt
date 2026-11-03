package com.smartspend.app.core.budget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smartspend.app.R
import com.smartspend.app.core.database.dao.BudgetDao
import com.smartspend.app.core.database.dao.TransactionDao
import com.smartspend.app.core.database.entity.BudgetEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BudgetEvaluator(
    private val context: Context,
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao
) {
    companion object {
        const val CHANNEL_BUDGET_ALERTS = "smartspend_budget_alerts"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_BUDGET_ALERTS,
                "Peringatan Budget",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi saat pengeluaran mendekati atau melampaui batas budget."
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Evaluates monthly budget and triggers notifications if thresholds are crossed.
     */
    suspend fun evaluateMonthlyBudget() {
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(System.currentTimeMillis())
        val budget = budgetDao.getTotalBudgetForMonth(currentMonth) ?: return

        val (startOfMonth, endOfMonth) = getMonthBounds()
        val expenses = transactionDao.getExpenseTransactionsBetween(startOfMonth, endOfMonth)
        val totalSpent = expenses.sumOf { it.amount + it.fee }

        val thresholdAmount = (budget.limitAmount * budget.alertThresholdPct) / 100
        val newLevel = when {
            totalSpent >= budget.limitAmount -> 2
            totalSpent >= thresholdAmount -> 1
            else -> 0
        }

        if (newLevel > budget.lastAlertLevel) {
            // Trigger alert notification
            sendBudgetNotification(totalSpent, budget, newLevel)
            budgetDao.update(budget.copy(lastAlertLevel = newLevel, updatedAt = System.currentTimeMillis()))
        } else if (newLevel < budget.lastAlertLevel) {
            // If expense was reduced or budget limit increased, reset level
            budgetDao.update(budget.copy(lastAlertLevel = newLevel, updatedAt = System.currentTimeMillis()))
        }
    }

    private fun sendBudgetNotification(spent: Long, budget: BudgetEntity, level: Int) {
        val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        val spentStr = format.format(spent).replace(",00", "")
        val limitStr = format.format(budget.limitAmount).replace(",00", "")

        val (title, message) = if (level == 2) {
            Pair(
                "🚨 Budget Bulan Ini Terlampaui!",
                "Pengeluaranmu sudah mencapai $spentStr, melebihi batas budget $limitStr."
            )
        } else {
            Pair(
                "⚠️ Pengeluaran Mendekati Batas Budget",
                "Kamu sudah memakai ${budget.alertThresholdPct}% budget ($spentStr dari $limitStr)."
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1001, builder.build())
    }

    private fun getMonthBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return Pair(start, end)
    }
}
