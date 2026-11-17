package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.VuLmsNetworkService
import com.example.ui.theme.*

private sealed interface DocsLoadState {
    data object Loading : DocsLoadState
    data class Loaded(val markdown: String) : DocsLoadState
    data class Error(val message: String) : DocsLoadState
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class BulletItem(val text: String) : MdBlock
    data class NumberedItem(val number: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data class Badges(val items: List<Pair<String, String>>) : MdBlock
    data object Divider : MdBlock
}

private val badgeRegex = Regex("""\[!\[([^\]]*)\]\([^)]*\)\]\(([^)]*)\)""")
private val inlineTokenRegex = Regex("""(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\)|\*[^*\s][^*]*\*)""")

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MdBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Fenced code block
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++
            blocks.add(MdBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            continue
        }

        // Horizontal rule
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MdBlock.Divider)
            i++
            continue
        }

        // Heading
        val headingMatch = Regex("""^(#{1,4})\s+(.+)$""").find(trimmed)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val text = headingMatch.groupValues[2].trim()
            blocks.add(MdBlock.Heading(level, text))
            i++
            continue
        }

        // Badge lines (collect consecutive badge lines cleanly)
        if (badgeRegex.containsMatchIn(trimmed) && trimmed.startsWith("[![")) {
            val badges = mutableListOf<Pair<String, String>>()
            while (i < lines.size) {
                val cur = lines[i].trim()
                if (cur.startsWith("[![") && badgeRegex.containsMatchIn(cur)) {
                    badgeRegex.findAll(cur).forEach { m ->
                        val label = m.groupValues[1].ifBlank { "Link" }
                        val url = m.groupValues[2]
                        badges.add(label to url)
                    }
                    i++
                } else {
                    break
                }
            }
            if (badges.isNotEmpty()) {
                blocks.add(MdBlock.Badges(badges))
            }
            continue
        }

        // Markdown Table
        if (trimmed.startsWith("|") && i + 1 < lines.size && lines[i + 1].trim().matches(Regex("""^\|[\s:|-]+\|$"""))) {
            fun splitRow(row: String): List<String> =
                row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

            val headers = splitRow(trimmed)
            i += 2 // skip header and alignment separator
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                rows.add(splitRow(lines[i]))
                i++
            }
            blocks.add(MdBlock.Table(headers, rows))
            continue
        }

        // Blockquote
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            blocks.add(MdBlock.Quote(quoteLines.joinToString("\n")))
            continue
        }

        // Unordered bullet
        val bulletMatch = Regex("""^[-*+]\s+(.+)$""").find(trimmed)
        if (bulletMatch != null) {
            blocks.add(MdBlock.BulletItem(bulletMatch.groupValues[1].trim()))
            i++
            continue
        }

        // Numbered item
        val numMatch = Regex("""^(\d+)\.\s+(.+)$""").find(trimmed)
        if (numMatch != null) {
            blocks.add(MdBlock.NumberedItem(numMatch.groupValues[1], numMatch.groupValues[2].trim()))
            i++
            continue
        }

        // Regular paragraph (merge consecutive plain lines)
        val paraLines = mutableListOf<String>()
        while (i < lines.size) {
            val cur = lines[i].trim()
            if (cur.isEmpty() ||
                cur.startsWith("#") ||
                cur.startsWith("```") ||
                cur.startsWith(">") ||
                cur == "---" ||
                cur == "***" ||
                cur.startsWith("[![") ||
                cur.startsWith("|") ||
                Regex("""^([-*+]|\d+\.)\s+""").containsMatchIn(cur)
            ) {
                break
            }
            paraLines.add(cur)
            i++
        }
        if (paraLines.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paraLines.joinToString(" ")))
        } else {
            i++
        }
    }

    return blocks
}

