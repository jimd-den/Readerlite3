package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ReaderApplication
import com.example.data.gateway.AiGateway
import com.example.ui.util.AppSettings
import com.example.domain.model.*
import com.example.domain.repository.StudyRepository
import com.example.domain.service.TextSanitizer
import com.example.domain.service.TextSanitizerImpl
import com.example.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // 1. Resolve Dependencies
    private val repository: StudyRepository = (application as ReaderApplication).repository
    private val importBookUseCase = (application as ReaderApplication).importBookUseCase
    private val prefs = application.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    // 2. Initialize Pure Kotlin Domain Services & Use Cases
    private val sanitizer: TextSanitizer = TextSanitizerImpl()
    private val loadChapterSentencesUseCase = LoadChapterSentencesUseCase(repository)
    private val loadRewriteUseCase = LoadRewriteUseCase(repository)
    private val navigateSentenceUseCase = NavigateSentenceUseCase()
    private val parseDocumentUseCase = ParseDocumentUseCase((application as ReaderApplication).epubExtractor)
    private val normalizeTextUseCase = NormalizeTextUseCase(sanitizer)

    // 3. Delegate Instantiation of Component-focused Sub-ViewModels under the hood
    private val libraryVM = LibraryViewModel(repository, importBookUseCase)
    private val readerVM = ReaderViewModel(repository, loadChapterSentencesUseCase, navigateSentenceUseCase)
    private val rewriteVM = RewriteViewModel(repository, loadRewriteUseCase, sanitizer)
    private val settingsVM = SettingsViewModel(application)

    // 4. Expose Orchestration StateFlows / SharedFlows (driven by component ViewModels or repository)
    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    // Library State
    val classes: StateFlow<List<StudyClass>> = repository.getAllClasses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedClassId = MutableStateFlow<String?>(null)
    val selectedClassId: StateFlow<String?> = _selectedClassId.asStateFlow()

    private val _selectedBookId = MutableStateFlow<String?>(null)
    val selectedBookId: StateFlow<String?> = _selectedBookId.asStateFlow()

    private val _selectedChapterIndex = MutableStateFlow<Int>(-1)
    val selectedChapterIndex: StateFlow<Int> = _selectedChapterIndex.asStateFlow()

    val books: StateFlow<List<Book>> = _selectedClassId
        .flatMapLatest { classId ->
            if (classId != null) repository.getBooksForClass(classId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chapters: StateFlow<List<Chapter>> = _selectedBookId
        .flatMapLatest { bookId ->
            if (bookId != null) repository.getChaptersForBook(bookId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sentences = MutableStateFlow<List<Sentence>>(emptyList())
    val sentences: StateFlow<List<Sentence>> = _sentences.asStateFlow()

    val notes: StateFlow<List<Note>> = _selectedBookId
        .flatMapLatest { bookId ->
            if (bookId != null) repository.getNotesForBook(bookId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedRewritesForBook: StateFlow<List<SavedRewrite>> = _selectedBookId
        .flatMapLatest { bookId ->
            if (bookId != null) repository.getSavedRewritesForBook(bookId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeClass: StateFlow<StudyClass?> = libraryVM.activeClass
    val activeBook: StateFlow<Book?> = libraryVM.activeBook
    val activeChapter: StateFlow<Chapter?> = readerVM.activeChapter
    val activeSentenceIndex: StateFlow<Int> = readerVM.activeSentenceIndex

    // Importing states
    private val _isBookImporting = MutableStateFlow(false)
    val isBookImporting: StateFlow<Boolean> = _isBookImporting.asStateFlow()

    private val _bookImportStatus = MutableStateFlow("")
    val bookImportStatus: StateFlow<String> = _bookImportStatus.asStateFlow()

    private val _bookImportError = MutableStateFlow<String?>(null)
    val bookImportError: StateFlow<String?> = _bookImportError.asStateFlow()

    // Rewrite states (orchestrated and bridged to RewriteViewModel)
    val activeRewrite: StateFlow<SavedRewrite?> = rewriteVM.activeRewrite
    val isRewriting: StateFlow<Boolean> = rewriteVM.isRewriting
    val rewriteProgress: StateFlow<String?> = rewriteVM.rewriteProgress
    val rewriteError: StateFlow<String?> = rewriteVM.rewriteError

    private val _currentReadingMode = MutableStateFlow("ORIGINAL") // "ORIGINAL" or "REWRITE"
    val currentReadingMode: StateFlow<String> = _currentReadingMode.asStateFlow()

    val rewrittenSentences: StateFlow<List<String>> = rewriteVM.rewrittenSentences

    // Settings / Customization states
    val openRouterKey: StateFlow<String> = settingsVM.openRouterKey
    val openRouterModel: StateFlow<String> = settingsVM.openRouterModel

    private val _openRouterModels = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val openRouterModels: StateFlow<List<Pair<String, String>>> = _openRouterModels.asStateFlow()

    val activeTheme: StateFlow<ColorThemeOption> = settingsVM.activeTheme
    val activeFontName: StateFlow<String> = settingsVM.activeFontName
    val activeFontFamily: StateFlow<FontFamily?> = settingsVM.activeFontFamily

    private val _customProfiles = MutableStateFlow<List<MixProfile>>(emptyList())
    val customProfiles: StateFlow<List<MixProfile>> = _customProfiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow(prefs.getString("active_profile_id", "calm-focus") ?: "calm-focus")
    val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

    private val _activeProfile = MutableStateFlow<MixProfile>(MixProfile.BUILT_IN_PROFILES.first())
    val activeProfile: StateFlow<MixProfile> = _activeProfile.asStateFlow()

    // Wikipedia Recommended curriculum states
    private val _wikiRecommendations = MutableStateFlow<List<WikiRecommendation>>(emptyList())
    val wikiRecommendations: StateFlow<List<WikiRecommendation>> = _wikiRecommendations.asStateFlow()

    private val _isGeneratingWiki = MutableStateFlow(false)
    val isGeneratingWiki: StateFlow<Boolean> = _isGeneratingWiki.asStateFlow()

    private val _wikiError = MutableStateFlow<String?>(null)
    val wikiError: StateFlow<String?> = _wikiError.asStateFlow()

    private val _wikiSourceProvider = MutableStateFlow(AppSettings.getWikiSourceProvider(getApplication()))
    val wikiSourceProvider: StateFlow<String> = _wikiSourceProvider.asStateFlow()

    fun selectWikiSourceProvider(provider: String) {
        _wikiSourceProvider.value = provider.lowercase()
        AppSettings.setWikiSourceProvider(getApplication(), provider.lowercase())
    }

    // Next section / Continuous Reading state flow
    val nextChapter: StateFlow<Chapter?> = _selectedBookId
        .flatMapLatest { bookId ->
            if (bookId == null) flowOf(null)
            else combine(activeChapter, chapters) { active, all ->
                if (active == null || all.isEmpty()) null
                else {
                    val idx = all.indexOfFirst { it.id == active.id }
                    if (idx >= 0 && idx < all.size - 1) all[idx + 1] else null
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        _customProfiles.value = loadCustomProfilesFromPrefs()
        _activeProfile.value = retrieveProfileById(_activeProfileId.value)
        fetchAvailableOpenRouterModels()

        // Sync SharedFlow events from components ViewModels to our primary uiEvent Flow
        viewModelScope.launch {
            rewriteVM.uiEvent.collect { message ->
                _uiEvent.emit(message)
            }
        }

        // Initialize notification channel safely
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val name = "AI Rewrite Updates"
                val descriptionText = "Notifications for AI chapter adaptation completion"
                val importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
                val channel = android.app.NotificationChannel("ai_rewrite_channel", name, importance).apply {
                    description = descriptionText
                }
                val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Auto-seed default Class on start
        viewModelScope.launch {
            repository.getAllClasses().collect { list ->
                if (list.isEmpty()) {
                    repository.createClass(
                        name = "General Academic Class",
                        description = "A default workspace to import your textbook chapters and study notes"
                    )
                } else {
                    if (_selectedClassId.value == null) {
                        _selectedClassId.value = list.first().id
                        libraryVM.selectClass(list.first())
                    }
                }
            }
        }

        // Automatically sync active reading mode with saved rewrite availability on chapter change
        viewModelScope.launch {
            activeRewrite.collect { rewrite ->
                if (rewrite == null) {
                    _currentReadingMode.value = "ORIGINAL"
                } else {
                    _currentReadingMode.value = "REWRITE"
                }
            }
        }
    }

    private fun postRewriteNotification(title: String, text: String) {
        try {
            val context = getApplication<Application>()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val builder = androidx.core.app.NotificationCompat.Builder(context, "ai_rewrite_channel")
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            manager.notify(1002, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Actions ---

    fun selectClass(studyClass: StudyClass) {
        libraryVM.selectClass(studyClass)
        _selectedClassId.value = studyClass.id
        resetActiveReaderSelections()
    }

    fun createClass(name: String, description: String) {
        viewModelScope.launch {
            libraryVM.createClass(name, description)
        }
    }

    fun deleteClass(classId: String) {
        viewModelScope.launch {
            libraryVM.deleteClass(classId)
            if (_selectedClassId.value == classId) {
                _selectedClassId.value = null
                resetActiveReaderSelections()
            }
        }
    }

    fun selectBook(book: Book) {
        libraryVM.selectBook(book)
        _selectedBookId.value = book.id
        resetActiveReaderSelections()
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            libraryVM.deleteBook(bookId)
            if (_selectedBookId.value == bookId) {
                _selectedBookId.value = null
                resetActiveReaderSelections()
            }
        }
    }

    private fun resetActiveReaderSelections() {
        readerVM.clearActiveChapter()
        _selectedChapterIndex.value = -1
        _sentences.value = emptyList()
        _currentReadingMode.value = "ORIGINAL"
    }

    fun clearBookImportError() {
        _bookImportError.value = null
    }

    fun importBook(title: String, author: String, fileType: String, content: String, filePath: String = "", uri: android.net.Uri? = null) {
        val classId = _selectedClassId.value ?: return
        _isBookImporting.value = true
        _bookImportStatus.value = "Starting book processing..."
        _bookImportError.value = null

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    var finalPath = if (filePath.isNotEmpty()) filePath else "assets/$title"
                    var finalContent = content

                    if (uri != null) {
                        if (fileType == "EPUB") {
                            _bookImportStatus.value = "Copying EPUB to local storage..."
                            val booksDir = java.io.File(getApplication<Application>().filesDir, "books")
                            if (!booksDir.exists()) booksDir.mkdirs()
                            val destinationFile = java.io.File(booksDir, "${java.util.UUID.randomUUID()}.epub")
                            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                                destinationFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            finalPath = destinationFile.absolutePath
                            finalContent = ""
                        } else {
                            _bookImportStatus.value = "Reading document..."
                            val parsed = when (fileType) {
                                "PDF" -> {
                                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                                        ?: throw java.io.FileNotFoundException("Could not open PDF stream")
                                    com.example.ui.util.BookParser.parsePdf(getApplication(), stream)
                                }
                                else -> {
                                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                                        stream.bufferedReader(Charsets.UTF_8).readText()
                                    } ?: ""
                                }
                            }
                            finalContent = parsed
                            finalPath = "assets/$title"
                        }
                    }

                    _bookImportStatus.value = "Parsing structure & splitting sentences..."
                    
                    importBookUseCase.execute(
                        classId = classId,
                        title = title,
                        author = author,
                        fileType = fileType,
                        filePath = finalPath,
                        rawContent = finalContent,
                        inputStreamProvider = {
                            if (fileType == "EPUB") {
                                val file = java.io.File(finalPath)
                                if (file.exists() && file.isFile) {
                                    file.inputStream()
                                } else null
                            } else null
                        }
                    )
                }
                _bookImportStatus.value = "Import completed successfully!"
            } catch (e: Exception) {
                e.printStackTrace()
                _bookImportError.value = "Error: " + (e.localizedMessage ?: "Parsing & storing book chapters failed.")
            } finally {
                _isBookImporting.value = false
            }
        }
    }

    fun selectChapter(chapter: Chapter) {
        readerVM.selectChapter(chapter)
        _selectedChapterIndex.value = chapter.orderIndex
        
        viewModelScope.launch {
            // Load and hold original sentences locally for index bounds calculation
            repository.getSentencesForChapter(chapter.bookId, chapter.orderIndex)
                .collectLatest { sList ->
                    _sentences.value = sList
                }
        }

        // Bridge active rewrite loading of RewriteViewModel
        rewriteVM.loadRewriteForChapter(chapter.bookId, chapter.orderIndex)
    }

    fun toggleReadingMode(mode: String) {
        _currentReadingMode.value = mode
        readerVM.selectSentenceIndex(0)
    }

    fun nextSentence() {
        readerVM.navigateToNextSentence()
    }

    fun previousSentence() {
        readerVM.navigateToPreviousSentence()
    }

    fun setSentenceIndex(index: Int) {
        readerVM.selectSentenceIndex(index)
    }

    fun addNote(content: String, type: NoteType) {
        val classId = _selectedClassId.value ?: return
        val bookId = _selectedBookId.value ?: return
        val chapterIdx = _selectedChapterIndex.value
        val sectionTitle = if (chapterIdx >= 0) {
            if (_currentReadingMode.value == "REWRITE") "AI Rewrite Section"
            else _sentences.value.getOrNull(activeSentenceIndex.value)?.sectionTitle ?: "Outline Section"
        } else null

        val currentSentenceTxt = if (chapterIdx >= 0) {
            if (_currentReadingMode.value == "REWRITE") {
                rewrittenSentences.value.getOrNull(activeSentenceIndex.value)
            } else {
                _sentences.value.getOrNull(activeSentenceIndex.value)?.text
            }
        } else null

        viewModelScope.launch {
            repository.addNote(
                classId = classId,
                bookId = bookId,
                chapterIndex = chapterIdx,
                sectionTitle = sectionTitle,
                sentenceIndex = if (chapterIdx >= 0) activeSentenceIndex.value else null,
                content = content,
                type = type,
                snippet = currentSentenceTxt
            )
        }
    }

    fun addOutlineNote(chapterIndex: Int, sectionTitle: String?, content: String, type: NoteType) {
        val classId = _selectedClassId.value ?: return
        val bookId = _selectedBookId.value ?: return
        viewModelScope.launch {
            repository.addNote(
                classId = classId,
                bookId = bookId,
                chapterIndex = chapterIndex,
                sectionTitle = sectionTitle ?: "General Chapter Annotation",
                sentenceIndex = null,
                content = content,
                type = type,
                snippet = null
            )
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
        }
    }

    fun clearRewriteError() {
        rewriteVM.clearRewriteError()
    }

    fun rewriteChapter(
        chapterIndex: Int,
        style: String,
        customPrompt: String? = null,
        forceSimulation: Boolean = false,
        notifyOnComplete: Boolean = true
    ) {
        val bookId = _selectedBookId.value ?: return
        if (chapterIndex < 0) return

        rewriteVM.triggerRewrite(
            bookId = bookId,
            chapterIndex = chapterIndex,
            style = style,
            customPrompt = customPrompt,
            provider = _wikiSourceProvider.value,
            openRouterKey = openRouterKey.value,
            openRouterModel = openRouterModel.value,
            forceSimulation = _wikiSourceProvider.value == "simulation",
            onSuccess = {
                if (_selectedChapterIndex.value == chapterIndex) {
                    _currentReadingMode.value = "REWRITE"
                    readerVM.selectSentenceIndex(0)
                }
                if (notifyOnComplete) {
                    postRewriteNotification(
                        title = "AI Adaptation Complete!",
                        text = "Successfully processed and added chapter ${chapterIndex + 1} to your local AI Library."
                    )
                }
            }
        )
    }

    // --- Settings & Profiles ---

    fun downloadAndSetFont(fontName: String) {
        viewModelScope.launch {
            val success = com.example.ui.util.FontDownloader.downloadGoogleFont(getApplication(), fontName)
            if (success) {
                settingsVM.setFont(fontName)
                android.widget.Toast.makeText(getApplication(), "Font $fontName downloaded successfully!", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(getApplication(), "Failed to download $fontName.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setSystemFont() {
        settingsVM.setFont("System")
        android.widget.Toast.makeText(getApplication(), "System font active", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun saveOpenRouterSettings(key: String, model: String) {
        settingsVM.setOpenRouterKey(key)
        settingsVM.setOpenRouterModel(model)
        android.widget.Toast.makeText(getApplication(), "OpenRouter settings saved successfully!", android.widget.Toast.LENGTH_SHORT).show()
        fetchAvailableOpenRouterModels()
    }

    fun fetchAvailableOpenRouterModels() {
        viewModelScope.launch {
            try {
                val list = AiGateway.fetchOpenRouterModels()
                if (list.isNotEmpty()) {
                    _openRouterModels.value = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setTheme(theme: ColorThemeOption) {
        settingsVM.setTheme(theme)
    }

    private fun retrieveProfileById(id: String): MixProfile {
        val builtIn = MixProfile.BUILT_IN_PROFILES.firstOrNull { it.id == id }
        if (builtIn != null) return builtIn
        val customs = loadCustomProfilesFromPrefs()
        return customs.firstOrNull { it.id == id } ?: MixProfile.BUILT_IN_PROFILES.first()
    }

    fun selectMixProfile(profile: MixProfile) {
        prefs.edit().putString("active_profile_id", profile.id).apply()
        _activeProfileId.value = profile.id
        _activeProfile.value = profile
    }

    fun updateChaos(chaos: Float) {
        val current = _activeProfile.value
        val updated = current.copy(chaosLevel = chaos.coerceIn(0f, 1f))
        _activeProfile.value = updated
    }

    fun updateTempo(tempo: Float) {
        val current = _activeProfile.value
        val updated = current.copy(tempoScale = tempo.coerceIn(0.5f, 2.0f))
        _activeProfile.value = updated
    }

    fun updateSizeScale(size: Float) {
        val current = _activeProfile.value
        val updated = current.copy(sizeScale = size.coerceIn(0.7f, 1.5f))
        _activeProfile.value = updated
    }

    fun updateWeightContrast(weight: Float) {
        val current = _activeProfile.value
        val updated = current.copy(weightContrast = weight.coerceIn(0f, 1f))
        _activeProfile.value = updated
    }

    fun updateOpacityDepth(opacity: Float) {
        val current = _activeProfile.value
        val updated = current.copy(opacityDepth = opacity.coerceIn(0f, 1f))
        _activeProfile.value = updated
    }

    fun resetProfileToDefaults() {
        val current = _activeProfile.value
        val defaultOrOriginal = MixProfile.BUILT_IN_PROFILES.firstOrNull { it.id == current.id }
            ?: MixProfile.BUILT_IN_PROFILES.first()
        _activeProfile.value = defaultOrOriginal
    }

    fun saveAsCustomProfile(name: String) {
        val cleanName = name.trim()
        val id = "custom-${System.currentTimeMillis()}"
        val baseProfile = _activeProfile.value
        val newProfile = MixProfile(
            id = id,
            name = cleanName,
            isBuiltIn = false,
            chaosLevel = baseProfile.chaosLevel,
            tempoScale = baseProfile.tempoScale,
            sizeScale = baseProfile.sizeScale,
            weightContrast = baseProfile.weightContrast,
            opacityDepth = baseProfile.opacityDepth,
            alignmentBias = "mixed",
            allowRightAlign = true,
            animationSet = MixProfile.ACTIVE_CHOREOGRAPHIES,
            reduceMotion = false
        )
        saveCustomProfileToPrefs(newProfile)
        _customProfiles.value = loadCustomProfilesFromPrefs()
        selectMixProfile(newProfile)
        android.widget.Toast.makeText(getApplication(), "Saved custom profile '$cleanName'!", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun loadCustomProfilesFromPrefs(): List<MixProfile> {
        val list = mutableListOf<MixProfile>()
        val keys = prefs.all.keys
        for (key in keys) {
            if (key.startsWith("custom_profile:")) {
                val data = prefs.getString(key, null) ?: continue
                val parts = data.split(";")
                if (parts.size >= 7) {
                    try {
                        list.add(
                            MixProfile(
                                id = parts[0],
                                name = parts[1],
                                isBuiltIn = false,
                                chaosLevel = parts[2].toFloat(),
                                tempoScale = parts[3].toFloat(),
                                sizeScale = parts[4].toFloat(),
                                weightContrast = parts[5].toFloat(),
                                opacityDepth = parts[6].toFloat(),
                                alignmentBias = "mixed",
                                allowRightAlign = true,
                                animationSet = MixProfile.ACTIVE_CHOREOGRAPHIES,
                                reduceMotion = false
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return list
    }

    private fun saveCustomProfileToPrefs(profile: MixProfile) {
        val data = "${profile.id};${profile.name};${profile.chaosLevel};${profile.tempoScale};${profile.sizeScale};${profile.weightContrast};${profile.opacityDepth}"
        prefs.edit().putString("custom_profile:${profile.id}", data).apply()
    }

    fun generateWikiRecommendations(prompt: String) {
        if (prompt.isBlank()) return
        _isGeneratingWiki.value = true
        _wikiError.value = null
        viewModelScope.launch {
            try {
                val provider = _wikiSourceProvider.value
                val openRouterKey = AppSettings.getOpenRouterKey(getApplication())
                val openRouterModel = AppSettings.getOpenRouterModel(getApplication())
                val list = AiGateway.getWikipediaRecommendations(
                    prompt = prompt,
                    provider = provider,
                    openRouterKey = openRouterKey,
                    openRouterModel = openRouterModel
                )
                _wikiRecommendations.value = list
            } catch (e: Exception) {
                e.printStackTrace()
                _wikiError.value = e.localizedMessage ?: "Failed to generate recommendations"
            } finally {
                _isGeneratingWiki.value = false
            }
        }
    }

    fun clearWikiRecommendations() {
        _wikiRecommendations.value = emptyList()
        _wikiError.value = null
    }

    fun downloadWikipediaBook(recommendation: WikiRecommendation) {
        val classId = _selectedClassId.value ?: return
        _isBookImporting.value = true
        _bookImportStatus.value = "Downloading raw Wikipedia text..."
        _bookImportError.value = null

        viewModelScope.launch {
            try {
                // Fetch Wikipedia article page text
                val pageText = AiGateway.fetchWikipediaArticle(recommendation.articleKey)
                if (pageText.isBlank()) {
                    throw Exception("Retrieved page content is empty")
                }
                
                _bookImportStatus.value = "Formatting and parsing structure..."
                val structure = parseWikipediaText(recommendation.title, pageText)
                
                _bookImportStatus.value = "Persisting Wikipedia textbook to class..."
                importBookUseCase.execute(
                    classId = classId,
                    title = recommendation.title,
                    author = "Wikipedia Contributors",
                    fileType = "WIKI",
                    filePath = "wiki/${recommendation.articleKey}",
                    rawContent = "",
                    inputStreamProvider = { null },
                    structureOfWiki = structure
                )
                
                _bookImportStatus.value = "Import completed successfully!"
                _uiEvent.emit("Successfully downloaded and indexed '${recommendation.title}'!")
            } catch (e: Exception) {
                e.printStackTrace()
                _bookImportError.value = "Wikipedia Download Error: " + (e.localizedMessage ?: "Connection failed.")
            } finally {
                _isBookImporting.value = false
            }
        }
    }

    private fun parseWikipediaText(title: String, text: String): EpubStructureDomainModel {
        val chapters = mutableListOf<ParsedChapterDomain>()
        val sentences = mutableListOf<ParsedSentenceDomain>()

        // Chapter 0: Introduction
        chapters.add(ParsedChapterDomain(
            title = "Introduction",
            isSubchapter = false,
            parentTitle = null,
            nestingLevel = 0
        ))

        var currentChapterIndex = 0
        var sentenceIndex = 0
        val lines = text.split("\n")
        var currentSubheading: String? = "Introduction"

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("==") && trimmed.endsWith("==")) {
                val isSub = trimmed.startsWith("===") && trimmed.endsWith("===")
                val headerText = trimmed.replace("=", "").trim()
                if (headerText.isEmpty() || 
                    headerText.equals("References", true) || 
                    headerText.equals("External links", true) || 
                    headerText.equals("See also", true) || 
                    headerText.equals("Further reading", true) || 
                    headerText.equals("Sources", true)
                ) {
                    continue
                }

                chapters.add(ParsedChapterDomain(
                    title = headerText,
                    isSubchapter = isSub,
                    parentTitle = if (isSub) chapters.firstOrNull { !it.isSubchapter }?.title else null,
                    nestingLevel = if (isSub) 1 else 0
                ))
                currentChapterIndex = chapters.lastIndex
                currentSubheading = headerText
                sentenceIndex = 0
            } else {
                val sentenceSplits = trimmed.split(Regex("(?<=[.!?])\\s+"))
                for (sent in sentenceSplits) {
                    val cleanSentTxt = sent.trim()
                    if (cleanSentTxt.isNotEmpty()) {
                        sentences.add(ParsedSentenceDomain(
                            chapterIndex = currentChapterIndex,
                            sentenceIndex = sentenceIndex++,
                            text = cleanSentTxt,
                            sectionTitle = currentSubheading ?: "Introduction"
                        ))
                    }
                }
            }
        }

        return EpubStructureDomainModel(
            title = title,
            author = "Wikipedia Contributors",
            chapters = chapters,
            sentences = sentences
        )
    }

    fun navigateToNextChapter() {
        val nextCh = nextChapter.value ?: return
        selectChapter(nextCh)
    }
}
