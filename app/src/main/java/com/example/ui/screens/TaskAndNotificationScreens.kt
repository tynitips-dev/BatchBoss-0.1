package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.NotificationEntity
import com.example.data.local.TaskEntity
import com.example.ui.theme.*
import java.util.Calendar
import java.util.Locale

data class CalendarDayItem(
    val dayNumber: Int,
    val month: Int, // 0-based
    val year: Int,
    val dateString: String, // "yyyy-MM-dd"
    val dayOfWeekShort: String, // "Mon", "Tue", ...
    val isToday: Boolean,
    val isCurrentMonth: Boolean
)

private fun getDaysForMonth(year: Int, month: Int): List<CalendarDayItem?> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }

    val todayCal = Calendar.getInstance()
    val todayYear = todayCal.get(Calendar.YEAR)
    val todayMonth = todayCal.get(Calendar.MONTH)
    val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sun = 1, Mon = 2
    val leadingBlanks = (dayOfWeek + 5) % 7 // Monday = 0, Sunday = 6

    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val list = mutableListOf<CalendarDayItem?>()

    for (i in 0 until leadingBlanks) {
        list.add(null)
    }

    val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    for (day in 1..daysInMonth) {
        cal.set(Calendar.DAY_OF_MONTH, day)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val dateString = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
        val isToday = (year == todayYear && month == todayMonth && day == todayDay)
        list.add(
            CalendarDayItem(
                dayNumber = day,
                month = month,
                year = year,
                dateString = dateString,
                dayOfWeekShort = dayNames[dow - 1],
                isToday = isToday,
                isCurrentMonth = true
            )
        )
    }

    return list
}

