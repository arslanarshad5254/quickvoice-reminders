package com.xovrix.quickvoicereminder.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.xovrix.quickvoicereminder.alarm.ReminderAlarmScheduler
import com.xovrix.quickvoicereminder.widget.QuickVoiceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class TaskRepository(
    private val context: Context,
    private val taskDao: TaskDao
) {
    private val scheduler = ReminderAlarmScheduler(context)

    val activeTasks: Flow<List<TaskEntity>> = taskDao.getActiveTasks()
    val completedTasks: Flow<List<TaskEntity>> = taskDao.getCompletedTasks()
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()

    private suspend fun notifyWidget() {
        try {
            QuickVoiceWidget().updateAll(context)
        } catch (_: Exception) {}
    }

    suspend fun insertTask(task: TaskEntity): Long = withContext(Dispatchers.IO) {
        val id = taskDao.insertTask(task)
        val savedTask = task.copy(id = id)
        if (!savedTask.isCompleted) {
            scheduler.schedule(savedTask)
        }
        notifyWidget()
        id
    }

    suspend fun updateTask(task: TaskEntity) = withContext(Dispatchers.IO) {
        taskDao.updateTask(task)
        scheduler.cancel(task.id)
        if (!task.isCompleted) {
            scheduler.schedule(task)
        }
        notifyWidget()
    }

    suspend fun setTaskCompletion(id: Long, completed: Boolean) = withContext(Dispatchers.IO) {
        val completedAt = if (completed) LocalDateTime.now().toString() else null
        taskDao.setTaskCompletion(id, completed, completedAt)
        if (completed) {
            scheduler.cancel(id)
        } else {
            val task = taskDao.getTaskById(id)
            if (task != null) {
                scheduler.schedule(task)
            }
        }
        notifyWidget()
    }

    suspend fun deleteTask(task: TaskEntity) = withContext(Dispatchers.IO) {
        scheduler.cancel(task.id)
        taskDao.deleteTask(task)
        notifyWidget()
    }

    suspend fun clearCompletedTasks() = withContext(Dispatchers.IO) {
        taskDao.clearCompletedTasks()
        notifyWidget()
    }

    suspend fun getTaskById(id: Long): TaskEntity? = withContext(Dispatchers.IO) {
        taskDao.getTaskById(id)
    }
}