private fun formatInlineMarkdown(
    text: String,
    linkColor: Color,
    codeBgColor: Color
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in inlineTokenRegex.findAll(text)) {
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        val token = match.value
        when {
            token.startsWith("**") && token.endsWith("**") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.removeSurrounding("**"))
                }
            }
            token.startsWith("`") && token.endsWith("`") -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        background = codeBgColor
                    )
                ) {
                    append(" ${token.removeSurrounding("`")} ")
                }
            }
            token.startsWith("[") -> {
                val m = Regex("""^\[([^\]]+)\]\(([^)]+)\)$""").find(token)
                val label = m?.groupValues?.getOrNull(1) ?: token
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(label)
                }
            }
            token.startsWith("*") && token.endsWith("*") -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.removeSurrounding("*"))
                }
            }
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}

@Composable
fun AppDocsDialog(
    onDismiss: () -> Unit,
    fetchReadme: suspend () -> Result<String> = { VuLmsNetworkService().fetchReadmeMarkdown() }
) {
    val context = LocalContext.current
    var reloadKey by remember { mutableIntStateOf(0) }
    var showOfflineFallback by remember { mutableStateOf(false) }
    var loadState by remember { mutableStateOf<DocsLoadState>(DocsLoadState.Loading) }

    LaunchedEffect(reloadKey) {
        if (!showOfflineFallback) {
            loadState = DocsLoadState.Loading
            val res = fetchReadme()
            loadState = res.fold(
                onSuccess = { DocsLoadState.Loaded(it) },
                onFailure = { DocsLoadState.Error(it.message ?: "Unable to fetch README.md from GitHub.") }
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 16.dp)
                .testTag("app_docs_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VuInfoCyan.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, VuInfoCyan.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = if (showOfflineFallback) "OFFLINE" else "README",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = VuInfoCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Documentation & Details",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (showOfflineFallback) "Built-in Offline Summary" else "Synced from GitHub README.md",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                showOfflineFallback = false
                                reloadKey++
                            },
                            modifier = Modifier.testTag("refresh_docs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh GitHub README",
                                tint = VuInfoCyan
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )

                // Body
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        showOfflineFallback -> {
                            OfflineDocsContent()
                        }
                        loadState is DocsLoadState.Loading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("docs_loading_state"),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = VuInfoCyan)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Fetching latest README.md from GitHub...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        loadState is DocsLoadState.Error -> {
                            val errMsg = (loadState as DocsLoadState.Error).message
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .testTag("docs_error_state"),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(VuWarningAmber.copy(alpha = 0.14f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = VuWarningAmber,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Unable to Load GitHub README",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = errMsg,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = { showOfflineFallback = true },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Offline Guide", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { reloadKey++ },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = VuInfoCyan),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        loadState is DocsLoadState.Loaded -> {
                            val markdown = (loadState as DocsLoadState.Loaded).markdown
                            val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
                            val codeBg = MaterialTheme.colorScheme.surfaceVariant
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .testTag("docs_markdown_content"),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                blocks.forEach { block ->
                                    when (block) {
                                        is MdBlock.Heading -> {
                                            when (block.level) {
                                                1 -> Text(
                                                    text = block.text,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    lineHeight = 23.sp
                                                )
                                                2 -> Column(modifier = Modifier.padding(top = 6.dp)) {
                                                    Text(
                                                        text = block.text,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = VuInfoCyan,
                                                        lineHeight = 20.sp
                                                    )
                                                }
                                                else -> Text(
                                                    text = block.text,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                        is MdBlock.Badges -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                block.items.forEach { (label, url) ->
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = VuInfoCyan.copy(alpha = 0.12f),
                                                        border = BorderStroke(1.dp, VuInfoCyan.copy(alpha = 0.3f)),
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable(enabled = url.startsWith("http")) {
                                                                runCatching {
                                                                    context.startActivity(
                                                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = VuInfoCyan,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        is MdBlock.Paragraph -> {
                                            Text(
                                                text = formatInlineMarkdown(block.text, VuInfoCyan, codeBg),
                                                fontSize = 12.5.sp,
                                                lineHeight = 18.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        is MdBlock.BulletItem -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Text(
                                                    text = "•",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = VuInfoCyan,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                                Text(
                                                    text = formatInlineMarkdown(block.text, VuInfoCyan, codeBg),
                                                    fontSize = 12.5.sp,
                                                    lineHeight = 18.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        is MdBlock.NumberedItem -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Text(
                                                    text = "${block.number}.",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = VuInfoCyan,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                                Text(
                                                    text = formatInlineMarkdown(block.text, VuInfoCyan, codeBg),
                                                    fontSize = 12.5.sp,
                                                    lineHeight = 18.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        is MdBlock.Quote -> {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = VuWarningAmber.copy(alpha = 0.1f),
                                                border = BorderStroke(1.dp, VuWarningAmber.copy(alpha = 0.35f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = formatInlineMarkdown(block.text, VuInfoCyan, codeBg),
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(12.dp)
                                                )
                                            }
                                        }
                                        is MdBlock.CodeBlock -> {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = block.code,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    lineHeight = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(10.dp)
                                                )
                                            }
                                        }
                                        is MdBlock.Table -> {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (block.headers.isNotEmpty()) {
                                                        Text(
                                                            text = block.headers.joinToString("  •  "),
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = VuInfoCyan
                                                        )
                                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                                    }
                                                    block.rows.forEach { row ->
                                                        val title = row.firstOrNull().orEmpty()
                                                        val details = row.drop(1).filter { it.isNotBlank() }.joinToString(" — ")
                                                        Column {
                                                            if (title.isNotBlank()) {
                                                                Text(
                                                                    text = formatInlineMarkdown(title, VuInfoCyan, codeBg),
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }
                                                            if (details.isNotBlank()) {
                                                                Text(
                                                                    text = formatInlineMarkdown(details, VuInfoCyan, codeBg),
                                                                    fontSize = 11.5.sp,
                                                                    lineHeight = 16.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        MdBlock.Divider -> {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 2.dp),
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hanzlarehman404/vu-lms-reminder")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (ignored: Exception) {}
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View on GitHub", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VuInfoCyan)
                    ) {
                        Text("Got It", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineDocsContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DocSectionCard(
            icon = Icons.Default.School,
            title = "1. VU LMS Activity Reminder",
            iconColor = VuInfoCyan
        ) {
            Text(
                text = "An open-source, privacy-first Android client built exclusively for Virtual University students. Connects directly to vulms.vu.edu.pk without intermediary servers, syncs tasks, tracks fee challans, caches your E-Card offline, and fires punctual alarms.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DocSectionCard(
            icon = Icons.Default.EventNote,
            title = "2. Smart Due Dates & Pending Count",
            iconColor = VuWarningAmber
        ) {
            Text(
                text = "VU LMS calendar end dates use exclusive cutoffs (e.g., 2026,11,06 closes on 2026-11-05 at 23:59:59). The app automatically adjusts cutoffs by one day and counts only pending available tasks in the Dashboard badge.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DocSectionCard(
            icon = Icons.Default.NotificationsActive,
            title = "3. Routine Sync & Notification Audio",
            iconColor = VuGreenPrimary
        ) {
            Text(
                text = "• Morning Briefing (09:00), Mid-Day Check (14:00), and Evening Urgent Alarm (21:00).\n" +
                        "• Full-screen urgent alarms ring your device's default Alarm ringtone; regular & announcement notifications use the Android ringtone you pick in Settings (or silent vibration when muted).",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DocSectionCard(
            icon = Icons.Default.Security,
            title = "4. Signature Verification & Privacy",
            iconColor = VuNavy
        ) {
            Text(
                text = "Credentials stay in private on-device storage. Developer authenticity can be verified against update.json before login, and Student E-Card PDFs are validated and stored offline by roll number.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DocSectionCard(
    icon: ImageVector,
    title: String,
    iconColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(iconColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
