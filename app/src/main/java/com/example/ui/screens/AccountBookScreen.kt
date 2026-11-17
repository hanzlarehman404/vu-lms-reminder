package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ChallanEntity
import com.example.data.remote.VuLmsCookieJar
import com.example.ui.theme.VuDangerRed
import com.example.ui.theme.VuGreenPrimary
import com.example.ui.theme.VuInfoCyan
import com.example.ui.theme.VuNavy
import com.example.ui.theme.VuSidebarDark
import com.example.ui.theme.VuSuccessMint
import com.example.ui.theme.VuWarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountBookScreen(
    challans: List<ChallanEntity>,
    isSyncing: Boolean,
    syncMessage: String? = null,
    onClearSyncMessage: () -> Unit = {},
    onPreparePaymentSession: suspend (ChallanEntity) -> ChallanEntity = { it },
    onSyncNow: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Pending selected by default
    var selectedFilter by remember { mutableStateOf("Pending") } // "Pending", "All", "Paid"
    var searchQuery by remember { mutableStateOf("") }
    var selected1BillChallan by remember { mutableStateOf<ChallanEntity?>(null) }
    var selectedCardPaymentChallan by remember { mutableStateOf<ChallanEntity?>(null) }
    var selectedPrintVoucherChallan by remember { mutableStateOf<ChallanEntity?>(null) }
    var isPreparingPayment by remember { mutableStateOf(false) }
    var showResyncWarningDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "fee_sync_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    val now = System.currentTimeMillis()
    val sortedChallans = remember(challans) {
        ChallanEntity.sortChallansByUpcomingDeadline(challans, System.currentTimeMillis())
    }

    val pendingChallans = remember(sortedChallans) { sortedChallans.filter { !it.isPaid } }
    val paidChallans = remember(sortedChallans) { sortedChallans.filter { it.isPaid } }
    val pendingCount = pendingChallans.size
    val paidCount = paidChallans.size
    val dueSoonCount = remember(pendingChallans) {
        val currentNow = System.currentTimeMillis()
        val weekMs = 7L * 24 * 3600 * 1000
        pendingChallans.count { ch ->
            val due = ch.dueDateMillis()
            due != null && due >= currentNow && (due - currentNow) <= weekMs
        }
    }
    val overdueCount = remember(pendingChallans) {
        val currentNow = System.currentTimeMillis()
        pendingChallans.count { it.isOverdue(currentNow) }
    }

    val nearestUpcomingChallan = remember(pendingChallans) {
        val currentNow = System.currentTimeMillis()
        pendingChallans.firstOrNull { ch ->
            val due = ch.dueDateMillis()
            due != null && due >= currentNow
        } ?: pendingChallans.firstOrNull()
    }

    val filteredChallans = remember(sortedChallans, selectedFilter, searchQuery) {
        val base = when (selectedFilter) {
            "Pending" -> sortedChallans.filter { !it.isPaid }
            "Paid" -> sortedChallans.filter { it.isPaid }
            "Due Soon" -> {
                val currentNow = System.currentTimeMillis()
                val weekMs = 7L * 24 * 3600 * 1000
                sortedChallans.filter { ch ->
                    !ch.isPaid && (ch.dueDateMillis()?.let { it >= currentNow && (it - currentNow) <= weekMs } ?: false)
                }
            }
            "Overdue" -> sortedChallans.filter { it.isOverdue(System.currentTimeMillis()) }
            else -> sortedChallans
        }
        if (searchQuery.isBlank()) base
        else base.filter {
            it.challanNo.contains(searchQuery, ignoreCase = true) ||
                it.challanType.contains(searchQuery, ignoreCase = true) ||
                it.payableAmount.contains(searchQuery, ignoreCase = true)
        }
    }

    if (showResyncWarningDialog) {
        AlertDialog(
            onDismissRequest = { showResyncWarningDialog = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = VuWarningAmber)
            },
            title = {
                Text("Confirm LMS Resync", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Warning: Synchronizing with VU LMS may log you out of your current web browser session because VU LMS permits only one active login at a time.\n\nDo you want to proceed and refresh all activities, fees, subjects, and academic records?",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResyncWarningDialog = false
                        onSyncNow()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VuGreenPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Proceed & Sync", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResyncWarningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isPreparingPayment) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Preparing Secure Session...", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    Text("Refreshing VU LMS payment session and security tokens...", fontSize = 13.sp)
                }
            }
        )
    }

    if (selected1BillChallan != null) {
        OneBillPaymentDialog(
            challan = selected1BillChallan!!,
            onDismiss = { selected1BillChallan = null }
        )
    }

    if (selectedCardPaymentChallan != null) {
        CardPaymentDialog(
            challan = selectedCardPaymentChallan!!,
            onDismiss = { selectedCardPaymentChallan = null }
        )
    }

    if (selectedPrintVoucherChallan != null) {
        VoucherViewerDialog(
            challan = selectedPrintVoucherChallan!!,
            onDismiss = { selectedPrintVoucherChallan = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = "https://vulms.vu.edu.pk/App_Themes/Default/Images/logos/logo-1.png",
                            placeholder = painterResource(R.drawable.ic_vu_logo),
                            error = painterResource(R.drawable.ic_vu_logo),
                            fallback = painterResource(R.drawable.ic_vu_logo),
                            contentDescription = "VU Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(32.dp)
                                .aspectRatio(16f / 10f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Fee & Account Book",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "VU Challans, 1BILL Vouchers & Payments",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showResyncWarningDialog = true },
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("account_book_sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync All LMS Data",
                            tint = VuInfoCyan,
                            modifier = if (isSyncing) Modifier.rotate(rotation) else Modifier
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Sync status message banner (matching Activities Tab, auto-dismissed)
            var lastDisplayedMessage by remember { mutableStateOf("") }
            if (syncMessage != null) {
                lastDisplayedMessage = syncMessage
            }
            AnimatedVisibility(
                visible = syncMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VuSuccessMint.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VuSuccessMint.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = VuSuccessMint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = syncMessage ?: lastDisplayedMessage,
                                fontSize = 12.sp,
                                color = VuSuccessMint,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(onClick = onClearSyncMessage, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp), tint = VuSuccessMint)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

            // Hero Countdown Timer Card (matching Activities Tab CountdownTimerCard)
            item {
                ChallanCountdownTimerCard(
                    challan = nearestUpcomingChallan,
                    onPay1Bill = { ch -> selected1BillChallan = ch }
                )
            }

            // Quick Stats Bar (matching Activities Tab StatPills)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeeStatPill(
                        label = "Pending",
                        count = pendingCount,
                        isSelected = selectedFilter == "Pending",
                        color = VuDangerRed,
                        onClick = { selectedFilter = "Pending" }
                    )
                    FeeStatPill(
                        label = "Due Soon",
                        count = dueSoonCount,
                        isSelected = selectedFilter == "Due Soon",
                        color = VuWarningAmber,
                        onClick = { selectedFilter = "Due Soon" }
                    )
                    FeeStatPill(
                        label = "Overdue",
                        count = overdueCount,
                        isSelected = selectedFilter == "Overdue",
                        color = VuDangerRed,
                        onClick = { selectedFilter = "Overdue" }
                    )
                    FeeStatPill(
                        label = "All",
                        count = challans.size,
                        isSelected = selectedFilter == "All",
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { selectedFilter = "All" }
                    )
                    FeeStatPill(
                        label = "Paid",
                        count = paidCount,
                        isSelected = selectedFilter == "Paid",
                        color = VuGreenPrimary,
                        onClick = { selectedFilter = "Paid" }
                    )
                }
            }

            // Search Input (matching Activities Tab)
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by challan # or fee type...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("challan_search_input")
                )
            }

            // Section Header (matching Activities Tab)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (selectedFilter) {
                            "Pending" -> "Pending Challans (${filteredChallans.size})"
                            "Paid" -> "Paid Challans (${filteredChallans.size})"
                            "Due Soon" -> "Due This Week (${filteredChallans.size})"
                            "Overdue" -> "Overdue Challans (${filteredChallans.size})"
                            else -> "All Challans (${filteredChallans.size})"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Sorted by deadline",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Challans List
            if (filteredChallans.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (selectedFilter == "Pending") Icons.Default.CheckCircle else Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = if (selectedFilter == "Pending") VuGreenPrimary else VuInfoCyan,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (selectedFilter == "Pending") "No Pending Fee Challans" else "No Challans Found",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (selectedFilter == "Pending")
                                    "All semester dues are clear. Tap Sync to check latest updates from VU LMS."
                                else "Try changing the filter or search query.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredChallans, key = { it.challanNo }) { challan ->
                    ChallanCardItem(
                        challan = challan,
                        now = now,
                        onOpen1Bill = { selected1BillChallan = challan },
                        onOpenCardPayment = {
                            scope.launch {
                                isPreparingPayment = true
                                val prepared = onPreparePaymentSession(challan)
                                isPreparingPayment = false
                                selectedCardPaymentChallan = prepared
                            }
                        },
                        onOpenPrintVoucher = {
                            scope.launch {
                                isPreparingPayment = true
                                val prepared = onPreparePaymentSession(challan)
                                isPreparingPayment = false
                                selectedPrintVoucherChallan = prepared
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
            }
        }
    }
}

