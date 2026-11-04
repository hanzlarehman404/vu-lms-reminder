package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityEntity
import com.example.data.model.ActivityType
import com.example.ui.theme.VuInfoCyan
import com.example.ui.theme.VuSidebarDark

@Composable
fun CountdownTimerCard(
    activity: ActivityEntity?,
    onOpenLms: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (activity == null) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = modifier
                .fillMaxWidth()
                .testTag("empty_countdown_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassBottom,
                    contentDescription = null,
                    tint = VuInfoCyan,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "🎉 All Caught Up!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "No pending activities are due right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val remainingMillis = (activity.endDate - currentTime).coerceAtLeast(0)
    val days = remainingMillis / (24 * 3600 * 1000)
    val hours = (remainingMillis % (24 * 3600 * 1000)) / (3600 * 1000)
    val minutes = (remainingMillis % (3600 * 1000)) / (60 * 1000)
    val seconds = (remainingMillis % (60 * 1000)) / 1000

    val isUrgent = days == 0L && hours < 6
    val isCritical = days == 0L && hours < 2

    val gradientColors = when {
        isCritical -> listOf(Color(0xFF8B0000), Color(0xFF4A0000))
        isUrgent -> listOf(Color(0xFFB45309), Color(0xFF78350F))
        else -> listOf(VuSidebarDark, Color(0xFF151521))
    }

    val type = ActivityType.fromString(activity.activityType, activity.title)

    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("countdown_hero_card")
    ) {
        Box(
            modifier = Modifier
                .background(Brush.horizontalGradient(gradientColors))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = if (isCritical) "🚨 CRITICAL DEADLINE" else if (isUrgent) "⚡ DUE TODAY" else "⏳ NEAREST DEADLINE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = type.color
                    ) {
                        Text(
                            text = "${type.emoji} ${activity.courseCode}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = activity.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TimeBox(value = "%02d".format(days), label = "DAYS")
                    TimeBox(value = "%02d".format(hours), label = "HOURS")
                    TimeBox(value = "%02d".format(minutes), label = "MINS")
                    TimeBox(value = "%02d".format(seconds), label = "SECS")
                }

                Spacer(modifier = Modifier.height(14.dp))

                FilledTonalButton(
                    onClick = { onOpenLms("https://vulms.vu.edu.pk/") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("countdown_open_lms_button"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = VuInfoCyan,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open LMS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeBox(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = 1.sp
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
