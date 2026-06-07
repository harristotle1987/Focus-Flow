package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FocusRepository(private val focusDao: FocusDao) {

    val allBlockedApps: Flow<List<BlockedApp>> = focusDao.getAllBlockedApps()
    val allFocusSessions: Flow<List<FocusSession>> = focusDao.getAllFocusSessions()
    val activeSessionFlow: Flow<FocusSession?> = focusDao.getActiveSessionFlow()
    val allBlockedAttempts: Flow<List<BlockedAttempt>> = focusDao.getAllBlockedAttempts()

    suspend fun getActiveSession(): FocusSession? {
        return focusDao.getActiveSession()
    }

    suspend fun startSession(label: String, durationMinutes: Int): FocusSession {
        // Deactivate any currently active sessions first
        focusDao.deactivateAllSessions()
        val startTime = System.currentTimeMillis()
        val endTime = startTime + (durationMinutes * 60 * 1000)
        val session = FocusSession(
            label = label,
            durationMinutes = durationMinutes,
            startTime = startTime,
            endTime = endTime,
            isActive = true
        )
        val id = focusDao.insertFocusSession(session)
        return session.copy(id = id.toInt())
    }

    suspend fun stopActiveSession() {
        val active = focusDao.getActiveSession()
        if (active != null) {
            focusDao.updateFocusSession(active.copy(isActive = false, wasCompleted = false))
        }
    }

    suspend fun completeActiveSession() {
        val active = focusDao.getActiveSession()
        if (active != null) {
            focusDao.updateFocusSession(active.copy(isActive = false, wasCompleted = true))
        }
    }

    suspend fun insertBlockedApp(packageName: String, appName: String, isEnabled: Boolean = true) {
        focusDao.insertBlockedApp(BlockedApp(packageName, appName, isEnabled))
    }

    suspend fun updateBlockedApp(app: BlockedApp) {
        focusDao.updateBlockedApp(app)
    }

    suspend fun deleteBlockedApp(app: BlockedApp) {
        focusDao.deleteBlockedApp(app)
    }

    suspend fun isAppBlocked(packageName: String): Boolean {
        return focusDao.isAppBlocked(packageName)
    }

    suspend fun logBlockedAttempt(packageName: String, appName: String) {
        focusDao.insertBlockedAttempt(BlockedAttempt(packageName = packageName, appName = appName))
    }

    fun getTodayBlockedCountFlow(): Flow<Int> {
        val startOfToday = getStartOfToday()
        return focusDao.getBlockedAttemptCountFlow(startOfToday)
    }

    suspend fun getTodayBlockedCount(): Int {
        val startOfToday = getStartOfToday()
        return focusDao.getBlockedAttemptCount(startOfToday)
    }

    private fun getStartOfToday(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    suspend fun seedDefaultBlockedAppsIfNeeded() {
        val existing = allBlockedApps.first()
        if (existing.isEmpty()) {
            val defaults = listOf(
                BlockedApp("com.instagram.android", "Instagram", true),
                BlockedApp("com.zhiliaoapp.musically", "TikTok", true),
                BlockedApp("com.facebook.katana", "Facebook", true),
                BlockedApp("com.twitter.android", "Twitter / X", true),
                BlockedApp("com.google.android.youtube", "YouTube", true),
                BlockedApp("com.netflix.mediaclient", "Netflix", true),
                BlockedApp("com.snapchat.android", "Snapchat", true),
                BlockedApp("com.reddit.frontpage", "Reddit", true)
            )
            for (app in defaults) {
                focusDao.insertBlockedApp(app)
            }
        }
    }
}
