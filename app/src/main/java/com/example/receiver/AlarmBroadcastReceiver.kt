package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.VuLmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "vulms:RoutineAlarmWakeLock"
        )
        wakeLock.acquire(15000L) // 15 seconds max

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val prefs = PreferencesManager(context)
                val repository = VuLmsRepository(context)
                val notificationHelper = NotificationHelper(context)
                val now = System.currentTimeMillis()

                when (intent.action) {
                    ACTION_MORNING_ROUTINE -> {
                        Log.d(TAG, "Executing Morning Routine...")
                        // Sync and auto-mark completed
                        try {
                            repository.syncActivities()
                        } catch (e: Exception) {
                            Log.w(TAG, "Morning sync error", e)
                        }

                        val available = db.activityDao().getAvailablePendingActivitiesSync(now)
                        val todayDueCount = available.count { it.isDueToday(now) }
                        val tomorrowCount = if (prefs.isNextDayRoutineEnabled()) {
                            available.count { it.isDueTomorrow(now) }
                        } else 0

                        notificationHelper.showMorningBriefingNotification(
                            todayDueCount = todayDueCount,
                            totalAvailableCount = available.size,
                            tomorrowCount = tomorrowCount
                        )

                        // Reschedule for next day
                        AlarmScheduler(context).scheduleRoutines()
                    }

                    ACTION_MIDDAY_ROUTINE -> {
                        Log.d(TAG, "Executing Mid-Day Check Routine...")
                        try {
                            repository.syncActivities()
                        } catch (e: Exception) {
                            Log.w(TAG, "Midday sync error", e)
                        }

                        val available = db.activityDao().getAvailablePendingActivitiesSync(now)
                        val todayDueCount = available.count { it.isDueToday(now) }

                        notificationHelper.showMiddayCheckNotification(
                            availableActivitiesCount = available.size,
                            todayDueCount = todayDueCount
                        )

                        // Reschedule for next day
                        AlarmScheduler(context).scheduleRoutines()
                    }

                    ACTION_EVENING_ROUTINE -> {
                        Log.d(TAG, "Executing Evening Last Reminder Routine...")
                        // Sync and auto-mark completed
                        try {
                            repository.syncActivities()
                        } catch (e: Exception) {
                            Log.w(TAG, "Evening sync error", e)
                        }

                        val available = db.activityDao().getAvailablePendingActivitiesSync(now)
                        val todayDueActivities = available.filter { it.isDueToday(now) }

                        for (activity in todayDueActivities) {
                            val remainingHours = (activity.endDate - now) / (3600 * 1000L)
                            notificationHelper.showDeadlineNotification(activity, remainingHours, isUrgent = true)
                        }

                        // Reschedule for next day
                        AlarmScheduler(context).scheduleRoutines()
                    }

                    ACTION_ACTIVITY_ALARM, null -> {
                        val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID) ?: return@launch
                        val isUrgent = intent.getBooleanExtra(EXTRA_IS_URGENT, false)
                        val activity = db.activityDao().getActivityById(activityId) ?: return@launch

                        if (activity.isCompleted || activity.endDate <= now) {
                            return@launch
                        }

                        val remainingHours = (activity.endDate - now) / (3600 * 1000L)
                        notificationHelper.showDeadlineNotification(activity, remainingHours, isUrgent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling alarm trigger", e)
            } finally {
                wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmBroadcastReceiver"
        const val ACTION_MORNING_ROUTINE = "com.example.vulms.ACTION_MORNING_ROUTINE"
        const val ACTION_MIDDAY_ROUTINE = "com.example.vulms.ACTION_MIDDAY_ROUTINE"
        const val ACTION_EVENING_ROUTINE = "com.example.vulms.ACTION_EVENING_ROUTINE"
        const val ACTION_ACTIVITY_ALARM = "com.example.vulms.ACTION_ACTIVITY_ALARM"

        const val EXTRA_ACTIVITY_ID = "extra_activity_id"
        const val EXTRA_IS_URGENT = "extra_is_urgent"
    }
}
