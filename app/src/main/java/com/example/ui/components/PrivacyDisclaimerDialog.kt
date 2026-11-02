package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import com.example.R
import coil.compose.AsyncImage
import com.example.ui.theme.VuGreenPrimary
import com.example.ui.theme.VuInfoCyan

@Composable
fun PrivacyDisclaimerDialog(
    onAccept: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { /* Non-cancellable until accepted */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("privacy_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Official VU Logo header with bundled vector fallback
                AsyncImage(
                    model = "https://vulms.vu.edu.pk/App_Themes/Default/Images/logos/logo-1.png",
                    placeholder = painterResource(R.drawable.ic_vu_logo),
                    error = painterResource(R.drawable.ic_vu_logo),
                    fallback = painterResource(R.drawable.ic_vu_logo),
                    contentDescription = "VU Logo",
                    modifier = Modifier
                        .height(48.dp)
                        .aspectRatio(16f / 10f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Welcome to VU LMS Reminder",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Open Source & Student Privacy Guarantee",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VuInfoCyan,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Highlighted Privacy Box
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = VuGreenPrimary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VuGreenPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Safe",
                            tint = VuGreenPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Your Data Never Leaves Your Phone",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Login credentials and calendar activities are sent directly to https://vulms.vu.edu.pk and saved only on your device. No third-party servers, tracking, or cloud storage.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Open Source Callout
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = VuInfoCyan.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VuInfoCyan.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Open Source",
                            tint = VuInfoCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "100% Free & Open Source Project",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Developed by Hanzla Rehman for VU peers. You can inspect the source code or contribute on GitHub.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                DisclaimerBulletItem(
                    icon = Icons.Default.Lock,
                    title = "Independent Companion Tool",
                    description = "This is a 3rd-party open student productivity app created to help students track deadlines and avoid late submissions. Not affiliated with or endorsed by Virtual University of Pakistan."
                )

                Spacer(modifier = Modifier.height(10.dp))

                DisclaimerBulletItem(
                    icon = Icons.Default.Security,
                    title = "Personal Responsibility",
                    description = "While this app automatically synchronizes deadlines and schedules reminder alarms, students remain personally responsible for verifying submission requirements on the official VU LMS website."
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("privacy_accept_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = VuInfoCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "I Understand & Accept",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hanzlarehman404/vu-lms-reminder"))
                            context.startActivity(intent)
                        } catch (ignored: Exception) {}
                    }
                ) {
                    Text("View Project Repository on GitHub", fontSize = 12.sp, color = VuInfoCyan)
                }
            }
        }
    }
}

@Composable
private fun DisclaimerBulletItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VuInfoCyan,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
    }
}
