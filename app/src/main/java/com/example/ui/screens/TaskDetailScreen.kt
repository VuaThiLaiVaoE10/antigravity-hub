package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AgentTask
import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import com.example.data.model.TaskStatus
import com.example.ui.components.EmptyMissionState
import com.example.ui.components.InlineTelemetryError
import com.example.ui.components.TaskStatusPill
import com.example.ui.components.TelemetryCard
import com.example.ui.components.TelemetryLoadingBlock
import com.example.ui.components.formatClockTimestamp
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.viewmodel.DataUiState

private enum class TelemetryViewMode { TERMINAL, TIMELINE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    task: AgentTask?,
    workspaceName: String,
    logsState: DataUiState<List<LogEntry>>,
    isActionInFlight: Boolean,
    onStartObservingLogs: (String) -> Unit,
    onStopObservingLogs: () -> Unit,
    onBack: () -> Unit,
    onPause: (AgentTask) -> Unit,
    onResume: (AgentTask) -> Unit,
    onStop: (AgentTask) -> Unit,
    onSendSteeringPrompt: (AgentTask, String, () -> Unit) -> Unit,
    onAuditLogsWithAi: (AgentTask, String) -> Unit,
    onCopiedNotice: (String) -> Unit
) {
    val telemetry = LocalTelemetryColors.current
    val clipboard = LocalClipboardManager.current

    var viewMode by remember { mutableStateOf(TelemetryViewMode.TERMINAL) }
    var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) }
    var steeringInput by remember { mutableStateOf("") }
    var showStopConfirm by remember { mutableStateOf(false) }

    val taskId = task?.id
    DisposableEffect(taskId) {
        if (!taskId.isNullOrBlank()) {
            onStartObservingLogs(taskId)
        }
        onDispose { onStopObservingLogs() }
    }

    val allLogs = (logsState as? DataUiState.Success)?.data.orEmpty()
    val filteredLogs by remember(allLogs, selectedLevelFilter, viewMode) {
        derivedStateOf {
            val modeFiltered = if (viewMode == TelemetryViewMode.TIMELINE) {
                allLogs.filter { it.levelEnum == LogLevel.STEP || it.levelEnum == LogLevel.COMMAND || it.levelEnum == LogLevel.ERROR }
            } else {
                allLogs
            }
            if (selectedLevelFilter == null) modeFiltered
            else modeFiltered.filter { it.levelEnum == selectedLevelFilter }
        }
    }

    val quickChips = remember {
        listOf(
            "Summarize current progress",
            "Run unit tests before continuing",
            "Pause and explain root cause",
            "Focus on fixing build errors first"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("task_detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Command Deck"
                    )
                }
            },
            title = {
                Column {
                    Text(
                        text = task?.title ?: "Mission Telemetry",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = workspaceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            actions = {
                if (task != null) {
                    IconButton(
                        onClick = {
                            val transcript = allLogs.joinToString("\n") { entry ->
                                "[${formatClockTimestamp(entry.createdAt)}] [${entry.levelEnum.label}/${entry.sourceEnum.label}] ${entry.message}"
                            }.ifBlank { "Task Objective: ${task.objective}\nCurrent Phase: ${task.currentPhase}\nSummary: ${task.summary}" }
                            onAuditLogsWithAi(task, transcript)
                        },
                        modifier = Modifier.testTag("task_detail_ai_audit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = "Audit task logs with Gemini 4 Carbon",
                            tint = telemetry.thinking
                        )
                    }
                    IconButton(
                        onClick = {
                            val raw = allLogs.joinToString("\n") {
                                "[${formatClockTimestamp(it.createdAt)}] ${it.levelEnum.label}: ${it.message}"
                            }
                            clipboard.setText(AnnotatedString(raw.ifBlank { task.objective }))
                            onCopiedNotice("Telemetry logs copied to clipboard.")
                        },
                        modifier = Modifier.testTag("task_detail_copy_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy telemetry logs"
                        )
                    }
                }
            }
        )

        if (task == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                InlineTelemetryError("Mission no longer exists in Firestore.")
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Control Strip & Progress Card
            item {
                val status = task.statusEnum
                TelemetryCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TaskStatusPill(status = status)
                        Text(
                            text = "${task.activeToolEnum.label.uppercase()} · ${task.progressPercent.coerceIn(0, 100)}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = task.currentPhase,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = task.objective,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { task.clampedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )

                    if (!status.isTerminal) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (status == TaskStatus.PAUSED) {
                                Button(
                                    onClick = { onResume(task) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("detail_resume_button")
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_resume))
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onPause(task) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("detail_pause_button")
                                ) {
                                    Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_pause))
                                }
                            }
                            OutlinedButton(
                                onClick = { showStopConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("detail_stop_button")
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_stop))
                            }
                        }
                    }
                }
            }

            // Toggle Terminal / Timeline + Level Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = viewMode == TelemetryViewMode.TERMINAL,
                            onClick = { viewMode = TelemetryViewMode.TERMINAL },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = { Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text("Terminal Stream")
                        }
                        SegmentedButton(
                            selected = viewMode == TelemetryViewMode.TIMELINE,
                            onClick = { viewMode = TelemetryViewMode.TIMELINE },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = { Icon(Icons.Outlined.Timeline, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text("Step Timeline")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedLevelFilter == null,
                            onClick = { selectedLevelFilter = null },
                            label = { Text("ALL (${allLogs.size})") }
                        )
                        listOf(LogLevel.STEP, LogLevel.COMMAND, LogLevel.WARN, LogLevel.ERROR).forEach { lvl ->
                            FilterChip(
                                selected = selectedLevelFilter == lvl,
                                onClick = {
                                    selectedLevelFilter = if (selectedLevelFilter == lvl) null else lvl
                                },
                                label = { Text(lvl.label) }
                            )
                        }
                    }
                }
            }

            when (logsState) {
                is DataUiState.Loading -> item {
                    TelemetryLoadingBlock("Subscribing to live telemetry stream…")
                }
                is DataUiState.Error -> item {
                    InlineTelemetryError(logsState.message)
                }
                is DataUiState.Success -> {
                    if (filteredLogs.isEmpty()) {
                        item {
                            EmptyMissionState(
                                icon = Icons.Outlined.Terminal,
                                title = stringResource(R.string.empty_logs_title),
                                body = stringResource(R.string.empty_logs_body)
                            )
                        }
                    } else if (viewMode == TelemetryViewMode.TERMINAL) {
                        items(filteredLogs, key = { it.id }) { entry ->
                            TerminalLogRow(entry = entry)
                        }
                    } else {
                        items(filteredLogs, key = { it.id }) { entry ->
                            TimelineMilestoneRow(entry = entry)
                        }
                    }
                }
            }
        }

        // Persistent Steering Prompt Composer
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, telemetry.hairlineBorder),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickChips.forEach { preset ->
                        AssistChip(
                            onClick = { steeringInput = preset },
                            label = { Text(preset, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = steeringInput,
                        onValueChange = { steeringInput = it },
                        placeholder = { Text(stringResource(R.string.steering_prompt_hint)) },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("steering_prompt_input")
                    )
                    Button(
                        onClick = {
                            val prompt = steeringInput.trim()
                            if (prompt.isNotEmpty()) {
                                onSendSteeringPrompt(task, prompt) {
                                    steeringInput = ""
                                }
                            }
                        },
                        enabled = !isActionInFlight && steeringInput.isNotBlank() && !task.statusEnum.isTerminal,
                        contentPadding = PaddingValues(14.dp),
                        modifier = Modifier.testTag("send_steering_prompt_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.action_send),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showStopConfirm) {
        val currentTask = task
        if (currentTask != null) {
            AlertDialog(
                onDismissRequest = { showStopConfirm = false },
                title = { Text(stringResource(R.string.stop_task_dialog_title)) },
                text = { Text(stringResource(R.string.stop_task_dialog_body)) },
                confirmButton = {
                    Button(
                        onClick = {
                            onStop(currentTask)
                            showStopConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(stringResource(R.string.action_confirm_stop))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStopConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun TerminalLogRow(entry: LogEntry) {
    val telemetry = LocalTelemetryColors.current
    val levelColor = when (entry.levelEnum) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> telemetry.warning
        LogLevel.COMMAND -> MaterialTheme.colorScheme.primary
        LogLevel.STEP -> telemetry.online
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, telemetry.hairlineBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = formatClockTimestamp(entry.createdAt),
            style = MonoCodeStyle,
            color = telemetry.inkMuted
        )
        Text(
            text = entry.levelEnum.label,
            style = MonoCodeStyle,
            color = levelColor
        )
        Text(
            text = entry.message,
            style = MonoCodeStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TimelineMilestoneRow(entry: LogEntry) {
    val telemetry = LocalTelemetryColors.current
    val dotColor = when (entry.levelEnum) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.COMMAND -> MaterialTheme.colorScheme.primary
        else -> telemetry.online
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(34.dp)
                    .background(telemetry.hairlineBorder)
            )
        }
        TelemetryCard(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${entry.sourceEnum.label} · ${entry.levelEnum.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = dotColor
                )
                Text(
                    text = formatClockTimestamp(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = telemetry.inkMuted
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
