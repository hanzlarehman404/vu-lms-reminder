package com.example.receiver

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.PreferencesManager
import com.example.data.model.RemoteNotificationItem
import java.util.concurrent.TimeUnit

object RemoteNotificationScheduler {
    const val UNIQUE_WORK_NAME = "remote_notifications_5m_sync"
    const val REFRESH_INTERVAL_MINUTES = 5L
    const val REFRESH_INTERVAL_MS = REFRESH_INTERVAL_MINUTES * 60_000L

    fun scheduleNextBackgroundSync(
        context: Context,
        delayMinutes: Long = REFRESH_INTERVAL_MINUTES,
        replaceExisting: Boolean = false
    ) {
        runCatching {
            val appContext = context.applicationContext
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<RemoteNotificationSyncWorker>()
                .setInitialDelay(delayMinutes.coerceAtLeast(1L), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    fun dispatchSystemNotificationsIfEligible(
        context: Context,
        eligibleItems: List<RemoteNotificationItem>,
        prefs: PreferencesManager = PreferencesManager(context),
        nowMillis: Long = System.currentTimeMillis(),
        postNotification: (RemoteNotificationItem) -> Boolean = { item ->
            NotificationHelper(context).showRemoteAnnouncementNotification(item)
        }
    ): Int {
        var postedCount = 0
        for (item in eligibleItems) {
            if (item.showNotificationBar && prefs.canShowNotificationBar(item, nowMillis)) {
                val posted = postNotification(item)
                if (posted) {
                    prefs.markNotificationBarShown(item.id, nowMillis)
                    postedCount++
                }
            }
        }
        return postedCount
    }
}
