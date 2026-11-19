package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.repository.AntigravityRepository
import com.example.data.repository.DeepReviewRepository
import com.example.firebase.authStateFlow
import com.example.firebase.signOutGoogle
import com.example.ui.components.missionAtmosphere
import com.example.ui.screens.AiReviewScreen
import com.example.ui.screens.ApprovalsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DeckScreen
import com.example.ui.screens.RelaySetupScreen
import com.example.ui.screens.TaskDetailScreen
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.DeepReviewViewModel
import com.example.ui.viewmodel.MissionControlViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

const val ROUTE_DECK = "deck"
const val ROUTE_APPROVALS = "approvals"
const val ROUTE_AI_REVIEW = "ai_review"
const val ROUTE_RELAY = "relay"
const val ROUTE_TASK_DETAIL = "task_detail/{taskId}"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopLevelDestinations = listOf(
    NavDestination(ROUTE_DECK, R.string.nav_deck, Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_deck"),
    NavDestination(ROUTE_APPROVALS, R.string.nav_approvals, Icons.Filled.VerifiedUser, Icons.Outlined.VerifiedUser, "nav_approvals"),
    NavDestination(ROUTE_AI_REVIEW, R.string.nav_ai_review, Icons.Filled.Psychology, Icons.Outlined.Psychology, "nav_ai_review"),
    NavDestination(ROUTE_RELAY, R.string.nav_relay, Icons.Filled.CloudQueue, Icons.Outlined.CloudQueue, "nav_relay")
)

@Composable
fun AntigravityHubApp(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener drives transition automatically */ })
    } else {
        key(user.uid) {
            AuthenticatedMissionShell(
                currentUserId = user.uid,
                userEmail = user.email
            )
        }
    }
}

