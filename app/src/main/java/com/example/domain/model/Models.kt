package com.example.domain.model

data class StudyClass(
    val id: String,
    val name: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Book(
    val id: String,
    val classId: String,
    val title: String,
    val author: String,
    val fileType: String, // "PDF", "EPUB", "TXT"
    val filePath: String,
    val totalChapters: Int,
    val createdAt: Long = System.currentTimeMillis()
)

data class Chapter(
    val id: String,
    val bookId: String,
    val title: String,
    val orderIndex: Int,
    val isSubchapter: Boolean = false,
    val parentTitle: String? = null,
    val nestingLevel: Int = 0
)

data class Sentence(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val sentenceIndex: Int,
    val text: String,
    val sectionTitle: String? = null
)

enum class NoteType {
    NOTE, QUESTION
}

data class Note(
    val id: String,
    val classId: String,
    val bookId: String,
    val chapterIndex: Int,
    val sectionTitle: String?,
    val sentenceIndex: Int?, // if attached to a specific sentence
    val content: String,
    val type: NoteType,
    val snippet: String? = null, // excerpt text
    val createdAt: Long = System.currentTimeMillis()
)

data class SavedRewrite(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val prompt: String,
    val rewrittenText: String,
    val createdAt: Long = System.currentTimeMillis()
)
