package com.xovrix.quickvoicereminder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xovrix.quickvoicereminder.ai.ParsedTaskResult
import com.xovrix.quickvoicereminder.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParsedReviewSheet(
    parsed: ParsedTaskResult,
    onConfirm: (ParsedTaskResult) -> Unit,
    onDismiss: () -> Unit
) {
    val maxDialogHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.9f
    }
    var editableTask by remember { mutableStateOf(parsed.task) }
    var editableDueDate by remember { mutableStateOf(parsed.dueDate.orEmpty()) }
    var editableDueTime by remember { mutableStateOf(parsed.dueTime.orEmpty()) }
    var editableCategory by remember { mutableStateOf(parsed.category) }
    var editableIsNag by remember { mutableStateOf(parsed.isNag) }
    var editableLocation by remember { mutableStateOf(parsed.location ?: "") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showLocationEdit by remember { mutableStateOf(false) }
    var showTaskEdit by remember { mutableStateOf(false) }

    val categoryColor = CategoryUtils.getCategoryColor(editableCategory)
    val categoryIcon = CategoryUtils.getCategoryIcon(editableCategory)
    val formattedDue = if (editableDueDate.isBlank()) "No schedule" else CategoryUtils.formatDueString(editableDueDate, editableDueTime)

    if (showDatePicker) {
        val initialEpoch = try {
            LocalDate.parse(editableDueDate).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialEpoch)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        editableDueDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                        showTimePicker = true
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val (h, m) = try {
            val p = editableDueTime.split(":")
            p[0].toInt() to p[1].toInt()
        } catch (_: Exception) { 9 to 0 }
        
        val timePickerState = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    editableDueTime = "${timePickerState.hour.toString().padStart(2, '0')}:${timePickerState.minute.toString().padStart(2, '0')}"
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select Time", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            confirmButton = { TextButton(onClick = { showCategoryPicker = false }) { Text("Close") } },
            title = { Text("Select Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryUtils.ALL_CATEGORIES.forEach { cat ->
                        Surface(
                            onClick = {
                                editableCategory = cat
                                showCategoryPicker = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (editableCategory == cat) CategoryUtils.getCategoryColor(cat).copy(alpha = 0.1f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(CategoryUtils.getCategoryIcon(cat), contentDescription = null, tint = CategoryUtils.getCategoryColor(cat))
                                Spacer(Modifier.width(12.dp))
                                Text(cat, fontWeight = if (editableCategory == cat) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        )
    }

    if (showLocationEdit) {
        var tempLoc by remember { mutableStateOf(editableLocation) }
        AlertDialog(
            onDismissRequest = { showLocationEdit = false },
            confirmButton = {
                TextButton(onClick = {
                    editableLocation = tempLoc
                    showLocationEdit = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showLocationEdit = false }) { Text("Cancel") }
            },
            title = { Text("Edit Location") },
            text = {
                OutlinedTextField(
                    value = tempLoc,
                    onValueChange = { tempLoc = it },
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    if (showTaskEdit) {
        var tempTask by remember { mutableStateOf(editableTask) }
        AlertDialog(
            onDismissRequest = { showTaskEdit = false },
            confirmButton = {
                TextButton(onClick = {
                    if (tempTask.isNotBlank()) {
                        editableTask = tempTask
                        showTaskEdit = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showTaskEdit = false }) { Text("Cancel") }
            },
            title = { Text("Edit Task") },
            text = {
                OutlinedTextField(
                    value = tempTask,
                    onValueChange = { tempTask = it },
                    label = { Text("Task Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .heightIn(max = maxDialogHeight)
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ElectricIndigo.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "AI Parsed",
                                tint = ElectricIndigo,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = if (parsed.isAiParsed) "AI Extracted Reminder" else "Extracted Reminder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Task Action Title
                Surface(
                    onClick = { showTaskEdit = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACTION ITEM",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = editableTask,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Task",
                            tint = ElectricIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Details grid
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Due Date & Time
                    DetailItem(
                        icon = Icons.Outlined.Schedule,
                        iconTint = ElectricIndigo,
                        label = "Schedule",
                        value = formattedDue,
                        onClick = { showDatePicker = true }
                    )
                    if (editableDueDate.isNotBlank()) {
                        TextButton(onClick = {
                            editableDueDate = ""
                            editableDueTime = ""
                        }) { Text("Remove schedule") }
                    }

                    // Category
                    DetailItem(
                        icon = categoryIcon,
                        iconTint = categoryColor,
                        label = "Category",
                        value = editableCategory,
                        onClick = { showCategoryPicker = true }
                    )

                    // Location if present
                    DetailItem(
                        icon = Icons.Outlined.LocationOn,
                        iconTint = CyanAccent,
                        label = "Location",
                        value = if (editableLocation.isBlank()) "No location" else editableLocation,
                        onClick = { showLocationEdit = true }
                    )

                    // Nag Mode Status
                    DetailItem(
                        icon = Icons.Filled.LocalFireDepartment,
                        iconTint = if (editableIsNag) AmberFlame else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "Urgency Mode",
                        value = if (editableIsNag) "⚡ Nag Mode Active" else "Standard Alert",
                        onClick = { editableIsNag = !editableIsNag }
                    )
                    if (parsed.isRecurring) {
                        DetailItem(
                            icon = Icons.Outlined.Repeat,
                            iconTint = ElectricIndigo,
                            label = "Repeats",
                            value = buildString {
                                append(parsed.recurrence ?: "Recurring")
                                parsed.recurrenceDays?.takeIf { it.isNotEmpty() }?.let { append(" · ${it.joinToString()}") }
                            },
                            onClick = { }
                        )
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Discard")
                    }

                    Button(
                        onClick = {
                            onConfirm(editableTask.let { 
                                parsed.copy(
                                    task = editableTask,
                                    dueDate = editableDueDate.ifBlank { null },
                                    dueTime = editableDueTime.ifBlank { null },
                                    category = editableCategory,
                                    isNag = editableIsNag,
                                    location = editableLocation.ifBlank { null }
                                )
                            })
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_parsed_task_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                    ) {
                        Text("Schedule", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
