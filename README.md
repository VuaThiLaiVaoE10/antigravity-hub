# Antigravity Hub

**Antigravity Hub** is a Dark Mission Control Android companion app for **Google Antigravity** that monitors live autonomous agent progress, manages human-in-the-loop approvals, streams terminal telemetry, dispatches remote steering commands over a **Firebase Cloud Firestore** relay, and provides deep-thinking code/error audits powered by **Gemini 4 Carbon (`ThinkingLevel.HIGH`)**.

---

## Core Features

- **Command Deck**: Monitor paired local workstations and cloud sandboxes, inspect active run counts and pending approval gates, dispatch remote missions, and issue **Pause**, **Resume**, or **Stop** commands with confirmation guards.
- **Approval Queue (Human-in-the-Loop)**: Review pending **Implementation Plans**, **Code Diffs**, and **Terminal Commands** with risk ratings, expandable monospace payloads, one-tap **Approve / Reject** (with optional rationale), and instant handoff to AI Deep Review.
- **Live Task Telemetry & Steering**: Inspect chronological **Step Timelines** and filterable monospace **Terminal Streams** (`ALL`, `STEP`, `CMD`, `WARN`, `ERROR`), copy transcripts, and send real-time steering prompts with quick-steer presets.
- **Gemini 4 Carbon High-Thinking Analyst**: Stream deep-reasoning audits (`gemini-4-carbon`, `ThinkingLevel.HIGH`) via **Firebase AI Logic** and **Firebase App Check** to evaluate risky diffs, plans, or build errors and dispatch the recommended steering prompt directly to an active agent.
- **Workspaces & Desktop Relay Contract**: Pair/manage workstations, send heartbeat pings, inspect outbound command delivery status, and copy your authenticated Firestore relay paths for a desktop Antigravity bridge.
- **Zero-Trust Google Sign-In**: Authenticated via Jetpack **Credential Manager** (`GetSignInWithGoogleOption`), enforcing strict per-user data isolation in Cloud Firestore.

---

## Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose, Material Design 3 (adaptive `NavigationBar` / `NavigationRail`, edge-to-edge safe insets)
- **Typography**: Bundled Google Fonts (`Space Grotesk`, `Inter`, `JetBrains Mono`)
- **Backend & Sync**: Firebase Cloud Firestore Enterprise (named database), Firebase Authentication (Google Sign-In via Credential Manager)
- **AI & Attestation**: Firebase AI Logic (`Firebase.ai`, `gemini-4-carbon`, `ThinkingLevel.HIGH`), Firebase App Check (Debug & reCAPTCHA Enterprise)
- **Architecture**: MVVM (`ViewModel`, `StateFlow`, Repository pattern), Navigation Compose

---

## Cloud Relay Firestore Schema

All data is strictly isolated under `/users/{uid}/...` and protected by `firestore.rules` (enforcing authentication, ownership, schema validation, parent existence, and terminal state locking):

| Collection Path | Purpose |
| :--- | :--- |
| `users/{uid}/workspaces/{workspaceId}` | Registered local workstations & cloud sandboxes with live heartbeat status (`ONLINE`, `IDLE`, `OFFLINE`). |
| `users/{uid}/tasks/{taskId}` | Autonomous agent runs (`RUNNING`, `PAUSED`, `AWAITING_APPROVAL`, `COMPLETED`, `FAILED`, `STOPPED`). |
| `users/{uid}/approvals/{approvalId}` | Pending and decided `PLAN`, `CODE_DIFF`, and `TERMINAL_COMMAND` gates. |
| `users/{uid}/commands/{commandId}` | Outbound control queue (`PAUSE`, `RESUME`, `STOP`, `STEER_PROMPT`, `APPROVE_ARTIFACT`, `REJECT_ARTIFACT`, `START_TASK`). |
| `users/{uid}/logs/{logId}` | Append-only step timeline and terminal log stream entries. |

---

## Getting Started

### Prerequisites
- Android Studio (Koala / Ladybug or newer) with JDK 11+
- Android SDK 36 (`minSdk = 24`, `targetSdk = 36`)
- A Firebase project with **Cloud Firestore**, **Google Sign-In**, **Firebase AI Logic**, and **App Check** enabled (`app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml`)

### Build Debug / Release APK
```bash
# Compile & assemble Debug APK
gradle :app:assembleDebug

# Assemble Release APK (requires release signing keystore configured via environment variables)
gradle :app:assembleRelease
```

### Run Firestore Security Rules Tests Locally
```bash
# Start single-database Firestore emulator on port 8085
firebase emulators:start --only firestore --project demo-no-project

# In another terminal, execute the security test suite
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

---

## Distributing via Direct `.apk` (GitHub Releases) or Google Play

1. **Automated GitHub Releases APK (Included via GitHub Actions)**:
   - Every push to `main` (or manual run under **Actions → Build & Publish Android APK**) automatically compiles `AntigravityHub-latest.apk` and publishes it to the **`latest`** Release.
   - On your Android phone, open `https://github.com/vuathilaivaoe10/<your-repo>/releases` (or direct download `.../releases/latest/download/AntigravityHub-latest.apk`), tap **`AntigravityHub-latest.apk`**, allow *Install unknown apps*, and install.
   - *(Tip for Google Sign-In)*: To keep the same SHA-1 already registered in Firebase, add your base64-encoded `debug.keystore` as a repository secret named `DEBUG_KEYSTORE_BASE64`, or copy the SHA-1/SHA-256 printed in the Actions log into **Firebase Console → Project Settings → Your apps (Android)**.
2. **Manual `.apk` Sharing**:
   - Generate the `.apk` (via AI Studio **Settings → Generate APK** or `gradle :app:assembleRelease`) and attach it to a new Release in your repository.
   - Release builds automatically use **reCAPTCHA Enterprise** (`recaptcha_site_key`) for Firebase App Check.
3. **Google Play Store (`.aab`)**:
   - Export an Android App Bundle (`.aab`), enroll in Play App Signing, and register Play's App Signing SHA-1/SHA-256 certificates in Firebase Console.

---

## Security & Privacy Notes

- No client-side Gemini API key is required or bundled; all Gemini calls are authenticated via **Firebase App Check** and routed through **Firebase AI Logic**.
- Sensitive local artifacts (`.env`, `debug.keystore`, `*-debug.log`) are excluded via `.gitignore`.
