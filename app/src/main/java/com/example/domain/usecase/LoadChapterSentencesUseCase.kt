package com.example.domain.usecase

import com.example.domain.model.Sentence
import com.example.domain.repository.StudyRepository
import kotlinx.coroutines.flow.Flow

/**
 * Clean architecture Use Case to load sentences for a given chapter as a reactive flow.
 */
class LoadChapterSentencesUseCase(private val repository: StudyRepository) {
    fun execute(bookId: String, chapterIndex: Int): Flow<List<Sentence>> {
        return repository.getSentencesForChapter(bookId, chapterIndex)
    }
}
