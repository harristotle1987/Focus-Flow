package com.example.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
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
import java.util.TreeMap

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

        // Start high-frequency proactive polling (Aggressive implementation)
        startForegroundPolling()

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

    private fun getActiveWindowPackage(): String? {
        val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
        val accPkg = rootNode?.packageName?.toString()
        if (accPkg != null) {
            return accPkg
        }
        // Fallback: check focused window in the list of windows
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val windowList = try { windows } catch (e: Exception) { null }
            if (windowList != null) {
                for (window in windowList) {
                    if (window.isFocused) {
                        val root = try { window.root } catch (e: Exception) { null }
                        val pkg = root?.packageName?.toString()
                        if (!pkg.isNullOrEmpty()) {
                            return pkg
                        }
                    }
                }
            }
        }
        return getForegroundPackage()
    }

    private fun getPackageNamesFromWindows(): Set<String> {
        val packages = mutableSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val windowList = try { windows } catch (e: Exception) { null }
            if (windowList != null) {
                for (window in windowList) {
                    if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                        val root = try { window.root } catch (e: Exception) { null }
                        val pkg = root?.packageName?.toString()
                        if (!pkg.isNullOrEmpty()) {
                            packages.add(pkg)
                        }
                    }
                }
            }
        }
        return packages
    }

    private data class BlockDecision(
        val shouldBlock: Boolean,
        val appName: String = "",
        val packageName: String = ""
    )

    private fun evaluateActivePackages(): BlockDecision {
        if (!isSessionActive) {
            return BlockDecision(false)
        }

        // 1. Gather all unique packages currently visible/active
        val candidatePackages = mutableSetOf<String>()
        
        val activePkg = getActiveWindowPackage()
        if (activePkg != null) {
            candidatePackages.add(activePkg)
        }
        
        candidatePackages.addAll(getPackageNamesFromWindows())

        // 2. Iterate through candidate packages to check for block conditions
        for (pkg in candidatePackages) {
            if (pkg.isEmpty()) continue
            if (pkg == packageName) continue

            // A. Settings Force-Stop Prevention Block (Strict Mode)
            if (pkg == "com.android.settings" || pkg.contains("com.google.android.settings")) {
                val root = try { rootInActiveWindow } catch (e: Exception) { null }
                if (root != null) {
                    val textNodes = root.findAccessibilityNodeInfosByText(packageName)
                    if (textNodes != null && textNodes.isNotEmpty()) {
                        return BlockDecision(true, "Focus-Flow Protection (Strict)", pkg)
                    }
                }
                return BlockDecision(true, "Android Settings (Strict Block Active)", pkg)
            }

            // B. Normal Blacklist Block
            val blockedApp = blockedApps.find { it.packageName == pkg && it.isEnabled }
            if (blockedApp != null) {
                if (System.currentTimeMillis() < temporaryBypassUntil) {
                    continue // Bypassed
                }
                return BlockDecision(true, blockedApp.appName, pkg)
            }
        }

        // C. If no direct app matches, check browser URL scanning if the active app is a browser
        if (activePkg != null && isBrowserPackage(activePkg)) {
            val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
            val urlText = findUrlInNodes(rootNode)
            if (urlText != null) {
                val blacklistedDomain = matchesBlacklistedDomain(urlText)
                if (blacklistedDomain != null) {
                    if (System.currentTimeMillis() >= temporaryBypassUntil) {
                        return BlockDecision(true, "$blacklistedDomain (Web)", activePkg)
                    }
                }
            }
        }

        return BlockDecision(false)
    }

    private fun performPackageEnforcement(decision: BlockDecision) {
        if (decision.shouldBlock) {
            if (currentBlockedAppName != decision.appName) {
                // Warn the user with a notification
                FocusNotificationManager.sendBlockedAppWarningPlatform(this, decision.appName)
                serviceScope.launch {
                    repository.logBlockedAttempt(decision.packageName, decision.appName)
                }
            }
            showOverlay(decision.appName)
        } else {
            val activePkg = getActiveWindowPackage() ?: ""
            val isTransientSystem = activePkg == "com.android.systemui" || activePkg == "android"
            if (!isTransientSystem) {
                dismissOverlay()
            }
        }
    }

    private fun evaluateAndEnforce() {
        if (!isSessionActive) {
            dismissOverlay()
            return
        }
        val decision = evaluateActivePackages()
        performPackageEnforcement(decision)
    }

    private fun startForegroundPolling() {
        serviceScope.launch {
            while (isActive) {
                if (isSessionActive) {
                    withContext(Dispatchers.Main) {
                        evaluateAndEnforce()
                    }
                }
                delay(250L) // Poll every 250ms (Aggressive)
            }
        }
    }

    private fun getForegroundPackage(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usm?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60, time)
            if (stats != null && stats.isNotEmpty()) {
                val sortedMap = TreeMap<Long, android.app.usage.UsageStats>()
                for (usageStats in stats) {
                    sortedMap[usageStats.lastTimeUsed] = usageStats
                }
                return sortedMap.isEmpty().let { if (it) null else sortedMap.lastEntry()?.value?.packageName }
            }
        } else {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            @Suppress("DEPRECATION")
            val tasks = am?.getRunningTasks(1)
            return tasks?.firstOrNull()?.topActivity?.packageName
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: ""

        // Detect Recents panel attempts to prevent app peeking
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
             if (pkgName == "com.android.systemui" && event.className?.toString()?.contains("Recents", ignoreCase = true) == true) {
                 if (currentBlockedAppName != null || isSessionActive) {
                     performGlobalAction(GLOBAL_ACTION_HOME)
                     return
                 }
             }
        }

        // 1. App-switching doomscrolling log & check
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkgName.isNotEmpty()) {
            if (pkgName != packageName && pkgName != "com.android.systemui" && !pkgName.contains("launcher") && pkgName != "android") {
                if (pkgName != lastLoggedPackage) {
                    lastLoggedPackage = pkgName
                    serviceScope.launch {
                        repository.insertAppSwitchLog(pkgName)

                        val switches = repository.getAppSwitchesInLast10Minutes()
                        if (switches.size > 5) {
                            val active = repository.getActiveSession()
                            if (active == null || !active.isActive) {
                                repository.startSession("Automatic Doomscroll Protection", 15)
                                repository.clearOldAppSwitches()
                                Log.d("FocusAccessibility", "Doomscrolling detected (>5 switches/10m). Started automatic Lockout Session!")
                            }
                        }
                    }
                }
            }
        }

        if (!isSessionActive) {
            dismissOverlay()
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkgName.isNotEmpty()) {
            evaluateAndEnforce()
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
        // Aggressive Home Flush: Ensure the app is gone first
        if (currentBlockedAppName == null) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        if (interceptingView != null) {
            currentBlockedAppName = appName
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e("FocusBlocker", "Overlay permission is missing; falling back to Home redirect + Blockscreen Activity.")
            redirectUserToHome()
            val mainIntent = Intent(this, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("TRIGGER_BLOCKSCREEN", true)
                putExtra("BLOCKED_APP_NAME", appName)
                putExtra("TIME_LEFT", "Active Focus")
            }
            startActivity(mainIntent)
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
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
            screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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

                    BlockOverlayScreen(
                        blockedAppName = currentApp.value,
                        timeLeft = timeLeft,
                        quote = "",
                        author = "",
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
