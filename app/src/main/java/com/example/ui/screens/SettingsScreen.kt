package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.PreferencesManager
import com.example.ui.components.PrivacyDisclaimerDialog
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    studentId: String,
    studentAvatarFile: java.io.File? = null,
    onLogout: () -> Unit,
    onResetCompletions: () -> Unit,
    onCheckUpdate: () -> Unit,
    onSetTheme: (String) -> Unit
) {
    val context = LocalContext.current
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    var notificationsEnabled by remember { mutableStateOf(prefs.isNotificationsEnabled()) }
    var urgentAlarmEnabled by remember { mutableStateOf(prefs.isUrgentAlarmEnabled()) }
    var alarmSoundEnabled by remember { mutableStateOf(prefs.isAlarmSoundEnabled()) }
    var vibrationEnabled by remember { mutableStateOf(prefs.isVibrationEnabled()) }
    var nextDayRoutineEnabled by remember { mutableStateOf(prefs.isNextDayRoutineEnabled()) }

    var morningTime by remember { mutableStateOf(prefs.getMorningSyncTime()) }
    var eveningTime by remember { mutableStateOf(prefs.getEveningReminderTime()) }
    var selectedTheme by remember { mutableStateOf(prefs.getAppTheme()) }

    fun saveNotifSettings() {
        prefs.setNotificationSettings(
            enabled = notificationsEnabled,
            urgentAlarm = urgentAlarmEnabled,
            sound = alarmSoundEnabled,
            vibrate = vibrationEnabled
        )
    }

    if (showPrivacyDialog) {
        PrivacyDisclaimerDialog(onAccept = { showPrivacyDialog = false })
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset All Done Statuses", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to clear all manual and auto completion marks? Reminders will be rescheduled for all available activities.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetCompletions()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VuDangerRed)
                ) {
                    Text("Reset All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Routine",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Student Account Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (studentAvatarFile != null && studentAvatarFile.exists() && studentAvatarFile.length() > 0) {
                                AsyncImage(
                                    model = studentAvatarFile,
                                    contentDescription = "Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, VuGreenPrimary, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(VuInfoCyan.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = VuInfoCyan)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = studentId.ifEmpty { "Active Student" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Connected to vulms.vu.edu.pk",
                                    fontSize = 11.sp,
                                    color = VuGreenPrimary
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onLogout,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VuDangerRed),
                            modifier = Modifier.testTag("logout_button")
                        ) {
                            Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Routine & Sync Schedule (Morning Wake Up & Evening Last Reminder)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = VuInfoCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Daily Routine Sync & Alarms",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "⚠️ Notice: At these two times, please do not work on VU LMS in your web browser, as you will be logged out of your active browser session exactly at these two sync times.",
                            fontSize = 12.sp,
                            color = VuWarningAmber,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Morning Routine Time
                        Text("1. Morning Wake-Up Sync Time:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("App syncs with LMS, auto-marks completed tasks, and notifies available activities.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("08:00", "09:00", "10:00").forEach { t ->
                                FilterChip(
                                    selected = morningTime == t,
                                    onClick = {
                                        morningTime = t
                                        prefs.setRoutineTimes(morningTime, eveningTime)
                                    },
                                    label = { Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Evening Last Reminder Time
                        Text("2. Evening Last Reminder Time:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("App resyncs and rings full-screen alarms for remaining activities due today.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("20:00", "21:00", "22:00").forEach { t ->
                                FilterChip(
                                    selected = eveningTime == t,
                                    onClick = {
                                        eveningTime = t
                                        prefs.setRoutineTimes(morningTime, eveningTime)
                                    },
                                    label = { Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Next Day Routine Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Next-Day Routine Preview", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Include tomorrow's due activities in morning briefing", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = nextDayRoutineEnabled,
                                onCheckedChange = {
                                    nextDayRoutineEnabled = it
                                    prefs.setNextDayRoutineEnabled(it)
                                }
                            )
                        }
                    }
                }
            }

            // Notification & Alarm Settings Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Alerts & Notifications",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable Notifications", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = {
                                    notificationsEnabled = it
                                    saveNotifSettings()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Full-Screen Alarm on Due Date", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = urgentAlarmEnabled,
                                onCheckedChange = {
                                    urgentAlarmEnabled = it
                                    saveNotifSettings()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Alarm Audio Ringtone", fontSize = 13.sp)
                            Switch(
                                checked = alarmSoundEnabled,
                                onCheckedChange = {
                                    alarmSoundEnabled = it
                                    saveNotifSettings()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Vibrate on Alert", fontSize = 13.sp)
                            Switch(
                                checked = vibrationEnabled,
                                onCheckedChange = {
                                    vibrationEnabled = it
                                    saveNotifSettings()
                                }
                            )
                        }
                    }
                }
            }

            // Theme & Appearance Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Theme & Appearance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("system" to "System", "light" to "VU Light", "dark" to "VU Dark").forEach { (mode, label) ->
                                FilterChip(
                                    selected = selectedTheme == mode,
                                    onClick = {
                                        selectedTheme = mode
                                        onSetTheme(mode)
                                    },
                                    label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Task Actions & Version Check Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showResetConfirmDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Manual Done Statuses", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onCheckUpdate,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check for Updates (v1.0.0)", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showPrivacyDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Privacy & Open Source Transparency", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Contact, Developer & Donate Section (Item 10 requirement)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VuInfoCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AsyncImage(
                                model = "https://avatars.githubusercontent.com/u/115634418?v=4",
                                contentDescription = "Hanzla Rehman",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, VuInfoCyan, CircleShape)
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = "Hanzla Rehman",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Developer",
                                    fontSize = 12.sp,
                                    color = VuInfoCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "github.com/hanzlarehman404",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "If you find any issues and want to improve something or fix it, kindly contribute to the repository of this project. Support and contributions are warmly welcome!",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hanzlarehman404"))
                                        context.startActivity(intent)
                                    } catch (ignored: Exception) {}
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VuInfoCyan),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("contact_donate_button")
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Contact / Donate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hanzlarehman404/vu-lms-reminder"))
                                        context.startActivity(intent)
                                    } catch (ignored: Exception) {}
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("contribute_repo_button")
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Contribute Repo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
