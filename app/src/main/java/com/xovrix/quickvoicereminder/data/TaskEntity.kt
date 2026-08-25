package com.xovrix.quickvoicereminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val task: String,
    val dueDate: String? = null,           // YYYY-MM-DD
    val dueTime: String? = null,           // HH:mm (24-hour)
    val scheduledDateTime: String? = null, // ISO-8601 combined (YYYY-MM-DDTHH:mm:ss) for indexing/sorting
    val category: String = "General",      // Personal, Work, Errands, Health, Finance, General
    val location: String? = null,          // Location or geofence trigger
    val isNag: Boolean = false,            // Persistent periodic notification alerts until completed
    val isCompleted: Boolean = false,
    val completedAt: String? = null,
    val createdAt: String = LocalDateTime.now().toString()
)
