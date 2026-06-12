package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BlockedApp
import com.example.ui.FocusViewModel
import com.example.ui.PermissionUtils
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: FocusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                FocusFlowApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("TRIGGER_BLOCKSCREEN", false) == true) {
            val appName = intent.getStringExtra("BLOCKED_APP_NAME") ?: "Distracting App"
            val timeLeft = intent.getStringExtra("TIME_LEFT") ?: "--:--"
            viewModel.showBlockOverlay(appName, timeLeft)
        }
    }
}

@Composable
fun FocusFlowApp(viewModel: FocusViewModel) {
    val context = LocalContext.current

    // Observe DB States
    val blockedApps by viewModel.allBlockedApps.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val allBlockedAttempts by viewModel.allBlockedAttempts.collectAsStateWithLifecycle()
    val todayBlockedCount by viewModel.todayBlockedCount.collectAsStateWithLifecycle()
    val allNotificationLogs by viewModel.allNotificationLogs.collectAsStateWithLifecycle()
    val allWifiAnchors by viewModel.allWifiAnchors.collectAsStateWithLifecycle()

    // Observe Blocker Overlay state
    val isOverlayActive by viewModel.isBlockOverlayActive.collectAsStateWithLifecycle()
    val overlayBlockedAppName by viewModel.overlayBlockedAppName.collectAsStateWithLifecycle()
    val overlayTimeLeft by viewModel.overlayTimeLeft.collectAsStateWithLifecycle()
    val randomQuote by viewModel.randomQuote.collectAsStateWithLifecycle()

    // Local runtime states for permission checks
    var hasStatsPerm by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }
    var hasOverlayPerm by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var hasNotifyPerm by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }
    var hasAccessibilityPerm by remember { mutableStateOf(PermissionUtils.isAccessibilityServiceEnabled(context, com.example.service.FocusAccessibilityService::class.java)) }

    // Re-verify permissions when returning to activity
    LaunchedEffect(isOverlayActive) {
        hasStatsPerm = PermissionUtils.hasUsageStatsPermission(context)
        hasOverlayPerm = PermissionUtils.hasOverlayPermission(context)
        hasNotifyPerm = PermissionUtils.hasNotificationPermission(context)
        hasAccessibilityPerm = PermissionUtils.isAccessibilityServiceEnabled(context, com.example.service.FocusAccessibilityService::class.java)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SpaceBackground
    ) {
        if (isOverlayActive) {
            androidx.activity.compose.BackHandler {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
            }
            // Full-Screen overlay countdown blocking view
            BlockOverlayScreen(
                blockedAppName = overlayBlockedAppName,
                timeLeft = overlayTimeLeft,
                quote = randomQuote.first,
                author = randomQuote.second,
                isPowerConnected = false,
                onCloseTap = {
                    // Redirect safely to home screen to bypass distraction
                    viewModel.dismissBlockOverlay()
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                }
            )
        } else {
            // Normal Application Flow
            var selectedTab by remember { mutableStateOf(0) }

            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = SpaceSurface,
                        contentColor = SpaceTextPrimary
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Focus Launcher") },
                            label = { Text("Focus") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SpaceAccent,
                                unselectedIconColor = SpaceTextSecondary,
                                selectedTextColor = SpaceAccent,
                                unselectedTextColor = SpaceTextSecondary,
                                indicatorColor = SpaceCard
                            ),
                            modifier = Modifier.testTag("nav_focus_tab")
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.Lock, contentDescription = "Blocklist Screen") },
                            label = { Text("Blocklist") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SpaceTeal,
                                unselectedIconColor = SpaceTextSecondary,
                                selectedTextColor = SpaceTeal,
                                unselectedTextColor = SpaceTextSecondary,
                                indicatorColor = SpaceCard
                            ),
                            modifier = Modifier.testTag("nav_blocklist_tab")
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.List, contentDescription = "Stats History Dashboard") },
                            label = { Text("Log Stats") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SpaceAccent,
                                unselectedIconColor = SpaceTextSecondary,
                                selectedTextColor = SpaceAccent,
                                unselectedTextColor = SpaceTextSecondary,
                                indicatorColor = SpaceCard
                            ),
                            modifier = Modifier.testTag("nav_stats_tab")
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Advanced Settings Dashboard") },
                            label = { Text("Advanced") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SpaceTeal,
                                unselectedIconColor = SpaceTextSecondary,
                                selectedTextColor = SpaceTeal,
                                unselectedTextColor = SpaceTextSecondary,
                                indicatorColor = SpaceCard
                            ),
                            modifier = Modifier.testTag("nav_advanced_tab")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SpaceBackground)
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        0 -> FocusHomeScreen(
                            viewModel = viewModel,
                            activeSession = activeSession,
                            hasStatsPerm = hasStatsPerm,
                            hasOverlayPerm = hasOverlayPerm,
                            hasNotifyPerm = hasNotifyPerm,
                            hasAccessibilityPerm = hasAccessibilityPerm,
                            todayBlockedCount = todayBlockedCount,
                            onRefreshPermissions = {
                                hasStatsPerm = PermissionUtils.hasUsageStatsPermission(context)
                                hasOverlayPerm = PermissionUtils.hasOverlayPermission(context)
                                hasNotifyPerm = PermissionUtils.hasNotificationPermission(context)
                                hasAccessibilityPerm = PermissionUtils.isAccessibilityServiceEnabled(context, com.example.service.FocusAccessibilityService::class.java)
                            }
                        )
                        1 -> BlocklistScreen(
                            viewModel = viewModel,
                            blockedApps = blockedApps
                        )
                        2 -> StatsDashboardScreen(
                            todayBlockedCount = todayBlockedCount,
                            allBlockedAttempts = allBlockedAttempts
                        )
                        3 -> AdvancedDashboardScreen(
                            viewModel = viewModel,
                            allNotificationLogs = allNotificationLogs,
                            allWifiAnchors = allWifiAnchors
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FocusHomeScreen(
    viewModel: FocusViewModel,
    activeSession: com.example.data.FocusSession?,
    hasStatsPerm: Boolean,
    hasOverlayPerm: Boolean,
    hasNotifyPerm: Boolean,
    hasAccessibilityPerm: Boolean,
    todayBlockedCount: Int,
    onRefreshPermissions: () -> Unit
) {
    val context = LocalContext.current
    val blockedApps by viewModel.allBlockedApps.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isSoundEnabled by viewModel.isSoundEnabled.collectAsStateWithLifecycle()
    var showAppDrawerDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var sessionLabel by remember { mutableStateOf("Co-Work Sprint") }
    var durationMinutes by remember { mutableStateOf(25f) }
    var selectedMode by remember { mutableStateOf(1) } // Default to Pomodoro mode as requested

    // Notification permission request launcher for Android 13+
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        onRefreshPermissions()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Identity Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            Brush.linearGradient(listOf(SpaceAccent, SpaceTeal)),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Logo icon",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Focus-Flow",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Realtime Screen Blocker",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpaceTextSecondary
                    )
                }
            }
        }

        // Permission check alert widget block
        val needsNotifyPerm = !hasNotifyPerm && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        if (!hasStatsPerm || !hasOverlayPerm || !hasAccessibilityPerm || needsNotifyPerm) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("permission_card"),
                    colors = CardDefaults.cardColors(containerColor = SpaceCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Action Required: Setup Permissions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SpaceTeal
                        )
                        Text(
                            text = "To monitor distractions and overlays, Focus-Flow requires 'Accessibility Service', 'Draw over other apps' and 'Usage Access' permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpaceTextSecondary
                        )

                        if (!hasOverlayPerm) {
                            Button(
                                onClick = { PermissionUtils.launchOverlaySettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = SpaceAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("grant_overlay_btn")
                            ) {
                                Text("1. Grant Draw Over Other Apps Overlay", fontSize = 12.sp)
                            }
                        }

                        if (!hasAccessibilityPerm) {
                            Button(
                                onClick = { PermissionUtils.launchAccessibilitySettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = SpaceTeal),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("grant_accessibility_btn")
                            ) {
                                Text("2. Enable Blocker Accessibility Service", fontSize = 12.sp)
                            }
                        }

                        if (!hasStatsPerm) {
                            Button(
                                onClick = { PermissionUtils.launchUsageStatsSettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = SpaceCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SpaceTextSecondary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("grant_usage_btn")
                            ) {
                                Text("3. Grant Usage Access (Optional Dynamic Stats)", fontSize = 11.sp, color = SpaceTextPrimary)
                            }
                        }

                        if (needsNotifyPerm) {
                            Button(
                                onClick = { notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                                colors = ButtonDefaults.buttonColors(containerColor = SpaceAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("grant_notification_btn")
                            ) {
                                Text("4. Grant Notification Alert Permission", fontSize = 12.sp)
                            }
                        }

                        TextButton(
                            onClick = onRefreshPermissions,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Check Permissions", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Click to Refresh Setup Status", fontSize = 12.sp, color = SpaceTextPrimary)
                        }
                    }
                }
            }
        }

        // Dynamic State block switcher (Active vs Settings launcher panel)
        if (activeSession != null && activeSession.isActive) {
            item {
                ActiveFocusClockCard(
                    session = activeSession,
                    onStopTap = { viewModel.stopFocusSession(context) },
                    onTransitionTap = { nextIsBreak ->
                        if (nextIsBreak) {
                            com.example.service.SoundAlertManager.playWorkToBreakSound(context)
                            viewModel.startFocusSession(
                                label = "Pomodoro Rest Break ☕",
                                durationMinutes = 5,
                                context = context,
                                isBreak = true,
                                isPomodoro = activeSession.isPomodoro,
                                pomodoroWorkDuration = activeSession.pomodoroWorkDuration
                            )
                        } else {
                            com.example.service.SoundAlertManager.playBreakToWorkSound(context)
                            viewModel.startFocusSession(
                                label = "Pomodoro Work Sprint 🎯",
                                durationMinutes = activeSession.pomodoroWorkDuration,
                                context = context,
                                isBreak = false,
                                isPomodoro = activeSession.isPomodoro,
                                pomodoroWorkDuration = activeSession.pomodoroWorkDuration
                            )
                        }
                    },
                    todayBlockedAttempts = todayBlockedCount
                )
            }
        } else {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_session_card"),
                    colors = CardDefaults.cardColors(containerColor = SpaceCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Launch New Focus Session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        // Mode Selector Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SpaceSurface, shape = RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val modes = listOf("Focus Block 🎯", "Pomodoro Loop ⏲", "Rest Break ☕")
                            modes.forEachIndexed { index, title ->
                                val selected = selectedMode == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (selected) SpaceCard else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedMode = index }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) SpaceTeal else SpaceTextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        if (selectedMode == 0) {
                            // Custom focus configuration
                            OutlinedTextField(
                                value = sessionLabel,
                                onValueChange = { sessionLabel = it },
                                label = { Text("Session Goal Name") },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = SpaceAccent,
                                    unfocusedBorderColor = SpaceTextSecondary,
                                    focusedLabelColor = SpaceAccent,
                                    unfocusedLabelColor = SpaceTextSecondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("session_goal_input")
                            )
                        }

                        if (selectedMode == 1) {
                            // Pomodoro Timer Component Configuration
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("🍅", fontSize = 18.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Adjustable Pomodoro Loop", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                        }
                                        Text("A classic productivity cycle. Work for ${durationMinutes.toInt()}m, then rest for 5m. Repeats automatically with sound alerts.", style = MaterialTheme.typography.bodySmall, color = SpaceTextSecondary)
                                    }
                                }
                            }
                        }

                        // Duration selection (Enabled for all focus modes)
                        if (selectedMode != 2 || durationMinutes > 0) { 
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val label = if (selectedMode == 1) "Focus Sprint Duration" else "Duration"
                                    Text(label, color = SpaceTextSecondary, fontSize = 13.sp)
                                    Text("${durationMinutes.toInt()} minutes", color = SpaceAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Slider(
                                    value = durationMinutes,
                                    onValueChange = { durationMinutes = it },
                                    valueRange = 5f..120f,
                                    steps = 23,
                                    colors = SliderDefaults.colors(
                                        thumbColor = SpaceAccent,
                                        activeTrackColor = SpaceAccent,
                                        inactiveTrackColor = SpaceSurface
                                    ),
                                    modifier = Modifier.testTag("duration_slider")
                                )
                            }

                            // Presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val presets = if (selectedMode == 2) listOf(5, 10, 15, 20) else listOf(15, 25, 45, 60)
                                presets.forEach { preset ->
                                    SuggestionChip(
                                        onClick = { durationMinutes = preset.toFloat() },
                                        label = { Text("$preset min") },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (durationMinutes.toInt() == preset) SpaceAccent.copy(alpha = 0.2f) else Color.Transparent,
                                            labelColor = if (durationMinutes.toInt() == preset) SpaceAccent else SpaceTextPrimary
                                        ),
                                        modifier = Modifier.testTag("preset_$preset")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val ctaText = when (selectedMode) {
                            1 -> "Start Pomodoro Loop"
                            2 -> "Initiate Rest Break"
                            else -> "Initiate Focus Blocker"
                        }
                        val btnColor = if (selectedMode == 2) SpaceTeal else SpaceAccent

                        Button(
                            onClick = {
                                when (selectedMode) {
                                    1 -> {
                                        // Play starting work sprint sound
                                        com.example.service.SoundAlertManager.playBreakToWorkSound(context)
                                        viewModel.startFocusSession(
                                            label = "Pomodoro Work Sprint 🎯",
                                            durationMinutes = durationMinutes.toInt(),
                                            context = context,
                                            isBreak = false,
                                            isPomodoro = true,
                                            pomodoroWorkDuration = durationMinutes.toInt()
                                        )
                                    }
                                    2 -> {
                                        // Play starting rest break sound
                                        com.example.service.SoundAlertManager.playWorkToBreakSound(context)
                                        viewModel.startFocusSession(
                                            label = "Custom Rest Break ☕",
                                            durationMinutes = durationMinutes.toInt(),
                                            context = context,
                                            isBreak = true,
                                            isPomodoro = false
                                        )
                                    }
                                    else -> {
                                        // Play work start sound
                                        com.example.service.SoundAlertManager.playBreakToWorkSound(context)
                                        viewModel.startFocusSession(
                                            label = sessionLabel,
                                            durationMinutes = durationMinutes.toInt(),
                                            context = context,
                                            isBreak = false,
                                            isPomodoro = false
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("start_session_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Launch Session")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(ctaText, fontWeight = FontWeight.Bold, color = if (selectedMode == 2) Color.Black else Color.White)
                        }
                    }
                }
            }

            // Quick Stats Indicator Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(SpaceCard, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$todayBlockedCount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (todayBlockedCount > 0) SpaceError else SpaceTeal
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Distractions Blocked Today",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (todayBlockedCount > 0) "Stay strong! Focus-Flow shielded you $todayBlockedCount times." else "No distracted apps opened today! Clean streak.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SpaceTextSecondary
                            )
                        }
                    }
                }
            }

            // 🛡️ Application Blocker Shield Setup card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SpaceCard),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (activeSession?.isActive == true) SpaceTeal else SpaceTextSecondary.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Shield Blocker",
                                tint = if (activeSession?.isActive == true) SpaceTeal else SpaceAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "System App Blocker Shield",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = if (activeSession?.isActive == true) "Active shield is blocking chosen apps." else "Passive. Start block to enforce rules.",
                                    fontSize = 11.sp,
                                    color = SpaceTextSecondary
                                )
                            }
                        }

                        // Start / Stop quick controls
                        Divider(color = SpaceSurface, thickness = 1.dp)

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (activeSession?.isActive == true) "🛡️ SCREEN BLOCKING ACTIVE" else "🛡️ SCREEN BLOCKING INACTIVE",
                                color = if (activeSession?.isActive == true) SpaceTeal else SpaceTextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )

                            if (activeSession?.isActive == true) {
                                Button(
                                    onClick = { viewModel.stopFocusSession(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SpaceError),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("home_blocker_stop_btn")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Stop", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stop Blocker", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        com.example.service.SoundAlertManager.playBreakToWorkSound(context)
                                        viewModel.startFocusSession(
                                            label = "Direct Blocker Run",
                                            durationMinutes = 25,
                                            context = context
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SpaceAccent),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("home_blocker_start_btn")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Start", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Start Blocker", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }

                        Divider(color = SpaceSurface, thickness = 1.dp)

                        // Sound configuration option inside Blocker Shield card
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SpaceSurface, shape = RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Alert Sound Status",
                                    tint = if (isSoundEnabled) SpaceTeal else SpaceTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Transition Alert Audio",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (isSoundEnabled) "Bells & tones play on transitions." else "Polite silent focus. No audio played.",
                                        fontSize = 10.sp,
                                        color = SpaceTextSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = isSoundEnabled,
                                onCheckedChange = { viewModel.setSoundEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SpaceAccent,
                                    checkedTrackColor = SpaceCard,
                                    uncheckedThumbColor = SpaceTextSecondary,
                                    uncheckedTrackColor = SpaceSurface
                                ),
                                modifier = Modifier.testTag("sound_enabled_switch")
                            )
                        }

                        Divider(color = SpaceSurface, thickness = 1.dp)

                        // Blocked list apps and App Drawer Trigger Button
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Currently Distracting Apps in Blocklist: (${blockedApps.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = SpaceTextPrimary
                            )

                            if (blockedApps.isEmpty()) {
                                Text(
                                    text = "No apps on blocklist. Tap below to select apps to block.",
                                    fontSize = 11.sp,
                                    color = SpaceTextSecondary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                // Horizontal layout of chips for currently blocked apps
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val visibleList = blockedApps.take(4)
                                    items(visibleList.size) { i ->
                                        val app = visibleList[i]
                                        Box(
                                            modifier = Modifier
                                                .background(SpaceSurface, shape = RoundedCornerShape(8.dp))
                                                .border(1.dp, if (app.isEnabled) SpaceTeal.copy(alpha = 0.5f) else Color.Transparent, shape = RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .background(if (app.isEnabled) SpaceTeal else SpaceTextSecondary, shape = CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(app.appName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    if (blockedApps.size > 4) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .background(SpaceSurface, shape = RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("+${blockedApps.size - 4} more", color = SpaceTextSecondary, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            // Big Direct Button to configure the app list
                            Button(
                                onClick = { showAppDrawerDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = SpaceSurface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SpaceAccent),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .testTag("home_setup_blocklist_btn")
                            ) {
                                Icon(Icons.Default.List, contentDescription = "App Drawer List", tint = SpaceAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Detect Apps from App Drawer", color = SpaceAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAppDrawerDialog) {
        Dialog(onDismissRequest = { showAppDrawerDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                colors = CardDefaults.cardColors(containerColor = SpaceBackground),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SpaceAccent)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "App Drawer Blocklist",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Select which system apps to auto-block",
                                color = SpaceTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { showAppDrawerDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Dialog", tint = Color.LightGray)
                        }
                    }

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search system apps...", color = SpaceTextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SpaceTextSecondary) },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SpaceAccent,
                            unfocusedBorderColor = SpaceSurface,
                            focusedContainerColor = SpaceSurface,
                            unfocusedContainerColor = SpaceSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("modal_app_search")
                    )

                    val filteredApps = remember(installedApps, searchQuery, blockedApps) {
                        if (searchQuery.isBlank()) {
                            installedApps
                        } else {
                            installedApps.filter {
                                it.appName.contains(searchQuery, ignoreCase = true) ||
                                        it.packageName.contains(searchQuery, ignoreCase = true)
                            }
                        }
                    }

                    if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No installed apps matched '$searchQuery'",
                                color = SpaceTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps) { systemApp ->
                                // Determine if this app is currently in the DB blockedApps list
                                val dbApp = blockedApps.find { it.packageName == systemApp.packageName }
                                val isActiveInBlocklist = dbApp != null && dbApp.isEnabled

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(SpaceCard, shape = CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Menu,
                                                contentDescription = "App Icon",
                                                tint = if (isActiveInBlocklist) SpaceAccent else SpaceTextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(systemApp.appName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                            Text(systemApp.packageName, color = SpaceTextSecondary, fontSize = 10.sp, maxLines = 1)
                                        }

                                        // Switch control
                                        Switch(
                                            checked = isActiveInBlocklist,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    viewModel.addCustomBlockedApp(systemApp.packageName, systemApp.appName)
                                                } else {
                                                    if (dbApp != null) {
                                                        viewModel.deleteBlockedApp(dbApp)
                                                    }
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = SpaceAccent,
                                                checkedTrackColor = SpaceCard,
                                                uncheckedThumbColor = SpaceTextSecondary,
                                                uncheckedTrackColor = SpaceSurface
                                            ),
                                            modifier = Modifier.testTag("modal_switch_${systemApp.packageName}")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { showAppDrawerDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveFocusClockCard(
    session: com.example.data.FocusSession,
    onStopTap: () -> Unit,
    onTransitionTap: (nextIsBreak: Boolean) -> Unit,
    todayBlockedAttempts: Int
) {
    // Dynamic countdown timer animation logic
    var remainingTimeText by remember { mutableStateOf("00:00") }
    var progressFraction by remember { mutableStateOf(1.0f) }

    LaunchedEffect(session) {
        while (session.isActive) {
            val now = System.currentTimeMillis()
            val remainingMs = session.endTime - now
            if (remainingMs <= 0) {
                remainingTimeText = "00:00"
                progressFraction = 0.0f
                break
            }
            val totalMs = session.durationMinutes * 60 * 1000L
            progressFraction = (remainingMs.toFloat() / totalMs).coerceIn(0.0f, 1.0f)

            val mins = (remainingMs / 1000 / 60).toInt()
            val secs = ((remainingMs / 1000) % 60).toInt()
            remainingTimeText = String.format("%02d:%02d", mins, secs)
            delay(500L)
        }
    }

    val arcColor = if (session.isBreak) SpaceTeal else SpaceAccent
    val headerLabel = if (session.isBreak) "BREAK ACTIVE ☕" else "FOCUS ACTIVE: ${session.label}"
    val bodyDescription = if (session.isBreak) {
        "Take a breather! Distractions and notification blockages are temporarily lifted. Relax your eyes and stretch."
    } else {
        "Screen blocker active. Apps toggled in Blocklist will be blocked automatically till the countdown ends."
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_timer_card"),
        colors = CardDefaults.cardColors(containerColor = SpaceCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = headerLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = arcColor,
                modifier = Modifier
                    .background(SpaceSurface, shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // Animated breathing circle graphics countdown timer
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw outer background circle
                    drawCircle(
                        color = Color(0xFF1E293B),
                        radius = size.minDimension / 2,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Draw animated arc indicator
                    drawArc(
                        color = arcColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progressFraction,
                        useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = remainingTimeText,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "REMAINING",
                        fontSize = 10.sp,
                        color = SpaceTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = bodyDescription,
                style = MaterialTheme.typography.bodySmall,
                color = SpaceTextSecondary,
                textAlign = TextAlign.Center
            )

            // Dynamic manual transition action buttons
            if (session.isBreak) {
                Button(
                    onClick = { onTransitionTap(false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                        .testTag("transition_to_work_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = SpaceAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Transition to focus")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Resume Work Sprint 🎯", fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                Button(
                    onClick = { onTransitionTap(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                        .testTag("transition_to_break_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = SpaceTeal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Transition to break")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Take a 5-min Rest Break ☕", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }

            // Always provide Stop/Disconnect CTA at the bottom
            Button(
                onClick = onStopTap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp)
                    .testTag("stop_session_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SpaceError),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Terminate session")
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (session.isBreak) "Deactivate Break Session" else "Deactivate Block Filter", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BlocklistScreen(
    viewModel: FocusViewModel,
    blockedApps: List<BlockedApp>
) {
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var blocklistSubTab by remember { mutableStateOf(0) } // 0 = Active Blocklist, 1 = App Drawer
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var inputAppName by remember { mutableStateOf("") }
    var inputPackageName by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Blocker Status Card with Start & Stop buttons
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeSession?.isActive == true) SpaceTeal.copy(alpha = 0.15f) else SpaceCard
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (activeSession?.isActive == true) SpaceTeal else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (activeSession?.isActive == true) SpaceTeal else SpaceTextSecondary,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (activeSession?.isActive == true) "BLOCKER ACTIVE" else "BLOCKER INACTIVE",
                                color = if (activeSession?.isActive == true) SpaceTeal else SpaceTextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (activeSession?.isActive == true) {
                                "Focus session is active. Shield is fully active."
                            } else {
                                "Shield is currently bypassed. Tap Start to protect."
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    if (activeSession?.isActive == true) {
                        Button(
                            onClick = { viewModel.stopFocusSession(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = SpaceError),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("blocklist_stop_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Stop Blocker", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                com.example.service.SoundAlertManager.playBreakToWorkSound(context)
                                viewModel.startFocusSession(
                                    label = "Immediate Blocker Session",
                                    durationMinutes = 25,
                                    context = context
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpaceAccent),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("blocklist_start_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start Blocker", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Start", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Distraction Blocklist list header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Distraction Blocklist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Customize apps to block during active focus",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpaceTextSecondary
                    )
                }

                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .background(SpaceAccent, shape = CircleShape)
                        .size(36.dp)
                        .testTag("add_blocklist_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add custom app", tint = Color.White)
                }
            }

            // Sub-tabs Selection Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpaceSurface, shape = RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val subTabs = listOf("Active Blocklist (${blockedApps.size})", "App Drawer (${installedApps.size})")
                subTabs.forEachIndexed { index, title ->
                    val selected = blocklistSubTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) SpaceCard else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { blocklistSubTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) SpaceTeal else SpaceTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (blocklistSubTab == 0) {
                // ACTIVE BLOCKLIST TAB
                if (blockedApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Lock Empty",
                                tint = SpaceTextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text("No apps in Focus Blocklist", color = SpaceTextSecondary, fontSize = 14.sp)
                            TextButton(onClick = { blocklistSubTab = 1 }) {
                                Text("Browse Apps in App Drawer", color = SpaceTeal, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(blockedApps) { app ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(SpaceCard, shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = "Distracted app marker",
                                            tint = if (app.isEnabled) SpaceTeal else SpaceTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.appName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(app.packageName, color = SpaceTextSecondary, fontSize = 11.sp, maxLines = 1)
                                    }
                                    Switch(
                                        checked = app.isEnabled,
                                        onCheckedChange = { viewModel.toggleBlockedApp(app) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = SpaceTeal,
                                            checkedTrackColor = SpaceCard,
                                            uncheckedThumbColor = SpaceTextSecondary,
                                            uncheckedTrackColor = SpaceSurface
                                        ),
                                        modifier = Modifier.testTag("switch_${app.packageName}")
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { viewModel.deleteBlockedApp(app) },
                                        modifier = Modifier.testTag("delete_${app.packageName}")
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete app filter",
                                            tint = SpaceError,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // APP DRAWER RECONSTRUCTION TAB
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Search box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search system apps...", color = SpaceTextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SpaceTextSecondary) },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SpaceAccent,
                            unfocusedBorderColor = SpaceSurface,
                            focusedContainerColor = SpaceSurface,
                            unfocusedContainerColor = SpaceSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("app_drawer_search")
                    )

                    val filteredApps = remember(installedApps, searchQuery, blockedApps) {
                        if (searchQuery.isBlank()) {
                            installedApps
                        } else {
                            installedApps.filter {
                                it.appName.contains(searchQuery, ignoreCase = true) ||
                                        it.packageName.contains(searchQuery, ignoreCase = true)
                            }
                        }
                    }

                    if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No installed apps matched '$searchQuery'",
                                color = SpaceTextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredApps) { systemApp ->
                                // Determine if this app is currently in the DB blockedApps list
                                val dbApp = blockedApps.find { it.packageName == systemApp.packageName }
                                val isActiveInBlocklist = dbApp != null && dbApp.isEnabled

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(SpaceCard, shape = CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Menu,
                                                contentDescription = "App Logo Holder",
                                                tint = if (isActiveInBlocklist) SpaceAccent else SpaceTextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(systemApp.appName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(systemApp.packageName, color = SpaceTextSecondary, fontSize = 11.sp, maxLines = 1)
                                        }

                                        // Simple Block switch that immediately inserts into or deletes from DB blocklist
                                        Switch(
                                            checked = isActiveInBlocklist,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    viewModel.addCustomBlockedApp(systemApp.packageName, systemApp.appName)
                                                } else {
                                                    if (dbApp != null) {
                                                        viewModel.deleteBlockedApp(dbApp)
                                                    }
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = SpaceAccent,
                                                checkedTrackColor = SpaceCard,
                                                uncheckedThumbColor = SpaceTextSecondary,
                                                uncheckedTrackColor = SpaceSurface
                                            ),
                                            modifier = Modifier.testTag("drawer_switch_${systemApp.packageName}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            Dialog(onDismissRequest = { showAddDialog = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SpaceCard)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Add Custom App Filter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        OutlinedTextField(
                            value = inputAppName,
                            onValueChange = { inputAppName = it },
                            label = { Text("Display App Name (e.g. YouTube)") },
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = SpaceAccent,
                                unfocusedBorderColor = SpaceTextSecondary
                            ),
                            modifier = Modifier.testTag("dialog_app_name")
                        )

                        OutlinedTextField(
                            value = inputPackageName,
                            onValueChange = { inputPackageName = it },
                            label = { Text("Java Package Name (e.g. com.google.android.youtube)") },
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = SpaceAccent,
                                unfocusedBorderColor = SpaceTextSecondary
                            ),
                            modifier = Modifier.testTag("dialog_package_name")
                        )

                        Text(
                            text = "*Note: You must specify the app's exact system package name for the blocker engine to identify it.",
                            color = SpaceTextSecondary,
                            fontSize = 11.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showAddDialog = false }) {
                                Text("Cancel", color = SpaceTextSecondary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (inputPackageName.isNotBlank() && inputAppName.isNotBlank()) {
                                        viewModel.addCustomBlockedApp(
                                            inputPackageName.trim(),
                                            inputAppName.trim()
                                        )
                                        inputPackageName = ""
                                        inputAppName = ""
                                        showAddDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SpaceAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("dialog_confirm_add")
                            ) {
                                Text("Add Filter")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsDashboardScreen(
    todayBlockedCount: Int,
    allBlockedAttempts: List<com.example.data.BlockedAttempt>
) {
    val formatter = remember { SimpleDateFormat("hh:mm a • d MMM yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Screen Time Blocker Logs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Realtime telemetry of blocked distraction attempts",
                style = MaterialTheme.typography.bodySmall,
                color = SpaceTextSecondary
            )
        }

        // Highlight Hero Metrics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SpaceSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Total Distraction Shieldings Today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpaceTextSecondary
                )
                Text(
                    text = "$todayBlockedCount",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpaceAccent,
                    modifier = Modifier.testTag("today_blocks_metric")
                )
                Text(
                    text = if (todayBlockedCount > 10) "High distraction risks detected. Keep focus sessions smaller." else if (todayBlockedCount > 0) "Your focus willpower in action!" else "Spotless! No distraction attempts tracked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpaceTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        // List Header
        Text(
            text = "Chronological Shield Logs (${allBlockedAttempts.size})",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = SpaceTeal
        )

        if (allBlockedAttempts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "No violations yet", tint = SpaceTeal, modifier = Modifier.size(40.dp))
                    Text("No screen blocker logs. Great job!", color = SpaceTextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allBlockedAttempts) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SpaceCard),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(SpaceError.copy(alpha = 0.2f), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Shield Violation Triggered", tint = SpaceError, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.appName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(item.packageName, color = SpaceTextSecondary, fontSize = 10.sp)
                            }
                            Text(
                                text = formatter.format(Date(item.timestamp)),
                                color = SpaceTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

val BLOCKING_QUOTES = listOf(
    "Deep work is not a nice-to-have, it's a superpower." to "Cal Newport",
    "Focus is a muscle, and you build it by choosing what to ignore." to "Unknown",
    "Your mind is for having ideas, not holding them." to "David Allen",
    "You will never reach your destination if you stop and throw stones at every dog that barks." to "Winston Churchill",
    "The successful warrior is the average man, with laser-like focus." to "Bruce Lee",
    "Distraction is the enemy of direction." to "Unknown",
    "Disconnect to reconnect with your true goals." to "Unknown",
    "Focus on being productive instead of busy." to "Tim Ferriss",
    "Starve your distractions, feed your focus." to "Unknown",
    "Quiet the mind, and the soul will speak." to "Ma Jaya Sati Bhagavati",
    "Concentrate all your thoughts upon the work at hand. The sun's rays do not burn until brought to a focus." to "Alexander Graham Bell",
    "It is during our darkest moments that we must focus to see the light." to "Aristotle",
    "Where of one's attention goes, energy flows." to "James Redfield",
    "Simplicity is the ultimate sophistication." to "Leonardo da Vinci",
    "A clear purpose will bind your focus into a single point of light." to "Unknown",
    "Do not let what you cannot do interfere with what you can do." to "John Wooden",
    "The secret of change is to focus all of your energy, not on fighting the old, but on building the new." to "Socrates",
    "Don't stay in bed, unless you can make money in bed." to "George Burns",
    "He who runs after two hares catches neither." to "Latin Proverb",
    "If you want to live a happy life, tie it to a goal, not to people or things." to "Albert Einstein",
    "I fear not the man who has practiced 10,000 kicks once, but I fear the man who has practiced one kick 10,000 times." to "Bruce Lee",
    "You cannot depend on your eyes when your imagination is out of focus." to "Mark Twain",
    "Only the paranoid survive when it comes to keeping their focus pristine." to "Andy Grove",
    "Discipline is choosing between what you want now and what you want most." to "Abraham Lincoln",
    "Focus is the art of saying 'no' to a thousand things." to "Steve Jobs",
    "One way to keep momentum going is to have constantly greater goals." to "Michael Korda",
    "By failing to prepare, you are preparing to fail." to "Benjamin Franklin",
    "Tomorrow becomes today when you stop waiting and start doing." to "Unknown",
    "Ordinary people think merely of spending time. Great people think of using it." to "Arthur Schopenhauer",
    "Energy is the essence of life. Every day you decide how you are going to spend it by where you focus." to "Oprah Winfrey",
    "The key to focus is omitting the noise from your mental landscape." to "Unknown",
    "If you don't pay appropriate attention to what has your attention, it will take more of your attention than it deserves." to "David Allen",
    "Quality means doing it right when no one is looking." to "Henry Ford",
    "Great things are done by a series of small things brought together." to "Vincent Van Gogh",
    "It does not matter how slowly you go as long as you do not stop." to "Confucius"
)

enum class OverlayState {
    BLOCKED,
    TIME_TAX
}

@Composable
fun BlockOverlayScreen(
    blockedAppName: String,
    timeLeft: String,
    quote: String,
    author: String,
    isPowerConnected: Boolean = false,
    onCloseTap: () -> Unit,
    onBypassSuccess: () -> Unit = {}
) {
    var overlayState by remember { mutableStateOf(OverlayState.BLOCKED) }
    var currentQuoteIndex by remember { mutableStateOf((0 until BLOCKING_QUOTES.size).random()) }

    // Automated randomized continuous slideshow every 6 seconds with smart state change
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(6000L)
            var nextIndex = (0 until BLOCKING_QUOTES.size).random()
            while (nextIndex == currentQuoteIndex && BLOCKING_QUOTES.size > 1) {
                nextIndex = (0 until BLOCKING_QUOTES.size).random()
            }
            currentQuoteIndex = nextIndex
        }
    }

    if (overlayState == OverlayState.BLOCKED) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(24.dp)
                .testTag("full_block_screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // App top identity
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Shield Alert Locked", tint = SpaceError, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "FOCUS-FLOW LOCKED",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = SpaceError,
                    letterSpacing = 1.sp
                )
            }

            // Middle container details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(SpaceError.copy(alpha = 0.15f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Lock overlay screen badge",
                        tint = SpaceError,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Text(
                    text = "$blockedAppName is Blocked!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                // Remaining Time Clock Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = SpaceCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$timeLeft Remaining", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SpaceTeal)
                        Text("active focus pomodoro sprint", fontSize = 9.sp, color = SpaceTextSecondary)
                    }
                }

                // Beautiful sliding motivational quote Box with modern border accent and background quote symbol
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("animated_quote_card"),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SpaceTeal.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Background stylized double quotation mark for visual sophistication
                        Text(
                            text = "“",
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpaceTeal.copy(alpha = 0.12f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 12.dp, y = (-12).dp)
                        )

                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AnimatedContent(
                                targetState = currentQuoteIndex,
                                transitionSpec = {
                                    (slideInHorizontally { width -> width / 2 } + fadeIn(animationSpec = spring())).togetherWith(
                                        slideOutHorizontally { width -> -width / 2 } + fadeOut(animationSpec = spring())
                                    )
                                },
                                label = "QuoteSlideshowTransition"
                            ) { index ->
                                val (qText, qAuthor) = BLOCKING_QUOTES[index]
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = qText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = "— $qAuthor",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SpaceTeal,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // Interactive elegant horizontal indicator dots
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                repeat(5) { i ->
                                    val active = (currentQuoteIndex % 5) == i
                                    Box(
                                        modifier = Modifier
                                            .size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                                            .background(
                                                color = if (active) SpaceTeal else SpaceTextSecondary.copy(alpha = 0.4f),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Actions
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isPowerConnected) {
                    Button(
                        onClick = { overlayState = OverlayState.TIME_TAX },
                        modifier = Modifier.fillMaxWidth().height(45.dp).testTag("power_bypass_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceTeal),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Bypass charger", tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Initiate 2-min Bypass (Requires 60s Time Tax)", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 12.sp)
                    }
                } else {
                    Text(
                        text = "🔌 Connect phone charger to unlock temporary bypass",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpaceTextSecondary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Button(
                    onClick = onCloseTap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                        .testTag("overlay_dismiss_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = SpaceAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = "Return home")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back to Productivity!", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    } else {
        // Time Tax Screen
        var taxTimeLeft by remember { mutableStateOf(60) }
        var progressMultiplier by remember { mutableStateOf(0f) }

        LaunchedEffect(Unit) {
            taxTimeLeft = 60
            while (taxTimeLeft > 0) {
                delay(1000L)
                taxTimeLeft--
                progressMultiplier = (60 - taxTimeLeft).toFloat() / 60f
                if (taxTimeLeft == 0) {
                    onBypassSuccess()
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0F19))
                .padding(24.dp)
                .testTag("time_tax_screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Identity Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = "Tax Trigger Warning", tint = SpaceTeal, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "COGNITIVE TIME TAX",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = SpaceTeal,
                    letterSpacing = 1.sp
                )
            }

            // Central progress details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(SpaceTeal.copy(alpha = 0.1f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${taxTimeLeft}s",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpaceTeal
                        )
                        Text(
                            text = "WAIT TIME",
                            fontSize = 9.sp,
                            color = SpaceTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Stay here for 60 seconds to earn a 2-minute temporary bypass. Leaving this screen will cancel development.",
                    fontSize = 14.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Beautiful Linear progress bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = progressMultiplier,
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = SpaceTeal,
                        trackColor = Color(0xFF1E293B)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0%", fontSize = 10.sp, color = SpaceTextSecondary)
                        Text("${(progressMultiplier * 100).toInt()}% Tax Paid", fontSize = 10.sp, color = SpaceTeal, fontWeight = FontWeight.Bold)
                        Text("100%", fontSize = 10.sp, color = SpaceTextSecondary)
                    }
                }
            }

            // Bottom cancel/dismiss back Button
            Button(
                onClick = { overlayState = OverlayState.BLOCKED },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SpaceError),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel tax checkout")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Abort Bypass (Reset Timer)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun AdvancedDashboardScreen(
    viewModel: FocusViewModel,
    allNotificationLogs: List<com.example.data.NotificationLog>,
    allWifiAnchors: List<com.example.data.WifiAnchor>
) {
    val context = LocalContext.current
    val isSoundEnabled by viewModel.isSoundEnabled.collectAsStateWithLifecycle()
    var inputSsid by remember { mutableStateOf("") }
    var inputDuration by remember { mutableStateOf("25") }
    var showsAddWifi by remember { mutableStateOf(false) }

    // Check device admin active
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager }
    val componentName = remember { android.content.ComponentName(context, com.example.service.FocusDeviceAdminReceiver::class.java) }
    var isStrictEnabled by remember { mutableStateOf(dpm.isAdminActive(componentName)) }

    // Re-evaluate on Resume/State change
    LaunchedEffect(Unit) {
        isStrictEnabled = dpm.isAdminActive(componentName)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Sound Configuration Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sound_settings_card"),
                colors = CardDefaults.cardColors(containerColor = SpaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Transition Alert Audio Settings",
                                tint = if (isSoundEnabled) SpaceTeal else SpaceTextSecondary
                            )
                            Text(
                                text = "Transition Sound Alert",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Switch(
                            checked = isSoundEnabled,
                            onCheckedChange = { viewModel.setSoundEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SpaceAccent,
                                checkedTrackColor = SpaceCard,
                                uncheckedThumbColor = SpaceTextSecondary,
                                uncheckedTrackColor = SpaceSurface
                            ),
                            modifier = Modifier.testTag("sound_settings_switch")
                        )
                    }

                    Text(
                        text = "Plays polite, eyes-safe tones on session start, breaks, and completions. Turn off to run in total silent focus mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpaceTextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Strict Protection Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("strict_mode_card"),
                colors = CardDefaults.cardColors(containerColor = SpaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = "Strict Protection", tint = SpaceTeal)
                            Text("Strict Mode (Uninstall lock)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        if (isStrictEnabled) {
                            Text(
                                "ACTIVE",
                                color = SpaceTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .background(SpaceTeal.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        } else {
                            Text(
                                "INACTIVE",
                                color = SpaceTextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .background(SpaceSurface, shape = RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "Prevents deactivation of Focus-Flow and blocks access to Android Settings to bypass active focus timers. Must be enabled prior to starting a sprint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpaceTextSecondary,
                        lineHeight = 18.sp
                    )

                    Button(
                        onClick = {
                            if (!isStrictEnabled) {
                                val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects Focus-Flow against forced uninstall or deactivation during active focus sessions.")
                                }
                                context.startActivity(intent)
                            } else {
                                val intent = Intent().apply {
                                    component = android.content.ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStrictEnabled) SpaceError.copy(alpha = 0.2f) else SpaceAccent
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (isStrictEnabled) "Deactivate Strict Guard" else "Activate Strict Protection (Enables Guard)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isStrictEnabled) SpaceError else Color.White
                        )
                    }
                }
            }
        }

        // Wi-Fi SSIDs Anchor Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("wifi_anchoring_card"),
                colors = CardDefaults.cardColors(containerColor = SpaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Wi-Fi triggers", tint = SpaceAccent)
                            Text("Wi-Fi SSID Anchoring", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        IconButton(
                            onClick = { showsAddWifi = !showsAddWifi },
                            modifier = Modifier.background(SpaceSurface, shape = CircleShape).size(28.dp)
                        ) {
                            Icon(
                                if (showsAddWifi) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = "Add Anchor",
                                tint = SpaceAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "Connect phone to study/work networks to auto-trigger a focus session block list automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpaceTextSecondary
                    )

                    if (showsAddWifi) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .background(SpaceSurface, shape = RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text("Add Wireless Study Network", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                            OutlinedTextField(
                                value = inputSsid,
                                onValueChange = { inputSsid = it },
                                placeholder = { Text("Enter Wi-Fi name (SSID)") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SpaceAccent,
                                    unfocusedBorderColor = SpaceCard,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = SpaceBackground,
                                    unfocusedContainerColor = SpaceBackground
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = inputDuration,
                                onValueChange = { inputDuration = it },
                                placeholder = { Text("Duration in minutes (e.g. 25)") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SpaceAccent,
                                    unfocusedBorderColor = SpaceCard,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = SpaceBackground,
                                    unfocusedContainerColor = SpaceBackground
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Button(
                                onClick = {
                                    if (inputSsid.isNotEmpty()) {
                                        val mins = inputDuration.toIntOrNull() ?: 25
                                        viewModel.addWifiAnchor(inputSsid, mins)
                                        inputSsid = ""
                                        showsAddWifi = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SpaceAccent),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Bind Study SSID", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (allWifiAnchors.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No SSID anchors added yet.", color = SpaceTextSecondary, fontSize = 12.sp)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            allWifiAnchors.forEach { anchor ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SpaceSurface, shape = RoundedCornerShape(8.dp))
                                        .padding(vertical = 8.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Settings, contentDescription = "Active Wi-Fi SSID", tint = SpaceTeal, modifier = Modifier.size(16.dp))
                                        Column {
                                            Text(anchor.ssid, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Schedules ${anchor.durationMinutes}m focus session", color = SpaceTextSecondary, fontSize = 10.sp)
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteWifiAnchor(anchor) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete SSID Anchor", tint = SpaceError, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Calm Focus Digest (Intercepted notifications) Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("notification_digest_card"),
                colors = CardDefaults.cardColors(containerColor = SpaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Notifications, contentDescription = "suppressed items", tint = SpaceAccent)
                            Text("Calm Focus Digest", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        if (allNotificationLogs.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearNotificationDigest() }) {
                                Text("Clear All", color = SpaceError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = "Suppress dopamine notifications during focus sessions. Delayed alerts appear here so you can browse them on your own schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpaceTextSecondary,
                        lineHeight = 18.sp
                    )

                    if (allNotificationLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Check, contentDescription = "Clean Digest Inbox", tint = SpaceTeal, modifier = Modifier.size(24.dp))
                                Text("Your notification digest inbox is wonderfully empty.", color = SpaceTextSecondary, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            allNotificationLogs.forEach { log ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.Star, contentDescription = "suppressed notification log app icon badge", tint = SpaceAccent, modifier = Modifier.size(12.dp))
                                                Text(log.appName, color = SpaceAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                                            Text(timeFormat.format(Date(log.timestamp)), color = SpaceTextSecondary, fontSize = 9.sp)
                                        }
                                        Text(log.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        if (log.text.isNotEmpty()) {
                                            Text(log.text, color = SpaceTextSecondary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

