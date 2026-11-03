package com.smartspend.app.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smartspend.app.MainActivity
import com.smartspend.app.R
import com.smartspend.app.core.database.entity.TransactionEntity
import java.text.NumberFormat
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_TRANSACTIONS = "smartspend_transactions"
    const val CHANNEL_BUDGET = "smartspend_budget_alerts"
    const val CHANNEL_REMINDERS = "smartspend_debt_reminders"
    const val CHANNEL_SYSTEM = "smartspend_system"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val txChannel = NotificationChannel(
                CHANNEL_TRANSACTIONS,
                "Transaksi Otomatis",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Konfirmasi transaksi yang tercatat otomatis dari notifikasi perbankan."
                enableVibration(true)
            }

            val budgetChannel = NotificationChannel(
                CHANNEL_BUDGET,
                "Peringatan Anggaran",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peringatan saat pengeluaran mendekati atau melebihi batas anggaran."
                enableVibration(true)
            }

            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Pengingat Utang & Tagihan",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Jadwal jatuh tempo bayar dan tagih utang."
            }

            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "Sistem & Uji Coba",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pemberitahuan diagnostik dan pengujian dev mode."
            }

            nm.createNotificationChannels(listOf(txChannel, budgetChannel, reminderChannel, systemChannel))
        }
    }

    fun sendTransactionNotification(
        context: Context,
        tx: TransactionEntity,
        accountName: String?
    ) {
        if (!hasNotificationPermission(context)) return

        val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        val amountStr = format.format(tx.amount).replace(",00", "")
        val isExpense = tx.type == "EXPENSE"

        val title = if (isExpense) "Pengeluaran Tercatat: $amountStr" else "Pemasukan Tercatat: $amountStr"
        val subtitle = "${tx.merchant ?: "Transaksi"} • ${accountName ?: "Akun"} • Auto-Captured"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smartspend://transactions"), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (tx.id xor 0x7F).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TRANSACTIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$subtitle\nKetuk untuk meninjau atau mengedit detail transaksi."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }

    fun sendBudgetAlertNotification(
        context: Context,
        title: String,
        message: String
    ) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smartspend://settings"), context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(1001, notification)
    }

    fun sendTestNotification(context: Context) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            9999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("SmartSpend Push Notification Aktif")
            .setContentText("Sistem notifikasi lokal dan heads-up banner berfungsi normal di HP ini.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Sistem notifikasi lokal dan heads-up banner berfungsi normal di HP ini. Transaksi dan peringatan budget akan otomatis muncul di sini."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(9999, notification)
    }

    const val NOTIFICATION_ID_RESTORE_ISLAND = 8888
    const val ACTION_RESTORE_ISLAND = "com.smartspend.app.ACTION_RESTORE_ISLAND"

    fun showRestoreIslandNotification(context: Context) {
        if (!hasNotificationPermission(context)) return

        createNotificationChannels(context)

        val restoreIntent = Intent(context, com.smartspend.app.ui.components.island.IslandRestoreReceiver::class.java).apply {
            action = ACTION_RESTORE_ISLAND
        }
        val pendingRestore = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_RESTORE_ISLAND,
            restoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Dynamic Island Disembunyikan")
            .setContentText("Ketuk untuk menyalakan kembali Dynamic Island di layar")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingRestore)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Nyalakan",
                pendingRestore
            )
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_RESTORE_ISLAND, notification)
    }

    fun cancelRestoreIslandNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID_RESTORE_ISLAND)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}
