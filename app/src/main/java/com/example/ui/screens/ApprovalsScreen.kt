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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AgentTask
import com.example.data.model.ApprovalRequest
import com.example.data.model.ApprovalStatus
import com.example.data.model.ApprovalType
import com.example.data.model.RiskLevel
import com.example.ui.components.ApprovalStatusBadge
import com.example.ui.components.EmptyMissionState
import com.example.ui.components.InlineTelemetryError
import com.example.ui.components.RiskLevelBadge
import com.example.ui.components.TelemetryCard
import com.example.ui.components.TelemetryLoadingBlock
import com.example.ui.components.formatRelativeTimestamp
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.viewmodel.DataUiState

@Composable
fun ApprovalsScreen(
    approvalsState: DataUiState<List<ApprovalRequest>>,
    tasksState: DataUiState<List<AgentTask>>,
    isActionInFlight: Boolean,
    onApprove: (ApprovalRequest) -> Unit,
    onReject: (ApprovalRequest, String) -> Unit,
    onSendToAiReview: (ApprovalRequest) -> Unit,
    onCreateGate: (AgentTask, ApprovalType, String, String, String, RiskLevel, () -> Unit) -> Unit
) {
    var showPendingOnly by remember { mutableStateOf(true) }
    var rejectTarget by remember { mutableStateOf<ApprovalRequest?>(null) }
    var showCreateGateDialog by remember { mutableStateOf(false) }

    val tasks = (tasksState as? DataUiState.Success)?.data.orEmpty()
    val approvals = (approvalsState as? DataUiState.Success)?.data.orEmpty()
    val visibleApprovals = remember(approvals, showPendingOnly) {
        if (showPendingOnly) approvals.filter { it.statusEnum == ApprovalStatus.PENDING }
        else approvals
    }
    val pendingCount = remember(approvals) {
        approvals.count { it.statusEnum == ApprovalStatus.PENDING }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
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
                        text = "HUMAN-IN-THE-LOOP",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalTelemetryColors.current.warning
                    )
                    Text(
                        text = "Approval Queue",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (tasks.isNotEmpty()) {
                    TextButton(
                        onClick = { showCreateGateDialog = true },
                        modifier = Modifier.testTag("create_approval_gate_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Log Gate")
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = showPendingOnly,
                    onClick = { showPendingOnly = true },
                    label = { Text("Pending ($pendingCount)") },
                    modifier = Modifier.testTag("approvals_filter_pending")
                )
                FilterChip(
                    selected = !showPendingOnly,
                    onClick = { showPendingOnly = false },
                    label = { Text("All History (${approvals.size})") },
                    modifier = Modifier.testTag("approvals_filter_all")
                )
            }
        }

        when (approvalsState) {
            is DataUiState.Loading -> item {
                TelemetryLoadingBlock("Syncing approval gates…")
            }
            is DataUiState.Error -> item {
                InlineTelemetryError(approvalsState.message)
            }
            is DataUiState.Success -> {
                if (visibleApprovals.isEmpty()) {
                    item {
                        EmptyMissionState(
                            icon = Icons.Outlined.VerifiedUser,
                            title = stringResource(R.string.empty_approvals_title),
                            body = stringResource(R.string.empty_approvals_body),
                            primaryActionLabel = if (tasks.isNotEmpty()) "Submit Gate for Review" else null,
                            onPrimaryAction = if (tasks.isNotEmpty()) ({ showCreateGateDialog = true }) else null,
                            primaryActionTag = "empty_create_gate_button"
                        )
                    }
                } else {
                    items(visibleApprovals, key = { it.id }) { approval ->
                        val taskTitle = tasks.firstOrNull { it.id == approval.taskId }?.title
                            ?: "Task ${approval.taskId.take(6)}"
                        ApprovalGateCard(
                            approval = approval,
                            taskTitle = taskTitle,
                            isBusy = isActionInFlight,
                            onApprove = { onApprove(approval) },
                            onRequestReject = { rejectTarget = approval },
                            onSendToAiReview = { onSendToAiReview(approval) }
                        )
                    }
                }
            }
        }
    }

    rejectTarget?.let { target ->
        var note by remember(target.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text(stringResource(R.string.reject_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = target.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.reject_dialog_hint)) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reject_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReject(target, note)
                        rejectTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_reject_button")
                ) {
                    Text(stringResource(R.string.action_reject))
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showCreateGateDialog && tasks.isNotEmpty()) {
        CreateApprovalGateDialog(
            tasks = tasks,
            isSubmitting = isActionInFlight,
            onDismiss = { showCreateGateDialog = false },
            onSubmit = { task, type, title, desc, payload, risk ->
                onCreateGate(task, type, title, desc, payload, risk) {
                    showCreateGateDialog = false
                }
            }
        )
    }
}

