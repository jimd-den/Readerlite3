package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Book
import com.example.domain.model.Chapter
import com.example.domain.model.StudyClass
import com.example.domain.repository.StudyRepository
import com.example.domain.usecase.ImportBookUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Split ViewModel handling library features: classes, books, and chapter selection entry points.
 * Conforms to Single Responsibility and Clean Architecture.
 */
class LibraryViewModel(
    private val repository: StudyRepository,
    private val importBookUseCase: ImportBookUseCase
) : ViewModel() {

    private val _classesList = MutableStateFlow<List<StudyClass>>(emptyList())
    val classesList: StateFlow<List<StudyClass>> = _classesList.asStateFlow()

    private val _activeClass = MutableStateFlow<StudyClass?>(null)
    val activeClass: StateFlow<StudyClass?> = _activeClass.asStateFlow()

    private val _booksList = MutableStateFlow<List<Book>>(emptyList())
    val booksList: StateFlow<List<Book>> = _booksList.asStateFlow()

    private val _activeBook = MutableStateFlow<Book?>(null)
    val activeBook: StateFlow<Book?> = _activeBook.asStateFlow()

    private val _chaptersList = MutableStateFlow<List<Chapter>>(emptyList())
    val chaptersList: StateFlow<List<Chapter>> = _chaptersList.asStateFlow()

    init {
        loadClasses()
    }

    fun loadClasses() {
        viewModelScope.launch {
            repository.getAllClasses().collect { list ->
                _classesList.value = list
                if (_activeClass.value == null && list.isNotEmpty()) {
                    _activeClass.value = list.first()
                    loadBooksForClass(list.first().id)
                }
            }
        }
    }

    fun selectClass(cls: StudyClass) {
        _activeClass.value = cls
        loadBooksForClass(cls.id)
    }

    fun createClass(name: String, description: String) {
        viewModelScope.launch {
            repository.createClass(name, description)
            loadClasses()
        }
    }

    fun deleteClass(classId: String) {
        viewModelScope.launch {
            repository.deleteClass(classId)
            loadClasses()
        }
    }

    fun loadBooksForClass(classId: String) {
        viewModelScope.launch {
            repository.getBooksForClass(classId).collect { list ->
                _booksList.value = list
            }
        }
    }

    fun selectBook(book: Book) {
        _activeBook.value = book
        loadChaptersForBook(book.id)
    }

    fun loadChaptersForBook(bookId: String) {
        viewModelScope.launch {
            repository.getChaptersForBook(bookId).collect { chapters ->
                _chaptersList.value = chapters
            }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            repository.deleteBook(bookId)
            _activeBook.value = null
            _chaptersList.value = emptyList()
        }
    }
}
