package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import com.example.data.model.ChallanEntity
import com.example.data.model.StudentProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersistenceAndChallanTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vu_lms_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `parseChallanDateMillis supports multiple VU date formats and defaults to end of day`() {
        val formats = listOf(
            "Nov 25, 2026",
            "25-Nov-2026",
            "25 Nov 2026",
            "25/11/2026",
            "2026-11-25",
            "Due: 25-Nov-2026"
        )
        for (raw in formats) {
            val millis = ChallanEntity.parseChallanDateMillis(raw)
            assertNotNull("Expected non-null timestamp for '$raw'", millis)
            val cal = Calendar.getInstance().apply { timeInMillis = millis!! }
            assertEquals(2026, cal.get(Calendar.YEAR))
            assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
            assertEquals(25, cal.get(Calendar.DAY_OF_MONTH))
            assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
            assertEquals(59, cal.get(Calendar.MINUTE))
        }

        assertNull(ChallanEntity.parseChallanDateMillis(""))
        assertNull(ChallanEntity.parseChallanDateMillis("-"))
        assertNull(ChallanEntity.parseChallanDateMillis("UnPaid"))
    }

    @Test
    fun `sortChallansByUpcomingDeadline orders unpaid upcoming nearest first then overdue then paid`() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 13, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = nowCal.timeInMillis

        val paidRecent = ChallanEntity(
            challanNo = "1001",
            challanType = "Tuition Fee",
            payableAmount = "10000",
            paidAmount = "10000",
            balance = "0",
            dueDate = "Nov 14, 2026",
            paidDate = "Nov 10, 2026",
            isPaid = true
        )
        val unpaidFarUpcoming = ChallanEntity(
            challanNo = "1002",
            challanType = "Exam Fee",
            payableAmount = "3000",
            paidAmount = "0",
            balance = "3000",
            dueDate = "Dec 20, 2026",
            paidDate = "UnPaid",
            isPaid = false
        )
        val unpaidNearestUpcoming = ChallanEntity(
            challanNo = "1003",
            challanType = "Installment 2",
            payableAmount = "8500",
            paidAmount = "0",
            balance = "8500",
            dueDate = "Nov 15, 2026",
            paidDate = "UnPaid",
            isPaid = false
        )
        val unpaidOverdue = ChallanEntity(
            challanNo = "1004",
            challanType = "Late Installment",
            payableAmount = "5000",
            paidAmount = "0",
            balance = "5000",
            dueDate = "Nov 01, 2026",
            paidDate = "UnPaid",
            isPaid = false
        )

        val sorted = ChallanEntity.sortChallansByUpcomingDeadline(
            listOf(paidRecent, unpaidFarUpcoming, unpaidOverdue, unpaidNearestUpcoming),
            now = now
        )

        assertEquals(
            listOf("1003", "1002", "1004", "1001"),
            sorted.map { it.challanNo }
        )
    }

    @Test
    fun `saveStudentProfile merges partial updates and persists across PreferencesManager instances`() {
        val prefs1 = PreferencesManager(context)
        prefs1.saveLogin("bc250417449", "secret")

        val fullProfile = StudentProfile(
            studentId = "bc250417449",
            studentName = "HANZLA REHMAN",
            studyProgram = "BS (Software Engineering)",
            cgpa = "2.96",
            effectiveCgpa = "2.96",
            creditsEarned = "28",
            creditsExempted = "0",
            currentSemesterNo = "3",
            semesterName = "Fall 2026"
        )
        prefs1.saveStudentProfile(fullProfile)

        // Simulate fallback navbar-only fetch returning blank academic fields
        val partialNavbarProfile = StudentProfile(
            studentId = "bc250417449",
            studentName = "HANZLA REHMAN",
            studyProgram = "",
            cgpa = "",
            effectiveCgpa = "",
            creditsEarned = "",
            creditsExempted = "",
            currentSemesterNo = "",
            semesterName = ""
        )
        val merged = prefs1.saveStudentProfile(partialNavbarProfile)
        assertEquals("2.96", merged.cgpa)
        assertEquals("BS (Software Engineering)", merged.studyProgram)
        assertEquals("3", merged.currentSemesterNo)

        // Re-instantiate PreferencesManager to simulate app restart
        val prefs2 = PreferencesManager(context)
        val restored = prefs2.getStudentProfile()
        assertEquals("BC250417449".lowercase(), restored.studentId.lowercase())
        assertEquals("HANZLA REHMAN", restored.studentName)
        assertEquals("BS (Software Engineering)", restored.studyProgram)
        assertEquals("2.96", restored.cgpa)
        assertEquals("28", restored.creditsEarned)
        assertEquals("3", restored.currentSemesterNo)
        assertEquals("Fall 2026", restored.semesterName)
    }

    @Test
    fun `saveChallans persists challan list across restart and strips oversized viewstate`() {
        val prefs1 = PreferencesManager(context)
        val challan = ChallanEntity(
            challanNo = "28022249",
            challanType = "Installments_FS",
            payableAmount = "8517",
            paidAmount = "0",
            balance = "8517",
            dueDate = "Dec 15, 2026",
            paidDate = "UnPaid",
            isPaid = false,
            printUrl = "https://vulms.vu.edu.pk/AccountBook/ChallanPrintPreview.aspx?ref=ABC",
            payCardUrl = "https://vulms.vu.edu.pk/IPG/Payment.aspx?ref=ABC",
            payControlName = "ctl00\$MainContent\$grdaccountbook\$ctl24\$hlPay",
            viewState = "HUGE_VIEWSTATE_PAYLOAD",
            viewStateGenerator = "E8A5E93A",
            previousPage = "PREV"
        )
        prefs1.saveChallans(listOf(challan))

        val prefs2 = PreferencesManager(context)
        val restored = prefs2.getChallans()
        assertEquals(1, restored.size)
        assertEquals("28022249", restored[0].challanNo)
        assertEquals("8517", restored[0].payableAmount)
        assertEquals("Dec 15, 2026", restored[0].dueDate)
        assertEquals("https://vulms.vu.edu.pk/IPG/Payment.aspx?ref=ABC", restored[0].payCardUrl)
        assertTrue(restored[0].viewState.isEmpty())

        prefs2.clearLogin()
        assertTrue(prefs2.getChallans().isEmpty())
        assertTrue(prefs2.getStudentProfile().cgpa.isEmpty())
    }
}
