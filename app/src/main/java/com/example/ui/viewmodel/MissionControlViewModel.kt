package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActiveTool
import com.example.data.model.AgentTask
import com.example.data.model.ApprovalRequest
import com.example.data.model.ApprovalStatus
import com.example.data.model.ApprovalType
import com.example.data.model.CommandAction
import com.example.data.model.ControlCommand
import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import com.example.data.model.LogSource
import com.example.data.model.RiskLevel
import com.example.data.model.TaskStatus
import com.example.data.model.Workspace
import com.example.data.model.WorkspaceEnvironment
import com.example.data.model.WorkspaceStatus
import com.example.data.repository.AntigravityRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

class MissionControlViewModel(
    private val repository: AntigravityRepository,
    val currentUserId: String
) : ViewModel() {

    private val _selectedWorkspaceId = MutableStateFlow<String?>(null)
    val selectedWorkspaceId: StateFlow<String?> = _selectedWorkspaceId.asStateFlow()

    private val _observedTaskId = MutableStateFlow<String?>(null)

    private val _isActionInFlight = MutableStateFlow(false)
    val isActionInFlight: StateFlow<Boolean> = _isActionInFlight.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val workspacesState: StateFlow<DataUiState<List<Workspace>>> =
        repository.observeWorkspaces(currentUserId)
            .map<List<Workspace>, DataUiState<List<Workspace>>> { DataUiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreError(error, "Failed to sync workspaces")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DataUiState.Loading
            )

    val tasksState: StateFlow<DataUiState<List<AgentTask>>> =
        repository.observeTasks(currentUserId)
            .map<List<AgentTask>, DataUiState<List<AgentTask>>> { DataUiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreError(error, "Failed to sync tasks")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DataUiState.Loading
            )

    val approvalsState: StateFlow<DataUiState<List<ApprovalRequest>>> =
        repository.observeApprovals(currentUserId)
            .map<List<ApprovalRequest>, DataUiState<List<ApprovalRequest>>> { DataUiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreError(error, "Failed to sync approval queue")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DataUiState.Loading
            )

    val commandsState: StateFlow<DataUiState<List<ControlCommand>>> =
        repository.observeCommands(currentUserId)
            .map<List<ControlCommand>, DataUiState<List<ControlCommand>>> { DataUiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreError(error, "Failed to sync commands")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DataUiState.Loading
            )

    val pendingApprovalCount: StateFlow<Int> = approvalsState
        .map { state ->
            if (state is DataUiState.Success) {
                state.data.count { it.statusEnum == ApprovalStatus.PENDING }
            } else {
                0
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedTaskLogsState: StateFlow<DataUiState<List<LogEntry>>> = _observedTaskId
        .flatMapLatest { taskId ->
            if (taskId.isNullOrBlank()) {
                flowOf(DataUiState.Success(emptyList()))
            } else {
                repository.observeTaskLogs(currentUserId, taskId)
                    .map<List<LogEntry>, DataUiState<List<LogEntry>>> { DataUiState.Success(it) }
                    .catch { error -> emit(unwrapFirestoreError(error, "Failed to stream task logs")) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DataUiState.Loading
        )

    private fun unwrapFirestoreError(error: Throwable, fallback: String): DataUiState.Error {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, fallback, rootError)
        return DataUiState.Error(rootError.localizedMessage ?: fallback)
    }

    fun selectWorkspace(workspaceId: String?) {
        _selectedWorkspaceId.value = workspaceId
    }

    fun observeLogsForTask(taskId: String?) {
        _observedTaskId.value = taskId
    }

    fun registerWorkspace(
        name: String,
        hostName: String,
        environment: WorkspaceEnvironment,
        repositoryPath: String,
        branch: String,
        onSuccess: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isActionInFlight.value = true
            val result = repository.registerWorkspace(
                userId = currentUserId,
                name = name,
                hostName = hostName,
                environment = environment,
                repository = repositoryPath,
                branch = branch
            )
            _isActionInFlight.value = false
            result.onSuccess { workspaceId ->
                _selectedWorkspaceId.value = workspaceId
                _snackbarEvents.tryEmit("Workspace paired: $name")
                onSuccess(workspaceId)
            }.onFailure { error ->
                _snackbarEvents.tryEmit(error.localizedMessage ?: "Unable to register workspace.")
            }
        }
    }

    fun pingWorkspace(workspace: Workspace, nextStatus: WorkspaceStatus = WorkspaceStatus.ONLINE) {
        viewModelScope.launch {
            val result = repository.pingWorkspaceHeartbeat(currentUserId, workspace.id, nextStatus)
            result.onSuccess {
                _snackbarEvents.tryEmit("Heartbeat synced for ${workspace.name}")
            }.onFailure { error ->
                _snackbarEvents.tryEmit(error.localizedMessage ?: "Heartbeat update failed.")
            }
        }
    }

    fun deleteWorkspace(workspace: Workspace) {
        viewModelScope.launch {
            val result = repository.deleteWorkspace(currentUserId, workspace.id)
            result.onSuccess {
                if (_selectedWorkspaceId.value == workspace.id) {
                    _selectedWorkspaceId.value = null
                }
                _snackbarEvents.tryEmit("Removed workspace ${workspace.name}")
            }.onFailure { error ->
                _snackbarEvents.tryEmit(error.localizedMessage ?: "Failed to remove workspace.")
            }
        }
    }

    fun dispatchTask(
        workspaceId: String,
        title: String,
        objective: String,
        activeTool: ActiveTool,
        onDispatched: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isActionInFlight.value = true
            val result = repository.dispatchTask(
                userId = currentUserId,
                workspaceId = workspaceId,
                title = title,
                objective = objective,
                activeTool = activeTool
            )
            _isActionInFlight.value = false
            result.onSuccess { taskId ->
                _snackbarEvents.tryEmit("Mission dispatched to Antigravity relay.")
                onDispatched(taskId)
            }.onFailure { error ->
                _snackbarEvents.tryEmit(error.localizedMessage ?: "Failed to dispatch task.")
            }
        }
    }

    fun pauseTask(task: AgentTask) {
        viewModelScope.launch {
            val result = repository.controlTaskLifecycle(
                userId = currentUserId,
                task = task,
                targetStatus = TaskStatus.PAUSED,
                action = CommandAction.PAUSE,
                phaseLabel = "Paused remotely",
                logMessage = "Operator paused agent run."
            )
            result.onSuccess {
                _snackbarEvents.tryEmit("PAUSE command queued for ${task.title}")
            }.onFailure { error ->
                _snackbarEvents.tryEmit(error.localizedMessage ?: "Unable to pause task.")
            }
        }
    }

    fun resumeTask(task: AgentTask) {
        viewModelScope.launch {
            val result = repository.controlTaskLifecycle(
                userId = currentUserId,
                task = task,
                targetStatus = TaskStatus.RUNNING,
                action = CommandAction.RESUME,
                phaseLabel = "Resumed remotely",
                logMessage = "Operator resumed agent run."
            )
            result.onSuccess {
                _snackbarEvents.tryEmit("RESUME command queued for ${task.title}")
            }.onFailure { error ->
                _snackbarEvents.tryEmit(error.localizedMessage ?: "Unable to resume task.")
            }
        }
    }

    fun stopTask(task: AgentTask) {
        viewModelScope.launch {
            val result = repository.controlTaskLifecycle(
                userId = currentUserId,
                task = task,
                targetStatus = TaskStatus.STOPPED,
                action = CommandAction.STOP,
                phaseLabel = "Stopped by remote operator",
                logMessage = "Operator issued emergency STOP."
            )
            result.onSuccess {
                _snackbarEvents.tryEmit("STOP command sent — run sealed.")
            }.onFailure { error ->
                _snackbarEvents.tryEmit(error.localizedMessage ?: "Unable to stop task.")
            }
        }
    }

    fun sendSteeringPrompt(task: AgentTask, prompt: String, onSent: () -> Unit = {}) {
        viewModelScope.launch {
            _isActionInFlight.value = true
            val result = repository.sendSteeringPrompt(currentUserId, task, prompt)
            _isActionInFlight.value = false
            result.onSuccess {
                _snackbarEvents.tryEmit("Steering prompt queued for Antigravity.")
                onSent()
            }.onFailure { error ->
                _snackbarEvents.tryEmit(error.localizedMessage ?: "Failed to send prompt.")
            }
        }
    }

    fun decideApproval(approval: ApprovalRequest, approve: Boolean, note: String = "") {
        viewModelScope.launch {
            _isActionInFlight.value = true
            val result = repository.submitApprovalDecision(currentUserId, approval, approve, note)
            _isActionInFlight.value = false
            result.onSuccess {
                val verb = if (approve) "Approved" else "Rejected"
                _snackbarEvents.tryEmit("$verb: ${approval.title}")
            }.onFailure { error ->
                _snackbarEvents.tryEmit(error.localizedMessage ?: "Decision could not be saved.")
            }
        }
    }

    fun createApprovalGate(
        task: AgentTask,
        type: ApprovalType,
        title: String,
        description: String,
        payload: String,
        riskLevel: RiskLevel,
        onCreated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isActionInFlight.value = true
            val result = repository.createApprovalGate(
                userId = currentUserId,
                task = task,
                type = type,
                title = title,
                description = description,
                payload = payload,
                riskLevel = riskLevel
            )
            _isActionInFlight.value = false
            result.onSuccess {
                _snackbarEvents.tryEmit("Approval gate added to queue.")
                onCreated()
            }.onFailure { error ->
                _snackbarEvents.tryEmit(error.localizedMessage ?: "Could not create gate.")
            }
        }
    }

    fun appendManualLog(taskId: String, level: LogLevel, source: LogSource, message: String) {
        viewModelScope.launch {
            val result = repository.appendLogEntry(currentUserId, taskId, level, source, message)
            result.onFailure { error ->
                _snackbarEvents.tryEmit(error.localizedMessage ?: "Failed to write log.")
            }
        }
    }

    private companion object {
        const val TAG = "MissionControlVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
