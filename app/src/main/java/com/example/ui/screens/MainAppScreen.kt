package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.zIndex
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import kotlinx.coroutines.flow.collectLatest
import com.example.domain.model.*
import com.example.ui.theme.TypographyPoster
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.components.KineticCard
import com.example.ui.components.TypographyPlayground
import com.example.ui.components.SimpleFlowRow

sealed class ScreenState {
    object ClassSelect : ScreenState()
    object ClassDetail : ScreenState()
    object ReadingWorkspace : ScreenState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    // Current user flow inside app
    var currentScreen by remember { mutableStateOf<ScreenState>(ScreenState.ClassSelect) }

    // Dialog trigger states
    var showCreateClassDialog by remember { mutableStateOf(false) }
    var showImportBookDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showAddOutlineNoteDialog by remember { mutableStateOf(false) }
    var selectedOutlineChapter by remember { mutableStateOf<Chapter?>(null) }
    var selectedOutlineSection by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Import Dialog State held at top level to support file pre-filling
    var bookTitle by remember { mutableStateOf("") }
    var bookAuthor by remember { mutableStateOf("") }
    var fileType by remember { mutableStateOf("EPUB") } // "EPUB", "PDF", "TXT"
    var textContent by remember { mutableStateOf("") }
    var epubLocalPath by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                selectedFileUri = uri
                // Extract filename as default book title
                var displayName = "Imported Document"
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            displayName = it.getString(nameIndex)
                        }
                    }
                }
                
                val extension = if (displayName.contains(".")) displayName.substringAfterLast(".").uppercase() else "TXT"
                val rawTitleBase = displayName.replace(Regex("\\.[a-zA-Z0-9]+$"), "")
                bookTitle = rawTitleBase
                    .replace("_", " ")
                    .replace("-", " ")
                    .trim()
                    .split(" ")
                    .filter { it.isNotEmpty() }
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                bookAuthor = "Local Importer"
                fileType = if (extension in listOf("EPUB", "PDF", "TXT")) extension else "TXT"
                
                // Track selected file via uri, clear previous texts in preparation for background loading
                textContent = ""
                epubLocalPath = ""
                showImportBookDialog = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Observers
    val isBookImporting by viewModel.isBookImporting.collectAsState()
    val bookImportStatus by viewModel.bookImportStatus.collectAsState()
    val bookImportError by viewModel.bookImportError.collectAsState()

    val classes by viewModel.classes.collectAsState()
    val books by viewModel.books.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val sentences by viewModel.sentences.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val savedRewrites by viewModel.savedRewritesForBook.collectAsState()

    val activeClass by viewModel.activeClass.collectAsState()
    val activeBook by viewModel.activeBook.collectAsState()
    val activeChapter by viewModel.activeChapter.collectAsState()
    val activeSentenceIndex by viewModel.activeSentenceIndex.collectAsState()
    val isRewriting by viewModel.isRewriting.collectAsState()
    val rewriteProgress by viewModel.rewriteProgress.collectAsState()
    val rewriteError by viewModel.rewriteError.collectAsState()
    val activeRewrite by viewModel.activeRewrite.collectAsState()
    val currentReadingMode by viewModel.currentReadingMode.collectAsState()

    val wikiRecommendations by viewModel.wikiRecommendations.collectAsState()
    val isGeneratingWiki by viewModel.isGeneratingWiki.collectAsState()
    val wikiError by viewModel.wikiError.collectAsState()
    val wikiSourceProvider by viewModel.wikiSourceProvider.collectAsState()
    val nextChapter by viewModel.nextChapter.collectAsState()
    val rewrittenSentences by viewModel.rewrittenSentences.collectAsState()

    // Preferences & Engine states
    val activeTheme by viewModel.activeTheme.collectAsState()
    val activeFontName by viewModel.activeFontName.collectAsState()
    val activeFontFamily by viewModel.activeFontFamily.collectAsState()
    val openRouterKey by viewModel.openRouterKey.collectAsState()
    val openRouterModel by viewModel.openRouterModel.collectAsState()
    val openRouterModels by viewModel.openRouterModels.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val customProfiles by viewModel.customProfiles.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCalibratorPanel by remember { mutableStateOf(false) }

    // Screen configuration for Adaptive Layouts
    val config = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (activeClass != null) activeClass!!.name else "Effortless SQ5R",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (activeBook != null) {
                            Text(
                                text = activeBook!!.title,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (currentScreen != ScreenState.ClassSelect) {
                        IconButton(
                            onClick = {
                                when (currentScreen) {
                                    ScreenState.ReadingWorkspace -> currentScreen = ScreenState.ClassDetail
                                    ScreenState.ClassDetail -> currentScreen = ScreenState.ClassSelect
                                    else -> {}
                                }
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "App Logo",
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (currentScreen == ScreenState.ClassSelect) {
                        IconButton(
                            onClick = { showCreateClassDialog = true },
                            modifier = Modifier.testTag("create_class_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create Class")
                        }
                    } else if (currentScreen == ScreenState.ClassDetail) {
                        IconButton(
                            onClick = {
                                bookTitle = ""
                                bookAuthor = ""
                                fileType = "TXT"
                                textContent = ""
                                showImportBookDialog = true
                            },
                            modifier = Modifier.testTag("import_book_button")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Import Material")
                        }
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("global_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // Main Transition Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Floating background upload / process cue
            AnimatedVisibility(
                visible = isBookImporting,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .zIndex(10f)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Processing Reading Material...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = bookImportStatus,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // Floating error alert banner
            AnimatedVisibility(
                visible = bookImportError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .zIndex(10f)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Import Error Icon",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Import Failed",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = bookImportError ?: "",
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearBookImportError() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            when (currentScreen) {
                is ScreenState.ClassSelect -> {
                    ClassDashboardScreen(
                        classes = classes,
                        onCreateClassClick = { showCreateClassDialog = true },
                        onClassSelected = { studyClass ->
                            viewModel.selectClass(studyClass)
                            currentScreen = ScreenState.ClassDetail
                        },
                        onDeleteClass = { id -> viewModel.deleteClass(id) }
                    )
                }
                is ScreenState.ClassDetail -> {
                    ClassWorkspaceScreen(
                        books = books,
                        wikiRecommendations = wikiRecommendations,
                        isGeneratingWiki = isGeneratingWiki,
                        wikiError = wikiError,
                        wikiSourceProvider = wikiSourceProvider,
                        onSelectWikiProvider = { provider -> viewModel.selectWikiSourceProvider(provider) },
                        onGenerateRecommendations = { prompt -> viewModel.generateWikiRecommendations(prompt) },
                        onDownloadBook = { rec -> viewModel.downloadWikipediaBook(rec) },
                        onClearRecommendations = { viewModel.clearWikiRecommendations() },
                        onAddBookClick = {
                            bookTitle = ""
                            bookAuthor = ""
                            fileType = "TXT"
                            textContent = ""
                            showImportBookDialog = true
                        },
                        onBookSelected = { book ->
                            viewModel.selectBook(book)
                            currentScreen = ScreenState.ReadingWorkspace
                        },
                        onDeleteBook = { id -> viewModel.deleteBook(id) }
                    )
                }
                is ScreenState.ReadingWorkspace -> {
                    ReadingWorkspaceScreen(
                        chapters = chapters,
                        sentences = if (currentReadingMode == "REWRITE") rewrittenSentences.mapIndexed { idx, s -> Sentence(idx.toString(), "", 0, idx, s, "AI Rewrite") } else sentences,
                        notes = notes,
                        activeChapter = activeChapter,
                        activeSentenceIndex = activeSentenceIndex,
                        isRewriting = isRewriting,
                        activeRewrite = activeRewrite,
                        currentReadingMode = currentReadingMode,
                        savedRewrites = savedRewrites,
                        isTablet = isTablet,
                        profile = activeProfile,
                        customFontFamily = activeFontFamily,
                        customProfiles = customProfiles,
                        nextChapter = nextChapter,
                        onStartNextSection = { viewModel.navigateToNextChapter() },
                        onChapterSelected = { ch -> viewModel.selectChapter(ch) },
                        onPreviousSentence = { viewModel.previousSentence() },
                        onNextSentence = { viewModel.nextSentence() },
                        onSentenceSelected = { idx -> viewModel.setSentenceIndex(idx) },
                        onAddNoteClick = { showAddNoteDialog = true },
                        onAddOutlineNoteClick = { chapter, section ->
                            selectedOutlineChapter = chapter
                            selectedOutlineSection = section
                            showAddOutlineNoteDialog = true
                        },
                        onDeleteNote = { id -> viewModel.deleteNote(id) },
                        onToggleMode = { mode -> viewModel.toggleReadingMode(mode) },
                        onAIStyleSelected = { chapterIndex, style, prompt, simulate, notify ->
                            viewModel.rewriteChapter(chapterIndex, style, prompt, simulate, notify)
                        },
                        onProfileSelected = { viewModel.selectMixProfile(it) },
                        onSaveProfile = { viewModel.saveAsCustomProfile(it) },
                        onResetDefaults = { viewModel.resetProfileToDefaults() },
                        onChaosChanged = { viewModel.updateChaos(it) },
                        onTempoChanged = { viewModel.updateTempo(it) },
                        onSizeChanged = { viewModel.updateSizeScale(it) },
                        onWeightChanged = { viewModel.updateWeightContrast(it) },
                        onOpacityChanged = { viewModel.updateOpacityDepth(it) }
                    )
                }
            }
        }
    }

    // Modal Dialog: Reader preferences configuration
    if (showSettingsDialog) {
        GlobalSettingsDialog(
            openRouterKey = openRouterKey,
            openRouterModel = openRouterModel,
            openRouterModels = openRouterModels,
            activeTheme = activeTheme,
            activeFontName = activeFontName,
            onSaveOpenRouter = { key, model -> viewModel.saveOpenRouterSettings(key, model) },
            onSetTheme = { theme -> viewModel.setTheme(theme) },
            onDownloadFont = { name -> viewModel.downloadAndSetFont(name) },
            onSetSystemFont = { viewModel.setSystemFont() },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Modal Dialog: Create Class
    if (showCreateClassDialog) {
        var className by remember { mutableStateOf("") }
        var classDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateClassDialog = false },
            title = { Text("Start a Class Workspace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = className,
                        onValueChange = { className = it },
                        label = { Text("Class Name (e.g. Physics)") },
                        modifier = Modifier.fillMaxWidth().testTag("class_name_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = classDesc,
                        onValueChange = { classDesc = it },
                        label = { Text("Topic Description / Syllabus") },
                        modifier = Modifier.fillMaxWidth().testTag("class_desc_input"),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (className.isNotBlank()) {
                            viewModel.createClass(className, classDesc)
                            showCreateClassDialog = false
                        }
                    },
                    modifier = Modifier.testTag("submit_class")
                ) {
                    Text("Start Class")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateClassDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }

    // Modal Dialog: Import Book
    if (showImportBookDialog) {
        AlertDialog(
            onDismissRequest = { showImportBookDialog = false },
            title = { Text("Import Reading Material") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = bookTitle,
                        onValueChange = { bookTitle = it },
                        label = { Text("Document Title") },
                        modifier = Modifier.fillMaxWidth().testTag("book_title_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = bookAuthor,
                        onValueChange = { bookAuthor = it },
                        label = { Text("Author") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("FORMAT TYPE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("EPUB", "PDF", "TXT").forEach { format ->
                            FilterChip(
                                selected = fileType == format,
                                onClick = { fileType = format },
                                label = { Text(format) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("LOAD FILE DIRECTLY:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        TextButton(
                            onClick = { 
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "text/plain", 
                                        "application/pdf", 
                                        "application/epub+zip", 
                                        "application/octet-stream"
                                    )
                                ) 
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select Document (.txt, .pdf, .epub)", fontSize = 11.sp)
                        }
                    }

                    if (fileType == "EPUB") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "📖 EPUB Packaged Document\nReady for database metadata extraction & structuring.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = textContent,
                            onValueChange = { textContent = it },
                            label = { Text("Write/Paste Content (Optional)") },
                            placeholder = { Text("Leave blank to autopopulate the textbook section for demo study!") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            maxLines = 8
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (bookTitle.isNotBlank()) {
                            viewModel.importBook(
                                title = bookTitle,
                                author = bookAuthor,
                                fileType = fileType,
                                content = textContent,
                                filePath = epubLocalPath,
                                uri = selectedFileUri
                            )
                            showImportBookDialog = false
                        }
                    },
                    modifier = Modifier.testTag("submit_book")
                ) {
                    Text("Load & Parse")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportBookDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Dialog: Add Note
    if (showAddNoteDialog) {
        var noteText by remember { mutableStateOf("") }
        var isQuestion by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = { Text(if (isQuestion) "Ask a Guiding Question (SQ)" else "Record active Insight (R)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Response Link:")
                        FilterChip(
                            selected = !isQuestion,
                            onClick = { isQuestion = false },
                            label = { Text("Study Note") }
                        )
                        FilterChip(
                            selected = isQuestion,
                            onClick = { isQuestion = true },
                            label = { Text("Syllabus Question") }
                        )
                    }

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text(if (isQuestion) "What is the core mystery explained here?" else "Record study insights, citations, summaries...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("note_input_field"),
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            viewModel.addNote(
                                content = noteText,
                                type = if (isQuestion) NoteType.QUESTION else NoteType.NOTE
                            )
                            showAddNoteDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_note_button")
                ) {
                    Text("Attach Annotation")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNoteDialog = false }) {
                    Text("Back")
                }
            }
        )
    }

    // Modal Dialog: Add Note/Question to specific Outline Section
    if (showAddOutlineNoteDialog) {
        var noteText by remember { mutableStateOf("") }
        var isQuestion by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddOutlineNoteDialog = false },
            title = { Text("Annotate Heading: ${selectedOutlineSection ?: "Chapter"}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !isQuestion,
                            onClick = { isQuestion = false },
                            label = { Text("Note") }
                        )
                        FilterChip(
                            selected = isQuestion,
                            onClick = { isQuestion = true },
                            label = { Text("Question") }
                        )
                    }

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text(if (isQuestion) "What question does this section explore?" else "Summarize outline segment...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteText.isNotBlank() && selectedOutlineChapter != null) {
                            viewModel.addOutlineNote(
                                chapterIndex = selectedOutlineChapter!!.orderIndex,
                                sectionTitle = selectedOutlineSection,
                                content = noteText,
                                type = if (isQuestion) NoteType.QUESTION else NoteType.NOTE
                            )
                            showAddOutlineNoteDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddOutlineNoteDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Error Alert Dialog for Rewrite failures
    if (rewriteError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearRewriteError() },
            icon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("AI Adaptation Failed") },
            text = {
                Column {
                    Text(
                        text = "An error occurred while communicating with the AI gateway:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = rewriteError ?: "Unknown Error",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.clearRewriteError() }) {
                    Text("Dismiss")
                }
            }
        )
    }

    // Rewrite generator loading screen with real-time progress
    if (isRewriting) {
        Dialog(onDismissRequest = {}) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AI Adaptation in Progress", 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = rewriteProgress ?: "Processing chapter syntax...", 
                        fontSize = 13.sp, 
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------------- SUBVIEW 1: Class Dashboard ----------------
@Composable
fun ClassDashboardScreen(
    classes: List<StudyClass>,
    onCreateClassClick: () -> Unit,
    onClassSelected: (StudyClass) -> Unit,
    onDeleteClass: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Welcome Poster
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF381E72), Color(0xFF1D1B20))
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "Welcome to",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Effortless SQ5R",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Unlock active study. Survey outlines, ask questions, read sentence-by-sentence, and record milestones.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "YOUR CLASSES",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(
                onClick = onCreateClassClick,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Class")
            }
        }

        if (classes.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No study classes yet",
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Create a class and upload textbook materials to start.",
                        fontSize = 11.sp,
                        color = Color.Gray.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(classes) { studyClass ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClassSelected(studyClass) }
                            .testTag("class_card_${studyClass.name}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = studyClass.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = studyClass.description.ifEmpty { "General academic track" },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onDeleteClass(studyClass.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Class",
                                    tint = Color.Red.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- SUBVIEW 2: Class Detail / Books Workspace ----------------
@Composable
fun ClassWorkspaceScreen(
    books: List<Book>,
    wikiRecommendations: List<WikiRecommendation>,
    isGeneratingWiki: Boolean,
    wikiError: String?,
    wikiSourceProvider: String,
    onSelectWikiProvider: (String) -> Unit,
    onGenerateRecommendations: (String) -> Unit,
    onDownloadBook: (WikiRecommendation) -> Unit,
    onClearRecommendations: () -> Unit,
    onAddBookClick: () -> Unit,
    onBookSelected: (Book) -> Unit,
    onDeleteBook: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STUDY MATERIALS",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Button(
                onClick = onAddBookClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Document", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (books.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Class Workspace empty",
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Upload academic PDFs, EPUBs, or notes. We automatically parse structure and outline.",
                        fontSize = 10.sp,
                        color = Color.Gray.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(0.5f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(books) { book ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBookSelected(book) }
                            .testTag("book_card_${book.title}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = book.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = book.fileType.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Text(
                                        text = book.author,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onDeleteBook(book.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Book",
                                    tint = Color.Red.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Wikipedia Course Guide / Assistant Section ---
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Wikipedia Lesson Builder",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Request Wikipedia article recommendations for this class and index them directly as course books.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // AI Service Provider Choice Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI Engine:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )

                    listOf("gemini" to "Direct Gemini", "openrouter" to "OpenRouter", "simulation" to "Demo Sim").forEach { (id, label) ->
                        val isSelected = wikiSourceProvider.lowercase() == id

                        val containerCol = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        }
                        val contentCol = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val borderCol = if (isSelected) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }

                        Surface(
                            onClick = { onSelectWikiProvider(id) },
                            shape = RoundedCornerShape(8.dp),
                            color = containerCol,
                            contentColor = contentCol,
                            border = BorderStroke(1.dp, borderCol),
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    val icon = when (id) {
                                        "gemini" -> Icons.Default.AutoAwesome
                                        "openrouter" -> Icons.Default.Cloud
                                        else -> Icons.Default.Bolt
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                var promptText by remember { mutableStateOf("") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        placeholder = { Text("E.g., Quantum Mechanics, Space, Neuroscience...", fontSize = 11.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (promptText.isNotBlank()) {
                                onGenerateRecommendations(promptText)
                            }
                        },
                        enabled = !isGeneratingWiki && promptText.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        if (isGeneratingWiki) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Guide Me", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (wikiError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error notification icon",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AI Service Error",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = wikiError ?: "An unknown exception occurred.",
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (wikiRecommendations.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECOMMENDED ARTICLES",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = onClearRecommendations,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Clear", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(wikiRecommendations) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.description,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 13.sp
                                        )
                                    }

                                    Button(
                                        onClick = { onDownloadBook(item) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Index", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active curriculum. Request above to plan your study.",
                            color = Color.Gray.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ---------------- SUBVIEW 3: Reading Workspace (Outline, Reader, Notes Split Panel) ----------------
@Composable
fun ReadingWorkspaceScreen(
    chapters: List<Chapter>,
    sentences: List<Sentence>,
    notes: List<Note>,
    activeChapter: Chapter?,
    activeSentenceIndex: Int,
    isRewriting: Boolean,
    activeRewrite: SavedRewrite?,
    currentReadingMode: String,
    savedRewrites: List<SavedRewrite>,
    isTablet: Boolean,
    profile: MixProfile,
    customFontFamily: FontFamily?,
    customProfiles: List<MixProfile>,
    nextChapter: Chapter?,
    onStartNextSection: () -> Unit,
    onChapterSelected: (Chapter) -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onSentenceSelected: (Int) -> Unit,
    onAddNoteClick: () -> Unit,
    onAddOutlineNoteClick: (Chapter, String?) -> Unit,
    onDeleteNote: (String) -> Unit,
    onToggleMode: (String) -> Unit,
    onAIStyleSelected: (Int, String, String?, Boolean, Boolean) -> Unit,
    
    // Engine interactions
    onProfileSelected: (MixProfile) -> Unit,
    onSaveProfile: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onChaosChanged: (Float) -> Unit,
    onTempoChanged: (Float) -> Unit,
    onSizeChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit
) {
    var workspaceTab by remember { mutableStateOf(0) } // 0 = SURVEY OUTLINE, 1 = READ SCRIPT, 2 = NOTE BOARD

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Navigation
        TabRow(
            selectedTabIndex = workspaceTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = workspaceTab == 0,
                onClick = { workspaceTab = 0 },
                text = { Text("1. SURVEY", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = workspaceTab == 1,
                onClick = { workspaceTab = 1 },
                text = { Text("2. READ", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = workspaceTab == 2,
                onClick = { workspaceTab = 2 },
                text = { Text("3. REVIEW", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = workspaceTab == 3,
                onClick = { workspaceTab = 3 },
                text = { Text("4. AI LIBRARY", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (workspaceTab) {
                0 -> {
                    // SURVEY: Table of contents outline showing chapters, questions, and note counts
                    SurveyOutlinePane(
                        chapters = chapters,
                        notes = notes,
                        activeChapter = activeChapter,
                        onChapterSelected = { ch ->
                            onChapterSelected(ch)
                            workspaceTab = 1 // Instantly switch to reading view
                        },
                        onAddNoteClick = onAddOutlineNoteClick,
                        onAIStyleSelected = onAIStyleSelected,
                        isRewriting = isRewriting
                    )
                }
                1 -> {
                    // READ: Sentence by sentence focused view with typography poster integration
                    ReadSentencePane(
                        sentences = sentences,
                        activeSentenceIndex = activeSentenceIndex,
                        activeRewrite = activeRewrite,
                        currentReadingMode = currentReadingMode,
                        profile = profile,
                        customFontFamily = customFontFamily,
                        customProfiles = customProfiles,
                        nextChapter = nextChapter,
                        onStartNextSection = onStartNextSection,
                        onPrevious = onPreviousSentence,
                        onNext = onNextSentence,
                        onSentenceSelected = onSentenceSelected,
                        onAddNote = onAddNoteClick,
                        onToggleMode = onToggleMode,
                        onProfileSelected = onProfileSelected,
                        onSaveProfile = onSaveProfile,
                        onResetDefaults = onResetDefaults,
                        onChaosChanged = onChaosChanged,
                        onTempoChanged = onTempoChanged,
                        onSizeChanged = onSizeChanged,
                        onWeightChanged = onWeightChanged,
                        onOpacityChanged = onOpacityChanged
                    )
                }
                2 -> {
                    // REVIEW: The historical board displaying study notes grouped neatly
                    ReviewNotesPane(
                        notes = notes,
                        onDeleteNote = onDeleteNote
                    )
                }
                3 -> {
                    // AI LIBRARY: Exhibiting saved translation versions
                    AiLibraryPane(
                        savedRewrites = savedRewrites,
                        chapters = chapters
                    )
                }
            }
        }
    }
}

// 1. Survey Outline Pane Composable
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SurveyOutlinePane(
    chapters: List<Chapter>,
    notes: List<Note>,
    activeChapter: Chapter?,
    onChapterSelected: (Chapter) -> Unit,
    onAddNoteClick: (Chapter, String?) -> Unit,
    onAIStyleSelected: (Int, String, String?, Boolean, Boolean) -> Unit,
    isRewriting: Boolean
) {
    var expandedChapterIndex by remember { mutableStateOf(-1) }
    var showRewriteDialog by remember { mutableStateOf(false) }
    var targetRewriteChapter by remember { mutableStateOf<Chapter?>(null) }

    val chapterNumbers = remember(chapters) {
        var currentMainChapterNum = 0
        chapters.map { ch ->
            if (!ch.isSubchapter) {
                currentMainChapterNum += 1
                currentMainChapterNum
            } else {
                0
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "💡 SURVEY STEP: Look at the chapters and subheadings below. Formulate mental questions before reading to prime your brain.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        itemsIndexed(chapters) { idx, chapter ->
            val isActive = activeChapter?.id == chapter.id
            val chapterNotes = notes.filter { it.chapterIndex == chapter.orderIndex }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (chapter.nestingLevel * 16).dp)
                    .animateContentSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isActive) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = 1.dp,
                            color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            expandedChapterIndex = if (expandedChapterIndex == idx) -1 else idx
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (chapter.isSubchapter) {
                                if (chapter.parentTitle != null) "Subchapter under ${chapter.parentTitle}" else "Subchapter"
                            } else {
                                "Chapter ${chapterNumbers.getOrElse(idx) { chapter.orderIndex + 1 }}"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (chapter.isSubchapter) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = chapter.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Indicators
                        if (chapterNotes.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${chapterNotes.size} NOTES",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Icon(
                            imageVector = if (expandedChapterIndex == idx) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand chapter outline",
                            tint = Color.Gray
                        )
                    }
                }

                // Expanded Section details
                if (expandedChapterIndex == idx) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "OUTLINE SUBHEADINGS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // We can display the unique action items
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onChapterSelected(chapter) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Read", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        targetRewriteChapter = chapter
                                        showRewriteDialog = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("AI Rewrite", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Quick outline annotations
                            Text(
                                text = "Add localized chapter target / question:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(onClick = { onAddNoteClick(chapter, "Introduction Outline") }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Note", fontSize = 11.sp)
                                }
                                TextButton(onClick = { onAddNoteClick(chapter, "Review Prompts") }) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Question", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRewriteDialog) {
        var customPrompt by remember { mutableStateOf("") }
        var notifyOnComplete by remember { mutableStateOf(true) }
        var forceSimulation by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showRewriteDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("AI Chapter Adaptation", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Customize how the AI rewrites and structures this chapter. Enter your custom prompt instructions below, or click any quick template to pre-fill the text.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Quick select styles / templates
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Elementary", "Socratic", "Summary").forEach { label ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable {
                                        customPrompt = when (label) {
                                            "Elementary" -> "Translate this chapter into extremely simple words suitable for elementary school students. Use analogies where possible."
                                            "Socratic" -> "Formulate this text as a Socratic dialogue. Turn primary definitions into logical headings, and ask deep questions about core concepts."
                                            else -> "Create an actionable summary of the chapter. Extract key definitions and put them in bold bullet points."
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Multi-line TextField
                    OutlinedTextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp),
                        placeholder = { Text("E.g., Simplify explanations, rewrite in Spanish, etc...", fontSize = 13.sp) },
                        label = { Text("Custom Prompt (Optional)") },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        maxLines = 4
                    )

                    // Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Notify me when completed", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = notifyOnComplete,
                            onCheckedChange = { notifyOnComplete = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Demo Simulation", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("Generate offline summary locally if no Gemini Key", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = forceSimulation,
                            onCheckedChange = { forceSimulation = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chapIndex = targetRewriteChapter?.orderIndex ?: -1
                        if (chapIndex >= 0) {
                            val defaultStyle = "High Hooking Action"
                            onAIStyleSelected(chapIndex, defaultStyle, customPrompt.ifBlank { null }, forceSimulation, notifyOnComplete)
                        }
                        showRewriteDialog = false
                    },
                    modifier = Modifier.testTag("submit_rewrite_button")
                ) {
                    Text("Adapt Chapter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRewriteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// 2. Read Sentence Focused Pane Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadSentencePane(
    sentences: List<Sentence>,
    activeSentenceIndex: Int,
    activeRewrite: SavedRewrite?,
    currentReadingMode: String,
    profile: MixProfile,
    customFontFamily: FontFamily?,
    customProfiles: List<MixProfile>,
    nextChapter: Chapter?,
    onStartNextSection: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSentenceSelected: (Int) -> Unit,
    onAddNote: () -> Unit,
    onToggleMode: (String) -> Unit,
    
    // Engine interactions
    onProfileSelected: (MixProfile) -> Unit,
    onSaveProfile: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onChaosChanged: (Float) -> Unit,
    onTempoChanged: (Float) -> Unit,
    onSizeChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit
) {
    if (sentences.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select a chapter in the 'SURVEY' tab to begin reading structure.", textAlign = TextAlign.Center, color = Color.Gray)
            }
        }
        return
    }

    val currentSentenceObj = sentences.getOrNull(activeSentenceIndex)
    val totalSentences = sentences.size

    val originalText = currentSentenceObj?.text ?: "No active text"
    val activeSubheadingText = currentSentenceObj?.sectionTitle ?: "Introductory Reading"

    var showCalibrator by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Toggle Panel between Original & Rewrite when rewrite exists
            if (activeRewrite != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { onToggleMode("ORIGINAL") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentReadingMode == "ORIGINAL") MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (currentReadingMode == "ORIGINAL") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Original Text", fontSize = 11.sp)
                    }

                    Button(
                        onClick = { onToggleMode("REWRITE") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentReadingMode == "REWRITE") MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (currentReadingMode == "REWRITE") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("AI Rewrite", fontSize = 11.sp)
                    }
                }
            }

            // Reading view scroll deck (Prior context indicator)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Secondary preceding context
                    if (activeSentenceIndex > 0) {
                        Text(
                            text = sentences.getOrNull(activeSentenceIndex - 1)?.text ?: "",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clickable { onPrevious() }
                        )
                    }

                    // Active text highlighted with dynamic kinetic poster styling
                    KineticCard(
                        sentenceId = "${currentSentenceObj?.id ?: activeSentenceIndex}_${currentReadingMode}",
                        sentenceText = originalText,
                        subheading = activeSubheadingText,
                        index = activeSentenceIndex,
                        profile = profile,
                        userFontSize = 24f, // Dynamic high design size
                        customFontFamily = customFontFamily,
                        modifier = Modifier.testTag("focused_sentence_poster")
                    )

                    // Secondary succeeding context
                    if (activeSentenceIndex < totalSentences - 1) {
                        Text(
                            text = sentences.getOrNull(activeSentenceIndex + 1)?.text ?: "",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clickable { onNext() }
                        )
                    } else if (nextChapter != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStartNextSection() }
                                .padding(top = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Ready to continue? Start: ${nextChapter.title}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Bottom controller buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Slider / Indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${activeSentenceIndex + 1} / $totalSentences",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LinearProgressIndicator(
                        progress = { (activeSentenceIndex + 1).toFloat() / totalSentences.toFloat() },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }

                // Quick Actions & Navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Return / Prev
                    FilledIconButton(
                        onClick = onPrevious,
                        modifier = Modifier.size(50.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Prev Sentence",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Capture Annotation Button
                    Button(
                        onClick = onAddNote,
                        modifier = Modifier
                            .height(50.dp)
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                            .testTag("add_note_fab"),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Annotate", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    // Calibrator Tuner Toggle Icon
                    FilledIconButton(
                        onClick = { showCalibrator = !showCalibrator },
                        modifier = Modifier.size(50.dp).padding(end = 4.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (showCalibrator) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Calibrate Engine",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Forward / Next
                    FilledIconButton(
                        onClick = onNext,
                        modifier = Modifier.size(50.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Next Sentence",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Calibrator sliding panel overlay
        if (showCalibrator) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showCalibrator = false }
            )
            TypographyPlayground(
                activeProfile = profile,
                customProfiles = customProfiles,
                onProfileSelected = onProfileSelected,
                onSaveProfile = onSaveProfile,
                onResetDefaults = onResetDefaults,
                onChaosChanged = onChaosChanged,
                onTempoChanged = onTempoChanged,
                onSizeChanged = onSizeChanged,
                onWeightChanged = onWeightChanged,
                onOpacityChanged = onOpacityChanged,
                onClose = { showCalibrator = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clickable(enabled = false) {} // block click through
            )
        }
    }
}

// 2.5 Curator Font Catalog & Settings Dialog Composable
data class FontCatalogItem(
    val name: String,
    val category: String,
    val fallbackFamily: FontFamily,
    val description: String
)

val PREBUILT_FONTS = listOf(
    FontCatalogItem("Space Grotesk", "Sans-Serif", FontFamily.SansSerif, "Geometric display sans"),
    FontCatalogItem("Cinzel", "Display", FontFamily.Serif, "Classical Roman elegance"),
    FontCatalogItem("Playfair Display", "Serif", FontFamily.Serif, "High-contrast dynamic literary"),
    FontCatalogItem("Lora", "Serif", FontFamily.Serif, "Contemporary calligraphic reading"),
    FontCatalogItem("Merriweather", "Serif", FontFamily.Serif, "Engraved text readability serif"),
    FontCatalogItem("Montserrat", "Sans-Serif", FontFamily.SansSerif, "Urban geometric symmetry"),
    FontCatalogItem("Inter", "Sans-Serif", FontFamily.SansSerif, "Premium interface legibility"),
    FontCatalogItem("JetBrains Mono", "Monospace", FontFamily.Monospace, "Optimized structural code layout"),
    FontCatalogItem("Fira Code", "Monospace", FontFamily.Monospace, "Code structure with ligatures"),
    FontCatalogItem("Caveat", "Handwriting", FontFamily.Cursive, "Natural script brushstrokes"),
    FontCatalogItem("Pacifico", "Handwriting", FontFamily.Cursive, "Retro script bold cursive"),
    FontCatalogItem("Dancing Script", "Handwriting", FontFamily.Cursive, "Fluid dancing calligraphic loops"),
    FontCatalogItem("Lobster", "Display", FontFamily.Cursive, "Vintage heavy display script"),
    FontCatalogItem("Bebas Neue", "Display", FontFamily.SansSerif, "Condensed bold impact heading"),
    FontCatalogItem("Righteous", "Display", FontFamily.SansSerif, "Futuristic bubble art deco")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsDialog(
    openRouterKey: String,
    openRouterModel: String,
    openRouterModels: List<Pair<String, String>>,
    activeTheme: ColorThemeOption,
    activeFontName: String,
    onSaveOpenRouter: (String, String) -> Unit,
    onSetTheme: (ColorThemeOption) -> Unit,
    onDownloadFont: (String) -> Unit,
    onSetSystemFont: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(openRouterKey) }
    var selectedModelInput by remember { mutableStateOf(openRouterModel) }
    var fontNameInput by remember { mutableStateOf("") }
    var showModelDropdown by remember { mutableStateOf(false) }

    // Search and category tracking inside typography explorer
    var fontQuery by remember { mutableStateOf("") }
    var fontCategoryTab by remember { mutableStateOf("All") }
    val context = LocalContext.current
    
    var downloadedMap by remember { mutableStateOf(emptyMap<String, Boolean>()) }

    // Update download map when activeFontName updates or dialog loads
    LaunchedEffect(activeFontName) {
        val map = PREBUILT_FONTS.associate { it.name to com.example.ui.util.FontDownloader.isFontDownloaded(context, it.name) }
        downloadedMap = map
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "READER PREFERENCES",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Color Themes
                Column {
                    Text(
                        text = "1. SENSORY COLOR PALETTE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    SimpleFlowRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ColorThemeOption.values().forEach { option ->
                            val isSelected = option == activeTheme
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { onSetTheme(option) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = option.name.replace("_", " "),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Section 2: Curated Google Fonts Directory & Explorer
                Column {
                    Text(
                        text = "2. DYNAMIC GOOGLE FONTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Browse, search, and preview Google Fonts categorized by style. Tap any item to download & apply dynamically.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Text Query Search Bar
                    OutlinedTextField(
                        value = fontQuery,
                        onValueChange = { fontQuery = it },
                        placeholder = { Text("Search curated fonts (e.g. Lora, Inter...)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                        trailingIcon = {
                            if (fontQuery.isNotEmpty()) {
                                IconButton(onClick = { fontQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category Filter Scroll Row
                    val fontCategories = listOf("All", "Serif", "Sans-Serif", "Monospace", "Handwriting", "Display")
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(fontCategories) { cat ->
                            val isCatSelected = fontCategoryTab == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isCatSelected) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { fontCategoryTab = cat }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCatSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val filteredCatalog = PREBUILT_FONTS.filter { font ->
                        val matchesQuery = font.name.contains(fontQuery, ignoreCase = true) || font.description.contains(fontQuery, ignoreCase = true)
                        val matchesCategory = fontCategoryTab == "All" || font.category.equals(fontCategoryTab, ignoreCase = true)
                        matchesQuery && matchesCategory
                    }

                    // Constrained scrollable catalog grid box
                    Box(
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                            .padding(4.dp)
                    ) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (filteredCatalog.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No matching fonts found offline", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }

                            items(filteredCatalog) { font ->
                                val isFontActive = font.name.equals(activeFontName, ignoreCase = true)
                                val isInstalled = downloadedMap[font.name] ?: false

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onDownloadFont(font.name) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isFontActive) MaterialTheme.colorScheme.primaryContainer 
                                                         else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isFontActive) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = font.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = font.category.uppercase(),
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                            }

                                            // Visual dynamic type preview based on categories
                                            Text(
                                                text = when (font.category) {
                                                    "Serif" -> "Empirical Literary Typology"
                                                    "Sans-Serif" -> "Symmetrical Clean Interface"
                                                    "Monospace" -> "fun reader() { compile_xml_toc() }"
                                                    "Handwriting" -> "Graceful handwritten stroke script"
                                                    "Display" -> "BOLD ARTISTIC HEADLINE POSTER"
                                                    else -> "Sensory reading type style"
                                                },
                                                fontFamily = font.fallbackFamily,
                                                fontStyle = if (font.category == "Handwriting") FontStyle.Italic else FontStyle.Normal,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            if (isFontActive) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Active",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else if (isInstalled) {
                                                Text("Apply", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "Download Font",
                                                    tint = Color.Gray.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Or input any custom Google Font by exact name:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = fontNameInput,
                            onValueChange = { fontNameInput = it },
                            placeholder = { Text("Exact Font Name (e.g. Syne)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                        )
                        Button(
                            onClick = {
                                if (fontNameInput.isNotBlank()) {
                                    onDownloadFont(fontNameInput.trim())
                                    fontNameInput = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("Fetch", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current Active Font: $activeFontName",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (activeFontName != "System") {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = onSetSystemFont,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Reset to standard sans-serif", fontSize = 10.sp)
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Section 3: OpenRouter API Configuration
                Column {
                    Text(
                        text = "3. OPENROUTER SYNTAX REWRITER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Input your OpenRouter key to enable deep rewriting of textbook chapters using generative AI.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("OpenRouter API Key", fontSize = 10.sp) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Model Selection Box
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedModelInput,
                            onValueChange = { selectedModelInput = it },
                            label = { Text("Target Generative Model ID", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                            trailingIcon = {
                                IconButton(onClick = { showModelDropdown = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Show fetched list")
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = showModelDropdown,
                            onDismissRequest = { showModelDropdown = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val items = if (openRouterModels.isEmpty()) {
                                listOf(
                                    Pair("google/gemini-2.5-flash", "Gemini 2.5 Flash"),
                                    Pair("meta-llama/llama-3-8b-instruct:free", "Llama 3 8B Instruct (Free)"),
                                    Pair("mistralai/mistral-7b-instruct:free", "Mistral 7B Instruct (Free)"),
                                    Pair("microsoft/phi-3-medium-128k-instruct:free", "Phi 3 Medium (Free)"),
                                    Pair("openrouter/auto", "Auto Selector / Default")
                                )
                            } else {
                                openRouterModels
                            }

                            items.forEach { (modelId, modelFriendlyName) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(modelFriendlyName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(modelId, fontSize = 9.sp, color = Color.Gray)
                                        }
                                    },
                                    onClick = {
                                        selectedModelInput = modelId
                                        showModelDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveOpenRouter(apiKeyInput.trim(), selectedModelInput.trim())
                    onDismiss()
                }
            ) {
                Text("Apply settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// 3. Review Notes Deck Pane Composable
@Composable
fun ReviewNotesPane(
    notes: List<Note>,
    onDeleteNote: (String) -> Unit
) {
    if (notes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("No annotations recorded yet", fontWeight = FontWeight.Medium, color = Color.Gray)
                Text(
                    text = "As you read sentence by sentence, press 'Annotate' to log active takeaways or questions.",
                    fontSize = 11.sp,
                    color = Color.Gray.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "✍️ RECITE & REVIEW: Recall the concepts from memory before expanding. Check notes to consolidate facts and evaluate syllabus targets.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        items(notes) { note ->
            Card(
                modifier = Modifier.fillMaxWidth().testTag("note_card_${note.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header meta details for citation display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (note.type == NoteType.QUESTION) Icons.Default.Info else Icons.Default.Star,
                                contentDescription = null,
                                tint = if (note.type == NoteType.QUESTION) Color(0xFFF39C12) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (note.type == NoteType.QUESTION) "SYLLABUS QUESTION" else "SUMMARY TAKEAWAY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (note.type == NoteType.QUESTION) Color(0xFFF39C12) else MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }

                        IconButton(
                            onClick = { onDeleteNote(note.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Annotation", tint = Color.Red.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Captured note body
                    Text(
                        text = note.content,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Quote / Excerpt if available
                    if (!note.snippet.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "EXCERPT CITATION",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "“${note.snippet}”",
                                    fontSize = 12.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Fully reconstructed citation grade string
                    Text(
                        text = "Anchor: ${note.sectionTitle ?: "General Outlines"}" +
                                if (note.sentenceIndex != null) " • Sentence ${note.sentenceIndex + 1}" else "",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun AiLibraryPane(
    savedRewrites: List<SavedRewrite>,
    chapters: List<Chapter>
) {
    if (savedRewrites.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "AI Library is Empty",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To generate rewrites, go to the '1. SURVEY' tab, select a chapter, and select an AI reading style.",
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(savedRewrites) { rewrite ->
                val associatedCh = chapters.getOrNull(rewrite.chapterIndex)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (associatedCh != null) {
                                    if (associatedCh.isSubchapter) {
                                        "SUBCHAPTER: ${associatedCh.title.uppercase()}"
                                    } else {
                                        "CHAPTER ${associatedCh.orderIndex + 1}: ${associatedCh.title.uppercase()}"
                                    }
                                } else "CHAPTER ${rewrite.chapterIndex + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Adapted AI Translation",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // AI Prompt / Persona Banner 
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp).padding(top = 1.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "ADAPTATION METHOD",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = rewrite.prompt,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                        
                        MarkdownChapterRenderer(
                            text = rewrite.rewrittenText,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownChapterRenderer(
    text: String,
    modifier: Modifier = Modifier
) {
    val lines = remember(text) { text.split("\n") }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var insideCodeBlock = false
        val currentCodeLines = remember { mutableListOf<String>() }
        
        lines.forEach { line ->
            val trimmed = line.trim()
            
            // Code block handling
            if (trimmed.startsWith("```")) {
                if (insideCodeBlock) {
                    CodeBlock(code = currentCodeLines.joinToString("\n"))
                    currentCodeLines.clear()
                    insideCodeBlock = false
                } else {
                    insideCodeBlock = true
                }
                return@forEach
            }
            
            if (insideCodeBlock) {
                currentCodeLines.add(line)
                return@forEach
            }
            
            when {
                // Header 1 (# Title)
                trimmed.startsWith("# ") -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = trimmed.removePrefix("# ").trim(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                // Header 2 (## Title)
                trimmed.startsWith("## ") -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = trimmed.removePrefix("## ").trim(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                // Header 3 (### Title)
                trimmed.startsWith("### ") -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = trimmed.removePrefix("### ").trim(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                }
                // Bullet List Items (- or *)
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val content = if (trimmed.startsWith("- ")) trimmed.removePrefix("- ") else trimmed.removePrefix("* ")
                    BulletListItem(text = content.trim())
                }
                // Plain numbered lists (e.g. "1. Item")
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val parts = trimmed.split(Regex("^\\d+\\.\\s+"), limit = 2)
                    if (parts.size == 2) {
                        NumberedListItem(numberText = trimmed.substringBefore(parts[1]).trim(), text = parts[1].trim())
                    } else {
                        BulletListItem(text = trimmed)
                    }
                }
                // Standard blank/empty line
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                // Standard paragraph text
                else -> {
                    Text(
                        text = renderInlineMarkdown(trimmed),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Fallback for unclosed code block
        if (insideCodeBlock && currentCodeLines.isNotEmpty()) {
            CodeBlock(code = currentCodeLines.joinToString("\n"))
        }
    }
}

@Composable
fun BulletListItem(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = renderInlineMarkdown(text),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun NumberedListItem(numberText: String, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = numberText,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = renderInlineMarkdown(text),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CodeBlock(code: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = code,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
fun renderInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var index = 0
        val len = text.length
        
        while (index < len) {
            val nextBold = text.indexOf("**", index)
            val nextItalic = text.indexOf("*", index)
            val nextInlineCode = text.indexOf("`", index)
            
            // Find earliest marker
            var minIndex = len
            var marker = ""
            
            if (nextBold in index until minIndex) {
                minIndex = nextBold
                marker = "**"
            }
            if (nextItalic in index until minIndex && nextItalic != nextBold) {
                minIndex = nextItalic
                marker = "*"
            }
            if (nextInlineCode in index until minIndex) {
                minIndex = nextInlineCode
                marker = "`"
            }
            
            if (minIndex == len) {
                append(text.substring(index))
                break
            }
            
            append(text.substring(index, minIndex))
            index = minIndex + marker.length
            
            val closingIndex = text.indexOf(marker, index)
            if (closingIndex == -1) {
                append(marker)
                append(text.substring(index))
                break
            }
            
            val content = text.substring(index, closingIndex)
            when (marker) {
                "**" -> {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                    append(content)
                    pop()
                }
                "*" -> {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic))
                    append(content)
                    pop()
                }
                "`" -> {
                    pushStyle(androidx.compose.ui.text.SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    ))
                    append(content)
                    pop()
                }
            }
            index = closingIndex + marker.length
        }
    }
}

