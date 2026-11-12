package com.xovrix.quickvoicereminder.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xovrix.quickvoicereminder.data.TaskEntity
import com.xovrix.quickvoicereminder.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    task: TaskEntity,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {}
) {
    val categoryColor = CategoryUtils.getCategoryColor(task.category)
    val categoryIcon = CategoryUtils.getCategoryIcon(task.category)
    val formattedDue = CategoryUtils.formatDueString(task.dueDate, task.dueTime)
    var countdown by remember(task.dueDate, task.dueTime) {
        mutableStateOf(CategoryUtils.getRelativeTimeCountdown(task.dueDate, task.dueTime))
    }
    LaunchedEffect(task.dueDate, task.dueTime) {
        while (true) {
            countdown = CategoryUtils.getRelativeTimeCountdown(task.dueDate, task.dueTime)
            delay(COUNTDOWN_REFRESH_MS)
        }
    }
    val (countdownText, isOverdue) = countdown

    val cardBgColor by animateColorAsState(
        targetValue = when {
            isSelected -> ElectricIndigo.copy(alpha = 0.15f)
            task.isCompleted -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(300),
        label = "cardBgColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("task_item_card_${task.id}")
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else onEdit() },
                onLongClick = { if (!isSelectionMode) onToggleSelection() }
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted || isSelected) 0.dp else 2.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                isSelected -> ElectricIndigo
                task.isNag && !task.isCompleted -> AmberFlame.copy(alpha = 0.65f)
                !task.isCompleted -> categoryColor.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left Category Color Indicator Stripe
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(
                        when {
                            isSelected -> ElectricIndigo
                            task.isCompleted -> categoryColor.copy(alpha = 0.4f)
                            else -> categoryColor
                        }
                    )
                    .testTag("category_indicator_stripe_${task.id}")
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Row: Category tag, Nag badge, Countdown, and Action Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Category Chip Indicator
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = categoryColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = categoryColor.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("category_chip_${task.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(categoryColor)
                            )
                            Icon(
                                imageVector = categoryIcon,
                                contentDescription = task.category,
                                tint = categoryColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = task.category,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = categoryColor
                            )
                        }
                    }

                    // Badges Row (Nag flame & Countdown)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (task.isNag && !task.isCompleted) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AmberFlame.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    AmberFlame.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocalFireDepartment,
                                        contentDescription = "Nag Mode Active",
                                        tint = AmberFlame,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Nag",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AmberFlame
                                    )
                                }
                            }
                        }

                        if (countdownText.isNotBlank() && !task.isCompleted) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isOverdue) CoralRed.copy(alpha = 0.15f) else ElectricIndigo.copy(
                                    alpha = 0.12f
                                )
                            ) {
                                Text(
                                    text = countdownText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOverdue) CoralRed else ElectricIndigo,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (!isSelectionMode) {
                            // Edit button
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("task_edit_button_${task.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit reminder details",
                                    tint = ElectricIndigo.copy(alpha = 0.85f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Delete button
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("task_delete_button_${task.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Delete task",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            // Selection Checkbox (Right Side)
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelection() },
                                colors = CheckboxDefaults.colors(checkedColor = ElectricIndigo)
                            )
                        }
                    }
                }

                // Middle: Checkbox + Task Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Interactive Custom Checkbox (>=48dp touch target)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("task_checkbox_${task.id}")
                            .combinedClickable(
                                onClick = { if (isSelectionMode) onToggleSelection() else onToggleComplete() },
                                onLongClick = { if (!isSelectionMode) onToggleSelection() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (task.isCompleted) EmeraldGreen else Color.Transparent
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (task.isCompleted) EmeraldGreen else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.isCompleted) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Mark as not done",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Task Name
                    Text(
                        text = task.task,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (task.isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (task.isCompleted && !isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onToggleComplete,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .testTag("task_mark_not_done_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Mark not done",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Bottom Info Bar: Date/Time + Location
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Date & Time
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = "Due time",
                            tint = if (isOverdue && !task.isCompleted) CoralRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.8f
                            ),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = formattedDue,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOverdue && !task.isCompleted) CoralRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.8f
                            )
                        )
                    }

                    // Location if present
                    if (!task.location.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = "Location",
                                tint = CyanAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = task.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val COUNTDOWN_REFRESH_MS = 30_000L
