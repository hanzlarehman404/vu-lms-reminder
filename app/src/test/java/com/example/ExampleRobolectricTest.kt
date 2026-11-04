package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ActivityEntity
import com.example.data.model.ActivityType
import com.example.data.model.VuRawActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `test activity parsing and type detection`() {
        val raw = VuRawActivity(
            title = "CS301: Assignment# 1",
            coursecode = "CS301",
            semester = "Fall 2026",
            start = "2026,10,29",
            end = "2026,11,04",
            url = "/ActivityCalendar/OpenActivitySection.aspx?coursecode=CS301&ActivityType=Assignment",
            isExpired = "0"
        )
        val entity = raw.toActivityEntity()
        assertEquals("CS301", entity.courseCode)
        assertEquals("CS301: Assignment# 1", entity.title)
        assertEquals("Assignment", entity.activityType)
        assertTrue(entity.url.startsWith("https://vulms.vu.edu.pk"))
        assertNotNull(entity.id)
    }

    @Test
    fun `test activity available vs upcoming separation`() {
        val now = System.currentTimeMillis()
        val availableEntity = ActivityEntity(
            id = "act_avail",
            title = "CS301: Assignment# 1",
            courseCode = "CS301",
            semester = "Fall 2026",
            activityType = "Assignment",
            startDate = now - 3600000L,
            endDate = now + 86400000L,
            isExpired = false,
            url = "",
            colorHex = "#F56954"
        )
        assertTrue(availableEntity.isAvailable(now))
        assertFalse(availableEntity.isUpcoming(now))

        val upcomingEntity = ActivityEntity(
            id = "act_up",
            title = "CS525: Graded Quiz 2",
            courseCode = "CS525",
            semester = "Fall 2026",
            activityType = "Quiz",
            startDate = now + 86400000L,
            endDate = now + (3 * 86400000L),
            isExpired = false,
            url = "",
            colorHex = "#00A65A"
        )
        assertFalse(upcomingEntity.isAvailable(now))
        assertTrue(upcomingEntity.isUpcoming(now))
    }

    @Test
    fun `test html todo matching key generation`() {
        val entity = ActivityEntity(
            id = "act_key",
            title = "CS301: Assignment # 1",
            courseCode = "CS301",
            semester = "Fall 2026",
            activityType = "Assignment",
            startDate = 1000L,
            endDate = 2000L,
            isExpired = false,
            url = "",
            colorHex = "#F56954"
        )
        assertEquals("CS301_assignment#1", entity.getMatchingKey())
    }

    @Test
    fun `test activity completion suppresses alarm calculations`() {
        val futureEnd = System.currentTimeMillis() + (48 * 3600 * 1000L)
        val entity = ActivityEntity(
            id = "test_1",
            title = "CS525: Graded Quiz 1",
            courseCode = "CS525",
            semester = "Fall 2026",
            activityType = "Quiz",
            startDate = System.currentTimeMillis(),
            endDate = futureEnd,
            isExpired = false,
            isCompleted = true,
            url = "",
            colorHex = "#00A65A"
        )
        assertFalse(entity.isOverdue())
        assertTrue(entity.isCompleted)
    }

    @Test
    fun `test exclusive calendar end date calculates previous day at 23 59 59`() {
        // User rule: "end": "2026,11,06" means deadline is 2026-11-05 23:59:59
        val raw = VuRawActivity(
            title = "CS301: Assignment# 1",
            coursecode = "CS301",
            semester = "Fall 2026",
            start = "2026,10,29",
            end = "2026,11,06"
        )
        val entity = raw.toActivityEntity()

        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = entity.endDate
        }
        assertEquals(2026, cal.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.NOVEMBER, cal.get(java.util.Calendar.MONTH))
        assertEquals(5, cal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(23, cal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(java.util.Calendar.MINUTE))
        assertEquals(59, cal.get(java.util.Calendar.SECOND))
    }

    @Test
    fun `test update info certificate sha256 deserialization`() {
        val json = """
            {
              "version": "2.0.0",
              "versionCode": 2,
              "minimumSupportedVersion": 1,
              "forceUpdate": false,
              "releaseName": "Major v2.0.0 Update",
              "releaseNotes": ["Exclusive end dates", "Developer verification"],
              "downloadUrl": "https://github.com/hanzlarehman404/vu-lms-reminder/releases/download/v2.0.0/vu-lms-reminder-v2.0.0.apk",
              "sha256": "66db0530c949cd67d07ee5628b2c807dae74881126756bcb72d0dd3400fade32",
              "certificate_sha256": "27:AD:82:EF:6C:AA:E3:B7:0D:2C:A3:D9:F8:45:E2:9B:AA:04:C0:A4:5D:2A:EE:F7:AC:ED:64:24:6B:0F:99:74",
              "publishedAt": "2026-11-03T00:00:00Z"
            }
        """.trimIndent()

        val moshi = com.squareup.moshi.Moshi.Builder().build()
        val adapter = moshi.adapter(com.example.data.model.AppUpdateInfo::class.java)
        val parsed = adapter.fromJson(json)

        assertNotNull(parsed)
        assertEquals("2.0.0", parsed?.version)
        assertEquals(2, parsed?.versionCode)
        assertEquals("27:AD:82:EF:6C:AA:E3:B7:0D:2C:A3:D9:F8:45:E2:9B:AA:04:C0:A4:5D:2A:EE:F7:AC:ED:64:24:6B:0F:99:74", parsed?.certificateSha256)
        assertEquals("66db0530c949cd67d07ee5628b2c807dae74881126756bcb72d0dd3400fade32", parsed?.sha256)
    }
}
