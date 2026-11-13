package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ActivityEntity
import com.example.data.model.ActivityType
import com.example.ui.components.ActivityDetailDialog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    activities: List<ActivityEntity>,
    onToggleCompletion: ((activityId: String, completed: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedActivityForDetail by remember { mutableStateOf<ActivityEntity?>(null) }

    // Calendar state: Year and Month
    val currentCal = remember { Calendar.getInstance() }
    var displayedCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        })
    }

    var selectedDayCal by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        })
    }

    var viewMode by remember { mutableStateOf("Calendar") } // "Calendar" or "Timeline"

    val monthTitleFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val selectedDayFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()) }

    // Determine activities for selected month
    val currentMonthStart = remember(displayedCalendar) {
        val c = displayedCalendar.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.timeInMillis
    }

    val currentMonthEnd = remember(displayedCalendar) {
        val c = displayedCalendar.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.timeInMillis
    }

    val monthActivities = remember(activities, currentMonthStart, currentMonthEnd) {
        activities.filter { act ->
            // An activity belongs to this month if its active range overlaps this month
            act.startDate <= currentMonthEnd && act.endDate >= currentMonthStart
        }.sortedBy { it.endDate }
    }

    // Determine activities on the selected day
    val selectedDayStart = remember(selectedDayCal) {
        val c = selectedDayCal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.timeInMillis
    }

    val selectedDayEnd = remember(selectedDayCal) {
        val c = selectedDayCal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.timeInMillis
    }

    val selectedDayActivities = remember(activities, selectedDayStart, selectedDayEnd) {
        activities.filter { act ->
            act.startDate <= selectedDayEnd && act.endDate >= selectedDayStart
        }.sortedBy { it.endDate }
    }

    if (selectedActivityForDetail != null) {
        ActivityDetailDialog(
            activity = selectedActivityForDetail!!,
            onDismiss = { selectedActivityForDetail = null }
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
                                text = "Activity Calendar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Interactive Monthly Schedule & Deadlines",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Quick Today jump button
                    TextButton(
                        onClick = {
                            val now = Calendar.getInstance()
                            displayedCalendar = (now.clone() as Calendar).apply {
                                set(Calendar.DAY_OF_MONTH, 1)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                            }
                            selectedDayCal = (now.clone() as Calendar).apply {
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                            }
                        }
                    ) {
                        Text("Today", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // View Mode Selector: Segmented Calendar Grid vs Timeline Bars
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val calSelected = viewMode == "Calendar"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (calSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewMode = "Calendar" }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = if (calSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Month Calendar",
                                    fontSize = 13.sp,
                                    fontWeight = if (calSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (calSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val timelineSelected = viewMode == "Timeline"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (timelineSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewMode = "Timeline" }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ViewTimeline,
                                    contentDescription = null,
                                    tint = if (timelineSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Timeline Bars",
                                    fontSize = 13.sp,
                                    fontWeight = if (timelineSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (timelineSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Month Summary & Total Activities Pill Row (cleanly separated from view switcher)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (viewMode == "Timeline") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val newCal = displayedCalendar.clone() as Calendar
                                    newCal.add(Calendar.MONTH, -1)
                                    displayedCalendar = newCal
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month", modifier = Modifier.size(18.dp))
                            }
                            Text(
                                text = monthTitleFormat.format(displayedCalendar.time),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = {
                                    val newCal = displayedCalendar.clone() as Calendar
                                    newCal.add(Calendar.MONTH, 1)
                                    displayedCalendar = newCal
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month", modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        Text(
                            text = "${monthTitleFormat.format(displayedCalendar.time)} Overview",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${monthActivities.size} ${if (monthActivities.size == 1) "Activity" else "Activities"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (viewMode == "Calendar") {
                // Interactive Month Calendar Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Month Header with Prev & Next Arrows
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val newCal = displayedCalendar.clone() as Calendar
                                        newCal.add(Calendar.MONTH, -1)
                                        displayedCalendar = newCal
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                                }

                                Text(
                                    text = monthTitleFormat.format(displayedCalendar.time),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                IconButton(
                                    onClick = {
                                        val newCal = displayedCalendar.clone() as Calendar
                                        newCal.add(Calendar.MONTH, 1)
                                        displayedCalendar = newCal
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Day of Week Header: Mon, Tue, Wed, Thu, Fri, Sat, Sun
                            val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                dayNames.forEach { dayName ->
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(8.dp))

                            // Calendar Days Grid
                            MonthCalendarGrid(
                                displayedMonth = displayedCalendar,
                                selectedDay = selectedDayCal,
                                activities = activities,
                                onSelectDay = { dayCal ->
                                    selectedDayCal = dayCal
                                }
                            )
                        }
                    }
                }

                // Agenda Header for Selected Day
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = selectedDayFormat.format(selectedDayCal.time),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (selectedDayActivities.isEmpty()) "No activities on this date"
                                else "${selectedDayActivities.size} active activity milestone${if (selectedDayActivities.size > 1) "s" else ""}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (selectedDayActivities.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${selectedDayActivities.size}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Day Activities List
                if (selectedDayActivities.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.EventAvailable,
                                    contentDescription = null,
                                    tint = VuGreenPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No Activities Due on This Date",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap on days with colored dots to see active tasks, or switch to Timeline Bars view.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(selectedDayActivities, key = { it.id }) { act ->
                        CalendarDayActivityCard(
                            activity = act,
                            onClick = { selectedActivityForDetail = act }
                        )
                    }
                }
            } else {
                // Timeline Bars View (Full Month Overview)
                if (monthActivities.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("No Activities for ${monthTitleFormat.format(displayedCalendar.time)}", fontWeight = FontWeight.Bold)
                                Text("Sync with VU LMS to refresh coursework.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(monthActivities, key = { it.id }) { act ->
                        TimelineBarCard(
                            activity = act,
                            onClick = { selectedActivityForDetail = act }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * 7xN Calendar Month Grid Composable with Day Cells & Activity Dots
 */
@Composable
private fun MonthCalendarGrid(
    displayedMonth: Calendar,
    selectedDay: Calendar,
    activities: List<ActivityEntity>,
    onSelectDay: (Calendar) -> Unit
) {
    val daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Calculate start day of week (1=Sunday, 2=Monday, ...)
    val firstDayCal = (displayedMonth.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    // Convert so Monday = 0, Tuesday = 1, ..., Sunday = 6
    val firstDayOfWeek = (firstDayCal.get(Calendar.DAY_OF_WEEK) + 5) % 7

    val todayCal = Calendar.getInstance()
    val isCurrentMonthToday = todayCal.get(Calendar.YEAR) == displayedMonth.get(Calendar.YEAR) &&
            todayCal.get(Calendar.MONTH) == displayedMonth.get(Calendar.MONTH)
    val todayDate = todayCal.get(Calendar.DAY_OF_MONTH)

    val isSelectedMonth = selectedDay.get(Calendar.YEAR) == displayedMonth.get(Calendar.YEAR) &&
            selectedDay.get(Calendar.MONTH) == displayedMonth.get(Calendar.MONTH)
    val selectedDate = selectedDay.get(Calendar.DAY_OF_MONTH)

    // Group activities by days they span in this month
    val daysWithActivities = remember(displayedMonth, activities) {
        val map = mutableMapOf<Int, MutableList<ActivityType>>()
        val mStart = (displayedMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val mEnd = (displayedMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, daysInMonth)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }

        for (day in 1..daysInMonth) {
            val dStart = (displayedMonth.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            val dEnd = (displayedMonth.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }.timeInMillis

            val matches = activities.filter { it.startDate <= dEnd && it.endDate >= dStart }
            if (matches.isNotEmpty()) {
                val types = matches.map { ActivityType.fromString(it.activityType, it.title) }.distinct()
                map[day] = types.toMutableList()
            }
        }
        map
    }

    // Total cells = leading blanks + days in month
    val totalCells = firstDayOfWeek + daysInMonth
    val numRows = (totalCells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0 until numRows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - firstDayOfWeek + 1

                    if (dayNum in 1..daysInMonth) {
                        val isToday = isCurrentMonthToday && dayNum == todayDate
                        val isSelected = isSelectedMonth && dayNum == selectedDate
                        val typesForDay = daysWithActivities[dayNum].orEmpty()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    width = if (isToday && !isSelected) 1.5.dp else 0.dp,
                                    color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    val newSelect = (displayedMonth.clone() as Calendar).apply {
                                        set(Calendar.DAY_OF_MONTH, dayNum)
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                    }
                                    onSelectDay(newSelect)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "$dayNum",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isSelected -> Color.White
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )

                                // Activity Indicator Dots
                                if (typesForDay.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        typesForDay.take(3).forEach { t ->
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color.White else t.color)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty spacer cell
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayActivityCard(
    activity: ActivityEntity,
    onClick: () -> Unit
) {
    val type = ActivityType.fromString(activity.activityType, activity.title)
    val now = System.currentTimeMillis()
    val isAvailable = activity.isAvailable(now)
    val isUpcoming = activity.isUpcoming(now)

    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    val startStr = dateFormat.format(Date(activity.startDate))
    val endStr = dateFormat.format(Date(activity.endDate))

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, type.color.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = type.color.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, type.color.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${type.emoji} ${activity.courseCode}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = type.color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        activity.isCompleted -> VuGreenPrimary.copy(alpha = 0.12f)
                        isAvailable -> VuDangerRed.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when {
                            activity.isCompleted -> "✓ Submitted"
                            isAvailable -> "🚨 Active Now"
                            else -> "Opens $startStr"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            activity.isCompleted -> VuGreenPrimary
                            isAvailable -> VuDangerRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = activity.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Opens: $startStr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Deadline: $endStr",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAvailable && !activity.isCompleted) VuDangerRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimelineBarCard(
    activity: ActivityEntity,
    onClick: () -> Unit
) {
    val type = ActivityType.fromString(activity.activityType, activity.title)
    val now = System.currentTimeMillis()
    val isUpcoming = activity.isUpcoming(now)
    val isAvailable = activity.isAvailable(now)

    val totalDurationMillis = (activity.endDate - activity.startDate).coerceAtLeast(1)
    val elapsedMillis = (now - activity.startDate).coerceIn(0, totalDurationMillis)
    val progress = (elapsedMillis.toFloat() / totalDurationMillis.toFloat()).coerceIn(0f, 1f)

    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    val startStr = dateFormat.format(Date(activity.startDate))
    val endStr = dateFormat.format(Date(activity.endDate))

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, type.color.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("timeline_card_${activity.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = type.color.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, type.color.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${type.emoji} ${activity.courseCode}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = type.color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        activity.isCompleted -> VuGreenPrimary.copy(alpha = 0.12f)
                        isAvailable -> VuDangerRed.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when {
                            activity.isCompleted -> "✓ Submitted"
                            isAvailable -> "🚨 Active Now"
                            else -> "Opens $startStr"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            activity.isCompleted -> VuGreenPrimary
                            isAvailable -> VuDangerRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = activity.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // VU LMS Timeline Bar Line representation
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Opens: $startStr",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Deadline: $endStr",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAvailable && !activity.isCompleted) VuDangerRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (activity.isCompleted) 1f else if (isUpcoming) 0.05f else progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (activity.isCompleted) VuGreenPrimary
                                else type.color
                            )
                    )
                }
            }
        }
    }
}
