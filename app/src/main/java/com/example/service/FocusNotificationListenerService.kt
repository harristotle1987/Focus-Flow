package com.example.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.FocusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FocusNotificationListenerService : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var repository: FocusRepository

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        repository = FocusRepository(database.focusDao())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkgName = sbn.packageName

        // Do not suppress notifications from our own app
        if (pkgName == packageName) return

        serviceScope.launch {
            val session = repository.getActiveSession()
            if (session != null && session.isActive && !session.isBreak) {
                val isBlocked = repository.isAppBlocked(pkgName)
                if (isBlocked) {
                    val extras = sbn.notification?.extras
                    val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "New Notification"
                    val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                    val appName = getAppName(pkgName)

                    // Log to DB for the calm Focus Digest screen
                    repository.insertNotificationLog(
                        packageName = pkgName,
                        appName = appName,
                        title = title,
                        text = text
                    )

                    // Suppress (Cancel) notification immediately from status bar
                    try {
                        cancelNotification(sbn.key)
                        Log.d("NotificationListener", "Successfully suppressed notification from $pkgName ($appName)")
                    } catch (e: Exception) {
                        Log.e("NotificationListener", "Failed to cancel notification", e)
                    }
                }
            }
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
