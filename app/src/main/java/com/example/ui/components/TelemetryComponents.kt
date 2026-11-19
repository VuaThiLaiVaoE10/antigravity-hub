package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.ApprovalStatus
import com.example.data.model.RiskLevel
import com.example.data.model.TaskStatus
import com.example.data.model.WorkspaceStatus
import com.example.ui.theme.LocalTelemetryColors
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Modifier.missionAtmosphere(accentColor: Color, gridColor: Color): Modifier = drawBehind {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.14f),
                Color.Transparent
            ),
            center = Offset(size.width * 0.82f, 0f),
            radius = size.width.coerceAtLeast(size.height) * 0.85f
        )
    )
    val step = 36.dp.toPx()
    var x = step
    while (x < size.width) {
        drawLine(
            color = gridColor.copy(alpha = 0.14f),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(
            color = gridColor.copy(alpha = 0.12f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += step
    }
}

@Composable
fun TelemetryCard(
    modifier: Modifier = Modifier,
    highlightBorder: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val telemetry = LocalTelemetryColors.current
    val border = BorderStroke(1.dp, highlightBorder ?: telemetry.hairlineBorder)
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            colors = colors,
            border = border
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    } else {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            colors = colors,
            border = border
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun PulsingDot(
    color: Color,
    isPulsing: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha = if (isPulsing) {
        val transition = rememberInfiniteTransition(label = "pulse_dot")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        animated
    } else {
        1f
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun StatusPill(
    label: String,
    accentColor: Color,
    containerColor: Color,
    pulsing: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PulsingDot(color = accentColor, isPulsing = pulsing)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor
            )
        }
    }
}

@Composable
fun TaskStatusPill(status: TaskStatus, modifier: Modifier = Modifier) {
    val telemetry = LocalTelemetryColors.current
    val (accent, container, pulsing) = when (status) {
        TaskStatus.RUNNING -> Triple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer,
            true
        )
        TaskStatus.PAUSED -> Triple(
            telemetry.warning,
            telemetry.warningContainer,
            false
        )
        TaskStatus.AWAITING_APPROVAL -> Triple(
            telemetry.warning,
            telemetry.warningContainer,
            true
        )
        TaskStatus.COMPLETED -> Triple(
            telemetry.online,
            telemetry.onlineContainer,
            false
        )
        TaskStatus.FAILED, TaskStatus.STOPPED -> Triple(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer,
            false
        )
    }
    StatusPill(
        label = status.label,
        accentColor = accent,
        containerColor = container,
        pulsing = pulsing,
        modifier = modifier
    )
}

@Composable
fun WorkspaceStatusPill(status: WorkspaceStatus, modifier: Modifier = Modifier) {
    val telemetry = LocalTelemetryColors.current
    val (accent, container, pulsing) = when (status) {
        WorkspaceStatus.ONLINE -> Triple(telemetry.online, telemetry.onlineContainer, true)
        WorkspaceStatus.IDLE -> Triple(telemetry.warning, telemetry.warningContainer, false)
        WorkspaceStatus.OFFLINE -> Triple(
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            false
        )
    }
    StatusPill(
        label = status.label,
        accentColor = accent,
        containerColor = container,
        pulsing = pulsing,
        modifier = modifier
    )
}

@Composable
fun RiskLevelBadge(riskLevel: RiskLevel, modifier: Modifier = Modifier) {
    val telemetry = LocalTelemetryColors.current
    val (accent, container) = when (riskLevel) {
        RiskLevel.LOW -> telemetry.online to telemetry.onlineContainer
        RiskLevel.MEDIUM -> telemetry.warning to telemetry.warningContainer
        RiskLevel.HIGH -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    }
    StatusPill(
        label = riskLevel.label,
        accentColor = accent,
        containerColor = container,
        pulsing = riskLevel == RiskLevel.HIGH,
        modifier = modifier
    )
}

@Composable
fun ApprovalStatusBadge(status: ApprovalStatus, modifier: Modifier = Modifier) {
    val telemetry = LocalTelemetryColors.current
    val (accent, container) = when (status) {
        ApprovalStatus.PENDING -> telemetry.warning to telemetry.warningContainer
        ApprovalStatus.APPROVED -> telemetry.online to telemetry.onlineContainer
        ApprovalStatus.REJECTED -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    }
    StatusPill(
        label = status.label,
        accentColor = accent,
        containerColor = container,
        pulsing = status == ApprovalStatus.PENDING,
        modifier = modifier
    )
}

@Composable
fun EmptyMissionState(
    icon: ImageVector,
    title: String,
    body: String,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    primaryActionTag: String = "empty_primary_action",
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val telemetry = LocalTelemetryColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, telemetry.hairlineBorder, MaterialTheme.shapes.large)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (primaryActionLabel != null && onPrimaryAction != null) {
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier.testTag(primaryActionTag)
                ) {
                    Text(primaryActionLabel)
                }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    OutlinedButton(onClick = onSecondaryAction) {
                        Text(secondaryActionLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun InlineTelemetryError(
    message: String,
    modifier: Modifier = Modifier
) {
    TelemetryCard(
        modifier = modifier.fillMaxWidth(),
        highlightBorder = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun TelemetryLoadingBlock(
    label: String = "Syncing Antigravity relay…",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun formatRelativeTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return "just now"
    val nowMs = System.currentTimeMillis()
    val diffSec = ((nowMs - timestamp.toDate().time) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 10L -> "just now"
        diffSec < 60L -> "${diffSec}s ago"
        diffSec < 3600L -> "${diffSec / 60L}m ago"
        diffSec < 86400L -> "${diffSec / 3600L}h ago"
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(timestamp.toDate())
    }
}

fun formatClockTimestamp(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)
}
