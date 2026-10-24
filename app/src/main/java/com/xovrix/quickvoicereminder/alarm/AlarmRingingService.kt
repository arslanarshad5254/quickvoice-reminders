package com.xovrix.quickvoicereminder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xovrix.quickvoicereminder.QuickVoiceApp
import com.xovrix.quickvoicereminder.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AlarmRingingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentTaskId = -1L
    private var autoStopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_COMPLETE -> complete(intent.getLongExtra(EXTRA_TASK_ID, currentTaskId))
            ACTION_SNOOZE -> snooze(
                intent.getLongExtra(EXTRA_TASK_ID, currentTaskId),
                intent.getStringExtra(EXTRA_TASK_TITLE) ?: getString(R.string.alarm_default_title)
            )
            ACTION_STOP -> stopAlarm()
            else -> startAlarm(intent ?: return START_NOT_STICKY)
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(intent: Intent) {
        currentTaskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        createChannel()
        startForeground(notificationId(currentTaskId), buildNotification(intent))
        startSound()
        startVibration()
        autoStopJob?.cancel()
        autoStopJob = serviceScope.launch {
            delay(MAX_RING_DURATION_MS)
            stopAlarm()
        }
    }

    private fun buildNotification(source: Intent): Notification {
        val taskId = source.getLongExtra(EXTRA_TASK_ID, -1L)
        val taskTitle = source.getStringExtra(EXTRA_TASK_TITLE) ?: getString(R.string.alarm_default_title)
        val category = source.getStringExtra(EXTRA_CATEGORY) ?: "General"
        val location = source.getStringExtra(EXTRA_LOCATION)

        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtras(source)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            notificationId(taskId),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val completePendingIntent = PendingIntent.getService(
            this,
            actionRequestCode(taskId, 1),
            actionIntent(ACTION_COMPLETE, taskId, taskTitle),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePendingIntent = PendingIntent.getService(
            this,
            actionRequestCode(taskId, 2),
            actionIntent(ACTION_SNOOZE, taskId, taskTitle),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val detail = buildString {
            append(category)
            if (!location.isNullOrBlank()) append(" • ").append(location)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(taskTitle)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$taskTitle\n$detail"))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, getString(R.string.alarm_mark_done), completePendingIntent)
            .addAction(0, getString(R.string.alarm_snooze), snoozePendingIntent)
            .build()
    }

    private fun actionIntent(action: String, taskId: Long, taskTitle: String) =
        Intent(this, AlarmRingingService::class.java).apply {
            this.action = action
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
        }

    private fun startSound() {
        stopSound()
        requestAudioFocus()
        val configured = (application as QuickVoiceApp).settingsManager.notificationToneUri
        val preferred = configured?.let(android.net.Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        mediaPlayer = preparePlayer(preferred) ?: preparePlayer(fallback)
        mediaPlayer?.start()
    }

    private fun preparePlayer(uri: android.net.Uri?): MediaPlayer? {
        if (uri == null) return null
        return runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingingService, uri)
                isLooping = true
                prepare()
            }
        }.onFailure { Log.w(TAG, "Unable to play selected alarm tone", it) }.getOrNull()
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 700, 500, 700, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun complete(taskId: Long) {
        silenceAlarm()
        if (taskId <= 0) {
            finishService()
            return
        }
        serviceScope.launch {
            try {
                (application as QuickVoiceApp).repository.setTaskCompletion(taskId, true)
            } finally {
                finishService()
            }
        }
    }

    private fun snooze(taskId: Long, taskTitle: String) {
        silenceAlarm()
        if (taskId <= 0) {
            finishService()
            return
        }
        serviceScope.launch {
            try {
                val app = application as QuickVoiceApp
                val task = app.repository.getTaskById(taskId)
                if (task != null) {
                    val next = LocalDateTime.now().plusMinutes(SNOOZE_MINUTES.toLong())
                    app.repository.updateTask(
                        task.copy(
                            dueDate = next.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            dueTime = next.format(DateTimeFormatter.ofPattern("HH:mm")),
                            scheduledDateTime = next.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            isCompleted = false,
                            completedAt = null
                        )
                    )
                } else {
                    ReminderAlarmScheduler(this@AlarmRingingService)
                        .scheduleSnooze(taskId, taskTitle, SNOOZE_MINUTES)
                }
            } finally {
                finishService()
            }
        }
    }

    private fun stopAlarm() {
        silenceAlarm()
        finishService()
    }

    private fun silenceAlarm() {
        stopSound()
        vibrator?.cancel()
        vibrator = null
    }

    private fun finishService() {
        autoStopJob?.cancel()
        autoStopJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopSound() {
        runCatching { mediaPlayer?.stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        }
        audioFocusRequest = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.alarm_channel_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopSound()
        vibrator?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmRingingService"
        private const val CHANNEL_ID = "active_reminder_alarms_v1"
        private const val ACTION_COMPLETE = "com.xovrix.quickvoicereminder.ALARM_COMPLETE"
        private const val ACTION_SNOOZE = "com.xovrix.quickvoicereminder.ALARM_SNOOZE"
        private const val ACTION_STOP = "com.xovrix.quickvoicereminder.ALARM_STOP"
        const val EXTRA_TASK_ID = "alarm_task_id"
        const val EXTRA_TASK_TITLE = "alarm_task_title"
        const val EXTRA_CATEGORY = "alarm_category"
        const val EXTRA_LOCATION = "alarm_location"
        const val EXTRA_IS_NAG = "alarm_is_nag"
        const val SNOOZE_MINUTES = 10
        private const val MAX_RING_DURATION_MS = 10 * 60 * 1000L

        fun startIntent(
            context: Context,
            taskId: Long,
            taskTitle: String,
            category: String,
            location: String?,
            isNag: Boolean
        ) = Intent(context, AlarmRingingService::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
            putExtra(EXTRA_CATEGORY, category)
            putExtra(EXTRA_LOCATION, location)
            putExtra(EXTRA_IS_NAG, isNag)
        }

        fun completeIntent(context: Context, taskId: Long, taskTitle: String) =
            Intent(context, AlarmRingingService::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
            }

        fun snoozeIntent(context: Context, taskId: Long, taskTitle: String) =
            Intent(context, AlarmRingingService::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
            }

        private fun notificationId(taskId: Long) = if (taskId > 0) taskId.toInt() else 7001
        private fun actionRequestCode(taskId: Long, action: Int) = (taskId * 10 + action).toInt()
    }
}
