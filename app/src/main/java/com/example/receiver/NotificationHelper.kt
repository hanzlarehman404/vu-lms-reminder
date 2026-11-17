package com.example.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ActivityEntity
import com.example.data.model.ActivityType
import com.example.data.model.RemoteNotificationItem
import java.util.Locale

class NotificationHelper(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    fun syncNotificationChannels() {
        createNotificationChannels()
    }

    private fun resolveNotificationToneUri(prefs: com.example.data.local.PreferencesManager): Uri {
        val customUriStr = prefs.getNotificationToneUri()
        if (customUriStr.isNotBlank()) {
            val parsed = runCatching { Uri.parse(customUriStr) }.getOrNull()
            if (parsed != null) return parsed
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    }

    private fun activeDeadlineChannelId(prefs: com.example.data.local.PreferencesManager): String {
        return "${CHANNEL_DEADLINE_ID}_${prefs.resolveNotificationChannelKey()}"
    }

    private fun activeAnnouncementsChannelId(prefs: com.example.data.local.PreferencesManager): String {
        return "${CHANNEL_REMOTE_ANNOUNCEMENTS_ID}_${prefs.resolveNotificationChannelKey()}"
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val prefs = com.example.data.local.PreferencesManager(context)
            val deadlineChannelId = activeDeadlineChannelId(prefs)
            val announcementsChannelId = activeAnnouncementsChannelId(prefs)

            // Clean up obsolete notification channels when user switches notification tone
            runCatching {
                notificationManager.notificationChannels.forEach { existing ->
                    val id = existing.id
                    val isObsoleteDeadline = id.startsWith(CHANNEL_DEADLINE_ID) && id != deadlineChannelId
                    val isObsoleteAnnouncement = id.startsWith(CHANNEL_REMOTE_ANNOUNCEMENTS_ID) && id != announcementsChannelId
                    if (isObsoleteDeadline || isObsoleteAnnouncement) {
                        notificationManager.deleteNotificationChannel(id)
                    }
                }
            }

            val soundEnabled = prefs.isAlarmSoundEnabled()
            val selectedNotificationUri = resolveNotificationToneUri(prefs)
            val notificationAudioAttrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val deadlineChannel = NotificationChannel(
                deadlineChannelId,
                "LMS Activity Routine Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Morning routine reminders and available activity updates"
                enableLights(true)
                enableVibration(prefs.isVibrationEnabled())
                if (soundEnabled) {
                    setSound(selectedNotificationUri, notificationAudioAttrs)
                } else {
                    setSound(null, null)
                }
            }

            val urgentSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: selectedNotificationUri
            val alarmAudioAttrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            val urgentChannel = NotificationChannel(
                CHANNEL_URGENT_ID,
                "Urgent Deadlines Today",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alarms for activities due today with few hours remaining"
                enableLights(true)
                enableVibration(true)
                setSound(urgentSound, alarmAudioAttrs)
            }

            val announcementsChannel = NotificationChannel(
                announcementsChannelId,
                "Announcements & Upcoming Notices",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Major update previews, VU LMS notices, and important announcements"
                enableLights(true)
                enableVibration(prefs.isVibrationEnabled())
                if (soundEnabled) {
                    setSound(selectedNotificationUri, notificationAudioAttrs)
                } else {
                    setSound(null, null)
                }
            }

            notificationManager.createNotificationChannel(deadlineChannel)
            notificationManager.createNotificationChannel(urgentChannel)
            notificationManager.createNotificationChannel(announcementsChannel)
        }
    }

    fun showDeadlineNotification(
        activity: ActivityEntity,
        hoursRemaining: Long,
        isUrgent: Boolean = false
    ) {
        val prefs = com.example.data.local.PreferencesManager(context)
        createNotificationChannels()
        val channelId = if (isUrgent) CHANNEL_URGENT_ID else activeDeadlineChannelId(prefs)
        val notificationId = activity.id.hashCode()

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Open VU LMS directly
        val lmsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(activity.url.ifEmpty { "https://vulms.vu.edu.pk" })).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val lmsPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 1,
            lmsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Mark All Available Done
        val markAllIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_ALL_DONE
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markAllPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            markAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Resync with LMS
        val resyncIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_RESYNC
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val resyncPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 3,
            resyncIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = when {
            hoursRemaining <= 1 -> "< 1 hour remaining!"
            hoursRemaining < 24 -> "$hoursRemaining hours remaining today!"
            else -> "${hoursRemaining / 24} days left"
        }

        val type = ActivityType.fromString(activity.activityType, activity.title)
        val titlePrefix = if (isUrgent) "🚨 URGENT TODAY: " else "⏰ "
        val isDueToday = isUrgent || hoursRemaining < 24 || activity.isDueToday()
        val accentColor = if (isDueToday) COLOR_URGENT_RED else COLOR_INFO_CYAN

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_vu_bell)
            .setColor(accentColor)
            .setContentTitle("$titlePrefix[${activity.courseCode}] ${type.emoji} ${activity.title}")
            .setContentText("Due today • $timeText")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${type.emoji} ${activity.title}\nCourse: ${activity.courseCode}\nTime Left: $timeText\nDue: ${java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(activity.endDate))}"
                )
            )
            .setPriority(if (isUrgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isUrgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(appPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_view, "Open LMS", lmsPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Resync LMS", resyncPendingIntent)

        if (!prefs.isAlarmSoundEnabled()) {
            builder.setSilent(true)
        } else if (!isUrgent) {
            builder.setSound(resolveNotificationToneUri(prefs))
        }

        // Only launch full screen alarm if both isUrgent AND user has enabled urgent alarms in settings.
        // If disabled, user receives a high-priority heads-up notification without taking over their full screen.
        if (isUrgent && prefs.isUrgentAlarmEnabled()) {
            val fullScreenIntent = Intent(context, UrgentAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(UrgentAlarmActivity.EXTRA_ACTIVITY_ID, activity.id)
                putExtra(UrgentAlarmActivity.EXTRA_ACTIVITY_TITLE, activity.title)
                putExtra(UrgentAlarmActivity.EXTRA_COURSE_CODE, activity.courseCode)
                putExtra(UrgentAlarmActivity.EXTRA_END_DATE, activity.endDate)
                putExtra(UrgentAlarmActivity.EXTRA_URL, activity.url)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 4,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    fun showMiddayCheckNotification(
        availableActivitiesCount: Int,
        todayDueCount: Int
    ) {
        val notificationId = 10002
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resyncIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_RESYNC
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val resyncPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            resyncIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = buildString {
            append("Have you done all of your available activities?\n")
            if (todayDueCount > 0) {
                append("🚨 $todayDueCount items are DUE TODAY! Don't miss the deadline.\n")
            }
            append("📋 $availableActivitiesCount pending activities available in your LMS.")
        }

        val prefs = com.example.data.local.PreferencesManager(context)
        createNotificationChannels()
        val accentColor = if (todayDueCount > 0) COLOR_URGENT_RED else COLOR_INFO_CYAN
        val builder = NotificationCompat.Builder(context, activeDeadlineChannelId(prefs))
            .setSmallIcon(R.drawable.ic_notification_vu_bell)
            .setColor(accentColor)
            .setContentTitle("🌤️ VU LMS Mid-Day Check")
            .setContentText(if (todayDueCount > 0) "Reminder: $todayDueCount items due today! Have you done them?" else "Have you done all your $availableActivitiesCount available activities?")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(appPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_popup_sync, "Resync LMS", resyncPendingIntent)

        if (!prefs.isAlarmSoundEnabled()) {
            builder.setSilent(true)
        } else {
            builder.setSound(resolveNotificationToneUri(prefs))
        }

        notificationManager.notify(notificationId, builder.build())
    }

    fun showMorningBriefingNotification(
        todayDueCount: Int,
        totalAvailableCount: Int,
        tomorrowCount: Int
    ) {
        val notificationId = 10001
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resyncIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_RESYNC
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val resyncPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            resyncIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = buildString {
            if (todayDueCount > 0) {
                append("🚨 $todayDueCount activities are due TODAY!\n")
            } else {
                append("✨ No activities due today.\n")
            }
            append("📋 $totalAvailableCount total activities available to submit.\n")
            if (tomorrowCount > 0) {
                append("📅 Tomorrow routine: $tomorrowCount activities will be due.")
            }
        }

        val prefs = com.example.data.local.PreferencesManager(context)
        createNotificationChannels()
        val accentColor = if (todayDueCount > 0) COLOR_URGENT_RED else COLOR_INFO_CYAN
        val builder = NotificationCompat.Builder(context, activeDeadlineChannelId(prefs))
            .setSmallIcon(R.drawable.ic_notification_vu_bell)
            .setColor(accentColor)
            .setContentTitle("☀️ VU LMS Morning Briefing")
            .setContentText(if (todayDueCount > 0) "$todayDueCount DUE TODAY! Tap to review." else "$totalAvailableCount activities available.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(appPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_popup_sync, "Resync LMS", resyncPendingIntent)

        if (!prefs.isAlarmSoundEnabled()) {
            builder.setSilent(true)
        } else {
            builder.setSound(resolveNotificationToneUri(prefs))
        }

        notificationManager.notify(notificationId, builder.build())
    }

    fun showResyncConfirmationNotification(
        activitiesCount: Int,
        message: String = "LMS activities synced successfully. You're up to date!"
    ) {
        val notificationId = 10099
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefs = com.example.data.local.PreferencesManager(context)
        createNotificationChannels()
        val builder = NotificationCompat.Builder(context, activeDeadlineChannelId(prefs))
            .setSmallIcon(R.drawable.ic_notification_vu_bell)
            .setColor(COLOR_INFO_CYAN)
            .setContentTitle("✅ VU LMS Synced")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$message\nTotal available activities: $activitiesCount"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(appPendingIntent)
            .setAutoCancel(true)

        if (!prefs.isAlarmSoundEnabled()) {
            builder.setSilent(true)
        } else {
            builder.setSound(resolveNotificationToneUri(prefs))
        }

        notificationManager.notify(notificationId, builder.build())
    }

    fun showRemoteAnnouncementNotification(item: RemoteNotificationItem): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        val notificationId = 20000 + (item.id.hashCode() and 0x7FFF)
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val emoji = when (item.severity.lowercase(Locale.US)) {
            RemoteNotificationItem.SEVERITY_MAJOR_UPDATE -> "🚀"
            RemoteNotificationItem.SEVERITY_WARNING -> "⚠️"
            RemoteNotificationItem.SEVERITY_CRITICAL -> "🚨"
            else -> "📢"
        }

        val displayTitle = buildString {
            append(emoji).append(" ")
            val customBadge = item.effectiveBadge()
            if (customBadge.isNotEmpty() && !item.title.contains(customBadge, ignoreCase = true)) {
                append("[").append(customBadge).append("] ")
            }
            append(item.title.ifBlank { "VU LMS Reminder Notice" })
        }

        val shortText = item.message.ifBlank { item.effectiveBannerLine() }
        val bigText = buildString {
            append(shortText)
            if (item.highlights.isNotEmpty()) {
                append("\n")
                item.highlights.forEach { bullet ->
                    append("\n• ").append(bullet)
                }
            }
        }

        val prefs = com.example.data.local.PreferencesManager(context)
        createNotificationChannels()
        val builder = NotificationCompat.Builder(context, activeAnnouncementsChannelId(prefs))
            .setSmallIcon(R.drawable.ic_notification_vu_bell)
            .setColor(COLOR_INFO_CYAN)
            .setContentTitle(displayTitle)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(appPendingIntent)
            .setAutoCancel(true)

        if (item.actionUrl.isNotBlank()) {
            val linkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(item.actionUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val linkPendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 1,
                linkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_view,
                item.actionLabel.ifBlank { "Open Link" },
                linkPendingIntent
            )
        }

        if (!prefs.isAlarmSoundEnabled()) {
            builder.setSilent(true)
        } else {
            builder.setSound(resolveNotificationToneUri(prefs))
        }

        return try {
            notificationManager.notify(notificationId, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    companion object {
        const val CHANNEL_DEADLINE_ID = "channel_vu_deadlines"
        const val CHANNEL_URGENT_ID = "channel_vu_urgent_alarms"
        const val CHANNEL_REMOTE_ANNOUNCEMENTS_ID = "channel_vu_remote_announcements"
        const val COLOR_INFO_CYAN = 0xFF36A3F7.toInt()
        const val COLOR_URGENT_RED = 0xFFF4516C.toInt()
    }
}
