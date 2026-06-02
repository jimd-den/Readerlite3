package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface StudyRepository {
    fun getAllClasses(): Flow<List<StudyClass>>
    suspend fun createClass(name: String, description: String): String
    suspend fun deleteClass(classId: String)

    fun getBooksForClass(classId: String): Flow<List<Book>>
    suspend fun getBookById(bookId: String): Book?
    suspend fun importBook(
        classId: String,
        title: String,
        author: String,
        fileType: String,
        filePath: String,
        structure: EpubStructureDomainModel
    ): String
    suspend fun deleteBook(bookId: String)

    fun getChaptersForBook(bookId: String): Flow<List<Chapter>>
    fun getSentencesForChapter(bookId: String, chapterIndex: Int): Flow<List<Sentence>>

    fun getNotesForBook(bookId: String): Flow<List<Note>>
    fun getNotesForChapter(bookId: String, chapterIndex: Int): Flow<List<Note>>
    suspend fun addNote(
        classId: String,
        bookId: String,
        chapterIndex: Int,
        sectionTitle: String?,
        sentenceIndex: Int?,
        content: String,
        type: NoteType,
        snippet: String?
    ): String
    suspend fun deleteNote(noteId: String)

    suspend fun getRewriteForChapter(bookId: String, chapterIndex: Int): SavedRewrite?
    fun getSavedRewritesForBook(bookId: String): Flow<List<SavedRewrite>>
    suspend fun saveRewrite(bookId: String, chapterIndex: Int, prompt: String, rewrittenText: String)
}
