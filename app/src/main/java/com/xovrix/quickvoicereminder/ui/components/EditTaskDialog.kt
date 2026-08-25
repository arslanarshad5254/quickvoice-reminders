package com.xovrix.quickvoicereminder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xovrix.quickvoicereminder.data.TaskEntity
import com.xovrix.quickvoicereminder.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskDialog(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit
) {
    var taskTitle by remember { mutableStateOf(task.task) }
    var dueDate by remember { mutableStateOf(task.dueDate ?: LocalDate.now().toString()) }
    var dueTime by remember { mutableStateOf(task.dueTime ?: "09:00") }
    var category by remember { mutableStateOf(task.category) }
    var location by remember { mutableStateOf(task.location ?: "") }
    var isNag by remember { mutableStateOf(task.isNag) }
    var hasError by remember { mutableStateOf(false) }

    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }

    // Material 3 Date Picker Dialog
    if (showDatePickerDialog) {
        val initialEpochMillis = remember(dueDate) {
            try {
                LocalDate.parse(dueDate).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialEpochMillis
        )

        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedLocalDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            dueDate = selectedLocalDate.toString()
                        }
                        showDatePickerDialog = false
                    },
                    modifier = Modifier.testTag("date_picker_confirm_button")
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Material 3 Time Picker Dialog
    if (showTimePickerDialog) {
        val (initHour, initMinute) = remember(dueTime) {
            try {
                val parts = dueTime.split(":")
                Pair(parts[0].toInt(), parts[1].toInt())
            } catch (_: Exception) {
                Pair(9, 0)
            }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = initHour,
            initialMinute = initMinute,
            is24Hour = false
        )

        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val formattedH = timePickerState.hour.toString().padStart(2, '0')
                        val formattedM = timePickerState.minute.toString().padStart(2, '0')
                        dueTime = "$formattedH:$formattedM"
                        showTimePickerDialog = false
                    },
                    modifier = Modifier.testTag("time_picker_confirm_button")
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Cancel")
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Select Due Time",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .imePadding()
                .clip(RoundedCornerShape(28.dp))
                .testTag("edit_task_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ElectricIndigo.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EditCalendar,
                                contentDescription = null,
                                tint = ElectricIndigo,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (task.id == 0L) "Create" else "Edit Reminder",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (task.id == 0L) "Set title, date, time & settings" else "Modify title, date, time & settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Task Name Field
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = {
                        taskTitle = it
                        if (it.isNotBlank()) hasError = false
                    },
                    label = { Text("Reminder Title / Task *") },
                    placeholder = { Text("e.g., Buy groceries, Dentist appointment") },
                    isError = hasError,
                    supportingText = {
                        if (hasError) {
                            Text("Title cannot be empty", color = CoralRed)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_task_name_input"),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = false,
                    maxLines = 3
                )

                // Date Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Due Date",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { showDatePickerDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("open_date_picker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open Calendar", fontSize = 13.sp)
                        }
                    }

                    // Quick Date Chips
                    val dateToday = today.toString()
                    val dateTomorrow = today.plusDays(1).toString()
                    val dateNextWeek = today.plusWeeks(1).toString()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = dueDate == dateToday,
                            onClick = { dueDate = dateToday },
                            label = { Text("Today") },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = dueDate == dateTomorrow,
                            onClick = { dueDate = dateTomorrow },
                            label = { Text("Tomorrow") },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = dueDate == dateNextWeek,
                            onClick = { dueDate = dateNextWeek },
                            label = { Text("In 1 Week") },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        readOnly = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Event,
                                contentDescription = null,
                                tint = ElectricIndigo,
                                modifier = Modifier.clickable { showDatePickerDialog = true }
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showDatePickerDialog = true }) {
                                Icon(Icons.Filled.EditCalendar, contentDescription = "Pick Date", tint = ElectricIndigo)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePickerDialog = true }
                            .testTag("edit_task_date_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Time Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Due Time",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { showTimePickerDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("open_time_picker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pick Time", fontSize = 13.sp)
                        }
                    }

                    // Quick Time Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "09:00" to "Morning (9 AM)",
                            "13:00" to "Afternoon (1 PM)",
                            "18:00" to "Evening (6 PM)",
                            "21:00" to "Night (9 PM)"
                        ).forEach { (t, label) ->
                            FilterChip(
                                selected = dueTime == t,
                                onClick = { dueTime = t },
                                label = { Text(label) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = CategoryUtils.format12HourTime(dueTime),
                        onValueChange = { },
                        label = { Text("Due Time") },
                        readOnly = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = ElectricIndigo,
                                modifier = Modifier.clickable { showTimePickerDialog = true }
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showTimePickerDialog = true }) {
                                Icon(Icons.Filled.AccessTime, contentDescription = "Pick Time", tint = ElectricIndigo)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimePickerDialog = true }
                            .testTag("edit_task_time_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Category Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryUtils.ALL_CATEGORIES.forEach { cat ->
                            val isSelected = category.equals(cat, ignoreCase = true)
                            val catColor = CategoryUtils.getCategoryColor(cat)
                            val catIcon = CategoryUtils.getCategoryIcon(cat)
                            FilterChip(
                                selected = isSelected,
                                onClick = { category = cat },
                                label = {
                                    Text(
                                        text = cat,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = catIcon,
                                        contentDescription = cat,
                                        tint = if (isSelected) catColor else catColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) catColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    selectedBorderColor = catColor,
                                    borderWidth = if (isSelected) 1.5.dp else 1.dp
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = catColor.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }

                // Location Input
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (Optional)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = "Location",
                            tint = CyanAccent
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_task_location_input"),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                // Urgent Nag Mode Toggle
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isNag) AmberFlame.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isNag) AmberFlame.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isNag) AmberFlame else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalFireDepartment,
                                    contentDescription = "Nag Mode",
                                    tint = if (isNag) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Persistent Nag Mode",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNag) AmberFlame else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Repeats alert every 5 mins until completed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isNag,
                            onCheckedChange = { isNag = it },
                            modifier = Modifier.testTag("edit_nag_mode_switch")
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("edit_cancel_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (taskTitle.trim().isBlank()) {
                                hasError = true
                                return@Button
                            }

                            val scheduledDateTime = if (dueDate.isNotBlank()) {
                                val time = if (dueTime.isNotBlank()) dueTime else "09:00"
                                "${dueDate}T${if (time.length == 5) "$time:00" else time}"
                            } else null

                            val updated = task.copy(
                                id = task.id,
                                task = taskTitle.trim(),
                                dueDate = dueDate.ifBlank { null },
                                dueTime = dueTime.ifBlank { null },
                                scheduledDateTime = scheduledDateTime,
                                category = category,
                                location = location.trim().ifBlank { null },
                                isNag = isNag
                            )
                            onSave(updated)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("edit_save_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                    ) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (task.id == 0L) "Create" else "Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

