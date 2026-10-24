package com.xovrix.quickvoicereminder.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ParsedTaskResult(
    val task: String,
    @param:Json(name = "due_date") val dueDate: String? = null,
    @param:Json(name = "due_time") val dueTime: String? = null,
    @param:Json(name = "end_date") val endDate: String? = null,
    @param:Json(name = "end_time") val endTime: String? = null,
    @param:Json(name = "is_all_day") val isAllDay: Boolean = false,
    val category: String = "General",
    val location: String? = null,
    @param:Json(name = "is_nag") val isNag: Boolean = false,
    @param:Json(name = "is_recurring") val isRecurring: Boolean = false,
    val recurrence: String? = null,
    @param:Json(name = "recurrence_days") val recurrenceDays: List<String>? = null,
    @param:Json(name = "recurrence_interval_days") val recurrenceIntervalDays: Int? = null,
    val isAiParsed: Boolean = false
) {
    val scheduledDateTime: String?
        get() {
            if (dueDate.isNullOrBlank()) return null
            val time = if (!dueTime.isNullOrBlank()) dueTime else "09:00"
            return "${dueDate}T${if (time.length == 5) "$time:00" else time}"
        }
}
