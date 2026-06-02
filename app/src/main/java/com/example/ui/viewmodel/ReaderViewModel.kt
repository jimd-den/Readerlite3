package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Chapter
import com.example.domain.model.Sentence
import com.example.domain.repository.StudyRepository
import com.example.domain.usecase.LoadChapterSentencesUseCase
import com.example.domain.usecase.NavigateSentenceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Split ViewModel handling reading screen state: active chapter, current sentence indices,
 * reading mode, and navigation actions.
 */
class ReaderViewModel(
    private val repository: StudyRepository,
    private val loadChapterSentencesUseCase: LoadChapterSentencesUseCase,
    private val navigateSentenceUseCase: NavigateSentenceUseCase
) : ViewModel() {

    private val _activeChapter = MutableStateFlow<Chapter?>(null)
    val activeChapter: StateFlow<Chapter?> = _activeChapter.asStateFlow()

    private val _chapterSentences = MutableStateFlow<List<Sentence>>(emptyList())
    val chapterSentences: StateFlow<List<Sentence>> = _chapterSentences.asStateFlow()

    private val _activeSentenceIndex = MutableStateFlow(0)
    val activeSentenceIndex: StateFlow<Int> = _activeSentenceIndex.asStateFlow()

    fun selectChapter(chapter: Chapter) {
        _activeChapter.value = chapter
        _activeSentenceIndex.value = 0
        loadSentencesForChapter(chapter.bookId, chapter.orderIndex)
    }

    private fun loadSentencesForChapter(bookId: String, chapterIndex: Int) {
        viewModelScope.launch {
            loadChapterSentencesUseCase.execute(bookId, chapterIndex).collect { list ->
                _chapterSentences.value = list
                _activeSentenceIndex.value = 0
            }
        }
    }

    fun navigateToNextSentence() {
        val size = _chapterSentences.value.size
        _activeSentenceIndex.value = navigateSentenceUseCase.calculateNextIndex(_activeSentenceIndex.value, size)
    }

    fun navigateToPreviousSentence() {
        _activeSentenceIndex.value = navigateSentenceUseCase.calculatePreviousIndex(_activeSentenceIndex.value)
    }

    fun selectSentenceIndex(index: Int) {
        val size = _chapterSentences.value.size
        _activeSentenceIndex.value = navigateSentenceUseCase.calculateIndexWithinBounds(index, size)
    }

    fun clearActiveChapter() {
        _activeChapter.value = null
        _chapterSentences.value = emptyList()
        _activeSentenceIndex.value = 0
    }
}
