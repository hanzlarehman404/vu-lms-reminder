package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val courseCode: String,
    val semester: String,
    val activityType: String,
    val startDate: Long,
    val endDate: Long,
    val isExpired: Boolean,
    val isCompleted: Boolean = false,
    val url: String,
    val colorHex: String,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    /**
     * An activity is available if its opening/start date has arrived and it hasn't expired.
     */
    fun isAvailable(currentTime: Long = System.currentTimeMillis()): Boolean {
        return startDate <= currentTime && endDate >= currentTime
    }

    /**
     * An activity is upcoming if its opening date is in the future.
     */
    fun isUpcoming(currentTime: Long = System.currentTimeMillis()): Boolean {
        return startDate > currentTime
    }

    fun isDueToday(currentTime: Long = System.currentTimeMillis()): Boolean {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return sdf.format(Date(endDate)) == sdf.format(Date(currentTime))
    }

    fun isDueTomorrow(currentTime: Long = System.currentTimeMillis()): Boolean {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val tomorrow = currentTime + (24 * 60 * 60 * 1000)
        return sdf.format(Date(endDate)) == sdf.format(Date(tomorrow))
    }

    fun isDueThisWeek(currentTime: Long = System.currentTimeMillis()): Boolean {
        val weekEnd = currentTime + (7 * 24 * 60 * 60 * 1000)
        return endDate in currentTime..weekEnd
    }

    fun isOverdue(currentTime: Long = System.currentTimeMillis()): Boolean {
        return !isCompleted && endDate < currentTime
    }

    fun remainingTimeMillis(currentTime: Long = System.currentTimeMillis()): Long {
        return (endDate - currentTime).coerceAtLeast(0)
    }

    fun daysUntilStart(currentTime: Long = System.currentTimeMillis()): Long {
        return ((startDate - currentTime) / (24 * 3600 * 1000L)).coerceAtLeast(0)
    }

    /**
     * Normalizes course and title for matching against HTML ToDo tables.
     * Example: "CS301: Assignment# 1" -> course="CS301", titleKey="assignment#1"
     */
    fun getMatchingKey(): String {
        val cleanCourse = courseCode.trim().uppercase().replace(" ", "")
        val titleOnly = if (title.contains(":")) title.substringAfter(":").trim() else title.trim()
        val cleanTitle = titleOnly.lowercase().replace(" ", "")
        return "${cleanCourse}_$cleanTitle"
    }

    companion object {
        fun generateId(courseCode: String, title: String, endDateMillis: Long): String {
            val raw = "${courseCode.trim().uppercase()}_${title.trim().lowercase()}_$endDateMillis"
            return try {
                val md = MessageDigest.getInstance("MD5")
                val digest = md.digest(raw.toByteArray())
                digest.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                raw.hashCode().toString()
            }
        }
    }
}
