package com.smartspend.app

import android.app.Application
import com.smartspend.app.core.database.SmartSpendDatabase
import com.smartspend.app.core.widget.WidgetUpdater
import com.smartspend.app.service.BootReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmartSpendApplication : Application() {

    lateinit var database: SmartSpendDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = SmartSpendDatabase.getInstance(this)

        // Initialize local notification channels
        com.smartspend.app.core.notification.NotificationHelper.createNotificationChannels(this)

        // Schedule periodic reminder sweep
        BootReceiver.scheduleWorkers(this)

        // Initial widget update
        CoroutineScope(Dispatchers.IO).launch {
            WidgetUpdater.refreshSnapshot(applicationContext)
        }
    }
}
