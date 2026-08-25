package com.xovrix.quickvoicereminder.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.xovrix.quickvoicereminder.ui.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object CategoryUtils {
    val ALL_CATEGORIES = listOf("Personal", "Work", "Errands", "Health", "Finance", "Urgent", "General")

    fun getCategoryColor(category: String): Color {
        return when (category.lowercase()) {
            "personal" -> CategoryPersonal
            "work" -> CategoryWork
            "errands", "shopping" -> CategoryErrands
            "health", "fitness" -> CategoryHealth
            "finance", "bills" -> CategoryFinance
            "urgent", "important" -> CoralRed
            else -> CategoryGeneral
        }
    }

    fun getCategoryIcon(category: String): ImageVector {
        return when (category.lowercase()) {
            "personal" -> Icons.Filled.Person
            "work" -> Icons.Filled.Work
            "errands", "shopping" -> Icons.Filled.ShoppingCart
            "health", "fitness" -> Icons.Filled.Favorite
            "finance", "bills" -> Icons.Filled.AccountBalance
            "urgent", "important" -> Icons.Filled.PriorityHigh
            else -> Icons.Filled.PushPin
        }
    }

    fun formatDueString(dueDate: String?, dueTime: String?): String {
        if (dueDate.isNullOrBlank()) return "No due date"

        return try {
            val date = LocalDate.parse(dueDate)
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            val dateText = when (date) {
                today -> "Today"
                tomorrow -> "Tomorrow"
                today.minusDays(1) -> "Yesterday"
                else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
            }

            if (!dueTime.isNullOrBlank()) {
                val time = try {
                    LocalTime.parse(dueTime)
                } catch (e: Exception) {
                    // Try parsing 12h format if 24h fails
                    LocalTime.parse(dueTime, DateTimeFormatter.ofPattern("h:mm a"))
                }
                val timeText = time.format(DateTimeFormatter.ofPattern("h:mm a"))
                "$dateText at $timeText"
            } else {
                dateText
            }
        } catch (e: Exception) {
            dueDate + (if (!dueTime.isNullOrBlank()) " $dueTime" else "")
        }
    }

    fun format12HourTime(timeStr: String?): String {
        if (timeStr.isNullOrBlank()) return ""
        return try {
            val time = LocalTime.parse(timeStr)
            time.format(DateTimeFormatter.ofPattern("h:mm a"))
        } catch (e: Exception) {
            timeStr
        }
    }

    fun parseTo24HourTime(time12h: String?): String? {
        if (time12h.isNullOrBlank()) return null
        return try {
            val time = LocalTime.parse(time12h, DateTimeFormatter.ofPattern("h:mm a"))
            time.format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            try {
                // If already in 24h format
                val time = LocalTime.parse(time12h)
                time.format(DateTimeFormatter.ofPattern("HH:mm"))
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun getRelativeTimeCountdown(dueDate: String?, dueTime: String?): Pair<String, Boolean> {
        if (dueDate.isNullOrBlank()) return Pair("", false)
        return try {
            val date = LocalDate.parse(dueDate)
            val time = if (!dueTime.isNullOrBlank()) LocalTime.parse(dueTime) else LocalTime.of(9, 0)
            val targetDateTime = LocalDateTime.of(date, time)
            val now = LocalDateTime.now()

            val minutesDiff = ChronoUnit.MINUTES.between(now, targetDateTime)

            if (minutesDiff < 0) {
                val absMins = Math.abs(minutesDiff)
                if (absMins < 60) {
                    Pair("Overdue by ${absMins}m", true)
                } else if (absMins < 1440) {
                    Pair("Overdue by ${absMins / 60}h", true)
                } else {
                    Pair("Overdue by ${absMins / 1440}d", true)
                }
            } else if (minutesDiff < 60) {
                Pair("In ${minutesDiff}m", false)
            } else if (minutesDiff < 1440) {
                Pair("In ${minutesDiff / 60}h", false)
            } else {
                val days = minutesDiff / 1440
                Pair("In ${days}d", false)
            }
        } catch (e: Exception) {
            Pair("", false)
        }
    }
}
