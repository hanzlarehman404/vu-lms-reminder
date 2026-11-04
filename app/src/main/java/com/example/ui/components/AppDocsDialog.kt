package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*

@Composable
fun AppDocsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = VuInfoCyan.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, VuInfoCyan.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "📖 DOCS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = VuInfoCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "App Documentation & Details",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Section 1: Introduction
                    DocSectionCard(
                        icon = Icons.Default.School,
                        title = "1. Introduction & Overview",
                        iconColor = VuNavy
                    ) {
                        Text(
                            text = "VU LMS Reminder is an open-source, student-focused Android companion for Virtual University of Pakistan (VU). It connects directly to the student portal to scrape and monitor assignments, quizzes, graded discussion boards (GDBs), and fee challans, ensuring you never miss an academic deadline.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Section 2: Smart Due Date Calculation & VU LMS Grace Period
                    DocSectionCard(
                        icon = Icons.Default.EventBusy,
                        title = "2. Smart Due Date Calculation & LMS Quirk",
                        iconColor = VuWarningAmber
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "• In VU LMS calendar backend data, end dates are transmitted exclusively (for example: \"end\": \"2026,11,06\" specifies the exclusive cutoff boundary).\n" +
                                        "• The actual final submission day is 2026-11-05 (closing at end of day). On the 6th, the regular portal deadline has passed.\n" +
                                        "• VU LMS Reminder automatically adjusts this exclusive date by 1 day to show you the genuine, safe deadline so you submit well before cutoff.",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Prominent Callout regarding LMS quirk
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = VuWarningAmber.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, VuWarningAmber.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = VuWarningAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Important Note: VU LMS Grace Submissions",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Due to a known quirk / minor bug in the VU LMS portal system, some teachers or system scripts occasionally accept submissions even after the posted due date has elapsed. Always check your official VU LMS portal even after the due date, as you might still have a chance to submit your activity!",
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: Notification Schema
                    DocSectionCard(
                        icon = Icons.Default.NotificationsActive,
                        title = "3. Three-Tier Notification Schema",
                        iconColor = VuGreenPrimary
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoutineItem(
                                title = "☀️ Morning Briefing (e.g., 09:00 AM)",
                                description = "Synchronizes latest LMS submissions, auto-marks finished items as Done, and notifies of all tasks due today alongside an optional preview of tomorrow's deadlines."
                            )
                            RoutineItem(
                                title = "🌤️ Mid-Day Check (e.g., 02:00 PM)",
                                description = "Midday accountability check: \"Have you done all of your available activities?\" Keeps you on track throughout the afternoon with quick LMS action buttons."
                            )
                            RoutineItem(
                                title = "🌙 Evening Last Reminder (e.g., 09:00 PM)",
                                description = "Critical sync & urgent notification for remaining tasks due before end of day so you never lose academic marks."
                            )
                        }
                    }

                    // Section 4: Full-Screen Alarm vs Regular Notification & Tone
                    DocSectionCard(
                        icon = Icons.Default.Alarm,
                        title = "4. Full-Screen Alarm vs Regular Notifications",
                        iconColor = VuDangerRed
                    ) {
                        Text(
                            text = "• Full-Screen Alarm ON: When an activity is due today (< 2 hours remaining), the app wakes the device screen with a dedicated full-screen alarm overlay featuring Snooze (15m/30m), Mark as Done, and Direct LMS Launch.\n\n" +
                                    "• Full-Screen Alarm OFF: If disabled, the app instead posts a standard high-priority heads-up notification in your Android notification shade with action buttons—letting you manage deadlines without interrupting what you're currently doing on your phone.\n\n" +
                                    "• Alarm Audio Ringtone & Preview: Controls whether audible sound plays when reminders trigger. You can customize the tone (Alarm, Notification Sound, Gentle Chime, or System Default) and tap to hear an instant preview. If sound is switched off, reminders vibrate silently.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Section 5: Security & Signature Verification
                    DocSectionCard(
                        icon = Icons.Default.Security,
                        title = "5. Developer Authenticity Verification",
                        iconColor = VuInfoCyan
                    ) {
                        Text(
                            text = "• To protect students from modified or tampered third-party builds, tapping \"Verify & Sign In\" verifies the installed APK's cryptographic signing certificate against the official update.json repository.\n" +
                                    "• Official Certificate SHA-256:\n" +
                                    "  27:AD:82:EF:6C:AA:E3:B7:0D:2C:A3:D9:F8:45:E2:9B:AA:04:C0:A4:5D:2A:EE:F7:AC:ED:64:24:6B:0F:99:74\n" +
                                    "• If signatures mismatch, the app warns the user and links directly to the official GitHub release to prevent credential harvesting.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Section 6: Network & Privacy Architecture
                    DocSectionCard(
                        icon = Icons.Default.Lock,
                        title = "6. Zero-Data-Collection & Direct Network",
                        iconColor = VuNavy
                    ) {
                        Text(
                            text = "• Zero intermediary servers: App communicates exclusively with https://vulms.vu.edu.pk and official GitHub releases for updates.\n" +
                                    "• Built with a custom OkHttp SSL Socket Factory to resolve Android certificate anchor issues with the official VU server.\n" +
                                    "• Credentials and data are stored strictly on-device in private SharedPreferences and encrypted Room database.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hanzlarehman404/vu-lms-reminder")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (ignored: Exception) {}
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Source Code", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VuInfoCyan)
                    ) {
                        Text("Got It", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DocSectionCard(
    icon: ImageVector,
    title: String,
    iconColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(iconColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun RoutineItem(title: String, description: String) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(description, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
