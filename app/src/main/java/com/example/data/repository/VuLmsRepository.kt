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

import com.example.data.model.ChallanEntity
import com.example.data.model.EnrolledSubject
import com.example.data.model.StudentProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class VuLmsRepository(
    private val context: Context,
    private val dao: ActivityDao = AppDatabase.getDatabase(context).activityDao(),
    private val prefs: PreferencesManager = PreferencesManager(context),
    private val networkService: VuLmsNetworkService = VuLmsNetworkService(),
    private val alarmScheduler: AlarmScheduler = AlarmScheduler(context)
) {
    val allActivities: Flow<List<ActivityEntity>> = dao.getAllActivities()

    private val _challansFlow = MutableStateFlow<List<ChallanEntity>>(prefs.getChallans())
    val challans: StateFlow<List<ChallanEntity>> = _challansFlow.asStateFlow()

    private val _subjectsFlow = MutableStateFlow<List<EnrolledSubject>>(prefs.getCurrentSubjects())
    val currentSubjects: StateFlow<List<EnrolledSubject>> = _subjectsFlow.asStateFlow()

    private val _studentProfileFlow = MutableStateFlow(prefs.getStudentProfile())
    val studentProfile: StateFlow<StudentProfile> = _studentProfileFlow.asStateFlow()

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

            // Step 3: Fetch activities and all LMS data immediately
            syncActivitiesInternal()

            // Step 4: Ensure student profile is refreshed
            try {
                refreshStudentProfile()
            } catch (pe: Exception) {
                Log.w(TAG, "Post-login academic profile sync note: ${pe.message}")
            }

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

        // 2. Fetch Account Book to parse challans
        try {
            val challanResult = networkService.fetchAccountBook()
            if (challanResult.isSuccess) {
                val list = challanResult.getOrNull().orEmpty()
                _challansFlow.value = list
                prefs.saveChallans(list)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Account book fetch error", e)
        }

        // 3. Fetch Enrolled Subjects
        try {
            val subjectsResult = networkService.fetchCurrentSubjects()
            val parsedSubjects = subjectsResult.getOrNull().orEmpty().toMutableList()
            // Merge with courses derived from calendar activities
            val derivedCourses = remoteActivities
                .filter { it.courseCode.isNotEmpty() && !it.courseCode.equals("FEE", ignoreCase = true) }
                .groupBy { it.courseCode }
                .map { (code, acts) ->
                    val fullTitle = acts.firstOrNull()?.title?.substringAfter(":")?.trim()?.ifEmpty { code } ?: code
                    EnrolledSubject(code = code, title = fullTitle)
                }
            for (dc in derivedCourses) {
                if (parsedSubjects.none { it.code.equals(dc.code, ignoreCase = true) }) {
                    parsedSubjects.add(dc)
                }
            }
            if (parsedSubjects.isNotEmpty()) {
                _subjectsFlow.value = parsedSubjects
                prefs.saveCurrentSubjects(parsedSubjects)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Current subjects fetch error", e)
        }

        // 4. Fetch Student Profile (always merge with cached so partial responses never wipe CGPA/credits)
        try {
            val profileResult = networkService.fetchStudentProfile()
            if (profileResult.isSuccess) {
                var prof = profileResult.getOrThrow()
                if (prof.studentId.isEmpty()) {
                    prof = prof.copy(studentId = prefs.getStudentId())
                }
                if (prof.studentId.isNotEmpty() || prof.studentName.isNotEmpty() || prof.cgpa.isNotEmpty()) {
                    val merged = prefs.saveStudentProfile(prof)
                    _studentProfileFlow.value = merged
                }
            } else if (_studentProfileFlow.value.studentId.isEmpty() && prefs.getStudentId().isNotEmpty()) {
                val merged = prefs.saveStudentProfile(_studentProfileFlow.value.copy(studentId = prefs.getStudentId()))
                _studentProfileFlow.value = merged
            }
        } catch (e: Exception) {
            Log.w(TAG, "Student profile fetch error", e)
            if (_studentProfileFlow.value.studentId.isEmpty() && prefs.getStudentId().isNotEmpty()) {
                val merged = prefs.saveStudentProfile(_studentProfileFlow.value.copy(studentId = prefs.getStudentId()))
                _studentProfileFlow.value = merged
            }
        }

        // 5. Fresh sync: wipe old duplicates and refresh database with fresh remote activities
        val finalActivities = remoteActivities.map { act ->
            val isDone = autoCompletedIds.contains(act.id)
            act.copy(isCompleted = isDone)
        }
        dao.refreshAll(finalActivities)
        prefs.setLastSyncTime(now)

        // 6. Fetch and cache profile avatar if possible
        try {
            val avatarResult = networkService.fetchProfileAvatar()
            if (avatarResult.isSuccess) {
                val avatarBytes = avatarResult.getOrThrow()
                val avatarFile = File(context.filesDir, "student_avatar.jpg")
                avatarFile.writeBytes(avatarBytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not cache profile avatar", e)
        }

        // 7. Reschedule upcoming routine alarms
        val pending = dao.getAvailablePendingActivitiesSync(now)
        alarmScheduler.rescheduleAll(pending)

        return remoteActivities.size
    }

    fun getStudentECardFile(): File? {
        val file = File(context.filesDir, "student_ecard.pdf")
        return if (file.exists() && file.length() > 0) file else null
    }

    suspend fun refreshStudentProfile(): Result<StudentProfile> = withContext(Dispatchers.IO) {
        if (prefs.isLoggedIn() && !networkService.hasSessionCookie()) {
            val studentId = prefs.getStudentId()
            val pass = prefs.getPassword()
            if (studentId.isNotEmpty() && pass.isNotEmpty()) {
                val tokens = networkService.fetchLoginPage().getOrNull()
                if (tokens != null) {
                    networkService.submitLogin(studentId, pass, tokens)
                }
            }
        }

        var profileResult = networkService.fetchStudentProfile()
        if (profileResult.isFailure && prefs.isLoggedIn()) {
            val studentId = prefs.getStudentId()
            val pass = prefs.getPassword()
            if (studentId.isNotEmpty() && pass.isNotEmpty()) {
                Log.d(TAG, "Re-authenticating for student profile fetch...")
                val tokens = networkService.fetchLoginPage().getOrNull()
                if (tokens != null) {
                    val loginRes = networkService.submitLogin(studentId, pass, tokens)
                    if (loginRes.isSuccess) {
                        profileResult = networkService.fetchStudentProfile()
                    }
                }
            }
        }

        if (profileResult.isSuccess) {
            var prof = profileResult.getOrThrow()
            if (prof.studentId.isEmpty()) {
                prof = prof.copy(studentId = prefs.getStudentId())
            }
            if (prof.studentId.isNotEmpty() || prof.studentName.isNotEmpty() || prof.cgpa.isNotEmpty()) {
                val merged = prefs.saveStudentProfile(prof)
                _studentProfileFlow.value = merged
            }
        }

        try {
            val subjectsRes = networkService.fetchCurrentSubjects()
            if (subjectsRes.isSuccess) {
                val subs = subjectsRes.getOrNull().orEmpty()
                if (subs.isNotEmpty()) {
                    _subjectsFlow.value = subs
                    prefs.saveCurrentSubjects(subs)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Subjects refresh error: ${e.message}")
        }

        profileResult
    }

    fun syncCookiesToWebView() {
        networkService.syncCookiesToWebView()
    }

    suspend fun prepareChallanForPayment(challan: ChallanEntity): ChallanEntity = withContext(Dispatchers.IO) {
        try {
            if (!networkService.hasSessionCookie() && prefs.isLoggedIn()) {
                val studentId = prefs.getStudentId()
                val pass = prefs.getPassword()
                if (studentId.isNotEmpty() && pass.isNotEmpty()) {
                    val tokens = networkService.fetchLoginPage().getOrNull()
                    if (tokens != null) {
                        networkService.submitLogin(studentId, pass, tokens)
                    }
                }
            }

            if (challan.viewState.isEmpty() || !networkService.hasSessionCookie()) {
                var res = networkService.fetchAccountBook()
                if (res.isFailure && prefs.isLoggedIn()) {
                    val studentId = prefs.getStudentId()
                    val pass = prefs.getPassword()
                    val tokens = networkService.fetchLoginPage().getOrNull()
                    if (tokens != null && studentId.isNotEmpty() && pass.isNotEmpty()) {
                        networkService.submitLogin(studentId, pass, tokens)
                        res = networkService.fetchAccountBook()
                    }
                }
                if (res.isSuccess) {
                    val freshList = res.getOrNull().orEmpty()
                    if (freshList.isNotEmpty()) {
                        _challansFlow.value = freshList
                        prefs.saveChallans(freshList)
                        val match = freshList.firstOrNull { it.challanNo == challan.challanNo }
                        if (match != null) {
                            networkService.syncCookiesToWebView()
                            return@withContext match
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "prepareChallanForPayment note: ${e.message}")
        }
        networkService.syncCookiesToWebView()
        challan
    }

    suspend fun downloadStudentECard(): Result<File> = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, "student_ecard.pdf")
        networkService.downloadStudentECard(target)
    }

    fun getProfileAvatarFile(): File? {
        val file = File(context.filesDir, "student_avatar.jpg")
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
        _challansFlow.value = emptyList()
        _subjectsFlow.value = emptyList()
        _studentProfileFlow.value = StudentProfile()
        try { java.io.File(context.filesDir, "student_avatar.jpg").delete() } catch (ignored: Exception) {}
        try { java.io.File(context.filesDir, "student_ecard.pdf").delete() } catch (ignored: Exception) {}
    }

    fun getPreferences(): PreferencesManager = prefs

    companion object {
        private const val TAG = "VuLmsRepository"
    }
}
