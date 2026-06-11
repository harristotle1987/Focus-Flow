package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.BlockOverlayScreen
import com.example.data.AppDatabase
import com.example.data.BlockedApp
import com.example.data.FocusRepository
import com.example.data.FocusSession
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

// Custom FrameLayout subclass to intercept all key events (specifically Back button)
class KeyInterceptingView(context: Context, private val onBackPress: () -> Unit) : FrameLayout(context) {
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                onBackPress()
            }
            return true // Consume the back press completely
        }
        return super.dispatchKeyEvent(event)
    }
}

class FocusAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var repository: FocusRepository
    private var isSessionActive = false
    private var blockedApps = listOf<BlockedApp>()
    private var activeSession: FocusSession? = null

    // Window management trackers
    private var overlayLifecycleOwner: ServiceLifecycleOwner? = null
    private var interceptingView: KeyInterceptingView? = null
    private var currentBlockedAppName: String? = null

    private var temporaryBypassUntil: Long = 0L
    private var isPowerConnected = false
    private var lastLoggedPackage: String? = null

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    isPowerConnected = true
                    Log.d("FocusAccessibility", "Power connected!")
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    isPowerConnected = false
                    Log.d("FocusAccessibility", "Power disconnected!")
                }
            }
        }
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                @Suppress("DEPRECATION")
                val info = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                if (info != null && info.isConnected) {
                    val wifiManager = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val wifiInfo = wifiManager?.connectionInfo
                    var ssid = wifiInfo?.ssid
                    if (ssid != null) {
                        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                            ssid = ssid.substring(1, ssid.length - 1)
                        }
                        if (ssid != "<unknown ssid>" && ssid.isNotEmpty()) {
                            checkWifiAnchorConnection(ssid)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        repository = FocusRepository(database.focusDao())

        // Register power connection broadcast receiver
        val powerFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerReceiver, powerFilter)

        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        isPowerConnected = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // Register Wi-Fi SSID trigger dynamic receiver
        val wifiFilter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        registerReceiver(wifiReceiver, wifiFilter)

        // Collect repository states reactively
        serviceScope.launch {
            repository.activeSessionFlow.collectLatest { session ->
                activeSession = session
                isSessionActive = session?.isActive == true && !session.isBreak
                if (!isSessionActive) {
                    withContext(Dispatchers.Main) {
                        dismissOverlay()
                    }
                }
            }
        }

        serviceScope.launch {
            repository.allBlockedApps.collect { apps ->
                blockedApps = apps
            }
        }
    }

    private fun checkWifiAnchorConnection(ssid: String) {
        serviceScope.launch {
            val anchors = repository.allWifiAnchors.first()
            val anchor = anchors.find { it.ssid.equals(ssid, ignoreCase = true) }
            if (anchor != null) {
                val session = repository.getActiveSession()
                if (session == null || !session.isActive) {
                    // Auto-start a focus session anchor!
                    repository.startSession("Wi-Fi Auto-Focus: ${anchor.ssid}", anchor.durationMinutes)
                    Log.d("FocusAccessibility", "Auto-started Focus Session on Wi-Fi connection to ${anchor.ssid}")
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 1. App-switching doomscrolling log & check
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkgName = event.packageName?.toString() ?: return

            // Avoid logging our own app layout states or system UI
            if (pkgName != packageName && pkgName != "com.android.systemui" && !pkgName.contains("launcher")) {
                if (pkgName != lastLoggedPackage) {
                    lastLoggedPackage = pkgName
                    serviceScope.launch {
                        repository.insertAppSwitchLog(pkgName)

                        val switches = repository.getAppSwitchesInLast10Minutes()
                        if (switches.size > 5) {
                            val active = repository.getActiveSession()
                            if (active == null || !active.isActive) {
                                // Trigger automatic block session!
                                repository.startSession("Automatic Doomscroll Protection", 15)
                                repository.clearOldAppSwitches()
                                Log.d("FocusAccessibility", "Doomscrolling detected (>5 switches/10m). Started automatic Lockout Session!")
                            }
                        }
                    }
                }
            }

            // 2. Settings Uninstall block (Strict mode) deactivation block
            if (isSessionActive && pkgName == "com.android.settings") {
                // Settings is opened during active session. Block Settings to enforce the block!
                showOverlay("Android Settings (Strict Block Active)")
                return
            }

            // 3. Normal app blacklist block
            if (isSessionActive) {
                if (pkgName == packageName) return

                val blockedApp = blockedApps.find { it.packageName == pkgName && it.isEnabled }
                if (blockedApp != null) {
                    // Check if current bypass is active
                    if (System.currentTimeMillis() < temporaryBypassUntil) {
                        // User is inside 2-minute temporary bypass
                        return
                    }

                    // Distraction detected! Log attempt
                    serviceScope.launch {
                        repository.logBlockedAttempt(blockedApp.packageName, blockedApp.appName)
                    }
                    showOverlay(blockedApp.appName)
                } else {
                    // Check browser URL scanning if the app is a mobile browser
                    if (isBrowserPackage(pkgName)) {
                        val rootNode = rootInActiveWindow
                        val urlText = findUrlInNodes(rootNode)
                        if (urlText != null) {
                            val blacklistedDomain = matchesBlacklistedDomain(urlText)
                            if (blacklistedDomain != null) {
                                if (System.currentTimeMillis() < temporaryBypassUntil) {
                                    return
                                }
                                serviceScope.launch {
                                    repository.logBlockedAttempt(pkgName, blacklistedDomain)
                                }
                                showOverlay("$blacklistedDomain (Web)")
                                return
                            }
                        }
                    }
                    dismissOverlay()
                }
            } else {
                dismissOverlay()
            }
        }
    }

    private fun isBrowserPackage(pkg: String): Boolean {
        return pkg == "com.android.chrome" ||
                pkg == "org.mozilla.firefox" ||
                pkg == "com.sec.android.app.sbrowser" ||
                pkg == "com.opera.browser" ||
                pkg == "com.microsoft.emmx"
    }

    private fun findUrlInNodes(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val id = node.viewIdResourceName
        if (id != null && (id.endsWith("url_bar") || id.endsWith("url_edit_text") || id.contains("address_bar"))) {
            return node.text?.toString()
        }
        val text = node.text?.toString() ?: ""
        if (text.startsWith("http://") || text.startsWith("https://") || text.contains(".com") || text.contains(".org") || text.contains(".net")) {
            if (node.className == "android.widget.EditText" || node.className == "android.widget.TextView") {
                return text
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findUrlInNodes(child)
            if (res != null) return res
        }
        return null
    }

    private fun matchesBlacklistedDomain(url: String): String? {
        // Find if url contains a name from our blockedApps list (e.g. instagram.com, youtube.com)
        for (app in blockedApps) {
            if (!app.isEnabled) continue
            val keyword = app.appName.lowercase().replace(" ", "")
            if (url.lowercase().contains(keyword) || url.lowercase().contains(app.packageName.substringAfterLast("."))) {
                return app.appName
            }
        }
        val domains = listOf("instagram.com", "tiktok.com", "facebook.com", "twitter.com", "x.com", "youtube.com/shorts", "reddit.com")
        for (dom in domains) {
            if (url.lowercase().contains(dom)) {
                return dom
            }
        }
        return null
    }

    private fun showOverlay(appName: String) {
        if (interceptingView != null) {
            currentBlockedAppName = appName
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e("FocusBlocker", "Overlay permission is missing; cannot display overlay!")
            return
        }

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
        }

        val lifecycleOwner = ServiceLifecycleOwner().apply {
            start()
            resume()
        }
        overlayLifecycleOwner = lifecycleOwner

        currentBlockedAppName = appName

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                MyApplicationTheme {
                    var timeLeft by remember { mutableStateOf("00:00") }
                    val currentApp = remember { derivedStateOf { currentBlockedAppName ?: appName } }

                    LaunchedEffect(Unit) {
                        while (true) {
                            val session = repository.getActiveSession()
                            if (session != null && session.isActive) {
                                val remaining = session.endTime - System.currentTimeMillis()
                                if (remaining > 0) {
                                    val mins = (remaining / 1000 / 60).toInt()
                                    val secs = ((remaining / 1000) % 60).toInt()
                                    timeLeft = String.format("%02d:%02d", mins, secs)
                                } else {
                                    timeLeft = "00:00"
                                    dismissOverlay()
                                    break
                                }
                            } else {
                                timeLeft = "00:00"
                                dismissOverlay()
                                break
                            }
                            delay(1000L)
                        }
                    }

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
                    val randomQuote = remember { quotes.random() }

                    BlockOverlayScreen(
                        blockedAppName = currentApp.value,
                        timeLeft = timeLeft,
                        quote = randomQuote.first,
                        author = randomQuote.second,
                        isPowerConnected = isPowerConnected,
                        onCloseTap = {
                            redirectUserToHome()
                        },
                        onBypassSuccess = {
                            temporaryBypassUntil = System.currentTimeMillis() + 2 * 60 * 1000L // 2 min bypass!
                            dismissOverlay()
                        }
                    )
                }
            }
        }

        val wrapper = KeyInterceptingView(this) {
            redirectUserToHome()
        }.apply {
            addView(composeView)
        }

        try {
            windowManager.addView(wrapper, params)
            interceptingView = wrapper
        } catch (e: Exception) {
            Log.e("FocusBlocker", "Failed to add un-bypassable window overlay view", e)
        }
    }

    private fun redirectUserToHome() {
        dismissOverlay()
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun dismissOverlay() {
        val view = interceptingView
        if (view != null) {
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e("FocusBlocker", "Failed to remove overlay view safely", e)
            }
            overlayLifecycleOwner?.stop()
            interceptingView = null
            overlayLifecycleOwner = null
            currentBlockedAppName = null
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(powerReceiver)
            unregisterReceiver(wifiReceiver)
        } catch (e: Exception) {
            // Ignored
        }
        serviceJob.cancel()
        dismissOverlay()
    }
}
