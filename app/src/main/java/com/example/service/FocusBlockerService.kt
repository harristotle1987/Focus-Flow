package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.BlockedApp
import com.example.data.FocusRepository
import com.example.data.FocusSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.util.Log

class FocusBlockerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var repository: FocusRepository
    private var monitoringJob: Job? = null

    companion object {
        const val CHANNEL_ID = "FocusBlockerChannel"
        const val NOTIFICATION_ID = 4242
        const val ACTION_START = "ACTION_START_BLOCKING"
        const val ACTION_STOP = "ACTION_STOP_BLOCKING"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
    }

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        repository = FocusRepository(database.focusDao())
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
                startBlocking(sessionId)
            }
            ACTION_STOP -> {
                stopBlocking()
            }
        }
        return START_STICKY
    }

    private fun startBlocking(sessionId: Int) {
        // Build initial notification to start foreground
        val notification = buildNotification("Focus Session Active", "Screen blocker is running.")
        startForeground(NOTIFICATION_ID, notification)

        // Start background polling monitor
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            repository.seedDefaultBlockedAppsIfNeeded()

            while (isActive) {
                val activeSession = repository.getActiveSession()
                if (activeSession == null || !activeSession.isActive) {
                    // Session was stopped in DB, shutdown service
                    withContext(Dispatchers.Main) {
                        stopBlocking()
                    }
                    break
                }

                val now = System.currentTimeMillis()
                if (now >= activeSession.endTime) {
                    // Session completed
                    if (activeSession.isPomodoro) {
                        if (!activeSession.isBreak) {
                            // Work sprint completed! Transition to Pomodoro Break!
                            SoundAlertManager.playWorkToBreakSound(this@FocusBlockerService)
                            repository.completeActiveSession()
                            repository.startSession(
                                label = "Pomodoro Rest Break ☕",
                                durationMinutes = 5,
                                isBreak = true,
                                isPomodoro = true,
                                pomodoroWorkDuration = activeSession.pomodoroWorkDuration
                            )
                        } else {
                            // Break sprint completed! Transition to Work Sprint!
                            SoundAlertManager.playBreakToWorkSound(this@FocusBlockerService)
                            repository.completeActiveSession()
                            repository.startSession(
                                label = "Pomodoro Work Sprint 🎯",
                                durationMinutes = activeSession.pomodoroWorkDuration,
                                isBreak = false,
                                isPomodoro = true,
                                pomodoroWorkDuration = activeSession.pomodoroWorkDuration
                            )
                        }
                        delay(1000L)
                        continue
                    } else {
                        // Normal non-pomodoro session completed
                        SoundAlertManager.playCompletionSound(this@FocusBlockerService)
                        repository.completeActiveSession()
                        withContext(Dispatchers.Main) {
                            stopBlocking()
                        }
                        break
                    }
                }

                // Check remaining time
                val remainingMs = activeSession.endTime - now
                val remainingMin = (remainingMs / 1000 / 60).toInt()
                val remainingSec = ((remainingMs / 1000) % 60).toInt()
                val timeLeftStr = String.format("%02d:%02d", remainingMin, remainingSec)

                // Secondary Fallback App Blocker Shield via Usage Access
                if (!activeSession.isBreak) {
                    val foregroundPkg = getForegroundPackage()
                    if (foregroundPkg != null && foregroundPkg != packageName && foregroundPkg != "com.android.systemui" && !foregroundPkg.contains("launcher")) {
                        val dbBlockedApps = repository.allBlockedApps.first()
                        val blockedApp = dbBlockedApps.find { it.packageName == foregroundPkg && it.isEnabled }
                        if (blockedApp != null) {
                            Log.d("FocusBlockerService", "Foreground app $foregroundPkg is blocked. Enforcing block!")
                            
                            // Log blocked attempt
                            repository.logBlockedAttempt(blockedApp.packageName, blockedApp.appName)
                            
                            // 1. Kick them to Home
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(homeIntent)
                            
                            // 2. Launch block overlay in MainActivity
                            val mainIntent = Intent(this@FocusBlockerService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra("TRIGGER_BLOCKSCREEN", true)
                                putExtra("BLOCKED_APP_NAME", blockedApp.appName)
                                putExtra("TIME_LEFT", timeLeftStr)
                            }
                            startActivity(mainIntent)
                        }
                    }
                }

                // Update notification dynamically for Break vs Work
                val notifTitle = if (activeSession.isBreak) "Rest Break ☕" else "Focus Active: ${activeSession.label}"
                val notifText = if (activeSession.isBreak) {
                    "Time remaining: $timeLeftStr • Screen lock and app blocks are lifted."
                } else {
                    "Time remaining: $timeLeftStr • Focus-Flow is blocking distractions."
                }

                val updatedNotification = buildNotification(notifTitle, notifText)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, updatedNotification)

                delay(1000L) // Poll every 1 second
            }
        }
    }

    private fun stopBlocking() {
        monitoringJob?.cancel()
        stopForeground(true)
        stopSelf()
    }

    private fun getForegroundPackage(): String? {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 10000 // Look back 10 seconds

            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var lastForegroundApp: String? = null

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastForegroundApp = event.packageName
                }
            }
            lastForegroundApp
        } catch (e: Exception) {
            Log.e("FocusBlockerService", "Error querying usage events", e)
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Session Notification",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays remaining focus session time and block updates"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
