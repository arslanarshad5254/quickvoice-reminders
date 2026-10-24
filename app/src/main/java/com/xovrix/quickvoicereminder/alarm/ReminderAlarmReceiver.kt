package com.xovrix.quickvoicereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xovrix.quickvoicereminder.QuickVoiceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderAlarmScheduler.EXTRA_TASK_ID, -1L)
        val taskTitle = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_TASK_TITLE) ?: "Reminder"
        val isNag = intent.getBooleanExtra(ReminderAlarmScheduler.EXTRA_IS_NAG, false)
        val isNagRepeat = intent.getBooleanExtra(ReminderAlarmScheduler.EXTRA_IS_NAG_REPEAT, false)
        val category = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_CATEGORY) ?: "General"
        val location = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_LOCATION)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as QuickVoiceApp
                var task = if (taskId > 0) app.repository.getTaskById(taskId) else null
                if (taskId > 0 && (task == null || task.isCompleted)) {
                    Log.d(TAG, "Ignoring missing or completed task $taskId")
                    return@launch
                }

                ContextCompat.startForegroundService(
                    context,
                    AlarmRingingService.startIntent(
                        context = context,
                        taskId = taskId,
                        taskTitle = taskTitle,
                        category = category,
                        location = location,
                        isNag = isNag
                    )
                )

                if (task?.isRecurring == true && !isNagRepeat) {
                    val scheduler = ReminderAlarmScheduler(context)
                    scheduler.nextRecurringOccurrence(task)?.let { nextTask ->
                        app.repository.updateTask(nextTask)
                        task = nextTask
                    }
                }

                if (isNag && taskId > 0 && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    ReminderAlarmScheduler(context)
                        .scheduleNagRepeat(taskId, taskTitle, category, location)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Unable to start reminder alarm", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderAlarmReceiver"
    }
}
