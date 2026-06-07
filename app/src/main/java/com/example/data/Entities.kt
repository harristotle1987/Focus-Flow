package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true
)

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val durationMinutes: Int,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val isActive: Boolean = false,
    val wasCompleted: Boolean = false
)

@Entity(tableName = "blocked_attempts")
data class BlockedAttempt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis()
)
