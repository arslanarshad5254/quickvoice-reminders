package com.xovrix.quickvoicereminder.alarm

import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xovrix.quickvoicereminder.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmRingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        volumeControlStream = AudioManager.STREAM_ALARM

        val taskId = intent.getLongExtra(AlarmRingingService.EXTRA_TASK_ID, -1L)
        val taskTitle = intent.getStringExtra(AlarmRingingService.EXTRA_TASK_TITLE) ?: "Reminder"
        val category = intent.getStringExtra(AlarmRingingService.EXTRA_CATEGORY) ?: "General"
        val location = intent.getStringExtra(AlarmRingingService.EXTRA_LOCATION)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                AlarmScreen(
                    taskTitle = taskTitle,
                    category = category,
                    location = location,
                    onComplete = {
                        sendAction(AlarmRingingService.completeIntent(this, taskId, taskTitle))
                    },
                    onSnooze = {
                        sendAction(AlarmRingingService.snoozeIntent(this, taskId, taskTitle))
                    }
                )
            }
        }
    }

    private fun sendAction(intent: Intent) {
        startService(intent)
        finishAndRemoveTask()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun AlarmScreen(
    taskTitle: String,
    category: String,
    location: String?,
    onComplete: () -> Unit,
    onSnooze: () -> Unit
) {
    BackHandler(enabled = true) { }
    val formatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    var currentTime by remember { mutableStateOf(LocalTime.now().format(formatter)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            currentTime = LocalTime.now().format(formatter)
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF111827), Color(0xFF312E81), Color(0xFF0F172A))
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp, vertical = 48.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ALARM", color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        currentTime,
                        color = Color.White,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Light
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        taskTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        buildString {
                            append(category)
                            if (!location.isNullOrBlank()) append(" • ").append(location)
                        },
                        color = Color(0xFFCBD5E1),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text("Mark done", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Snooze ${AlarmRingingService.SNOOZE_MINUTES} minutes", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