@Composable
private fun ChallanCountdownTimerCard(
    challan: ChallanEntity?,
    onPay1Bill: (ChallanEntity) -> Unit
) {
    if (challan == null) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("empty_fee_countdown_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = VuGreenPrimary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "🎉 All Fees Cleared!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "No pending fee challans are due right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    val dueMillis = challan.dueDateMillis() ?: (currentTime + 24 * 3600 * 1000L)
    val isOverdue = dueMillis < currentTime
    val remainingMillis = (dueMillis - currentTime).coerceAtLeast(0L)
    val days = remainingMillis / (24 * 3600 * 1000)
    val hours = (remainingMillis % (24 * 3600 * 1000)) / (3600 * 1000)
    val minutes = (remainingMillis % (3600 * 1000)) / (60 * 1000)
    val seconds = (remainingMillis % (60 * 1000)) / 1000

    val isCritical = isOverdue || (days == 0L && hours < 12)
    val isUrgent = !isCritical && days <= 2L

    val gradientColors = when {
        isCritical -> listOf(Color(0xFF8B0000), Color(0xFF4A0000))
        isUrgent -> listOf(Color(0xFFB45309), Color(0xFF78350F))
        else -> listOf(VuSidebarDark, Color(0xFF151521))
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fee_countdown_hero_card")
    ) {
        Box(
            modifier = Modifier
                .background(Brush.horizontalGradient(gradientColors))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = when {
                                isOverdue -> "🚨 OVERDUE CHALLAN"
                                isCritical -> "🚨 CRITICAL FEE DEADLINE"
                                isUrgent -> "⚡ DUE SOON"
                                else -> "⏳ NEAREST FEE DEADLINE"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = VuGreenPrimary
                    ) {
                        Text(
                            text = if (challan.payableAmount.isNotEmpty()) "PKR ${challan.payableAmount}" else "#${challan.challanNo}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = challan.challanType.ifEmpty { "VU Fee Challan #${challan.challanNo}" },
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Challan #${challan.challanNo} • Due: ${challan.dueDate.ifEmpty { "Check LMS" }}",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FeeTimeBox(value = "%02d".format(days), label = "DAYS")
                    FeeTimeBox(value = "%02d".format(hours), label = "HOURS")
                    FeeTimeBox(value = "%02d".format(minutes), label = "MINS")
                    FeeTimeBox(value = "%02d".format(seconds), label = "SECS")
                }

                Spacer(modifier = Modifier.height(14.dp))

                FilledTonalButton(
                    onClick = { onPay1Bill(challan) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("fee_countdown_pay_button"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = VuInfoCyan,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Payment,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pay via 1Bill Now",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FeeTimeBox(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = 1.sp
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FeeStatPill(
    label: String,
    count: Int,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) color else color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$count",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isSelected) Color.White else color
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ChallanCardItem(
    challan: ChallanEntity,
    now: Long = System.currentTimeMillis(),
    onOpen1Bill: () -> Unit,
    onOpenCardPayment: () -> Unit,
    onOpenPrintVoucher: () -> Unit
) {
    val dueMillis = challan.dueDateMillis()
    val isPast = dueMillis != null && dueMillis < now && !challan.isPaid
    val remainingMillis = ((dueMillis ?: now) - now).coerceAtLeast(0L)
    val remainingHours = remainingMillis / (3600 * 1000)
    val remainingDays = remainingMillis / (24 * 3600 * 1000)

    val relativeText = when {
        challan.isPaid -> "✅ Paid"
        dueMillis == null -> "⏳ Check Due Date"
        isPast -> "⚠️ Overdue"
        remainingHours <= 1 -> "🚨 < 1 hr left!"
        remainingHours < 24 -> "⚡ $remainingHours hrs left"
        remainingDays == 1L -> "📅 Due tomorrow"
        else -> "⏳ $remainingDays days left"
    }

    val badgeColor = when {
        challan.isPaid -> VuGreenPrimary
        dueMillis == null -> MaterialTheme.colorScheme.primary
        isPast -> VuDangerRed
        remainingHours < 24 -> VuDangerRed
        remainingHours < 72 -> VuWarningAmber
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (challan.isPaid) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (challan.isPaid) 1.dp else 2.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (challan.isPaid) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            else badgeColor.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (challan.isPaid) 0.8f else 1f)
            .testTag("challan_card_${challan.challanNo}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "💳 #${challan.challanNo}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    if (challan.payableAmount.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "PKR ${challan.payableAmount}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = relativeText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = challan.challanType.ifEmpty { "VU Fee Challan" },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Due: ${challan.dueDate.ifEmpty { "Check LMS" }}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (challan.isPaid) MaterialTheme.colorScheme.onSurfaceVariant else badgeColor
                )

                if (challan.paidDate.isNotEmpty() && challan.paidDate != "-") {
                    Text(
                        text = "Paid: ${challan.paidDate}",
                        fontSize = 11.sp,
                        color = VuGreenPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: 1Bill, Print Challan, Pay with Card
            if (!challan.isPaid) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpen1Bill,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("pay_1bill_button_${challan.challanNo}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VuGreenPrimary),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pay via 1Bill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onOpenPrintVoucher,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("print_challan_button_${challan.challanNo}"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print Challan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = onOpenCardPayment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("pay_card_button_${challan.challanNo}"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pay with Card (Visa / MasterCard IPG)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                OutlinedButton(
                    onClick = onOpenPrintVoucher,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("print_receipt_button_${challan.challanNo}"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print Paid Challan / Receipt", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * 1Bill Payment Dialog matching VU LMS 1BILL modal specification.
 * Voucher format: 100225047 + Challan Number.
 */
@Composable
fun OneBillPaymentDialog(
    challan: ChallanEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    // Voucher Number with prefix 100225047 as specified by user
    val voucherNumber = remember(challan.challanNo) {
        if (challan.challanNo.startsWith("100225047")) {
            challan.challanNo
        } else {
            "100225047${challan.challanNo}"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Modal Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = VuGreenPrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Payment,
                                    contentDescription = null,
                                    tint = VuGreenPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "1Bill Payment",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Modal Body with Gray container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(18.dp)
                ) {
                    // INFO CARDS: Voucher Number (with Copy), Payable Amount, Due Date
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "VOUCHER NUMBER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = voucherNumber,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("1Bill Voucher", voucherNumber)
                                        clipboard.setPrimaryClip(clip)
                                        isCopied = true
                                        Toast.makeText(context, "Voucher copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "Copy Voucher",
                                        tint = if (isCopied) VuGreenPrimary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (isCopied) {
                                Text(
                                    text = "Copied to clipboard!",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VuGreenPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Payable Amount Card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(1.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Payable Amount", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (challan.payableAmount.isNotEmpty()) "PKR ${challan.payableAmount}" else "As per Challan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = VuGreenPrimary
                                )
                            }
                        }

                        // Due Date Card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(1.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Due Date", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = challan.dueDate.ifEmpty { "Check LMS" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = VuDangerRed
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // PAYMENT PROCEDURE (Exact steps from user HTML)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = VuNavy, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Payment Procedure",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Open Bank App & Enter Details:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val steps = listOf(
                                "1. Open your bank's mobile app or internet banking portal.",
                                "2. Go to Bill Payments.",
                                "3. Select 1BILL.",
                                "4. Choose Voucher/Invoice Payment.",
                                "5. Enter your Voucher Number ($voucherNumber).",
                                "6. (Optional) Add alias name.",
                                "7. Review details and click Proceed."
                            )

                            steps.forEach { step ->
                                Text(
                                    text = step,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Supported Banks Info
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = VuInfoCyan.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = VuInfoCyan,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Compatible with all Pakistani 1BILL member apps including Easypaisa, JazzCash, SadaPay, NayaPay, HBL, Meezan, Bank Alfalah, Allied Bank, UBL, and MCB.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Close & Copy Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("1Bill Voucher", voucherNumber)
                                clipboard.setPrimaryClip(clip)
                                isCopied = true
                                Toast.makeText(context, "Voucher copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VuGreenPrimary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Voucher", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Done", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CardPaymentDialog(
    challan: ChallanEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var currentWebUrl by remember { mutableStateOf("about:blank") }
    var webError by remember { mutableStateOf<String?>(null) }
    var settleSecondsLeft by remember { mutableIntStateOf(4) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var isDialogActive by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose {
            isDialogActive = false
        }
    }

    // Ensure session cookies from VuLmsCookieJar are flushed to Android CookieManager before opening
    LaunchedEffect(challan.challanNo) {
        VuLmsCookieJar.shared.syncToAndroidWebView()
    }

    val isOnVuOrBlank = currentWebUrl.isBlank() ||
        currentWebUrl.startsWith("about:", ignoreCase = true) ||
        currentWebUrl.startsWith("data:", ignoreCase = true) ||
        currentWebUrl.contains("vulms.vu.edu.pk", ignoreCase = true)

    val isExternalGateway = currentWebUrl.startsWith("http", ignoreCase = true) && !isOnVuOrBlank

    // Settle countdown timer once external Bank Alfalah / payment gateway URL starts loading
    LaunchedEffect(currentWebUrl, isLoading, isExternalGateway) {
        if (!isExternalGateway) {
            settleSecondsLeft = 4
        } else if (isLoading) {
            settleSecondsLeft = 4
        } else {
            var remaining = 4
            settleSecondsLeft = remaining
            while (remaining > 0) {
                delay(1000)
                remaining--
                settleSecondsLeft = remaining
            }
        }
    }

    val isBrowserButtonActive = isExternalGateway && !isLoading && settleSecondsLeft == 0 && webError == null

    val formHtml = remember(challan) {
        val targetUrl = TextUtils.htmlEncode(
            if (challan.payCardUrl.isNotEmpty()) challan.payCardUrl else "https://vulms.vu.edu.pk/IPG/Payment.aspx"
        )
        val controlName = TextUtils.htmlEncode(
            if (challan.payControlName.isNotEmpty()) challan.payControlName else "ctl00\$MainContent\$grdaccountbook\$ctl24\$hlPay"
        )
        val safeViewState = TextUtils.htmlEncode(challan.viewState)
        val safeGenerator = TextUtils.htmlEncode(challan.viewStateGenerator)
        val safePrevPage = TextUtils.htmlEncode(challan.previousPage)
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>VU Online Payment</title>
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; background: #f8fafc; color: #1e293b; text-align: center; padding: 20px; box-sizing: border-box; }
                .spinner { border: 4px solid #e2e8f0; border-top: 4px solid #1a365d; border-radius: 50%; width: 44px; height: 44px; animation: spin 0.8s linear infinite; margin-bottom: 16px; }
                @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
                h3 { margin: 0 0 8px; font-size: 17px; color: #0f172a; }
                p { font-size: 13px; color: #64748b; margin: 0; }
            </style>
        </head>
        <body onload="document.getElementById('payForm').submit();">
            <div class="spinner"></div>
            <h3>Connecting to VU Payment Gateway...</h3>
            <p>Please wait, redirecting to Bank Alfalah / 1Link / Visa &amp; MasterCard IPG for Challan #${TextUtils.htmlEncode(challan.challanNo)}</p>
            <form id="payForm" method="POST" action="$targetUrl">
                <input type="hidden" name="__EVENTTARGET" value="" />
                <input type="hidden" name="__EVENTARGUMENT" value="" />
                <input type="hidden" name="__VIEWSTATE" value="$safeViewState" />
                <input type="hidden" name="__VIEWSTATEGENERATOR" value="$safeGenerator" />
                <input type="hidden" name="__PREVIOUSPAGE" value="$safePrevPage" />
                <input type="hidden" name="$controlName.x" value="17" />
                <input type="hidden" name="$controlName.y" value="21" />
                <input type="hidden" name="ctl00${'$'}MainContent${'$'}hfChallanID" value="" />
                <input type="hidden" name="ctl00${'$'}MainContent${'$'}ddlBankName" value="Select Bank Name" />
                <input type="hidden" name="ctl00${'$'}MainContent${'$'}txtBankBranch" value="" />
            </form>
        </body>
        </html>
        """.trimIndent()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = VuNavy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CreditCard,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "VU Card Payment (IPG)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Challan #${challan.challanNo} • PKR ${challan.payableAmount}",
                                    color = Color.White.copy(alpha = 0.84f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 10.dp)
                                )

                                FilledTonalButton(
                                    onClick = {
                                        if (isBrowserButtonActive) {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentWebUrl)))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Unable to open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    enabled = isBrowserButtonActive,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isBrowserButtonActive) VuGreenPrimary else Color.White.copy(alpha = 0.14f),
                                        contentColor = Color.White,
                                        disabledContainerColor = Color.White.copy(alpha = 0.12f),
                                        disabledContentColor = Color.White.copy(alpha = 0.55f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .testTag("card_payment_open_browser_button")
                                ) {
                                    Icon(
                                        imageVector = if (isBrowserButtonActive) Icons.Default.OpenInBrowser else Icons.Default.LockClock,
                                        contentDescription = "Open in Browser",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            isBrowserButtonActive -> "Open in Browser"
                                            isExternalGateway && !isLoading -> "Wait ${settleSecondsLeft}s"
                                            else -> "Locked"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        if (webError != null || !isBrowserButtonActive) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = when {
                                        webError != null -> "⚠️ Gateway error: $webError"
                                        isExternalGateway && isLoading -> "⏳ Loading payment gateway page..."
                                        isExternalGateway -> "🔒 Securing gateway URL... Open in Browser unlocks in ${settleSecondsLeft}s"
                                        else -> "⏳ Negotiating VU LMS IPG redirect... Open in Browser stays locked until gateway loads"
                                    },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = VuGreenPrimary
                    )
                }

                if (webError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = webError ?: "Connection error",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    webError = null
                                    isLoading = true
                                    VuLmsCookieJar.shared.syncToAndroidWebView()
                                    val activeView = webViewRef
                                    if (activeView != null) {
                                        activeView.loadDataWithBaseURL(
                                            "https://vulms.vu.edu.pk/AccountBook/AccountBook.aspx",
                                            formHtml,
                                            "text/html",
                                            "UTF-8",
                                            null
                                        )
                                    } else {
                                        webViewGeneration++
                                    }
                                }
                            ) {
                                Text("Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                key(webViewGeneration) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                runCatching {
                                    val cm = CookieManager.getInstance()
                                    cm.setAcceptCookie(true)
                                    cm.setAcceptThirdPartyCookies(this, true)
                                }
                                VuLmsCookieJar.shared.syncToAndroidWebView()

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.loadsImagesAutomatically = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.userAgentString = com.example.data.remote.VuLmsNetworkService.USER_AGENT

                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        if (!isDialogActive) return true
                                        val url = request?.url?.toString().orEmpty()
                                        if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                                            currentWebUrl = url
                                            return false
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?
                                    ) {
                                        if (!isDialogActive) return
                                        isLoading = true
                                        webError = null
                                        if (!url.isNullOrBlank()) {
                                            currentWebUrl = url
                                        }
                                    }

                                    override fun doUpdateVisitedHistory(
                                        view: WebView?,
                                        url: String?,
                                        isReload: Boolean
                                    ) {
                                        if (!isDialogActive) return
                                        if (!url.isNullOrBlank()) {
                                            currentWebUrl = url
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        if (!isDialogActive) return
                                        isLoading = false
                                        if (!url.isNullOrBlank()) {
                                            currentWebUrl = url
                                            if (url.contains("LMS_LP.aspx", ignoreCase = true)) {
                                                webError = "VU LMS session expired. Please close and tap Sync, then retry."
                                            }
                                        }
                                        runCatching { CookieManager.getInstance().flush() }
                                    }

                                    override fun onReceivedSslError(
                                        view: WebView?,
                                        handler: SslErrorHandler?,
                                        error: SslError?
                                    ) {
                                        val host = Uri.parse(error?.url.orEmpty()).host?.lowercase().orEmpty()
                                        handler?.cancel()
                                        if (!isDialogActive) return
                                        isLoading = false
                                        webError = "Untrusted SSL certificate on ${host.ifEmpty { "gateway" }}"
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (!isDialogActive) return
                                        if (request?.isForMainFrame == true) {
                                            isLoading = false
                                            webError = error?.description?.toString() ?: "Failed to load payment gateway"
                                        }
                                    }

                                    override fun onRenderProcessGone(
                                        view: WebView?,
                                        detail: RenderProcessGoneDetail?
                                    ): Boolean {
                                        if (webViewRef === view) {
                                            webViewRef = null
                                        }
                                        runCatching {
                                            view?.stopLoading()
                                            view?.onPause()
                                            (view?.parent as? ViewGroup)?.removeView(view)
                                            view?.removeAllViews()
                                            view?.destroy()
                                        }
                                        if (isDialogActive) {
                                            isLoading = false
                                            val crashed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                detail?.didCrash() == true
                                            } else {
                                                true
                                            }
                                            webError = if (crashed) {
                                                "Payment browser renderer crashed. Tap Retry to reload."
                                            } else {
                                                "Payment browser was closed by the system. Tap Retry to reload."
                                            }
                                        }
                                        return true
                                    }
                                }
                                loadDataWithBaseURL(
                                    "https://vulms.vu.edu.pk/AccountBook/AccountBook.aspx",
                                    formHtml,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        },
                        onRelease = { view ->
                            isDialogActive = false
                            if (webViewRef === view) {
                                webViewRef = null
                            }
                            runCatching {
                                view.stopLoading()
                                view.onPause()
                                view.webChromeClient = null
                                view.clearHistory()
                                (view.parent as? ViewGroup)?.removeView(view)
                                view.removeAllViews()
                            }
                            Handler(Looper.getMainLooper()).post {
                                runCatching { view.destroy() }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun VoucherViewerDialog(
    challan: ChallanEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val effectiveUrl = remember(challan) {
        if (challan.printUrl.isNotEmpty()) {
            challan.printUrl
        } else {
            "https://vulms.vu.edu.pk/AccountBook/PrintChallan.aspx?ChallanNo=${challan.challanNo}"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = VuNavy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fee Voucher Preview", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(
                                text = "Challan #${challan.challanNo} • Due: ${challan.dueDate}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effectiveUrl)))
                                }
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Print / Open in Browser", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "VU Fee Challan #${challan.challanNo}")
                                        putExtra(Intent.EXTRA_TEXT, "VU Fee Challan #${challan.challanNo}\nPayable Amount: PKR ${challan.payableAmount}\nDue Date: ${challan.dueDate}\nVoucher Link: $effectiveUrl")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Voucher Link"))
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share Voucher", tint = Color.White)
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = effectiveUrl,
                                    placeholder = painterResource(R.drawable.ic_vu_logo),
                                    error = painterResource(R.drawable.ic_vu_logo),
                                    contentDescription = "Fee Challan Voucher",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effectiveUrl)))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VuNavy)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open in Browser", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Close Preview", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}
