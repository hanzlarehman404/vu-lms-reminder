package com.example

import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.Insets as CoreInsets
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import com.example.data.model.ChallanEntity
import com.example.data.remote.VuLmsNetworkService
import com.example.ui.components.AppDocsDialog
import com.example.ui.screens.CardPaymentDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.OneBillPaymentDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudentPortalScreen
import com.example.ui.theme.VuLmsTheme
import com.example.ui.viewmodel.DashboardStats
import com.example.ui.viewmodel.LoginUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsAndDashboardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vu_lms_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = PreferencesManager(context)
    }

    @Test
    fun `dashboard renders formatted student greeting above nearest deadline`() {
        composeRule.setContent {
            VuLmsTheme {
                DashboardScreen(
                    stats = DashboardStats(),
                    activities = emptyList(),
                    selectedStatus = "ALL",
                    selectedActivityType = "ALL",
                    showUpcomingOnly = false,
                    searchQuery = "",
                    studentId = "BC230401234",
                    studentName = "MUHAMMAD HANZLA",
                    lastSyncTime = 0L,
                    isSyncing = false,
                    syncMessage = null,
                    onStatusSelected = {},
                    onActivityTypeSelected = {},
                    onToggleUpcomingOnly = {},
                    onSearchQueryChanged = {},
                    onSyncNow = {},
                    onClearSyncMessage = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_greeting_header").assertIsDisplayed()
        composeRule.onNodeWithText("Hi Muhammad Hanzla,").assertIsDisplayed()
        composeRule.onNodeWithText("Have a look at your activities and upcoming deadlines.").assertIsDisplayed()
    }

    @Test
    fun `settings screen shows clean top sign out separated autosync mute toggle tappable ringtone and developer heading`() {
        composeRule.setContent {
            VuLmsTheme {
                SettingsScreen(
                    prefs = prefs,
                    studentId = "BC230401234",
                    onLogout = {},
                    onCheckUpdate = {},
                    onSetTheme = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Top-bar Sign Out icon button (roll no should not appear in Settings body)
        composeRule.onNodeWithTag("logout_button").assertIsDisplayed()
        composeRule.onNodeWithText("BC230401234").assertDoesNotExist()

        // Separated Auto-Sync card above Daily Routine Sync
        composeRule.onNodeWithText("App Launch Synchronization").assertIsDisplayed()
        composeRule.onNodeWithTag("auto_sync_launch_switch").assertIsDisplayed()

        // Alerts & Notifications revised controls
        composeRule.onNode(androidx.compose.ui.test.hasScrollAction())
            .performScrollToNode(hasTestTag("mute_notifications_switch"))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("enable_notifications_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("mute_notifications_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("pick_notification_tone_button").assertIsDisplayed()
        composeRule.onNodeWithTag("preview_notification_tone_button").assertIsDisplayed()
        composeRule.onNodeWithText("Browse List").assertDoesNotExist()

        // Muting notifications silences audio preference and hides ringtone selector
        composeRule.onNodeWithTag("mute_notifications_switch").performClick()
        composeRule.waitForIdle()
        assertFalse(prefs.isAlarmSoundEnabled())
        composeRule.onNodeWithTag("pick_notification_tone_button").assertDoesNotExist()

        // Developer & Support card has explicit heading and Report an Issue button
        composeRule.onNode(androidx.compose.ui.test.hasScrollAction())
            .performScrollToNode(hasTestTag("report_issue_button"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Developer & Support").assertIsDisplayed()
        composeRule.onNodeWithTag("report_issue_button").assertIsDisplayed()

        // Clicking top-bar Sign Out opens confirmation dialog
        composeRule.onNodeWithTag("logout_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Confirm Sign Out").assertIsDisplayed()
    }

    @Test
    fun `app docs dialog fetches and renders markdown readme and supports offline fallback`() {
        assertEquals(
            "https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/default/README.md",
            VuLmsNetworkService.README_DEFAULT_URL
        )

        var shouldFail = false
        val sampleReadme = """
            # VU LMS Activity Reminder
            ## Key Highlights
            - Supports **custom notification tones** and `README.md` sync.
            > Browser session notice works cleanly.
        """.trimIndent()

        composeRule.setContent {
            VuLmsTheme {
                AppDocsDialog(
                    onDismiss = {},
                    fetchReadme = {
                        if (shouldFail) Result.failure(IOException("Offline"))
                        else Result.success(sampleReadme)
                    }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("docs_markdown_content").assertIsDisplayed()
        composeRule.onNodeWithText("VU LMS Activity Reminder").assertIsDisplayed()
        composeRule.onNodeWithText("Key Highlights").assertIsDisplayed()

        // Switch to error state and trigger refresh -> Offline Guide fallback works
        shouldFail = true
        composeRule.onNodeWithTag("refresh_docs_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("docs_error_state").assertIsDisplayed()
        composeRule.onNodeWithText("Offline Guide").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1. VU LMS Activity Reminder").assertIsDisplayed()
    }

    @Test
    fun `login screen renders hero header highlights and validates empty submission`() {
        composeRule.setContent {
            VuLmsTheme {
                LoginScreen(
                    loginState = LoginUiState.Idle,
                    isPrivacyAccepted = true,
                    onAcceptPrivacy = {},
                    onLogin = { _, _ -> },
                    onResetLoginState = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("VU LMS Reminder").assertIsDisplayed()
        composeRule.onNodeWithText("👋 Student Sign In").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Enter your VU LMS credentials to sync").assertDoesNotExist()
        composeRule.onNodeWithText("🛡️ Encrypted").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("student_id_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("password_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Verify SHA256 & Sign In").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Smart Alarms").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Auto Sync").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Privacy First").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("✨ Built for Virtual University Students").assertDoesNotExist()
        composeRule.onNodeWithTag("open_source_github_link").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open Source on GitHub").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("login_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Please enter your VU Student ID (e.g. bc240417499)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `card payment dialog renders compact header and omits bank alfalah ready line`() {
        val sampleChallan = ChallanEntity(
            challanNo = "2026111601",
            challanType = "Semester Fee",
            payableAmount = "15,500",
            paidAmount = "0",
            balance = "15,500",
            dueDate = "2026-11-30",
            paidDate = "",
            isPaid = false
        )

        composeRule.setContent {
            VuLmsTheme {
                CardPaymentDialog(
                    challan = sampleChallan,
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("VU Card Payment (IPG)").assertIsDisplayed()
        composeRule.onNodeWithText("Challan #2026111601 • PKR 15,500").assertIsDisplayed()
        composeRule.onNodeWithTag("card_payment_open_browser_button").assertIsDisplayed()
        composeRule.onNodeWithText("Open Bank URL").assertDoesNotExist()
        composeRule.onNodeWithText("Bank Alfalah gateway ready", substring = true).assertDoesNotExist()
    }

    @Test
    fun `saved student ecard omits saved offline text and 1bill dialog shows copy`() {
        val tempPdf = java.io.File(context.cacheDir, "bc240417499.pdf").apply {
            writeText("%PDF-1.4\n%test\n")
        }

        composeRule.setContent {
            VuLmsTheme {
                StudentPortalScreen(
                    studentProfile = com.example.data.model.StudentProfile(studentId = "BC240417499"),
                    currentSubjects = emptyList(),
                    studentAvatarFile = null,
                    isDownloadingECard = false,
                    eCardFile = tempPdf,
                    eCardMessage = null,
                    isSyncing = false,
                    onDownloadECard = {},
                    onSyncNow = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Saved Offline", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Official Digital ID").assertIsDisplayed()
        composeRule.onNodeWithText("View E-Card").assertIsDisplayed()
        tempPdf.delete()
    }

    @Test
    fun `one bill payment dialog labels button as copy`() {
        val sampleChallan = ChallanEntity(
            challanNo = "2026111601",
            challanType = "Semester Fee",
            payableAmount = "15,500",
            paidAmount = "0",
            balance = "15,500",
            dueDate = "2026-11-30",
            paidDate = "",
            isPaid = false
        )

        composeRule.setContent {
            VuLmsTheme {
                OneBillPaymentDialog(
                    challan = sampleChallan,
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("one_bill_copy_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Copy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Copy Voucher").assertDoesNotExist()
    }

    @Test
    fun `nested dashboard scaffold leaves no blank row above bottom bar and renders compact top bar logo`() {
        var contentContainerBottomPx = -1f
        var navBarTopPx = -1f
        var topBarTopPx = -1f
        var topBarBottomPx = -1f
        var logoWidthPx = -1f
        var logoHeightPx = -1f

        composeRule.setContent {
            val view = LocalView.current
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, _ ->
                WindowInsetsCompat.Builder()
                    .setInsets(
                        WindowInsetsCompat.Type.statusBars(),
                        CoreInsets.of(0, 48, 0, 0)
                    )
                    .setInsets(
                        WindowInsetsCompat.Type.navigationBars(),
                        CoreInsets.of(0, 0, 0, 96)
                    )
                    .build()
            }
            ViewCompat.requestApplyInsets(view)

            VuLmsTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .testTag("main_navigation_bar")
                                .onGloballyPositioned { coords ->
                                    navBarTopPx = coords.boundsInRoot().top
                                }
                        ) {
                            NavigationBarItem(
                                selected = true,
                                onClick = {},
                                icon = {},
                                label = { Text("Activities") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .onGloballyPositioned { coords ->
                                contentContainerBottomPx = coords.boundsInRoot().bottom
                            }
                    ) {
                        DashboardScreen(
                            stats = DashboardStats(),
                            activities = emptyList(),
                            selectedStatus = "All",
                            selectedActivityType = "All",
                            showUpcomingOnly = false,
                            searchQuery = "",
                            studentId = "BC230401234",
                            studentName = "MUHAMMAD HANZLA",
                            lastSyncTime = 0L,
                            isSyncing = false,
                            syncMessage = null,
                            onStatusSelected = {},
                            onActivityTypeSelected = {},
                            onToggleUpcomingOnly = {},
                            onSearchQueryChanged = {},
                            onSyncNow = {},
                            onClearSyncMessage = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val logoBounds = composeRule.onNodeWithTag("top_bar_vu_logo")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        logoWidthPx = logoBounds.width
        logoHeightPx = logoBounds.height

        val topBarBounds = composeRule.onNodeWithTag("screen_top_app_bar")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        topBarTopPx = topBarBounds.top
        topBarBottomPx = topBarBounds.bottom

        val bodyBounds = composeRule.onNodeWithTag("dashboard_body_container")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        // Top bar starts at root top (no extra outer status bar gap above TopAppBar)
        assertEquals(0f, topBarTopPx, 2f)
        // Logo is square 36.dp (no wide 16:10 blank gap)
        assertEquals(logoHeightPx, logoWidthPx, 2f)
        // Both Surface container and Dashboard inner body touch top of NavigationBar (no blank row above tabs)
        assertEquals(navBarTopPx, contentContainerBottomPx, 2f)
        assertEquals(navBarTopPx, bodyBounds.bottom, 2f)
        // TopAppBar stays compact without double top inset
        val singleBarExpectedMaxPx = with(composeRule.density) { 120.dp.toPx() }
        org.junit.Assert.assertTrue(
            "TopAppBar height ($topBarBottomPx px) should not double-apply status bar insets",
            topBarBottomPx <= singleBarExpectedMaxPx
        )
    }
}
