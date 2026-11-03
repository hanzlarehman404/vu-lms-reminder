package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.model.ActivityEntity
import com.example.data.model.ActivityType

class NotificationHelper(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()

            val deadlineChannel = NotificationChannel(
                CHANNEL_DEADLINE_ID,
                "LMS Activity Routine Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Morning routine reminders and available activity updates"
                enableLights(true)
                enableVibration(true)
                setSound(alarmSound, audioAttributes)
            }

            val urgentSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val urgentChannel = NotificationChannel(
                CHANNEL_URGENT_ID,
                "Urgent Deadlines Today",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alarms for activities due today with few hours remaining"
                enableLights(true)
                enableVibration(true)
                setSound(urgentSound, audioAttributes)
            }

            notificationManager.createNotificationChannel(deadlineChannel)
            notificationManager.createNotificationChannel(urgentChannel)
        }
    }

    fun showDeadlineNotification(
        activity: ActivityEntity,
        hoursRemaining: Long,
        isUrgent: Boolean = false
    ) {
        val channelId = if (isUrgent) CHANNEL_URGENT_ID else CHANNEL_DEADLINE_ID
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

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
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
            .addAction(android.R.drawable.checkbox_on_background, "Mark All Done", markAllPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Resync LMS", resyncPendingIntent)

        val prefs = com.example.data.local.PreferencesManager(context)
        if (!prefs.isAlarmSoundEnabled()) {
            builder.setSilent(true)
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

        val body = buildString {
            append("Have you done all of your available activities?\n")
            if (todayDueCount > 0) {
                append("🚨 $todayDueCount items are DUE TODAY! Don't miss the deadline.\n")
            }
            append("📋 $availableActivitiesCount pending activities available in your LMS.")
        }

        val prefs = com.example.data.local.PreferencesManager(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_DEADLINE_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🌤️ VU LMS Mid-Day Check")
            .setContentText(if (todayDueCount > 0) "Reminder: $todayDueCount items due today! Have you done them?" else "Have you done all your $availableActivitiesCount available activities?")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(appPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "Mark All Done", markAllPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Resync LMS", resyncPendingIntent)

        if (!prefs.isAlarmSoundEnabled()) {
            builder.setSilent(true)
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
        val builder = NotificationCompat.Builder(context, CHANNEL_DEADLINE_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("☀️ VU LMS Morning Briefing")
            .setContentText(if (todayDueCount > 0) "$todayDueCount DUE TODAY! Tap to review." else "$totalAvailableCount activities available.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(appPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "Mark All Done", markAllPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Resync LMS", resyncPendingIntent)

        if (!prefs.isAlarmSoundEnabled()) {
            builder.setSilent(true)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    companion object {
        const val CHANNEL_DEADLINE_ID = "channel_vu_deadlines"
        const val CHANNEL_URGENT_ID = "channel_vu_urgent_alarms"
    }
}
