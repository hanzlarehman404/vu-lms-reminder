package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ActivityEntity
import com.example.data.model.ActivityType
import com.example.ui.theme.VuGreen
import com.example.ui.theme.VuNavy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityDetailDialog(
    activity: ActivityEntity,
    onDismiss: () -> Unit,
    onToggleCompletion: (Boolean) -> Unit
) {
    val type = ActivityType.fromString(activity.activityType, activity.title)
    
    val startStr = try {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(activity.startDate))
    } catch (e: Exception) {
        "N/A"
    }

    val endStr = try {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = activity.endDate }
        val isEndOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY) == 23 && cal.get(java.util.Calendar.MINUTE) >= 59
        val baseDate = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date(activity.endDate))
        if (isEndOfDay) {
            "$baseDate (Final Due Day)"
        } else {
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(activity.endDate))
            "$baseDate at $time"
        }
    } catch (e: Exception) {
        "N/A"
    }

    val now = System.currentTimeMillis()
    val isUpcoming = activity.isUpcoming(now)
    val remainingMillis = (activity.endDate - now).coerceAtLeast(0)
    val days = remainingMillis / (24 * 3600 * 1000)
    val hours = (remainingMillis % (24 * 3600 * 1000)) / (3600 * 1000)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
                .testTag("activity_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = type.color.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, type.color.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${activity.courseCode} • ${type.emoji} ${type.displayName}",
                            color = type.color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 26.sp
                )

                if (activity.semester.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Semester: ${activity.semester}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(modifier = Modifier.height(16.dp))

                DetailItem(
                    icon = Icons.Default.DateRange,
                    title = "Opening Date",
                    value = startStr
                )

                Spacer(modifier = Modifier.height(12.dp))

                DetailItem(
                    icon = Icons.Default.HourglassBottom,
                    title = "Submission Deadline",
                    value = endStr,
                    isHighlight = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                DetailItem(
                    icon = Icons.Default.School,
                    title = "Time Remaining",
                    value = if (activity.isCompleted) {
                        "Completed (Alarms muted)"
                    } else if (isUpcoming) {
                        "Opens in ${activity.daysUntilStart(now)} days"
                    } else if (activity.endDate < now) {
                        "Expired / Overdue"
                    } else {
                        "$days days $hours hours remaining"
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Toggle Completion (Only for available activities)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (isUpcoming) Icons.Default.LockClock else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (activity.isCompleted) VuGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isUpcoming) "Status: Upcoming (Opens $startStr)"
                                           else if (activity.isCompleted) "Status: Completed"
                                           else "Status: Pending",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isUpcoming) "Cannot be marked done before opening date"
                                           else if (activity.isCompleted) "Alarms muted for this task"
                                           else "Alarms active before deadline",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isUpcoming) {
                            Switch(
                                checked = activity.isCompleted,
                                onCheckedChange = onToggleCompletion,
                                colors = SwitchDefaults.colors(checkedThumbColor = VuGreen)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Dismiss / Close Button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("detail_close_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun DetailItem(
    icon: ImageVector,
    title: String,
    value: String,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
                color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
