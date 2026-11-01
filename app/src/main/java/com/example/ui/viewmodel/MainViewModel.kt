package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.model.ActivityEntity
import com.example.data.model.AppUpdateInfo
import com.example.data.repository.VuLmsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    data class Loading(val step: String) : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

sealed class ManualUpdateResult {
    data class UpToDate(val version: String, val releaseName: String) : ManualUpdateResult()
    data class Error(val message: String) : ManualUpdateResult()
}

data class DashboardStats(
    val todayCount: Int = 0,
    val tomorrowCount: Int = 0,
    val thisWeekCount: Int = 0,
    val overdueCount: Int = 0,
    val completedCount: Int = 0,
    val availableCount: Int = 0,
    val upcomingCount: Int = 0,
    val totalPending: Int = 0,
    val nearestUpcoming: ActivityEntity? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VuLmsRepository(application)
    private val prefs: PreferencesManager = repository.getPreferences()

    val isLoggedIn: StateFlow<Boolean> = prefs.isLoggedInFlow
    val studentId: StateFlow<String> = prefs.studentIdFlow
    val isPrivacyAccepted: StateFlow<Boolean> = prefs.privacyAcceptedFlow
    val lastSyncTime: StateFlow<Long> = prefs.lastSyncFlow
    val appTheme: StateFlow<String> = prefs.appThemeFlow

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Dashboard, 1: Calendar, 2: Settings
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>("All")
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    // Flag to separate Available vs Upcoming activities
    private val _showUpcomingOnly = MutableStateFlow(false)
    val showUpcomingOnly: StateFlow<Boolean> = _showUpcomingOnly.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _manualUpdateResult = MutableStateFlow<ManualUpdateResult?>(null)
    val manualUpdateResult: StateFlow<ManualUpdateResult?> = _manualUpdateResult.asStateFlow()

    private val _studentAvatarFile = MutableStateFlow<java.io.File?>(repository.getProfileAvatarFile())
    val studentAvatarFile: StateFlow<java.io.File?> = _studentAvatarFile.asStateFlow()

    val allActivities: StateFlow<List<ActivityEntity>> = repository.allActivities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<DashboardStats> = allActivities.map { list ->
        val now = System.currentTimeMillis()
        val available = list.filter { it.isAvailable(now) }
        val upcoming = list.filter { it.isUpcoming(now) }
        val pendingAvailable = available.filter { !it.isCompleted }
        val nearest = pendingAvailable.minByOrNull { it.endDate }

        DashboardStats(
            todayCount = pendingAvailable.count { it.isDueToday(now) },
            tomorrowCount = pendingAvailable.count { it.isDueTomorrow(now) },
            thisWeekCount = pendingAvailable.count { it.isDueThisWeek(now) },
            overdueCount = list.count { it.isOverdue(now) },
            completedCount = list.count { it.isCompleted },
            availableCount = available.size,
            upcomingCount = upcoming.size,
            totalPending = pendingAvailable.size,
            nearestUpcoming = nearest
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    val filteredActivities: StateFlow<List<ActivityEntity>> = combine(
        allActivities,
        _selectedCategory,
        _showUpcomingOnly,
        _searchQuery
    ) { list, category, upcomingOnly, query ->
        val now = System.currentTimeMillis()
        list.filter { item ->
            // Availability filter: If upcomingOnly is true, show future start dates; otherwise show available activities
            val matchesTimeline = if (upcomingOnly) {
                item.isUpcoming(now)
            } else {
                // By default show available activities (or completed/overdue in this cycle)
                item.startDate <= now || item.isCompleted
            }

            val matchesCategory = when (category) {
                "All", null -> true
                "Today" -> item.isDueToday(now) && !item.isCompleted
                "Tomorrow" -> item.isDueTomorrow(now) && !item.isCompleted
                "This Week" -> item.isDueThisWeek(now) && !item.isCompleted
                "Overdue" -> item.isOverdue(now)
                "Completed" -> item.isCompleted
                else -> item.activityType.equals(category, ignoreCase = true)
            }

            val matchesQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.courseCode.contains(query, ignoreCase = true)

            matchesTimeline && matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (prefs.isLoggedIn()) {
            syncNow(silent = true)
        }
        checkForAppUpdates()
    }

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun toggleUpcomingOnly(upcoming: Boolean) {
        _showUpcomingOnly.value = upcoming
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun acceptPrivacy() {
        prefs.setPrivacyAccepted(true)
    }

    fun login(studentId: String, pass: String) {
        if (studentId.isBlank() || pass.isBlank()) {
            _loginState.value = LoginUiState.Error("Please enter your VU Student ID and Password")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading("Negotiating ASP.NET session with VU LMS...")
            val result = repository.login(studentId, pass)
            if (result.isSuccess) {
                _studentAvatarFile.value = repository.getProfileAvatarFile()
                _loginState.value = LoginUiState.Success
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Login failed. Check your credentials."
                _loginState.value = LoginUiState.Error(err)
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginUiState.Idle
    }

    fun syncNow(silent: Boolean = false) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            if (!silent) _syncMessage.value = "Syncing & auto-marking completed tasks..."
            val result = repository.syncActivities()
            _isSyncing.value = false
            if (result.isSuccess) {
                _studentAvatarFile.value = repository.getProfileAvatarFile()
                val count = result.getOrNull() ?: 0
                if (!silent) _syncMessage.value = "Synced $count activities & updated completed statuses"
            } else {
                if (!silent) {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "Sync failed"
                    _syncMessage.value = "Sync failed: $err"
                }
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun toggleActivityCompleted(activityId: String, completed: Boolean) {
        viewModelScope.launch {
            repository.toggleActivityCompletion(activityId, completed)
        }
    }

    fun markAllAvailableAsDone() {
        viewModelScope.launch {
            repository.markAllAvailableCompleted()
            _syncMessage.value = "All available activities marked as completed"
        }
    }

    fun resetAllCompletions() {
        viewModelScope.launch {
            repository.resetAllCompletions()
            _syncMessage.value = "All completion statuses reset"
        }
    }

    fun checkForAppUpdates(isManual: Boolean = false) {
        viewModelScope.launch {
            if (isManual) {
                _isCheckingUpdate.value = true
                _manualUpdateResult.value = null
            }
            val result = repository.checkForUpdate()
            if (isManual) {
                _isCheckingUpdate.value = false
            }
            if (result.isSuccess) {
                val info = result.getOrNull()
                // Current versionCode is 1
                if (info != null && info.versionCode > 1) {
                    _updateInfo.value = info
                } else if (isManual) {
                    _manualUpdateResult.value = ManualUpdateResult.UpToDate(
                        version = info?.version ?: "1.0.0",
                        releaseName = info?.releaseName.orEmpty()
                    )
                }
            } else if (isManual) {
                _manualUpdateResult.value = ManualUpdateResult.Error(
                    "Unable to check for updates right now. Kindly try again later or contact the developer."
                )
            }
        }
    }

    fun dismissManualUpdateResult() {
        _manualUpdateResult.value = null
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _studentAvatarFile.value = null
            _selectedTab.value = 0
            _loginState.value = LoginUiState.Idle
        }
    }

    fun setAppTheme(theme: String) {
        prefs.setAppTheme(theme)
    }

    fun setRoutineTimes(morning: String, evening: String) {
        prefs.setRoutineTimes(morning, evening)
        repository.getPreferences().setRoutineTimes(morning, evening)
    }

    fun setNextDayRoutineEnabled(enabled: Boolean) {
        prefs.setNextDayRoutineEnabled(enabled)
    }

    fun getPreferences(): PreferencesManager = prefs
}
