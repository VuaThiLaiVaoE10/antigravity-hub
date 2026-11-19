package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AgentTask
import com.example.data.model.ApprovalRequest
import com.example.data.model.ApprovalStatus
import com.example.ui.components.InlineTelemetryError
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryCard
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.DeepReviewUiState
import com.example.ui.viewmodel.ReviewDraft

@Composable
fun AiReviewScreen(
    reviewUiState: DeepReviewUiState,
    draft: ReviewDraft,
    approvalsState: DataUiState<List<ApprovalRequest>>,
    tasksState: DataUiState<List<AgentTask>>,
    onUpdateTitle: (String) -> Unit,
    onUpdateCategory: (String) -> Unit,
    onUpdatePayload: (String) -> Unit,
    onPrefillFromApproval: (ApprovalRequest) -> Unit,
    onRunAudit: () -> Unit,
    onResetAudit: () -> Unit,
    onDispatchSuggestedPrompt: (AgentTask, String) -> Unit,
    onCopiedNotice: (String) -> Unit
) {
    val telemetry = LocalTelemetryColors.current
    val clipboard = LocalClipboardManager.current
    val pendingApprovals = remember(approvalsState) {
        (approvalsState as? DataUiState.Success)?.data
            ?.filter { it.statusEnum == ApprovalStatus.PENDING }
            .orEmpty()
    }
    val activeTasks = remember(tasksState) {
        (tasksState as? DataUiState.Success)?.data
            ?.filter { !it.statusEnum.isTerminal }
            .orEmpty()
    }
    var selectedDispatchTaskId by remember(draft.targetTaskId, activeTasks) {
        mutableStateOf(
            draft.targetTaskId?.takeIf { id -> activeTasks.any { it.id == id } }
                ?: activeTasks.firstOrNull()?.id
        )
    }
    val categories = remember { listOf("CODE_DIFF", "PLAN", "TERMINAL_COMMAND", "ERROR_LOG") }
    val isRunning = reviewUiState is DeepReviewUiState.Thinking || reviewUiState is DeepReviewUiState.Streaming

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(
                    label = stringResource(R.string.ai_thinking_badge),
                    accentColor = telemetry.thinking,
                    containerColor = telemetry.thinkingContainer,
                    pulsing = isRunning
                )
                Text(
                    text = "Deep-Thinking Analyst",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Audit risky diffs, implementation plans, or stack traces with Gemini 4 Carbon high-thinking before approving or steering your agent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Quick-load from Pending Approval Gates
        if (pendingApprovals.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "LOAD FROM PENDING APPROVAL GATES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pendingApprovals.forEach { approval ->
                            FilterChip(
                                selected = draft.title == approval.title && draft.payload == approval.payload,
                                onClick = { onPrefillFromApproval(approval) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(approval.title.take(28)) }
                            )
                        }
                    }
                }
            }
        }

        // Input Card
        item {
            TelemetryCard(
                modifier = Modifier.fillMaxWidth(),
                highlightBorder = telemetry.thinking.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = draft.category == cat,
                            onClick = { onUpdateCategory(cat) },
                            label = { Text(cat.replace('_', ' ')) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = draft.title,
                    onValueChange = onUpdateTitle,
                    label = { Text("Artifact or Error Title") },
                    placeholder = { Text("e.g. Migration diff or failing Gradle stack trace") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_review_title_input")
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = draft.payload,
                    onValueChange = onUpdatePayload,
                    label = { Text("Diff, Plan, Shell Command, or Terminal Log *") },
                    placeholder = { Text("Paste code diff, implementation plan, or terminal output…") },
                    minLines = 5,
                    maxLines = 10,
                    textStyle = MonoCodeStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_review_payload_input")
                )

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = onRunAudit,
                    enabled = !isRunning && draft.payload.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = telemetry.thinking,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("run_deep_review_button")
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.ai_analyzing_status))
                    } else {
                        Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ai_analyze_button))
                    }
                }
            }
        }

        // Result Card
        item {
            AnimatedVisibility(visible = reviewUiState !is DeepReviewUiState.Idle) {
                when (reviewUiState) {
                    is DeepReviewUiState.Idle -> Unit
                    is DeepReviewUiState.Thinking -> {
                        TelemetryCard(
                            modifier = Modifier.fillMaxWidth(),
                            highlightBorder = telemetry.thinking.copy(alpha = 0.65f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = telemetry.thinking
                                )
                                Column {
                                    Text(
                                        text = "High-Thinking Reasoning Active",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = telemetry.thinking
                                    )
                                    Text(
                                        text = "Evaluating ${reviewUiState.subjectTitle} with ThinkingLevel.HIGH…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    is DeepReviewUiState.Streaming -> {
                        AuditOutputCard(
                            title = reviewUiState.subjectTitle,
                            outputText = reviewUiState.text,
                            isStreaming = true,
                            suggestedPrompt = null,
                            activeTasks = emptyList(),
                            selectedTaskId = null,
                            onSelectTask = {},
                            onCopy = {
                                clipboard.setText(AnnotatedString(reviewUiState.text))
                                onCopiedNotice("Audit copied to clipboard.")
                            },
                            onReset = onResetAudit,
                            onDispatchPrompt = { _, _ -> }
                        )
                    }
                    is DeepReviewUiState.Success -> {
                        AuditOutputCard(
                            title = reviewUiState.subjectTitle,
                            outputText = reviewUiState.text,
                            isStreaming = false,
                            suggestedPrompt = reviewUiState.suggestedSteerPrompt,
                            activeTasks = activeTasks,
                            selectedTaskId = selectedDispatchTaskId,
                            onSelectTask = { selectedDispatchTaskId = it },
                            onCopy = {
                                clipboard.setText(AnnotatedString(reviewUiState.text))
                                onCopiedNotice("Audit copied to clipboard.")
                            },
                            onReset = onResetAudit,
                            onDispatchPrompt = onDispatchSuggestedPrompt
                        )
                    }
                    is DeepReviewUiState.Error -> {
                        InlineTelemetryError(message = reviewUiState.message)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditOutputCard(
    title: String,
    outputText: String,
    isStreaming: Boolean,
    suggestedPrompt: String?,
    activeTasks: List<AgentTask>,
    selectedTaskId: String?,
    onSelectTask: (String) -> Unit,
    onCopy: () -> Unit,
    onReset: () -> Unit,
    onDispatchPrompt: (AgentTask, String) -> Unit
) {
    val telemetry = LocalTelemetryColors.current
    TelemetryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_review_output_card"),
        highlightBorder = telemetry.thinking.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isStreaming) "STREAMING AUDIT…" else "DEEP-THINKING AUDIT COMPLETE",
                    style = MaterialTheme.typography.labelSmall,
                    color = telemetry.thinking
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy audit")
                }
                IconButton(onClick = onReset) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Clear audit")
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = outputText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (!suggestedPrompt.isNullOrBlank() && activeTasks.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "RECOMMENDED STEERING PROMPT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = suggestedPrompt,
                    style = MonoCodeStyle,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (activeTasks.size > 1) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeTasks.forEach { task ->
                            FilterChip(
                                selected = selectedTaskId == task.id,
                                onClick = { onSelectTask(task.id) },
                                label = { Text(task.title.take(22)) }
                            )
                        }
                    }
                }
                val targetTask = activeTasks.firstOrNull { it.id == selectedTaskId } ?: activeTasks.first()
                Button(
                    onClick = { onDispatchPrompt(targetTask, suggestedPrompt) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dispatch_ai_steer_prompt_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send Prompt to ${targetTask.title.take(20)}")
                }
            }
        }
    }
}
