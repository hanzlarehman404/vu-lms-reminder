package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.VuGreenPrimary
import com.example.ui.theme.VuInfoCyan
import com.example.ui.theme.VuNavy
import com.example.ui.theme.VuNavyDark
import com.example.ui.theme.VuSuccessMint
import com.example.ui.theme.VuWarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PrivacyDisclaimerDialog(
    onAccept: () -> Unit
) {
    val context = LocalContext.current

    // Entrance animations
    val cardScale = remember { Animatable(0.90f) }
    val cardAlpha = remember { Animatable(0f) }
    var showHeroText by remember { mutableStateOf(false) }
    var showFeatureRow by remember { mutableStateOf(false) }
    var showPrivacyCard by remember { mutableStateOf(false) }
    var showOpenSourceCard by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            cardScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            cardAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
        }
        delay(80)
        showHeroText = true
        delay(90)
        showFeatureRow = true
        delay(90)
        showPrivacyCard = true
        delay(80)
        showOpenSourceCard = true
        delay(80)
        showActions = true
    }

    // Continuous ambient motion for hero aurora, logo halo & CTA shimmer
    val infiniteTransition = rememberInfiniteTransition(label = "welcome_ambient")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cta_shimmer"
    )

    Dialog(
        onDismissRequest = { /* Non-cancellable until accepted */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .padding(vertical = 16.dp)
                .scale(cardScale.value)
                .alpha(cardAlpha.value)
                .testTag("privacy_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Animated Obsidian-Navy Hero Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(VuNavy, VuNavyDark, Color(0xFF091D3E))
                            )
                        )
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Ambient glowing aurora & concentric rings drawn behind the VU crest
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val cx = size.width / 2f
                        val cy = size.height * 0.34f
                        val ringRadius1 = 44.dp.toPx() + (pulseProgress * 8.dp.toPx())
                        val ringRadius2 = 62.dp.toPx() + (pulseProgress * 12.dp.toPx())

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    VuInfoCyan.copy(alpha = 0.28f),
                                    VuSuccessMint.copy(alpha = 0.12f),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = 120.dp.toPx()
                            ),
                            center = Offset(cx, cy),
                            radius = 120.dp.toPx()
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.10f + (pulseProgress * 0.08f)),
                            radius = ringRadius1,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                        drawCircle(
                            color = VuInfoCyan.copy(alpha = 0.18f - (pulseProgress * 0.08f)),
                            radius = ringRadius2,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Glassmorphic Logo Emblem + Verified Shield Overlay
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                                    AsyncImage(
                                        model = "https://vulms.vu.edu.pk/App_Themes/Default/Images/logos/logo-1.png",
                                        placeholder = painterResource(R.drawable.ic_vu_logo),
                                        error = painterResource(R.drawable.ic_vu_logo),
                                        fallback = painterResource(R.drawable.ic_vu_logo),
                                        contentDescription = "VU Logo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = VuGreenPrimary,
                                border = BorderStroke(2.dp, VuNavyDark),
                                modifier = Modifier
                                    .offset(x = 6.dp, y = 6.dp)
                                    .size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        AnimatedVisibility(
                            visible = showHeroText,
                            enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 3 }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = CircleShape,
                                    color = VuInfoCyan.copy(alpha = 0.22f),
                                    border = BorderStroke(1.dp, VuInfoCyan.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = "SMART COMPANION • OPEN SOURCE",
                                        color = Color(0xFF93C5FD),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Welcome to VU LMS Reminder",
                                    color = Color.White,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 26.sp
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Stay ahead of every Quiz, Assignment, GDB, Practical & Fee Deadline automatically.",
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }

                // 2. Main Body Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 3 Quick Highlight Chips
                    AnimatedVisibility(
                        visible = showFeatureRow,
                        enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 3 }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WelcomeHighlightChip(
                                icon = Icons.Default.Timer,
                                label = "Live Timers",
                                tint = VuInfoCyan,
                                modifier = Modifier.weight(1f)
                            )
                            WelcomeHighlightChip(
                                icon = Icons.Default.AlarmOn,
                                label = "Due Alarms",
                                tint = VuWarningAmber,
                                modifier = Modifier.weight(1f)
                            )
                            WelcomeHighlightChip(
                                icon = Icons.Default.Badge,
                                label = "E-Card & Fees",
                                tint = VuGreenPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Privacy Guarantee Vault Card
                    AnimatedVisibility(
                        visible = showPrivacyCard,
                        enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 3 }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = VuGreenPrimary.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, VuGreenPrimary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = VuGreenPrimary.copy(alpha = 0.18f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = "On-Device Privacy",
                                            tint = VuGreenPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "100% On-Device Privacy Vault",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Your VU Student ID, password, activities, and academic records communicate directly with vulms.vu.edu.pk and are stored strictly on your device. Zero third-party servers, tracking, or ads.",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Open Source & Transparency Card
                    AnimatedVisibility(
                        visible = showOpenSourceCard,
                        enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 3 }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = VuInfoCyan.copy(alpha = 0.09f),
                                border = BorderStroke(1.dp, VuInfoCyan.copy(alpha = 0.30f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        try {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://github.com/hanzlarehman404/vu-lms-reminder")
                                                )
                                            )
                                        } catch (_: Exception) {}
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = VuInfoCyan.copy(alpha = 0.18f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Code,
                                                contentDescription = "Open Source",
                                                tint = VuInfoCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Free & Open Source for VU Peers",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = null,
                                                tint = VuInfoCyan,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "Developed by Hanzla Rehman. Inspect the source code or contribute anytime on GitHub.",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }

                            // Concise Student Responsibility Notice
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(top = 1.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Independent companion tool (not officially affiliated with Virtual University). Always verify final submission status directly on VU LMS.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    // Primary Shimmer CTA & GitHub Link
                    AnimatedVisibility(
                        visible = showActions,
                        enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 2 }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Spacer(modifier = Modifier.height(2.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(VuInfoCyan, VuGreenPrimary)
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable(role = Role.Button, onClick = onAccept)
                                    .testTag("privacy_accept_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                // Animated diagonal highlight sweep across CTA
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    val sweepX = size.width * shimmerOffset
                                    drawRect(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.White.copy(alpha = 0.22f),
                                                Color.Transparent
                                            ),
                                            start = Offset(sweepX - 80f, 0f),
                                            end = Offset(sweepX + 80f, size.height)
                                        )
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Get Started — I Understand & Accept",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            TextButton(
                                onClick = {
                                    try {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/hanzlarehman404/vu-lms-reminder")
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = VuInfoCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "View Source Code on GitHub",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VuInfoCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeHighlightChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}
