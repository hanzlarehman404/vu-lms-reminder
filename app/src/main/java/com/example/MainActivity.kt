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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.VuLmsTheme
import com.example.ui.viewmodel.MainViewModel

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
                val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
                val isPrivacyAccepted by viewModel.isPrivacyAccepted.collectAsStateWithLifecycle()
                val loginState by viewModel.loginState.collectAsStateWithLifecycle()
                val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
                val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()

                // Check for In-App Update dialog
                updateInfo?.let { info ->
                    UpdateDialog(
                        updateInfo = info,
                        onDismiss = { viewModel.dismissUpdateDialog() }
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
                                    label = { Text("Dashboard") },
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
                                            if (selectedTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
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
                        val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
                        val showUpcomingOnly by viewModel.showUpcomingOnly.collectAsStateWithLifecycle()
                        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                        val studentId by viewModel.studentId.collectAsStateWithLifecycle()
                        val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
                        val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
                        val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
                        val studentAvatarFile by viewModel.studentAvatarFile.collectAsStateWithLifecycle()

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
                                    selectedCategory = selectedCategory,
                                    showUpcomingOnly = showUpcomingOnly,
                                    searchQuery = searchQuery,
                                    studentId = studentId,
                                    lastSyncTime = lastSyncTime,
                                    isSyncing = isSyncing,
                                    syncMessage = syncMessage,
                                    onCategorySelected = { viewModel.setCategory(it) },
                                    onToggleUpcomingOnly = { viewModel.toggleUpcomingOnly(it) },
                                    onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                                    onToggleCompletion = { id, done -> viewModel.toggleActivityCompleted(id, done) },
                                    onMarkAllAvailableDone = { viewModel.markAllAvailableAsDone() },
                                    onSyncNow = { viewModel.syncNow() },
                                    onClearSyncMessage = { viewModel.clearSyncMessage() }
                                )

                                1 -> CalendarScreen(
                                    activities = allActivities,
                                    onToggleCompletion = { id, done -> viewModel.toggleActivityCompleted(id, done) }
                                )

                                2 -> SettingsScreen(
                                    prefs = viewModel.getPreferences(),
                                    studentId = studentId,
                                    studentAvatarFile = studentAvatarFile,
                                    onLogout = { viewModel.logout() },
                                    onResetCompletions = { viewModel.resetAllCompletions() },
                                    onCheckUpdate = { viewModel.checkForAppUpdates() },
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