private fun getDaysForWeek(selectedDateString: String): List<CalendarDayItem> {
    val cal = Calendar.getInstance()
    val parts = selectedDateString.split("-")
    if (parts.size == 3) {
        cal.set(parts[0].toIntOrNull() ?: 2026, (parts[1].toIntOrNull() ?: 1) - 1, parts[2].toIntOrNull() ?: 1)
    }
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    val diff = (dow + 5) % 7
    cal.add(Calendar.DAY_OF_MONTH, -diff)

    val todayCal = Calendar.getInstance()
    val todayYear = todayCal.get(Calendar.YEAR)
    val todayMonth = todayCal.get(Calendar.MONTH)
    val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

    val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val list = mutableListOf<CalendarDayItem>()

    for (i in 0..6) {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val dw = cal.get(Calendar.DAY_OF_WEEK)
        val dateString = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
        val isToday = (y == todayYear && m == todayMonth && d == todayDay)
        list.add(
            CalendarDayItem(
                dayNumber = d,
                month = m,
                year = y,
                dateString = dateString,
                dayOfWeekShort = dayNames[dw - 1],
                isToday = isToday,
                isCurrentMonth = true
            )
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return list
}

private fun getDayOfWeekForDate(dateString: String): String {
    return try {
        val parts = dateString.split("-")
        if (parts.size == 3) {
            val cal = Calendar.getInstance().apply {
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
        } else "Mon"
    } catch (e: Exception) {
        "Mon"
    }
}

private fun formatReadableDate(dateString: String): String {
    return try {
        val parts = dateString.split("-")
        if (parts.size == 3) {
            val cal = Calendar.getInstance().apply {
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
            val dowNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val dow = dowNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
            val month = monthNames[cal.get(Calendar.MONTH)]
            "$dow, ${parts[2].toInt()} $month ${parts[0]}"
        } else dateString
    } catch (e: Exception) {
        dateString
    }
}

private fun formatShortDate(dateString: String): String {
    return try {
        val parts = dateString.split("-")
        if (parts.size == 3) {
            val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val m = (parts[1].toIntOrNull() ?: 1) - 1
            "${parts[2].toIntOrNull() ?: 1} ${monthNames.getOrElse(m) { "Sep" }}"
        } else dateString
    } catch (e: Exception) {
        dateString
    }
}

@Composable
fun TasksScreen(
    tasks: List<TaskEntity>,
    onBack: () -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
    onAddTask: (title: String, orderRef: String, dueTime: String, priority: String, dueDate: String, dayOfWeek: String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    // Calendar state
    val todayCal = remember { Calendar.getInstance() }
    var displayedYear by remember { mutableStateOf(todayCal.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableStateOf(todayCal.get(Calendar.MONTH)) } // 0..11
    var selectedDateString by remember {
        mutableStateOf(
            String.format(
                Locale.US,
                "%04d-%02d-%02d",
                todayCal.get(Calendar.YEAR),
                todayCal.get(Calendar.MONTH) + 1,
                todayCal.get(Calendar.DAY_OF_MONTH)
            )
        )
    }

    var isFullMonthView by remember { mutableStateOf(true) }
    var filterScope by remember { mutableStateOf("SelectedDate") } // "SelectedDate" or "AllTasks"
    var selectedStatusFilter by remember { mutableStateOf("All") } // "All", "Pending", "InProgress", "Completed"

    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    // Month Navigation Functions
    val moveToPrevMonth = {
        if (displayedMonth == 0) {
            displayedMonth = 11
            displayedYear -= 1
        } else {
            displayedMonth -= 1
        }
    }

    val moveToNextMonth = {
        if (displayedMonth == 11) {
            displayedMonth = 0
            displayedYear += 1
        } else {
            displayedMonth += 1
        }
    }

    val jumpToToday = {
        val now = Calendar.getInstance()
        displayedYear = now.get(Calendar.YEAR)
        displayedMonth = now.get(Calendar.MONTH)
        selectedDateString = String.format(
            Locale.US,
            "%04d-%02d-%02d",
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH)
        )
        filterScope = "SelectedDate"
    }

    // Days for the full month view
    val daysInMonth = remember(displayedYear, displayedMonth) {
        getDaysForMonth(displayedYear, displayedMonth)
    }

    // Days for week strip view
    val daysInWeek = remember(selectedDateString) {
        getDaysForWeek(selectedDateString)
    }

    // Filter tasks based on scope and status
    val selectedDayTasks = remember(tasks, selectedDateString) {
        tasks.filter { task ->
            if (task.dueDate.isNotBlank()) {
                task.dueDate == selectedDateString
            } else {
                val dow = getDayOfWeekForDate(selectedDateString)
                task.dayOfWeek.equals(dow, ignoreCase = true)
            }
        }
    }

    val displayedTasks = remember(tasks, selectedDateString, filterScope, selectedStatusFilter) {
        val baseList = if (filterScope == "SelectedDate") {
            tasks.filter { task ->
                if (task.dueDate.isNotBlank()) {
                    task.dueDate == selectedDateString
                } else {
                    val dow = getDayOfWeekForDate(selectedDateString)
                    task.dayOfWeek.equals(dow, ignoreCase = true)
                }
            }
        } else {
            tasks
        }

        if (selectedStatusFilter == "All") {
            baseList
        } else {
            baseList.filter { it.status == selectedStatusFilter }
        }
    }

    // Counts
    val pendingCount = displayedTasks.count { it.status == "Pending" }
    val inProgressCount = displayedTasks.count { it.status == "InProgress" }
    val completedCount = displayedTasks.count { it.status == "Completed" }

    Scaffold(
        topBar = {
            Surface(
                color = SurfaceWhite,
                shadowElevation = 2.dp,
                modifier = Modifier.statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("btn_tasks_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Column {
                            Text(
                                text = "Tasks & Calendar",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                            Text(
                                text = "${tasks.size} total tasks recorded",
                                fontSize = 11.sp,
                                color = LightText
                            )
                        }
                    }

                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_add_task_trigger")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add Task",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = BatchPink,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New Task", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("fab_add_task")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 80.dp)
        ) {
            // 1. FULL INTERACTIVE CALENDAR CARD
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth().testTag("calendar_section")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Month Header with Navigation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Month Title and Navigation
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = moveToPrevMonth,
                                    modifier = Modifier.size(34.dp).testTag("btn_prev_month")
                                ) {
                                    Icon(
                                        Icons.Filled.ChevronLeft,
                                        contentDescription = "Previous Month",
                                        tint = DarkText
                                    )
                                }

                                Text(
                                    text = "${monthNames[displayedMonth]} $displayedYear",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText,
                                    modifier = Modifier.padding(horizontal = 4.dp).testTag("txt_month_year")
                                )

                                IconButton(
                                    onClick = moveToNextMonth,
                                    modifier = Modifier.size(34.dp).testTag("btn_next_month")
                                ) {
                                    Icon(
                                        Icons.Filled.ChevronRight,
                                        contentDescription = "Next Month",
                                        tint = DarkText
                                    )
                                }
                            }

                            // View Controls (Today & Toggle Month/Week View)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = jumpToToday,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp).testTag("btn_calendar_today")
                                ) {
                                    Text("Today", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BatchPink)
                                }

                                IconButton(
                                    onClick = { isFullMonthView = !isFullMonthView },
                                    modifier = Modifier.size(30.dp).testTag("btn_toggle_calendar_view")
                                ) {
                                    Icon(
                                        imageVector = if (isFullMonthView) Icons.Filled.CalendarViewWeek else Icons.Filled.CalendarMonth,
                                        contentDescription = "Toggle calendar layout",
                                        tint = BatchPink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Day of week labels row: Mon, Tue, Wed, Thu, Fri, Sat, Sun
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                            dayLabels.forEach { label ->
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LightText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        HorizontalDivider(color = DividerColor)

                        // CALENDAR VIEW: Full Month or Week Strip
                        if (isFullMonthView) {
                            // Full Month Grid
                            val weeks = daysInMonth.chunked(7)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                weeks.forEach { week ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        week.forEach { dayItem ->
                                            Box(
                                                modifier = Modifier.weight(1f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (dayItem != null) {
                                                    val isSelected = dayItem.dateString == selectedDateString
                                                    val dayTaskCount = tasks.count {
                                                        it.dueDate == dayItem.dateString || (it.dueDate.isBlank() && it.dayOfWeek.equals(dayItem.dayOfWeekShort, ignoreCase = true))
                                                    }
                                                    val hasPendingTask = tasks.any {
                                                        it.status == "Pending" && (it.dueDate == dayItem.dateString || (it.dueDate.isBlank() && it.dayOfWeek.equals(dayItem.dayOfWeekShort, ignoreCase = true)))
                                                    }
                                                    val hasCompletedTask = tasks.any {
                                                        it.status == "Completed" && (it.dueDate == dayItem.dateString || (it.dueDate.isBlank() && it.dayOfWeek.equals(dayItem.dayOfWeekShort, ignoreCase = true)))
                                                    }

                                                    CalendarDayCell(
                                                        dayNumber = dayItem.dayNumber.toString(),
                                                        isSelected = isSelected,
                                                        isToday = dayItem.isToday,
                                                        hasTasks = dayTaskCount > 0,
                                                        hasPending = hasPendingTask,
                                                        hasCompleted = hasCompletedTask,
                                                        onClick = {
                                                            selectedDateString = dayItem.dateString
                                                            filterScope = "SelectedDate"
                                                        }
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.size(34.dp))
                                                }
                                            }
                                        }
                                        // Fill remaining slots in week if less than 7 days
                                        if (week.size < 7) {
                                            for (i in 0 until (7 - week.size)) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Compact Week View Strip
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                daysInWeek.forEach { dayItem ->
                                    val isSelected = dayItem.dateString == selectedDateString
                                    val dayTaskCount = tasks.count {
                                        it.dueDate == dayItem.dateString || (it.dueDate.isBlank() && it.dayOfWeek.equals(dayItem.dayOfWeekShort, ignoreCase = true))
                                    }
                                    val hasPendingTask = tasks.any {
                                        it.status == "Pending" && (it.dueDate == dayItem.dateString || (it.dueDate.isBlank() && it.dayOfWeek.equals(dayItem.dayOfWeekShort, ignoreCase = true)))
                                    }
                                    val hasCompletedTask = tasks.any {
                                        it.status == "Completed" && (it.dueDate == dayItem.dateString || (it.dueDate.isBlank() && it.dayOfWeek.equals(dayItem.dayOfWeekShort, ignoreCase = true)))
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedDateString = dayItem.dateString
                                                filterScope = "SelectedDate"
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = dayItem.dayOfWeekShort,
                                            fontSize = 11.sp,
                                            color = if (isSelected) BatchPink else LightText,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        CalendarDayCell(
                                            dayNumber = dayItem.dayNumber.toString(),
                                            isSelected = isSelected,
                                            isToday = dayItem.isToday,
                                            hasTasks = dayTaskCount > 0,
                                            hasPending = hasPendingTask,
                                            hasCompleted = hasCompletedTask,
                                            onClick = {
                                                selectedDateString = dayItem.dateString
                                                filterScope = "SelectedDate"
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom calendar legend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Viewing: ${formatReadableDate(selectedDateString)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(BatchPink))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pending", fontSize = 10.sp, color = LightText)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MintGreen))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Done", fontSize = 10.sp, color = LightText)
                                }
                            }
                        }
                    }
                }
            }

            // 2. SCOPE & STATUS FILTERS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Date Scope Chips: Selected Date vs All Tasks
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterScope == "SelectedDate",
                            onClick = { filterScope = "SelectedDate" },
                            label = {
                                Text(
                                    "📅 ${formatShortDate(selectedDateString)} (${selectedDayTasks.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BatchPinkLight,
                                selectedLabelColor = BatchPink
                            ),
                            modifier = Modifier.weight(1f).testTag("chip_filter_selected_date")
                        )

                        FilterChip(
                            selected = filterScope == "AllTasks",
                            onClick = { filterScope = "AllTasks" },
                            label = {
                                Text(
                                    "📋 All Tasks (${tasks.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BatchPinkLight,
                                selectedLabelColor = BatchPink
                            ),
                            modifier = Modifier.weight(1f).testTag("chip_filter_all_tasks")
                        )
                    }

                    // Status Chips: All, Pending, In Progress, Completed
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val statuses = listOf(
                            Pair("All", "All"),
                            Pair("Pending", "Pending"),
                            Pair("InProgress", "In Progress"),
                            Pair("Completed", "Completed")
                        )
                        items(statuses) { (statusKey, statusLabel) ->
                            FilterChip(
                                selected = selectedStatusFilter == statusKey,
                                onClick = { selectedStatusFilter = statusKey },
                                label = { Text(statusLabel, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // 3. SUMMARY STATS CARDS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TaskStatusCard(
                        count = pendingCount,
                        label = "Pending",
                        icon = Icons.Filled.WarningAmber,
                        accentColor = BatchPink,
                        modifier = Modifier.weight(1f)
                    )
                    TaskStatusCard(
                        count = inProgressCount,
                        label = "In Progress",
                        icon = Icons.Filled.HourglassEmpty,
                        accentColor = WarmAmber,
                        modifier = Modifier.weight(1f)
                    )
                    TaskStatusCard(
                        count = completedCount,
                        label = "Completed",
                        icon = Icons.Filled.CheckCircle,
                        accentColor = MintGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. TASKS LIST
            if (displayedTasks.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth().testTag("empty_tasks_view")
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Outlined.EventAvailable,
                                contentDescription = null,
                                tint = BatchPink,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = if (filterScope == "SelectedDate")
                                    "No tasks scheduled for ${formatShortDate(selectedDateString)}"
                                else
                                    "No tasks matching filter",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = DarkText,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Schedule cake baking, pastry batches, decorating, ingredient restocking, or client order deliveries.",
                                fontSize = 12.sp,
                                color = MediumText,
                                textAlign = TextAlign.Center
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Button(
                                    onClick = { showAddDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Task for this Day", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                if (filterScope == "SelectedDate" && tasks.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { filterScope = "AllTasks" },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("View All Tasks (${tasks.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (filterScope == "SelectedDate")
                                "Tasks for ${formatShortDate(selectedDateString)} (${displayedTasks.size})"
                            else
                                "All Scheduled Tasks (${displayedTasks.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )

                        Text(
                            text = "Tap to toggle",
                            fontSize = 11.sp,
                            color = LightText
                        )
                    }
                }

                items(displayedTasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggle = { onToggleTask(task) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            defaultDate = selectedDateString,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, orderRef, dueTime, priority, dueDate, dayOfWeek ->
                onAddTask(title, orderRef, dueTime, priority, dueDate, dayOfWeek)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun CalendarDayCell(
    dayNumber: String,
    isSelected: Boolean,
    isToday: Boolean,
    hasTasks: Boolean,
    hasPending: Boolean,
    hasCompleted: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp, horizontal = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isSelected) BatchPink else Color.Transparent)
                .then(
                    if (isToday && !isSelected)
                        Modifier.border(1.5.dp, BatchPink, CircleShape)
                    else
                        Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dayNumber,
                fontSize = 13.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else if (isToday) BatchPink else DarkText
            )
        }

        // Indicator dots for tasks
        if (hasTasks) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (hasPending) {
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) Color.White else BatchPink))
                }
                if (hasCompleted) {
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) Color.White else MintGreen))
                }
            }
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun TaskStatusCard(
    count: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = count.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 12.sp, color = LightText)
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    onToggle: () -> Unit
) {
    val isDone = task.status == "Completed"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDone) MintGreen.copy(alpha = 0.3f) else BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("task_item_${task.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isDone,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MintGreen,
                    uncheckedColor = LightText
                ),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDone) LightText else DarkText,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.orderRef.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BackgroundLight
                        ) {
                            Text(
                                text = task.orderRef,
                                fontSize = 11.sp,
                                color = MediumText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (task.dueTime.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = LightText, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = task.dueTime,
                                fontSize = 11.sp,
                                color = LightText
                            )
                        }
                    }

                    if (task.dueDate.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = LightText, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = formatShortDate(task.dueDate),
                                fontSize = 11.sp,
                                color = LightText
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Priority badge
            val badgeColor = when (task.priority) {
                "High" -> BatchPinkContainer
                "Medium" -> AmberLight
                else -> MintLight
            }
            val textColor = when (task.priority) {
                "High" -> BatchPink
                "Medium" -> WarmAmber
                else -> MintGreen
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = task.priority,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (isDone) {
                    Text("✓ Done", fontSize = 10.sp, color = MintGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddTaskDialog(
    defaultDate: String = "",
    onDismiss: () -> Unit,
    onConfirm: (title: String, orderRef: String, dueTime: String, priority: String, dueDate: String, dayOfWeek: String) -> Unit
) {
    val todayCal = remember { Calendar.getInstance() }
    val todayFormatted = remember {
        String.format(
            Locale.US,
            "%04d-%02d-%02d",
            todayCal.get(Calendar.YEAR),
            todayCal.get(Calendar.MONTH) + 1,
            todayCal.get(Calendar.DAY_OF_MONTH)
        )
    }

    var title by remember { mutableStateOf("") }
    var orderRef by remember { mutableStateOf("") }
    var dueTime by remember { mutableStateOf("12:00 PM") }
    var dueDate by remember { mutableStateOf(defaultDate.ifBlank { todayFormatted }) }
    var priority by remember { mutableStateOf("High") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Bakery Task", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Name (e.g. Bake Red Velvet Cake)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_task_title")
                )

                OutlinedTextField(
                    value = orderRef,
                    onValueChange = { orderRef = it },
                    label = { Text("Order Ref (e.g. Order #ORD-1028)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_task_order_ref")
                )

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date (YYYY-MM-DD)") },
                    placeholder = { Text("e.g. 2026-09-14") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_task_due_date")
                )

                // Quick Date selector chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dueDate == todayFormatted,
                        onClick = { dueDate = todayFormatted },
                        label = { Text("Today", fontSize = 11.sp) }
                    )

                    val tomorrowFormatted = remember {
                        val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
                        String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            tomorrowCal.get(Calendar.YEAR),
                            tomorrowCal.get(Calendar.MONTH) + 1,
                            tomorrowCal.get(Calendar.DAY_OF_MONTH)
                        )
                    }
                    FilterChip(
                        selected = dueDate == tomorrowFormatted,
                        onClick = { dueDate = tomorrowFormatted },
                        label = { Text("Tomorrow", fontSize = 11.sp) }
                    )
                }

                OutlinedTextField(
                    value = dueTime,
                    onValueChange = { dueTime = it },
                    label = { Text("Due Time (e.g. 10:00 AM or 2:30 PM)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_task_due_time")
                )

                Text("Priority", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("High", "Medium", "Low").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val finalDate = dueDate.ifBlank { todayFormatted }
                        val dow = getDayOfWeekForDate(finalDate)
                        onConfirm(
                            title,
                            orderRef.ifBlank { "Bakery Task" },
                            if (dueTime.startsWith("Due", ignoreCase = true)) dueTime else "Due $dueTime",
                            priority,
                            finalDate,
                            dow
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                modifier = Modifier.testTag("btn_confirm_add_task")
            ) {
                Text("Add Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NotificationsScreen(
    notifications: List<NotificationEntity>,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit,
    onNotificationClick: ((NotificationEntity) -> Unit)? = null
) {
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredNotifications = remember(notifications, selectedFilter) {
        if (selectedFilter == "All") notifications
        else notifications.filter { it.type == selectedFilter }
    }

    val todayNotifications = filteredNotifications.filter { it.dateGroup == "Today" }
    val yesterdayNotifications = filteredNotifications.filter { it.dateGroup == "Yesterday" }

    Scaffold(
        topBar = {
            Surface(
                color = SurfaceWhite,
                shadowElevation = 2.dp,
                modifier = Modifier.statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("btn_notif_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = "Notifications",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onMarkAllRead, modifier = Modifier.testTag("btn_mark_all_read")) {
                            Text(
                                text = "Mark all as read",
                                fontSize = 13.sp,
                                color = BatchPink,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Outlined.FilterList, contentDescription = "Filter", tint = DarkText)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // Filter Tabs (All, Alerts, Orders, System)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Alerts", "Orders", "System").forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) BatchPink else SurfaceWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) BatchPink else BorderLight
                            ),
                            modifier = Modifier
                                .clickable { selectedFilter = filter }
                                .testTag("filter_$filter")
                        ) {
                            Text(
                                text = filter,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else DarkText,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Today Section
            if (todayNotifications.isNotEmpty()) {
                item {
                    Text(
                        text = "Today",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                }
                items(todayNotifications) { notif ->
                    NotificationCard(
                        notif = notif,
                        onClick = onNotificationClick?.let { { it(notif) } }
                    )
                }
            }

            // Yesterday Section
            if (yesterdayNotifications.isNotEmpty()) {
                item {
                    Text(
                        text = "Yesterday",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(yesterdayNotifications) { notif ->
                    NotificationCard(
                        notif = notif,
                        onClick = onNotificationClick?.let { { it(notif) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notif: NotificationEntity,
    onClick: (() -> Unit)? = null
) {
    val isWelcome = notif.title.contains("Welcome", ignoreCase = true)
    val icon = when {
        isWelcome -> Icons.Outlined.MarkEmailRead
        notif.type == "Alerts" -> Icons.Outlined.WarningAmber
        notif.type == "Orders" -> Icons.Outlined.Receipt
        else -> Icons.Outlined.Settings
    }
    val iconColor = when {
        isWelcome -> BatchPink
        notif.type == "Alerts" -> WarmAmber
        notif.type == "Orders" -> SoftBlue
        else -> MediumText
    }
    val iconBg = when {
        isWelcome -> BatchPinkLight
        notif.type == "Alerts" -> AmberLight
        notif.type == "Orders" -> BlueLight
        else -> BorderLight
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isWelcome) BatchPink.copy(alpha = 0.4f) else BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notif.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Text(
                        text = notif.timeLabel,
                        fontSize = 12.sp,
                        color = LightText
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = notif.message,
                    fontSize = 13.sp,
                    color = MediumText,
                    lineHeight = 18.sp
                )

                if (isWelcome) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Tap to open welcome letter & starter guide",
                            color = BatchPink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = BatchPink, modifier = Modifier.size(13.dp))
                    }
                }
            }

            if (notif.isUnread) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BatchPink)
                )
            }
        }
    }
}
