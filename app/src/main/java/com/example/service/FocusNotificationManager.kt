package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import java.util.concurrent.ConcurrentHashMap

object FocusNotificationManager {

    const val ALERTS_CHANNEL_ID = "FocusAlertsChannel"
    const val ALERTS_NOTIFICATION_ID = 4243
    const val WARNING_NOTIFICATION_ID = 4244

    private val lastWarningTimestamps = ConcurrentHashMap<String, Long>()
    private const val WARNING_DEBOUNCE_MS = 8000L // Prevent warning notifications spamming on high frequency loops

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Alerts channel for session start, end, and app warnings (High Importance)
            val alertsChannel = NotificationChannel(
                ALERTS_CHANNEL_ID,
                "Focus Session Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Triggers alerts when a session starts, ends, or when a blocked app is intercepted."
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(alertsChannel)
            Log.d("FocusNotification", "Successfully created notifications channel '$ALERTS_CHANNEL_ID'")
        }
    }

    fun sendSessionStartNotification(context: Context, sessionLabel: String) {
        createNotificationChannels(context)
        val title = "Focus Session Activated 🎯"
        val message = "Session \"$sessionLabel\" has started. Distractions are now blocked."
        sendAlertNotification(context, title, message, ALERTS_NOTIFICATION_ID)
    }

    fun sendSessionEndNotification(context: Context, sessionLabel: String, completedSuccessfully: Boolean = true) {
        createNotificationChannels(context)
        val title = if (completedSuccessfully) "Focus Session Completed! 🎉" else "Focus Session Stopped"
        val message = if (completedSuccessfully) {
            "Congratulations! You completed: \"$sessionLabel\". Take a well-deserved break."
        } else {
            "The active session has been manually stopped."
        }
        sendAlertNotification(context, title, message, ALERTS_NOTIFICATION_ID)
    }

    fun sendBlockedAppWarningPlatform(context: Context, appName: String) {
        val now = System.currentTimeMillis()
        val lastWarned = lastWarningTimestamps[appName] ?: 0L
        if (now - lastWarned < WARNING_DEBOUNCE_MS) {
            // Rate limit to prevent spamming
            return
        }
        lastWarningTimestamps[appName] = now

        createNotificationChannels(context)
        val title = "App Warning: Blocked Attempt 🛑"
        val message = "\"$appName\" is blocked. Stay focused on your active session!"
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            WARNING_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(WARNING_NOTIFICATION_ID, notification)
        Log.d("FocusNotification", "Warning posted for app '$appName'")
    }

    private fun sendAlertNotification(context: Context, title: String, message: String, notificationId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }
}
