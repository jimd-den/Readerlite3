package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.gateway.AiGateway
import com.example.domain.model.SavedRewrite
import com.example.domain.repository.StudyRepository
import com.example.domain.service.TextSanitizer
import com.example.domain.usecase.LoadRewriteUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.first

/**
 * Split ViewModel handling AI adaptation tasks, progress states, saved rewrites,
 * and background API executions.
 */
class RewriteViewModel(
    private val repository: StudyRepository,
    private val loadRewriteUseCase: LoadRewriteUseCase,
    private val sanitizer: TextSanitizer
) : ViewModel() {

    private val _isRewriting = MutableStateFlow(false)
    val isRewriting: StateFlow<Boolean> = _isRewriting.asStateFlow()

    private val _rewriteProgress = MutableStateFlow<String?>(null)
    val rewriteProgress: StateFlow<String?> = _rewriteProgress.asStateFlow()

    private val _rewriteError = MutableStateFlow<String?>(null)
    val rewriteError: StateFlow<String?> = _rewriteError.asStateFlow()

    private val _activeRewrite = MutableStateFlow<SavedRewrite?>(null)
    val activeRewrite: StateFlow<SavedRewrite?> = _activeRewrite.asStateFlow()

    private val _rewrittenSentences = MutableStateFlow<List<String>>(emptyList())
    val rewrittenSentences: StateFlow<List<String>> = _rewrittenSentences.asStateFlow()

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent: SharedFlow<String> = _uiEvent.asSharedFlow()

    fun clearRewriteError() {
        _rewriteError.value = null
    }

    fun loadRewriteForChapter(bookId: String, chapterIndex: Int) {
        viewModelScope.launch {
            val rewrite = loadRewriteUseCase.execute(bookId, chapterIndex)
            _activeRewrite.value = rewrite
            if (rewrite != null) {
                _rewrittenSentences.value = rewrite.rewrittenText
                    .split(Regex("(?<=[.!?])\\s+"))
                    .map { sanitizer.sanitize(it) }
                    .filter { it.isNotEmpty() }
            } else {
                _rewrittenSentences.value = emptyList()
            }
        }
    }

    fun triggerRewrite(
        bookId: String,
        chapterIndex: Int,
        style: String,
        customPrompt: String?,
        openRouterKey: String,
        openRouterModel: String,
        forceSimulation: Boolean,
        onSuccess: suspend () -> Unit
    ) {
        _isRewriting.value = true
        _rewriteProgress.value = "Starting chapter rewrite..."
        _rewriteError.value = null

        viewModelScope.launch {
            try {
                _rewriteProgress.value = "Loading original sentences..."
                val originalSentences = repository.getSentencesForChapter(bookId, chapterIndex).first()
                if (originalSentences.isEmpty()) {
                    throw Exception("No sentences found in this chapter to adapt.")
                }

                _rewriteProgress.value = "Reading ${originalSentences.size} original sentences..."
                val fullText = originalSentences.joinToString(" ") { it.text }

                val rewritten = if (openRouterKey.isNotBlank()) {
                    _rewriteProgress.value = "Connecting to OpenRouter..."
                    _uiEvent.emit("Sending rewrite request to OpenRouter...")
                    AiGateway.rewriteChapterOpenRouter(openRouterKey, openRouterModel, fullText, customPrompt ?: style)
                } else {
                    _rewriteProgress.value = "Sending request to Gemini API..."
                    _uiEvent.emit("Sending rewrite request to Gemini...")
                    AiGateway.rewriteChapter(fullText, style, customPrompt, forceSimulation)
                }

                _rewriteProgress.value = "Success! Saving adapted structure..."
                repository.saveRewrite(bookId, chapterIndex, customPrompt ?: style, rewritten)

                loadRewriteForChapter(bookId, chapterIndex)
                onSuccess()

                _rewriteProgress.value = null
                _isRewriting.value = false
                _uiEvent.emit("Rewrite complete! Chapter adapted.")
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = e.localizedMessage ?: "Unknown error"
                _rewriteError.value = errorMsg
                _isRewriting.value = false
                _rewriteProgress.value = null
                _uiEvent.emit("AI Rewrite Failed: $errorMsg")
            }
        }
    }
}
