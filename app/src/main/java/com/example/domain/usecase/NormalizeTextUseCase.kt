package com.example.domain.usecase

import com.example.domain.service.TextSanitizer

/**
 * Clean architecture use case representing the text normalization boundary.
 */
class NormalizeTextUseCase(private val sanitizer: TextSanitizer) {
    fun execute(text: String): String {
        return sanitizer.sanitize(text)
    }
}
