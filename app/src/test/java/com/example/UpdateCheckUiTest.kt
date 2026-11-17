package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.AppUpdateInfo
import com.example.ui.components.ManualUpdateStatusDialog
import com.example.ui.components.UpdateDialog
import com.example.ui.theme.VuLmsTheme
import com.example.ui.viewmodel.ManualUpdateResult
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class UpdateCheckUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val officialCertSha256 =
        "27:AD:82:EF:6C:AA:E3:B7:0D:2C:A3:D9:F8:45:E2:9B:AA:04:C0:A4:5D:2A:EE:F7:AC:ED:64:24:6B:0F:99:74"
    private val sampleApkSha256 =
        "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"

    @Test
    fun `manual up to date dialog shows github version apk sha256 and matching signature`() {
        val info = AppUpdateInfo(
            version = "2.0.0",
            versionCode = 2,
            releaseName = "v2.0.0 Official",
            releaseNotes = listOf("Custom VU + Bell notification icon", "Android 12 TLS fix"),
            downloadUrl = "https://github.com/hanzlarehman404/vu-lms-reminder/releases/download/v2.0.0/app.apk",
            sha256 = sampleApkSha256,
            certificateSha256 = officialCertSha256,
            publishedAt = "2026-11-16T00:00:00Z"
        )

        composeRule.setContent {
            VuLmsTheme {
                ManualUpdateStatusDialog(
                    result = ManualUpdateResult.UpToDate(
                        updateInfo = info,
                        installedVersion = "2.0.0",
                        installedVersionCode = 2
                    ),
                    onRetry = {},
                    onDismiss = {},
                    installedCertificateSha256Provider = { officialCertSha256 }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("manual_update_up_to_date_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("App is Up to Date").assertIsDisplayed()
        composeRule.onNodeWithText("Release: v2.0.0 Official").assertIsDisplayed()
        composeRule.onNodeWithTag("update_signature_status_badge").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Official APK Signature Matched").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("update_apk_sha256_text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("update_cert_sha256_text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("update_installed_sig_text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("• Custom VU + Bell notification icon").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `manual update error dialog displays failure detail and invokes retry`() {
        var retried = false
        composeRule.setContent {
            VuLmsTheme {
                ManualUpdateStatusDialog(
                    result = ManualUpdateResult.Error(
                        message = "Unable to fetch GitHub release details right now.",
                        detail = "GitHub returned HTTP 503"
                    ),
                    onRetry = { retried = true },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("manual_update_error_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("GitHub returned HTTP 503").assertIsDisplayed()
        composeRule.onNodeWithTag("manual_update_retry_button").performClick()
        assertTrue(retried)
    }

    @Test
    fun `update available dialog displays github latest version and apk sha256`() {
        val info = AppUpdateInfo(
            version = "2.1.0",
            versionCode = 3,
            releaseName = "v2.1.0",
            releaseNotes = listOf("Faster LMS sync"),
            downloadUrl = "https://github.com/hanzlarehman404/vu-lms-reminder/releases/download/v2.1.0/app.apk",
            sha256 = sampleApkSha256,
            certificateSha256 = officialCertSha256,
            publishedAt = "2026-11-17T00:00:00Z"
        )

        composeRule.setContent {
            VuLmsTheme {
                UpdateDialog(
                    updateInfo = info,
                    onDismiss = {},
                    installedCertificateSha256Provider = { officialCertSha256 }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("update_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("New Update Available!").assertIsDisplayed()
        composeRule.onNodeWithText("v2.1.0").assertIsDisplayed()
        composeRule.onNodeWithTag("update_apk_sha256_text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("update_download_button").performScrollTo().assertIsDisplayed()
    }
}
