package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class WorkspaceEnvironment(val label: String) {
    LOCAL_WORKSTATION("Local Workstation"),
    CLOUD_SANDBOX("Cloud Sandbox");

    companion object {
        fun fromRaw(raw: String): WorkspaceEnvironment =
            entries.firstOrNull { it.name == raw } ?: LOCAL_WORKSTATION
    }
}

enum class WorkspaceStatus(val label: String) {
    ONLINE("ONLINE"),
    IDLE("IDLE"),
    OFFLINE("OFFLINE");

    companion object {
        fun fromRaw(raw: String): WorkspaceStatus =
            entries.firstOrNull { it.name == raw } ?: OFFLINE
    }
}

enum class TaskStatus(val label: String) {
    RUNNING("RUNNING"),
    PAUSED("PAUSED"),
    AWAITING_APPROVAL("AWAITING GATE"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED"),
    STOPPED("STOPPED");

    val isTerminal: Boolean
        get() = this == COMPLETED || this == STOPPED

    companion object {
        fun fromRaw(raw: String): TaskStatus =
            entries.firstOrNull { it.name == raw } ?: RUNNING
    }
}

enum class ActiveTool(val label: String) {
    EDITOR("Editor"),
    TERMINAL("Terminal"),
    BROWSER("Browser"),
    PLANNER("Planner");

    companion object {
        fun fromRaw(raw: String): ActiveTool =
            entries.firstOrNull { it.name == raw } ?: PLANNER
    }
}

enum class ApprovalType(val label: String) {
    PLAN("Implementation Plan"),
    CODE_DIFF("Code Diff"),
    TERMINAL_COMMAND("Terminal Command");

    companion object {
        fun fromRaw(raw: String): ApprovalType =
            entries.firstOrNull { it.name == raw } ?: PLAN
    }
}

enum class RiskLevel(val label: String) {
    LOW("LOW RISK"),
    MEDIUM("MED RISK"),
    HIGH("HIGH RISK");

    companion object {
        fun fromRaw(raw: String): RiskLevel =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class ApprovalStatus(val label: String) {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED");

    companion object {
        fun fromRaw(raw: String): ApprovalStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class CommandAction(val label: String) {
    PAUSE("Pause"),
    RESUME("Resume"),
    STOP("Stop"),
    STEER_PROMPT("Steer Prompt"),
    APPROVE_ARTIFACT("Approve"),
    REJECT_ARTIFACT("Reject"),
    START_TASK("Start Task");

    companion object {
        fun fromRaw(raw: String): CommandAction =
            entries.firstOrNull { it.name == raw } ?: STEER_PROMPT
    }
}

enum class DeliveryStatus(val label: String) {
    QUEUED("QUEUED"),
    ACKNOWLEDGED("ACK"),
    EXECUTED("EXECUTED");

    companion object {
        fun fromRaw(raw: String): DeliveryStatus =
            entries.firstOrNull { it.name == raw } ?: QUEUED
    }
}

enum class LogLevel(val label: String) {
    INFO("INFO"),
    STEP("STEP"),
    WARN("WARN"),
    ERROR("ERROR"),
    COMMAND("CMD");

    companion object {
        fun fromRaw(raw: String): LogLevel =
            entries.firstOrNull { it.name == raw } ?: INFO
    }
}

enum class LogSource(val label: String) {
    AGENT("AGENT"),
    TERMINAL("TERM"),
    BROWSER("WEB"),
    SYSTEM("SYS");

    companion object {
        fun fromRaw(raw: String): LogSource =
            entries.firstOrNull { it.name == raw } ?: AGENT
    }
}

@Keep
@IgnoreExtraProperties
data class Workspace(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val hostName: String = "",
    val environment: String = WorkspaceEnvironment.LOCAL_WORKSTATION.name,
    val repository: String = "",
    val branch: String = "main",
    val status: String = WorkspaceStatus.ONLINE.name,
    @ServerTimestamp val lastHeartbeatAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val environmentEnum: WorkspaceEnvironment get() = WorkspaceEnvironment.fromRaw(environment)
    val statusEnum: WorkspaceStatus get() = WorkspaceStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class AgentTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workspaceId: String = "",
    val title: String = "",
    val objective: String = "",
    val status: String = TaskStatus.RUNNING.name,
    val currentPhase: String = "Initializing",
    val activeTool: String = ActiveTool.PLANNER.name,
    val progressPercent: Long = 0L,
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val statusEnum: TaskStatus get() = TaskStatus.fromRaw(status)
    val activeToolEnum: ActiveTool get() = ActiveTool.fromRaw(activeTool)
    val clampedProgress: Float get() = (progressPercent.coerceIn(0L, 100L) / 100f)
}

@Keep
@IgnoreExtraProperties
data class ApprovalRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workspaceId: String = "",
    val taskId: String = "",
    val type: String = ApprovalType.PLAN.name,
    val title: String = "",
    val description: String = "",
    val payload: String = "",
    val riskLevel: String = RiskLevel.MEDIUM.name,
    val status: String = ApprovalStatus.PENDING.name,
    val decisionNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val typeEnum: ApprovalType get() = ApprovalType.fromRaw(type)
    val riskLevelEnum: RiskLevel get() = RiskLevel.fromRaw(riskLevel)
    val statusEnum: ApprovalStatus get() = ApprovalStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class ControlCommand(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workspaceId: String = "",
    val taskId: String = "",
    val action: String = CommandAction.STEER_PROMPT.name,
    val payload: String = "",
    val deliveryStatus: String = DeliveryStatus.QUEUED.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val actionEnum: CommandAction get() = CommandAction.fromRaw(action)
    val deliveryStatusEnum: DeliveryStatus get() = DeliveryStatus.fromRaw(deliveryStatus)
}

@Keep
@IgnoreExtraProperties
data class LogEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val taskId: String = "",
    val level: String = LogLevel.INFO.name,
    val source: String = LogSource.AGENT.name,
    val message: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val levelEnum: LogLevel get() = LogLevel.fromRaw(level)
    val sourceEnum: LogSource get() = LogSource.fromRaw(source)
}
