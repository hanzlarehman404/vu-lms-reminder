package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import com.example.data.model.ActivityEntity
import com.example.data.model.RemoteNotificationItem
import com.example.receiver.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationToneTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vu_lms_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = PreferencesManager(context)
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Test
    fun `default notification tone uses system default channel key`() {
        assertEquals("", prefs.getNotificationToneUri())
        assertEquals(PreferencesManager.DEFAULT_NOTIFICATION_TONE_NAME, prefs.getNotificationToneName())
        assertEquals("default", prefs.resolveNotificationChannelKey())
    }

    @Test
    fun `custom notification tone rotates channel key and reset restores default`() {
        val customUri = "content://media/internal/audio/media/42"
        prefs.setNotificationTone(customUri, "Crystal Bell")

        assertEquals(customUri, prefs.getNotificationToneUri())
        assertEquals("Crystal Bell", prefs.getNotificationToneName())
        val customKey = prefs.resolveNotificationChannelKey()
        assertNotEquals("default", customKey)
        assertNotEquals("silent", customKey)
        assertEquals(customKey, prefs.resolveNotificationChannelKey())

        // Disabling sound switches active channel key to silent without erasing selected tone metadata
        prefs.setNotificationSettings(
            enabled = true,
            urgentAlarm = true,
            sound = false,
            vibrate = true
        )
        assertEquals("silent", prefs.resolveNotificationChannelKey())
        assertEquals(customUri, prefs.getNotificationToneUri())

        // Re-enabling sound and resetting restores default key and name
        prefs.setNotificationSettings(
            enabled = true,
            urgentAlarm = true,
            sound = true,
            vibrate = true
        )
        prefs.resetNotificationTone()
        assertEquals("", prefs.getNotificationToneUri())
        assertEquals(PreferencesManager.DEFAULT_NOTIFICATION_TONE_NAME, prefs.getNotificationToneName())
        assertEquals("default", prefs.resolveNotificationChannelKey())
    }

    @Test
    fun `syncNotificationChannels updates notification channels and keeps urgent alarm untouched`() {
        val initialHelper = NotificationHelper(context)
        initialHelper.syncNotificationChannels()

        val initialDeadlineId = "${NotificationHelper.CHANNEL_DEADLINE_ID}_default"
        val initialAnnouncementId = "${NotificationHelper.CHANNEL_REMOTE_ANNOUNCEMENTS_ID}_default"
        assertNotNull(notificationManager.getNotificationChannel(initialDeadlineId))
        assertNotNull(notificationManager.getNotificationChannel(initialAnnouncementId))

        // Pick custom ringtone and sync
        val customUriStr = "content://media/internal/audio/media/99"
        prefs.setNotificationTone(customUriStr, "Galaxy Chime")
        initialHelper.syncNotificationChannels()

        val newKey = prefs.resolveNotificationChannelKey()
        val updatedDeadlineId = "${NotificationHelper.CHANNEL_DEADLINE_ID}_$newKey"
        val updatedAnnouncementId = "${NotificationHelper.CHANNEL_REMOTE_ANNOUNCEMENTS_ID}_$newKey"

        val updatedDeadlineChannel = notificationManager.getNotificationChannel(updatedDeadlineId)
        val updatedAnnouncementChannel = notificationManager.getNotificationChannel(updatedAnnouncementId)
        assertNotNull(updatedDeadlineChannel)
        assertNotNull(updatedAnnouncementChannel)
        assertEquals(Uri.parse(customUriStr), updatedDeadlineChannel?.sound)
        assertEquals(Uri.parse(customUriStr), updatedAnnouncementChannel?.sound)

        // Obsolete default channels should be cleaned up automatically
        assertNull(notificationManager.getNotificationChannel(initialDeadlineId))
        assertNull(notificationManager.getNotificationChannel(initialAnnouncementId))

        // Full-screen urgent alarm channel stays on its own fixed ID & alarm sound
        val urgentChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_URGENT_ID)
        assertNotNull(urgentChannel)
        val expectedAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (expectedAlarmUri != null) {
            assertEquals(expectedAlarmUri, urgentChannel?.sound)
        } else {
            assertTrue(urgentChannel?.id == NotificationHelper.CHANNEL_URGENT_ID)
        }
    }

    @Test
    fun `all notifications use custom vu bell icon and tint red when deadlines are due today`() {
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val helper = NotificationHelper(context)
        val shadowNm = Shadows.shadowOf(notificationManager)
        val now = System.currentTimeMillis()

        val futureActivity = ActivityEntity(
            id = "future-1",
            title = "Assignment 2",
            courseCode = "CS301",
            semester = "Fall 2026",
            activityType = "Assignment",
            startDate = now - 3_600_000L,
            endDate = now + 3 * 24 * 3_600_000L,
            isExpired = false,
            url = "https://vulms.vu.edu.pk",
            colorHex = "#36A3F7"
        )
        helper.showDeadlineNotification(futureActivity, hoursRemaining = 72, isUrgent = false)
        val futureNotif = shadowNm.getNotification(futureActivity.id.hashCode())
        assertNotNull(futureNotif)
        assertEquals(R.drawable.ic_notification_vu_bell, futureNotif.smallIcon.resId)
        assertEquals(NotificationHelper.COLOR_INFO_CYAN, futureNotif.color)

        val todayActivity = futureActivity.copy(
            id = "today-1",
            endDate = now + 2 * 3_600_000L
        )
        helper.showDeadlineNotification(todayActivity, hoursRemaining = 2, isUrgent = true)
        val todayNotif = shadowNm.getNotification(todayActivity.id.hashCode())
        assertNotNull(todayNotif)
        assertEquals(R.drawable.ic_notification_vu_bell, todayNotif.smallIcon.resId)
        assertEquals(NotificationHelper.COLOR_URGENT_RED, todayNotif.color)

        // Morning & Midday: Cyan when 0 due today, Red when >= 1 due today
        helper.showMorningBriefingNotification(todayDueCount = 0, totalAvailableCount = 4, tomorrowCount = 1)
        val calmMorning = shadowNm.getNotification(10001)
        assertEquals(R.drawable.ic_notification_vu_bell, calmMorning.smallIcon.resId)
        assertEquals(NotificationHelper.COLOR_INFO_CYAN, calmMorning.color)

        helper.showMorningBriefingNotification(todayDueCount = 2, totalAvailableCount = 4, tomorrowCount = 1)
        val dueTodayMorning = shadowNm.getNotification(10001)
        assertEquals(R.drawable.ic_notification_vu_bell, dueTodayMorning.smallIcon.resId)
        assertEquals(NotificationHelper.COLOR_URGENT_RED, dueTodayMorning.color)

        helper.showMiddayCheckNotification(availableActivitiesCount = 3, todayDueCount = 0)
        val calmMidday = shadowNm.getNotification(10002)
        assertEquals(R.drawable.ic_notification_vu_bell, calmMidday.smallIcon.resId)
        assertEquals(NotificationHelper.COLOR_INFO_CYAN, calmMidday.color)

        helper.showMiddayCheckNotification(availableActivitiesCount = 3, todayDueCount = 1)
        val dueTodayMidday = shadowNm.getNotification(10002)
        assertEquals(R.drawable.ic_notification_vu_bell, dueTodayMidday.smallIcon.resId)
        assertEquals(NotificationHelper.COLOR_URGENT_RED, dueTodayMidday.color)

        // Sync confirmation & remote announcements use Cyan + VU Bell
        helper.showResyncConfirmationNotification(activitiesCount = 3)
        val syncNotif = shadowNm.getNotification(10099)
        assertEquals(R.drawable.ic_notification_vu_bell, syncNotif.smallIcon.resId)
        assertEquals(NotificationHelper.COLOR_INFO_CYAN, syncNotif.color)

        val announcement = RemoteNotificationItem(
            id = "notice-1",
            title = "Semester Notice",
            message = "Check updated schedule."
        )
        assertTrue(helper.showRemoteAnnouncementNotification(announcement))
        val noticeNotif = shadowNm.getNotification(20000 + (announcement.id.hashCode() and 0x7FFF))
        assertEquals(R.drawable.ic_notification_vu_bell, noticeNotif.smallIcon.resId)
        assertEquals(NotificationHelper.COLOR_INFO_CYAN, noticeNotif.color)
    }
}
