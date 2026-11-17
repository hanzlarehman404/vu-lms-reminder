package com.example.receiver

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.BuildConfig
import com.example.data.local.PreferencesManager
import com.example.data.remote.VuLmsNetworkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RemoteNotificationSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val networkService = VuLmsNetworkService()
            val result = networkService.fetchRemoteNotifications()
            if (result.isSuccess) {
                val now = System.currentTimeMillis()
                val versionCode = BuildConfig.VERSION_CODE
                val eligible = result.getOrNull().orEmpty()
                    .filter { it.isCurrentlyEligible(nowMillis = now, currentVersionCode = versionCode) }
                    .sortedByDescending { it.priority }

                val prefs = PreferencesManager(applicationContext)
                RemoteNotificationScheduler.dispatchSystemNotificationsIfEligible(
                    context = applicationContext,
                    eligibleItems = eligible,
                    prefs = prefs,
                    nowMillis = now
                )
            }
        } catch (_: Exception) {
            // Silent resilience: never crash or spam failures when offline
        } finally {
            RemoteNotificationScheduler.scheduleNextBackgroundSync(
                context = applicationContext,
                delayMinutes = RemoteNotificationScheduler.REFRESH_INTERVAL_MINUTES,
                replaceExisting = true
            )
        }
        Result.success()
    }
}
