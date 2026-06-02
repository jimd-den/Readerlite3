package com.example.domain.usecase

import com.example.domain.model.SavedRewrite
import com.example.domain.repository.StudyRepository

/**
 * Clean architecture Use Case to load a saved rewrite for a chapter.
 */
class LoadRewriteUseCase(private val repository: StudyRepository) {
    suspend fun execute(bookId: String, chapterIndex: Int): SavedRewrite? {
        return repository.getRewriteForChapter(bookId, chapterIndex)
    }
}
