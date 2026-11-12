package com.xovrix.quickvoicereminder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["isCompleted", "scheduledDateTime"]),
        Index(value = ["completedAt"])
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val task: String,
    val dueDate: String? = null,           // YYYY-MM-DD
    val dueTime: String? = null,           // HH:mm (24-hour)
    val scheduledDateTime: String? = null, // ISO-8601 combined (YYYY-MM-DDTHH:mm:ss) for indexing/sorting
    val category: String = "General",      // Personal, Work, Errands, Health, Finance, General
    val location: String? = null,          // Location or geofence trigger
    val isNag: Boolean = false,            // Persistent periodic notification alerts until completed
    val endDate: String? = null,
    val endTime: String? = null,
    val isAllDay: Boolean = false,
    val isRecurring: Boolean = false,
    val recurrence: String? = null,
    val recurrenceDays: String? = null,    // Comma-separated DayOfWeek names
    val recurrenceIntervalDays: Int? = null,
    val isCompleted: Boolean = false,
    val completedAt: String? = null,
    val createdAt: String = LocalDateTime.now().toString(),
    val parseSource: String = "manual"
)

internal fun TaskEntity.normalizedForStorage(): TaskEntity {
    val normalizedTask = task.trim().replace(Regex("""\s+"""), " ").take(200)
    require(normalizedTask.isNotBlank()) { "Reminder title cannot be blank" }

    val validDate = dueDate?.takeIf { value -> runCatching { LocalDate.parse(value) }.isSuccess }
    val validTime = dueTime?.takeIf { value -> runCatching { LocalTime.parse(value) }.isSuccess }
        ?.take(5)
    val storedTime = validTime.takeIf { validDate != null && !isAllDay }
    val validEndDate = endDate
        ?.takeIf { value -> runCatching { LocalDate.parse(value) }.isSuccess }
        ?.takeIf { validDate != null }
    val validEndTime = endTime?.takeIf { value -> runCatching { LocalTime.parse(value) }.isSuccess }
        ?.take(5)
        ?.takeIf { validEndDate != null && !isAllDay }
    val validCategories = setOf("Personal", "Work", "Errands", "Health", "Finance", "General")
    val normalizedCategory = validCategories.firstOrNull { it.equals(category, ignoreCase = true) }
        ?: "General"
    val validRecurrences = setOf("daily", "weekly", "monthly", "yearly", "custom")
    val normalizedRecurrence = recurrence?.lowercase()?.takeIf { it in validRecurrences }
    val recurring = isRecurring && normalizedRecurrence != null
    val normalizedDays = recurrenceDays
        ?.split(',')
        ?.map { it.trim().uppercase() }
        ?.filter { it in WEEKDAYS }
        ?.distinct()
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(",")
    return copy(
        task = normalizedTask,
        dueDate = validDate,
        dueTime = storedTime,
        scheduledDateTime = validDate?.let { "${it}T${storedTime ?: "09:00"}:00" },
        category = normalizedCategory,
        location = location?.trim()?.replace(Regex("""\s+"""), " ")?.take(120)?.ifBlank { null },
        endDate = validEndDate,
        endTime = validEndTime,
        isRecurring = recurring,
        recurrence = normalizedRecurrence.takeIf { recurring },
        recurrenceDays = normalizedDays.takeIf { recurring },
        recurrenceIntervalDays = recurrenceIntervalDays?.takeIf { recurring && it in 1..3650 },
        parseSource = parseSource.takeIf { it in setOf("manual", "local", "ai") } ?: "manual"
    )
}

private val WEEKDAYS = setOf(
    "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
)
