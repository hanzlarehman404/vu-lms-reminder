package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.BuildConfig
import com.example.data.model.AppUpdateInfo
import com.example.data.remote.VuLmsNetworkService
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

sealed interface VerificationState {
    data object Loading : VerificationState
    data class Verified(
        val updateInfo: AppUpdateInfo,
        val certificateSha256: String,
        val installedSha256: String
    ) : VerificationState
    data class Tampered(
        val expectedSha256: String,
        val actualSha256: String,
        val updateInfo: AppUpdateInfo?
    ) : VerificationState
    data class Error(val message: String) : VerificationState
}

@Composable
fun SecurityVerificationDialog(
    onDismiss: () -> Unit,
    onContinueSignIn: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var state by remember { mutableStateOf<VerificationState>(VerificationState.Loading) }

    fun runVerification() {
        state = VerificationState.Loading
        coroutineScope.launch {
            val installedCert = withContext(Dispatchers.IO) {
                getInstalledCertificateSha256(context)
            }
            val networkService = VuLmsNetworkService()
            val updateResult = withContext(Dispatchers.IO) {
                networkService.checkAppUpdate()
            }
            val updateInfo = updateResult.getOrNull()

            if (updateInfo == null) {
                state = VerificationState.Error("Could not reach GitHub update server to fetch official security certificate. Please check your internet connection.")
                return@launch
            }

            val expectedCert = updateInfo.certificateSha256.trim()
            val cleanInstalled = installedCert.trim()

            // If certificates match (case-insensitive)
            if (expectedCert.isNotEmpty() && cleanInstalled.equals(expectedCert, ignoreCase = true)) {
                state = VerificationState.Verified(
                    updateInfo = updateInfo,
                    certificateSha256 = expectedCert,
                    installedSha256 = cleanInstalled
                )
            } else {
                state = VerificationState.Tampered(
                    expectedSha256 = expectedCert.ifEmpty { "27:AD:82:EF:6C:AA:E3:B7:0D:2C:A3:D9:F8:45:E2:9B:AA:04:C0:A4:5D:2A:EE:F7:AC:ED:64:24:6B:0F:99:74" },
                    actualSha256 = cleanInstalled.ifEmpty { "UNKNOWN / DEBUG SIGNATURE" },
                    updateInfo = updateInfo
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        runVerification()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val current = state) {
                    is VerificationState.Loading -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(VuInfoCyan.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = VuInfoCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Verifying App Authenticity...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Checking cryptographic developer signature against official release registry to verify the APK hasn't been reverse-engineered or tampered with.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is VerificationState.Verified -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(VuSuccessMint.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Security Verified",
                                tint = VuSuccessMint,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Official Release Verified",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Cryptographic integrity confirmed. This app is genuinely signed by the official developer.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Developer:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Hanzla Rehman", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VuGreenPrimary)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("App Version:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("v2.0.0 (${current.updateInfo.version})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                                Text("Signing Certificate SHA-256:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = current.certificateSha256,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = VuGreenPrimary,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }

                                if (current.updateInfo.sha256.isNotEmpty()) {
                                    Text(
                                        text = "Release APK SHA-256 (for reference):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = current.updateInfo.sha256,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onContinueSignIn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("continue_signin_verified_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = VuGreenPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Continue to Sign In", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    is VerificationState.Tampered -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(VuDangerRed.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Tampered App Alert",
                                tint = VuDangerRed,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Security Signature Warning!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = VuDangerRed
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "The installed package certificate does NOT match the official permanent developer release signature. This APK may have been modified or re-signed by an untrusted third party.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Expected Official Certificate:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = current.expectedSha256,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = VuGreenPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Installed Package Signature:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = current.actualSha256,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = VuDangerRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hanzlarehman404/vu-lms-reminder")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (ignored: Exception) {}
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VuDangerRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Official Repository", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Allow developer / debug testing bypass when running debug keystore locally
                        if (BuildConfig.DEBUG || current.actualSha256.contains("DEBUG") || current.actualSha256.startsWith("FA:C7") || current.actualSha256.startsWith("35:34")) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onContinueSignIn,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Proceed (Developer Debug Mode)", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    is VerificationState.Error -> {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = VuWarningAmber,
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Verification Server Unreachable",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = current.message,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Close")
                            }

                            Button(
                                onClick = { runVerification() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VuInfoCyan)
                            ) {
                                Text("Retry")
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(onClick = onContinueSignIn) {
                            Text("Skip Verification & Sign In (Offline)", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun getInstalledCertificateSha256(context: Context): String {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
        }

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        val cert = signatures?.firstOrNull()?.toByteArray() ?: return ""
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(cert)
        digest.joinToString(":") { "%02X".format(it) }
    } catch (e: Exception) {
        ""
    }
}
