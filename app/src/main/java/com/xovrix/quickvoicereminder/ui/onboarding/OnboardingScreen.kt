package com.xovrix.quickvoicereminder.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xovrix.quickvoicereminder.ui.theme.CyanAccent
import com.xovrix.quickvoicereminder.ui.theme.ElectricIndigo

private data class OnboardingPage(
    val icon: ImageVector,
    val eyebrow: String,
    val title: String,
    val description: String,
    val steps: List<OnboardingStep>,
    val accent: Color
)

private data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val description: String
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage(
                icon = Icons.Default.KeyboardVoice,
                eyebrow = "CAPTURE",
                title = "Say it or type it",
                description = "Create a complete reminder in seconds using everyday language.",
                steps = listOf(
                    OnboardingStep(
                        icon = Icons.Default.Mic,
                        title = "Speak",
                        description = "Tap the microphone and say your reminder naturally."
                    ),
                    OnboardingStep(
                        icon = Icons.Default.Keyboard,
                        title = "Type",
                        description = "Use the quick capture field when you prefer typing."
                    ),
                    OnboardingStep(
                        icon = Icons.Default.CalendarMonth,
                        title = "Add details",
                        description = "Mention a date, time, category, or recurrence."
                    )
                ),
                accent = ElectricIndigo
            ),
            OnboardingPage(
                icon = Icons.Default.AutoAwesome,
                eyebrow = "REVIEW",
                title = "AI organizes the details",
                description = "The app extracts the task, schedule, category, location, recurrence, and urgency.",
                steps = listOf(
                    OnboardingStep(
                        icon = Icons.Default.AutoAwesome,
                        title = "AI extracts",
                        description = "Your task and scheduling details are organized for you."
                    ),
                    OnboardingStep(
                        icon = Icons.Default.Edit,
                        title = "Review and edit",
                        description = "Correct any date, time, category, or recurrence."
                    ),
                    OnboardingStep(
                        icon = Icons.Default.CheckCircle,
                        title = "Confirm",
                        description = "Save the reminder and schedule its alarm."
                    )
                ),
                accent = CyanAccent
            ),
            OnboardingPage(
                icon = Icons.Default.Alarm,
                eyebrow = "ALERT",
                title = "Get an alarm you won’t miss",
                description = "At the scheduled time, a full-screen alarm rings with your selected custom tone.",
                steps = listOf(
                    OnboardingStep(
                        icon = Icons.Default.NotificationsActive,
                        title = "Allow alerts",
                        description = "Enable notifications, exact alarms, and full-screen access."
                    ),
                    OnboardingStep(
                        icon = Icons.Default.MusicNote,
                        title = "Choose a tone",
                        description = "Select the custom sound used by reminder alarms."
                    ),
                    OnboardingStep(
                        icon = Icons.Default.Snooze,
                        title = "Take action",
                        description = "Mark done or snooze for 10 minutes from the alarm screen."
                    )
                ),
                accent = Color(0xFFF59E0B)
            )
        )
    }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[pageIndex]
    BackHandler(enabled = pageIndex > 0) { pageIndex-- }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        page.accent.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QuickVoice",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${pageIndex + 1} of ${pages.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(42.dp))
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(page.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = page.accent,
                    modifier = Modifier.size(56.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = page.eyebrow,
                color = page.accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = page.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    page.steps.forEach { step ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(page.accent.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = step.icon,
                                    contentDescription = step.title,
                                    tint = page.accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = step.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                pages.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pageIndex) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pageIndex) page.accent
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                    )
                }
            }
            Button(
                onClick = {
                    if (pageIndex < pages.lastIndex) pageIndex++ else onComplete()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = page.accent)
            ) {
                Text(
                    text = if (pageIndex == pages.lastIndex) "Got it" else "Next",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