@Composable
private fun AuthenticatedMissionShell(
    currentUserId: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val telemetry = LocalTelemetryColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()

    val missionViewModel: MissionControlViewModel = viewModel(
        key = "mission_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MissionControlViewModel(AntigravityRepository(db), currentUserId)
            }
        }
    )

    val deepReviewViewModel: DeepReviewViewModel = viewModel(
        key = "ai_review_$currentUserId",
        factory = viewModelFactory {
            initializer {
                DeepReviewViewModel(DeepReviewRepository())
            }
        }
    )

    LaunchedEffect(missionViewModel) {
        missionViewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val workspacesState by missionViewModel.workspacesState.collectAsStateWithLifecycle()
    val tasksState by missionViewModel.tasksState.collectAsStateWithLifecycle()
    val approvalsState by missionViewModel.approvalsState.collectAsStateWithLifecycle()
    val commandsState by missionViewModel.commandsState.collectAsStateWithLifecycle()
    val logsState by missionViewModel.selectedTaskLogsState.collectAsStateWithLifecycle()
    val selectedWorkspaceId by missionViewModel.selectedWorkspaceId.collectAsStateWithLifecycle()
    val pendingApprovalCount by missionViewModel.pendingApprovalCount.collectAsStateWithLifecycle()
    val isActionInFlight by missionViewModel.isActionInFlight.collectAsStateWithLifecycle()

    val reviewUiState by deepReviewViewModel.uiState.collectAsStateWithLifecycle()
    val reviewDraft by deepReviewViewModel.draft.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DECK
    val showTopLevelChrome = TopLevelDestinations.any { it.route == currentRoute }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .missionAtmosphere(
                accentColor = MaterialTheme.colorScheme.primary,
                gridColor = telemetry.hairlineBorder
            )
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail && showTopLevelChrome) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    NavIconWithBadge(
                                        dest = dest,
                                        selected = selected,
                                        pendingApprovals = pendingApprovalCount
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && showTopLevelChrome) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    NavIconWithBadge(
                                        dest = dest,
                                        selected = selected,
                                        pendingApprovals = pendingApprovalCount
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DECK,
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize()
                    ) {
                        composable(ROUTE_DECK) {
                            DeckScreen(
                                workspacesState = workspacesState,
                                tasksState = tasksState,
                                selectedWorkspaceId = selectedWorkspaceId,
                                pendingApprovalsCount = pendingApprovalCount,
                                isActionInFlight = isActionInFlight,
                                onSelectWorkspace = missionViewModel::selectWorkspace,
                                onOpenTask = { taskId ->
                                    navController.navigate("task_detail/$taskId")
                                },
                                onOpenApprovals = { navigateTopLevel(ROUTE_APPROVALS) },
                                onOpenRelaySetup = { navigateTopLevel(ROUTE_RELAY) },
                                onPauseTask = missionViewModel::pauseTask,
                                onResumeTask = missionViewModel::resumeTask,
                                onStopTask = missionViewModel::stopTask,
                                onRegisterWorkspace = { name, host, env, repo, branch, onDone ->
                                    missionViewModel.registerWorkspace(name, host, env, repo, branch) {
                                        onDone()
                                    }
                                },
                                onDispatchTask = { wsId, title, objective, tool, onDone ->
                                    missionViewModel.dispatchTask(wsId, title, objective, tool, onDone)
                                }
                            )
                        }

                        composable(ROUTE_APPROVALS) {
                            ApprovalsScreen(
                                approvalsState = approvalsState,
                                tasksState = tasksState,
                                isActionInFlight = isActionInFlight,
                                onApprove = { approval ->
                                    missionViewModel.decideApproval(approval, approve = true)
                                },
                                onReject = { approval, note ->
                                    missionViewModel.decideApproval(approval, approve = false, note = note)
                                },
                                onSendToAiReview = { approval ->
                                    deepReviewViewModel.prefillDraft(
                                        title = approval.title,
                                        category = approval.type,
                                        payload = approval.payload,
                                        targetTaskId = approval.taskId
                                    )
                                    navigateTopLevel(ROUTE_AI_REVIEW)
                                    deepReviewViewModel.runDeepReview(extraContext = approval.description)
                                },
                                onCreateGate = { task, type, title, desc, payload, risk, onDone ->
                                    missionViewModel.createApprovalGate(task, type, title, desc, payload, risk, onDone)
                                }
                            )
                        }

                        composable(ROUTE_AI_REVIEW) {
                            AiReviewScreen(
                                reviewUiState = reviewUiState,
                                draft = reviewDraft,
                                approvalsState = approvalsState,
                                tasksState = tasksState,
                                onUpdateTitle = deepReviewViewModel::updateDraftTitle,
                                onUpdateCategory = deepReviewViewModel::updateDraftCategory,
                                onUpdatePayload = deepReviewViewModel::updateDraftPayload,
                                onPrefillFromApproval = { approval ->
                                    deepReviewViewModel.prefillDraft(
                                        title = approval.title,
                                        category = approval.type,
                                        payload = approval.payload,
                                        targetTaskId = approval.taskId
                                    )
                                },
                                onRunAudit = { deepReviewViewModel.runDeepReview() },
                                onResetAudit = deepReviewViewModel::resetAudit,
                                onDispatchSuggestedPrompt = { task, prompt ->
                                    missionViewModel.sendSteeringPrompt(task, prompt)
                                },
                                onCopiedNotice = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }

                        composable(ROUTE_RELAY) {
                            RelaySetupScreen(
                                userId = currentUserId,
                                userEmail = userEmail,
                                workspacesState = workspacesState,
                                commandsState = commandsState,
                                isActionInFlight = isActionInFlight,
                                onRegisterWorkspace = { name, host, env, repo, branch, onDone ->
                                    missionViewModel.registerWorkspace(name, host, env, repo, branch) {
                                        onDone()
                                    }
                                },
                                onPingWorkspace = missionViewModel::pingWorkspace,
                                onDeleteWorkspace = missionViewModel::deleteWorkspace,
                                onSignOut = {
                                    signOutGoogle(credentialManager, scope)
                                },
                                onCopiedNotice = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }

                        composable(
                            route = ROUTE_TASK_DETAIL,
                            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
                        ) { entry ->
                            val taskId = entry.arguments?.getString("taskId").orEmpty()
                            val tasks = (tasksState as? DataUiState.Success)?.data.orEmpty()
                            val workspaces = (workspacesState as? DataUiState.Success)?.data.orEmpty()
                            val task = tasks.firstOrNull { it.id == taskId }
                            val workspaceName = workspaces.firstOrNull { it.id == task?.workspaceId }?.name
                                ?: "Paired Host"

                            TaskDetailScreen(
                                task = task,
                                workspaceName = workspaceName,
                                logsState = logsState,
                                isActionInFlight = isActionInFlight,
                                onStartObservingLogs = missionViewModel::observeLogsForTask,
                                onStopObservingLogs = { missionViewModel.observeLogsForTask(null) },
                                onBack = { navController.popBackStack() },
                                onPause = missionViewModel::pauseTask,
                                onResume = missionViewModel::resumeTask,
                                onStop = missionViewModel::stopTask,
                                onSendSteeringPrompt = missionViewModel::sendSteeringPrompt,
                                onAuditLogsWithAi = { auditedTask, transcript ->
                                    deepReviewViewModel.prefillDraft(
                                        title = "Logs · ${auditedTask.title}",
                                        category = "ERROR_LOG",
                                        payload = transcript,
                                        targetTaskId = auditedTask.id
                                    )
                                    navigateTopLevel(ROUTE_AI_REVIEW)
                                    deepReviewViewModel.runDeepReview(
                                        extraContext = "Task phase: ${auditedTask.currentPhase}; Status: ${auditedTask.status}"
                                    )
                                },
                                onCopiedNotice = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavIconWithBadge(
    dest: NavDestination,
    selected: Boolean,
    pendingApprovals: Int
) {
    val iconVector = if (selected) dest.selectedIcon else dest.unselectedIcon
    val label = stringResource(dest.labelRes)
    if (dest.route == ROUTE_APPROVALS && pendingApprovals > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = LocalTelemetryColors.current.warning,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(if (pendingApprovals > 99) "99+" else pendingApprovals.toString())
                }
            }
        ) {
            Icon(imageVector = iconVector, contentDescription = label)
        }
    } else {
        Icon(imageVector = iconVector, contentDescription = label)
    }
}
