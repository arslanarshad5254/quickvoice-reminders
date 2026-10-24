package com.xovrix.quickvoicereminder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.xovrix.quickvoicereminder.alarm.ReminderAlarmReceiver
import com.xovrix.quickvoicereminder.billing.BillingManager
import com.xovrix.quickvoicereminder.data.AppDatabase
import com.xovrix.quickvoicereminder.data.TaskRepository
import com.xovrix.quickvoicereminder.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuickVoiceApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var database: AppDatabase
        private set

    lateinit var repository: TaskRepository
        private set

    lateinit var billingManager: BillingManager
        private set

    lateinit var settingsManager: SettingsManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        repository = TaskRepository(this, database.taskDao())
        billingManager = BillingManager(this)
        settingsManager = SettingsManager(this)
        initNotificationChannels()
        
    }

    private fun initNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val toneUriStr = settingsManager.notificationToneUri
        val soundUri = if (toneUriStr != null) Uri.parse(toneUriStr) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        val defaultChannelId = "${ReminderAlarmReceiver.CHANNEL_ID_DEFAULT}_${toneUriStr.hashCode()}"
        val defaultChannel = NotificationChannel(
            defaultChannelId,
            getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.reminder_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            setSound(soundUri, audioAttributes)
        }

        val nagChannelId = "${ReminderAlarmReceiver.CHANNEL_ID_NAG}_${toneUriStr.hashCode()}"
        val nagChannel = NotificationChannel(
            nagChannelId,
            "Urgent Nag Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High-urgency persistent reminders repeating every 5 minutes until checked off"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            setSound(soundUri, audioAttributes)
        }

        notificationManager.createNotificationChannel(defaultChannel)
        notificationManager.createNotificationChannel(nagChannel)
    }
}