@Composable
private fun ApprovalGateCard(
    approval: ApprovalRequest,
    taskTitle: String,
    isBusy: Boolean,
    onApprove: () -> Unit,
    onRequestReject: () -> Unit,
    onSendToAiReview: () -> Unit
) {
    val telemetry = LocalTelemetryColors.current
    var expanded by remember(approval.id) { mutableStateOf(approval.statusEnum == ApprovalStatus.PENDING) }
    val typeIcon = when (approval.typeEnum) {
        ApprovalType.PLAN -> Icons.Outlined.Description
        ApprovalType.CODE_DIFF -> Icons.Outlined.Code
        ApprovalType.TERMINAL_COMMAND -> Icons.Outlined.Terminal
    }

    TelemetryCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("approval_card_${approval.id}"),
        highlightBorder = if (approval.statusEnum == ApprovalStatus.PENDING) {
            telemetry.warning.copy(alpha = 0.45f)
        } else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ApprovalStatusBadge(status = approval.statusEnum)
                RiskLevelBadge(riskLevel = approval.riskLevelEnum)
            }
            Text(
                text = formatRelativeTimestamp(approval.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = typeIcon,
                contentDescription = approval.typeEnum.label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "${approval.typeEnum.label} · $taskTitle",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = approval.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (approval.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = approval.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(if (expanded) "Hide Payload" else "Inspect Payload")
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, telemetry.hairlineBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = approval.payload,
                    style = MonoCodeStyle,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        if (!approval.decisionNote.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Decision note: ${approval.decisionNote}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onSendToAiReview,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = telemetry.thinking),
                border = BorderStroke(1.dp, telemetry.thinking.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.testTag("ai_review_gate_${approval.id}")
            ) {
                Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("AI Audit")
            }

            if (approval.statusEnum == ApprovalStatus.PENDING) {
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onRequestReject,
                    enabled = !isBusy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("reject_gate_${approval.id}")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_reject))
                }
                Button(
                    onClick = onApprove,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = telemetry.online,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("approve_gate_${approval.id}")
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_approve))
                }
            }
        }
    }
}

@Composable
private fun CreateApprovalGateDialog(
    tasks: List<AgentTask>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (AgentTask, ApprovalType, String, String, String, RiskLevel) -> Unit
) {
    var selectedTask by remember { mutableStateOf(tasks.first()) }
    var type by remember { mutableStateOf(ApprovalType.CODE_DIFF) }
    var riskLevel by remember { mutableStateOf(RiskLevel.MEDIUM) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit Approval Gate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tasks.take(6).forEach { task ->
                        FilterChip(
                            selected = selectedTask.id == task.id,
                            onClick = { selectedTask = task },
                            label = { Text(task.title.take(20)) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ApprovalType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RiskLevel.entries.forEach { r ->
                        FilterChip(
                            selected = riskLevel == r,
                            onClick = { riskLevel = r },
                            label = { Text(r.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Gate Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Context") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text("Plan / Diff / Shell Command *") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedTask, type, title, description, payload, riskLevel) },
                enabled = !isSubmitting && title.isNotBlank() && payload.isNotBlank()
            ) {
                Text("Queue Gate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
