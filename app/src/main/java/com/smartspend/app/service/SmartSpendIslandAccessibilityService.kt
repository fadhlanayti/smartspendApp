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

    fun isCurrentlyOnHomeScreen(): Boolean {
        val pkg = currentPackage ?: return true
        // Stale self-reference (our own activity is/was foreground) carries no
        // information about the launcher, so treat it as "unknown but plausibly home".
        if (pkg == packageName) return true
        return isHomeScreen(pkg)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: ""

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                android.util.Log.e("SmartSpendA11y", "windowState pkg: $pkg, class: $cls")

                // Crucial fix: Prevent overlay's own window event from hiding itself!
                if (pkg == packageName) {
                    currentPackage = pkg
                    if (cls == MainActivity::class.java.name || cls.isBlank()) {
                        SystemIslandOverlayManager.setIslandVisible(this, false)
                    }
                    return
                }

                if (ignoredSystemPackages.contains(pkg) || pkg.contains("inputmethod") || pkg.contains("keyboard")) {
                    return
                }

                val shouldBeVisible = isHomeScreen(pkg)

                // Deduplicate: only skip when the package AND the desired visibility are
                // unchanged. A pure package check left the island permanently hidden
                // whenever the launcher re-announced itself while currentPackage was stale.
                if (pkg == currentPackage &&
                    SystemIslandOverlayManager.isIslandVisible() == shouldBeVisible
                ) {
                    return
                }
                currentPackage = pkg

                SystemIslandOverlayManager.setIslandVisible(this, shouldBeVisible)
            }

            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // Cheap recovery signal: when the launcher reports its window set changing
                // (app drawer closed, widget resized, screen turned on) our overlay may have
                // been detached with no window-state event. Re-assert intent.
                if (pkg == packageName) return
                if (isHomeScreen(pkg) && SystemIslandOverlayManager.isIslandVisible()) {
                    SystemIslandOverlayManager.setIslandVisible(this, true)
                }
            }
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
