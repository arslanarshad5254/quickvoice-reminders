package com.xovrix.quickvoicereminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.xovrix.quickvoicereminder.ui.QuickVoiceScreen
import com.xovrix.quickvoicereminder.ui.theme.MyApplicationTheme
import com.xovrix.quickvoicereminder.ui.viewmodel.ReminderViewModel
import com.xovrix.quickvoicereminder.update.UpdateManager

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_START_VOICE = "com.xovrix.quickvoicereminder.ACTION_START_VOICE"
        const val EXTRA_START_VOICE = "EXTRA_START_VOICE"
    }

    private val viewModel: ReminderViewModel by viewModels()
    private var triggerVoiceOnStart by mutableStateOf(false)
    private lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateManager = UpdateManager(this)
        updateManager.checkForUpdates()
        checkVoiceIntent(intent)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    QuickVoiceScreen(
                        viewModel = viewModel,
                        autoStartVoice = triggerVoiceOnStart,
                        onVoiceAutoStarted = { triggerVoiceOnStart = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkVoiceIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateManager.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateManager.onDestroy()
    }

    private fun checkVoiceIntent(intent: Intent?) {
        if (intent != null) {
            val isVoiceAction = intent.action == ACTION_START_VOICE ||
                    intent.getBooleanExtra(EXTRA_START_VOICE, false)
            if (isVoiceAction) {
                triggerVoiceOnStart = true
            }
        }
    }
}


