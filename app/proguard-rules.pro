# ---- Kotlin / coroutines ----
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---- Compose ----
-dontwarn androidx.compose.**
-keepclassmembers class androidx.compose.runtime.** { *; }

# ---- Glance AppWidget ----
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# ---- AppWidget receivers referenced from the manifest ----
-keep class com.smartspend.app.core.widget.SmartSpendGlanceWidgetReceiver { *; }
-keep class com.smartspend.app.core.widget.SmartSpendGlanceWidget { *; }

# ---- Services / receivers referenced from the manifest ----
-keep class com.smartspend.app.service.SpendNotificationListener { *; }
-keep class com.smartspend.app.service.BootReceiver { *; }
-keep class com.smartspend.app.service.ReminderSweepWorker { *; }
-keep class com.smartspend.app.MainActivity { *; }
-keep class com.smartspend.app.SmartSpendApplication { *; }

# ---- AccessibilityService entry points are invoked by the framework ----
-keep class * extends android.service.notification.NotificationListenerService { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ---- DataStore ----
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ---- Coil ----
-dontwarn coil.**

# ---- Reflection used by WorkManager / Room generated code ----
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep enum values used via string comparison
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Attribution / source info that OEM scanners score positively when intact
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
