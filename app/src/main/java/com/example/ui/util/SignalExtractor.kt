package com.example.ui.util

import com.example.domain.model.TypographySignals

object SignalExtractor {
    fun extract(text: String): TypographySignals {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return TypographySignals(0, true, true, false, false, 0f, 0, 0f, false, false, false)
        }
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val wordCount = words.size
        val isShortForm = wordCount <= 10
        val isPosterCandidate = wordCount <= 5
        val hasExclamation = trimmed.contains("!")
        val hasQuestion = trimmed.contains("?")
        
        var capsWordsCount = 0
        var longestLength = 0
        var punctuationCount = 0
        
        for (word in words) {
            val cleanWord = word.replace(Regex("[^a-zA-Z]"), "")
            if (cleanWord.isNotEmpty()) {
                // Count titlecase or uppercase
                if (cleanWord == cleanWord.uppercase() || (cleanWord.isNotEmpty() && cleanWord[0].isUpperCase())) {
                    capsWordsCount++
                }
                if (cleanWord.length > longestLength) {
                    longestLength = cleanWord.length
                }
            }
            
            if (word.contains(",") || word.contains(";") || word.contains(":")) {
                punctuationCount++
            }
        }
        
        val capsRatio = if (wordCount > 0) capsWordsCount.toFloat() / wordCount.toFloat() else 0f
        val punctuationDensity = if (wordCount > 0) punctuationCount.toFloat() / wordCount.toFloat() else 0f
        
        // Structural overrides for code-heavy documents or formatted highlights
        val isCode = trimmed.startsWith("```") || trimmed.endsWith("```") || 
                     trimmed.contains("{") || trimmed.contains("}") || 
                     trimmed.contains("fun ") || trimmed.contains("class ") || 
                     trimmed.startsWith("import ") || trimmed.contains("val ") || 
                     trimmed.contains("var ") || trimmed.contains(";") && (trimmed.contains("import") || trimmed.contains("public") || trimmed.contains("return"))
                     
        val isHeading = trimmed.startsWith("#") || (wordCount in 1..4 && capsRatio > 0.8f && punctuationDensity == 0f)
        val isBlockquote = trimmed.startsWith(">")
        
        return TypographySignals(
            wordCount = wordCount,
            isShortForm = isShortForm,
            isPosterCandidate = isPosterCandidate,
            hasExclamation = hasExclamation,
            hasQuestion = hasQuestion,
            capsRatio = capsRatio,
            longestWordLength = longestLength,
            punctuationDensity = punctuationDensity,
            isCode = isCode,
            isHeading = isHeading,
            isBlockquote = isBlockquote
        )
    }
}
