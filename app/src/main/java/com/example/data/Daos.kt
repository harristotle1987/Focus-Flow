package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {
    // Blocked Apps
    @Query("SELECT * FROM blocked_apps")
    fun getAllBlockedApps(): Flow<List<BlockedApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApp(app: BlockedApp)

    @Update
    suspend fun updateBlockedApp(app: BlockedApp)

    @Delete
    suspend fun deleteBlockedApp(app: BlockedApp)

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_apps WHERE packageName = :packageName AND isEnabled = 1)")
    suspend fun isAppBlocked(packageName: String): Boolean

    // Focus Sessions
    @Query("SELECT * FROM focus_sessions ORDER BY id DESC")
    fun getAllFocusSessions(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE isActive = 1 LIMIT 1")
    fun getActiveSessionFlow(): Flow<FocusSession?>

    @Query("SELECT * FROM focus_sessions WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSession(): FocusSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusSession(session: FocusSession): Long

    @Update
    suspend fun updateFocusSession(session: FocusSession)

    @Query("UPDATE focus_sessions SET isActive = 0")
    suspend fun deactivateAllSessions()

    // Blocked Attempts
    @Query("SELECT * FROM blocked_attempts ORDER BY timestamp DESC")
    fun getAllBlockedAttempts(): Flow<List<BlockedAttempt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedAttempt(attempt: BlockedAttempt)

    @Query("SELECT COUNT(*) FROM blocked_attempts WHERE timestamp >= :sinceTimestamp")
    fun getBlockedAttemptCountFlow(sinceTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM blocked_attempts WHERE timestamp >= :sinceTimestamp")
    suspend fun getBlockedAttemptCount(sinceTimestamp: Long): Int
}
