package com.example.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.data.db.*
import com.example.domain.model.*
import com.example.domain.repository.StudyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class StudyRepositoryImpl(
    private val db: AppDatabase
) : StudyRepository {

    private val studyClassDao = db.studyClassDao()
    private val bookDao = db.bookDao()
    private val chapterDao = db.chapterDao()
    private val sentenceDao = db.sentenceDao()
    private val noteDao = db.noteDao()
    private val savedRewriteDao = db.savedRewriteDao()

    override fun getAllClasses(): Flow<List<StudyClass>> {
        return studyClassDao.getAllClasses().map { entities ->
            entities.map { StudyClass(it.id, it.name, it.description, it.createdAt) }
        }
    }

    override suspend fun createClass(name: String, description: String): String {
        val id = UUID.randomUUID().toString()
        studyClassDao.insertClass(
            StudyClassEntity(id, name, description, System.currentTimeMillis())
        )
        return id
    }

    override suspend fun deleteClass(classId: String) {
        studyClassDao.deleteClassById(classId)
    }

    override fun getBooksForClass(classId: String): Flow<List<Book>> {
        return bookDao.getBooksForClass(classId).map { entities ->
            entities.map {
                Book(
                    it.id,
                    it.classId,
                    it.title,
                    it.author,
                    it.fileType,
                    it.filePath,
                    it.totalChapters,
                    it.createdAt
                )
            }
        }
    }

    override suspend fun getBookById(bookId: String): Book? {
        return bookDao.getBookById(bookId)?.let {
            Book(
                it.id,
                it.classId,
                it.title,
                it.author,
                it.fileType,
                it.filePath,
                it.totalChapters,
                it.createdAt
            )
        }
    }

    override suspend fun importBook(
        classId: String,
        title: String,
        author: String,
        fileType: String,
        filePath: String,
        structure: EpubStructureDomainModel
    ): String {
        val bookId = UUID.randomUUID().toString()

        var lastMainChapterTitle: String? = null
        val chaptersList = structure.chapters.mapIndexed { index, parsedCh ->
            val isSub = parsedCh.isSubchapter
            val title = parsedCh.title
            val parent = if (isSub) {
                parsedCh.parentTitle ?: lastMainChapterTitle
            } else {
                lastMainChapterTitle = title
                null
            }
            ChapterEntity(
                id = "${bookId}_ch_${index}",
                bookId = bookId,
                title = title,
                orderIndex = index,
                isSubchapter = isSub,
                parentTitle = parent,
                nestingLevel = parsedCh.nestingLevel
            )
        }

        val sentencesList = structure.sentences.map { parsedSent ->
            SentenceEntity(
                id = "${bookId}_ch_${parsedSent.chapterIndex}_s_${parsedSent.sentenceIndex}",
                bookId = bookId,
                chapterIndex = parsedSent.chapterIndex,
                sentenceIndex = parsedSent.sentenceIndex,
                text = parsedSent.text,
                sectionTitle = parsedSent.sectionTitle
            )
        }

        // Persist structured book directly to Room under a single fast transaction with chunks
        db.withTransaction {
            bookDao.insertBook(
                BookEntity(
                    id = bookId,
                    classId = classId,
                    title = title,
                    author = author.ifEmpty { "Academic Author" },
                    fileType = fileType,
                    filePath = filePath,
                    totalChapters = chaptersList.size,
                    createdAt = System.currentTimeMillis()
                )
            )
            // Use chunks of 500 to avoid SQLite binding/IPC buffer limit exceptions
            chaptersList.chunked(500).forEach { chunk ->
                chapterDao.insertChapters(chunk)
            }
            sentencesList.chunked(500).forEach { chunk ->
                sentenceDao.insertSentences(chunk)
            }
        }

        return bookId
    }

    override suspend fun deleteBook(bookId: String) {
        bookDao.deleteBookById(bookId)
    }

    override fun getChaptersForBook(bookId: String): Flow<List<Chapter>> {
        return chapterDao.getChaptersForBook(bookId).map { entities ->
            entities.map { Chapter(it.id, it.bookId, it.title, it.orderIndex, it.isSubchapter, it.parentTitle, it.nestingLevel) }
        }
    }

    override fun getSentencesForChapter(bookId: String, chapterIndex: Int): Flow<List<Sentence>> {
        return sentenceDao.getSentencesForChapter(bookId, chapterIndex).map { entities ->
            entities.map { Sentence(it.id, it.bookId, it.chapterIndex, it.sentenceIndex, it.text, it.sectionTitle) }
        }
    }

    override fun getNotesForBook(bookId: String): Flow<List<Note>> {
        return noteDao.getNotesForBook(bookId).map { entities ->
            entities.map {
                Note(
                    it.id,
                    it.classId,
                    it.bookId,
                    it.chapterIndex,
                    it.sectionTitle,
                    it.sentenceIndex,
                    it.content,
                    NoteType.valueOf(it.type),
                    it.snippet,
                    it.createdAt
                )
            }
        }
    }

    override fun getNotesForChapter(bookId: String, chapterIndex: Int): Flow<List<Note>> {
        return noteDao.getNotesForChapter(bookId, chapterIndex).map { entities ->
            entities.map {
                Note(
                    it.id,
                    it.classId,
                    it.bookId,
                    it.chapterIndex,
                    it.sectionTitle,
                    it.sentenceIndex,
                    it.content,
                    NoteType.valueOf(it.type),
                    it.snippet,
                    it.createdAt
                )
            }
        }
    }

    override suspend fun addNote(
        classId: String,
        bookId: String,
        chapterIndex: Int,
        sectionTitle: String?,
        sentenceIndex: Int?,
        content: String,
        type: NoteType,
        snippet: String?
    ): String {
        val noteId = UUID.randomUUID().toString()
        noteDao.insertNote(
            NoteEntity(
                id = noteId,
                classId = classId,
                bookId = bookId,
                chapterIndex = chapterIndex,
                sectionTitle = sectionTitle,
                sentenceIndex = sentenceIndex,
                content = content,
                type = type.name,
                snippet = snippet,
                createdAt = System.currentTimeMillis()
            )
        )
        return noteId
    }

    override suspend fun deleteNote(noteId: String) {
        noteDao.deleteNoteById(noteId)
    }

    override suspend fun getRewriteForChapter(bookId: String, chapterIndex: Int): SavedRewrite? {
        return savedRewriteDao.getRewriteForChapter(bookId, chapterIndex)?.let {
            SavedRewrite(it.id, it.bookId, it.chapterIndex, it.prompt, it.rewrittenText, it.createdAt)
        }
    }

    override fun getSavedRewritesForBook(bookId: String): Flow<List<SavedRewrite>> {
        return savedRewriteDao.getSavedRewritesForBook(bookId).map { entities ->
            entities.map {
                SavedRewrite(
                    id = it.id,
                    bookId = it.bookId,
                    chapterIndex = it.chapterIndex,
                    prompt = it.prompt,
                    rewrittenText = it.rewrittenText,
                    createdAt = it.createdAt
                )
            }
        }
    }

    override suspend fun saveRewrite(
        bookId: String,
        chapterIndex: Int,
        prompt: String,
        rewrittenText: String
    ) {
        val id = UUID.randomUUID().toString()
        savedRewriteDao.insertRewrite(
            SavedRewriteEntity(
                id = id,
                bookId = bookId,
                chapterIndex = chapterIndex,
                prompt = prompt,
                rewrittenText = rewrittenText,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    // Helper text generator giving real content when the user selects a blank file or template.
    private fun getDemoBookContent(title: String): String {
        return when {
            title.contains("SQ5R", ignoreCase = true) -> """
                # Chapter 1: The Philosophy of Effortless SQ5R
                ## Surveying the Structure
                SQ5R stands for Survey, Question, Read, Recite, Record, and Review.
                Surveying the structure means active scanning of headings, subheadings, and captions before reading.
                By mapping out chapter layouts, your brain constructs a mental index of upcoming arguments.
                This prevents passive reading, which is the ultimate enemy of comprehension and retention.
                
                ## Questioning and Formulation
                The second phase requires active conversion of subheadings into exploratory questions.
                Ask yourself: what is the core mechanism explained in this paragraph?
                Turning static outlines into dynamic prompts unlocks intentional curiosity and focus.
                These questions act as mental anchor points for your annotations and thoughts.
                
                # Chapter 2: The Reading and Recording Game
                ## Sentence by Sentence Focus
                Deep study requires isolating one concept at a time to prevent sensory overload.
                When reading sentence by sentence, you absorb syntactic nuance and theoretical assertions.
                Dynamic typography variations keeps the locus of attention locked onto the cursor.
                This slow, visual rhythm trains the eyes to see and contextualize critical statements.
                
                ## Record and Review
                Recording involves documenting findings and taking real-time notes within the outline structure.
                Citing exactly which chapter, subheading, and sentence triggered your idea cements it in memory.
                Reviewing should be done in intervals to trigger cognitive recall and consolidate facts.
                Through rewriting complex sentences in simple language, raw information becomes active knowledge.
            """.trimIndent()
            
            title.contains("Data Structures", ignoreCase = true) -> """
                # Chapter 1: Arrays and Linked Lists
                ## Continuous Allocation of Memory
                An array is a data structure storing elements in contiguous memory.
                This contiguous layout allows constant time lookup using exact indices.
                However, insertions require shifting elements, leading to linear complexity.
                This makes arrays efficient for static tables but slow for dynamic queues.
                
                ## Node Links and Dynamic Allocation
                Linked lists decouple elements by storing individual nodes with structural pointers.
                Each node holds a data element and a reference to the succeeding structure.
                Dynamic memory allocation enables list expansion without resizing overhead.
                But linear traversal is obligatory because indexed access is absent.
                
                # Chapter 2: Trees and Hierarchies
                ## Binary Search Tree Traversal
                A binary search tree arranges nodes hierarchically so left children are smaller and right children are larger.
                Retrieving, inserting, and deleting nodes takes logarithmic average time complexity.
                In-order traversal produces sorted collections, showing the inherent neatness of trees.
                Unbalanced trees can degenerate into linear lists, destroying efficiency.
            """.trimIndent()
            
            else -> """
                # Chapter 1: Foundations of the Study Core
                ## Establishing Focus
                Success in study begins with deliberate focus and physical space layout.
                Eliminating peripheral noise enables deeper cognitive tracking of written arguments.
                The Effortless SQ5R framework is a system designed to systematically absorb text.
                Every paragraph contains key sentences that represent atomic nodes of truth.
                
                ## Structural Inspection and Exploration
                Never read a book sequentially without looking at the outline first.
                Browsing the hierarchy reveals the structural skeleton and thematic clusters.
                When you construct outline mental models, the brain prepares appropriate pathways.
                These questions form the foundational grid of high-efficiency study loops.
                
                # Chapter 2: active Reading Tactics
                ## Sentence Visual Rhythm
                By isolating single sentences, we interrupt natural skimming and lazy reading.
                The typography system adjusts families and weights to prevent mental habituation.
                Every sentence becomes a deliberate focal point rather than a blob of gray text.
                Scrolling upward reveals statements one at a time, keeping attention razor-sharp.
                
                ## Synthesis and Iteration
                Synthesizing ideas into targeted annotations forces active translation.
                If you cannot summarize a section, you have not grasped its fundamental driver.
                AI-powered rewriting of dense passages can simplify syntax to build initial schema.
                Revisiting outline notes completes the recall step, forming durable insights.
            """.trimIndent()
        }
    }
}
