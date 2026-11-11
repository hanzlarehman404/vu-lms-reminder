package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityEntity
import com.example.data.model.ActivityType
import com.example.ui.theme.VuDangerRed
import com.example.ui.theme.VuGreenPrimary
import com.example.ui.theme.VuWarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityCard(
    activity: ActivityEntity,
    onToggleCompletion: (Boolean) -> Unit,
    onOpenLms: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    val type = ActivityType.fromString(activity.activityType, activity.title)
    val now = System.currentTimeMillis()
    val isUpcoming = activity.isUpcoming(now)
    val isPast = activity.endDate < now
    val remainingMillis = (activity.endDate - now).coerceAtLeast(0)
    val remainingHours = remainingMillis / (3600 * 1000)
    val remainingDays = remainingMillis / (24 * 3600 * 1000)

    val relativeText = when {
        activity.isCompleted -> "✅ Done"
        isUpcoming -> "⏳ Starts in ${activity.daysUntilStart(now)}d"
        isPast -> "⚠️ Expired"
        remainingHours <= 1 -> "🚨 < 1 hr left!"
        remainingHours < 24 -> "⚡ $remainingHours hrs left"
        remainingDays == 1L -> "📅 Due tomorrow"
        else -> "$remainingDays days left"
    }

    val badgeColor = when {
        activity.isCompleted -> VuGreenPrimary
        isUpcoming -> MaterialTheme.colorScheme.primary
        isPast -> VuDangerRed
        remainingHours < 12 -> VuDangerRed
        remainingHours < 48 -> VuWarningAmber
        else -> MaterialTheme.colorScheme.primary
    }

    val dateCalendar = java.util.Calendar.getInstance().apply { timeInMillis = activity.endDate }
    val isEndOfDay = dateCalendar.get(java.util.Calendar.HOUR_OF_DAY) == 23 && dateCalendar.get(java.util.Calendar.MINUTE) >= 59
    val dateStr = if (isEndOfDay) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(activity.endDate))
    } else {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(activity.endDate))
    }
    val startDateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(activity.startDate))

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (activity.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(if (activity.isCompleted) 1.dp else 2.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (activity.isCompleted) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            else type.color.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (activity.isCompleted) 0.7f else 1f)
            .testTag("activity_card_${activity.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Type Badge with Emoji
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = type.color.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, type.color.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${type.emoji} ${type.displayName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = type.color,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    // Course Code Chip
                    if (activity.courseCode.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = activity.courseCode,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (isUpcoming) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.LockClock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Opens $startDateStr", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Auto status indicator badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = relativeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = activity.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (activity.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUpcoming) "Available: $startDateStr - $dateStr" else "Due: $dateStr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (activity.isCompleted) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = VuGreenPrimary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "✓ Submitted",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VuGreenPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
