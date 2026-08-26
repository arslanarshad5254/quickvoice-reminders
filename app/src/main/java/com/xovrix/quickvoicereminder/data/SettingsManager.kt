package com.xovrix.quickvoicereminder.data

import android.content.Context
import android.net.Uri
import android.media.RingtoneManager

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_NOTIFICATION_TONE = "notification_tone_uri"
    }

    var notificationToneUri: String?
        get() = prefs.getString(KEY_NOTIFICATION_TONE, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString())
        set(value) = prefs.edit().putString(KEY_NOTIFICATION_TONE, value).apply()
}
