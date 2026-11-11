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

    @Test
    fun `test combined status and activity type filtering`() {
        val now = System.currentTimeMillis()
        val list = listOf(
            ActivityEntity(
                id = "1",
                title = "CS101 Assignment 1",
                courseCode = "CS101",
                semester = "Fall 2026",
                activityType = "Assignment",
                startDate = now - 1000L,
                endDate = now + 86400000L,
                isExpired = false,
                isCompleted = false,
                url = "",
                colorHex = "#000000"
            ),
            ActivityEntity(
                id = "2",
                title = "CS101 Quiz 1",
                courseCode = "CS101",
                semester = "Fall 2026",
                activityType = "Quiz",
                startDate = now - 1000L,
                endDate = now + 86400000L,
                isExpired = false,
                isCompleted = false,
                url = "",
                colorHex = "#000000"
            ),
            ActivityEntity(
                id = "3",
                title = "CS101 Assignment 2",
                courseCode = "CS101",
                semester = "Fall 2026",
                activityType = "Assignment",
                startDate = now - 1000L,
                endDate = now + 86400000L,
                isExpired = false,
                isCompleted = true,
                url = "",
                colorHex = "#000000"
            )
        )

        // Filter: Status = Pending, Type = Assignment -> Should only return Assignment 1
        val pendingAssignments = list.filter {
            !it.isCompleted && it.activityType.equals("Assignment", ignoreCase = true)
        }
        assertEquals(1, pendingAssignments.size)
        assertEquals("CS101 Assignment 1", pendingAssignments.first().title)

        // Filter: Status = All, Type = Assignment -> Should return Assignment 1 and Assignment 2
        val allAssignments = list.filter {
            it.activityType.equals("Assignment", ignoreCase = true)
        }
        assertEquals(2, allAssignments.size)

        // Filter: Status = Pending, Type = All -> Should return Assignment 1 and Quiz 1
        val allPending = list.filter { !it.isCompleted }
        assertEquals(2, allPending.size)
    }

    @Test
    fun `test parseAccountBookChallans distinguishes paid from unpaid challans`() {
        val sampleHtml = """
            <div id="MainContent_grdaccountbook_pnl_0">
                <a id="MainContent_grdaccountbook_ibtnchallan_0" title="23604812">Challan No. 23604812</a>
                <span id="MainContent_grdaccountbook_lbl_0">23604812</span>
                <span id="MainContent_grdaccountbook_lblPaidAmount_0" class="m--font-bolder" style="color:Green;">11405</span>
                <span id="MainContent_grdaccountbook_lblpaiddate_0" class="m--font-bold" style="color:Green;">Sep 12, 2025</span>
                <span id="MainContent_grdaccountbook_PaidDiv_0">
                    <input type="image" id="MainContent_grdaccountbook_imgbtnPaid_0" title="Paid" src="../App_Themes/Default/Images/Icons/AccountBook/Paid.png">
                </span>
            </div>
            <div id="MainContent_grdaccountbook_pnl_19">
                <a id="MainContent_grdaccountbook_ibtnchallan_19" title="28022249">Challan No. 28022249</a>
                <span id="MainContent_grdaccountbook_lbl_19">28022249</span>
                <span id="MainContent_grdaccountbook_lblPaidAmount_19" class="m--font-bolder" style="color:Red;">0</span>
                <span id="MainContent_grdaccountbook_lblpaiddate_19" class="m--font-bold" style="color:Red;">UnPaid</span>
                <span id="MainContent_grdaccountbook_UnpaidDiv_19">
                    <input type="image" id="MainContent_grdaccountbook_hlPrint_19" title="Print" src="../App_Themes/Default/Images/Icons/AccountBook/DisablePrint.png">
                    <input type="image" id="MainContent_grdaccountbook_hlPay_19" title="Pay Now" src="../App_Themes/Default/Images/Icons/AccountBook/PayNowDisable.png">
                    <input type="image" id="MainContent_grdaccountbook_hl1Bill_19" title="1Bill Payment" onclick="On1Bill(this,event,'28022249','500','Dec 15, 2026');">
                </span>
            </div>
        """.trimIndent()

        val service = com.example.data.remote.VuLmsNetworkService()
        val challans = service.parseAccountBookChallans(sampleHtml)
        val paid = challans.filter { it.isPaid }.map { it.challanNo }
        val unpaid = challans.filter { !it.isPaid }.map { it.challanNo }

        assertTrue(paid.contains("23604812"))
        assertTrue(unpaid.contains("28022249"))
    }

    @Test
    fun `test Challan activity completion matching logic`() {
        val paidChallanIds = setOf("23604812", "27642560")

        val challanPaid = ActivityEntity(
            id = "c1",
            title = "Fee Challan 23604812",
            courseCode = "FEE",
            semester = "Fall 2026",
            activityType = "Challan",
            startDate = 0L,
            endDate = 10000L,
            isExpired = false,
            isCompleted = false,
            url = "",
            colorHex = "#A901FF"
        )

        val challanUnpaid = ActivityEntity(
            id = "c2",
            title = "ChallanID: 28022249",
            courseCode = "FEE",
            semester = "Fall 2026",
            activityType = "Challan",
            startDate = 0L,
            endDate = 10000L,
            isExpired = false,
            isCompleted = false,
            url = "",
            colorHex = "#A901FF"
        )

        val activities = listOf(challanPaid, challanUnpaid)
        val autoCompletedIds = mutableSetOf<String>()

        for (act in activities) {
            if (act.activityType.equals("Challan", ignoreCase = true) ||
                act.title.contains("challan", ignoreCase = true) ||
                act.title.contains("fee", ignoreCase = true)
            ) {
                val digitMatch = Regex("""\b\d{6,10}\b""").find(act.title)?.value
                val challanNumber = digitMatch ?: act.title.filter { it.isDigit() }
                val isPaid = (challanNumber.isNotEmpty() && paidChallanIds.contains(challanNumber)) ||
                        paidChallanIds.any { id -> act.title.contains(id) || act.url.contains(id) || act.id.contains(id) }

                if (isPaid) {
                    autoCompletedIds.add(act.id)
                }
            }
        }

        assertTrue(autoCompletedIds.contains("c1"))
        assertFalse(autoCompletedIds.contains("c2"))
    }

    @Test
    fun `test parseAccountBookChallans extracts structured challans and converts to activities`() {
        val sampleHtml = """
            <div id="MainContent_grdaccountbook_pnl_18">
                <a id="MainContent_grdaccountbook_ibtnchallan_18" title="28491979">Challan No. 28491979</a>
                <span id="MainContent_grdaccountbook_lbl_18">28491979</span>
                <span id="MainContent_grdaccountbook_lblDChallanType_18">Installments_FS</span>
                <span id="MainContent_grdaccountbook_lblPayableAmount_18">8517</span>
                <span id="MainContent_grdaccountbook_lblPaidAmount_18" class="m--font-bolder" style="color:Green;">8517</span>
                <span id="MainContent_grdaccountbook_lblduedate_18">Nov 05, 2026</span>
                <span id="MainContent_grdaccountbook_lblpaiddate_18" class="m--font-bold" style="color:Green;">Nov 05, 2026</span>
                <span id="MainContent_grdaccountbook_PaidDiv_18">
                    <input type="image" id="MainContent_grdaccountbook_imgbtnPaid_18" title="Paid" src="../App_Themes/Default/Images/Icons/AccountBook/Paid.png">
                </span>
            </div>
            <div id="MainContent_grdaccountbook_pnl_19">
                <a id="MainContent_grdaccountbook_ibtnchallan_19" title="28022249">Challan No. 28022249</a>
                <span id="MainContent_grdaccountbook_lbl_19">28022249</span>
                <span id="MainContent_grdaccountbook_lblDChallanType_19">SocietiesAndSportsFund</span>
                <span id="MainContent_grdaccountbook_lblPayableAmount_19">500</span>
                <span id="MainContent_grdaccountbook_lblPaidAmount_19" class="m--font-bolder" style="color:Red;">0</span>
                <span id="MainContent_grdaccountbook_lblduedate_19">Dec 15, 2026</span>
                <span id="MainContent_grdaccountbook_lblpaiddate_19" class="m--font-bold" style="color:Red;">UnPaid</span>
                <span id="MainContent_grdaccountbook_UnpaidDiv_19">
                    <input type="image" id="MainContent_grdaccountbook_hlPrint_19" title="Print" src="../App_Themes/Default/Images/Icons/AccountBook/DisablePrint.png">
                </span>
            </div>
        """.trimIndent()

        val service = com.example.data.remote.VuLmsNetworkService()
        val challans = service.parseAccountBookChallans(sampleHtml)
        assertEquals(2, challans.size)

        val paidChallan = challans.find { it.challanNo == "28491979" }
        assertNotNull(paidChallan)
        assertTrue(paidChallan!!.isPaid)
        assertEquals("Installments_FS", paidChallan.challanType)
        assertEquals("8517", paidChallan.payableAmount)

        assertEquals("Nov 05, 2026", paidChallan.dueDate)
        assertEquals("Nov 05, 2026", paidChallan.paidDate)

        val unpaidChallan = challans.find { it.challanNo == "28022249" }
        assertNotNull(unpaidChallan)
        assertFalse(unpaidChallan!!.isPaid)
    }

    @Test
    fun `test GDB activity type detection from URL and border color`() {
        val gdbRaw = VuRawActivity(
            title = "MGT602: Family Business Planning...",
            coursecode = "MGT602",
            semester = "Fall 2026",
            start = "2026,11,03",
            end = "2026,11,16",
            url = "/ActivityCalendar/OpenActivitySection.aspx?coursecode=MGT602&ActivityType=GDB",
            borderColor = "#f39c12",
            backgroundColor = "linear-gradient(90deg, #f39c12 5px, transparent 0%) center, #ffffff",
            isExpired = "0"
        )
        val entity = gdbRaw.toActivityEntity()
        assertEquals("GDB", entity.activityType)
        assertEquals("MGT602", entity.courseCode)
    }

    @Test
    fun `test parseStudentProfileHtml extracts complete student records from CourseSelectionClosed HTML`() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <span class="m-nav__link-text" style="font-size: 10pt;">HANZLA REHMAN<br />(bc250417449)</span>
                <span id="MainContent_lblSemester">Fall 2026</span>
                <table border="1">
                    <tr>
                        <td width="30%" class="label" align="right">Student ID</td>
                        <td class="txt" align="left">bc250417449</td>
                    </tr>
                    <tr>
                        <td class="label" align="right">Name</td>
                        <td class="txt" align="left">HANZLA REHMAN</td>
                    </tr>
                    <tr>
                        <td class="label" align="right">Study program</td>
                        <td class="txt" align="left">BS (Software Engineering)</td>
                    </tr>
                    <tr>
                        <td width="30%" class="label" align="right">CGPA:</td>
                        <td class="txt" align="left">2.96</td>
                    </tr>
                    <tr title="Effective CGPA is the total CGPA without counting Failure Courses">
                        <td width="30%" class="label" align="right">Effective CGPA:</td>
                        <td class="txt" align="left">2.96</td>
                    </tr>
                    <tr>
                        <td width="30%" class="label" align="right">Credits Earned:</td>
                        <td class="txt" align="left">28</td>
                    </tr>
                    <tr>
                        <td width="30%" class="label" align="right">Credits Exempted:</td>
                        <td class="txt" align="left">0</td>
                    </tr>
                    <tr>
                        <td width="30%" class="label" align="right">Current Semester No:</td>
                        <td class="txt" align="left">3</td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val service = com.example.data.remote.VuLmsNetworkService()
        val profile = service.parseStudentProfileHtml(sampleHtml)

        assertEquals("bc250417449", profile.studentId)
        assertEquals("HANZLA REHMAN", profile.studentName)
        assertEquals("BS (Software Engineering)", profile.studyProgram)
        assertEquals("2.96", profile.cgpa)
        assertEquals("2.96", profile.effectiveCgpa)
        assertEquals("28", profile.creditsEarned)
        assertEquals("0", profile.creditsExempted)
        assertEquals("3", profile.currentSemesterNo)
        assertEquals("Fall 2026", profile.semesterName)
    }

    @Test
    fun `test parseStudentProfileHtml extracts from exact CourseSelectionClosed div snippet`() {
        val snippetHtml = """
            <div class="container-fluid m--padding-right-30 m--padding-left-30  m--padding-top-10">  
                <div id="MainContent_divEligible">
                    &nbsp;<div class="m-scroll-top m-scroll-top--skin-top" data-toggle="m-scroll-top" data-scroll-offset="500" data-scroll-speed="300">
                        <i class="la la-arrow-up"></i>
                    </div><table align="center" cellpadding="2" cellspacing="1" border="1" style="border-collapse: collapse; padding-top:20px;
                        width: 100%;" bordercolor="gray" class="table table-bordered table-striped m-table m-table--head-bg-brand tblWidth">
                        <tbody><tr class="tblrows">
                            <td colspan="5" style="font-size: 14pt;" class="tblheading">
                                Course Selection
                                <span id="MainContent_lblSemester1">Fall 2026</span>
                                <span style="font-family: Arial; font-size: 12px">
                                   ( <a href="https://handbook.vu.edu.pk/HandBook_Pages/Course%20Selection%20BS%20(Software%20Engineering).htm" target="_blank" title="Click here to View Rules and Procedures for Course Selection">
                                        Rules and Procedures </a> )
                                 </span>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="5" align="center" style="background-color: #eeeeee;">
                                <table width="100%" cellpadding="3" border="1" style="border-collapse: collapse;" bordercolor="gray">
                                    <tr>
                                        <td width="30%" class="label" align="right">
                                            Student ID</td>
                                        <td class="txt" align="left">
                                            bc250417449
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="label" align="right">
                                            Name</td>
                                        <td class="txt" align="left">
                                            HANZLA REHMAN
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="label" align="right">
                                            Study program</td>
                                        <td class="txt" align="left">
                                            BS
                                            (Software Engineering)
                                            
                                        </td>
                                    </tr>
                                    <tr>
                                        <td width="30%" class="label" align="right">
                                            CGPA:</td>
                                        <td class="txt" align="left">
                                            2.96
                                        </td>
                                    </tr>
                                    <tr title="Effective CGPA is the total CGPA without counting Failure Courses">
                                        <td width="30%" class="label" align="right">
                                            Effective CGPA:</td>
                                        <td class="txt" align="left">
                                            2.96
                                        </td>
                                    </tr>
                                    <tr>
                                        <td width="30%" class="label" align="right">
                                            Credits Earned:</td>
                                        <td class="txt" align="left">
                                            28
                                        </td>
                                    </tr>
                                    <tr>
                                        <td width="30%" class="label" align="right">
                                            Credits Exempted:</td>
                                        <td class="txt" align="left">
                                            0
                                        </td>
                                    </tr>
                                    <tr>
                                        <td width="30%" class="label" align="right">
                                            Current Semester No:</td>
                                        <td class="txt" align="left">
                                            3
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr class="tblrows">
                            <td colspan="5" align="center" style="background-color: red; color: White; font-size:small; font-weight: bold;">
                                The Course Selection for Semester <span id="MainContent_lblSemester2">Fall 2026</span> is Closed.
                            </td>
                        </tr>
                    </tbody></table>
                </div>
            </div>
        """.trimIndent()

        val service = com.example.data.remote.VuLmsNetworkService()
        val profile = service.parseStudentProfileHtml(snippetHtml)

        assertEquals("bc250417449", profile.studentId)
        assertEquals("HANZLA REHMAN", profile.studentName)
        assertEquals("BS (Software Engineering)", profile.studyProgram)
        assertEquals("2.96", profile.cgpa)
        assertEquals("2.96", profile.effectiveCgpa)
        assertEquals("28", profile.creditsEarned)
        assertEquals("0", profile.creditsExempted)
        assertEquals("3", profile.currentSemesterNo)
        assertEquals("Fall 2026", profile.semesterName)
    }

    @Test
    fun `test parseAccountBookChallans extracts ChallanPrintPreview and IPG card payment URLs and state params`() {
        val sampleAccountBookHtml = """
            <input type="hidden" name="__VIEWSTATE" id="__VIEWSTATE" value="TEST_VIEWSTATE_123" />
            <input type="hidden" name="__VIEWSTATEGENERATOR" id="__VIEWSTATEGENERATOR" value="E8A5E93A" />
            <input type="hidden" name="__PREVIOUSPAGE" id="__PREVIOUSPAGE" value="PREV_PAGE_XYZ" />
            <div id="MainContent_grdaccountbook_pnl_24">
                <span id="MainContent_grdaccountbook_lbl_24">28991234</span>
                <span id="MainContent_grdaccountbook_lblDChallanType_24">Tuition Fee</span>
                <span id="MainContent_grdaccountbook_lblPayableAmount_24">12500</span>
                <span id="MainContent_grdaccountbook_lblduedate_24">Nov 25, 2026</span>
                <span id="MainContent_grdaccountbook_lblpaiddate_24">UnPaid</span>
                <div id="MainContent_grdaccountbook_UnpaidDiv_24">
                    <input type="image" name="ctl00${'$'}MainContent${'$'}grdaccountbook${'$'}ctl24${'$'}hlPrint" id="MainContent_grdaccountbook_hlPrint_24" title="Print" class="img-zoom" target="_new" src="../App_Themes/Default/Images/Icons/AccountBook/DisablePrint.png" onclick="javascript:WebForm_DoPostBackWithOptions(new WebForm_PostBackOptions(&quot;ctl00${'$'}MainContent${'$'}grdaccountbook${'$'}ctl24${'$'}hlPrint&quot;, &quot;&quot;, true, &quot;&quot;, &quot;ChallanPrintPreview.aspx?ref=CC/UIXIr3Y+QUyQUPWsGCQ==&amp;fref=ZZ7cSlXvo9YA7r9xWhiwBw==&quot;, false, false))" style="height:30px;width:30px;">
                    <input type="image" name="ctl00${'$'}MainContent${'$'}grdaccountbook${'$'}ctl24${'$'}hlPay" id="MainContent_grdaccountbook_hlPay_24" title="Pay Now" class="ml-2 img-zoom" src="../App_Themes/Default/Images/Icons/AccountBook/PayNowDisable.png" onclick="javascript:WebForm_DoPostBackWithOptions(new WebForm_PostBackOptions(&quot;ctl00${'$'}MainContent${'$'}grdaccountbook${'$'}ctl24${'$'}hlPay&quot;, &quot;&quot;, true, &quot;&quot;, &quot;https://vulms.vu.edu.pk/IPG/Payment.aspx?ref=CC/UIXIr3Y+QUyQUPWsGCQ==&amp;fref=ZZ7cSlXvo9YA7r9xWhiwBw==&amp;uref=TEST_UREF&amp;aref=TEST_AREF&quot;, false, false))" style="height:30px;width:30px;">
                </div>
            </div>
        """.trimIndent()

        val service = com.example.data.remote.VuLmsNetworkService()
        val list = service.parseAccountBookChallans(sampleAccountBookHtml)

        assertEquals(1, list.size)
        val item = list[0]
        assertEquals("28991234", item.challanNo)
        assertFalse(item.isPaid)
        assertEquals("https://vulms.vu.edu.pk/AccountBook/ChallanPrintPreview.aspx?ref=CC/UIXIr3Y+QUyQUPWsGCQ==&fref=ZZ7cSlXvo9YA7r9xWhiwBw==", item.printUrl)
        assertEquals("https://vulms.vu.edu.pk/IPG/Payment.aspx?ref=CC/UIXIr3Y+QUyQUPWsGCQ==&fref=ZZ7cSlXvo9YA7r9xWhiwBw==&uref=TEST_UREF&aref=TEST_AREF", item.payCardUrl)
        assertEquals("ctl00\$MainContent\$grdaccountbook\$ctl24\$hlPay", item.payControlName)
        assertEquals("TEST_VIEWSTATE_123", item.viewState)
        assertEquals("E8A5E93A", item.viewStateGenerator)
        assertEquals("PREV_PAGE_XYZ", item.previousPage)
    }
}

