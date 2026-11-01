package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.ActivityDao
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.ActivityEntity
import com.example.data.model.AppUpdateInfo
import com.example.data.remote.VuLmsNetworkService
import com.example.receiver.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VuLmsRepository(
    private val context: Context,
    private val dao: ActivityDao = AppDatabase.getDatabase(context).activityDao(),
    private val prefs: PreferencesManager = PreferencesManager(context),
    private val networkService: VuLmsNetworkService = VuLmsNetworkService(),
    private val alarmScheduler: AlarmScheduler = AlarmScheduler(context)
) {
    val allActivities: Flow<List<ActivityEntity>> = dao.getAllActivities()

    suspend fun login(studentId: String, pass: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Fetch tokens
            val tokenResult = networkService.fetchLoginPage()
            if (tokenResult.isFailure) {
                return@withContext Result.failure(tokenResult.exceptionOrNull() ?: Exception("Failed to load VU login page"))
            }
            val tokens = tokenResult.getOrThrow()

            // Step 2: Submit login credentials
            val loginResult = networkService.submitLogin(studentId, pass, tokens)
            if (loginResult.isFailure) {
                return@withContext Result.failure(loginResult.exceptionOrNull() ?: Exception("Login failed"))
            }

            // Save login state
            prefs.saveLogin(studentId, pass)

            // Step 3: Fetch activities immediately
            syncActivitiesInternal()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            Result.failure(e)
        }
    }

    suspend fun syncActivities(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val count = syncActivitiesInternal()
            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            Result.failure(e)
        }
    }

    private suspend fun syncActivitiesInternal(): Int {
        var fetchResult = networkService.fetchCalendarActivities()

        // If session expired, attempt re-authentication using saved credentials
        if (fetchResult.isFailure && prefs.isLoggedIn()) {
            val studentId = prefs.getStudentId()
            val pass = prefs.getPassword()
            if (studentId.isNotEmpty() && pass.isNotEmpty()) {
                val tokens = networkService.fetchLoginPage().getOrNull()
                if (tokens != null) {
                    networkService.submitLogin(studentId, pass, tokens)
                    fetchResult = networkService.fetchCalendarActivities()
                }
            }
        }

        if (fetchResult.isFailure) {
            throw fetchResult.exceptionOrNull() ?: Exception("Failed to sync calendar")
        }

        val calendarData = fetchResult.getOrThrow()
        val remoteActivities = calendarData.activities
        val now = System.currentTimeMillis()

        // 1. Fetch ToDo activities from Classic tab to check assignments/quizzes/GDBs/practicals
        val autoCompletedIds = mutableSetOf<String>()
        val todoKeysResult = networkService.fetchClassicToDoActivities(
            calendarData.viewState,
            calendarData.viewStateGenerator
        )

        if (todoKeysResult.isSuccess) {
            val pendingKeys = todoKeysResult.getOrNull().orEmpty()

            // An activity is completed if it's an assignment/quiz/gdb/practical, its start date has passed,
            // and it is NOT in the pending ToDo list!
            for (act in remoteActivities) {
                if (act.activityType.equals("Assignment", ignoreCase = true) ||
                    act.activityType.equals("Quiz", ignoreCase = true) ||
                    act.activityType.equals("GDB", ignoreCase = true) ||
                    act.activityType.equals("Practical", ignoreCase = true)
                ) {
                    if (act.startDate <= now) {
                        val key = act.getMatchingKey()
                        if (!pendingKeys.contains(key)) {
                            autoCompletedIds.add(act.id)
                        }
                    }
                }
            }
        }

        // 2. Fetch Account Book to check paid challans
        val paidChallansResult = networkService.fetchAccountBookPaidChallans()
        if (paidChallansResult.isSuccess) {
            val paidChallanIds = paidChallansResult.getOrNull().orEmpty()
            for (act in remoteActivities) {
                if (act.activityType.equals("Challan", ignoreCase = true)) {
                    // Title format: "ChallanID: 27765546"
                    val challanNumber = act.title.filter { it.isDigit() }
                    if (challanNumber.isNotEmpty() && paidChallanIds.contains(challanNumber)) {
                        autoCompletedIds.add(act.id)
                    }
                }
            }
        }

        // 3. Upsert into Room DB while preserving existing completions
        dao.upsertPreservingCompletion(remoteActivities, autoCompletedIds)
        prefs.setLastSyncTime(now)

        // 4. Prune old expired activities (> 7 days past deadline) to keep database small
        val cutoff = now - (7 * 24 * 3600 * 1000L)
        dao.deleteOldExpiredActivities(cutoff)

        // 5. Fetch and cache profile avatar if possible
        try {
            val avatarResult = networkService.fetchProfileAvatar()
            if (avatarResult.isSuccess) {
                val avatarBytes = avatarResult.getOrThrow()
                val avatarFile = java.io.File(context.filesDir, "student_avatar.jpg")
                avatarFile.writeBytes(avatarBytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not cache profile avatar", e)
        }

        // 6. Reschedule upcoming routine alarms
        val pending = dao.getAvailablePendingActivitiesSync(now)
        alarmScheduler.rescheduleAll(pending)

        return remoteActivities.size
    }

    fun getProfileAvatarFile(): java.io.File? {
        val file = java.io.File(context.filesDir, "student_avatar.jpg")
        return if (file.exists() && file.length() > 0) file else null
    }

    suspend fun toggleActivityCompletion(activityId: String, completed: Boolean) = withContext(Dispatchers.IO) {
        dao.setCompleted(activityId, completed)
        if (completed) {
            alarmScheduler.cancelAlarmsForActivity(activityId)
        } else {
            val act = dao.getActivityById(activityId)
            if (act != null && act.endDate > System.currentTimeMillis()) {
                alarmScheduler.scheduleActivityReminders(act)
            }
        }
    }

    suspend fun markAllAvailableCompleted() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val available = dao.getAvailablePendingActivitiesSync(now)
        dao.markAllAvailableCompleted(now)
        for (a in available) {
            alarmScheduler.cancelAlarmsForActivity(a.id)
        }
    }

    suspend fun resetAllCompletions() = withContext(Dispatchers.IO) {
        dao.resetAllCompletions()
        val now = System.currentTimeMillis()
        val pending = dao.getAvailablePendingActivitiesSync(now)
        alarmScheduler.rescheduleAll(pending)
    }

    suspend fun checkForUpdate(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        networkService.checkAppUpdate()
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val all = dao.getPendingUpcomingActivitiesSync(now)
        for (a in all) {
            alarmScheduler.cancelAlarmsForActivity(a.id)
        }
        networkService.clearCookies()
        prefs.clearLogin()
        dao.clearAll()
        try { java.io.File(context.filesDir, "student_avatar.jpg").delete() } catch (ignored: Exception) {}
    }

    fun getPreferences(): PreferencesManager = prefs

    companion object {
        private const val TAG = "VuLmsRepository"
    }
}
