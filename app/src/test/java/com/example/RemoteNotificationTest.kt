package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import com.example.data.model.RemoteNotificationItem
import com.example.data.model.RemoteNotificationSchedule
import com.example.data.model.RemoteNotificationsFeed
import com.example.data.remote.VuLmsNetworkService
import com.example.data.repository.VuLmsRepository
import com.example.receiver.RemoteNotificationScheduler
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RemoteNotificationTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vu_lms_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "student_ecard.pdf").delete()
        File(context.filesDir, "BC230401234.pdf").delete()
        prefs = PreferencesManager(context)
    }

    @Test
    fun `notifications feed url targets dedicated notifications branch on GitHub`() {
        assertEquals(
            "https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/notifications/notifications.json",
            VuLmsNetworkService.NOTIFICATIONS_FEED_URL
        )
    }

    @Test
    fun `moshi parses notifications feed including status bar parameters`() {
        val sampleJson = """
            {
              "notifications": [
                {
                  "id": "major-v21",
                  "active": true,
                  "showBanner": true,
                  "showPopup": false,
                  "showNotificationBar": true,
                  "popupFrequency": "once_per_day",
                  "notificationBarFrequency": "interval",
                  "notificationBarCooldownMinutes": 180,
                  "priority": 50,
                  "severity": "major_update",
                  "badge": "UPCOMING",
                  "title": "v2.1 Coming Soon",
                  "bannerText": "v2.1 drops next week!",
                  "message": "Major improvements incoming.",
                  "highlights": ["Fast sync", "Exam countdown"],
                  "actionLabel": "Release Notes",
                  "actionUrl": "https://github.com/hanzlarehman404/vu-lms-reminder/releases",
                  "startsAt": "2026-11-10T00:00:00Z",
                  "expiresAt": "2026-11-30",
                  "minVersionCode": 1,
                  "maxVersionCode": 10
                }
              ]
            }
        """.trimIndent()

        val feed = moshi.adapter(RemoteNotificationsFeed::class.java).fromJson(sampleJson)!!
        assertEquals(1, feed.notifications.size)
        val item = feed.notifications.first()
        assertEquals("major-v21", item.id)
        assertTrue(item.active)
        assertTrue(item.showBanner)
        assertFalse(item.showPopup)
        assertTrue(item.showNotificationBar)
        assertEquals("interval", item.notificationBarFrequency)
        assertEquals(180, item.notificationBarCooldownMinutes)
        assertEquals(listOf("Fast sync", "Exam countdown"), item.highlights)
        assertEquals("v2.1 drops next week!", item.effectiveBannerLine())
    }

    @Test
    fun `schedule filter respects active flag start expiry and app version bounds`() {
        val nov15Noon = RemoteNotificationSchedule.parseUtcTimestampMillis("2026-11-15T12:00:00Z")!!

        val activeItem = RemoteNotificationItem(
            id = "notice-1",
            active = true,
            title = "Active Notice",
            startsAt = "2026-11-10T00:00:00Z",
            expiresAt = "2026-11-20",
            minVersionCode = 2,
            maxVersionCode = 5
        )
        assertTrue(activeItem.isCurrentlyEligible(nowMillis = nov15Noon, currentVersionCode = 2))
        assertFalse(activeItem.isCurrentlyEligible(nowMillis = nov15Noon, currentVersionCode = 1))
        assertFalse(activeItem.isCurrentlyEligible(nowMillis = nov15Noon, currentVersionCode = 6))

        val inactiveItem = activeItem.copy(active = false)
        assertFalse(inactiveItem.isCurrentlyEligible(nowMillis = nov15Noon, currentVersionCode = 2))

        val expiredItem = activeItem.copy(expiresAt = "2026-11-15T11:59:59Z")
        assertFalse(expiredItem.isCurrentlyEligible(nowMillis = nov15Noon, currentVersionCode = 2))

        val futureItem = activeItem.copy(startsAt = "2026-11-16T00:00:00Z")
        assertFalse(futureItem.isCurrentlyEligible(nowMillis = nov15Noon, currentVersionCode = 2))

        val inclusiveDateEnd = RemoteNotificationSchedule.parseUtcTimestampMillis("2026-11-15T23:59:00Z")!!
        val dateOnlyExpiryItem = activeItem.copy(expiresAt = "2026-11-15")
        assertTrue(dateOnlyExpiryItem.isCurrentlyEligible(nowMillis = inclusiveDateEnd, currentVersionCode = 2))
    }

    @Test
    fun `popup displays once per day by default and supports once_per_id`() {
        val day1Morning = RemoteNotificationSchedule.parseUtcTimestampMillis("2026-11-15T09:00:00Z")!!
        val day1Evening = RemoteNotificationSchedule.parseUtcTimestampMillis("2026-11-15T20:00:00Z")!!
        val day2Morning = RemoteNotificationSchedule.parseUtcTimestampMillis("2026-11-16T09:00:00Z")!!

        val dailyPopup = RemoteNotificationItem(
            id = "daily-banner-popup",
            active = true,
            showPopup = true,
            popupFrequency = RemoteNotificationItem.FREQUENCY_ONCE_PER_DAY,
            title = "Daily Reminder"
        )

        assertTrue(prefs.canShowNotificationPopup(dailyPopup, nowMillis = day1Morning))
        prefs.markNotificationPopupShown(dailyPopup.id, timestampMs = day1Morning)

        assertFalse(prefs.canShowNotificationPopup(dailyPopup, nowMillis = day1Evening))
        assertTrue(prefs.canShowNotificationPopup(dailyPopup, nowMillis = day2Morning))

        val oncePerIdPopup = dailyPopup.copy(
            id = "single-time-popup",
            popupFrequency = RemoteNotificationItem.FREQUENCY_ONCE_PER_ID
        )
        assertTrue(prefs.canShowNotificationPopup(oncePerIdPopup, nowMillis = day1Morning))
        prefs.markNotificationPopupShown(oncePerIdPopup.id, timestampMs = day1Morning)
        assertFalse(prefs.canShowNotificationPopup(oncePerIdPopup, nowMillis = day2Morning))
    }

    @Test
    fun `status bar notification respects once_per_id once_per_day interval and always parameters`() {
        val t0 = RemoteNotificationSchedule.parseUtcTimestampMillis("2026-11-15T09:00:00Z")!!
        val tPlus30Min = t0 + (30 * 60_000L)
        val tPlus60Min = t0 + (60 * 60_000L)
        val day2 = RemoteNotificationSchedule.parseUtcTimestampMillis("2026-11-16T09:00:00Z")!!

        val onceIdItem = RemoteNotificationItem(
            id = "bar-once-id",
            showNotificationBar = true,
            notificationBarFrequency = RemoteNotificationItem.BAR_FREQUENCY_ONCE_PER_ID,
            title = "Once"
        )
        assertEquals(
            1,
            RemoteNotificationScheduler.dispatchSystemNotificationsIfEligible(
                context = context,
                eligibleItems = listOf(onceIdItem),
                prefs = prefs,
                nowMillis = t0,
                postNotification = { true }
            )
        )
        // Next day -> still suppressed for once_per_id
        assertEquals(
            0,
            RemoteNotificationScheduler.dispatchSystemNotificationsIfEligible(
                context = context,
                eligibleItems = listOf(onceIdItem),
                prefs = prefs,
                nowMillis = day2,
                postNotification = { true }
            )
        )

        val intervalItem = RemoteNotificationItem(
            id = "bar-interval",
            showNotificationBar = true,
            notificationBarFrequency = RemoteNotificationItem.BAR_FREQUENCY_INTERVAL,
            notificationBarCooldownMinutes = 60,
            title = "Interval"
        )
        assertTrue(prefs.canShowNotificationBar(intervalItem, nowMillis = t0))
        prefs.markNotificationBarShown(intervalItem.id, timestampMs = t0)
        assertFalse(prefs.canShowNotificationBar(intervalItem, nowMillis = tPlus30Min))
        assertTrue(prefs.canShowNotificationBar(intervalItem, nowMillis = tPlus60Min))

        val dailyBarItem = RemoteNotificationItem(
            id = "bar-daily",
            showNotificationBar = true,
            notificationBarFrequency = RemoteNotificationItem.BAR_FREQUENCY_ONCE_PER_DAY,
            title = "Daily Bar"
        )
        assertTrue(prefs.canShowNotificationBar(dailyBarItem, nowMillis = t0))
        prefs.markNotificationBarShown(dailyBarItem.id, timestampMs = t0)
        assertFalse(prefs.canShowNotificationBar(dailyBarItem, nowMillis = tPlus60Min))
        assertTrue(prefs.canShowNotificationBar(dailyBarItem, nowMillis = day2))
    }

    @Test
    fun `pdf validator rejects corrupted html and repository stores valid ecard by roll number`() {
        val htmlFakePdf = "<!DOCTYPE html><html><body>Login required</body></html>".padEnd(200, ' ')
            .toByteArray()
        assertFalse(VuLmsNetworkService.isValidPdfBytes(htmlFakePdf))

        val validPdfBytes = ("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n" + "0".repeat(120))
            .toByteArray()
        assertTrue(VuLmsNetworkService.isValidPdfBytes(validPdfBytes))

        prefs.saveLogin("bc230401234", "secret")
        val repo = VuLmsRepository(context = context, prefs = prefs)

        // Legacy corrupted file is automatically purged and rejected
        val legacyCorrupted = File(context.filesDir, "student_ecard.pdf")
        legacyCorrupted.writeBytes(htmlFakePdf)
        assertNull(repo.getStudentECardFile())
        assertFalse(legacyCorrupted.exists())

        // Valid roll-number PDF is preserved cleanly for offline viewing without duplicates
        val rollPdf = File(context.filesDir, "BC230401234.pdf")
        rollPdf.writeBytes(validPdfBytes)
        val resolved = repo.getStudentECardFile()
        assertNotNull(resolved)
        assertEquals("BC230401234.pdf", resolved?.name)
    }
}
