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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.xovrix.quickvoicereminder.ui.QuickVoiceScreen
import com.xovrix.quickvoicereminder.ui.onboarding.OnboardingScreen
import com.xovrix.quickvoicereminder.ui.theme.MyApplicationTheme
import com.xovrix.quickvoicereminder.ui.viewmodel.ReminderViewModel
import com.xovrix.quickvoicereminder.update.UpdateManager
import com.xovrix.quickvoicereminder.ads.AdManager
import com.xovrix.quickvoicereminder.ads.AdsConsentManager
import com.google.android.gms.ads.MobileAds
import com.xovrix.quickvoicereminder.data.OnboardingManager

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_START_VOICE = "com.xovrix.quickvoicereminder.ACTION_START_VOICE"
        const val EXTRA_START_VOICE = "EXTRA_START_VOICE"
    }

    private val viewModel: ReminderViewModel by viewModels()
    private var triggerVoiceOnStart by mutableStateOf(false)
    private lateinit var updateManager: UpdateManager
    private lateinit var adsConsentManager: AdsConsentManager
    private lateinit var onboardingManager: OnboardingManager
    private var postOnboardingServicesStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateManager = UpdateManager(this)
        adsConsentManager = AdsConsentManager(this)
        onboardingManager = OnboardingManager(this)
        if (onboardingManager.isCompleted) startPostOnboardingServices()
        checkVoiceIntent(intent)
        setContent {
            var showOnboarding by rememberSaveable { mutableStateOf(!onboardingManager.isCompleted) }
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showOnboarding) {
                        OnboardingScreen(
                            onComplete = {
                                onboardingManager.markCompleted()
                                showOnboarding = false
                                startPostOnboardingServices()
                            }
                        )
                    } else {
                        QuickVoiceScreen(
                            viewModel = viewModel,
                            autoStartVoice = triggerVoiceOnStart,
                            onVoiceAutoStarted = { triggerVoiceOnStart = false },
                            onPrivacyOptions = { adsConsentManager.showPrivacyOptions(this) }
                        )
                    }
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
        if (postOnboardingServicesStarted) updateManager.onResume()
        viewModel.rescheduleActiveTasks()
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
            intent.getLongExtra("OPEN_TASK_ID", -1L).takeIf { it > 0 }?.let {
                viewModel.openTaskById(it)
                intent.removeExtra("OPEN_TASK_ID")
            }
        }
    }

    private fun startPostOnboardingServices() {
        if (postOnboardingServicesStarted) return
        postOnboardingServicesStarted = true
        adsConsentManager.gatherConsent(this) { canRequestAds ->
            AdManager.setConsentStatus(canRequestAds)
            if (canRequestAds) MobileAds.initialize(this) {}
        }
        updateManager.checkForUpdates()
    }
}


