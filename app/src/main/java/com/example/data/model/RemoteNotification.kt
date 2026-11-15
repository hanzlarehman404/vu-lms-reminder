package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@JsonClass(generateAdapter = true)
data class RemoteNotificationsFeed(
    @Json(name = "notifications") val notifications: List<RemoteNotificationItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RemoteNotificationItem(
    @Json(name = "id") val id: String = "",
    @Json(name = "active") val active: Boolean = true,
    @Json(name = "showBanner") val showBanner: Boolean = true,
    @Json(name = "showPopup") val showPopup: Boolean = true,
    @Json(name = "popupFrequency") val popupFrequency: String = FREQUENCY_ONCE_PER_DAY,
    @Json(name = "priority") val priority: Int = 0,
    @Json(name = "severity") val severity: String = SEVERITY_INFO,
    @Json(name = "badge") val badge: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "bannerText") val bannerText: String = "",
    @Json(name = "message") val message: String = "",
    @Json(name = "highlights") val highlights: List<String> = emptyList(),
    @Json(name = "actionLabel") val actionLabel: String = "",
    @Json(name = "actionUrl") val actionUrl: String = "",
    @Json(name = "startsAt") val startsAt: String = "",
    @Json(name = "expiresAt") val expiresAt: String = "",
    @Json(name = "minVersionCode") val minVersionCode: Int? = null,
    @Json(name = "maxVersionCode") val maxVersionCode: Int? = null
) {
    fun isCurrentlyEligible(
        nowMillis: Long = System.currentTimeMillis(),
        currentVersionCode: Int = 1
    ): Boolean {
        if (!active) return false
        if (id.isBlank()) return false
        if (title.isBlank() && message.isBlank() && bannerText.isBlank()) return false

        if (minVersionCode != null && currentVersionCode < minVersionCode) return false
        if (maxVersionCode != null && currentVersionCode > maxVersionCode) return false

        if (startsAt.isNotBlank()) {
            val startMs = RemoteNotificationSchedule.parseUtcTimestampMillis(startsAt, isEndBoundary = false)
            if (startMs != null && nowMillis < startMs) return false
        }

        if (expiresAt.isNotBlank()) {
            val endMs = RemoteNotificationSchedule.parseUtcTimestampMillis(expiresAt, isEndBoundary = true)
            if (endMs != null && nowMillis > endMs) return false
        }

        return true
    }

    fun effectiveBannerLine(): String {
        if (bannerText.isNotBlank()) return bannerText.trim()
        return listOf(title.trim(), message.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" — ")
    }

    fun effectiveBadge(): String {
        if (badge.isNotBlank()) return badge.trim().uppercase(Locale.US)
        return when (severity.lowercase(Locale.US)) {
            SEVERITY_MAJOR_UPDATE -> "UPCOMING"
            SEVERITY_WARNING -> "ADVISORY"
            SEVERITY_CRITICAL -> "URGENT"
            else -> "NOTICE"
        }
    }

    fun formattedExpiryDisplay(): String? {
        if (expiresAt.isBlank()) return null
        val millis = RemoteNotificationSchedule.parseUtcTimestampMillis(expiresAt, isEndBoundary = true)
            ?: return null
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.US)
        return fmt.format(Date(millis))
    }

    companion object {
        const val FREQUENCY_ONCE_PER_DAY = "once_per_day"
        const val FREQUENCY_ONCE_PER_ID = "once_per_id"
        const val FREQUENCY_ALWAYS = "always"

        const val SEVERITY_INFO = "info"
        const val SEVERITY_MAJOR_UPDATE = "major_update"
        const val SEVERITY_WARNING = "warning"
        const val SEVERITY_CRITICAL = "critical"
    }
}

object RemoteNotificationSchedule {
    private val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")
    private val dateTimePatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm'Z'",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm"
    )

    fun parseUtcTimestampMillis(raw: String, isEndBoundary: Boolean = false): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        for (pattern in dateTimePatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = utcTimeZone
                    isLenient = false
                }
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }

        // Date-only shorthand (e.g. "2026-11-25"): start = 00:00:00Z, end = 23:59:59Z
        if (Regex("""^\d{4}-\d{2}-\d{2}$""").matches(trimmed)) {
            val suffix = if (isEndBoundary) "T23:59:59Z" else "T00:00:00Z"
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = utcTimeZone
                    isLenient = false
                }
                sdf.parse(trimmed + suffix)?.time
            } catch (_: Exception) {
                null
            }
        }

        return null
    }

    fun localCalendarDayKey(timestampMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(timestampMs))
    }
}
