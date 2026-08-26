package com.xovrix.quickvoicereminder.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.xovrix.quickvoicereminder.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val taskId = intent.getLongExtra(ReminderAlarmScheduler.EXTRA_TASK_ID, -1L)
        val notifId = intent.getIntExtra("NOTIFICATION_ID", taskId.toInt())

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notifId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val scheduler = ReminderAlarmScheduler(context)

                when (action) {
                    ACTION_COMPLETE_TASK -> {
                        if (taskId > 0) {
                            Log.d(TAG, "Marking task $taskId as completed from notification")
                            db.taskDao().setTaskCompletion(
                                id = taskId,
                                completed = true,
                                completedAt = LocalDateTime.now().toString()
                            )
                            scheduler.cancel(taskId)
                        }
                    }
                    ACTION_SNOOZE_TASK -> {
                        val taskTitle = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_TASK_TITLE) ?: "Reminder"
                        if (taskId > 0) {
                            Log.d(TAG, "Snoozing task $taskId for 10 minutes")
                            
                            // Update task time in DB
                            val task = db.taskDao().getTaskById(taskId)
                            if (task != null) {
                                val newTime = LocalDateTime.now().plusMinutes(10)
                                val updatedTask = task.copy(
                                    dueDate = newTime.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                    dueTime = newTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                    scheduledDateTime = newTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                )
                                db.taskDao().updateTask(updatedTask)
                                scheduler.schedule(updatedTask)
                            } else {
                                scheduler.scheduleSnooze(taskId, taskTitle, 10)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling notification action $action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val TAG = "NotificationActionRx"
        const val ACTION_COMPLETE_TASK = "com.xovrix.quickvoicereminder.ACTION_COMPLETE_TASK"
        const val ACTION_SNOOZE_TASK = "com.xovrix.quickvoicereminder.ACTION_SNOOZE_TASK"
    }
}
