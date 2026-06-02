package com.example.domain.usecase

/**
 * Clean architecture Use Case to encapsulate bounds calculation when navigating reader sentences.
 */
class NavigateSentenceUseCase {
    fun calculateNextIndex(currentIndex: Int, maxIndex: Int): Int {
        if (maxIndex <= 0) return 0
        return (currentIndex + 1).coerceAtMost(maxIndex - 1)
    }

    fun calculatePreviousIndex(currentIndex: Int): Int {
        return (currentIndex - 1).coerceAtLeast(0)
    }

    fun calculateIndexWithinBounds(targetIndex: Int, maxIndex: Int): Int {
        if (maxIndex <= 0) return 0
        return targetIndex.coerceIn(0, maxIndex - 1)
    }
}
