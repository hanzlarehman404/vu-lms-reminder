package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.repository.VuLmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val notificationHelper = NotificationHelper(context)
        if (notifId != -1) {
            notificationHelper.cancelNotification(notifId)
        }

        val alarmScheduler = AlarmScheduler(context)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val repository = VuLmsRepository(context)

                when (intent.action) {
                    ACTION_MARK_ALL_DONE -> {
                        repository.markAllAvailableCompleted()
                        Log.d(TAG, "Marked all available activities as done from notification")
                    }

                    ACTION_RESYNC -> {
                        repository.syncActivities()
                        Log.d(TAG, "Triggered resync from notification")
                    }

                    ACTION_MARK_DONE -> {
                        val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)
                        if (!activityId.isNullOrEmpty()) {
                            db.activityDao().setCompleted(activityId, true)
                            alarmScheduler.cancelAlarmsForActivity(activityId)
                            Log.d(TAG, "Marked activity as done from notification: $activityId")
                        }
                    }

                    ACTION_SNOOZE -> {
                        val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)
                        if (!activityId.isNullOrEmpty()) {
                            alarmScheduler.scheduleSnooze(activityId, 30)
                            Log.d(TAG, "Snoozed activity alarm for 30 minutes: $activityId")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling notification action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NotifActionReceiver"
        const val ACTION_MARK_DONE = "com.example.vulms.ACTION_MARK_DONE"
        const val ACTION_MARK_ALL_DONE = "com.example.vulms.ACTION_MARK_ALL_DONE"
        const val ACTION_RESYNC = "com.example.vulms.ACTION_RESYNC"
        const val ACTION_SNOOZE = "com.example.vulms.ACTION_SNOOZE"

        const val EXTRA_ACTIVITY_ID = "extra_activity_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
