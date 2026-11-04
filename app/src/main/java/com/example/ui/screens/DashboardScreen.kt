package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import com.example.ui.components.ActivityCard
import com.example.ui.components.ActivityDetailDialog
import com.example.ui.components.CountdownTimerCard
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
    selectedCategory: String?,
    showUpcomingOnly: Boolean,
    searchQuery: String,
    studentId: String,
    lastSyncTime: Long,
    isSyncing: Boolean,
    syncMessage: String?,
    onCategorySelected: (String?) -> Unit,
    onToggleUpcomingOnly: (Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onToggleCompletion: (activityId: String, completed: Boolean) -> Unit,
    onMarkAllAvailableDone: () -> Unit,
    onSyncNow: () -> Unit,
    onClearSyncMessage: () -> Unit
) {
    val context = LocalContext.current
    var selectedActivityForDetail by remember { mutableStateOf<ActivityEntity?>(null) }
    var showMarkAllConfirmDialog by remember { mutableStateOf(false) }
    var showResyncWarningDialog by remember { mutableStateOf(false) }

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

    if (showMarkAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showMarkAllConfirmDialog = false },
            title = {
                Text("Confirm Mark All Done", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Please confirm on your official VU LMS portal before marking all available activities as done. This will mark all currently active tasks completed and silence alarms.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMarkAllConfirmDialog = false
                        onMarkAllAvailableDone()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VuGreenPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm Done", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkAllConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedActivityForDetail != null) {
        ActivityDetailDialog(
            activity = selectedActivityForDetail!!,
            onDismiss = { selectedActivityForDetail = null },
            onToggleCompletion = { done ->
                onToggleCompletion(selectedActivityForDetail!!.id, done)
                selectedActivityForDetail = selectedActivityForDetail!!.copy(isCompleted = done)
            }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sync status message banner
            if (syncMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = VuSuccessMint.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VuSuccessMint.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
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
                                    text = syncMessage,
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
            }

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
                                text = "⏳ Upcoming (Opens Later)",
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
                            isSelected = selectedCategory == "All",
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { onCategorySelected("All") }
                        )
                        StatPill(
                            label = "Pending",
                            count = stats.totalPending,
                            isSelected = selectedCategory == null || selectedCategory == "Pending",
                            color = VuInfoCyan,
                            onClick = { onCategorySelected("Pending") }
                        )
                        StatPill(
                            label = "Due Today",
                            count = stats.todayCount,
                            isSelected = selectedCategory == "Today",
                            color = VuDangerRed,
                            onClick = { onCategorySelected("Today") }
                        )
                        StatPill(
                            label = "Tomorrow",
                            count = stats.tomorrowCount,
                            isSelected = selectedCategory == "Tomorrow",
                            color = VuWarningAmber,
                            onClick = { onCategorySelected("Tomorrow") }
                        )
                        StatPill(
                            label = "This Week",
                            count = stats.thisWeekCount,
                            isSelected = selectedCategory == "This Week",
                            color = VuInfoCyan,
                            onClick = { onCategorySelected("This Week") }
                        )
                        StatPill(
                            label = "Completed",
                            count = stats.completedCount,
                            isSelected = selectedCategory == "Completed",
                            color = VuGreenPrimary,
                            onClick = { onCategorySelected("Completed") }
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

            // Categories Filter with Emojis (Pending and All duplicate chips removed per user request)
            item {
                val categories = listOf(
                    "Assignment" to "📝 Assignments",
                    "Quiz" to "⚡ Quizzes",
                    "GDB" to "💬 GDBs",
                    "Practical" to "🧪 Practicals",
                    "Challan" to "💳 Challans"
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories) { (catKey, label) ->
                        val isSelected = selectedCategory == catKey
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) onCategorySelected(null)
                                else onCategorySelected(catKey)
                            },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Header Row with "Mark All Available as Done" button
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

                    if (!showUpcomingOnly && activities.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { showMarkAllConfirmDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = VuGreenPrimary.copy(alpha = 0.12f),
                                contentColor = VuGreenPrimary
                            ),
                            modifier = Modifier.testTag("mark_all_done_button")
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mark All Done", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
