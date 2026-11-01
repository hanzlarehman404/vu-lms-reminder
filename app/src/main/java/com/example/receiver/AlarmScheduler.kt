package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.PreferencesManager
import com.example.data.model.ActivityEntity
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = PreferencesManager(context)

    fun scheduleRoutines() {
        if (!prefs.isNotificationsEnabled()) {
            cancelRoutines()
            return
        }

        scheduleDailyRoutine(prefs.getMorningSyncTime(), REQUEST_CODE_MORNING, isEvening = false)
        scheduleDailyRoutine(prefs.getEveningReminderTime(), REQUEST_CODE_EVENING, isEvening = true)
    }

    private fun scheduleDailyRoutine(timeStr: String, requestCode: Int, isEvening: Boolean) {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: if (isEvening) 21 else 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
            action = if (isEvening) AlarmBroadcastReceiver.ACTION_EVENING_ROUTINE else AlarmBroadcastReceiver.ACTION_MORNING_ROUTINE
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled routine (evening=$isEvening) at ${cal.time}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule routine", e)
        }
    }

    fun scheduleActivityReminders(activity: ActivityEntity) {
        // Obey rule: No notification if completed, if past, or if not yet available
        val now = System.currentTimeMillis()
        if (activity.isCompleted || activity.endDate <= now) {
            cancelAlarmsForActivity(activity.id)
            return
        }

        // If activity is due today, schedule urgent alert for 2 hours before deadline
        if (activity.isDueToday(now) && prefs.isUrgentAlarmEnabled()) {
            val triggerTime = activity.endDate - (2 * 3600 * 1000L)
            if (triggerTime > now) {
                val requestCode = (activity.id.hashCode() xor 9999).coerceAtLeast(0)
                val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                    action = AlarmBroadcastReceiver.ACTION_ACTIVITY_ALARM
                    putExtra(AlarmBroadcastReceiver.EXTRA_ACTIVITY_ID, activity.id)
                    putExtra(AlarmBroadcastReceiver.EXTRA_IS_URGENT, true)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
                } catch (ignored: Exception) {}
            }
        }
    }

    fun cancelAlarmsForActivity(activityId: String) {
        val requestCode = (activityId.hashCode() xor 9999).coerceAtLeast(0)
        val intent = Intent(context, AlarmBroadcastReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    fun cancelRoutines() {
        val morningIntent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
            action = AlarmBroadcastReceiver.ACTION_MORNING_ROUTINE
        }
        val morningPi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_MORNING,
            morningIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        morningPi?.let { alarmManager.cancel(it) }

        val eveningIntent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
            action = AlarmBroadcastReceiver.ACTION_EVENING_ROUTINE
        }
        val eveningPi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_EVENING,
            eveningIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        eveningPi?.let { alarmManager.cancel(it) }
    }

    fun rescheduleAll(activities: List<ActivityEntity>) {
        scheduleRoutines()
        val now = System.currentTimeMillis()
        for (activity in activities) {
            if (!activity.isCompleted && activity.endDate > now && activity.isAvailable(now)) {
                scheduleActivityReminders(activity)
            } else {
                cancelAlarmsForActivity(activity.id)
            }
        }
    }

    fun scheduleSnooze(activityId: String, delayMinutes: Int = 30) {
        val triggerTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)
        val requestCode = (activityId.hashCode() xor 8888).coerceAtLeast(0)
        val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
            action = AlarmBroadcastReceiver.ACTION_ACTIVITY_ALARM
            putExtra(AlarmBroadcastReceiver.EXTRA_ACTIVITY_ID, activityId)
            putExtra(AlarmBroadcastReceiver.EXTRA_IS_URGENT, true)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
        } catch (ignored: Exception) {}
    }

    companion object {
        private const val TAG = "AlarmScheduler"
        const val REQUEST_CODE_MORNING = 101
        const val REQUEST_CODE_EVENING = 102
    }
}
