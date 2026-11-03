package com.smartspend.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartspend.app.R
import com.smartspend.app.core.database.SmartSpendDatabase
import java.text.NumberFormat
import java.util.Locale

class ReminderSweepWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_DEBT_REMINDERS = "smartspend_debt_reminders"
        const val CHANNEL_PLAN_REMINDERS = "smartspend_plan_reminders"
    }

    override suspend fun doWork(): Result {
        createChannels()
        val db = SmartSpendDatabase.getInstance(appContext)
        val now = System.currentTimeMillis()

        // 1. Process Debts Reminders
        val dueReminders = db.reminderDao().getDueReminders(now)
        for (reminder in dueReminders) {
            val debtId = reminder.debtId
            if (debtId != null) {
                val debt = db.debtDao().getDebtById(debtId)
                if (debt != null && debt.status == "ACTIVE" && !debt.isDeleted) {
                    sendDebtNotification(debt.direction, debt.counterpartyName, debt.principalAmount - debt.paidAmount)
                    val nextFire = reminder.repeatEveryDays?.let { days ->
                        now + (days * 86_400_000L)
                    }
                    val isStillEnabled = reminder.maxRepeats == 0 || (reminder.firedCount + 1 < reminder.maxRepeats)
                    db.reminderDao().update(
                        reminder.copy(
                            firedCount = reminder.firedCount + 1,
                            nextFireAt = if (isStillEnabled) nextFire else null,
                            isEnabled = isStillEnabled
                        )
                    )
                }
            }
        }

        // 2. Process Purchase Plans ready for decision (PRD section 9.4)
        val readyPlans = db.purchasePlanDao().getPlansReadyForDecision(now)
        for (plan in readyPlans) {
            db.purchasePlanDao().update(
                plan.copy(
                    status = "READY",
                    updatedAt = now
                )
            )
            sendPlanReadyNotification(plan.name, plan.estimatedPrice)
        }

        return Result.success()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val debtChannel = NotificationChannel(
                CHANNEL_DEBT_REMINDERS,
                "Pengingat Utang",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Pengingat jatuh tempo bayar dan tagih utang."
            }

            val planChannel = NotificationChannel(
                CHANNEL_PLAN_REMINDERS,
                "Rencana Beli",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Pengingat saat masa tunggu rencana beli berakhir."
            }

            nm.createNotificationChannel(debtChannel)
            nm.createNotificationChannel(planChannel)
        }
    }

    private fun sendDebtNotification(direction: String, name: String, remainingAmount: Long) {
        val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        val amountStr = format.format(remainingAmount).replace(",00", "")

        val (title, text) = if (direction == "OWE") {
            Pair("Pengingat Bayar Utang", "Jatuh tempo pembayaran $amountStr kepada $name.")
        } else {
            Pair("Pengingat Tagih Utang", "Waktunya menagih $amountStr kepada $name.")
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_DEBT_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }

    private fun sendPlanReadyNotification(planName: String, estimatedPrice: Long) {
        val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        val amountStr = format.format(estimatedPrice).replace(",00", "")

        val notification = NotificationCompat.Builder(appContext, CHANNEL_PLAN_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Waktunya Memutuskan!")
            .setContentText("Masa tunggu untuk \"$planName\" ($amountStr) telah selesai. Buka untuk memutuskan.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((System.currentTimeMillis() % 10000).toInt() + 100, notification)
    }
}
