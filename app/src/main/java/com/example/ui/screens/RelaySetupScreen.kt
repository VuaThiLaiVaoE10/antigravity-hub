package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ControlCommand
import com.example.data.model.Workspace
import com.example.data.model.WorkspaceEnvironment
import com.example.data.model.WorkspaceStatus
import com.example.ui.components.InlineTelemetryError
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryCard
import com.example.ui.components.TelemetryLoadingBlock
import com.example.ui.components.WorkspaceStatusPill
import com.example.ui.components.formatRelativeTimestamp
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.viewmodel.DataUiState

@Composable
fun RelaySetupScreen(
    userId: String,
    userEmail: String?,
    workspacesState: DataUiState<List<Workspace>>,
    commandsState: DataUiState<List<ControlCommand>>,
    isActionInFlight: Boolean,
    onRegisterWorkspace: (String, String, WorkspaceEnvironment, String, String, () -> Unit) -> Unit,
    onPingWorkspace: (Workspace, WorkspaceStatus) -> Unit,
    onDeleteWorkspace: (Workspace) -> Unit,
    onSignOut: () -> Unit,
    onCopiedNotice: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val telemetry = LocalTelemetryColors.current
    val databaseId = remember(context) { context.getString(R.string.firestore_database_id) }
    var showPairDialog by remember { mutableStateOf(false) }

    val relayContract = remember(userId, databaseId) {
        """
        databaseId: "$databaseId"
        userRoot:   "users/$userId"
        collections:
          - users/$userId/workspaces/{workspaceId}
          - users/$userId/tasks/{taskId}
          - users/$userId/approvals/{approvalId}
          - users/$userId/commands/{commandId}
          - users/$userId/logs/{logId}
        """.trimIndent()
    }

    val commands = (commandsState as? DataUiState.Success)?.data.orEmpty().take(8)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "CLOUD RELAY & BRIDGE",
                    style = MaterialTheme.typography.labelMedium,
                    color = telemetry.online
                )
                Text(
                    text = "Workspaces & Relay",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Account & Sign Out Card
        item {
            TelemetryCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AUTHENTICATED OPERATOR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = userEmail ?: "Google Account Connected",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "UID: $userId",
                            style = MonoCodeStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_sign_out))
                    }
                }
            }
        }

        // Firestore Relay Contract Card
        item {
            TelemetryCard(
                modifier = Modifier.fillMaxWidth(),
                highlightBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDone,
                            contentDescription = null,
                            tint = telemetry.online,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Desktop Antigravity Relay Contract",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(relayContract))
                            onCopiedNotice("Firestore relay contract copied.")
                        },
                        modifier = Modifier.testTag("copy_relay_contract_button")
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_copy))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Point your Antigravity desktop bridge to these exact Firestore paths authenticated as your Google UID.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, telemetry.hairlineBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = relayContract,
                        style = MonoCodeStyle,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Paired Workspaces Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PAIRED WORKSPACES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { showPairDialog = true },
                    modifier = Modifier.testTag("relay_pair_workspace_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_pair_workspace))
                }
            }
        }

        when (workspacesState) {
            is DataUiState.Loading -> item { TelemetryLoadingBlock("Loading paired hosts…") }
            is DataUiState.Error -> item { InlineTelemetryError(workspacesState.message) }
            is DataUiState.Success -> {
                if (workspacesState.data.isEmpty()) {
                    item {
                        TelemetryCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No workstations paired yet. Tap Pair Workspace above to register a local or cloud Antigravity host.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(workspacesState.data, key = { it.id }) { ws ->
                        TelemetryCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ws.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${ws.hostName} · ${ws.environmentEnum.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (ws.repository.isNotBlank()) {
                                        Text(
                                            text = "${ws.repository} (${ws.branch})",
                                            style = MonoCodeStyle,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                WorkspaceStatusPill(status = ws.statusEnum)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Heartbeat ${formatRelativeTimestamp(ws.lastHeartbeatAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = telemetry.inkMuted
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { onPingWorkspace(ws, WorkspaceStatus.ONLINE) },
                                        modifier = Modifier.testTag("ping_workspace_${ws.id}")
                                    ) {
                                        Icon(Icons.Outlined.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ping")
                                    }
                                    IconButton(
                                        onClick = { onDeleteWorkspace(ws) },
                                        modifier = Modifier.testTag("delete_workspace_${ws.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Remove workspace ${ws.name}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Outbound Relay Command Queue
        if (commands.isNotEmpty()) {
            item {
                Text(
                    text = "RECENT OUTBOUND COMMANDS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(commands, key = { it.id }) { cmd ->
                TelemetryCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cmd.actionEnum.label.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        StatusPill(
                            label = cmd.deliveryStatusEnum.label,
                            accentColor = telemetry.online,
                            containerColor = telemetry.onlineContainer
                        )
                    }
                    if (cmd.payload.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = cmd.payload,
                            style = MonoCodeStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatRelativeTimestamp(cmd.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = telemetry.inkMuted
                    )
                }
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
}
