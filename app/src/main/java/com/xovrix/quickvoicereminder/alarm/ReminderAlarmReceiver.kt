package com.xovrix.quickvoicereminder.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xovrix.quickvoicereminder.MainActivity
import com.xovrix.quickvoicereminder.R
import com.xovrix.quickvoicereminder.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderAlarmScheduler.EXTRA_TASK_ID, -1L)
        val taskTitle = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_TASK_TITLE) ?: "Reminder Alert"
        val isNag = intent.getBooleanExtra(ReminderAlarmScheduler.EXTRA_IS_NAG, false)
        val category = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_CATEGORY) ?: "General"
        val location = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_LOCATION)

        Log.d(TAG, "onReceive triggered for taskId: $taskId, isNag: $isNag")

        // First check if task is already completed in Room database
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (taskId > 0) {
                    val db = AppDatabase.getDatabase(context)
                    val task = db.taskDao().getTaskById(taskId)
                    if (task != null && task.isCompleted) {
                        Log.d(TAG, "Task $taskId is already completed, discarding alert.")
                        pendingResult.finish()
                        return@launch
                    }
                }

                showNotification(context, taskId, taskTitle, isNag, category, location)

                // If Nag Mode is active, automatically schedule repeating alarm in 5 minutes
                if (isNag && taskId > 0) {
                    val scheduler = ReminderAlarmScheduler(context)
                    scheduler.scheduleNagRepeat(taskId, taskTitle, category, location)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in ReminderAlarmReceiver: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        taskId: Long,
        taskTitle: String,
        isNag: Boolean,
        category: String,
        location: String?
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = if (isNag) CHANNEL_ID_NAG else CHANNEL_ID_DEFAULT

        createNotificationChannels(notificationManager)

        // Content intent to open MainActivity
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TASK_ID", taskId)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Complete Task
        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE_TASK
            putExtra(ReminderAlarmScheduler.EXTRA_TASK_ID, taskId)
            putExtra("NOTIFICATION_ID", taskId.toInt())
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            (taskId * 10 + 1).toInt(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze 10m
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_TASK
            putExtra(ReminderAlarmScheduler.EXTRA_TASK_ID, taskId)
            putExtra(ReminderAlarmScheduler.EXTRA_TASK_TITLE, taskTitle)
            putExtra("NOTIFICATION_ID", taskId.toInt())
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (taskId * 10 + 2).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val subtext = buildString {
            append("[$category]")
            if (isNag) append(" • Persistent Nag Mode ⚡")
            if (!location.isNullOrBlank()) append(" • 📍 $location")
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("QuickVoice Reminder")
            .setContentText(taskTitle)
            .setSubText(subtext)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$taskTitle\n$subtext"))
            .setPriority(if (isNag) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppPendingIntent)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "✓ Mark Done", completePendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, "⏱ Snooze 10m", snoozePendingIntent)

        val notifId = if (taskId > 0) taskId.toInt() else (System.currentTimeMillis() % 10000).toInt()
        notificationManager.notify(notifId, builder.build())
    }

    private fun createNotificationChannels(notificationManager: NotificationManager) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        val defaultChannel = NotificationChannel(
            CHANNEL_ID_DEFAULT,
            "QuickVoice Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Standard task reminder alerts"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            setSound(soundUri, audioAttributes)
        }

        val nagChannel = NotificationChannel(
            CHANNEL_ID_NAG,
            "Urgent Nag Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High-urgency persistent reminders repeating every 5 minutes until checked off"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            setSound(soundUri, audioAttributes)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        notificationManager.createNotificationChannel(defaultChannel)
        notificationManager.createNotificationChannel(nagChannel)
    }

    companion object {
        const val TAG = "ReminderAlarmReceiver"
        const val CHANNEL_ID_DEFAULT = "reminder_notifications"
        const val CHANNEL_ID_NAG = "reminder_notifications_nag"
    }
}
