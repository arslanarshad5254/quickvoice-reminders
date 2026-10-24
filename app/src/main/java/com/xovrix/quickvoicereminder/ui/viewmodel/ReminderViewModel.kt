package com.xovrix.quickvoicereminder.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xovrix.quickvoicereminder.QuickVoiceApp
import com.xovrix.quickvoicereminder.ai.AiTaskParser
import com.xovrix.quickvoicereminder.ai.ParsedTaskResult
import com.xovrix.quickvoicereminder.data.TaskEntity
import com.xovrix.quickvoicereminder.speech.SpeechManager
import com.xovrix.quickvoicereminder.speech.SpeechState
import com.xovrix.quickvoicereminder.ui.components.CategoryUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

enum class TaskFilter {
    ALL,
    TODAY,
    UPCOMING,
    NAG_MODE,
    COMPLETED
}

data class FilterParams(
    val filter: TaskFilter,
    val category: String?,
    val dateFilter: String?,
    val query: String
)

data class DashboardStats(
    val activeCount: Int = 0,
    val todayCount: Int = 0,
    val nagCount: Int = 0,
    val completedCount: Int = 0
)

data class ReminderUiState(
    val activeTasks: List<TaskEntity> = emptyList(),
    val completedTasks: List<TaskEntity> = emptyList(),
    val displayedTasks: List<TaskEntity> = emptyList(),
    val filter: TaskFilter = TaskFilter.ALL,
    val selectedCategory: String? = null,
    val selectedDate: String? = null,
    val searchQuery: String = "",
    val inputText: String = "",
    val isParsingAi: Boolean = false,
    val parsingFeedback: String? = null,
    val pendingConfirmation: ParsedTaskResult? = null,
    val editingTask: TaskEntity? = null,
    val stats: DashboardStats = DashboardStats(),
    val speechState: SpeechState = SpeechState.Idle,
    val snackbarMessage: String? = null,
    val isAdsFree: Boolean = false,
    val subPrice: String = "Price unavailable",
    val showUpgradeDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val currentToneUri: String? = null,
    val selectedTaskIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val taskToDelete: TaskEntity? = null,
    val isDeletingAll: Boolean = false,
    val isDeletingSelected: Boolean = false
)

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as QuickVoiceApp).repository
    val billingManager = (application as QuickVoiceApp).billingManager
    val settingsManager = (application as QuickVoiceApp).settingsManager
    private val taskParser = AiTaskParser(application)
    val speechManager = SpeechManager(application)

    private val _uiState = MutableStateFlow(ReminderUiState(
        currentToneUri = settingsManager.notificationToneUri
    ))
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()

    private var lastDeletedTasks = emptyList<TaskEntity>()

    init {
        // Collect billing state
        viewModelScope.launch {
            combine(
                billingManager.isAdsFree,
                billingManager.formattedPrice,
                billingManager.purchaseStatusMessage
            ) { isAdsFree, price, statusMsg ->
                Triple(isAdsFree, price, statusMsg)
            }.collect { (isAdsFree, price, statusMsg) ->
                _uiState.update {
                    it.copy(
                        isAdsFree = isAdsFree,
                        subPrice = price,
                        snackbarMessage = statusMsg ?: it.snackbarMessage
                    )
                }
                if (statusMsg != null) {
                    billingManager.clearStatusMessage()
                }
            }
        }
    }

    init {
        // Collect SpeechState
        viewModelScope.launch {
            speechManager.speechState.collect { sState ->
                _uiState.update { it.copy(speechState = sState) }
                if (sState is SpeechState.Listening && sState.partialText.isNotBlank()) {
                    _uiState.update { it.copy(inputText = sState.partialText) }
                } else if (sState is SpeechState.Result) {
                    _uiState.update { it.copy(inputText = sState.text) }
                }
            }
        }

        // Collect Room active and completed tasks
        viewModelScope.launch {
            val tasksFlow = combine(repository.activeTasks, repository.completedTasks) { active, completed ->
                Pair(active, completed)
            }
            val filtersFlow = combine(
                _uiState.map { it.filter }.distinctUntilChanged(),
                _uiState.map { it.selectedCategory }.distinctUntilChanged(),
                _uiState.map { it.selectedDate }.distinctUntilChanged(),
                _uiState.map { it.searchQuery }.distinctUntilChanged()
            ) { filter, category, dateFilter, query ->
                FilterParams(filter, category, dateFilter, query)
            }

            combine(tasksFlow, filtersFlow) { (active, completed), (filter, category, dateFilter, query) ->
                val todayStr = LocalDate.now().toString()
                val baseList = when (filter) {
                    TaskFilter.ALL -> active
                    TaskFilter.TODAY -> active.filter { it.dueDate == todayStr }
                    TaskFilter.UPCOMING -> active.filter { it.dueDate != null && it.dueDate > todayStr }
                    TaskFilter.NAG_MODE -> active.filter { it.isNag }
                    TaskFilter.COMPLETED -> completed
                }

                val filtered = baseList.filter { task ->
                    val matchesDate = dateFilter == null || task.dueDate == dateFilter
                    val matchesCategory = category == null || task.category.equals(category, ignoreCase = true)
                    val matchesQuery = query.isBlank() ||
                            task.task.contains(query, ignoreCase = true) ||
                            (task.location?.contains(query, ignoreCase = true) == true) ||
                            task.category.contains(query, ignoreCase = true)
                    matchesDate && matchesCategory && matchesQuery
                }

                val stats = DashboardStats(
                    activeCount = active.size,
                    todayCount = active.count { it.dueDate == todayStr },
                    nagCount = active.count { it.isNag },
                    completedCount = completed.size
                )

                _uiState.update {
                    it.copy(
                        activeTasks = active,
                        completedTasks = completed,
                        displayedTasks = filtered,
                        stats = stats
                    )
                }
            }.collect()
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilter(filter: TaskFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun setDateFilter(date: String?) {
        _uiState.update { it.copy(selectedDate = if (it.selectedDate == date) null else date) }
    }

    fun selectCategory(category: String?) {
        _uiState.update {
            it.copy(selectedCategory = if (it.selectedCategory == category) null else category)
        }
    }

    fun startVoiceRecording() {
        speechManager.startListening()
    }

    fun stopVoiceRecording() {
        speechManager.stopListening()
    }

    fun processInputAndSave(customInput: String? = null, autoSave: Boolean = false) {
        if (_uiState.value.isParsingAi) return
        val textToParse = (customInput ?: _uiState.value.inputText).trim()
        if (textToParse.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isParsingAi = true,
                    parsingFeedback = "Parsing with AI..."
                )
            }

            try {
                val parsed = taskParser.parseReminder(textToParse)

                if (autoSave) {
                    saveParsedTask(parsed)
                    _uiState.update {
                        it.copy(
                            inputText = "",
                            isParsingAi = false,
                            parsingFeedback = null,
                            snackbarMessage = "Saved: \"${parsed.task}\"" +
                                    (if (parsed.dueDate != null) " for ${parsed.dueDate} ${CategoryUtils.format12HourTime(parsed.dueTime)}" else "")
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isParsingAi = false,
                            parsingFeedback = null,
                            pendingConfirmation = parsed
                        )
                    }
                }
            } catch (e: Exception) {
                // Fallback to local heuristic parser
                val fallback = taskParser.parseLocally(textToParse)
                if (autoSave) {
                    saveParsedTask(fallback)
                    _uiState.update {
                        it.copy(
                            inputText = "",
                            isParsingAi = false,
                            parsingFeedback = null,
                            snackbarMessage = "Saved: \"${fallback.task}\""
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isParsingAi = false,
                            parsingFeedback = null,
                            pendingConfirmation = fallback
                        )
                    }
                }
            }
        }
    }

    fun confirmPendingTask(parsed: ParsedTaskResult): Boolean {
        scheduleValidationError(parsed.dueDate, parsed.dueTime)?.let { error ->
            _uiState.update { it.copy(snackbarMessage = error) }
            return false
        }
        viewModelScope.launch {
            saveParsedTask(parsed)
            _uiState.update {
                it.copy(
                    inputText = "",
                    pendingConfirmation = null,
                    snackbarMessage = "Reminder scheduled!"
                )
            }
        }
        return true
    }

    fun dismissPendingTask() {
        _uiState.update { it.copy(pendingConfirmation = null) }
    }

    private suspend fun saveParsedTask(parsed: ParsedTaskResult) {
        val taskEntity = TaskEntity(
            task = parsed.task,
            dueDate = parsed.dueDate,
            dueTime = parsed.dueTime,
            scheduledDateTime = parsed.scheduledDateTime,
            category = parsed.category,
            location = parsed.location,
            isNag = parsed.isNag,
            endDate = parsed.endDate,
            endTime = parsed.endTime,
            isAllDay = parsed.isAllDay,
            isRecurring = parsed.isRecurring,
            recurrence = parsed.recurrence,
            recurrenceDays = parsed.recurrenceDays?.joinToString(","),
            recurrenceIntervalDays = parsed.recurrenceIntervalDays,
            isCompleted = false
        )
        repository.insertTask(taskEntity)
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            val newStatus = !task.isCompleted
            repository.setTaskCompletion(task.id, newStatus)
            _uiState.update {
                it.copy(
                    snackbarMessage = if (newStatus) "Task completed! 🎉" else "Task restored to active"
                )
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            lastDeletedTasks = listOf(task)
            repository.deleteTask(task)
            _uiState.update { it.copy(
                snackbarMessage = "Reminder deleted",
                taskToDelete = null
            ) }
        }
    }

    fun requestDeleteTask(task: TaskEntity) {
        _uiState.update { it.copy(taskToDelete = task) }
    }

    fun cancelDeleteTask() {
        _uiState.update { it.copy(taskToDelete = null, isDeletingAll = false, isDeletingSelected = false) }
    }

    fun requestDeleteSelected() {
        _uiState.update { it.copy(isDeletingSelected = true) }
    }

    fun requestDeleteAll() {
        _uiState.update { it.copy(isDeletingAll = true) }
    }

    fun deleteAllDisplayedTasks() {
        viewModelScope.launch {
            val tasksToDelete = _uiState.value.displayedTasks
            lastDeletedTasks = tasksToDelete
            tasksToDelete.forEach { repository.deleteTask(it) }
            _uiState.update { it.copy(
                snackbarMessage = "${tasksToDelete.size} reminders deleted",
                isDeletingAll = false,
                isSelectionMode = false,
                selectedTaskIds = emptySet()
            ) }
        }
    }

    fun toggleSelectionMode() {
        _uiState.update { 
            val newMode = !it.isSelectionMode
            it.copy(
                isSelectionMode = newMode,
                selectedTaskIds = if (!newMode) emptySet() else it.selectedTaskIds
            )
        }
    }

    fun toggleTaskSelection(taskId: Long) {
        _uiState.update { state ->
            val current = state.selectedTaskIds
            val updated = if (current.contains(taskId)) {
                current - taskId
            } else {
                current + taskId
            }
            state.copy(
                selectedTaskIds = updated,
                isSelectionMode = updated.isNotEmpty() || state.isSelectionMode
            )
        }
    }

    fun selectAllDisplayedTasks() {
        _uiState.update { state ->
            val allIds = state.displayedTasks.map { it.id }.toSet()
            state.copy(selectedTaskIds = allIds)
        }
    }

    fun deleteSelectedTasks() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedTaskIds
            val tasksToDelete = _uiState.value.activeTasks.filter { ids.contains(it.id) } + 
                               _uiState.value.completedTasks.filter { ids.contains(it.id) }
            
            lastDeletedTasks = tasksToDelete
            tasksToDelete.forEach { repository.deleteTask(it) }
            
            _uiState.update { it.copy(
                selectedTaskIds = emptySet(),
                isSelectionMode = false,
                snackbarMessage = "${tasksToDelete.size} reminders deleted"
            ) }
        }
    }

    fun undoDelete() {
        val tasksToRestore = lastDeletedTasks
        if (tasksToRestore.isEmpty()) return
        
        viewModelScope.launch {
            tasksToRestore.forEach { task ->
                repository.insertTask(task.copy(id = 0))
            }
            lastDeletedTasks = emptyList()
            _uiState.update { it.copy(snackbarMessage = if (tasksToRestore.size == 1) "Reminder restored" else "${tasksToRestore.size} reminders restored") }
        }
    }

    fun openEditTask(task: TaskEntity?) {
        _uiState.update { it.copy(editingTask = task) }
    }

    fun openNewTask() {
        val defaultSchedule = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
        val newTask = TaskEntity(
            task = "",
            category = "General",
            dueDate = defaultSchedule.toLocalDate().toString(),
            dueTime = defaultSchedule.toLocalTime().toString().take(5)
        )
        _uiState.update { it.copy(editingTask = newTask) }
    }

    fun closeEditTask() {
        _uiState.update { it.copy(editingTask = null) }
    }

    fun updateTask(updatedTask: TaskEntity): Boolean {
        scheduleValidationError(updatedTask.dueDate, updatedTask.dueTime)?.let { error ->
            _uiState.update { it.copy(snackbarMessage = error) }
            return false
        }
        viewModelScope.launch {
            if (updatedTask.id == 0L) {
                repository.insertTask(updatedTask)
            } else {
                repository.updateTask(updatedTask)
            }
            _uiState.update {
                it.copy(
                    editingTask = null,
                    snackbarMessage = if (updatedTask.id == 0L) "Task created successfully" else "Task updated successfully"
                )
            }
        }
        return true
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repository.clearCompletedTasks()
            _uiState.update { it.copy(snackbarMessage = "Completed tasks cleared") }
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun applyQuickChip(prefix: String) {
        val current = _uiState.value.inputText
        val newText = if (current.isBlank()) prefix else "$current $prefix"
        _uiState.update { it.copy(inputText = newText) }
    }

    fun openUpgradeDialog() {
        _uiState.update { it.copy(showUpgradeDialog = true) }
    }

    fun closeUpgradeDialog() {
        _uiState.update { it.copy(showUpgradeDialog = false) }
    }

    fun launchUpgradePurchase(activity: android.app.Activity) {
        billingManager.launchBillingFlow(activity)
    }

    fun restorePurchases() {
        billingManager.queryActivePurchases()
        _uiState.update { it.copy(snackbarMessage = "Checking active subscriptions...") }
    }

    fun updateNotificationTone(uri: String) {
        settingsManager.notificationToneUri = uri
        _uiState.update { it.copy(
            currentToneUri = uri,
            snackbarMessage = "Notification tone updated"
        ) }
    }

    fun openSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun closeSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun openTaskById(taskId: Long) {
        viewModelScope.launch {
            repository.getTaskById(taskId)?.let { task ->
                _uiState.update { it.copy(editingTask = task) }
            }
        }
    }

    fun rescheduleActiveTasks() {
        viewModelScope.launch { repository.rescheduleAll() }
    }

    private fun scheduleValidationError(date: String?, time: String?): String? {
        if (date.isNullOrBlank()) return null
        return runCatching {
            val scheduled = LocalDateTime.of(
                LocalDate.parse(date),
                java.time.LocalTime.parse(time ?: "09:00")
            )
            if (!scheduled.isAfter(LocalDateTime.now())) "Choose a future date and time" else null
        }.getOrElse { "The reminder date or time is invalid" }
    }

    override fun onCleared() {
        speechManager.stopListening()
        super.onCleared()
    }
}
