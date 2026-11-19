package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ActiveTool
import com.example.data.model.AgentTask
import com.example.data.model.TaskStatus
import com.example.data.model.Workspace
import com.example.data.model.WorkspaceEnvironment
import com.example.data.model.WorkspaceStatus
import com.example.ui.components.EmptyMissionState
import com.example.ui.components.InlineTelemetryError
import com.example.ui.components.TaskStatusPill
import com.example.ui.components.TelemetryCard
import com.example.ui.components.TelemetryLoadingBlock
import com.example.ui.components.WorkspaceStatusPill
import com.example.ui.components.formatRelativeTimestamp
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.viewmodel.DataUiState

@Composable
fun DeckScreen(
    workspacesState: DataUiState<List<Workspace>>,
    tasksState: DataUiState<List<AgentTask>>,
    selectedWorkspaceId: String?,
    pendingApprovalsCount: Int,
    isActionInFlight: Boolean,
    onSelectWorkspace: (String?) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenApprovals: () -> Unit,
    onOpenRelaySetup: () -> Unit,
    onPauseTask: (AgentTask) -> Unit,
    onResumeTask: (AgentTask) -> Unit,
    onStopTask: (AgentTask) -> Unit,
    onRegisterWorkspace: (String, String, WorkspaceEnvironment, String, String, () -> Unit) -> Unit,
    onDispatchTask: (String, String, String, ActiveTool, (String) -> Unit) -> Unit
) {
    val telemetry = LocalTelemetryColors.current
    var showPairDialog by remember { mutableStateOf(false) }
    var showDispatchDialog by remember { mutableStateOf(false) }
    var pendingStopTask by remember { mutableStateOf<AgentTask?>(null) }

    val workspaces = (workspacesState as? DataUiState.Success)?.data.orEmpty()
    val allTasks = (tasksState as? DataUiState.Success)?.data.orEmpty()
    val filteredTasks = remember(allTasks, selectedWorkspaceId) {
        if (selectedWorkspaceId == null) allTasks
        else allTasks.filter { it.workspaceId == selectedWorkspaceId }
    }
    val runningCount = remember(allTasks) {
        allTasks.count { it.statusEnum == TaskStatus.RUNNING || it.statusEnum == TaskStatus.AWAITING_APPROVAL }
    }
    val onlineWorkspacesCount = remember(workspaces) {
        workspaces.count { it.statusEnum == WorkspaceStatus.ONLINE }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "COMMAND DECK",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (pendingApprovalsCount > 0) {
                        Surface(
                            onClick = onOpenApprovals,
                            shape = RoundedCornerShape(999.dp),
                            color = telemetry.warningContainer,
                            border = BorderStroke(1.dp, telemetry.warning.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("pending_approvals_banner_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VerifiedUser,
                                    contentDescription = "Pending approvals",
                                    tint = telemetry.warning,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "$pendingApprovalsCount GATES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = telemetry.warning
                                )
                            }
                        }
                    }
                }
            }

            // Telemetry KPI Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiTile(
                        label = "ACTIVE RUNS",
                        value = runningCount.toString(),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiTile(
                        label = "PENDING GATES",
                        value = pendingApprovalsCount.toString(),
                        accent = if (pendingApprovalsCount > 0) telemetry.warning else telemetry.online,
                        modifier = Modifier.weight(1f)
                    )
                    KpiTile(
                        label = "ONLINE HOSTS",
                        value = "$onlineWorkspacesCount/${workspaces.size}",
                        accent = telemetry.online,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Workspace Selector Strip
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WORKSPACES",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { showPairDialog = true },
                            modifier = Modifier.testTag("pair_workspace_inline_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_pair_workspace))
                        }
                    }

                    when (workspacesState) {
                        is DataUiState.Loading -> TelemetryLoadingBlock("Syncing paired workspaces…")
                        is DataUiState.Error -> InlineTelemetryError(workspacesState.message)
                        is DataUiState.Success -> {
                            if (workspaces.isEmpty()) {
                                EmptyMissionState(
                                    icon = Icons.Outlined.Dns,
                                    title = stringResource(R.string.empty_workspaces_title),
                                    body = stringResource(R.string.empty_workspaces_body),
                                    primaryActionLabel = stringResource(R.string.action_pair_workspace),
                                    onPrimaryAction = { showPairDialog = true },
                                    primaryActionTag = "empty_pair_workspace_button",
                                    secondaryActionLabel = "Relay Contract",
                                    onSecondaryAction = onOpenRelaySetup
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedWorkspaceId == null,
                                        onClick = { onSelectWorkspace(null) },
                                        label = { Text("All (${workspaces.size})") },
                                        modifier = Modifier.testTag("workspace_filter_all")
                                    )
                                    workspaces.forEach { ws ->
                                        FilterChip(
                                            selected = selectedWorkspaceId == ws.id,
                                            onClick = { onSelectWorkspace(ws.id) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (ws.environmentEnum == WorkspaceEnvironment.CLOUD_SANDBOX)
                                                        Icons.Outlined.CloudQueue else Icons.Outlined.Computer,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text(ws.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected workspace banner (if one is selected)
            val focusedWorkspace = workspaces.firstOrNull { it.id == selectedWorkspaceId }
            if (focusedWorkspace != null) {
                item {
                    TelemetryCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = focusedWorkspace.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${focusedWorkspace.hostName} · ${focusedWorkspace.branch}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            WorkspaceStatusPill(status = focusedWorkspace.statusEnum)
                        }
                    }
                }
            }

            // Missions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AGENT MISSIONS (${filteredTasks.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (workspaces.isNotEmpty()) {
                        TextButton(
                            onClick = { showDispatchDialog = true },
                            modifier = Modifier.testTag("dispatch_task_header_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RocketLaunch,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_dispatch_task))
                        }
                    }
                }
            }

            when (tasksState) {
                is DataUiState.Loading -> item {
                    TelemetryLoadingBlock("Syncing Antigravity missions…")
                }
                is DataUiState.Error -> item {
                    InlineTelemetryError(tasksState.message)
                }
                is DataUiState.Success -> {
                    if (filteredTasks.isEmpty() && workspaces.isNotEmpty()) {
                        item {
                            EmptyMissionState(
                                icon = Icons.Outlined.RocketLaunch,
                                title = stringResource(R.string.empty_tasks_title),
                                body = stringResource(R.string.empty_tasks_body),
                                primaryActionLabel = stringResource(R.string.action_dispatch_task),
                                onPrimaryAction = { showDispatchDialog = true },
                                primaryActionTag = "empty_dispatch_task_button"
                            )
                        }
                    } else {
                        items(filteredTasks, key = { it.id }) { task ->
                            val workspaceName = workspaces.firstOrNull { it.id == task.workspaceId }?.name
                                ?: "Paired Host"
                            AgentTaskCard(
                                task = task,
                                workspaceName = workspaceName,
                                onOpen = { onOpenTask(task.id) },
                                onPause = { onPauseTask(task) },
                                onResume = { onResumeTask(task) },
                                onRequestStop = { pendingStopTask = task }
                            )
                        }
                    }
                }
            }
        }

        if (workspaces.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showDispatchDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("dispatch_task_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.action_dispatch_task)
                )
            }
        }
    }

    if (showPairDialog) {
        PairWorkspaceDialog(
            isSubmitting = isActionInFlight,
            onDismiss = { showPairDialog = false },
            onSubmit = { name, host, env, repo, branch ->
                onRegisterWorkspace(name, host, env, repo, branch) {
                    showPairDialog = false
                }
            }
        )
    }

    if (showDispatchDialog && workspaces.isNotEmpty()) {
        val defaultWs = workspaces.firstOrNull { it.id == selectedWorkspaceId } ?: workspaces.first()
        DispatchTaskDialog(
            workspaces = workspaces,
            initialWorkspaceId = defaultWs.id,
            isSubmitting = isActionInFlight,
            onDismiss = { showDispatchDialog = false },
            onSubmit = { wsId, title, objective, tool ->
                onDispatchTask(wsId, title, objective, tool) { createdTaskId ->
                    showDispatchDialog = false
                    onOpenTask(createdTaskId)
                }
            }
        )
    }

    pendingStopTask?.let { taskToStop ->
        AlertDialog(
            onDismissRequest = { pendingStopTask = null },
            title = { Text(stringResource(R.string.stop_task_dialog_title)) },
            text = {
                Text("${stringResource(R.string.stop_task_dialog_body)}\n\nMission: ${taskToStop.title}")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onStopTask(taskToStop)
                        pendingStopTask = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_stop_task_button")
                ) {
                    Text(stringResource(R.string.action_confirm_stop))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingStopTask = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun KpiTile(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    TelemetryCard(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = accent
        )
    }
}

@Composable
private fun AgentTaskCard(
    task: AgentTask,
    workspaceName: String,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRequestStop: () -> Unit
) {
    val status = task.statusEnum
    TelemetryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskStatusPill(status = status)
            Text(
                text = formatRelativeTimestamp(task.updatedAt ?: task.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = task.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "$workspaceName · ${task.activeToolEnum.label} · ${task.currentPhase}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LinearProgressIndicator(
                progress = { task.clampedProgress },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = when (status) {
                    TaskStatus.FAILED, TaskStatus.STOPPED -> MaterialTheme.colorScheme.error
                    TaskStatus.COMPLETED -> LocalTelemetryColors.current.online
                    TaskStatus.PAUSED, TaskStatus.AWAITING_APPROVAL -> LocalTelemetryColors.current.warning
                    TaskStatus.RUNNING -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Text(
                text = "${task.progressPercent.coerceIn(0, 100)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (task.summary.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = task.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!status.isTerminal) {
                if (status == TaskStatus.PAUSED) {
                    OutlinedButton(
                        onClick = onResume,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("resume_task_${task.id}")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_resume))
                    }
                } else {
                    OutlinedButton(
                        onClick = onPause,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("pause_task_${task.id}")
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_pause))
                    }
                }
                OutlinedButton(
                    onClick = onRequestStop,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("stop_task_${task.id}")
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_stop))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpen) {
                Text("Inspect")
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Open mission telemetry",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun PairWorkspaceDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, WorkspaceEnvironment, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var hostName by remember { mutableStateOf("") }
    var environment by remember { mutableStateOf(WorkspaceEnvironment.LOCAL_WORKSTATION) }
    var repository by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair Antigravity Workspace") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workspace Name *") },
                    placeholder = { Text("e.g. Studio MacBook Pro") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workspace_name_input")
                )
                OutlinedTextField(
                    value = hostName,
                    onValueChange = { hostName = it },
                    label = { Text("Host / Sandbox ID *") },
                    placeholder = { Text("e.g. dev-workstation.local") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workspace_host_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkspaceEnvironment.entries.forEach { env ->
                        FilterChip(
                            selected = environment == env,
                            onClick = { environment = env },
                            label = {
                                Text(
                                    if (env == WorkspaceEnvironment.LOCAL_WORKSTATION) "Local" else "Cloud"
                                )
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = repository,
                    onValueChange = { repository = it },
                    label = { Text("Repository / Project Path") },
                    placeholder = { Text("e.g. org/antigravity-app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Git Branch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name, hostName, environment, repository, branch) },
                enabled = !isSubmitting && name.isNotBlank() && hostName.isNotBlank(),
                modifier = Modifier.testTag("submit_pair_workspace_button")
            ) {
                Text("Pair")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DispatchTaskDialog(
    workspaces: List<Workspace>,
    initialWorkspaceId: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, ActiveTool) -> Unit
) {
    var workspaceId by remember { mutableStateOf(initialWorkspaceId) }
    var title by remember { mutableStateOf("") }
    var objective by remember { mutableStateOf("") }
    var activeTool by remember { mutableStateOf(ActiveTool.PLANNER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dispatch Remote Mission") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Target Workspace",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    workspaces.forEach { ws ->
                        FilterChip(
                            selected = workspaceId == ws.id,
                            onClick = { workspaceId = ws.id },
                            label = { Text(ws.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Mission Title *") },
                    placeholder = { Text("e.g. Fix OAuth callback race condition") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input")
                )
                OutlinedTextField(
                    value = objective,
                    onValueChange = { objective = it },
                    label = { Text("Agent Objective / Prompt *") },
                    placeholder = { Text("Describe the implementation plan, tests, or fix for Antigravity…") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_objective_input")
                )
                Text(
                    text = "Initial Runtime Tool",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActiveTool.entries.forEach { tool ->
                        FilterChip(
                            selected = activeTool == tool,
                            onClick = { activeTool = tool },
                            label = { Text(tool.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(workspaceId, title, objective, activeTool) },
                enabled = !isSubmitting && title.isNotBlank() && objective.isNotBlank(),
                modifier = Modifier.testTag("submit_dispatch_task_button")
            ) {
                Text("Dispatch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
