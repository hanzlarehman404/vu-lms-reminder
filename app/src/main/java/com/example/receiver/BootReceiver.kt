package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val now = System.currentTimeMillis()
                    val pending = db.activityDao().getPendingUpcomingActivitiesSync(now)
                    val scheduler = AlarmScheduler(context)
                    scheduler.rescheduleAll(pending)
                    Log.d("BootReceiver", "Rescheduled ${pending.size} upcoming alarms on boot")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error restoring alarms on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
