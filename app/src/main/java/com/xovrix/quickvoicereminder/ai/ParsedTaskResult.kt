package com.xovrix.quickvoicereminder.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ParsedTaskResult(
    @Json(name = "task") val task: String,
    @Json(name = "due_date") val dueDate: String? = null,      // YYYY-MM-DD
    @Json(name = "due_time") val dueTime: String? = null,      // HH:mm
    @Json(name = "category") val category: String = "General", // Personal, Work, Errands, Health, Finance, General
    @Json(name = "location") val location: String? = null,
    @Json(name = "is_nag") val isNag: Boolean = false,
    val isAiParsed: Boolean = true
) {
    val scheduledDateTime: String?
        get() {
            if (dueDate.isNullOrBlank()) return null
            val time = if (!dueTime.isNullOrBlank()) dueTime else "09:00"
            return "${dueDate}T${if (time.length == 5) "$time:00" else time}"
        }
}
