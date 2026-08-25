package com.xovrix.quickvoicereminder.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xovrix.quickvoicereminder.speech.SpeechState
import com.xovrix.quickvoicereminder.ui.theme.*

@Composable
fun QuickCaptureBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    speechState: SpeechState,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    isParsing: Boolean,
    onQuickChipClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = speechState is SpeechState.Listening
    val rmsDb = (speechState as? SpeechState.Listening)?.rmsDb ?: 0f

    // Animated pulsing scale for microphone while recording
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Quick suggestion chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickChip(label = "📅 Today 5 PM", onClick = { onQuickChipClicked("today at 5pm") })
                QuickChip(label = "☀️ Tomorrow 9 AM", onClick = { onQuickChipClicked("tomorrow at 9am") })
                QuickChip(label = "🔥 Nag Mode", onClick = { onQuickChipClicked("urgent nag me") })
                QuickChip(label = "💊 Health", onClick = { onQuickChipClicked("doctor appointment") })
                QuickChip(label = "🛒 Errands", onClick = { onQuickChipClicked("buy groceries") })
                QuickChip(label = "💼 Work", onClick = { onQuickChipClicked("team meeting") })
            }

            // Real-time Voice Waveform or Parsing feedback banner
            AnimatedVisibility(
                visible = isListening || isParsing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (isListening) {
                    VoiceListeningIndicator(rmsDb = rmsDb, onStop = onStopVoice)
                } else if (isParsing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Gemini AI extracting task, time & urgency...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Input Row: TextField + Action Button (Mic / Send)
            val containerColor by animateColorAsState(
                targetValue = when {
                    isListening -> CoralRed.copy(alpha = 0.12f)
                    isParsing -> ElectricIndigo.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
                animationSpec = tween(300),
                label = "inputContainerColor"
            )

            val borderColor by animateColorAsState(
                targetValue = when {
                    isListening -> CoralRed.copy(alpha = 0.6f)
                    isParsing -> ElectricIndigo.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                },
                animationSpec = tween(300),
                label = "inputBorderColor"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextField(
                    value = inputText,
                    onValueChange = onInputChanged,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_input_field")
                        .border(1.5.dp, borderColor, RoundedCornerShape(24.dp)),
                    placeholder = {
                        Text(
                            text = when {
                                isListening -> "Listening... Speak your reminder"
                                isParsing -> "AI processing your reminder..."
                                else -> "e.g. Call dentist tomorrow at 3pm urgent"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isListening || isParsing) FontWeight.Medium else FontWeight.Normal,
                            color = when {
                                isListening -> CoralRed
                                isParsing -> ElectricIndigo
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            },
                            maxLines = 1
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = containerColor,
                        unfocusedContainerColor = containerColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    leadingIcon = {
                        when {
                            isListening -> {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = "Listening state",
                                    tint = CoralRed,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .scale(pulseScale)
                                )
                            }
                            isParsing -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = ElectricIndigo
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "AI Powered",
                                    tint = ElectricIndigo,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        if (inputText.isNotBlank()) {
                            IconButton(onClick = { onInputChanged("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear text",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSendClicked() })
                )

                // Dynamic Action Button: Mic when input is blank, Send when text exists
                val hasText = inputText.isNotBlank()
                IconButton(
                    onClick = {
                        if (isListening) {
                            onStopVoice()
                        } else {
                            if (hasText) onSendClicked() else onStartVoice()
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .testTag(if (hasText) "send_button" else "voice_record_button")
                        .scale(if (isListening) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            if (isListening) {
                                Brush.linearGradient(listOf(CoralRed, AmberFlame))
                            } else if (hasText) {
                                Brush.linearGradient(listOf(ElectricIndigo, CyanAccent))
                            } else {
                                Brush.linearGradient(listOf(ElectricIndigo, ElectricIndigoDark))
                            }
                        )
                ) {
                    if (isListening) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop recording",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    } else if (hasText) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "Capture Reminder",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Start Voice Input",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun VoiceListeningIndicator(
    rmsDb: Float,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CoralRed.copy(alpha = 0.12f))
            .border(1.dp, CoralRed.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(CoralRed)
            )
            Text(
                text = "Listening... Speak clearly",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = CoralRed
            )
        }

        // Mini animated audio bars
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val baseHeight = 12.dp
            val heights = listOf(
                baseHeight + (16.dp * (rmsDb * 0.8f)),
                baseHeight + (24.dp * rmsDb),
                baseHeight + (18.dp * (rmsDb * 0.9f)),
                baseHeight + (22.dp * (rmsDb * 0.7f)),
                baseHeight + (14.dp * (rmsDb * 0.5f))
            )
            heights.forEach { h ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(h.coerceIn(4.dp, 28.dp))
                        .clip(RoundedCornerShape(2.dp))
                        .background(CoralRed)
                )
            }
        }

        TextButton(
            onClick = onStop,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("Done", color = CoralRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
