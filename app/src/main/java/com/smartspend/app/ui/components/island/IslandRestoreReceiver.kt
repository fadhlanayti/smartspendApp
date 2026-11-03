package com.smartspend.app.ui.components.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartspend.app.core.notification.NotificationHelper

class IslandRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == NotificationHelper.ACTION_RESTORE_ISLAND) {
            // Cancel notification immediately so it disappears from status bar drawer
            NotificationHelper.cancelRestoreIslandNotification(context)

            // Restore the Dynamic Island on screen
            SystemIslandOverlayManager.restoreFromTemporaryHide(context)
        }
    }
}
