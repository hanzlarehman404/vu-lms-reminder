package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@JsonClass(generateAdapter = true)
data class VuRawActivity(
    @Json(name = "title") val title: String? = null,
    @Json(name = "coursecode") val coursecode: String? = null,
    @Json(name = "Semester") val semester: String? = null,
    @Json(name = "start") val start: String? = null,
    @Json(name = "end") val end: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "IsExpired") val isExpired: String? = null,
    @Json(name = "backgroundColor") val backgroundColor: String? = null,
    @Json(name = "borderColor") val borderColor: String? = null
) {
    fun toActivityEntity(): ActivityEntity {
        val cleanTitle = title?.trim().orEmpty().ifEmpty { "Activity" }
        var cleanCourse = coursecode?.trim().orEmpty().uppercase()

        // Extract coursecode from title if empty (e.g. "CS301: Assignment# 1")
        if (cleanCourse.isEmpty() && cleanTitle.contains(":")) {
            cleanCourse = cleanTitle.substringBefore(":").trim().uppercase()
        }
        if (cleanCourse.isEmpty() && cleanTitle.startsWith("Challan", ignoreCase = true)) {
            cleanCourse = "FEE"
        }

        val startMillis = parseDateToMillis(start)
        val endMillis = parseDateToMillis(end, isEnd = true)
        val expired = (isExpired == "1") || (endMillis < System.currentTimeMillis())

        val id = ActivityEntity.generateId(cleanCourse, cleanTitle, endMillis)
        val fullUrl = if (!url.isNullOrBlank() && !url.startsWith("http")) {
            "https://vulms.vu.edu.pk${if (url.startsWith("/")) "" else "/"}$url"
        } else {
            url.orEmpty()
        }

        val type = ActivityType.fromString(null, cleanTitle).displayName
        val color = borderColor ?: "#0D2F62"

        return ActivityEntity(
            id = id,
            title = cleanTitle,
            courseCode = cleanCourse,
            semester = semester.orEmpty(),
            activityType = type,
            startDate = startMillis,
            endDate = endMillis,
            isExpired = expired,
            isCompleted = false,
            url = fullUrl,
            colorHex = color,
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    companion object {
        fun parseDateToMillis(dateStr: String?, isEnd: Boolean = false): Long {
            if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
            val cleaned = dateStr.trim().replace("/", "-")

            // Format 1: "2026,10,29"
            if (cleaned.contains(",")) {
                val parts = cleaned.split(",").map { it.trim() }
                if (parts.size >= 3) {
                    try {
                        val year = parts[0].toInt()
                        val month = parts[1].toInt() - 1 // 0-based in Calendar
                        val day = parts[2].toInt()
                        val cal = java.util.Calendar.getInstance()
                        cal.set(java.util.Calendar.YEAR, year)
                        cal.set(java.util.Calendar.MONTH, month)
                        cal.set(java.util.Calendar.DAY_OF_MONTH, day)
                        if (isEnd) {
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                            cal.set(java.util.Calendar.MINUTE, 59)
                            cal.set(java.util.Calendar.SECOND, 59)
                        } else {
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            cal.set(java.util.Calendar.MINUTE, 0)
                            cal.set(java.util.Calendar.SECOND, 0)
                        }
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        return cal.timeInMillis
                    } catch (e: Exception) {
                        // fallback
                    }
                }
            }

            // Format 2: "yyyy-MM-dd"
            val formats = listOf("yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.US)
                    sdf.timeZone = TimeZone.getDefault()
                    val parsed = sdf.parse(cleaned)
                    if (parsed != null) {
                        val cal = java.util.Calendar.getInstance()
                        cal.time = parsed
                        if (isEnd && !cleaned.contains(":")) {
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                            cal.set(java.util.Calendar.MINUTE, 59)
                            cal.set(java.util.Calendar.SECOND, 59)
                        }
                        return cal.timeInMillis
                    }
                } catch (ignored: Exception) {}
            }

            return System.currentTimeMillis()
        }
    }
}
