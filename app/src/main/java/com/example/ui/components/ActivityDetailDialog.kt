package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    onToggleCompletion: (Boolean) -> Unit,
    onOpenLms: () -> Unit
) {
    val type = ActivityType.fromString(activity.activityType, activity.title)
    val sdf = SimpleDateFormat("EEEE, dd MMM yyyy 'at' hh:mm a", Locale.getDefault())
    val startStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(activity.startDate))
    val endStr = sdf.format(Date(activity.endDate))

    val now = System.currentTimeMillis()
    val remainingMillis = (activity.endDate - now).coerceAtLeast(0)
    val days = remainingMillis / (24 * 3600 * 1000)
    val hours = (remainingMillis % (24 * 3600 * 1000)) / (3600 * 1000)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("activity_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
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

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(modifier = Modifier.height(16.dp))

                DetailItem(
                    icon = Icons.Default.CalendarToday,
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
                        "Completed (Alarms cancelled)"
                    } else if (activity.endDate < now) {
                        "Expired / Overdue"
                    } else {
                        "$days days $hours hours remaining"
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                DetailItem(
                    icon = Icons.Default.Link,
                    title = "LMS Direct URL",
                    value = activity.url.ifEmpty { "https://vulms.vu.edu.pk" }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Toggle Completion
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (activity.isCompleted) VuGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (activity.isCompleted) "Status: Completed" else "Status: Pending",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (activity.isCompleted) "Alarms muted for this task" else "Alarms active before deadline",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = activity.isCompleted,
                            onCheckedChange = onToggleCompletion,
                            colors = SwitchDefaults.colors(checkedThumbColor = VuGreen)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Button: Open in LMS
                Button(
                    onClick = onOpenLms,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("detail_open_lms_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = VuNavy),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open in VU LMS", fontWeight = FontWeight.Bold)
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
