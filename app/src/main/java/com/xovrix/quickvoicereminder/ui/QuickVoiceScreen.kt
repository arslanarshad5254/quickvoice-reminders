package com.xovrix.quickvoicereminder.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xovrix.quickvoicereminder.R
import com.xovrix.quickvoicereminder.ads.AdBanner
import com.xovrix.quickvoicereminder.ads.AdManager
import com.xovrix.quickvoicereminder.data.TaskEntity
import com.xovrix.quickvoicereminder.speech.SpeechState
import com.xovrix.quickvoicereminder.ui.components.*
import com.xovrix.quickvoicereminder.ui.theme.*
import com.xovrix.quickvoicereminder.ui.viewmodel.ReminderViewModel
import com.xovrix.quickvoicereminder.ui.viewmodel.TaskFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickVoiceScreen(
    viewModel: ReminderViewModel,
    autoStartVoice: Boolean = false,
    onVoiceAutoStarted: () -> Unit = {},
    onPrivacyOptions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Audio & Notification Permission Launchers
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.startVoiceRecording()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) viewModel.showMessage("Notifications are disabled; reminders cannot alert you")
    }

    val requestReminderPermissions = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                }.onFailure { viewModel.showMessage("Enable Alarms & reminders access for precise alerts") }
            }
        }
    }

    // Automatically trigger voice recording when launched from widget shortcut
    LaunchedEffect(autoStartVoice, hasAudioPermission) {
        if (autoStartVoice) {
            if (hasAudioPermission) {
                viewModel.startVoiceRecording()
                onVoiceAutoStarted()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                onVoiceAutoStarted()
            }
        }
    }

    LaunchedEffect(uiState.isAdsFree) {
        if (!uiState.isAdsFree) {
            AdManager.loadInterstitial(context)
        }
    }

    // Show snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            val isDelete = msg.contains("deleted", ignoreCase = true)
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (isDelete) "Undo" else null,
                duration = if (isDelete) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed && isDelete) {
                viewModel.undoDelete()
            }
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = if (uiState.isAdsFree) 90.dp else 140.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!uiState.isAdsFree) {
                    AdBanner(isAdsFree = uiState.isAdsFree)
                }
                QuickCaptureBar(
                    inputText = uiState.inputText,
                    onInputChanged = viewModel::onInputTextChanged,
                    onSendClicked = {
                        viewModel.processInputAndSave(autoSave = false)
                    },
                    speechState = uiState.speechState,
                    onStartVoice = {
                        if (hasAudioPermission) {
                            viewModel.startVoiceRecording()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopVoice = {
                        viewModel.stopVoiceRecording()
                    },
                    isParsing = uiState.isParsingAi,
                    onQuickChipClicked = viewModel::applyQuickChip
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openNewTask,
                containerColor = ElectricIndigo,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .testTag("add_custom_reminder_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add Custom Reminder",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Selection Mode Header or Stats Header
            if (uiState.isSelectionMode) {
                SelectionHeader(
                    selectedCount = uiState.selectedTaskIds.size,
                    onDeleteSelected = viewModel::requestDeleteSelected, // Changed to request
                    onSelectAll = viewModel::selectAllDisplayedTasks,
                    onCancel = viewModel::toggleSelectionMode
                )
            } else {
                StatsHeader(
                    stats = uiState.stats,
                    selectedFilter = uiState.filter,
                    onFilterSelected = viewModel::setFilter,
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = viewModel::selectCategory,
                    selectedDate = uiState.selectedDate,
                    onDateSelected = viewModel::setDateFilter,
                    searchQuery = uiState.searchQuery,
                    onSearchChanged = viewModel::onSearchQueryChanged,
                    onClearCompleted = viewModel::requestDeleteAll, // Changed to request delete all
                    isAdsFree = uiState.isAdsFree,
                    onUpgradeClick = viewModel::openUpgradeDialog,
                    onSettingsClick = viewModel::openSettingsDialog,
                    onBulkSelectClick = viewModel::toggleSelectionMode
                )
            }

            // Task List or Empty State
            if (uiState.displayedTasks.isEmpty()) {
                EmptyTasksView(
                    filter = uiState.filter,
                    selectedCategory = uiState.selectedCategory,
                    selectedDate = uiState.selectedDate,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)
                ) {
                    items(
                        items = uiState.displayedTasks,
                        key = { it.id }
                    ) { task ->
                        SwipeableTaskItem(
                            task = task,
                            onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                            onEdit = { viewModel.openEditTask(task) },
                            onDelete = { viewModel.requestDeleteTask(task) }, // Changed to request confirmation
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = uiState.selectedTaskIds.contains(task.id),
                            isBeingDeleted = uiState.taskToDelete?.id == task.id,
                            onToggleSelection = { viewModel.toggleTaskSelection(task.id) }
                        )
                    }
                }
            }
        }

        // Upgrade / Ad-Free Dialog
        if (uiState.showUpgradeDialog) {
            UpgradeAdFreeDialog(
                isAdsFree = uiState.isAdsFree,
                formattedPrice = uiState.subPrice,
                onDismiss = viewModel::closeUpgradeDialog,
                onSubscribe = {
                    context.findActivity()?.let { act ->
                        viewModel.launchUpgradePurchase(act)
                    }
                },
                onRestorePurchases = viewModel::restorePurchases
            )
        }

        // Edit Task Modal Dialog
        uiState.editingTask?.let { taskToEdit ->
            EditTaskDialog(
                task = taskToEdit,
                onDismiss = viewModel::closeEditTask,
                onSave = { updatedTask ->
                    if (updatedTask.dueDate != null) requestReminderPermissions()
                    val isNew = updatedTask.id == 0L
                    val accepted = viewModel.updateTask(updatedTask)
                    if (isNew && accepted) {
                        context.findActivity()?.let { act ->
                            AdManager.showInterstitialEveryNth(act, isAdsFree = uiState.isAdsFree)
                        }
                    }
                }
            )
        }

        // Parsed Review Dialog
        uiState.pendingConfirmation?.let { parsed ->
            ParsedReviewSheet(
                parsed = parsed,
                onConfirm = { updatedParsed ->
                    if (updatedParsed.dueDate != null) requestReminderPermissions()
                    if (viewModel.confirmPendingTask(updatedParsed)) {
                        context.findActivity()?.let { act ->
                            AdManager.showInterstitialEveryNth(act, isAdsFree = uiState.isAdsFree)
                        }
                    }
                },
                onDismiss = viewModel::dismissPendingTask
            )
        }

        // Settings Dialog
        if (uiState.showSettingsDialog) {
            SettingsDialog(
                currentToneUri = uiState.currentToneUri,
                onDismiss = viewModel::closeSettingsDialog,
                onToneSelected = { uri ->
                    viewModel.updateNotificationTone(uri)
                },
                onPrivacyOptions = onPrivacyOptions
            )
        }

        // Delete Individual Confirmation
        uiState.taskToDelete?.let { task ->
            DeleteConfirmationDialog(
                onConfirm = { viewModel.deleteTask(task) },
                onDismiss = viewModel::cancelDeleteTask
            )
        }

        // Delete All Confirmation
        if (uiState.isDeletingAll) {
            DeleteConfirmationDialog(
                title = "Clear All Reminders?",
                message = "Are you sure you want to delete all ${uiState.displayedTasks.size} displayed reminders? This action cannot be easily undone.",
                onConfirm = viewModel::deleteAllDisplayedTasks,
                onDismiss = viewModel::cancelDeleteTask
            )
        }

        // Delete Selected Confirmation
        if (uiState.isDeletingSelected) {
            DeleteConfirmationDialog(
                title = "Delete Selected?",
                message = "Are you sure you want to delete ${uiState.selectedTaskIds.size} selected reminders?",
                onConfirm = viewModel::deleteSelectedTasks,
                onDismiss = viewModel::cancelDeleteTask
            )
        }
    }
}

