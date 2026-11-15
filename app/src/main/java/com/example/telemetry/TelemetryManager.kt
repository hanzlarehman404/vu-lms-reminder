package com.example.telemetry

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object TelemetryManager {
    internal const val PREFS_NAME = "telemetry_prefs"
    internal const val KEY_REPORTED_VERSION = "reported_version_code"
    internal const val KEY_LAST_SUCCESS_MS = "last_success_timestamp_ms"
    internal const val CHECKIN_INTERVAL_MS = 24L * 60L * 60L * 1000L // 24 hours
    internal const val UNIQUE_WORK_NAME = "app_telemetry_checkin"

    fun scheduleCheckIn(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val currentVersionCode = getCurrentVersionCode(appContext)
            val reportedVersionCode = prefs.getInt(KEY_REPORTED_VERSION, -1)
            val lastSuccessMs = prefs.getLong(KEY_LAST_SUCCESS_MS, 0L)
            val now = System.currentTimeMillis()

            val eventName = determineEventName(
                reportedVersionCode = reportedVersionCode,
                currentVersionCode = currentVersionCode,
                lastSuccessMs = lastSuccessMs,
                nowMs = now
            ) ?: return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<TelemetryWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(TelemetryWorker.KEY_EVENT_NAME to eventName))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    internal fun determineEventName(
        reportedVersionCode: Int,
        currentVersionCode: Int,
        lastSuccessMs: Long,
        nowMs: Long
    ): String? {
        return when {
            reportedVersionCode == -1 -> "first_open"
            currentVersionCode > reportedVersionCode -> "app_updated"
            nowMs - lastSuccessMs >= CHECKIN_INTERVAL_MS -> "daily_checkin"
            else -> null
        }
    }

    internal fun markSuccess(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_REPORTED_VERSION, getCurrentVersionCode(context))
            .putLong(KEY_LAST_SUCCESS_MS, nowMs)
            .apply()
    }

    internal fun getCurrentVersionCode(context: Context): Int {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            PackageInfoCompat.getLongVersionCode(info).toInt()
        }.getOrDefault(0)
    }
}
