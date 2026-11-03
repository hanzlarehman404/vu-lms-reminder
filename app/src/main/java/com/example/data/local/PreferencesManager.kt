package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vu_lms_app_prefs", Context.MODE_PRIVATE)

    private val _studentIdFlow = MutableStateFlow(getStudentId())
    val studentIdFlow: StateFlow<String> = _studentIdFlow.asStateFlow()

    private val _isLoggedInFlow = MutableStateFlow(isLoggedIn())
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    private val _privacyAcceptedFlow = MutableStateFlow(isPrivacyAccepted())
    val privacyAcceptedFlow: StateFlow<Boolean> = _privacyAcceptedFlow.asStateFlow()

    private val _lastSyncFlow = MutableStateFlow(getLastSyncTime())
    val lastSyncFlow: StateFlow<Long> = _lastSyncFlow.asStateFlow()

    private val _appThemeFlow = MutableStateFlow(getAppTheme())
    val appThemeFlow: StateFlow<String> = _appThemeFlow.asStateFlow()

    fun getStudentId(): String = prefs.getString(KEY_STUDENT_ID, "").orEmpty()
    fun getPassword(): String = prefs.getString(KEY_PASSWORD, "").orEmpty()
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    fun isPrivacyAccepted(): Boolean = prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)
    fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)

    fun getMorningSyncTime(): String = prefs.getString(KEY_MORNING_TIME, "09:00") ?: "09:00"
    fun getMiddayTime(): String = prefs.getString(KEY_MIDDAY_TIME, "14:00") ?: "14:00"
    fun getEveningReminderTime(): String = prefs.getString(KEY_EVENING_TIME, "21:00") ?: "21:00"
    fun isNextDayRoutineEnabled(): Boolean = prefs.getBoolean(KEY_NEXT_DAY_ROUTINE, true)
    fun isMiddayRoutineEnabled(): Boolean = prefs.getBoolean(KEY_MIDDAY_ROUTINE, true)

    fun isNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    fun isUrgentAlarmEnabled(): Boolean = prefs.getBoolean(KEY_URGENT_ALARM_ENABLED, true)
    fun isAlarmSoundEnabled(): Boolean = prefs.getBoolean(KEY_ALARM_SOUND, true)
    fun isVibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION, true)
    fun getNotificationRingtone(): String = prefs.getString(KEY_NOTIFICATION_RINGTONE, "alarm") ?: "alarm"
    fun getAppTheme(): String = prefs.getString(KEY_APP_THEME, "system") ?: "system"

    fun saveLogin(studentId: String, pass: String) {
        prefs.edit()
            .putString(KEY_STUDENT_ID, studentId.trim().uppercase())
            .putString(KEY_PASSWORD, pass)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
        _studentIdFlow.value = studentId.trim().uppercase()
        _isLoggedInFlow.value = true
    }

    fun clearLogin() {
        prefs.edit()
            .remove(KEY_STUDENT_ID)
            .remove(KEY_PASSWORD)
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .apply()
        _studentIdFlow.value = ""
        _isLoggedInFlow.value = false
    }

    fun setPrivacyAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY_ACCEPTED, accepted).apply()
        _privacyAcceptedFlow.value = accepted
    }

    fun setLastSyncTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply()
        _lastSyncFlow.value = timestamp
    }

    fun setRoutineTimes(morningTime: String, eveningTime: String) {
        prefs.edit()
            .putString(KEY_MORNING_TIME, morningTime)
            .putString(KEY_EVENING_TIME, eveningTime)
            .apply()
    }

    fun setRoutineTimes(morningTime: String, middayTime: String, eveningTime: String) {
        prefs.edit()
            .putString(KEY_MORNING_TIME, morningTime)
            .putString(KEY_MIDDAY_TIME, middayTime)
            .putString(KEY_EVENING_TIME, eveningTime)
            .apply()
    }

    fun setMiddayRoutineEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MIDDAY_ROUTINE, enabled).apply()
    }

    fun setNextDayRoutineEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NEXT_DAY_ROUTINE, enabled).apply()
    }

    fun setNotificationRingtone(ringtone: String) {
        prefs.edit().putString(KEY_NOTIFICATION_RINGTONE, ringtone).apply()
    }

    fun setNotificationSettings(
        enabled: Boolean,
        urgentAlarm: Boolean,
        sound: Boolean,
        vibrate: Boolean
    ) {
        prefs.edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
            .putBoolean(KEY_URGENT_ALARM_ENABLED, urgentAlarm)
            .putBoolean(KEY_ALARM_SOUND, sound)
            .putBoolean(KEY_VIBRATION, vibrate)
            .apply()
    }

    fun setAppTheme(theme: String) {
        prefs.edit().putString(KEY_APP_THEME, theme).apply()
        _appThemeFlow.value = theme
    }

    companion object {
        private const val KEY_STUDENT_ID = "student_id"
        private const val KEY_PASSWORD = "student_password"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_PRIVACY_ACCEPTED = "privacy_policy_accepted"
        private const val KEY_LAST_SYNC = "last_sync_time"

        private const val KEY_MORNING_TIME = "routine_morning_time" // e.g. "09:00"
        private const val KEY_MIDDAY_TIME = "routine_midday_time" // e.g. "14:00"
        private const val KEY_EVENING_TIME = "routine_evening_time" // e.g. "21:00"
        private const val KEY_NEXT_DAY_ROUTINE = "next_day_routine_enabled"
        private const val KEY_MIDDAY_ROUTINE = "midday_routine_enabled"

        private const val KEY_NOTIFICATIONS_ENABLED = "notif_enabled"
        private const val KEY_URGENT_ALARM_ENABLED = "urgent_alarm_enabled"
        private const val KEY_ALARM_SOUND = "alarm_sound_enabled"
        private const val KEY_VIBRATION = "alarm_vibrate_enabled"
        private const val KEY_NOTIFICATION_RINGTONE = "notification_ringtone_type"
        private const val KEY_APP_THEME = "app_theme_mode" // "system", "light", "dark"
    }
}
