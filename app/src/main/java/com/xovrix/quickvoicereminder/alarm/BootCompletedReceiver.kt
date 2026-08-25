package com.xovrix.quickvoicereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.xovrix.quickvoicereminder.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Boot or package update detected. Rescheduling active alarms...")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val scheduler = ReminderAlarmScheduler(context)
                    val activeTasks = db.taskDao().getAllScheduledPendingTasks()
                    for (task in activeTasks) {
                        scheduler.schedule(task)
                    }
                    Log.d(TAG, "Successfully rescheduled ${activeTasks.size} active tasks.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule alarms on boot: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val TAG = "BootCompletedReceiver"
    }
}