@Composable
fun SelectionHeader(
    selectedCount: Int,
    onDeleteSelected: () -> Unit,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ElectricIndigo,
        contentColor = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$selectedCount Selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Row {
                TextButton(onClick = onSelectAll, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text("Select All")
                }
                if (selectedCount > 0) {
                    IconButton(onClick = onDeleteSelected) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTasksView(
    filter: TaskFilter,
    selectedCategory: String?,
    selectedDate: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (filter == TaskFilter.ALL && selectedCategory == null && selectedDate == null) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "QuickVoice Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            selectedDate != null -> Icons.Outlined.CalendarMonth
                            filter == TaskFilter.COMPLETED -> Icons.Outlined.CheckCircle
                            filter == TaskFilter.NAG_MODE -> Icons.Filled.LocalFireDepartment
                            filter == TaskFilter.TODAY -> Icons.Outlined.Today
                            filter == TaskFilter.UPCOMING -> Icons.Outlined.Event
                            else -> Icons.Outlined.Alarm
                        },
                        contentDescription = "Empty State",
                        tint = if (filter == TaskFilter.NAG_MODE) AmberFlame else ElectricIndigo,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Text(
                text = when {
                    selectedDate != null -> "No reminders due on $selectedDate"
                    filter == TaskFilter.COMPLETED -> "No completed reminders yet"
                    filter == TaskFilter.NAG_MODE -> "No urgent nag reminders"
                    filter == TaskFilter.TODAY -> "Nothing due today"
                    filter == TaskFilter.UPCOMING -> "No upcoming reminders"
                    filter == TaskFilter.ALL -> if (selectedCategory != null) "No tasks in '$selectedCategory'" else "All caught up!"
                    else -> "No reminders found"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Tap the mic icon or type in the bar below to add a reminder in under 3 seconds.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTaskItem(
    task: TaskEntity,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isBeingDeleted: Boolean = false,
    onToggleSelection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    LaunchedEffect(isBeingDeleted) {
        if (!isBeingDeleted && (dismissState.currentValue != SwipeToDismissBoxValue.Settled || dismissState.targetValue != SwipeToDismissBoxValue.Settled)) {
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag("swipe_dismiss_${task.id}"),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isStartToEnd = direction == SwipeToDismissBoxValue.StartToEnd
            val alignment = if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            val targetScale by animateFloatAsState(
                targetValue = if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) 1.15f else 0.85f,
                label = "deleteScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) Color.Transparent else CoralRed)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isStartToEnd) Arrangement.Start else Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(targetScale)
                ) {
                    if (isStartToEnd) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete task",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete task",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        content = {
            TaskCard(
                task = task,
                onToggleComplete = onToggleComplete,
                onEdit = onEdit,
                onDelete = onDelete,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
                onToggleSelection = onToggleSelection
            )
        }
    )
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

