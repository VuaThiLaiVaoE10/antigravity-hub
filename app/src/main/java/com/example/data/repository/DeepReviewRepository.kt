package com.example.data.repository

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

class DeepReviewRepository {

    // Lazy initialization ensures FirebaseApp and App Check are configured first.
    // Mandated high-thinking configuration: gemini-4-carbon + ThinkingLevel.HIGH, no maxOutputTokens cap.
    private val deepThinkingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-carbon",
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            },
            systemInstruction = content {
                text(
                    """
                    You are the Antigravity Hub Deep-Thinking Engineering Analyst.
                    You review autonomous coding agent plans, unified code diffs, shell commands, and terminal error logs for a senior developer supervising Antigravity remotely from mobile.

                    Structure every audit concisely with clear headings:
                    1. VERDICT: One of [APPROVE], [HOLD & STEER], or [REJECT] with a 1-sentence rationale.
                    2. RISK & IMPACT: Specific regressions, security/data-loss hazards, or edge cases.
                    3. ROOT CAUSE / KEY FINDINGS: Concrete technical findings citing files, symbols, or commands.
                    4. SUGGESTED STEERING PROMPT: Put a single, crisp, copy-ready steering instruction on the final line prefixed exactly with `STEER_PROMPT: ` so the developer can dispatch it to the running Antigravity agent with one tap.
                    """.trimIndent()
                )
            }
        )
    }

    fun streamDeepAudit(
        subjectTitle: String,
        subjectCategory: String,
        payload: String,
        extraContext: String = ""
    ): Flow<String> {
        val prompt = buildString {
            appendLine("Review Subject: $subjectTitle")
            appendLine("Artifact / Telemetry Type: $subjectCategory")
            if (extraContext.isNotBlank()) {
                appendLine("Additional Context: $extraContext")
            }
            appendLine()
            appendLine("--- PAYLOAD START ---")
            appendLine(payload.trim())
            appendLine("--- PAYLOAD END ---")
        }
        return deepThinkingModel.generateContentStream(prompt)
            .mapNotNull { chunk -> chunk.text }
    }
}
