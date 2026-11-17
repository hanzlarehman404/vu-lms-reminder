package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import com.example.data.model.ChallanEntity
import com.example.data.remote.VuLmsNetworkService
import com.example.ui.components.AppDocsDialog
import com.example.ui.screens.CardPaymentDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SettingsScreen
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
}
