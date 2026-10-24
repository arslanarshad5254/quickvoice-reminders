@file:Suppress("RestrictedApi")

package com.xovrix.quickvoicereminder.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.xovrix.quickvoicereminder.MainActivity
import com.xovrix.quickvoicereminder.R
import com.xovrix.quickvoicereminder.data.AppDatabase
import com.xovrix.quickvoicereminder.data.TaskEntity
import com.xovrix.quickvoicereminder.QuickVoiceApp
import java.time.LocalDate
import java.time.LocalDateTime

class QuickVoiceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)
        val activeTasks = try {
            database.taskDao().getActiveTasksList()
        } catch (e: Exception) {
            emptyList()
        }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    context = context,
                    tasks = activeTasks
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        tasks: List<TaskEntity>
    ) {
        val voiceLaunchIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_START_VOICE
            putExtra(MainActivity.EXTRA_START_VOICE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(20.dp)
                .background(ColorProvider(R.color.widget_background))
                .padding(12.dp)
        ) {
            // Widget Header Row: Logo/Title + Task Count + Quick Voice Shortcut Button
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier
                        .clickable(actionStartActivity(openAppIntent)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_app_logo),
                        contentDescription = "QuickVoice",
                        modifier = GlanceModifier
                            .size(24.dp)
                            .cornerRadius(6.dp)
                    )

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    Text(
                        text = "QuickVoice",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ColorProvider(R.color.widget_text_primary)
                        )
                    )

                    if (tasks.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(10.dp)
                                .background(ColorProvider(R.color.widget_badge_bg))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${tasks.size}",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(R.color.widget_badge_text)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Quick Voice Capture Button
                Row(
                    modifier = GlanceModifier
                        .cornerRadius(16.dp)
                        .background(ColorProvider(R.color.widget_mic_btn_bg))
                        .clickable(actionStartActivity(voiceLaunchIntent))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_mic),
                        contentDescription = "Start Voice Input",
                        modifier = GlanceModifier.size(16.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "Voice",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ColorProvider(R.color.widget_mic_btn_text)
                        )
                    )
                }
            }

            // Task List or Empty State
            if (tasks.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity(voiceLaunchIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_sparkle),
                            contentDescription = "No tasks",
                            modifier = GlanceModifier.size(24.dp)
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = "No active reminders",
                            style = TextStyle(
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = ColorProvider(R.color.widget_text_secondary)
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = "Tap Voice to speak a reminder",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = ColorProvider(R.color.widget_mic_btn_bg)
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    items(tasks) { task ->
                        TaskWidgetItem(
                            task = task,
                            openAppIntent = openAppIntent
                        )
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun TaskWidgetItem(
        task: TaskEntity,
        openAppIntent: Intent
    ) {
        val todayStr = LocalDate.now().toString()
        val isDueToday = task.dueDate == todayStr
        val isOverdue = task.dueDate != null && task.dueDate < todayStr

        val itemBgColor = if (task.isNag) {
            ColorProvider(R.color.widget_nag_bg)
        } else {
            ColorProvider(R.color.widget_item_bg)
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(12.dp)
                .background(itemBgColor)
                .clickable(actionStartActivity(openAppIntent))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkmark button to complete task directly from widget
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .cornerRadius(14.dp)
                    .background(ColorProvider(R.color.widget_check_bg))
                    .clickable(
                        actionRunCallback<CompleteTaskActionCallback>(
                            actionParametersOf(CompleteTaskActionCallback.taskIdKey to task.id)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_check),
                    contentDescription = "Complete Task",
                    modifier = GlanceModifier.size(16.dp)
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            Column(
                modifier = GlanceModifier.defaultWeight()
            ) {
                Text(
                    text = task.task,
                    maxLines = 1,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ColorProvider(R.color.widget_text_primary)
                    )
                )

                val details = buildString {
                    if (task.dueDate != null) {
                        append(if (isDueToday) "Today" else task.dueDate)
                    }
                    if (task.dueTime != null) {
                        if (isNotEmpty()) append(" • ")
                        append(task.dueTime)
                    }
                    if (task.category.isNotBlank() && task.category != "General") {
                        if (isNotEmpty()) append(" • ")
                        append(task.category)
                    }
                }

                if (details.isNotBlank()) {
                    val dateColor = when {
                        task.isNag || isOverdue -> ColorProvider(R.color.widget_urgent_text)
                        isDueToday -> ColorProvider(R.color.widget_mic_btn_bg)
                        else -> ColorProvider(R.color.widget_text_secondary)
                    }

                    Text(
                        text = details,
                        maxLines = 1,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = dateColor
                        )
                    )
                }
            }
        }
    }
}

class CompleteTaskActionCallback : ActionCallback {
    companion object {
        val taskIdKey = ActionParameters.Key<Long>("taskId")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[taskIdKey] ?: return
        val repository = (context.applicationContext as QuickVoiceApp).repository
        repository.setTaskCompletion(taskId, true)
        QuickVoiceWidget().updateAll(context)
    }
}

class QuickVoiceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickVoiceWidget()
}
