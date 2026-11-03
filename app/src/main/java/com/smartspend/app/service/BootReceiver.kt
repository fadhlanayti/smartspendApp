package com.smartspend.app.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Request rebind for listener service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                android.service.notification.NotificationListenerService.requestRebind(
                    ComponentName(context, SpendNotificationListener::class.java)
                )
            } catch (_: Exception) {
                // Ignore if not permitted
            }
        }

        // Schedule periodic reminder sweep worker (every 12 hours)
        scheduleWorkers(context)
    }

    companion object {
        fun scheduleWorkers(context: Context) {
            val sweepRequest = PeriodicWorkRequestBuilder<ReminderSweepWorker>(
                12, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "smartspend_reminder_sweep",
                ExistingPeriodicWorkPolicy.KEEP,
                sweepRequest
            )
        }
    }
}
