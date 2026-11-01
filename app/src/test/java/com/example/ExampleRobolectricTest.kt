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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VU LMS Reminder", appName)
    }

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
}
