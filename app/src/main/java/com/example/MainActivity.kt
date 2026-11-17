package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.receiver.RemoteNotificationScheduler
import com.example.ui.components.AnnouncementPopupDialog
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.AccountBookScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudentPortalScreen
import com.example.ui.theme.VuLmsTheme
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val appThemeMode by viewModel.appTheme.collectAsStateWithLifecycle()
            val isDark = when (appThemeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            VuLmsTheme(darkTheme = isDark) {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
                val isPrivacyAccepted by viewModel.isPrivacyAccepted.collectAsStateWithLifecycle()
                val loginState by viewModel.loginState.collectAsStateWithLifecycle()
                val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
                val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
                val showAutoSyncPrompt by viewModel.showAutoSyncPrompt.collectAsStateWithLifecycle()
                val activeBannerNotifications by viewModel.activeBannerNotifications.collectAsStateWithLifecycle()
                val allUpcomingNotifications by viewModel.allUpcomingNotifications.collectAsStateWithLifecycle()
                val activePopupNotification by viewModel.activePopupNotification.collectAsStateWithLifecycle()
                val isCheckingNotifications by viewModel.isCheckingNotifications.collectAsStateWithLifecycle()
                val manualNotificationsState by viewModel.manualNotificationsState.collectAsStateWithLifecycle()

                // Refresh notifications whenever the user opens or returns to the app
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START) {
                            viewModel.fetchRemoteNotifications(isManual = false)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // Keep notifications fresh every 5 minutes while app stays open in foreground
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            delay(RemoteNotificationScheduler.REFRESH_INTERVAL_MS)
                            viewModel.fetchRemoteNotifications(isManual = false)
                        }
                    }
                }

                // Check for In-App Update dialog
                updateInfo?.let { info ->
                    UpdateDialog(
                        updateInfo = info,
                        onDismiss = { viewModel.dismissUpdateDialog() }
                    )
                }

                // Once-per-day Remote Announcement popup (yields if UpdateDialog or AutoSync prompt is visible)
                if (isLoggedIn && updateInfo == null && !showAutoSyncPrompt) {
                    activePopupNotification?.let { announcement ->
                        AnnouncementPopupDialog(
                            notification = announcement,
                            onDismiss = { viewModel.dismissNotificationPopup(announcement.id) }
                        )
                    }
                }

                // First-time prompt for Auto-Sync on Launch
                if (isLoggedIn && showAutoSyncPrompt) {
                    AlertDialog(
                        onDismissRequest = { viewModel.setAutoSyncChoice(false) },
                        icon = {
                            Icon(Icons.Filled.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        title = {
                            Text("Auto-Sync on Launch", fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Text(
                                "Would you like VU LMS Reminder to automatically fetch and update your activities, quizzes, assignments, and fee challans whenever you open the app?\n\nYou can change this preference anytime in Settings.",
                                fontSize = 13.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.setAutoSyncChoice(true) },
                                modifier = Modifier.testTag("auto_sync_enable_button")
                            ) {
                                Text("Enable Auto-Sync")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { viewModel.setAutoSyncChoice(false) },
                                modifier = Modifier.testTag("auto_sync_manual_button")
                            ) {
                                Text("Manual Only")
                            }
                        }
                    )
                }

                // Request POST_NOTIFICATIONS permission on Android 13+
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* handle permission result */ }

                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                if (!isLoggedIn) {
                    LoginScreen(
                        loginState = loginState,
                        isPrivacyAccepted = isPrivacyAccepted,
                        onAcceptPrivacy = { viewModel.acceptPrivacy() },
                        onLogin = { studentId, pass -> viewModel.login(studentId, pass) },
                        onResetLoginState = { viewModel.resetLoginState() }
                    )
                } else {
                    BackHandler(enabled = selectedTab != 0) {
                        viewModel.selectTab(0)
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets.safeDrawing,
                        bottomBar = {
                            NavigationBar(
                                modifier = Modifier.testTag("main_navigation_bar")
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { viewModel.selectTab(0) },
                                    icon = {
                                        Icon(
                                            if (selectedTab == 0) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                                            contentDescription = "Dashboard"
                                        )
                                    },
                                    label = { Text("Activities") },
                                    modifier = Modifier.testTag("nav_item_dashboard")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { viewModel.selectTab(1) },
                                    icon = {
                                        Icon(
                                            if (selectedTab == 1) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                                            contentDescription = "Timeline"
                                        )
                                    },
                                    label = { Text("Timeline") },
                                    modifier = Modifier.testTag("nav_item_calendar")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { viewModel.selectTab(2) },
                                    icon = {
                                        Icon(
                                            if (selectedTab == 2) Icons.Filled.ReceiptLong else Icons.Outlined.ReceiptLong,
                                            contentDescription = "Fees"
                                        )
                                    },
                                    label = { Text("Fees") },
                                    modifier = Modifier.testTag("nav_item_fees")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = { viewModel.selectTab(3) },
                                    icon = {
                                        Icon(
                                            if (selectedTab == 3) Icons.Filled.Badge else Icons.Outlined.Badge,
                                            contentDescription = "Student"
                                        )
                                    },
                                    label = { Text("Student") },
                                    modifier = Modifier.testTag("nav_item_student")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 4,
                                    onClick = { viewModel.selectTab(4) },
                                    icon = {
                                        Icon(
                                            if (selectedTab == 4) Icons.Filled.Settings else Icons.Outlined.Settings,
                                            contentDescription = "Settings"
                                        )
                                    },
                                    label = { Text("Settings") },
                                    modifier = Modifier.testTag("nav_item_settings")
                                )
                            }
                        }
                    ) { innerPadding ->
                        val stats by viewModel.stats.collectAsStateWithLifecycle()
                        val activities by viewModel.filteredActivities.collectAsStateWithLifecycle()
                        val allActivities by viewModel.allActivities.collectAsStateWithLifecycle()
                        val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
                        val selectedActivityType by viewModel.selectedActivityType.collectAsStateWithLifecycle()
                        val showUpcomingOnly by viewModel.showUpcomingOnly.collectAsStateWithLifecycle()
                        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                        val studentId by viewModel.studentId.collectAsStateWithLifecycle()
                        val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
                        val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
                        val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
                        val studentAvatarFile by viewModel.studentAvatarFile.collectAsStateWithLifecycle()
                        val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
                        val manualUpdateResult by viewModel.manualUpdateResult.collectAsStateWithLifecycle()
                        val activityTypeCounts by viewModel.activityTypeCounts.collectAsStateWithLifecycle()

                        // New tabs state
                        val challans by viewModel.challans.collectAsStateWithLifecycle()
                        val currentSubjects by viewModel.currentSubjects.collectAsStateWithLifecycle()
                        val studentProfile by viewModel.studentProfile.collectAsStateWithLifecycle()
                        val studentECardFile by viewModel.studentECardFile.collectAsStateWithLifecycle()
                        val isDownloadingECard by viewModel.isDownloadingECard.collectAsStateWithLifecycle()
                        val eCardMessage by viewModel.eCardMessage.collectAsStateWithLifecycle()

                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            when (selectedTab) {
                                0 -> DashboardScreen(
                                    stats = stats,
                                    activities = activities,
                                    selectedStatus = selectedStatus,
                                    selectedActivityType = selectedActivityType,
                                    showUpcomingOnly = showUpcomingOnly,
                                    searchQuery = searchQuery,
                                    studentId = studentId,
                                    studentName = studentProfile.studentName,
                                    lastSyncTime = lastSyncTime,
                                    isSyncing = isSyncing,
                                    syncMessage = syncMessage,
                                    typeCounts = activityTypeCounts,
                                    onStatusSelected = { viewModel.setStatus(it) },
                                    onActivityTypeSelected = { viewModel.setActivityType(it) },
                                    onToggleUpcomingOnly = { viewModel.toggleUpcomingOnly(it) },
                                    onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                                    onSyncNow = { viewModel.syncNow() },
                                    onClearSyncMessage = { viewModel.clearSyncMessage() },
                                    bannerNotifications = activeBannerNotifications,
                                    allUpcomingNotifications = allUpcomingNotifications
                                )

                                1 -> CalendarScreen(
                                    activities = allActivities
                                )

                                2 -> AccountBookScreen(
                                    challans = challans,
                                    isSyncing = isSyncing,
                                    syncMessage = syncMessage,
                                    onClearSyncMessage = { viewModel.clearSyncMessage() },
                                    onPreparePaymentSession = { challan -> viewModel.prepareChallanForPayment(challan) },
                                    onSyncNow = { viewModel.syncNow() }
                                )

                                3 -> StudentPortalScreen(
                                    studentProfile = studentProfile,
                                    currentSubjects = currentSubjects,
                                    studentAvatarFile = studentAvatarFile,
                                    isDownloadingECard = isDownloadingECard,
                                    eCardFile = studentECardFile,
                                    eCardMessage = eCardMessage,
                                    isSyncing = isSyncing,
                                    onDownloadECard = { viewModel.downloadECard() },
                                    onSyncNow = { viewModel.syncNow() }
                                )

                                4 -> SettingsScreen(
                                    prefs = viewModel.getPreferences(),
                                    studentId = studentId,
                                    studentAvatarFile = studentAvatarFile,
                                    isCheckingUpdate = isCheckingUpdate,
                                    manualUpdateResult = manualUpdateResult,
                                    isCheckingNotifications = isCheckingNotifications,
                                    manualNotificationsState = manualNotificationsState,
                                    onLogout = { viewModel.logout() },
                                    onCheckUpdate = { viewModel.checkForAppUpdates(isManual = true) },
                                    onDismissManualUpdateResult = { viewModel.dismissManualUpdateResult() },
                                    onCheckUpcomingNotifications = { viewModel.fetchRemoteNotifications(isManual = true) },
                                    onDismissManualNotifications = { viewModel.dismissManualNotificationsDialog() },
                                    onSetTheme = { viewModel.setAppTheme(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
