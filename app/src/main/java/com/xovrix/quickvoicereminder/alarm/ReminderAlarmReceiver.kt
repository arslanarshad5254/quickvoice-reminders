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
import androidx.core.app.NotificationManagerCompat
import com.xovrix.quickvoicereminder.MainActivity
import com.xovrix.quickvoicereminder.QuickVoiceApp
import com.xovrix.quickvoicereminder.R
import com.xovrix.quickvoicereminder.data.AppDatabase
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderAlarmScheduler.EXTRA_TASK_ID, -1L)
        val taskTitle = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_TASK_TITLE) ?: "Reminder Alert"
        val isNag = intent.getBooleanExtra(ReminderAlarmScheduler.EXTRA_IS_NAG, false)
        val isNagRepeat = intent.getBooleanExtra(ReminderAlarmScheduler.EXTRA_IS_NAG_REPEAT, false)
        val category = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_CATEGORY) ?: "General"
        val location = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_LOCATION)

        Log.d(TAG, "onReceive triggered for taskId: $taskId, isNag: $isNag")

        // First check if task is already completed in Room database
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as QuickVoiceApp
                var task = if (taskId > 0) app.repository.getTaskById(taskId) else null
                if (taskId > 0) {
                    if (task == null || task.isCompleted) {
                        Log.d(TAG, "Task $taskId is missing or completed, discarding alert.")
                        return@launch
                    }
                }

                val settingsManager = (context.applicationContext as QuickVoiceApp).settingsManager
                val toneUriStr = settingsManager.notificationToneUri
                val channelId = if (isNag) {
                    "${CHANNEL_ID_NAG}_${toneUriStr.hashCode()}"
                } else {
                    "${CHANNEL_ID_DEFAULT}_${toneUriStr.hashCode()}"
                }

                showNotification(context, taskId, taskTitle, isNag, category, location, channelId)

                if (task?.isRecurring == true && !isNagRepeat) {
                    val scheduler = ReminderAlarmScheduler(context)
                    scheduler.nextRecurringOccurrence(task)?.let { nextTask ->
                        app.repository.updateTask(nextTask)
                        task = nextTask
                    }
                }

                // If Nag Mode is active, automatically schedule repeating alarm in 5 minutes
                if (isNag && taskId > 0 && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
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
        location: String?,
        channelId: String
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannels(notificationManager, context)

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

        val settingsManager = (context.applicationContext as QuickVoiceApp).settingsManager
        val toneUriStr = settingsManager.notificationToneUri
        val soundUri = if (toneUriStr != null) Uri.parse(toneUriStr) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val subtext = buildString {
            append("[$category]")
            if (isNag) append(context.getString(R.string.notification_nag_suffix))
            if (!location.isNullOrBlank()) append(context.getString(R.string.notification_location, location))
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(taskTitle)
            .setSubText(subtext)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$taskTitle\n$subtext"))
            .setPriority(if (isNag) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppPendingIntent)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, context.getString(R.string.notification_mark_done), completePendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, context.getString(R.string.notification_snooze), snoozePendingIntent)

        val notifId = if (taskId > 0) taskId.toInt() else (System.currentTimeMillis() % 10000).toInt()
        notificationManager.notify(notifId, builder.build())
    }

    private fun createNotificationChannels(notificationManager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val settingsManager = (context.applicationContext as QuickVoiceApp).settingsManager
        val toneUriStr = settingsManager.notificationToneUri
        val soundUri = if (toneUriStr != null) Uri.parse(toneUriStr) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        val defaultChannelId = "${CHANNEL_ID_DEFAULT}_${toneUriStr.hashCode()}"
        val defaultChannel = NotificationChannel(
            defaultChannelId,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.standard_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            setSound(soundUri, audioAttributes)
        }

        val nagChannelId = "${CHANNEL_ID_NAG}_${toneUriStr.hashCode()}"
        val nagChannel = NotificationChannel(
            nagChannelId,
            context.getString(R.string.urgent_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.urgent_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            setSound(soundUri, audioAttributes)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }

        notificationManager.createNotificationChannel(defaultChannel)
        notificationManager.createNotificationChannel(nagChannel)
        notificationManager.notificationChannels
            .filter {
                (it.id.startsWith(CHANNEL_ID_DEFAULT) || it.id.startsWith(CHANNEL_ID_NAG)) &&
                        it.id != defaultChannelId && it.id != nagChannelId
            }
            .forEach { notificationManager.deleteNotificationChannel(it.id) }
    }

    companion object {
        const val TAG = "ReminderAlarmReceiver"
        const val CHANNEL_ID_DEFAULT = "reminder_notifications"
        const val CHANNEL_ID_NAG = "reminder_notifications_nag"
    }
}
