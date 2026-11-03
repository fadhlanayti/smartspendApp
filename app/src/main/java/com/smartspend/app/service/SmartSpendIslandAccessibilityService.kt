package com.smartspend.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import com.smartspend.app.MainActivity
import com.smartspend.app.ui.components.island.SystemIslandOverlayManager

class SmartSpendIslandAccessibilityService : AccessibilityService() {

    private var currentPackage: String? = null

    private val ignoredSystemPackages = setOf(
        "com.android.systemui",
        "android",
        "com.google.android.inputmethod.latin",
        "com.facemoji.lite.transsion",
        "com.transsion.smartpanel",
        "com.transsion.dynamicbar"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        android.util.Log.e("SmartSpendA11y", "onServiceConnected")
        SystemIslandOverlayManager.onAccessibilityServiceConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        android.util.Log.e("SmartSpendA11y", "onAccessibilityEvent type: ${event.eventType}, pkg: ${event.packageName}, class: ${event.className}")
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: ""

        // Crucial fix: Prevent overlay's own window event from hiding itself!
        if (pkg == packageName) {
            if (cls == MainActivity::class.java.name) {
                currentPackage = pkg
                SystemIslandOverlayManager.setIslandVisible(this, false)
            }
            return
        }

        if (ignoredSystemPackages.contains(pkg) || pkg.contains("inputmethod") || pkg.contains("keyboard")) {
            return
        }

        // Deduplicate: only react when foreground package ACTUALLY changes
        if (pkg == currentPackage) return
        currentPackage = pkg

        if (isHomeScreen(pkg)) {
            // Homescreen -> show dynamic island
            SystemIslandOverlayManager.setIslandVisible(this, true)
        } else {
            // Any other app (Chrome, Settings, etc.) -> hide dynamic island
            SystemIslandOverlayManager.setIslandVisible(this, false)
        }
    }

    private fun isHomeScreen(packageName: String): Boolean {
        if (packageName == "com.transsion.XOSLauncher" ||
            packageName.contains("launcher", ignoreCase = true) ||
            packageName.contains("quickstep", ignoreCase = true)
        ) {
            return true
        }

        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val defaultLauncher = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            packageName == defaultLauncher
        } catch (_: Exception) {
            false
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            SystemIslandOverlayManager.onAccessibilityServiceDisconnected()
        }
    }

    companion object {
        @Volatile
        var instance: SmartSpendIslandAccessibilityService? = null
    }
}
