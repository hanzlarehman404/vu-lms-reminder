package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ChallanEntity
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.VuDangerRed
import com.example.ui.theme.VuGreenPrimary
import com.example.ui.theme.VuInfoCyan
import com.example.ui.theme.VuNavy
import com.example.ui.theme.VuWarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountBookScreen(
    challans: List<ChallanEntity>,
    isSyncing: Boolean,
    onSyncNow: () -> Unit
) {
    val context = LocalContext.current
    // Pending selected by default as requested
    var selectedFilter by remember { mutableStateOf("Pending") } // "Pending", "All", "Paid"
    var selected1BillChallan by remember { mutableStateOf<ChallanEntity?>(null) }
    var selectedCardPaymentChallan by remember { mutableStateOf<ChallanEntity?>(null) }
    var selectedPrintVoucherChallan by remember { mutableStateOf<ChallanEntity?>(null) }

    val pendingCount = challans.count { !it.isPaid }
    val paidCount = challans.count { it.isPaid }

    val filteredChallans = remember(challans, selectedFilter) {
        when (selectedFilter) {
            "Pending" -> challans.filter { !it.isPaid }
            "Paid" -> challans.filter { it.isPaid }
            else -> challans
        }
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
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("account_book_sync_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync Fees")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))

                // Summary Metric Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedFilter = "Pending" }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = if (pendingCount > 0) VuDangerRed else VuGreenPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pending Fees", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$pendingCount Challan${if (pendingCount != 1) "s" else ""}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (pendingCount > 0) VuDangerRed else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedFilter = "Paid" }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = VuGreenPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paid Fees", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$paidCount Cleared",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = VuGreenPrimary
                            )
                        }
                    }
                }
            }

            // Filter Tabs: Pending (default), All, Paid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "Pending",
                        onClick = { selectedFilter = "Pending" },
                        label = { Text("🚨 Pending ($pendingCount)") },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("fee_filter_pending")
                    )

                    FilterChip(
                        selected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" },
                        label = { Text("📋 All (${challans.size})") },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("fee_filter_all")
                    )

                    FilterChip(
                        selected = selectedFilter == "Paid",
                        onClick = { selectedFilter = "Paid" },
                        label = { Text("✅ Paid ($paidCount)") },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("fee_filter_paid")
                    )
                }
            }

            // Challans List
            if (filteredChallans.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (selectedFilter == "Pending") Icons.Default.CheckCircle else Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = if (selectedFilter == "Pending") VuGreenPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (selectedFilter == "Pending") "No Pending Fee Challans" else "No Challans Found",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedFilter == "Pending")
                                    "All semester dues are clear. Resync anytime to refresh."
                                else "No records match the current filter.",
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
                        onOpen1Bill = { selected1BillChallan = challan },
                        onOpenCardPayment = { selectedCardPaymentChallan = challan },
                        onOpenPrintVoucher = { selectedPrintVoucherChallan = challan }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ChallanCardItem(
    challan: ChallanEntity,
    onOpen1Bill: () -> Unit,
    onOpenCardPayment: () -> Unit,
    onOpenPrintVoucher: () -> Unit
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = VuNavy.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "Challan #${challan.challanNo}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = VuNavy,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (challan.isPaid) VuGreenPrimary.copy(alpha = 0.12f) else VuDangerRed.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (challan.isPaid) "✅ Paid" else "🚨 Unpaid",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (challan.isPaid) VuGreenPrimary else VuDangerRed,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = challan.challanType.ifEmpty { "VU Fee Challan" },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Payable Amount", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (challan.payableAmount.isNotEmpty()) "PKR ${challan.payableAmount}" else "As per Challan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Due Date", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = challan.dueDate.ifEmpty { "Check LMS" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (challan.isPaid) MaterialTheme.colorScheme.onSurface else VuDangerRed
                    )
                }
            }

            if (challan.paidDate.isNotEmpty() && challan.paidDate != "-") {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Paid on: ${challan.paidDate}",
                    fontSize = 11.sp,
                    color = VuGreenPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: 1Bill, Print Challan, Pay with Card
            if (!challan.isPaid) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pay via 1Bill button
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

                    // Print Challan button
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

                // Pay with Card button (Bank / Card IPG)
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
                // Paid Challan: Print Paid Challan / Receipt
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

@Composable
fun CardPaymentDialog(
    challan: ChallanEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    val formHtml = remember(challan) {
        val targetUrl = if (challan.payCardUrl.isNotEmpty()) challan.payCardUrl else "https://vulms.vu.edu.pk/IPG/Payment.aspx"
        val controlName = if (challan.payControlName.isNotEmpty()) challan.payControlName else "ctl00\$MainContent\$grdaccountbook\$ctl24\$hlPay"
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
            <p>Please wait, launching Bank of Punjab / 1Link / Visa & MasterCard IPG for Challan #${challan.challanNo}</p>
            <form id="payForm" method="POST" action="$targetUrl">
                <input type="hidden" name="__EVENTTARGET" value="" />
                <input type="hidden" name="__EVENTARGUMENT" value="" />
                <input type="hidden" name="__VIEWSTATE" value="${challan.viewState}" />
                <input type="hidden" name="__VIEWSTATEGENERATOR" value="${challan.viewStateGenerator}" />
                <input type="hidden" name="__PREVIOUSPAGE" value="${challan.previousPage}" />
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("VU Card Payment (IPG)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(
                                text = "Challan #${challan.challanNo} • PKR ${challan.payableAmount}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val target = if (challan.payCardUrl.isNotEmpty()) challan.payCardUrl else "https://vulms.vu.edu.pk/AccountBook/AccountBook.aspx"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                                }
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in External Browser", tint = Color.White)
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
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

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.userAgentString = com.example.data.remote.VuLmsNetworkService.USER_AGENT
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
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
                    modifier = Modifier.fillMaxSize()
                )
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
