package com.example.ui.screens

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.IntentCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.PreferencesManager
import com.example.receiver.NotificationHelper
import com.example.ui.components.AppDocsDialog
import com.example.ui.components.ManualNotificationsUiState
import com.example.ui.components.ManualUpdateStatusDialog
import com.example.ui.components.PrivacyDisclaimerDialog
import com.example.ui.components.UpcomingNoticesDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.ManualUpdateResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    statusLabel: String,
    isGranted: Boolean,
    testTagValue: String,
    onClick: () -> Unit
) {
    val badgeBg = if (isGranted) VuGreenPrimary.copy(alpha = 0.14f) else iconTint.copy(alpha = 0.14f)
    val badgeText = if (isGranted) VuGreenPrimary else iconTint
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isGranted) VuGreenPrimary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTagValue)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(badgeBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    tint = badgeText,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeBg
            ) {
                Text(
                    text = statusLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    studentId: String,
    studentAvatarFile: java.io.File? = null,
    isCheckingUpdate: Boolean = false,
    manualUpdateResult: ManualUpdateResult? = null,
    isCheckingNotifications: Boolean = false,
    manualNotificationsState: ManualNotificationsUiState? = null,
    onLogout: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDismissManualUpdateResult: () -> Unit = {},
    onCheckUpcomingNotifications: () -> Unit = {},
    onDismissManualNotifications: () -> Unit = {},
    onSetTheme: (String) -> Unit
) {
    val context = LocalContext.current
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDocsDialog by remember { mutableStateOf(false) }

    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    var notificationsEnabled by remember { mutableStateOf(prefs.isNotificationsEnabled()) }
    var urgentAlarmEnabled by remember { mutableStateOf(prefs.isUrgentAlarmEnabled()) }
    var alarmSoundEnabled by remember { mutableStateOf(prefs.isAlarmSoundEnabled()) }
    var vibrationEnabled by remember { mutableStateOf(prefs.isVibrationEnabled()) }
    var nextDayRoutineEnabled by remember { mutableStateOf(prefs.isNextDayRoutineEnabled()) }
    var middayRoutineEnabled by remember { mutableStateOf(prefs.isMiddayRoutineEnabled()) }

    var morningTime by remember { mutableStateOf(prefs.getMorningSyncTime()) }
    var middayTime by remember { mutableStateOf(prefs.getMiddayTime()) }
    var eveningTime by remember { mutableStateOf(prefs.getEveningReminderTime()) }
    var notificationToneUri by remember { mutableStateOf(prefs.getNotificationToneUri()) }
    var notificationToneName by remember { mutableStateOf(prefs.getNotificationToneName()) }
    var autoSyncOnLaunch by remember { mutableStateOf(prefs.isAutoSyncOnLaunch()) }
    var selectedTheme by remember { mutableStateOf(prefs.getAppTheme()) }

    var activeRingtone by remember { mutableStateOf<android.media.Ringtone?>(null) }
    var isPlayingPreview by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun triggerVibration() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                @Suppress("DEPRECATION")
                vib?.vibrate(200)
            }
        } catch (ignored: Exception) {}
    }

    fun stopAudioPreview() {
        try {
            previewJob?.cancel()
            previewJob = null
            activeRingtone?.stop()
            activeRingtone = null
            isPlayingPreview = false
        } catch (ignored: Exception) {}
    }

    fun playTonePreview(toneUriStr: String) {
        try {
            stopAudioPreview()
            val targetUri = if (toneUriStr.isNotBlank()) {
                runCatching { Uri.parse(toneUriStr) }.getOrNull()
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            } ?: return

            val rt = RingtoneManager.getRingtone(context, targetUri)
            if (rt != null) {
                activeRingtone = rt
                isPlayingPreview = true
                rt.play()
                previewJob = coroutineScope.launch {
                    delay(2800)
                    try {
                        rt.stop()
                    } catch (_: Exception) {
                    }
                    activeRingtone = null
                    isPlayingPreview = false
                }
            }
        } catch (_: Exception) {
            isPlayingPreview = false
        }
    }

    fun applySelectedNotificationTone(uriStr: String, displayName: String, playPreview: Boolean = true) {
        notificationToneUri = uriStr
        notificationToneName = displayName
        if (uriStr.isBlank()) {
            prefs.resetNotificationTone()
        } else {
            prefs.setNotificationTone(uriStr, displayName)
        }
        NotificationHelper(context).syncNotificationChannels()
        if (playPreview && alarmSoundEnabled) {
            playTonePreview(uriStr)
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val pickedUri = if (data != null) {
                IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else null

            if (pickedUri == null) {
                applySelectedNotificationTone(
                    uriStr = "",
                    displayName = PreferencesManager.DEFAULT_NOTIFICATION_TONE_NAME
                )
            } else {
                val resolvedTitle = runCatching {
                    RingtoneManager.getRingtone(context, pickedUri)?.getTitle(context)?.trim()
                }.getOrNull().orEmpty().ifBlank { "Custom Notification Sound" }
                applySelectedNotificationTone(
                    uriStr = pickedUri.toString(),
                    displayName = resolvedTitle
                )
            }
        }
    }

    fun openAndroidRingtonePicker() {
        val currentUri = if (notificationToneUri.isNotBlank()) {
            runCatching { Uri.parse(notificationToneUri) }.getOrNull()
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose Notification Tone")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )
            if (currentUri != null) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            }
        }
        if (pickerIntent.resolveActivity(context.packageManager) != null) {
            try {
                stopAudioPreview()
                ringtonePickerLauncher.launch(pickerIntent)
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(context, "System ringtone picker is unavailable on this device.", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAudioPreview()
        }
    }

    fun saveNotifSettings() {
        prefs.setNotificationSettings(
            enabled = notificationsEnabled,
            urgentAlarm = urgentAlarmEnabled,
            sound = alarmSoundEnabled,
            vibrate = vibrationEnabled
        )
        NotificationHelper(context).syncNotificationChannels()
    }

    if (showPrivacyDialog) {
        PrivacyDisclaimerDialog(onAccept = { showPrivacyDialog = false })
    }

    if (showDocsDialog) {
        AppDocsDialog(onDismiss = { showDocsDialog = false })
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = VuDangerRed, modifier = Modifier.size(36.dp))
            },
            title = { Text("Confirm Sign Out", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to sign out? Your saved offline activities will remain on your device, but automatic LMS sync and reminders will be paused until you sign back in.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VuDangerRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }


    manualUpdateResult?.let { result ->
        ManualUpdateStatusDialog(
            result = result,
            onRetry = onCheckUpdate,
            onDismiss = onDismissManualUpdateResult
        )
    }

    manualNotificationsState?.let { notifState ->
        UpcomingNoticesDialog(
            state = notifState,
            onRetry = onCheckUpcomingNotifications,
            onDismiss = onDismissManualNotifications
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
                                text = "Settings & Routine",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Sync schedules, alerts & transparency",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showLogoutConfirmDialog = true },
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign Out",
                            tint = VuDangerRed
                        )
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // Auto-Sync on App Launch Card (directly above Daily Routine Sync & Alarms)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = VuInfoCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "App Launch Synchronization",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Auto-Sync on App Launch", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (autoSyncOnLaunch) "Fresh LMS sync runs automatically every time the app opens." else "Manual mode: tap refresh on the Dashboard to sync.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoSyncOnLaunch,
                                onCheckedChange = {
                                    autoSyncOnLaunch = it
                                    prefs.setAutoSyncOnLaunch(it)
                                },
                                modifier = Modifier.testTag("auto_sync_launch_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "⚠️ Opening the app with Auto-Sync on will log you out of any active VU LMS browser session.",
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            color = VuWarningAmber,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("auto_sync_warning_text")
                        )
                    }
                }
            }

            // Routine & Sync Schedule (Morning Wake Up, Mid-Day Check & Evening Last Reminder)
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

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Pick routine times outside active browser quizzes or exams so automatic syncs never interrupt your work.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        prefs.setRoutineTimes(morningTime, middayTime, eveningTime)
                                    },
                                    label = { Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Mid-Day Check Routine Time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("2. Mid-Day Activity Check:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Reminds: 'Have you done all of your available activities?'", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = middayRoutineEnabled,
                                onCheckedChange = {
                                    middayRoutineEnabled = it
                                    prefs.setMiddayRoutineEnabled(it)
                                }
                            )
                        }
                        if (middayRoutineEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("13:00", "14:00", "15:00").forEach { t ->
                                    FilterChip(
                                        selected = middayTime == t,
                                        onClick = {
                                            middayTime = t
                                            prefs.setRoutineTimes(morningTime, middayTime, eveningTime)
                                        },
                                        label = { Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Evening Last Reminder Time
                        Text("3. Evening Last Reminder Time:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("App resyncs and rings urgent reminders for remaining activities due today.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("20:00", "21:00", "22:00").forEach { t ->
                                FilterChip(
                                    selected = eveningTime == t,
                                    onClick = {
                                        eveningTime = t
                                        prefs.setRoutineTimes(morningTime, middayTime, eveningTime)
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

            // Alerts & Notifications Card
            item {
                val isMuted = !alarmSoundEnabled
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = VuWarningAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Alerts & Notifications",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Enable Notifications", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (notificationsEnabled) "Routine briefings, deadlines, and urgent reminders active." else "All scheduled notifications and alarms paused.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = {
                                    notificationsEnabled = it
                                    saveNotifSettings()
                                    if (!it) {
                                        stopAudioPreview()
                                    }
                                },
                                modifier = Modifier.testTag("enable_notifications_switch")
                            )
                        }

                        if (!notificationsEnabled) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Turn on notifications above to customize evening alarms, ringtones, and vibration.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        } else {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Full-Screen Evening Alarm", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (urgentAlarmEnabled) "Wakes screen with full alarm overlay for urgent deadlines." else "Posts a standard heads-up notification instead.",
                                        fontSize = 11.sp,
                                        color = if (urgentAlarmEnabled) VuSuccessMint else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = urgentAlarmEnabled,
                                    onCheckedChange = {
                                        urgentAlarmEnabled = it
                                        saveNotifSettings()
                                    },
                                    modifier = Modifier.testTag("urgent_alarm_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Mute Notifications & Alarms", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (isMuted) "Muted: alerts deliver silently (vibration only if enabled)." else "Sound on: plays selected tone for notifications and alarm tone for urgent overlay.",
                                        fontSize = 11.sp,
                                        color = if (isMuted) VuWarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isMuted,
                                    onCheckedChange = { muted ->
                                        alarmSoundEnabled = !muted
                                        saveNotifSettings()
                                        if (muted) {
                                            stopAudioPreview()
                                        }
                                    },
                                    modifier = Modifier.testTag("mute_notifications_switch")
                                )
                            }

                            if (!isMuted) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { openAndroidRingtonePicker() }
                                        .testTag("pick_notification_tone_button")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(VuInfoCyan.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = VuInfoCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Notification Ringtone",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = notificationToneName,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (isPlayingPreview) {
                                                    stopAudioPreview()
                                                } else {
                                                    playTonePreview(notificationToneUri)
                                                }
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("preview_notification_tone_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingPreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlayingPreview) "Stop ringtone preview" else "Preview notification ringtone",
                                                tint = if (isPlayingPreview) VuDangerRed else VuInfoCyan
                                            )
                                        }
                                        if (notificationToneUri.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    applySelectedNotificationTone(
                                                        uriStr = "",
                                                        displayName = PreferencesManager.DEFAULT_NOTIFICATION_TONE_NAME
                                                    )
                                                },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("reset_notification_tone_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.RestartAlt,
                                                    contentDescription = "Reset notification ringtone to default",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Change notification ringtone",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .padding(end = 6.dp)
                                                .size(18.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Vibrate on Alert", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("Haptic vibration on notifications and alarms", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = vibrationEnabled,
                                    onCheckedChange = {
                                        vibrationEnabled = it
                                        saveNotifSettings()
                                        triggerVibration()
                                    },
                                    modifier = Modifier.testTag("vibrate_alert_switch")
                                )
                            }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Theme & Appearance",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
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

            // Background Execution & Reliability Permissions Card
            item {
                val lifecycleOwner = LocalLifecycleOwner.current
                val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
                val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager }
                val notificationManager = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager }

                var isBatteryUnrestricted by remember {
                    mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false)
                }
                var isExactAlarmAllowed by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            alarmManager?.canScheduleExactAlarms() ?: true
                        } else true
                    )
                }
                var isFullScreenAllowed by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            notificationManager?.canUseFullScreenIntent() ?: true
                        } else true
                    )
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isBatteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                            isExactAlarmAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                alarmManager?.canScheduleExactAlarms() ?: true
                            } else true
                            isFullScreenAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                notificationManager?.canUseFullScreenIntent() ?: true
                            } else true
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                fun openAppDetailsSettings(toastHint: String) {
                    Toast.makeText(context, toastHint, Toast.LENGTH_LONG).show()
                    try {
                        val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(detailsIntent)
                    } catch (_: Exception) {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {}
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VuGreenPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = VuGreenPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "System Permissions & Reliability",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Grant these permissions so Android delivers morning briefings and evening deadline alarms reliably on time:",
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        PermissionStatusRow(
                            icon = Icons.Default.ElectricBolt,
                            iconTint = VuWarningAmber,
                            title = "Battery Optimization",
                            subtitle = if (isBatteryUnrestricted) "Unrestricted background execution enabled" else "Disable battery restrictions to prevent delayed alarms",
                            statusLabel = if (isBatteryUnrestricted) "Allowed" else "Allow",
                            isGranted = isBatteryUnrestricted,
                            testTagValue = "battery_optimization_button",
                            onClick = {
                                val pm = context.packageManager
                                val reqIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                if (!isBatteryUnrestricted && reqIntent.resolveActivity(pm) != null) {
                                    try {
                                        context.startActivity(reqIntent)
                                        return@PermissionStatusRow
                                    } catch (_: Exception) {}
                                }
                                val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                if (listIntent.resolveActivity(pm) != null) {
                                    try {
                                        Toast.makeText(
                                            context,
                                            if (isBatteryUnrestricted) "Battery optimization already unrestricted. Select VU LMS Reminder to manage." else "Find VU LMS Reminder and choose Unrestricted / Don't optimize.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        context.startActivity(listIntent)
                                        return@PermissionStatusRow
                                    } catch (_: Exception) {}
                                }
                                openAppDetailsSettings("Tap Battery -> Select 'Unrestricted'")
                            }
                        )

                        PermissionStatusRow(
                            icon = Icons.Default.RocketLaunch,
                            iconTint = VuWarningAmber,
                            title = "Auto-Run / Background Launch",
                            subtitle = "Allow background startup on MIUI, ColorOS, OnePlus & Samsung",
                            statusLabel = "Open",
                            isGranted = false,
                            testTagValue = "auto_run_settings_button",
                            onClick = {
                                val pm = context.packageManager
                                val autoStartIntents = listOf(
                                    Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                                    Intent().setComponent(ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
                                    Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                                    Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
                                    Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
                                    Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                                    Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                                    Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
                                    Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                                    Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
                                    Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                                    Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
                                    Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")),
                                    Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
                                    Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")),
                                    Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.itel.autobootmanager.activity.AutoBootMgrActivity"))
                                )
                                var launched = false
                                for (candidate in autoStartIntents) {
                                    candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    if (candidate.resolveActivity(pm) != null) {
                                        try {
                                            context.startActivity(candidate)
                                            Toast.makeText(context, "Enable Auto-Start / Background Run for VU LMS Reminder", Toast.LENGTH_LONG).show()
                                            launched = true
                                            break
                                        } catch (_: Exception) {}
                                    }
                                }
                                if (!launched) {
                                    openAppDetailsSettings("Tap 'Battery' -> Select 'Unrestricted' & allow Background Usage")
                                }
                            }
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PermissionStatusRow(
                                icon = Icons.Default.Alarm,
                                iconTint = VuInfoCyan,
                                title = "Exact Alarm Scheduling",
                                subtitle = if (isExactAlarmAllowed) "Alarms and routines fire at the exact scheduled minute" else "Required on Android 12+ for punctual reminder alarms",
                                statusLabel = if (isExactAlarmAllowed) "Allowed" else "Allow",
                                isGranted = isExactAlarmAllowed,
                                testTagValue = "exact_alarm_permission_row",
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        openAppDetailsSettings("Allow 'Alarms & reminders' in App Settings")
                                    }
                                }
                            )
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            PermissionStatusRow(
                                icon = Icons.Default.NotificationsActive,
                                iconTint = VuDangerRed,
                                title = "Full-Screen Lock-Screen Alarm",
                                subtitle = if (isFullScreenAllowed) "Urgent evening alarms can wake your lock screen" else "Required on Android 14+ to display full-screen alarms",
                                statusLabel = if (isFullScreenAllowed) "Allowed" else "Allow",
                                isGranted = isFullScreenAllowed,
                                testTagValue = "full_screen_alarm_permission_row",
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        openAppDetailsSettings("Enable full-screen notifications for evening alarms")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Version Check & Documentation Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = VuInfoCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "About, Updates & Documentation",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedButton(
                            onClick = onCheckUpcomingNotifications,
                            enabled = !isCheckingNotifications,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("check_upcoming_notices_button")
                        ) {
                            if (isCheckingNotifications) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetching Upcoming Notices...", fontSize = 12.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = null,
                                    tint = VuInfoCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("What's Upcoming & Announcements", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        OutlinedButton(
                            onClick = onCheckUpdate,
                            enabled = !isCheckingUpdate,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("check_update_button")
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetching GitHub Release & Signature...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check for Updates (v${com.example.BuildConfig.VERSION_NAME})", fontSize = 12.sp)
                            }
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

                        OutlinedButton(
                            onClick = { showDocsDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("view_docs_details_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Documentation & Details", fontSize = 12.sp)
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = VuInfoCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Developer & Support",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AsyncImage(
                                model = "https://avatars.githubusercontent.com/u/115634418?v=4",
                                placeholder = rememberVectorPainter(Icons.Default.Person),
                                error = rememberVectorPainter(Icons.Default.Person),
                                fallback = rememberVectorPainter(Icons.Default.Person),
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

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hanzlarehman404")).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (ignored: Exception) {}
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VuInfoCyan),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("contact_donate_button")
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Contact Developer / Donate", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hanzlarehman404/vu-lms-reminder")).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (ignored: Exception) {}
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("contribute_repo_button")
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Contribute on GitHub Repository", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hanzlarehman404/vu-lms-reminder/issues")).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (ignored: Exception) {}
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VuWarningAmber),
                                border = androidx.compose.foundation.BorderStroke(1.dp, VuWarningAmber.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("report_issue_button")
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Report an Issue", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
