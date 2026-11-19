package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ActiveTool
import com.example.data.model.AgentTask
import com.example.data.model.ApprovalRequest
import com.example.data.model.ApprovalStatus
import com.example.data.model.ApprovalType
import com.example.data.model.CommandAction
import com.example.data.model.ControlCommand
import com.example.data.model.DeliveryStatus
import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import com.example.data.model.LogSource
import com.example.data.model.RiskLevel
import com.example.data.model.TaskStatus
import com.example.data.model.Workspace
import com.example.data.model.WorkspaceEnvironment
import com.example.data.model.WorkspaceStatus
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AntigravityRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAuthorizedUid(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in account changed; refusing cross-user Firestore operation." }
        return uid
    }

    private fun userRoot(userId: String) = db.collection("users").document(userId)

    fun observeWorkspaces(userId: String): Flow<List<Workspace>> {
        val path = "users/$userId/workspaces"
        return userRoot(userId).collection("workspaces")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Workspace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logStreamFailureIfSignedIn(userId, path, error)
                throw error
            }
    }

    fun observeTasks(userId: String): Flow<List<AgentTask>> {
        val path = "users/$userId/tasks"
        return userRoot(userId).collection("tasks")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AgentTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logStreamFailureIfSignedIn(userId, path, error)
                throw error
            }
    }

    fun observeApprovals(userId: String): Flow<List<ApprovalRequest>> {
        val path = "users/$userId/approvals"
        return userRoot(userId).collection("approvals")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ApprovalRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logStreamFailureIfSignedIn(userId, path, error)
                throw error
            }
    }

    fun observeCommands(userId: String): Flow<List<ControlCommand>> {
        val path = "users/$userId/commands"
        return userRoot(userId).collection("commands")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ControlCommand::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logStreamFailureIfSignedIn(userId, path, error)
                throw error
            }
    }

    fun observeTaskLogs(userId: String, taskId: String): Flow<List<LogEntry>> {
        val path = "users/$userId/logs"
        return userRoot(userId).collection("logs")
            .whereEqualTo("taskId", taskId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(LogEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logStreamFailureIfSignedIn(userId, path, error)
                throw error
            }
    }

    private fun logStreamFailureIfSignedIn(userId: String, path: String, error: Throwable) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
    }

    suspend fun registerWorkspace(
        userId: String,
        name: String,
        hostName: String,
        environment: WorkspaceEnvironment,
        repository: String,
        branch: String
    ): Result<String> {
        var docPath = "users/$userId/workspaces"
        return try {
            val uid = requireAuthorizedUid(userId)
            val cleanName = name.trim().take(80)
            val cleanHost = hostName.trim().take(100)
            val cleanRepo = repository.trim().take(160)
            val cleanBranch = branch.trim().ifBlank { "main" }.take(80)
            require(cleanName.isNotEmpty()) { "Workspace name is required." }
            require(cleanHost.isNotEmpty()) { "Host or sandbox name is required." }

            val docRef = userRoot(uid).collection("workspaces").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "hostName" to cleanHost,
                "environment" to environment.name,
                "repository" to cleanRepo,
                "branch" to cleanBranch,
                "status" to WorkspaceStatus.ONLINE.name,
                "lastHeartbeatAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun pingWorkspaceHeartbeat(
        userId: String,
        workspaceId: String,
        status: WorkspaceStatus = WorkspaceStatus.ONLINE
    ): Result<Unit> {
        val docPath = "users/$userId/workspaces/$workspaceId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(workspaceId.isNotBlank()) { "Workspace ID must not be blank." }
            userRoot(uid).collection("workspaces").document(workspaceId)
                .update(
                    mapOf(
                        "status" to status.name,
                        "lastHeartbeatAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteWorkspace(userId: String, workspaceId: String): Result<Unit> {
        val docPath = "users/$userId/workspaces/$workspaceId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(workspaceId.isNotBlank()) { "Workspace ID must not be blank." }
            userRoot(uid).collection("workspaces").document(workspaceId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun dispatchTask(
        userId: String,
        workspaceId: String,
        title: String,
        objective: String,
        activeTool: ActiveTool = ActiveTool.PLANNER
    ): Result<String> {
        var docPath = "users/$userId/tasks"
        return try {
            val uid = requireAuthorizedUid(userId)
            val cleanTitle = title.trim().take(140)
            val cleanObjective = objective.trim().take(2000)
            require(workspaceId.isNotBlank()) { "Select a workspace first." }
            require(cleanTitle.isNotEmpty()) { "Task title is required." }
            require(cleanObjective.isNotEmpty()) { "Task objective is required." }

            val taskRef = userRoot(uid).collection("tasks").document()
            val commandRef = userRoot(uid).collection("commands").document()
            val logRef = userRoot(uid).collection("logs").document()
            docPath = taskRef.path

            val batch = db.batch()
            batch.set(
                taskRef,
                mapOf(
                    "userId" to uid,
                    "workspaceId" to workspaceId,
                    "title" to cleanTitle,
                    "objective" to cleanObjective,
                    "status" to TaskStatus.RUNNING.name,
                    "currentPhase" to "Queued for Antigravity relay",
                    "activeTool" to activeTool.name,
                    "progressPercent" to 5L,
                    "summary" to "Dispatched remotely from Antigravity Hub.",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                commandRef,
                mapOf(
                    "userId" to uid,
                    "workspaceId" to workspaceId,
                    "taskId" to taskRef.id,
                    "action" to CommandAction.START_TASK.name,
                    "payload" to cleanObjective,
                    "deliveryStatus" to DeliveryStatus.QUEUED.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                logRef,
                mapOf(
                    "userId" to uid,
                    "taskId" to taskRef.id,
                    "level" to LogLevel.COMMAND.name,
                    "source" to LogSource.SYSTEM.name,
                    "message" to "Remote mission dispatched: $cleanTitle",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(taskRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun controlTaskLifecycle(
        userId: String,
        task: AgentTask,
        targetStatus: TaskStatus,
        action: CommandAction,
        phaseLabel: String,
        logMessage: String
    ): Result<Unit> {
        val docPath = "users/$userId/tasks/${task.id}"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(task.id.isNotBlank()) { "Task ID is required." }
            require(!task.statusEnum.isTerminal) { "Task is already in terminal state (${task.status})." }

            val taskRef = userRoot(uid).collection("tasks").document(task.id)
            val commandRef = userRoot(uid).collection("commands").document()
            val logRef = userRoot(uid).collection("logs").document()

            val batch = db.batch()
            batch.update(
                taskRef,
                mapOf(
                    "status" to targetStatus.name,
                    "currentPhase" to phaseLabel.take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                commandRef,
                mapOf(
                    "userId" to uid,
                    "workspaceId" to task.workspaceId,
                    "taskId" to task.id,
                    "action" to action.name,
                    "payload" to phaseLabel.take(4000),
                    "deliveryStatus" to DeliveryStatus.QUEUED.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                logRef,
                mapOf(
                    "userId" to uid,
                    "taskId" to task.id,
                    "level" to LogLevel.COMMAND.name,
                    "source" to LogSource.SYSTEM.name,
                    "message" to logMessage.take(4000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendSteeringPrompt(
        userId: String,
        task: AgentTask,
        prompt: String
    ): Result<Unit> {
        val docPath = "users/$userId/commands"
        return try {
            val uid = requireAuthorizedUid(userId)
            val cleanPrompt = prompt.trim().take(4000)
            require(task.id.isNotBlank()) { "Task ID is required." }
            require(cleanPrompt.isNotEmpty()) { "Steering prompt cannot be empty." }

            val commandRef = userRoot(uid).collection("commands").document()
            val logRef = userRoot(uid).collection("logs").document()

            val batch = db.batch()
            batch.set(
                commandRef,
                mapOf(
                    "userId" to uid,
                    "workspaceId" to task.workspaceId,
                    "taskId" to task.id,
                    "action" to CommandAction.STEER_PROMPT.name,
                    "payload" to cleanPrompt,
                    "deliveryStatus" to DeliveryStatus.QUEUED.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                logRef,
                mapOf(
                    "userId" to uid,
                    "taskId" to task.id,
                    "level" to LogLevel.COMMAND.name,
                    "source" to LogSource.SYSTEM.name,
                    "message" to "Steer -> $cleanPrompt",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitApprovalDecision(
        userId: String,
        approval: ApprovalRequest,
        approve: Boolean,
        decisionNote: String
    ): Result<Unit> {
        val docPath = "users/$userId/approvals/${approval.id}"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(approval.id.isNotBlank()) { "Approval ID is required." }
            require(approval.statusEnum == ApprovalStatus.PENDING) {
                "Approval gate has already been decided."
            }

            val nextStatus = if (approve) ApprovalStatus.APPROVED else ApprovalStatus.REJECTED
            val action = if (approve) CommandAction.APPROVE_ARTIFACT else CommandAction.REJECT_ARTIFACT
            val cleanNote = decisionNote.trim().take(1000)

            val approvalRef = userRoot(uid).collection("approvals").document(approval.id)
            val commandRef = userRoot(uid).collection("commands").document()
            val logRef = userRoot(uid).collection("logs").document()

            val approvalUpdate = mutableMapOf<String, Any>(
                "status" to nextStatus.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanNote.isNotEmpty()) {
                approvalUpdate["decisionNote"] = cleanNote
            }

            val commandPayload = buildString {
                append("approvalId=${approval.id}; title=${approval.title}")
                if (cleanNote.isNotEmpty()) append("; note=$cleanNote")
            }.take(4000)

            val batch = db.batch()
            batch.update(approvalRef, approvalUpdate)
            batch.set(
                commandRef,
                mapOf(
                    "userId" to uid,
                    "workspaceId" to approval.workspaceId,
                    "taskId" to approval.taskId,
                    "action" to action.name,
                    "payload" to commandPayload,
                    "deliveryStatus" to DeliveryStatus.QUEUED.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                logRef,
                mapOf(
                    "userId" to uid,
                    "taskId" to approval.taskId,
                    "level" to LogLevel.STEP.name,
                    "source" to LogSource.SYSTEM.name,
                    "message" to "${nextStatus.label}: ${approval.title}" +
                        if (cleanNote.isNotEmpty()) " — $cleanNote" else "",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createApprovalGate(
        userId: String,
        task: AgentTask,
        type: ApprovalType,
        title: String,
        description: String,
        payload: String,
        riskLevel: RiskLevel
    ): Result<String> {
        var docPath = "users/$userId/approvals"
        return try {
            val uid = requireAuthorizedUid(userId)
            val cleanTitle = title.trim().take(140)
            val cleanDescription = description.trim().take(1000)
            val cleanPayload = payload.trim().take(20000)
            require(cleanTitle.isNotEmpty()) { "Gate title is required." }
            require(cleanPayload.isNotEmpty()) { "Gate payload is required." }

            val docRef = userRoot(uid).collection("approvals").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "workspaceId" to task.workspaceId,
                    "taskId" to task.id,
                    "type" to type.name,
                    "title" to cleanTitle,
                    "description" to cleanDescription,
                    "payload" to cleanPayload,
                    "riskLevel" to riskLevel.name,
                    "status" to ApprovalStatus.PENDING.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun appendLogEntry(
        userId: String,
        taskId: String,
        level: LogLevel,
        source: LogSource,
        message: String
    ): Result<Unit> {
        val docPath = "users/$userId/logs"
        return try {
            val uid = requireAuthorizedUid(userId)
            val cleanMessage = message.trim().take(4000)
            require(taskId.isNotBlank()) { "Task ID is required." }
            require(cleanMessage.isNotEmpty()) { "Log message cannot be empty." }

            userRoot(uid).collection("logs").document().set(
                mapOf(
                    "userId" to uid,
                    "taskId" to taskId,
                    "level" to level.name,
                    "source" to source.name,
                    "message" to cleanMessage,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }
}
