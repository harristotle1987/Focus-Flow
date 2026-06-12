package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.service.FocusBlockerService
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class InstalledApp(
    val packageName: String,
    val appName: String
)

class FocusViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FocusRepository

    val allBlockedApps: StateFlow<List<BlockedApp>>
    val allFocusSessions: StateFlow<List<FocusSession>>
    val activeSession: StateFlow<FocusSession?>
    val allBlockedAttempts: StateFlow<List<BlockedAttempt>>
    val todayBlockedCount: StateFlow<Int>
    val allNotificationLogs: StateFlow<List<NotificationLog>>
    val allWifiAnchors: StateFlow<List<WifiAnchor>>

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    // Screen overlay block state (triggered by MainActivity intent receiving background blocks)
    private val _isBlockOverlayActive = MutableStateFlow(false)
    val isBlockOverlayActive: StateFlow<Boolean> = _isBlockOverlayActive.asStateFlow()

    private val _overlayBlockedAppName = MutableStateFlow("")
    val overlayBlockedAppName: StateFlow<String> = _overlayBlockedAppName.asStateFlow()

    private val _overlayTimeLeft = MutableStateFlow("25:00")
    val overlayTimeLeft: StateFlow<String> = _overlayTimeLeft.asStateFlow()

    // Curated quotes for the block overlay
    val quotes = listOf(
        "Deep work is not a nice-to-have, it's a superpower." to "Cal Newport",
        "Focus is a muscle, and you build it by choosing what to ignore." to "Unknown",
        "Your mind is for having ideas, not holding them." to "David Allen",
        "You will never reach your destination if you stop and throw stones at every dog that barks." to "Winston Churchill",
        "The successful warrior is the average man, with laser-like focus." to "Bruce Lee",
        "Distraction is the enemy of direction." to "Unknown",
        "Disconnect to reconnect with your true goals." to "Unknown",
        "Focus on being productive instead of busy." to "Tim Ferriss",
        "Starve your distractions, feed your focus." to "Unknown",
        "Quiet the mind, and the soul will speak." to "Ma Jaya Sati Bhagavati"
    )

    private val _randomQuote = MutableStateFlow(quotes.first())
    val randomQuote: StateFlow<Pair<String, String>> = _randomQuote.asStateFlow()

    private val _isSoundEnabled = MutableStateFlow(com.example.service.SoundAlertManager.isSoundEnabled(application))
    val isSoundEnabled: StateFlow<Boolean> = _isSoundEnabled.asStateFlow()

    fun setSoundEnabled(enabled: Boolean) {
        _isSoundEnabled.value = enabled
        com.example.service.SoundAlertManager.setSoundEnabled(getApplication(), enabled)
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FocusRepository(database.focusDao())

        allBlockedApps = repository.allBlockedApps
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allFocusSessions = repository.allFocusSessions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        activeSession = repository.activeSessionFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        allBlockedAttempts = repository.allBlockedAttempts
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        todayBlockedCount = repository.getTodayBlockedCountFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        allNotificationLogs = repository.allNotificationLogs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allWifiAnchors = repository.allWifiAnchors
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        viewModelScope.launch {
            repository.seedDefaultBlockedAppsIfNeeded()
        }
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val pm = getApplication<Application>().packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                val currentPkg = getApplication<Application>().packageName
                val appList = resolveInfos
                    .filter { it.activityInfo.packageName != currentPkg }
                    .map { info ->
                        InstalledApp(
                            packageName = info.activityInfo.packageName,
                            appName = info.loadLabel(pm).toString()
                        )
                    }.distinctBy { it.packageName }
                    .sortedBy { it.appName.lowercase() }
                _installedApps.value = appList
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startFocusSession(
        label: String,
        durationMinutes: Int,
        context: Context,
        isBreak: Boolean = false,
        isPomodoro: Boolean = false,
        pomodoroWorkDuration: Int = 25
    ) {
        viewModelScope.launch {
            val session = repository.startSession(label, durationMinutes, isBreak, isPomodoro, pomodoroWorkDuration)
            // Trigger service
            val intent = Intent(context, FocusBlockerService::class.java).apply {
                action = FocusBlockerService.ACTION_START
                putExtra(FocusBlockerService.EXTRA_SESSION_ID, session.id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun stopFocusSession(context: Context) {
        viewModelScope.launch {
            repository.stopActiveSession()
            // Stop service
            val intent = Intent(context, FocusBlockerService::class.java).apply {
                action = FocusBlockerService.ACTION_STOP
            }
            context.startService(intent)
        }
    }

    fun toggleBlockedApp(app: BlockedApp) {
        viewModelScope.launch {
            repository.updateBlockedApp(app.copy(isEnabled = !app.isEnabled))
        }
    }

    fun addCustomBlockedApp(packageName: String, appName: String) {
        viewModelScope.launch {
            repository.insertBlockedApp(packageName, appName, isEnabled = true)
        }
    }

    fun deleteBlockedApp(app: BlockedApp) {
        viewModelScope.launch {
            repository.deleteBlockedApp(app)
        }
    }

    fun showBlockOverlay(appName: String, timeLeft: String) {
        _overlayBlockedAppName.value = appName
        _overlayTimeLeft.value = timeLeft
        _isBlockOverlayActive.value = true
        // Pick new random quote
        _randomQuote.value = quotes.random()
    }

    fun dismissBlockOverlay() {
        _isBlockOverlayActive.value = false
    }

    fun clearNotificationDigest() {
        viewModelScope.launch {
            repository.clearNotificationDigest()
        }
    }

    fun addWifiAnchor(ssid: String, durationMinutes: Int = 25) {
        viewModelScope.launch {
            repository.insertWifiAnchor(ssid, durationMinutes)
        }
    }

    fun deleteWifiAnchor(anchor: WifiAnchor) {
        viewModelScope.launch {
            repository.deleteWifiAnchor(anchor.ssid)
        }
    }
}
