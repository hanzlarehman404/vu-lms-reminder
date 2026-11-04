package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ActivityEntity
import com.example.data.model.ActivityType
import com.example.ui.components.ActivityDetailDialog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    activities: List<ActivityEntity>,
    onToggleCompletion: (activityId: String, completed: Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedActivityForDetail by remember { mutableStateOf<ActivityEntity?>(null) }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Active", "Upcoming", "Completed"
    var selectedMonth by remember { mutableStateOf("All Months") }

    val now = System.currentTimeMillis()
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

    val distinctMonths = remember(activities) {
        listOf("All Months") + activities
            .map { monthFormat.format(Date(it.endDate)) }
            .distinct()
    }

    val filteredList = remember(activities, selectedFilter, selectedMonth) {
        activities.filter { act ->
            val matchesTimeline = when (selectedFilter) {
                "Active" -> act.isAvailable(now) && !act.isCompleted
                "Upcoming" -> act.isUpcoming(now)
                "Completed" -> act.isCompleted
                else -> true
            }
            val matchesMonth = if (selectedMonth == "All Months") true
            else monthFormat.format(Date(act.endDate)) == selectedMonth ||
                 monthFormat.format(Date(act.startDate)) == selectedMonth

            matchesTimeline && matchesMonth
        }.sortedBy { it.startDate }
    }

    if (selectedActivityForDetail != null) {
        ActivityDetailDialog(
            activity = selectedActivityForDetail!!,
            onDismiss = { selectedActivityForDetail = null },
            onToggleCompletion = { done ->
                onToggleCompletion(selectedActivityForDetail!!.id, done)
                selectedActivityForDetail = selectedActivityForDetail!!.copy(isCompleted = done)
            },
            onOpenLms = {
                try {
                    val url = selectedActivityForDetail!!.url.ifEmpty { "https://vulms.vu.edu.pk" }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (ignored: Exception) {}
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
                            Text(
                                text = "Activity Timeline",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Official Semester Milestones & Exclusive Cutoffs",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            // Filter Pills: All, Active, Upcoming, Completed
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filters = listOf("All", "Active", "Upcoming", "Completed")
                    filters.forEach { f ->
                        FilterChip(
                            selected = selectedFilter == f,
                            onClick = { selectedFilter = f },
                            label = { Text(f, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Month Selector Pills
            if (distinctMonths.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        distinctMonths.forEach { m ->
                            SuggestionChip(
                                onClick = { selectedMonth = m },
                                label = { Text(m, fontSize = 11.sp, fontWeight = if (selectedMonth == m) FontWeight.Bold else FontWeight.Normal) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (selectedMonth == m) VuInfoCyan.copy(alpha = 0.15f) else Color.Transparent
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = if (selectedMonth == m) VuInfoCyan else MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }

            // Empty State
            if (filteredList.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = VuInfoCyan,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No Activities for Selected Timeline", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "Switch filter or sync with VU LMS to populate activities.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { act ->
                    TimelineBarCard(
                        activity = act,
                        onClick = { selectedActivityForDetail = act },
                        onOpenLms = {
                            try {
                                val url = act.url.ifEmpty { "https://vulms.vu.edu.pk" }
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (ignored: Exception) {}
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineBarCard(
    activity: ActivityEntity,
    onClick: () -> Unit,
    onOpenLms: () -> Unit
) {
    val type = ActivityType.fromString(activity.activityType, activity.title)
    val now = System.currentTimeMillis()
    val isUpcoming = activity.isUpcoming(now)
    val isAvailable = activity.isAvailable(now)
    val isPast = activity.endDate < now

    val totalDurationMillis = (activity.endDate - activity.startDate).coerceAtLeast(1)
    val elapsedMillis = (now - activity.startDate).coerceIn(0, totalDurationMillis)
    val progress = (elapsedMillis.toFloat() / totalDurationMillis.toFloat()).coerceIn(0f, 1f)

    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    val startStr = dateFormat.format(Date(activity.startDate))
    val endStr = dateFormat.format(Date(activity.endDate))
    val totalDays = (totalDurationMillis / (24 * 3600 * 1000L)).coerceAtLeast(1)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, type.color.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("timeline_card_${activity.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Course Code + Title + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = type.color.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, type.color.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${type.emoji} ${activity.courseCode}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = type.color,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "$totalDays Days Duration",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        activity.isCompleted -> VuGreenPrimary.copy(alpha = 0.12f)
                        isUpcoming -> VuInfoCyan.copy(alpha = 0.12f)
                        isPast -> VuDangerRed.copy(alpha = 0.12f)
                        else -> VuWarningAmber.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = when {
                            activity.isCompleted -> "✅ Done"
                            isUpcoming -> "⏳ In ${activity.daysUntilStart(now)}d"
                            isPast -> "⚠️ Expired"
                            else -> "⚡ Active"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            activity.isCompleted -> VuGreenPrimary
                            isUpcoming -> VuInfoCyan
                            isPast -> VuDangerRed
                            else -> Color(0xFFB45309)
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = activity.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // VU LMS Timeline Bar Line representation
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Opens: $startStr",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Deadline: $endStr",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAvailable) VuDangerRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // The visual timeline bar line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (activity.isCompleted) 1f else if (isUpcoming) 0.05f else progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (activity.isCompleted) VuGreenPrimary
                                else type.color
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onOpenLms,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open in LMS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
