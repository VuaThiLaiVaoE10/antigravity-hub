const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_engineer_123";
const BOB_UID = "bob_other_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msAgo = 2000) {
  return new Date(Date.now() - msAgo);
}

function futureDate(msAhead = 60000) {
  return new Date(Date.now() + msAhead);
}

function makeWorkspacePayload(uid, overrides = {}) {
  const ts = pastDate(5000);
  return {
    userId: uid,
    name: "MacBook Pro M4",
    hostName: "dev-mbp.local",
    environment: "LOCAL_WORKSTATION",
    repository: "github.com/acme/antigravity-core",
    branch: "main",
    status: "ONLINE",
    lastHeartbeatAt: ts,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function makeTaskPayload(uid, workspaceId, overrides = {}) {
  const ts = pastDate(4000);
  return {
    userId: uid,
    workspaceId,
    title: "Refactor auth middleware",
    objective: "Migrate session validation to JWT rotation and run full test suite.",
    status: "RUNNING",
    currentPhase: "Executing unit tests",
    activeTool: "TERMINAL",
    progressPercent: 45,
    summary: "Updated 4 files; running verification suite.",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function makeApprovalPayload(uid, workspaceId, taskId, overrides = {}) {
  const ts = pastDate(3000);
  return {
    userId: uid,
    workspaceId,
    taskId,
    type: "TERMINAL_COMMAND",
    title: "Run database migration script",
    description: "Applies schema migration v14 against staging DB.",
    payload: "./scripts/migrate.sh --env=staging",
    riskLevel: "HIGH",
    status: "PENDING",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function makeCommandPayload(uid, workspaceId, taskId, overrides = {}) {
  const ts = pastDate(2000);
  return {
    userId: uid,
    workspaceId,
    taskId,
    action: "STEER_PROMPT",
    payload: "Focus on fixing the failing token refresh unit test first.",
    deliveryStatus: "QUEUED",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function makeLogPayload(uid, taskId, overrides = {}) {
  return {
    userId: uid,
    taskId,
    level: "STEP",
    source: "TERMINAL",
    message: "gradle :app:testDebugUnitTest -> 18 passed, 0 failed",
    createdAt: pastDate(1500),
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any Antigravity Hub collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("workspaces").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workspaces")
      .doc("ws_1")
      .set(makeWorkspacePayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("approvals").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("commands").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("logs").get());
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's data", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workspaces")
      .doc("ws_1")
      .set(makeWorkspacePayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("workspaces").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("workspaces").doc("ws_1").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workspaces")
      .doc("ws_1")
      .update({ status: "OFFLINE", updatedAt: pastDate(500) })
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workspaces")
      .doc("ws_spoof")
      .set(makeWorkspacePayload(BOB_UID))
  );
});

test("Workspace lifecycle, heartbeat update, and shadow-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const wsRef = aliceDb.collection("users").doc(ALICE_UID).collection("workspaces").doc("ws_main");

  // Valid create (no @DocumentId `id` field)
  await assertSucceeds(wsRef.set(makeWorkspacePayload(ALICE_UID)));

  // List query matching Kotlin repository observeWorkspaces()
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("workspaces").get());

  // Valid partial update (heartbeat/status)
  await assertSucceeds(
    wsRef.update({
      status: "IDLE",
      lastHeartbeatAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Reject future timestamp
  await assertFails(
    wsRef.update({
      status: "ONLINE",
      lastHeartbeatAt: futureDate(),
      updatedAt: pastDate(500),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    wsRef.update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // Reject shadow/ghost field or @DocumentId `id` field on create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workspaces")
      .doc("ws_bad")
      .set(makeWorkspacePayload(ALICE_UID, { id: "ws_bad" }))
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workspaces")
      .doc("ws_ghost")
      .set(makeWorkspacePayload(ALICE_UID, { isVerified: true }))
  );
});

test("AgentTask requires existing parent workspace, supports lifecycle controls, and locks terminal states", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);

  // Orphaned task without existing workspace must fail
  await assertFails(
    userDoc.collection("tasks").doc("task_orphan").set(makeTaskPayload(ALICE_UID, "ws_missing"))
  );

  // Create parent workspace first
  await assertSucceeds(
    userDoc.collection("workspaces").doc("ws_1").set(makeWorkspacePayload(ALICE_UID))
  );

  // Create valid task
  const taskRef = userDoc.collection("tasks").doc("task_1");
  await assertSucceeds(taskRef.set(makeTaskPayload(ALICE_UID, "ws_1")));

  // List query matching Kotlin repository observeTasks()
  await assertSucceeds(userDoc.collection("tasks").get());

  // Pause -> Resume -> Stop lifecycle transitions succeed
  await assertSucceeds(
    taskRef.update({
      status: "PAUSED",
      currentPhase: "Paused by remote operator",
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    taskRef.update({
      status: "RUNNING",
      currentPhase: "Resumed execution",
      progressPercent: 70,
      updatedAt: pastDate(800),
    })
  );
  await assertSucceeds(
    taskRef.update({
      status: "STOPPED",
      currentPhase: "Stopped by operator",
      updatedAt: pastDate(600),
    })
  );

  // Terminal state lock: STOPPED task cannot be mutated again
  await assertFails(
    taskRef.update({
      status: "RUNNING",
      updatedAt: pastDate(200),
    })
  );
});

test("ApprovalRequest enforces parent task, one-time PENDING decision, and immutable payload", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    userDoc.collection("workspaces").doc("ws_1").set(makeWorkspacePayload(ALICE_UID))
  );
  await assertSucceeds(
    userDoc.collection("tasks").doc("task_1").set(makeTaskPayload(ALICE_UID, "ws_1"))
  );

  // Cannot create pre-approved gate
  await assertFails(
    userDoc
      .collection("approvals")
      .doc("app_bad")
      .set(makeApprovalPayload(ALICE_UID, "ws_1", "task_1", { status: "APPROVED" }))
  );

  const approvalRef = userDoc.collection("approvals").doc("app_1");
  await assertSucceeds(approvalRef.set(makeApprovalPayload(ALICE_UID, "ws_1", "task_1")));

  // List query matching Kotlin repository observeApprovals()
  await assertSucceeds(userDoc.collection("approvals").get());

  // Cannot tamper with payload while approving
  await assertFails(
    approvalRef.update({
      status: "APPROVED",
      payload: "rm -rf /",
      updatedAt: pastDate(500),
    })
  );

  // Valid human decision with decisionNote
  await assertSucceeds(
    approvalRef.update({
      status: "APPROVED",
      decisionNote: "Reviewed with Gemini 4 Carbon high-thinking audit.",
      updatedAt: pastDate(400),
    })
  );

  // Terminal state lock: already APPROVED gate cannot be flipped to REJECTED
  await assertFails(
    approvalRef.update({
      status: "REJECTED",
      decisionNote: "Changed mind",
      updatedAt: pastDate(200),
    })
  );
});

test("ControlCommand and append-only LogEntry enforce schema, parent task, and immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    userDoc.collection("workspaces").doc("ws_1").set(makeWorkspacePayload(ALICE_UID))
  );
  await assertSucceeds(
    userDoc.collection("tasks").doc("task_1").set(makeTaskPayload(ALICE_UID, "ws_1"))
  );

  // Create command
  const cmdRef = userDoc.collection("commands").doc("cmd_1");
  await assertSucceeds(cmdRef.set(makeCommandPayload(ALICE_UID, "ws_1", "task_1")));
  await assertSucceeds(userDoc.collection("commands").get());

  // Acknowledge and execute command
  await assertSucceeds(
    cmdRef.update({ deliveryStatus: "ACKNOWLEDGED", updatedAt: pastDate(500) })
  );
  await assertSucceeds(
    cmdRef.update({ deliveryStatus: "EXECUTED", updatedAt: pastDate(300) })
  );

  // Executed command is terminal
  await assertFails(
    cmdRef.update({ deliveryStatus: "QUEUED", updatedAt: pastDate(100) })
  );

  // Create append-only log entry
  const logRef = userDoc.collection("logs").doc("log_1");
  await assertSucceeds(logRef.set(makeLogPayload(ALICE_UID, "task_1")));
  await assertSucceeds(
    userDoc.collection("logs").where("taskId", "==", "task_1").get()
  );

  // Logs cannot be updated (append-only)
  await assertFails(logRef.update({ message: "Rewritten history" }));

  // Owner can delete log
  await assertSucceeds(logRef.delete());
});
