package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.DeepReviewRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface DeepReviewUiState {
    data object Idle : DeepReviewUiState
    data class Thinking(val subjectTitle: String) : DeepReviewUiState
    data class Streaming(val subjectTitle: String, val text: String) : DeepReviewUiState
    data class Success(
        val subjectTitle: String,
        val text: String,
        val suggestedSteerPrompt: String?
    ) : DeepReviewUiState
    data class Error(val subjectTitle: String, val message: String) : DeepReviewUiState
}

data class ReviewDraft(
    val title: String = "",
    val category: String = "CODE_DIFF",
    val payload: String = "",
    val targetTaskId: String? = null
)

class DeepReviewViewModel(
    private val repository: DeepReviewRepository = DeepReviewRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<DeepReviewUiState>(DeepReviewUiState.Idle)
    val uiState: StateFlow<DeepReviewUiState> = _uiState.asStateFlow()

    private val _draft = MutableStateFlow(ReviewDraft())
    val draft: StateFlow<ReviewDraft> = _draft.asStateFlow()

    private var activeJob: Job? = null

    fun prefillDraft(
        title: String,
        category: String,
        payload: String,
        targetTaskId: String? = null
    ) {
        _draft.value = ReviewDraft(
            title = title,
            category = category,
            payload = payload,
            targetTaskId = targetTaskId
        )
    }

    fun updateDraftTitle(title: String) {
        _draft.value = _draft.value.copy(title = title)
    }

    fun updateDraftCategory(category: String) {
        _draft.value = _draft.value.copy(category = category)
    }

    fun updateDraftPayload(payload: String) {
        _draft.value = _draft.value.copy(payload = payload)
    }

    fun runDeepReview(extraContext: String = "") {
        val current = _draft.value
        val cleanPayload = current.payload.trim()
        if (cleanPayload.isEmpty()) {
            _uiState.value = DeepReviewUiState.Error(
                subjectTitle = current.title.ifBlank { "Deep Review" },
                message = "Provide a diff, implementation plan, shell command, or terminal error log to analyze."
            )
            return
        }
        val cleanTitle = current.title.trim().ifBlank { "Antigravity Artifact Audit" }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = DeepReviewUiState.Thinking(cleanTitle)
            val accumulated = StringBuilder()
            try {
                withTimeout(AI_TIMEOUT_MILLIS) {
                    repository.streamDeepAudit(
                        subjectTitle = cleanTitle,
                        subjectCategory = current.category,
                        payload = cleanPayload,
                        extraContext = extraContext
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        _uiState.value = DeepReviewUiState.Streaming(
                            subjectTitle = cleanTitle,
                            text = accumulated.toString()
                        )
                    }
                }
                val finalOutput = accumulated.toString().trim()
                if (finalOutput.isNotEmpty()) {
                    _uiState.value = DeepReviewUiState.Success(
                        subjectTitle = cleanTitle,
                        text = finalOutput,
                        suggestedSteerPrompt = extractSteerPrompt(finalOutput)
                    )
                } else {
                    _uiState.value = DeepReviewUiState.Error(
                        subjectTitle = cleanTitle,
                        message = "Gemini returned an empty audit response. Please retry."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "High-thinking audit timed out", e)
                _uiState.value = DeepReviewUiState.Error(
                    subjectTitle = cleanTitle,
                    message = "Deep-thinking review timed out after 60 seconds. Try shortening the diff or retrying."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "High-thinking audit failed", e)
                _uiState.value = DeepReviewUiState.Error(
                    subjectTitle = cleanTitle,
                    message = e.localizedMessage ?: "Deep-thinking review failed."
                )
            }
        }
    }

    fun resetAudit() {
        activeJob?.cancel()
        _uiState.value = DeepReviewUiState.Idle
    }

    private fun extractSteerPrompt(output: String): String? {
        val marker = "STEER_PROMPT:"
        val line = output.lines().lastOrNull { it.trim().startsWith(marker, ignoreCase = true) }
            ?: return null
        return line.substringAfter(":", "").trim().takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val TAG = "DeepReviewVM"
        const val AI_TIMEOUT_MILLIS = 60_000L
    }
}
