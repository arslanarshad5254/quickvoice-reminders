package com.xovrix.quickvoicereminder.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.xovrix.quickvoicereminder.data.TaskEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(task: TaskEntity) {
        if (task.isCompleted) return

        val triggerAtEpoch = calculateTriggerEpoch(task) ?: return

        // If time has already passed, do not schedule
        if (triggerAtEpoch <= System.currentTimeMillis()) {
            Log.d(TAG, "Task ${task.id} scheduled time is in the past, skipping schedule.")
            return
        }

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TASK_TITLE, task.task)
            putExtra(EXTRA_IS_NAG, task.isNag)
            putExtra(EXTRA_CATEGORY, task.category)
            putExtra(EXTRA_LOCATION, task.location)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtEpoch,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtEpoch,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtEpoch,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled alarm for task ${task.id} (${task.task}) at epoch $triggerAtEpoch")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling exact alarm: ${e.message}")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtEpoch,
                pendingIntent
            )
        }
    }

    fun scheduleNagRepeat(taskId: Long, taskTitle: String, category: String, location: String?) {
        val nextTrigger = System.currentTimeMillis() + (5 * 60 * 1000L) // 5 minutes
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
            putExtra(EXTRA_IS_NAG, true)
            putExtra(EXTRA_CATEGORY, category)
            putExtra(EXTRA_LOCATION, location)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTrigger,
                pendingIntent
            )
            Log.d(TAG, "Scheduled Nag repeat for task $taskId in 5 mins")
        } catch (e: Exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
        }
    }

    fun scheduleSnooze(taskId: Long, taskTitle: String, minutes: Int = 10) {
        val nextTrigger = System.currentTimeMillis() + (minutes * 60 * 1000L)
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
            putExtra(EXTRA_IS_NAG, false)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            Log.d(TAG, "Scheduling snooze for task $taskId in $minutes minutes (at $nextTrigger)")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTrigger,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTrigger,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTrigger,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling snooze: ${e.message}")
            alarmManager.set(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
        }
    }

    fun cancel(taskId: Long) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for task $taskId")
        }
    }

    private fun calculateTriggerEpoch(task: TaskEntity): Long? {
        if (!task.scheduledDateTime.isNullOrBlank()) {
            return try {
                LocalDateTime.parse(task.scheduledDateTime)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (e: Exception) {
                null
            }
        }

        if (task.dueDate.isNullOrBlank()) return null

        return try {
            val date = LocalDate.parse(task.dueDate)
            val time = if (!task.dueTime.isNullOrBlank()) {
                LocalTime.parse(task.dueTime)
            } else {
                LocalTime.of(9, 0)
            }
            LocalDateTime.of(date, time)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val TAG = "ReminderAlarmScheduler"
        const val ACTION_TRIGGER_REMINDER = "com.xovrix.quickvoicereminder.ACTION_TRIGGER_REMINDER"
        const val EXTRA_TASK_ID = "TASK_ID"
        const val EXTRA_TASK_TITLE = "TASK_TITLE"
        const val EXTRA_IS_NAG = "IS_NAG"
        const val EXTRA_CATEGORY = "CATEGORY"
        const val EXTRA_LOCATION = "LOCATION"
    }
}
