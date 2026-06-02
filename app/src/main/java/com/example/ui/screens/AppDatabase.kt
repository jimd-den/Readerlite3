package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "study_classes")
data class StudyClassEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val createdAt: Long
)

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val classId: String,
    val title: String,
    val author: String,
    val fileType: String,
    val filePath: String,
    val totalChapters: Int,
    val createdAt: Long
)

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String,
    val orderIndex: Int,
    val isSubchapter: Boolean = false,
    val parentTitle: String? = null,
    val nestingLevel: Int = 0
)

@Entity(tableName = "sentences")
data class SentenceEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val sentenceIndex: Int,
    val text: String,
    val sectionTitle: String?
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val classId: String,
    val bookId: String,
    val chapterIndex: Int,
    val sectionTitle: String?,
    val sentenceIndex: Int?,
    val content: String,
    val type: String, // "NOTE", "QUESTION"
    val snippet: String?,
    val createdAt: Long
)

@Entity(tableName = "saved_rewrites")
data class SavedRewriteEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val prompt: String,
    val rewrittenText: String,
    val createdAt: Long
)

@Dao
interface StudyClassDao {
    @Query("SELECT * FROM study_classes ORDER BY createdAt DESC")
    fun getAllClasses(): Flow<List<StudyClassEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClass(studyClass: StudyClassEntity)

    @Query("DELETE FROM study_classes WHERE id = :id")
    suspend fun deleteClassById(id: String)
}

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE classId = :classId ORDER BY createdAt DESC")
    fun getBooksForClass(classId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: String)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex ASC")
    fun getChaptersForBook(bookId: String): Flow<List<ChapterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)
}

@Dao
interface SentenceDao {
    @Query("SELECT * FROM sentences WHERE bookId = :bookId AND chapterIndex = :chapterIndex ORDER BY sentenceIndex ASC")
    fun getSentencesForChapter(bookId: String, chapterIndex: Int): Flow<List<SentenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentences(sentences: List<SentenceEntity>)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getNotesForBook(bookId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE bookId = :bookId AND chapterIndex = :chapterIndex ORDER BY createdAt DESC")
    fun getNotesForChapter(bookId: String, chapterIndex: Int): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: String)
}

@Dao
interface SavedRewriteDao {
    @Query("SELECT * FROM saved_rewrites WHERE bookId = :bookId AND chapterIndex = :chapterIndex LIMIT 1")
    suspend fun getRewriteForChapter(bookId: String, chapterIndex: Int): SavedRewriteEntity?

    @Query("SELECT * FROM saved_rewrites WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getSavedRewritesForBook(bookId: String): Flow<List<SavedRewriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRewrite(rewrite: SavedRewriteEntity)
}

@Database(
    entities = [
        StudyClassEntity::class,
        BookEntity::class,
        ChapterEntity::class,
        SentenceEntity::class,
        NoteEntity::class,
        SavedRewriteEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studyClassDao(): StudyClassDao
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun sentenceDao(): SentenceDao
    abstract fun noteDao(): NoteDao
    abstract fun savedRewriteDao(): SavedRewriteDao
}
