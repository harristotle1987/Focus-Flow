package com.example

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

    // Observe Blocker Overlay state
    val isOverlayActive by viewModel.isBlockOverlayActive.collectAsStateWithLifecycle()
    val overlayBlockedAppName by viewModel.overlayBlockedAppName.collectAsStateWithLifecycle()
    val overlayTimeLeft by viewModel.overlayTimeLeft.collectAsStateWithLifecycle()
    val randomQuote by viewModel.randomQuote.collectAsStateWithLifecycle()

    // Local runtime states for permission checks
    var hasStatsPerm by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }
    var hasOverlayPerm by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var hasNotifyPerm by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }

    // Re-verify permissions when returning to activity
    LaunchedEffect(isOverlayActive) {
        hasStatsPerm = PermissionUtils.hasUsageStatsPermission(context)
        hasOverlayPerm = PermissionUtils.hasOverlayPermission(context)
        hasNotifyPerm = PermissionUtils.hasNotificationPermission(context)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SpaceBackground
    ) {
        if (isOverlayActive) {
            // Full-Screen overlay countdown blocking view
            BlockOverlayScreen(
                blockedAppName = overlayBlockedAppName,
                timeLeft = overlayTimeLeft,
                quote = randomQuote.first,
                author = randomQuote.second,
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
                            todayBlockedCount = todayBlockedCount,
                            onRefreshPermissions = {
                                hasStatsPerm = PermissionUtils.hasUsageStatsPermission(context)
                                hasOverlayPerm = PermissionUtils.hasOverlayPermission(context)
                                hasNotifyPerm = PermissionUtils.hasNotificationPermission(context)
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
    todayBlockedCount: Int,
    onRefreshPermissions: () -> Unit
) {
    val context = LocalContext.current
    var sessionLabel by remember { mutableStateOf("Co-Work Sprint") }
    var durationMinutes by remember { mutableStateOf(25f) }
    var showPresetDialog by remember { mutableStateOf(false) }

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
        if (!hasStatsPerm || !hasOverlayPerm) {
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
                            text = "To monitor distractions and overlays, Focus-Flow requires 'Usage Access' and 'Draw over other apps' permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpaceTextSecondary
                        )

                        if (!hasStatsPerm) {
                            Button(
                                onClick = { PermissionUtils.launchUsageStatsSettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = SpaceAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("grant_usage_btn")
                            ) {
                                Text("1. Grant Usage Statistics Access", fontSize = 12.sp)
                            }
                        }

                        if (!hasOverlayPerm) {
                            Button(
                                onClick = { PermissionUtils.launchOverlaySettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = SpaceTeal),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("grant_overlay_btn")
                            ) {
                                Text("2. Grant Draw Over Other Apps Overlay", fontSize = 12.sp)
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

                        // Duration Slider
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Duration", color = SpaceTextSecondary, fontSize = 13.sp)
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
                                )
                            )
                        }

                        // Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(15, 25, 45, 60).forEach { preset ->
                                SuggestionChip(
                                    onClick = { durationMinutes = preset.toFloat() },
                                    label = { Text("$preset min") },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        labelColor = if (durationMinutes.toInt() == preset) SpaceAccent else SpaceTextPrimary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                viewModel.startFocusSession(sessionLabel, durationMinutes.toInt(), context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("start_session_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SpaceAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Launch Session")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Initiate Focus Blocker", fontWeight = FontWeight.Bold)
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
        }
    }
}

@Composable
fun ActiveFocusClockCard(
    session: com.example.data.FocusSession,
    onStopTap: () -> Unit,
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
                text = "FOCUS ACTIVE: ${session.label}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SpaceTeal,
                modifier = Modifier.background(SpaceSurface, shape = RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
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
                        color = SpaceAccent,
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
                text = "Screen blocker active. Apps toggled in Blocklist will be blocked automatically till the countdown ends.",
                style = MaterialTheme.typography.bodySmall,
                color = SpaceTextSecondary,
                textAlign = TextAlign.Center
            )

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
                Text("Deactivate Block Filter", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BlocklistScreen(
    viewModel: FocusViewModel,
    blockedApps: List<BlockedApp>
) {
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
                        text = "Choose the apps that distract you most",
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
                        Icon(Icons.Default.Lock, contentDescription = "Lock Empty", tint = SpaceTextSecondary, modifier = Modifier.size(48.dp))
                        Text("No apps in Focus Blocklist", color = SpaceTextSecondary, fontSize = 14.sp)
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
                                    Icon(Icons.Default.Delete, contentDescription = "Delete app filter", tint = SpaceError, modifier = Modifier.size(18.dp))
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

@Composable
fun BlockOverlayScreen(
    blockedAppName: String,
    timeLeft: String,
    quote: String,
    author: String,
    onCloseTap: () -> Unit
) {
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
                fontSize = 24.sp,
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
                    Text("$timeLeft Remaining", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SpaceTeal)
                    Text("active focus pomodoro sprint", fontSize = 9.sp, color = SpaceTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Beautiful motivational quote Box
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "“$quote”",
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Text(
                        text = "— $author",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpaceTeal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Bottom Action to close and return
        Button(
            onClick = onCloseTap,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(48.dp)
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
