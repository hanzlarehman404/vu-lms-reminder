package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.R
import coil.compose.AsyncImage
import com.example.data.model.ActivityEntity
import com.example.data.model.RemoteNotificationItem
import com.example.ui.components.ActivityCard
import com.example.ui.components.ActivityDetailDialog
import com.example.ui.components.CountdownTimerCard
import com.example.ui.components.ManualNotificationsUiState
import com.example.ui.components.MovingNoticeBanner
import com.example.ui.components.UpcomingNoticesDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DashboardStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    stats: DashboardStats,
    activities: List<ActivityEntity>,
    selectedStatus: String,
    selectedActivityType: String,
    showUpcomingOnly: Boolean,
    searchQuery: String,
    studentId: String,
    lastSyncTime: Long,
    isSyncing: Boolean,
    syncMessage: String?,
    typeCounts: Map<String, Int> = emptyMap(),
    onStatusSelected: (String) -> Unit,
    onActivityTypeSelected: (String) -> Unit,
    onToggleUpcomingOnly: (Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onToggleCompletion: (activityId: String, completed: Boolean) -> Unit = { _, _ -> },
    onMarkAllAvailableDone: () -> Unit = {},
    onSyncNow: () -> Unit,
    onClearSyncMessage: () -> Unit,
    bannerNotifications: List<RemoteNotificationItem> = emptyList(),
    allUpcomingNotifications: List<RemoteNotificationItem> = emptyList()
) {
    val context = LocalContext.current
    var selectedActivityForDetail by remember { mutableStateOf<ActivityEntity?>(null) }
    var showResyncWarningDialog by remember { mutableStateOf(false) }
    var showBannerDetailDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    val lastSyncFormatted = remember(lastSyncTime) {
        if (lastSyncTime <= 0L) "Not synced yet"
        else SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastSyncTime))
    }

    if (showResyncWarningDialog) {
        AlertDialog(
            onDismissRequest = { showResyncWarningDialog = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = VuWarningAmber)
            },
            title = {
                Text("Confirm LMS Resync", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Warning: Synchronizing with VU LMS may log you out of your current web browser session because VU LMS permits only one active login at a time.\n\nDo you want to proceed?",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResyncWarningDialog = false
                        onSyncNow()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VuGreenPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Proceed & Sync", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResyncWarningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedActivityForDetail != null) {
        ActivityDetailDialog(
            activity = selectedActivityForDetail!!,
            onDismiss = { selectedActivityForDetail = null }
        )
    }

    if (showBannerDetailDialog) {
        val itemsToDisplay = allUpcomingNotifications.ifEmpty { bannerNotifications }
        UpcomingNoticesDialog(
            state = ManualNotificationsUiState.Loaded(itemsToDisplay),
            onDismiss = { showBannerDetailDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Official VU Logo in Top Bar with bundled vector fallback
                        AsyncImage(
                            model = "https://vulms.vu.edu.pk/App_Themes/Default/Images/logos/logo-1.png",
                            placeholder = painterResource(R.drawable.ic_vu_logo),
                            error = painterResource(R.drawable.ic_vu_logo),
                            fallback = painterResource(R.drawable.ic_vu_logo),
                            contentDescription = "VU Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(32.dp)
                                .aspectRatio(16f / 10f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "VU LMS Reminder",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = VuInfoCyan.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = studentId.ifEmpty { "STUDENT" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = VuInfoCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Synced: $lastSyncFormatted",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showResyncWarningDialog = true },
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("sync_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync Now",
                            tint = VuInfoCyan,
                            modifier = if (isSyncing) Modifier.rotate(rotation) else Modifier
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sync status message banner (auto-dismissed smoothly after sync completes)
            var lastDisplayedMessage by remember { mutableStateOf("") }
            if (syncMessage != null) {
                lastDisplayedMessage = syncMessage
            }
            AnimatedVisibility(
                visible = syncMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VuSuccessMint.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VuSuccessMint.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = VuSuccessMint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = syncMessage ?: lastDisplayedMessage,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(
                            onClick = onClearSyncMessage,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            MovingNoticeBanner(
                notifications = bannerNotifications,
                onBannerClick = { showBannerDetailDialog = true }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

            // Hero Countdown Timer for nearest deadline (from available tasks)
            if (!showUpcomingOnly) {
                item {
                    CountdownTimerCard(
                        activity = stats.nearestUpcoming,
                        onOpenLms = { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.ifEmpty { "https://vulms.vu.edu.pk" }))
                                context.startActivity(intent)
                            } catch (ignored: Exception) {}
                        }
                    )
                }
            }

            // Available vs Upcoming Segmented Control (Item 1 requirement)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isAvailableTab = !showUpcomingOnly
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAvailableTab) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (isAvailableTab) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onToggleUpcomingOnly(false) }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ Available Now",
                                fontWeight = if (isAvailableTab) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (isAvailableTab) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isAvailableTab) VuInfoCyan.copy(alpha = 0.15f) else Color.Transparent
                            ) {
                                Text(
                                    text = "${stats.availableCount}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAvailableTab) VuInfoCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (!isAvailableTab) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (!isAvailableTab) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onToggleUpcomingOnly(true) }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏳ Upcoming",
                                fontWeight = if (!isAvailableTab) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (!isAvailableTab) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (!isAvailableTab) VuWarningAmber.copy(alpha = 0.15f) else Color.Transparent
                            ) {
                                Text(
                                    text = "${stats.upcomingCount}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isAvailableTab) VuWarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Stats Bar (Only when showing available tasks)
            if (!showUpcomingOnly) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatPill(
                            label = "All",
                            count = stats.allCount,
                            isSelected = selectedStatus == "All",
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { onStatusSelected("All") }
                        )
                        StatPill(
                            label = "Pending",
                            count = stats.totalPending,
                            isSelected = selectedStatus == "Pending",
                            color = VuInfoCyan,
                            onClick = { onStatusSelected("Pending") }
                        )
                        StatPill(
                            label = "Due Today",
                            count = stats.todayCount,
                            isSelected = selectedStatus == "Today",
                            color = VuDangerRed,
                            onClick = { onStatusSelected("Today") }
                        )
                        StatPill(
                            label = "Tomorrow",
                            count = stats.tomorrowCount,
                            isSelected = selectedStatus == "Tomorrow",
                            color = VuWarningAmber,
                            onClick = { onStatusSelected("Tomorrow") }
                        )
                        StatPill(
                            label = "This Week",
                            count = stats.thisWeekCount,
                            isSelected = selectedStatus == "This Week",
                            color = VuInfoCyan,
                            onClick = { onStatusSelected("This Week") }
                        )
                        StatPill(
                            label = "Completed",
                            count = stats.completedCount,
                            isSelected = selectedStatus == "Completed",
                            color = VuGreenPrimary,
                            onClick = { onStatusSelected("Completed") }
                        )
                    }
                }
            }

            // Full-Width Search Input (Course select dropdown removed per requirement 8)
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Search by course or title...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("search_input")
                )
            }

            // Activity Types Filter with Emojis & Item Counts
            item {
                val types = listOf(
                    "All" to "📋 All (${typeCounts["All"] ?: activities.size})",
                    "Assignment" to "📝 Assignments (${typeCounts["Assignment"] ?: 0})",
                    "Quiz" to "⚡ Quizzes (${typeCounts["Quiz"] ?: 0})",
                    "GDB" to "💬 GDBs (${typeCounts["GDB"] ?: 0})",
                    "Practical" to "🧪 Practicals (${typeCounts["Practical"] ?: 0})"
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(types) { (typeKey, label) ->
                        val isSelected = selectedActivityType == typeKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { onActivityTypeSelected(typeKey) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showUpcomingOnly) "Upcoming Activities (${activities.size})"
                               else "Available Activities (${activities.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // VU Quick Link shortcuts
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.vu.edu.pk/SupportSystem/")))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(14.dp), tint = VuGreenPrimary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Support", fontSize = 11.sp, color = VuGreenPrimary, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://gmail.vu.edu.pk/")))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Mail, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFD93025))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Mail", fontSize = 11.sp, color = Color(0xFFD93025), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Empty State
            if (activities.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (showUpcomingOnly) Icons.Default.LockClock else Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = VuInfoCyan,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (showUpcomingOnly) "No Upcoming Activities" else "No Available Activities",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (showUpcomingOnly) "All activities are already active or completed."
                                       else "Try changing the filter or tap Sync to check latest updates from VU LMS.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(activities, key = { it.id }) { act ->
                    ActivityCard(
                        activity = act,
                        onToggleCompletion = { done -> onToggleCompletion(act.id, done) },
                        onOpenLms = {
                            try {
                                val url = act.url.ifEmpty { "https://vulms.vu.edu.pk" }
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (ignored: Exception) {}
                        },
                        onClick = { selectedActivityForDetail = act }
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    count: Int,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) color else color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$count",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isSelected) Color.White else color
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
