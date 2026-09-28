package dev.munote.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.munote.app.AppLanguage
import dev.munote.app.R
import dev.munote.app.backup.LocalBackupInfo
import dev.munote.app.backup.LocalBackupManager
import dev.munote.app.image.ImageStore
import dev.munote.app.image.PageImageNote
import dev.munote.app.image.StickerItem
import dev.munote.app.image.StickerStore
import dev.munote.app.ink.EraserMode
import dev.munote.app.ink.InkBrush
import dev.munote.app.ink.InkCanvasView
import dev.munote.app.ink.InkShape
import dev.munote.app.ink.InkStore
import dev.munote.app.ink.InkTool
import dev.munote.app.navigation.NavigationStore
import dev.munote.app.navigation.PageLink
import dev.munote.app.navigation.OutlineEntry
import dev.munote.app.handwriting.ChineseHandwritingRecognizer
import dev.munote.app.handwriting.HandwritingIndexStore
import dev.munote.app.handwriting.HandwritingModelState
import dev.munote.app.ocr.ChineseOcrEngine
import dev.munote.app.ocr.OcrIndexStore
import dev.munote.app.ocr.SearchHit
import dev.munote.app.ocr.SearchSource
import dev.munote.app.pdf.DocumentKind
import dev.munote.app.pdf.LibraryEntry
import dev.munote.app.pdf.LibraryFolder
import dev.munote.app.pdf.PageTemplate
import dev.munote.app.pdf.PdfExporter
import dev.munote.app.pdf.PdfLibrary
import dev.munote.app.pdf.PdfSession
import dev.munote.app.text.TextBoxNote
import dev.munote.app.text.TextStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MuNoteApp(initialPdf: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember { PdfLibrary(context) }
    val backupManager = remember { LocalBackupManager(context) }
    val handwritingRecognizer = remember { ChineseHandwritingRecognizer(context) }

    var session by remember { mutableStateOf<PdfSession?>(null) }
    var currentEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var indexStore by remember { mutableStateOf<OcrIndexStore?>(null) }
    var handwritingIndexStore by remember { mutableStateOf<HandwritingIndexStore?>(null) }
    var inkStore by remember { mutableStateOf<InkStore?>(null) }
    var textStore by remember { mutableStateOf<TextStore?>(null) }
    var imageStore by remember { mutableStateOf<ImageStore?>(null) }
    var navigationStore by remember { mutableStateOf<NavigationStore?>(null) }

    var splitSession by remember { mutableStateOf<PdfSession?>(null) }
    var splitEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var splitIndexStore by remember { mutableStateOf<OcrIndexStore?>(null) }
    var splitHandwritingIndexStore by remember { mutableStateOf<HandwritingIndexStore?>(null) }
    var splitInkStore by remember { mutableStateOf<InkStore?>(null) }
    var splitTextStore by remember { mutableStateOf<TextStore?>(null) }
    var splitImageStore by remember { mutableStateOf<ImageStore?>(null) }
    var splitNavigationStore by remember { mutableStateOf<NavigationStore?>(null) }
    var splitOcrDone by remember { mutableIntStateOf(0) }
    var splitOcrRevision by remember { mutableIntStateOf(0) }
    var showSplitPicker by remember { mutableStateOf(false) }

    var loadError by remember { mutableStateOf<String?>(null) }
    var ocrDone by remember { mutableIntStateOf(0) }
    var ocrRunning by remember { mutableStateOf(false) }
    var ocrRevision by remember { mutableIntStateOf(0) }
    var handwritingModelState by remember { mutableStateOf(HandwritingModelState.NOT_READY) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    var backupRevision by remember { mutableIntStateOf(0) }
    var coverTarget by remember { mutableStateOf<LibraryEntry?>(null) }

    suspend fun openEntrySession(entry: LibraryEntry): PdfSession =
        PdfSession.openStored(
            context = context,
            fingerprint = entry.fingerprint,
            pageOrder = entry.pdfPageOrder.takeIf {
                entry.kind == DocumentKind.PDF && it.isNotEmpty()
            },
        )

    fun closeSplit() {
        splitSession?.close()
        splitSession = null
        splitEntry = null
        splitIndexStore = null
        splitHandwritingIndexStore = null
        splitInkStore = null
        splitTextStore = null
        splitImageStore = null
        splitNavigationStore = null
        splitOcrDone = 0
        splitOcrRevision++
        showSplitPicker = false
    }

    fun attachSplitSession(next: PdfSession, entry: LibraryEntry) {
        splitSession?.close()
        splitSession = next
        splitEntry = entry

        val sameDocument = entry.fingerprint == currentEntry?.fingerprint
        splitIndexStore = if (sameDocument) indexStore else OcrIndexStore(context, next.fingerprint)
        splitHandwritingIndexStore = if (sameDocument) {
            handwritingIndexStore
        } else {
            HandwritingIndexStore(context, next.fingerprint)
        }
        splitInkStore = if (sameDocument) inkStore else InkStore(context, next.fingerprint)
        splitTextStore = if (sameDocument) textStore else TextStore(context, next.fingerprint)
        splitImageStore = if (sameDocument) imageStore else ImageStore(context, next.fingerprint)
        splitNavigationStore = if (sameDocument) {
            navigationStore
        } else {
            NavigationStore(context, next.fingerprint)
        }
        splitOcrDone = splitIndexStore?.completedPages() ?: 0
        splitOcrRevision++
    }

    fun openSplit(entry: LibraryEntry) {
        scope.launch {
            loadError = null
            runCatching {
                val next = openEntrySession(entry)
                val touched = library.touch(entry)
                attachSplitSession(next, touched)
            }.onFailure {
                loadError = it.message ?: context.getString(R.string.error_local_pdf_open)
                closeSplit()
            }
        }
    }

    fun attachSession(next: PdfSession, entry: LibraryEntry) {
        session?.close()
        session = next
        currentEntry = entry
        indexStore = OcrIndexStore(context, next.fingerprint)
        handwritingIndexStore = HandwritingIndexStore(context, next.fingerprint)
        inkStore = InkStore(context, next.fingerprint)
        textStore = TextStore(context, next.fingerprint)
        imageStore = ImageStore(context, next.fingerprint)
        navigationStore = NavigationStore(context, next.fingerprint)
        ocrDone = indexStore?.completedPages() ?: 0
        ocrRevision++
        libraryRevision++
    }

    fun openPdf(uri: Uri) {
        scope.launch {
            loadError = null
            runCatching {
                val title = library.displayName(uri)
                val imported = PdfSession.open(context, uri)
                val entry = library.registerImported(
                    fingerprint = imported.fingerprint,
                    title = title,
                    pageCount = imported.sourcePageCount,
                )
                imported.close()
                val next = openEntrySession(entry)
                attachSession(next, entry)
            }.onFailure {
                loadError = it.message ?: context.getString(R.string.error_pdf_open)
            }
        }
    }

    fun openStored(entry: LibraryEntry) {
        scope.launch {
            loadError = null
            runCatching {
                val next = openEntrySession(entry)
                val touched = library.touch(entry)
                attachSession(next, touched)
            }.onFailure {
                loadError = it.message ?: context.getString(R.string.error_local_pdf_open)
                libraryRevision++
            }
        }
    }

    fun closeDocument() {
        closeSplit()
        session?.close()
        session = null
        currentEntry = null
        indexStore = null
        handwritingIndexStore = null
        inkStore = null
        textStore = null
        imageStore = null
        navigationStore = null
        ocrDone = 0
        ocrRunning = false
        ocrRevision++
        libraryRevision++
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) openPdf(uri)
    }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = coverTarget
        coverTarget = null
        if (uri != null && target != null) {
            scope.launch {
                runCatching { library.setCustomCover(target, uri) }
                    .onSuccess { libraryRevision++ }
                    .onFailure { loadError = it.message }
            }
        }
    }

    LaunchedEffect(initialPdf) {
        if (initialPdf != null && session == null) openPdf(initialPdf)
    }

    DisposableEffect(Unit) {
        onDispose {
            session?.close()
            splitSession?.close()
            handwritingRecognizer.close()
        }
    }

    LaunchedEffect(Unit) {
        runCatching { backupManager.autoBackupIfDue() }
            .onSuccess { backupRevision++ }

        handwritingModelState = if (handwritingRecognizer.isReady()) {
            HandwritingModelState.READY
        } else {
            HandwritingModelState.DOWNLOADING
            if (handwritingRecognizer.ensureReady()) {
                HandwritingModelState.READY
            } else {
                HandwritingModelState.ERROR
            }
        }
    }

    BackHandler(enabled = session != null) {
        if (splitSession != null) closeSplit() else closeDocument()
    }

    LaunchedEffect(session?.fingerprint) {
        if (currentEntry?.kind == DocumentKind.NOTE) {
            ocrRunning = false
            ocrDone = 0
            return@LaunchedEffect
        }
        val pdf = session ?: return@LaunchedEffect
        val store = indexStore ?: return@LaunchedEffect
        val anchor = (currentEntry?.lastPage ?: 0)
            .coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0))

        // Large scanned textbooks should become useful around the page the user is actually
        // reading before the app spends time indexing hundreds of earlier/later pages.
        val pageOrder = buildList {
            if (pdf.pageCount > 0) add(anchor)
            for (distance in 1 until pdf.pageCount) {
                val left = anchor - distance
                val right = anchor + distance
                if (left >= 0) add(left)
                if (right < pdf.pageCount) add(right)
                if (size >= pdf.pageCount) break
            }
        }

        val engine = ChineseOcrEngine()
        ocrRunning = true
        try {
            for (page in pageOrder) {
                if (!store.needsRefresh(page)) {
                    ocrDone = store.completedPages()
                    continue
                }
                val bitmap = pdf.renderPage(page, 1500)
                val recognition = engine.recognize(bitmap)
                store.put(page, recognition)
                ocrDone = store.completedPages()
                ocrRevision++
            }
        } finally {
            engine.close()
            ocrRunning = false
        }
    }

    if (session == null) {
        LibraryHome(
            library = library,
            entries = remember(libraryRevision) { library.entries() },
            trashEntries = remember(libraryRevision) { library.trashEntries() },
            folders = remember(libraryRevision) { library.folders() },
            backups = remember(backupRevision) { backupManager.backups() },
            error = loadError,
            onImport = { picker.launch(arrayOf("application/pdf")) },
            onCreateNote = { title, template ->
                scope.launch {
                    loadError = null
                    runCatching {
                        val entry = library.createNotebook(title, template)
                        val next = openEntrySession(entry)
                        attachSession(next, entry)
                    }.onFailure {
                        loadError = it.message ?: context.getString(R.string.error_notebook_save)
                        libraryRevision++
                    }
                }
            },
            onOpen = ::openStored,
            onRename = { entry, title ->
                scope.launch {
                    runCatching { library.rename(entry, title) }
                        .onSuccess { libraryRevision++ }
                        .onFailure { loadError = it.message ?: context.getString(R.string.error_rename) }
                }
            },
            onDelete = { entry ->
                scope.launch {
                    runCatching { library.delete(entry) }
                        .onSuccess { libraryRevision++ }
                        .onFailure { loadError = it.message ?: context.getString(R.string.error_delete) }
                }
            },
            onRestore = { entry ->
                scope.launch {
                    runCatching { library.restoreFromTrash(entry) }
                        .onSuccess { libraryRevision++ }
                        .onFailure { loadError = it.message }
                }
            },
            onDeletePermanently = { entry ->
                scope.launch {
                    runCatching { library.deletePermanently(entry) }
                        .onSuccess { libraryRevision++ }
                        .onFailure { loadError = it.message ?: context.getString(R.string.error_delete) }
                }
            },
            onToggleFavorite = { entry ->
                scope.launch {
                    runCatching { library.toggleFavorite(entry) }
                        .onSuccess { libraryRevision++ }
                        .onFailure { loadError = it.message }
                }
            },
            onMoveToFolder = { entry, folderId ->
                scope.launch {
                    runCatching { library.moveToFolder(entry, folderId) }
                        .onSuccess { libraryRevision++ }
                        .onFailure { loadError = it.message }
                }
            },
            onCreateFolder = { name ->
                scope.launch {
                    runCatching { library.createFolder(name) }
                        .onSuccess { libraryRevision++ }
                        .onFailure { loadError = it.message }
                }
            },
            onSetCover = { entry ->
                coverTarget = entry
                coverPicker.launch(arrayOf("image/*"))
            },
            onResetCover = { entry ->
                scope.launch {
                    runCatching { library.clearCustomCover(entry) }
                        .onSuccess { libraryRevision++ }
                        .onFailure { loadError = it.message }
                }
            },
            onBackupNow = {
                scope.launch {
                    runCatching { backupManager.createManualBackup() }
                        .onSuccess {
                            backupRevision++
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_backup_created),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .onFailure { loadError = it.message }
                }
            },
            onRestoreBackup = { backup ->
                scope.launch {
                    runCatching {
                        backupManager.restore(backup.fileName)
                        library.reload()
                    }.onSuccess {
                        libraryRevision++
                        backupRevision++
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_backup_restored),
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { loadError = it.message }
                }
            },
        )
    } else {
        val renderPrimary: @Composable (Boolean) -> Unit = { splitMode ->
        ReaderScreen(
            documentTitle = currentEntry?.title ?: if (currentEntry?.kind == DocumentKind.NOTE) {
                context.getString(R.string.default_notebook_title)
            } else {
                context.getString(R.string.default_pdf_note)
            },
            initialPage = currentEntry?.lastPage ?: 0,
            session = session!!,
            indexStore = indexStore!!,
            handwritingIndexStore = handwritingIndexStore!!,
            handwritingRecognizer = handwritingRecognizer,
            handwritingModelState = handwritingModelState,
            inkStore = inkStore!!,
            textStore = textStore!!,
            imageStore = imageStore!!,
            navigationStore = navigationStore!!,
            ocrDone = ocrDone,
            ocrRunning = ocrRunning,
            ocrRevision = ocrRevision,
            bookmarks = currentEntry?.bookmarks ?: emptySet(),
            isNotebook = currentEntry?.kind == DocumentKind.NOTE && !splitMode,
            canManagePages = !splitMode,
            onAddPage = {
                currentEntry?.takeIf { it.kind == DocumentKind.NOTE }?.let { entry ->
                    scope.launch {
                        loadError = null
                        runCatching {
                            session?.close()
                            session = null
                            val appended = library.appendNotebookPage(entry)
                            val lastPage = appended.notePageCount.coerceAtLeast(1) - 1
                            val updated = library.updateLastPage(appended, lastPage)
                            val next = openEntrySession(updated)
                            attachSession(next, updated)
                        }.onFailure {
                            loadError = it.message ?: context.getString(R.string.error_notebook_save)
                            libraryRevision++
                        }
                    }
                }
            },
            onDeletePage = { page ->
                currentEntry?.let { entry ->
                    scope.launch {
                        loadError = null
                        runCatching {
                            val activeSession = requireNotNull(session)
                            val updated = when (entry.kind) {
                                DocumentKind.NOTE ->
                                    library.deleteNotebookPage(entry, page)
                                DocumentKind.PDF ->
                                    library.deletePdfPage(
                                        entry = entry,
                                        pageIndex = page,
                                        sourcePageCount = activeSession.sourcePageCount,
                                    )
                            }
                            inkStore?.deletePage(page)
                            textStore?.deletePage(page)
                            imageStore?.deletePage(page)
                            navigationStore?.deletePage(page)
                            handwritingIndexStore?.deletePage(page)
                            indexStore?.deletePage(page)
                            activeSession.close()
                            session = null
                            val next = openEntrySession(updated)
                            attachSession(next, updated)
                        }.onFailure {
                            loadError = it.message ?: context.getString(R.string.error_page_update)
                        }
                    }
                }
            },
            onDuplicatePage = { page ->
                currentEntry?.let { entry ->
                    scope.launch {
                        loadError = null
                        runCatching {
                            val activeSession = requireNotNull(session)
                            val updated = when (entry.kind) {
                                DocumentKind.NOTE ->
                                    library.duplicateNotebookPage(entry, page)
                                DocumentKind.PDF ->
                                    library.duplicatePdfPage(
                                        entry = entry,
                                        pageIndex = page,
                                        sourcePageCount = activeSession.sourcePageCount,
                                    )
                            }
                            inkStore?.duplicatePage(page)
                            textStore?.duplicatePage(page)
                            imageStore?.duplicatePage(page)
                            navigationStore?.duplicatePage(page)
                            handwritingIndexStore?.duplicatePage(page)
                            indexStore?.duplicatePage(page)
                            activeSession.close()
                            session = null
                            val next = openEntrySession(updated)
                            attachSession(next, updated)
                        }.onFailure {
                            loadError = it.message ?: context.getString(R.string.error_page_update)
                        }
                    }
                }
            },
            onMovePage = { from, to ->
                currentEntry?.let { entry ->
                    scope.launch {
                        loadError = null
                        runCatching {
                            val activeSession = requireNotNull(session)
                            val updated = when (entry.kind) {
                                DocumentKind.NOTE ->
                                    library.moveNotebookPage(entry, from, to)
                                DocumentKind.PDF ->
                                    library.movePdfPage(
                                        entry = entry,
                                        fromIndex = from,
                                        toIndex = to,
                                        sourcePageCount = activeSession.sourcePageCount,
                                    )
                            }
                            inkStore?.movePage(from, to)
                            textStore?.movePage(from, to)
                            imageStore?.movePage(from, to)
                            navigationStore?.movePage(from, to)
                            handwritingIndexStore?.movePage(from, to)
                            indexStore?.movePage(from, to)
                            activeSession.close()
                            session = null
                            val next = openEntrySession(updated)
                            attachSession(next, updated)
                        }.onFailure {
                            loadError = it.message ?: context.getString(R.string.error_page_update)
                        }
                    }
                }
            },
            onChangePageTemplate = { page, template ->
                currentEntry?.takeIf { it.kind == DocumentKind.NOTE }?.let { entry ->
                    scope.launch {
                        loadError = null
                        runCatching {
                            val updated = library.setNotebookPageTemplate(entry, page, template)
                            session?.close()
                            session = null
                            val next = openEntrySession(updated)
                            attachSession(next, updated)
                        }.onFailure {
                            loadError = it.message ?: context.getString(R.string.error_notebook_save)
                        }
                    }
                }
            },
            onToggleBookmark = { page ->
                currentEntry?.let { entry ->
                    scope.launch {
                        currentEntry = library.toggleBookmark(entry, page)
                    }
                }
            },
            onPageChanged = { page ->
                currentEntry?.let { entry ->
                    scope.launch {
                        currentEntry = library.updateLastPage(entry, page)
                    }
                }
            },
            onClose = ::closeDocument,
            onOpenPdf = {
                if (splitMode) showSplitPicker = true
                else picker.launch(arrayOf("application/pdf"))
            },
            splitMode = splitMode,
            onRequestSplit = { showSplitPicker = true },
            onCloseSplit = ::closeSplit,
        )
        }

        if (showSplitPicker) {
            SplitPickerDialog(
                currentEntry = currentEntry,
                entries = remember(libraryRevision) { library.entries() },
                onDismiss = { showSplitPicker = false },
                onSelect = { entry ->
                    showSplitPicker = false
                    openSplit(entry)
                },
            )
        }

        val renderSecondary: @Composable () -> Unit = {
            val secondarySession = splitSession
            val secondaryEntry = splitEntry
            val secondaryIndex = splitIndexStore
            val secondaryHandwriting = splitHandwritingIndexStore
            val secondaryInk = splitInkStore
            val secondaryText = splitTextStore
            val secondaryImages = splitImageStore
            val secondaryNavigation = splitNavigationStore

            if (
                secondarySession != null &&
                secondaryEntry != null &&
                secondaryIndex != null &&
                secondaryHandwriting != null &&
                secondaryInk != null &&
                secondaryText != null &&
                secondaryImages != null &&
                secondaryNavigation != null
            ) {
                ReaderScreen(
                    documentTitle = secondaryEntry.title,
                    initialPage = secondaryEntry.lastPage,
                    session = secondarySession,
                    indexStore = secondaryIndex,
                    handwritingIndexStore = secondaryHandwriting,
                    handwritingRecognizer = handwritingRecognizer,
                    handwritingModelState = handwritingModelState,
                    inkStore = secondaryInk,
                    textStore = secondaryText,
                    imageStore = secondaryImages,
                    navigationStore = secondaryNavigation,
                    ocrDone = splitOcrDone,
                    ocrRunning = false,
                    ocrRevision = splitOcrRevision,
                    bookmarks = secondaryEntry.bookmarks,
                    isNotebook = false,
                    canManagePages = false,
                    onAddPage = {},
                    onDeletePage = {},
                    onDuplicatePage = {},
                    onMovePage = { _, _ -> },
                    onChangePageTemplate = { _, _ -> },
                    onToggleBookmark = { page ->
                        splitEntry?.let { entry ->
                            scope.launch {
                                splitEntry = library.toggleBookmark(entry, page)
                            }
                        }
                    },
                    onPageChanged = { page ->
                        splitEntry?.let { entry ->
                            scope.launch {
                                splitEntry = library.updateLastPage(entry, page)
                            }
                        }
                    },
                    onClose = ::closeSplit,
                    onOpenPdf = { showSplitPicker = true },
                    splitMode = true,
                    onRequestSplit = {},
                    onCloseSplit = ::closeSplit,
                )
            }
        }

        if (splitSession == null) {
            renderPrimary(false)
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= 720.dp) {
                    Row(Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            renderPrimary(true)
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            renderSecondary()
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            renderPrimary(true)
                        }
                        Box(
                            modifier = Modifier
                                .height(1.dp)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            renderSecondary()
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun SplitPickerDialog(
    currentEntry: LibraryEntry?,
    entries: List<LibraryEntry>,
    onDismiss: () -> Unit,
    onSelect: (LibraryEntry) -> Unit,
) {
    val ordered = remember(entries, currentEntry?.fingerprint) {
        entries.sortedWith(
            compareByDescending<LibraryEntry> { it.fingerprint == currentEntry?.fingerprint }
                .thenByDescending { it.lastOpenedAt }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.78f),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.split_picker_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            stringResource(R.string.split_picker_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        bottom = 18.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        count = ordered.size,
                        key = { ordered[it].fingerprint }
                    ) { index ->
                        val entry = ordered[index]
                        Surface(
                            onClick = { onSelect(entry) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 14.dp,
                                    vertical = 12.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        if (entry.fingerprint == currentEntry?.fingerprint) {
                                            stringResource(R.string.split_same_document)
                                        } else if (entry.kind == DocumentKind.NOTE) {
                                            stringResource(R.string.native_notebook_pages, entry.notePageCount.coerceAtLeast(1))
                                        } else {
                                            stringResource(R.string.split_pdf_document)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    stringResource(R.string.action_open),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageMenu() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val current = AppLanguage.current(context)

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.Language,
                contentDescription = stringResource(R.string.language)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        (if (current == AppLanguage.CHINESE) "✓ " else "") +
                            stringResource(R.string.language_chinese)
                    )
                },
                onClick = {
                    expanded = false
                    AppLanguage.set(context, AppLanguage.CHINESE)
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        (if (current == AppLanguage.ENGLISH) "✓ " else "") +
                            stringResource(R.string.language_english)
                    )
                },
                onClick = {
                    expanded = false
                    AppLanguage.set(context, AppLanguage.ENGLISH)
                }
            )
        }
    }
}

private enum class LibraryView { ALL, FAVORITES, TRASH }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryHome(
    library: PdfLibrary,
    entries: List<LibraryEntry>,
    trashEntries: List<LibraryEntry>,
    folders: List<LibraryFolder>,
    backups: List<LocalBackupInfo>,
    error: String?,
    onImport: () -> Unit,
    onCreateNote: (String, PageTemplate) -> Unit,
    onOpen: (LibraryEntry) -> Unit,
    onRename: (LibraryEntry, String) -> Unit,
    onDelete: (LibraryEntry) -> Unit,
    onRestore: (LibraryEntry) -> Unit,
    onDeletePermanently: (LibraryEntry) -> Unit,
    onToggleFavorite: (LibraryEntry) -> Unit,
    onMoveToFolder: (LibraryEntry, String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onSetCover: (LibraryEntry) -> Unit,
    onResetCover: (LibraryEntry) -> Unit,
    onBackupNow: () -> Unit,
    onRestoreBackup: (LocalBackupInfo) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var permanentDeleteTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var moveTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var showNewNotebook by remember { mutableStateOf(false) }
    var newNotebookTitle by remember { mutableStateOf("") }
    var newNotebookTemplate by remember { mutableStateOf(PageTemplate.BLANK) }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showBackups by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(LibraryView.ALL) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }

    val visibleEntries = when (view) {
        LibraryView.TRASH -> trashEntries
        LibraryView.FAVORITES -> entries.filter { it.favorite }
        LibraryView.ALL -> if (selectedFolderId == null) {
            entries
        } else {
            entries.filter { it.folderId == selectedFolderId }
        }
    }

    if (showNewNotebook) {
        AlertDialog(
            onDismissRequest = { showNewNotebook = false },
            title = { Text(stringResource(R.string.dialog_new_notebook_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newNotebookTitle,
                        onValueChange = { newNotebookTitle = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_notebook_name)) }
                    )
                    Text(
                        stringResource(R.string.label_page_template),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            PageTemplate.BLANK to R.string.template_blank,
                            PageTemplate.RULED to R.string.template_ruled,
                            PageTemplate.GRID to R.string.template_grid,
                            PageTemplate.DOT to R.string.template_dot,
                        ).forEach { (template, labelRes) ->
                            AssistChip(
                                onClick = { newNotebookTemplate = template },
                                label = {
                                    Text(
                                        (if (newNotebookTemplate == template) "✓ " else "") +
                                            stringResource(labelRes)
                                    )
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateNote(newNotebookTitle, newNotebookTemplate)
                        newNotebookTitle = ""
                        newNotebookTemplate = PageTemplate.BLANK
                        showNewNotebook = false
                    }
                ) {
                    Text(stringResource(R.string.action_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewNotebook = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text(stringResource(R.string.dialog_new_folder_title)) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_folder_name)) }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newFolderName.trim().isNotEmpty(),
                    onClick = {
                        onCreateFolder(newFolderName)
                        newFolderName = ""
                        showNewFolder = false
                    }
                ) {
                    Text(stringResource(R.string.action_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolder = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    renameTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.dialog_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_document_name)) }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.trim().isNotEmpty(),
                    onClick = {
                        onRename(entry, renameText)
                        renameTarget = null
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.dialog_move_to_trash_title)) },
            text = { Text(stringResource(R.string.dialog_move_to_trash_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(entry)
                        deleteTarget = null
                    }
                ) {
                    Text(
                        stringResource(R.string.action_move_to_trash),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    permanentDeleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { permanentDeleteTarget = null },
            title = { Text(stringResource(R.string.dialog_delete_forever_title)) },
            text = { Text(stringResource(R.string.dialog_delete_forever_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePermanently(entry)
                        permanentDeleteTarget = null
                    }
                ) {
                    Text(
                        stringResource(R.string.action_delete_forever),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { permanentDeleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    moveTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text(stringResource(R.string.dialog_move_folder_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            onMoveToFolder(entry, null)
                            moveTarget = null
                        }
                    ) {
                        Text(stringResource(R.string.folder_root))
                    }
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(count = folders.size, key = { folders[it].id }) { index ->
                            val folder = folders[index]
                            TextButton(
                                onClick = {
                                    onMoveToFolder(entry, folder.id)
                                    moveTarget = null
                                }
                            ) {
                                Text(folder.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moveTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showBackups) {
        AlertDialog(
            onDismissRequest = { showBackups = false },
            title = { Text(stringResource(R.string.local_backups_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.local_backups_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onBackupNow) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_backup_now))
                    }
                    if (backups.isEmpty()) {
                        Text(
                            stringResource(R.string.local_backups_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(count = backups.size, key = { backups[it].fileName }) { index ->
                                val backup = backups[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (backup.automatic) {
                                                stringResource(R.string.backup_auto)
                                            } else {
                                                stringResource(R.string.backup_manual)
                                            },
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            backup.fileName + " · " +
                                                ((backup.sizeBytes + 1023L) / 1024L).toString() + " KB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            onRestoreBackup(backup)
                                            showBackups = false
                                        }
                                    ) {
                                        Text(stringResource(R.string.action_restore))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBackups = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(tonalElevation = 1.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("MuNote", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.home_tagline),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { showBackups = true }) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = stringResource(R.string.local_backups_title)
                    )
                }
                LanguageMenu()
                Surface(
                    onClick = { showNewNotebook = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(
                            stringResource(R.string.action_new_notebook),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Surface(
                    onClick = onImport,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Text(
                            stringResource(R.string.action_import_pdf),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        if (error != null) {
            Text(
                error,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = {
                    view = LibraryView.ALL
                    selectedFolderId = null
                },
                label = {
                    Text(
                        (if (view == LibraryView.ALL && selectedFolderId == null) "✓ " else "") +
                            stringResource(R.string.library_all)
                    )
                }
            )
            AssistChip(
                onClick = {
                    view = LibraryView.FAVORITES
                    selectedFolderId = null
                },
                label = {
                    Text(
                        (if (view == LibraryView.FAVORITES) "✓ " else "") +
                            stringResource(R.string.library_favorites)
                    )
                },
                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
            )
            folders.forEach { folder ->
                AssistChip(
                    onClick = {
                        view = LibraryView.ALL
                        selectedFolderId = folder.id
                    },
                    label = {
                        Text(
                            (if (view == LibraryView.ALL && selectedFolderId == folder.id) "✓ " else "") +
                                folder.name
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                )
            }
            AssistChip(
                onClick = { showNewFolder = true },
                label = { Text(stringResource(R.string.action_new_folder)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
            )
            AssistChip(
                onClick = {
                    view = LibraryView.TRASH
                    selectedFolderId = null
                },
                label = {
                    Text(
                        (if (view == LibraryView.TRASH) "✓ " else "") +
                            stringResource(R.string.library_trash)
                    )
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
        }

        if (visibleEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.padding(28.dp)
                ) {
                    Column(
                        Modifier.padding(horizontal = 36.dp, vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (view == LibraryView.ALL && selectedFolderId == null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalIconButton(
                                    onClick = { showNewNotebook = true },
                                    modifier = Modifier.size(58.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = stringResource(R.string.action_new_notebook)
                                    )
                                }
                                FilledTonalIconButton(
                                    onClick = onImport,
                                    modifier = Modifier.size(58.dp)
                                ) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = stringResource(R.string.action_import_pdf)
                                    )
                                }
                            }
                        }
                        Text(
                            if (view == LibraryView.TRASH) {
                                stringResource(R.string.trash_empty)
                            } else {
                                stringResource(R.string.home_empty_title)
                            }
                        )
                        if (view != LibraryView.TRASH) {
                            Text(
                                stringResource(R.string.home_empty_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                when {
                    view == LibraryView.TRASH -> stringResource(R.string.library_trash)
                    view == LibraryView.FAVORITES -> stringResource(R.string.library_favorites)
                    selectedFolderId != null -> folders.firstOrNull { it.id == selectedFolderId }?.name
                        ?: stringResource(R.string.recent_documents)
                    else -> stringResource(R.string.recent_documents)
                },
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(
                    count = visibleEntries.size,
                    key = { visibleEntries[it].fingerprint }
                ) { index ->
                    val entry = visibleEntries[index]
                    LibraryDocumentCard(
                        library = library,
                        entry = entry,
                        inTrash = view == LibraryView.TRASH,
                        onClick = { onOpen(entry) },
                        onRename = {
                            renameText = entry.title
                            renameTarget = entry
                        },
                        onSetCover = { onSetCover(entry) },
                        onResetCover = { onResetCover(entry) },
                        onToggleFavorite = { onToggleFavorite(entry) },
                        onMove = { moveTarget = entry },
                        onDelete = { deleteTarget = entry },
                        onRestore = { onRestore(entry) },
                        onDeletePermanently = { permanentDeleteTarget = entry },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryDocumentCard(
    library: PdfLibrary,
    entry: LibraryEntry,
    inTrash: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onSetCover: () -> Unit,
    onResetCover: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val cover by produceState<Bitmap?>(
        initialValue = null,
        entry.fingerprint,
        entry.hasCustomCover
    ) {
        value = library.renderCover(entry, 420)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            onClick = onClick,
            enabled = !inTrash,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            color = Color.White
        ) {
            Box(Modifier.fillMaxSize()) {
                val bitmap = cover
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = entry.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (entry.kind == DocumentKind.NOTE) "NOTE" else "PDF",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }

                if (entry.favorite && !inTrash) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = stringResource(R.string.library_favorites),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    )
                }

                Box(
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                    ) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.cd_document_menu)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (inTrash) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_restore)) },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onRestore()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete_forever)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDeletePermanently()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (entry.favorite) {
                                                R.string.action_unfavorite
                                            } else {
                                                R.string.action_favorite
                                            }
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (entry.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleFavorite()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_move_folder)) },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onMove()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_set_cover)) },
                                onClick = {
                                    menuExpanded = false
                                    onSetCover()
                                }
                            )
                            if (entry.hasCustomCover) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_reset_cover)) },
                                    onClick = {
                                        menuExpanded = false
                                        onResetCover()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_move_to_trash)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }

        Text(
            entry.title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 2.dp, end = 2.dp)
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    repeatDelayMillis = 1200
                ),
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            when {
                inTrash -> stringResource(R.string.library_trash)
                entry.kind == DocumentKind.NOTE ->
                    stringResource(
                        R.string.native_notebook_pages,
                        entry.notePageCount.coerceAtLeast(1)
                    )
                entry.lastPage > 0 ->
                    stringResource(R.string.last_viewed_page, entry.lastPage + 1)
                else ->
                    stringResource(R.string.local_saved_auto_ocr)
            },
            modifier = Modifier.padding(start = 2.dp, top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    documentTitle: String,
    initialPage: Int,
    session: PdfSession,
    indexStore: OcrIndexStore,
    handwritingIndexStore: HandwritingIndexStore,
    handwritingRecognizer: ChineseHandwritingRecognizer,
    handwritingModelState: HandwritingModelState,
    inkStore: InkStore,
    textStore: TextStore,
    imageStore: ImageStore,
    navigationStore: NavigationStore,
    ocrDone: Int,
    ocrRunning: Boolean,
    ocrRevision: Int,
    bookmarks: Set<Int>,
    isNotebook: Boolean,
    canManagePages: Boolean,
    onAddPage: () -> Unit,
    onDeletePage: (Int) -> Unit,
    onDuplicatePage: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onChangePageTemplate: (Int, PageTemplate) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onClose: () -> Unit,
    onOpenPdf: () -> Unit,
    splitMode: Boolean,
    onRequestSplit: () -> Unit,
    onCloseSplit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stickerStore = remember { StickerStore(context) }
    val pager = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0)),
        pageCount = { session.pageCount }
    )
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var selectedHit by remember { mutableIntStateOf(0) }

    var tool by remember { mutableStateOf(InkTool.PEN) }
    var inkRevision by remember { mutableIntStateOf(0) }
    var handwritingRevision by remember { mutableIntStateOf(0) }
    var textRevision by remember { mutableIntStateOf(0) }
    var imageRevision by remember { mutableIntStateOf(0) }
    var stickerRevision by remember { mutableIntStateOf(0) }
    var showStickerPicker by remember { mutableStateOf(false) }
    var navigationRevision by remember { mutableIntStateOf(0) }
    var showNavigationPanel by remember { mutableStateOf(false) }
    var showAddLink by remember { mutableStateOf(false) }
    var showAddOutline by remember { mutableStateOf(false) }
    var newLinkLabel by remember { mutableStateOf("") }
    var newLinkTarget by remember { mutableStateOf("") }
    var newOutlineTitle by remember { mutableStateOf("") }
    var activeTextBoxId by remember { mutableStateOf<String?>(null) }
    var activeImageId by remember { mutableStateOf<String?>(null) }
    var activeInkView by remember { mutableStateOf<InkCanvasView?>(null) }
    var lassoSelectionActive by remember { mutableStateOf(false) }
    var showThumbnails by remember { mutableStateOf(false) }
    var showBookmarksOnly by remember { mutableStateOf(false) }
    var showPageOverview by remember { mutableStateOf(false) }
    val pageHistory = remember { mutableStateListOf<Int>() }
    val inputPrefs = remember { context.getSharedPreferences("editor_preferences", android.content.Context.MODE_PRIVATE) }
    var fingerWriting by remember { mutableStateOf(inputPrefs.getBoolean("finger_writing", false)) }
    var showPageJump by remember { mutableStateOf(false) }
    var pageJumpText by remember { mutableStateOf("") }
    var showPenOptions by remember { mutableStateOf(false) }
    var exportRunning by remember { mutableStateOf(false) }
    var exportCompleted by remember { mutableIntStateOf(0) }
    var exportTotal by remember { mutableIntStateOf(0) }
    var exportJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    imageStore.add(pager.currentPage, uri)
                }.onSuccess { image ->
                    activeImageId = image.id
                    imageRevision++
                    tool = InkTool.IMAGE
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: context.getString(R.string.error_image_insert),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    if (showStickerPicker) {
        StickerPickerDialog(
            stickers = remember(stickerRevision) { stickerStore.items() },
            store = stickerStore,
            onDismiss = { showStickerPicker = false },
            onSelect = { sticker ->
                scope.launch {
                    runCatching {
                        imageStore.addSticker(
                            index = pager.currentPage,
                            sticker = sticker,
                            source = stickerStore.fileFor(sticker),
                        )
                    }.onSuccess { image ->
                        activeImageId = image.id
                        imageRevision++
                        showStickerPicker = false
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            error.message ?: context.getString(R.string.error_image_insert),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onDelete = { sticker ->
                scope.launch {
                    stickerStore.delete(sticker.id)
                    stickerRevision++
                }
            },
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            exportJob = scope.launch {
                exportRunning = true
                exportCompleted = 0
                exportTotal = session.pageCount
                try {
                    PdfExporter.exportFlattened(
                        context = context,
                        session = session,
                        inkStore = inkStore,
                        textStore = textStore,
                        imageStore = imageStore,
                        destination = uri,
                        onProgress = { completed, total ->
                            exportCompleted = completed
                            exportTotal = total
                        },
                    )
                    Toast.makeText(context, context.getString(R.string.toast_export_success), Toast.LENGTH_SHORT).show()
                } catch (_: CancellationException) {
                    Toast.makeText(context, context.getString(R.string.toast_export_cancelled), Toast.LENGTH_SHORT).show()
                } catch (error: Throwable) {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.error_export_failed,
                            error.message ?: context.getString(R.string.error_export_unknown)
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    exportRunning = false
                    exportJob = null
                }
            }
        }
    }

    var penColor by remember {
        mutableIntStateOf(inputPrefs.getInt("pen_color", 0xFF1C1D1F.toInt()))
    }
    var highlighterColor by remember {
        mutableIntStateOf(inputPrefs.getInt("highlighter_color", 0xFFFFD54F.toInt()))
    }
    var penWidth by remember {
        mutableStateOf(inputPrefs.getFloat("pen_width", 2.15f))
    }
    var highlighterWidth by remember {
        mutableStateOf(inputPrefs.getFloat("highlighter_width", 12f))
    }
    var brush by remember {
        mutableStateOf(
            runCatching {
                InkBrush.valueOf(
                    inputPrefs.getString("pen_brush", InkBrush.FOUNTAIN.name)
                        ?: InkBrush.FOUNTAIN.name
                )
            }.getOrDefault(InkBrush.FOUNTAIN)
        )
    }
    var eraserMode by remember {
        mutableStateOf(
            runCatching {
                EraserMode.valueOf(
                    inputPrefs.getString("eraser_mode", EraserMode.STROKE.name)
                        ?: EraserMode.STROKE.name
                )
            }.getOrDefault(EraserMode.STROKE)
        )
    }
    var eraserSize by remember {
        mutableStateOf(inputPrefs.getFloat("eraser_size", 18f))
    }
    val favoritePenColors = remember {
        mutableStateListOf<Int>().apply {
            inputPrefs.getString("favorite_pen_colors", "")
                .orEmpty()
                .split(",")
                .mapNotNull { it.toIntOrNull() }
                .distinct()
                .forEach(::add)
        }
    }
    val favoritePenWidths = remember {
        mutableStateListOf<Float>().apply {
            inputPrefs.getString("favorite_pen_widths", "")
                .orEmpty()
                .split(",")
                .mapNotNull { it.toFloatOrNull() }
                .distinct()
                .forEach(::add)
        }
    }
    var selectedShape by remember { mutableStateOf(InkShape.LINE) }

    fun persistColorFavorites() {
        inputPrefs.edit()
            .putString("favorite_pen_colors", favoritePenColors.joinToString(","))
            .apply()
    }

    fun persistWidthFavorites() {
        inputPrefs.edit()
            .putString("favorite_pen_widths", favoritePenWidths.joinToString(","))
            .apply()
    }

    suspend fun jumpToPage(page: Int, rememberLocation: Boolean = true) {
        val target = page.coerceIn(0, session.pageCount - 1)
        if (target == pager.currentPage) return
        if (rememberLocation) {
            if (pageHistory.lastOrNull() != pager.currentPage) {
                pageHistory += pager.currentPage
                while (pageHistory.size > 20) pageHistory.removeAt(0)
            }
        }
        pager.animateScrollToPage(target)
    }

    suspend fun goBackInPageHistory() {
        val target = pageHistory.removeLastOrNull() ?: return
        pager.animateScrollToPage(target.coerceIn(0, session.pageCount - 1))
    }

    suspend fun goToHit(index: Int) {
        if (hits.isEmpty()) return
        selectedHit = ((index % hits.size) + hits.size) % hits.size
        jumpToPage(hits[selectedHit].pageIndex)
    }

    fun openPageOverview() {
        showPageOverview = true
    }

    LaunchedEffect(session.fingerprint, handwritingModelState) {
        if (handwritingModelState != HandwritingModelState.READY) return@LaunchedEffect
        for (page in inkStore.pageIndices()) {
            val blocks = handwritingRecognizer.recognizePage(inkStore.page(page))
            handwritingIndexStore.put(page, blocks)
            handwritingRevision++
        }
    }

    // Re-index the current page only after the user pauses writing. Continuous pen strokes keep
    // cancelling this delay, so handwriting recognition never runs in the latency-critical path.
    LaunchedEffect(inkRevision, pager.currentPage, handwritingModelState) {
        if (inkRevision == 0 || handwritingModelState != HandwritingModelState.READY) {
            return@LaunchedEffect
        }
        delay(700)
        val page = pager.currentPage
        val blocks = handwritingRecognizer.recognizePage(inkStore.page(page))
        handwritingIndexStore.put(page, blocks)
        handwritingRevision++
    }

    LaunchedEffect(query, ocrRevision, handwritingRevision, textRevision) {
        hits = (
            indexStore.search(query) +
                handwritingIndexStore.search(query) +
                textStore.search(query)
            )
            .sortedWith(compareBy<SearchHit> { it.pageIndex }.thenBy { it.source.ordinal })
        selectedHit = selectedHit.coerceIn(0, (hits.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(pager.currentPage) {
        lassoSelectionActive = false
        activeTextBoxId = null
        activeImageId = null
        activeInkView = null
        onPageChanged(pager.currentPage)
    }

    if (showPageJump) {
        AlertDialog(
            onDismissRequest = { showPageJump = false },
            title = { Text(stringResource(R.string.dialog_jump_title)) },
            text = {
                OutlinedTextField(
                    value = pageJumpText,
                    onValueChange = { value ->
                        pageJumpText = value.filter { it.isDigit() }.take(6)
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_page_range, session.pageCount)) }
                )
            },
            confirmButton = {
                val target = pageJumpText.toIntOrNull()
                TextButton(
                    enabled = target != null && target in 1..session.pageCount,
                    onClick = {
                        val page = (pageJumpText.toIntOrNull() ?: 1) - 1
                        scope.launch { jumpToPage(page) }
                        showPageJump = false
                    }
                ) {
                    Text(stringResource(R.string.action_jump))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPageJump = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showAddLink) {
        AlertDialog(
            onDismissRequest = { showAddLink = false },
            title = { Text(stringResource(R.string.dialog_add_link_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newLinkLabel,
                        onValueChange = { newLinkLabel = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.link_label)) }
                    )
                    OutlinedTextField(
                        value = newLinkTarget,
                        onValueChange = { newLinkTarget = it.filter(Char::isDigit).take(6) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.link_target_page)) }
                    )
                }
            },
            confirmButton = {
                val target = newLinkTarget.toIntOrNull()
                TextButton(
                    enabled = target != null && target in 1..session.pageCount,
                    onClick = {
                        val targetPage = (newLinkTarget.toIntOrNull() ?: 1) - 1
                        scope.launch {
                            navigationStore.addLink(
                                sourcePage = pager.currentPage,
                                targetPage = targetPage,
                                label = newLinkLabel,
                            )
                            navigationRevision++
                        }
                        newLinkLabel = ""
                        newLinkTarget = ""
                        showAddLink = false
                    }
                ) {
                    Text(stringResource(R.string.action_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLink = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showAddOutline) {
        AlertDialog(
            onDismissRequest = { showAddOutline = false },
            title = { Text(stringResource(R.string.dialog_add_outline_title)) },
            text = {
                OutlinedTextField(
                    value = newOutlineTitle,
                    onValueChange = { newOutlineTitle = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.outline_title_label)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            navigationStore.addOutline(
                                pageIndex = pager.currentPage,
                                title = newOutlineTitle,
                            )
                            navigationRevision++
                        }
                        newOutlineTitle = ""
                        showAddOutline = false
                    }
                ) {
                    Text(stringResource(R.string.action_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddOutline = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showNavigationPanel) {
        NavigationDialog(
            currentPage = pager.currentPage,
            pageCount = session.pageCount,
            outline = remember(navigationRevision) { navigationStore.allOutline() },
            pageLinks = remember(navigationRevision, pager.currentPage) {
                navigationStore.linksForPage(pager.currentPage)
            },
            onDismiss = { showNavigationPanel = false },
            onAddLink = {
                newLinkTarget = (pager.currentPage + 1).toString()
                showAddLink = true
            },
            onAddOutline = {
                newOutlineTitle = ""
                showAddOutline = true
            },
            onNavigate = { page ->
                showNavigationPanel = false
                scope.launch { jumpToPage(page) }
            },
            onDeleteLink = { id ->
                scope.launch {
                    navigationStore.deleteLink(id)
                    navigationRevision++
                }
            },
            onDeleteOutline = { id ->
                scope.launch {
                    navigationStore.deleteOutline(id)
                    navigationRevision++
                }
            },
        )
    }

    if (showPageOverview) {
        PageOverviewDialog(
            session = session,
            currentPage = pager.currentPage,
            bookmarks = bookmarks,
            isNotebook = isNotebook,
            canManagePages = canManagePages,
            onDismiss = { showPageOverview = false },
            onSelectPage = { page ->
                showPageOverview = false
                scope.launch { jumpToPage(page) }
            },
            onDuplicatePage = { page ->
                showPageOverview = false
                onDuplicatePage(page)
            },
            onDeletePage = { page ->
                showPageOverview = false
                onDeletePage(page)
            },
            onChangePageTemplate = { page, template ->
                showPageOverview = false
                onChangePageTemplate(page, template)
            },
            onMovePage = { from, to ->
                showPageOverview = false
                onMovePage(from, to)
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back_library))
                    }
                    Text(
                        documentTitle,
                        modifier = Modifier.width(132.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge
                    )
                    LanguageMenu()
                    TextButton(onClick = { showNavigationPanel = true }) {
                        Text(
                            stringResource(R.string.navigation_title),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    TextButton(
                        onClick = if (splitMode) onCloseSplit else onRequestSplit
                    ) {
                        Text(
                            stringResource(
                                if (splitMode) R.string.action_close_split
                                else R.string.action_split_view
                            ),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    IconButton(onClick = onOpenPdf) {
                        Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.cd_import_another_pdf))
                    }
                    if (exportRunning) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                            Text(
                                "${exportCompleted}/${exportTotal.coerceAtLeast(1)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            IconButton(onClick = { exportJob?.cancel() }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_cancel_export))
                            }
                        }
                    } else {
                        IconButton(
                            onClick = {
                                exportLauncher.launch(
                                    context.getString(
                                        R.string.export_filename,
                                        documentTitle.take(80).ifBlank { "MuNote" }
                                    )
                                )
                            }
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.cd_export_annotated_pdf))
                        }
                    }
                    IconButton(onClick = { showThumbnails = !showThumbnails }) {
                        Icon(Icons.Default.List, contentDescription = stringResource(R.string.cd_page_thumbnails))
                    }
                    if (showThumbnails) {
                        IconButton(
                            enabled = bookmarks.isNotEmpty(),
                            onClick = { showBookmarksOnly = !showBookmarksOnly }
                        ) {
                            Icon(
                                if (showBookmarksOnly) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (showBookmarksOnly) {
                                    stringResource(R.string.cd_show_all_pages)
                                } else {
                                    stringResource(R.string.cd_bookmarks_only)
                                }
                            )
                        }
                    }
                    IconButton(onClick = { onToggleBookmark(pager.currentPage) }) {
                        Icon(
                            if (pager.currentPage in bookmarks) Icons.Default.Star
                            else Icons.Default.StarBorder,
                            contentDescription = if (pager.currentPage in bookmarks) {
                                stringResource(R.string.cd_remove_bookmark)
                            } else {
                                stringResource(R.string.cd_add_bookmark)
                            }
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            selectedHit = 0
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(18.dp)
                    )
                    if (hits.isNotEmpty()) {
                        IconButton(onClick = { scope.launch { goToHit(selectedHit - 1) } }) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_previous_search_result))
                        }
                        Text(
                            "${selectedHit + 1}/${hits.size}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        IconButton(onClick = { scope.launch { goToHit(selectedHit + 1) } }) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_search_result))
                        }
                    }
                    if (ocrRunning) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                            Text(
                                "${ocrDone}/${session.pageCount}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    when (handwritingModelState) {
                        HandwritingModelState.DOWNLOADING -> Text(
                            stringResource(R.string.handwriting_model_downloading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                        HandwritingModelState.ERROR -> Text(
                            stringResource(R.string.handwriting_unavailable),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                        else -> Unit
                    }
                }

                if (query.isNotBlank()) {
                    if (hits.isEmpty()) {
                        Text(
                            when {
                                ocrRunning -> stringResource(R.string.search_pdf_processing)
                                handwritingModelState == HandwritingModelState.DOWNLOADING ->
                                    stringResource(R.string.search_handwriting_model_downloading)
                                else -> stringResource(R.string.search_no_results)
                            },
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            hits.forEachIndexed { index, hit ->
                                AssistChip(
                                    onClick = { scope.launch { goToHit(index) } },
                                    label = {
                                        val sourceLabel = when (hit.source) {
                                            SearchSource.HANDWRITING ->
                                                stringResource(R.string.search_source_handwriting)
                                            SearchSource.TEXT ->
                                                stringResource(R.string.search_source_text)
                                            SearchSource.PDF ->
                                                stringResource(R.string.search_source_pdf)
                                        }
                                        Text(
                                            stringResource(
                                                R.string.search_result_label,
                                                hit.pageIndex + 1,
                                                sourceLabel,
                                                hit.snippet
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (showThumbnails) {
                ThumbnailRail(
                    session = session,
                    currentPage = pager.currentPage,
                    bookmarks = bookmarks,
                    showBookmarksOnly = showBookmarksOnly,
                    onPageClick = { page ->
                        scope.launch { jumpToPage(page) }
                    }
                )
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                beyondViewportPageCount = 1,
                userScrollEnabled = false
            ) { page ->
                PdfInkPage(
                    session = session,
                    pageIndex = page,
                    tool = tool,
                    strokes = remember(inkRevision, page) { inkStore.page(page) },
                    textBoxes = remember(textRevision, page) { textStore.page(page) },
                    images = remember(imageRevision, page) { imageStore.page(page) },
                    imageStore = imageStore,
                    links = remember(navigationRevision, page) {
                        navigationStore.linksForPage(page)
                    },
                    activeTextBoxId = activeTextBoxId,
                    activeImageId = activeImageId,
                    highlight = hits.getOrNull(selectedHit)?.takeIf { it.pageIndex == page },
                    inkColor = if (tool == InkTool.HIGHLIGHTER) highlighterColor else penColor,
                    penWidthDp = penWidth,
                    highlighterWidthDp = highlighterWidth,
                    shape = selectedShape,
                    brush = brush,
                    eraserMode = eraserMode,
                    eraserSizeDp = eraserSize,
                    fingerWritingEnabled = fingerWriting,
                    onShowPageOverview = ::openPageOverview,
                    onAddTextBox = { x, y ->
                        scope.launch {
                            val box = textStore.add(page, x, y)
                            activeTextBoxId = box.id
                            textRevision++
                        }
                    },
                    onUpdateTextBox = { box ->
                        scope.launch {
                            textStore.update(page, box)
                            textRevision++
                        }
                    },
                    onDeleteTextBox = { id ->
                        scope.launch {
                            textStore.delete(page, id)
                            if (activeTextBoxId == id) activeTextBoxId = null
                            textRevision++
                        }
                    },
                    onActivateTextBox = { id -> activeTextBoxId = id },
                    onUpdateImage = { image ->
                        scope.launch {
                            imageStore.update(page, image)
                            imageRevision++
                        }
                    },
                    onDeleteImage = { id ->
                        scope.launch {
                            imageStore.delete(page, id)
                            if (activeImageId == id) activeImageId = null
                            imageRevision++
                        }
                    },
                    onActivateImage = { id -> activeImageId = id },
                    onSaveSticker = { image ->
                        scope.launch {
                            runCatching {
                                stickerStore.saveFromImage(
                                    source = imageStore.imageFile(image),
                                    image = image,
                                )
                            }.onSuccess {
                                stickerRevision++
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_sticker_saved),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    error.message ?: context.getString(R.string.error_sticker_save),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onNavigateLink = { target ->
                        scope.launch { jumpToPage(target) }
                    },
                    onUpdateLink = { link ->
                        scope.launch {
                            navigationStore.updateLink(link)
                            navigationRevision++
                        }
                    },
                    onViewReady = { view ->
                        if (page == pager.currentPage) activeInkView = view
                    },
                    onSelectionChanged = { selected ->
                        if (page == pager.currentPage) lassoSelectionActive = selected
                    },
                    onPageSwipe = { direction ->
                        scope.launch {
                            pager.animateScrollToPage(
                                (pager.currentPage + direction).coerceIn(0, session.pageCount - 1)
                            )
                        }
                    },
                    onStroke = { stroke ->
                        scope.launch {
                            inkStore.append(page, stroke)
                            inkRevision++
                        }
                    },
                    onMutated = { strokes ->
                        scope.launch {
                            inkStore.replacePage(page, strokes)
                            inkRevision++
                        }
                    }
                )
            }
        }

        if (tool == InkTool.SHAPE || tool == InkTool.RULER) {
            ShapeOptionsBar(
                tool = tool,
                selectedShape = selectedShape,
                onShapeSelected = { selectedShape = it },
            )
        }

        if (tool == InkTool.IMAGE) {
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = { imagePicker.launch(arrayOf("image/*")) }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_insert_image))
                    }
                    TextButton(onClick = { showStickerPicker = true }) {
                        Icon(Icons.Default.StarBorder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_stickers))
                    }
                    Text(
                        stringResource(R.string.image_edit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (tool == InkTool.ERASER) {
            EraserOptionsBar(
                mode = eraserMode,
                sizeDp = eraserSize,
                onMode = {
                    eraserMode = it
                    inputPrefs.edit().putString("eraser_mode", it.name).apply()
                },
                onSize = {
                    eraserSize = it
                    inputPrefs.edit().putFloat("eraser_size", it).apply()
                },
                onClearPage = {
                    scope.launch {
                        inkStore.replacePage(pager.currentPage, emptyList())
                        inkRevision++
                    }
                },
            )
        }

        if (showPenOptions && (tool == InkTool.PEN || tool == InkTool.HIGHLIGHTER)) {
            PenOptionsBar(
                tool = tool,
                penColor = penColor,
                highlighterColor = highlighterColor,
                penWidth = penWidth,
                highlighterWidth = highlighterWidth,
                brush = brush,
                favoritePenColors = favoritePenColors,
                favoritePenWidths = favoritePenWidths,
                onBrush = {
                    brush = it
                    inputPrefs.edit().putString("pen_brush", it.name).apply()
                },
                onPenColor = {
                    penColor = it
                    inputPrefs.edit().putInt("pen_color", it).apply()
                },
                onHighlighterColor = {
                    highlighterColor = it
                    inputPrefs.edit().putInt("highlighter_color", it).apply()
                },
                onPenWidth = {
                    penWidth = it
                    inputPrefs.edit().putFloat("pen_width", it).apply()
                },
                onHighlighterWidth = {
                    highlighterWidth = it
                    inputPrefs.edit().putFloat("highlighter_width", it).apply()
                },
                onToggleColorFavorite = {
                    if (!favoritePenColors.remove(penColor)) favoritePenColors += penColor
                    while (favoritePenColors.size > 8) favoritePenColors.removeAt(0)
                    persistColorFavorites()
                },
                onToggleWidthFavorite = {
                    val existing = favoritePenWidths.indexOfFirst { kotlin.math.abs(it - penWidth) < 0.001f }
                    if (existing >= 0) favoritePenWidths.removeAt(existing) else favoritePenWidths += penWidth
                    while (favoritePenWidths.size > 6) favoritePenWidths.removeAt(0)
                    persistWidthFavorites()
                },
            )
        }

        Surface(
            tonalElevation = 3.dp,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                ToolButton(
                    selected = fingerWriting,
                    label = if (fingerWriting) {
                        stringResource(R.string.input_mode_finger)
                    } else {
                        stringResource(R.string.input_mode_pen)
                    },
                    icon = { Icon(Icons.Default.TouchApp, contentDescription = null) },
                    onClick = {
                        fingerWriting = !fingerWriting
                        inputPrefs.edit().putBoolean("finger_writing", fingerWriting).apply()
                    }
                )
                if (pageHistory.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            scope.launch { goBackInPageHistory() }
                        }
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = stringResource(R.string.cd_back_previous_location)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                ToolButton(
                    selected = tool == InkTool.PEN,
                    label = stringResource(R.string.tool_pen),
                    icon = { Icon(Icons.Default.Brush, contentDescription = null) },
                    onClick = {
                        if (tool == InkTool.PEN) showPenOptions = !showPenOptions
                        else {
                            tool = InkTool.PEN
                            showPenOptions = false
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                ToolButton(
                    selected = tool == InkTool.HIGHLIGHTER,
                    label = stringResource(R.string.tool_highlighter),
                    icon = { Text("▰") },
                    onClick = {
                        if (tool == InkTool.HIGHLIGHTER) showPenOptions = !showPenOptions
                        else {
                            tool = InkTool.HIGHLIGHTER
                            showPenOptions = false
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                ToolButton(
                    selected = tool == InkTool.ERASER,
                    label = stringResource(R.string.tool_eraser),
                    icon = { Icon(Icons.Default.Clear, contentDescription = null) },
                    onClick = {
                        tool = InkTool.ERASER
                        showPenOptions = false
                    }
                )
                Spacer(Modifier.width(8.dp))
                ToolButton(
                    selected = tool == InkTool.LASSO,
                    label = stringResource(R.string.tool_lasso),
                    icon = { Icon(Icons.Default.Gesture, contentDescription = null) },
                    onClick = {
                        tool = InkTool.LASSO
                        showPenOptions = false
                    }
                )
                Spacer(Modifier.width(8.dp))
                ToolButton(
                    selected = tool == InkTool.TEXT,
                    label = stringResource(R.string.tool_text),
                    icon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                    onClick = {
                        tool = InkTool.TEXT
                        showPenOptions = false
                    }
                )
                Spacer(Modifier.width(8.dp))
                ToolButton(
                    selected = tool == InkTool.IMAGE,
                    label = stringResource(R.string.tool_image),
                    icon = { Icon(Icons.Default.Image, contentDescription = null) },
                    onClick = {
                        tool = InkTool.IMAGE
                        showPenOptions = false
                        activeTextBoxId = null
                    }
                )
                Spacer(Modifier.width(8.dp))
                ToolButton(
                    selected = tool == InkTool.SHAPE,
                    label = stringResource(R.string.tool_shape),
                    icon = { Text("△○") },
                    onClick = {
                        tool = InkTool.SHAPE
                        showPenOptions = false
                    }
                )
                Spacer(Modifier.width(8.dp))
                ToolButton(
                    selected = tool == InkTool.RULER,
                    label = stringResource(R.string.tool_ruler),
                    icon = { Text("╱") },
                    onClick = {
                        tool = InkTool.RULER
                        showPenOptions = false
                    }
                )
                if (tool == InkTool.LASSO && lassoSelectionActive) {
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { activeInkView?.duplicateSelection() }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.cd_copy_selection))
                    }
                    IconButton(onClick = { activeInkView?.scaleSelection(0.9f) }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = stringResource(R.string.cd_shrink_selection))
                    }
                    IconButton(onClick = { activeInkView?.scaleSelection(1.1f) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = stringResource(R.string.cd_grow_selection))
                    }
                    IconButton(onClick = { activeInkView?.deleteSelection() }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_selection))
                    }
                }
                Spacer(Modifier.width(14.dp))
                IconButton(
                    enabled = inkStore.canUndo(pager.currentPage),
                    onClick = {
                        scope.launch {
                            inkStore.undo(pager.currentPage)
                            inkRevision++
                        }
                    }
                ) {
                    Icon(Icons.Default.Undo, contentDescription = stringResource(R.string.cd_undo))
                }
                IconButton(
                    enabled = inkStore.canRedo(pager.currentPage),
                    onClick = {
                        scope.launch {
                            inkStore.redo(pager.currentPage)
                            inkRevision++
                        }
                    }
                ) {
                    Icon(Icons.Default.Redo, contentDescription = stringResource(R.string.cd_redo))
                }
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = {
                        scope.launch {
                            jumpToPage((pager.currentPage - 1).coerceAtLeast(0), rememberLocation = false)
                        }
                    }
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_previous_page))
                }
                Surface(
                    onClick = {
                        pageJumpText = (pager.currentPage + 1).toString()
                        showPageJump = true
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        "${pager.currentPage + 1} / ${session.pageCount}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            jumpToPage(
                                (pager.currentPage + 1).coerceAtMost(session.pageCount - 1),
                                rememberLocation = false
                            )
                        }
                    }
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = stringResource(R.string.cd_next_page))
                }
                if (isNotebook) {
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = onAddPage) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_add_page)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageOverviewDialog(
    session: PdfSession,
    currentPage: Int,
    bookmarks: Set<Int>,
    isNotebook: Boolean,
    canManagePages: Boolean,
    onDismiss: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onDuplicatePage: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onChangePageTemplate: (Int, PageTemplate) -> Unit,
    onMovePage: (Int, Int) -> Unit,
) {
    val pageOrder = remember(session.fingerprint, session.pageCount) {
        mutableStateListOf<Int>().apply { addAll(0 until session.pageCount) }
    }
    val gridState = rememberLazyGridState()
    var draggedPage by remember { mutableIntStateOf(-1) }
    var draggedSlot by remember { mutableIntStateOf(-1) }

    fun itemAt(x: Float, y: Float): Int? =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            x >= item.offset.x &&
                x <= item.offset.x + item.size.width &&
                y >= item.offset.y &&
                y <= item.offset.y + item.size.height
        }?.index

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.page_overview_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            when {
                                isNotebook ->
                                    stringResource(R.string.page_overview_manage_hint)
                                canManagePages ->
                                    stringResource(R.string.page_overview_manage_pdf_hint)
                                else ->
                                    stringResource(R.string.page_overview_hint)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    state = gridState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(canManagePages, session.pageCount) {
                            if (!canManagePages) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val slot = itemAt(offset.x, offset.y) ?: return@detectDragGesturesAfterLongPress
                                    draggedSlot = slot
                                    draggedPage = pageOrder.getOrElse(slot) { -1 }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val target = itemAt(change.position.x, change.position.y)
                                        ?: return@detectDragGesturesAfterLongPress
                                    if (
                                        draggedSlot >= 0 &&
                                        target in pageOrder.indices &&
                                        target != draggedSlot
                                    ) {
                                        val moving = pageOrder.removeAt(draggedSlot)
                                        pageOrder.add(target, moving)
                                        draggedSlot = target
                                    }
                                },
                                onDragCancel = {
                                    draggedPage = -1
                                    draggedSlot = -1
                                },
                                onDragEnd = {
                                    val from = draggedPage
                                    val to = draggedSlot
                                    draggedPage = -1
                                    draggedSlot = -1
                                    if (from >= 0 && to >= 0 && from != to) {
                                        onMovePage(from, to)
                                    }
                                }
                            )
                        },
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        count = pageOrder.size,
                        key = { pageOrder[it] }
                    ) { slot ->
                        val page = pageOrder[slot]
                        var menuExpanded by remember(page) { mutableStateOf(false) }

                        Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = if (page == draggedPage) 0.72f else 1f
                            }
                        ) {
                            ThumbnailCard(
                                session = session,
                                pageIndex = page,
                                selected = page == currentPage,
                                bookmarked = page in bookmarks,
                                onClick = { onSelectPage(page) }
                            )

                            if (canManagePages) {
                                Box(Modifier.align(Alignment.TopEnd)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                    ) {
                                        IconButton(
                                            onClick = { menuExpanded = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = stringResource(
                                                    R.string.cd_page_actions,
                                                    page + 1
                                                )
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_duplicate_page)) },
                                            leadingIcon = {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onDuplicatePage(page)
                                            }
                                        )
                                        if (isNotebook) {
                                            listOf(
                                                PageTemplate.BLANK to R.string.template_blank,
                                                PageTemplate.RULED to R.string.template_ruled,
                                                PageTemplate.GRID to R.string.template_grid,
                                                PageTemplate.DOT to R.string.template_dot,
                                            ).forEach { (template, label) ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            stringResource(
                                                                R.string.action_set_page_template,
                                                                stringResource(label)
                                                            )
                                                        )
                                                    },
                                                    onClick = {
                                                        menuExpanded = false
                                                        onChangePageTemplate(page, template)
                                                    }
                                                )
                                            }
                                        }
                                        if (session.pageCount > 1) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.action_delete_page)) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Delete, contentDescription = null)
                                                },
                                                onClick = {
                                                    menuExpanded = false
                                                    onDeletePage(page)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailRail(
    session: PdfSession,
    currentPage: Int,
    bookmarks: Set<Int>,
    showBookmarksOnly: Boolean,
    onPageClick: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val visiblePages = remember(session.pageCount, bookmarks, showBookmarksOnly) {
        if (showBookmarksOnly) bookmarks.sorted()
        else (0 until session.pageCount).toList()
    }

    LaunchedEffect(currentPage, showBookmarksOnly, visiblePages) {
        if (!listState.isScrollInProgress) {
            val target = visiblePages.indexOf(currentPage)
            if (target >= 0) listState.animateScrollToItem(target)
        }
    }

    Surface(
        modifier = Modifier
            .width(116.dp)
            .fillMaxHeight(),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                count = visiblePages.size,
                key = { visiblePages[it] }
            ) { index ->
                val page = visiblePages[index]
                ThumbnailCard(
                    session = session,
                    pageIndex = page,
                    selected = page == currentPage,
                    bookmarked = page in bookmarks,
                    onClick = { onPageClick(page) }
                )
            }
        }
    }
}

@Composable
private fun ThumbnailCard(
    session: PdfSession,
    pageIndex: Int,
    selected: Boolean,
    bookmarked: Boolean,
    onClick: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, session.fingerprint, pageIndex) {
        value = withContext(Dispatchers.IO) {
            session.renderPage(pageIndex, 220)
        }
    }

    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = border,
        color = Color.White,
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val bmp = bitmap
            if (bmp == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(118.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            } else {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_page_thumbnail, pageIndex + 1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat()),
                    contentScale = ContentScale.FillBounds
                )
            }
            Row(
                modifier = Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (bookmarked) {
                    Text(
                        "★",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    "${pageIndex + 1}",
                    color = Color(0xFF515151),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun PenOptionsBar(
    tool: InkTool,
    penColor: Int,
    highlighterColor: Int,
    penWidth: Float,
    highlighterWidth: Float,
    brush: InkBrush,
    favoritePenColors: List<Int>,
    favoritePenWidths: List<Float>,
    onBrush: (InkBrush) -> Unit,
    onPenColor: (Int) -> Unit,
    onHighlighterColor: (Int) -> Unit,
    onPenWidth: (Float) -> Unit,
    onHighlighterWidth: (Float) -> Unit,
    onToggleColorFavorite: () -> Unit,
    onToggleWidthFavorite: () -> Unit,
) {
    val isHighlighter = tool == InkTool.HIGHLIGHTER
    val currentWidth = if (isHighlighter) highlighterWidth else penWidth
    val defaultWidths = if (isHighlighter) {
        listOf(8f, 12f, 16f)
    } else {
        listOf(1.0f, 1.4f, 2.15f, 3.0f, 4.0f)
    }
    val widths = if (isHighlighter) {
        defaultWidths
    } else {
        (favoritePenWidths + defaultWidths).distinct().take(10)
    }
    val defaultColors = if (isHighlighter) {
        listOf(
            0xFFFFD54F.toInt(),
            0xFF80DEEA.toInt(),
            0xFFF48FB1.toInt(),
            0xFFA5D6A7.toInt(),
            0xFFCE93D8.toInt(),
        )
    } else {
        listOf(
            0xFF1C1D1F.toInt(),
            0xFF3157C8.toInt(),
            0xFFC93C3C.toInt(),
            0xFF26805A.toInt(),
            0xFF7E57C2.toInt(),
            0xFF7A4E2D.toInt(),
            0xFF616161.toInt(),
            0xFFE67E22.toInt(),
        )
    }
    val colors = if (isHighlighter) {
        defaultColors
    } else {
        (favoritePenColors + defaultColors).distinct().take(16)
    }
    val currentColor = if (isHighlighter) highlighterColor else penColor

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                if (isHighlighter) {
                    stringResource(R.string.tool_highlighter_full)
                } else {
                    stringResource(R.string.tool_pen)
                },
                style = MaterialTheme.typography.labelLarge
            )

            if (!isHighlighter) {
                listOf(
                    InkBrush.FOUNTAIN to R.string.brush_fountain,
                    InkBrush.BALLPOINT to R.string.brush_ballpoint,
                    InkBrush.PENCIL to R.string.brush_pencil,
                ).forEach { (candidate, label) ->
                    AssistChip(
                        onClick = { onBrush(candidate) },
                        label = {
                            Text(
                                (if (brush == candidate) "✓ " else "") +
                                    stringResource(label)
                            )
                        }
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            widths.forEach { width ->
                AssistChip(
                    onClick = {
                        if (isHighlighter) onHighlighterWidth(width) else onPenWidth(width)
                    },
                    label = {
                        Text(
                            if (isHighlighter) {
                                "${width.toInt()}"
                            } else {
                                String.format("%.2g", width)
                            }
                        )
                    },
                    leadingIcon = if (kotlin.math.abs(currentWidth - width) < 0.001f) {
                        { Text("●") }
                    } else null
                )
            }

            if (!isHighlighter) {
                IconButton(onClick = onToggleWidthFavorite) {
                    Icon(
                        if (favoritePenWidths.any {
                                kotlin.math.abs(it - currentWidth) < 0.001f
                            }) {
                            Icons.Default.Star
                        } else {
                            Icons.Default.StarBorder
                        },
                        contentDescription = stringResource(R.string.favorite_current_width)
                    )
                }
            }

            Spacer(Modifier.width(5.dp))
            colors.forEach { color ->
                ColorSwatch(
                    colorArgb = color,
                    selected = color == currentColor,
                    onClick = {
                        if (isHighlighter) onHighlighterColor(color) else onPenColor(color)
                    }
                )
            }

            if (!isHighlighter) {
                IconButton(onClick = onToggleColorFavorite) {
                    Icon(
                        if (currentColor in favoritePenColors) {
                            Icons.Default.Star
                        } else {
                            Icons.Default.StarBorder
                        },
                        contentDescription = stringResource(R.string.favorite_current_color)
                    )
                }
            }
        }
    }
}

@Composable
private fun EraserOptionsBar(
    mode: EraserMode,
    sizeDp: Float,
    onMode: (EraserMode) -> Unit,
    onSize: (Float) -> Unit,
    onClearPage: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.eraser_options),
                style = MaterialTheme.typography.labelLarge
            )
            AssistChip(
                onClick = { onMode(EraserMode.STROKE) },
                label = {
                    Text(
                        (if (mode == EraserMode.STROKE) "✓ " else "") +
                            stringResource(R.string.eraser_stroke)
                    )
                }
            )
            AssistChip(
                onClick = { onMode(EraserMode.PIXEL) },
                label = {
                    Text(
                        (if (mode == EraserMode.PIXEL) "✓ " else "") +
                            stringResource(R.string.eraser_pixel)
                    )
                }
            )
            listOf(10f, 18f, 30f, 46f).forEach { size ->
                AssistChip(
                    onClick = { onSize(size) },
                    label = { Text("${size.toInt()}") },
                    leadingIcon = if (kotlin.math.abs(sizeDp - size) < 0.001f) {
                        { Text("●") }
                    } else null
                )
            }
            TextButton(onClick = onClearPage) {
                Text(stringResource(R.string.eraser_clear_page))
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    colorArgb: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(30.dp),
        shape = CircleShape,
        color = Color(colorArgb),
        border = if (selected) {
            BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        content = {}
    )
}

@Composable
private fun ShapeOptionsBar(
    tool: InkTool,
    selectedShape: InkShape,
    onShapeSelected: (InkShape) -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tool == InkTool.RULER) {
                Text(
                    stringResource(R.string.ruler_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                listOf(
                    InkShape.LINE to R.string.shape_line,
                    InkShape.RECTANGLE to R.string.shape_rectangle,
                    InkShape.ELLIPSE to R.string.shape_ellipse,
                    InkShape.ARROW to R.string.shape_arrow,
                    InkShape.TRIANGLE to R.string.shape_triangle,
                ).forEach { (shape, label) ->
                    AssistChip(
                        onClick = { onShapeSelected(shape) },
                        label = {
                            Text(
                                (if (selectedShape == shape) "✓ " else "") +
                                    stringResource(label)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    selected: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TextBoxLayer(
    boxes: List<TextBoxNote>,
    editable: Boolean,
    activeTextBoxId: String?,
    onActivate: (String) -> Unit,
    onUpdate: (TextBoxNote) -> Unit,
    onDelete: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pageWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val pageHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        boxes.forEach { box ->
            val focusRequester = remember(box.id) { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            var localBox by remember(
                box.id,
                box.x,
                box.y,
                box.width,
                box.height,
                box.fontSizeSp
            ) { mutableStateOf(box) }
            var localText by remember(box.id, box.text) { mutableStateOf(box.text) }
            val isActive = activeTextBoxId == box.id
            val boxHeight = maxOf(56.dp, maxHeight * localBox.height)

            fun persistGeometry() {
                onUpdate(localBox.copy(text = localText))
            }

            LaunchedEffect(isActive, editable) {
                if (isActive && editable) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
            }

            LaunchedEffect(localText) {
                if (localText != box.text) {
                    delay(350)
                    onUpdate(localBox.copy(text = localText))
                }
            }

            Surface(
                modifier = Modifier
                    .offset(
                        x = maxWidth * localBox.x,
                        y = maxHeight * localBox.y
                    )
                    .width(maxWidth * localBox.width)
                    .height(boxHeight),
                shape = RoundedCornerShape(8.dp),
                color = if (editable) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                } else {
                    Color.Transparent
                },
                border = when {
                    isActive && editable ->
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    editable ->
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    else -> null
                }
            ) {
                Box(Modifier.fillMaxSize()) {
                    BasicTextField(
                        value = localText,
                        onValueChange = { localText = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                if (it.isFocused) onActivate(box.id)
                            }
                            .padding(
                                start = 8.dp,
                                top = if (editable && isActive) 34.dp else 8.dp,
                                end = if (editable) 32.dp else 8.dp,
                                bottom = if (editable && isActive) 28.dp else 8.dp
                            ),
                        readOnly = !editable,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = localBox.fontSizeSp.sp
                        )
                    )

                    if (editable) {
                        IconButton(
                            onClick = { onDelete(box.id) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_delete_text_box)
                            )
                        }
                    }

                    if (editable && isActive) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .height(26.dp)
                                .width(72.dp)
                                .pointerInput(box.id, pageWidthPx, pageHeightPx) {
                                    detectDragGestures(
                                        onDragEnd = { persistGeometry() },
                                        onDragCancel = {
                                            localBox = box
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        val dx = dragAmount.x / pageWidthPx
                                        val dy = dragAmount.y / pageHeightPx
                                        localBox = localBox.copy(
                                            x = (localBox.x + dx)
                                                .coerceIn(0f, (1f - localBox.width).coerceAtLeast(0f)),
                                            y = (localBox.y + dy)
                                                .coerceIn(0f, (1f - localBox.height).coerceAtLeast(0f)),
                                        )
                                    }
                                },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "↕↔",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 5.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    localBox = localBox.copy(
                                        fontSizeSp = (localBox.fontSizeSp - 2f).coerceAtLeast(10f)
                                    )
                                    persistGeometry()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("A−", style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(
                                onClick = {
                                    localBox = localBox.copy(
                                        fontSizeSp = (localBox.fontSizeSp + 2f).coerceAtMost(42f)
                                    )
                                    persistGeometry()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("A+", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(30.dp)
                                .pointerInput(box.id, pageWidthPx, pageHeightPx) {
                                    detectDragGestures(
                                        onDragEnd = { persistGeometry() },
                                        onDragCancel = { localBox = box }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        val dw = dragAmount.x / pageWidthPx
                                        val dh = dragAmount.y / pageHeightPx
                                        val maxWidth = (1f - localBox.x).coerceAtLeast(0.18f)
                                        val maxHeight = (1f - localBox.y).coerceAtLeast(0.08f)
                                        localBox = localBox.copy(
                                            width = (localBox.width + dw).coerceIn(0.18f, maxWidth),
                                            height = (localBox.height + dh).coerceIn(0.08f, maxHeight),
                                        )
                                    }
                                },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "↘",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerPickerDialog(
    stickers: List<StickerItem>,
    store: StickerStore,
    onDismiss: () -> Unit,
    onSelect: (StickerItem) -> Unit,
    onDelete: (StickerItem) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.76f),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.sticker_library_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            stringResource(R.string.sticker_library_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                }

                if (stickers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.sticker_library_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(118.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            count = stickers.size,
                            key = { stickers[it].id }
                        ) { index ->
                            val sticker = stickers[index]
                            val bitmap by produceState<Bitmap?>(
                                initialValue = null,
                                sticker.id,
                                sticker.fileName,
                                sticker.cropLeft,
                                sticker.cropTop,
                                sticker.cropRight,
                                sticker.cropBottom,
                            ) {
                                value = store.loadBitmap(sticker, 420)
                            }

                            Surface(
                                onClick = { onSelect(sticker) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    val bmp = bitmap
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = stringResource(
                                                R.string.sticker_item_description
                                            ),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDelete(sticker) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(
                                                R.string.sticker_delete
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageImageLayer(
    images: List<PageImageNote>,
    store: ImageStore,
    editable: Boolean,
    activeImageId: String?,
    onActivate: (String) -> Unit,
    onUpdate: (PageImageNote) -> Unit,
    onDelete: (String) -> Unit,
    onSaveSticker: (PageImageNote) -> Unit,
) {
    var cropTarget by remember { mutableStateOf<PageImageNote?>(null) }

    cropTarget?.let { target ->
        var draft by remember(
            target.id,
            target.cropLeft,
            target.cropTop,
            target.cropRight,
            target.cropBottom,
        ) { mutableStateOf(target) }

        AlertDialog(
            onDismissRequest = { cropTarget = null },
            title = { Text(stringResource(R.string.image_crop_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(stringResource(R.string.image_crop_left))
                    Slider(
                        value = draft.cropLeft,
                        onValueChange = {
                            draft = draft.copy(
                                cropLeft = it.coerceIn(0f, draft.cropRight - 0.05f)
                            )
                        },
                        valueRange = 0f..0.9f
                    )
                    Text(stringResource(R.string.image_crop_right))
                    Slider(
                        value = draft.cropRight,
                        onValueChange = {
                            draft = draft.copy(
                                cropRight = it.coerceIn(draft.cropLeft + 0.05f, 1f)
                            )
                        },
                        valueRange = 0.1f..1f
                    )
                    Text(stringResource(R.string.image_crop_top))
                    Slider(
                        value = draft.cropTop,
                        onValueChange = {
                            draft = draft.copy(
                                cropTop = it.coerceIn(0f, draft.cropBottom - 0.05f)
                            )
                        },
                        valueRange = 0f..0.9f
                    )
                    Text(stringResource(R.string.image_crop_bottom))
                    Slider(
                        value = draft.cropBottom,
                        onValueChange = {
                            draft = draft.copy(
                                cropBottom = it.coerceIn(draft.cropTop + 0.05f, 1f)
                            )
                        },
                        valueRange = 0.1f..1f
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdate(draft)
                        cropTarget = null
                    }
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { cropTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pageWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val pageHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        images.forEach { image ->
            var localImage by remember(
                image.id,
                image.x,
                image.y,
                image.width,
                image.height,
                image.rotationDegrees,
                image.locked,
                image.cropLeft,
                image.cropTop,
                image.cropRight,
                image.cropBottom,
            ) { mutableStateOf(image) }
            val isActive = editable && activeImageId == image.id
            val bitmap by produceState<Bitmap?>(
                initialValue = null,
                image.id,
                image.fileName,
                image.cropLeft,
                image.cropTop,
                image.cropRight,
                image.cropBottom,
            ) {
                value = store.loadBitmap(image, 1200)
            }

            Surface(
                onClick = {
                    if (editable) onActivate(image.id)
                },
                modifier = Modifier
                    .offset(
                        x = maxWidth * localImage.x,
                        y = maxHeight * localImage.y
                    )
                    .width(maxWidth * localImage.width)
                    .height(maxHeight * localImage.height)
                    .graphicsLayer { rotationZ = localImage.rotationDegrees }
                    .pointerInput(
                        editable,
                        localImage.locked,
                        image.id,
                        pageWidthPx,
                        pageHeightPx,
                    ) {
                        if (!editable || localImage.locked) return@pointerInput
                        detectDragGestures(
                            onDragEnd = { onUpdate(localImage) },
                            onDragCancel = { localImage = image }
                        ) { change, drag ->
                            change.consume()
                            localImage = localImage.copy(
                                x = (localImage.x + drag.x / pageWidthPx)
                                    .coerceIn(0f, (1f - localImage.width).coerceAtLeast(0f)),
                                y = (localImage.y + drag.y / pageHeightPx)
                                    .coerceIn(0f, (1f - localImage.height).coerceAtLeast(0f)),
                            )
                        }
                    },
                shape = RoundedCornerShape(4.dp),
                color = Color.Transparent,
                border = if (isActive) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else null
            ) {
                Box(Modifier.fillMaxSize()) {
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = stringResource(R.string.inserted_image),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    }

                    if (isActive) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    RoundedCornerShape(14.dp)
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val next = localImage.copy(
                                        rotationDegrees = localImage.rotationDegrees - 15f
                                    )
                                    localImage = next
                                    onUpdate(next)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.RotateLeft,
                                    contentDescription = stringResource(R.string.image_rotate_left)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val next = localImage.copy(
                                        rotationDegrees = localImage.rotationDegrees + 15f
                                    )
                                    localImage = next
                                    onUpdate(next)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.RotateRight,
                                    contentDescription = stringResource(R.string.image_rotate_right)
                                )
                            }
                            IconButton(
                                onClick = { cropTarget = localImage },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.Crop,
                                    contentDescription = stringResource(R.string.image_crop_title)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val next = localImage.copy(locked = !localImage.locked)
                                    localImage = next
                                    onUpdate(next)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    if (localImage.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = stringResource(
                                        if (localImage.locked) R.string.image_unlock else R.string.image_lock
                                    )
                                )
                            }
                            IconButton(
                                onClick = { onSaveSticker(localImage) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.StarBorder,
                                    contentDescription = stringResource(R.string.image_save_as_sticker)
                                )
                            }
                            IconButton(
                                onClick = { onDelete(localImage.id) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.image_delete)
                                )
                            }
                        }

                        if (!localImage.locked) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(30.dp)
                                    .pointerInput(
                                        image.id,
                                        pageWidthPx,
                                        pageHeightPx,
                                    ) {
                                        detectDragGestures(
                                            onDragEnd = { onUpdate(localImage) },
                                            onDragCancel = { localImage = image }
                                        ) { change, drag ->
                                            change.consume()
                                            localImage = localImage.copy(
                                                width = (localImage.width + drag.x / pageWidthPx)
                                                    .coerceIn(0.08f, 1f - localImage.x),
                                                height = (localImage.height + drag.y / pageHeightPx)
                                                    .coerceIn(0.08f, 1f - localImage.y),
                                            )
                                        }
                                    },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "↘",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationDialog(
    currentPage: Int,
    pageCount: Int,
    outline: List<OutlineEntry>,
    pageLinks: List<PageLink>,
    onDismiss: () -> Unit,
    onAddLink: () -> Unit,
    onAddOutline: () -> Unit,
    onNavigate: (Int) -> Unit,
    onDeleteLink: (String) -> Unit,
    onDeleteOutline: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .fillMaxHeight(0.78f),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.navigation_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            stringResource(
                                R.string.navigation_current_page,
                                currentPage + 1,
                                pageCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = onAddOutline,
                        label = { Text(stringResource(R.string.action_add_outline)) }
                    )
                    AssistChip(
                        onClick = onAddLink,
                        label = { Text(stringResource(R.string.action_add_link)) }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Text(
                            stringResource(R.string.outline_section),
                            modifier = Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    if (outline.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.outline_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        items(
                            count = outline.size,
                            key = { "outline-" + outline[it].id }
                        ) { index ->
                            val entry = outline[index]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { onNavigate(entry.pageIndex) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "${entry.title} · ${entry.pageIndex + 1}",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                IconButton(onClick = { onDeleteOutline(entry.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            stringResource(R.string.page_links_section),
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    if (pageLinks.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.page_links_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        items(
                            count = pageLinks.size,
                            key = { "link-" + pageLinks[it].id }
                        ) { index ->
                            val link = pageLinks[index]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { onNavigate(link.targetPage) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "${link.label} → ${link.targetPage + 1}",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                IconButton(onClick = { onDeleteLink(link.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageLinkLayer(
    links: List<PageLink>,
    onNavigate: (Int) -> Unit,
    onUpdate: (PageLink) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pageWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val pageHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        links.forEach { link ->
            var localLink by remember(link.id, link.x, link.y, link.width) {
                mutableStateOf(link)
            }

            Surface(
                onClick = { onNavigate(localLink.targetPage) },
                modifier = Modifier
                    .offset(
                        x = maxWidth * localLink.x,
                        y = maxHeight * localLink.y
                    )
                    .width(maxWidth * localLink.width)
                    .pointerInput(link.id, pageWidthPx, pageHeightPx) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { onUpdate(localLink) },
                            onDragCancel = { localLink = link },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                localLink = localLink.copy(
                                    x = (
                                        localLink.x + dragAmount.x / pageWidthPx
                                        ).coerceIn(
                                            0f,
                                            (1f - localLink.width).coerceAtLeast(0f)
                                        ),
                                    y = (
                                        localLink.y + dragAmount.y / pageHeightPx
                                        ).coerceIn(0f, 0.94f),
                                )
                            }
                        )
                    },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                tonalElevation = 2.dp
            ) {
                Text(
                    "↗ ${localLink.label}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PdfInkPage(
    session: PdfSession,
    pageIndex: Int,
    tool: InkTool,
    strokes: List<dev.munote.app.ink.InkStroke>,
    textBoxes: List<TextBoxNote>,
    images: List<PageImageNote>,
    imageStore: ImageStore,
    links: List<PageLink>,
    activeTextBoxId: String?,
    activeImageId: String?,
    highlight: SearchHit?,
    inkColor: Int,
    penWidthDp: Float,
    highlighterWidthDp: Float,
    shape: InkShape,
    brush: InkBrush,
    eraserMode: EraserMode,
    eraserSizeDp: Float,
    fingerWritingEnabled: Boolean,
    onShowPageOverview: () -> Unit,
    onAddTextBox: (Float, Float) -> Unit,
    onUpdateTextBox: (TextBoxNote) -> Unit,
    onDeleteTextBox: (String) -> Unit,
    onActivateTextBox: (String) -> Unit,
    onUpdateImage: (PageImageNote) -> Unit,
    onDeleteImage: (String) -> Unit,
    onActivateImage: (String) -> Unit,
    onSaveSticker: (PageImageNote) -> Unit,
    onNavigateLink: (Int) -> Unit,
    onUpdateLink: (PageLink) -> Unit,
    onViewReady: (InkCanvasView) -> Unit,
    onSelectionChanged: (Boolean) -> Unit,
    onPageSwipe: (Int) -> Unit,
    onStroke: (dev.munote.app.ink.InkStroke) -> Unit,
    onMutated: (List<dev.munote.app.ink.InkStroke>) -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, session.fingerprint, pageIndex) {
        value = withContext(Dispatchers.IO) {
            session.renderPage(pageIndex, 1800)
        }
    }

    var scale by remember(pageIndex) { mutableStateOf(1f) }
    var pan by remember(pageIndex) { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp == null) {
            CircularProgressIndicator()
            return@BoxWithConstraints
        }

        val density = LocalDensity.current
        val pageRatio = bmp.width.toFloat() / bmp.height.toFloat()
        val containerRatio = maxWidth.value / maxHeight.value
        val pageWidthPx: Float
        val pageHeightPx: Float
        val pageModifier: Modifier

        if (containerRatio <= pageRatio) {
            pageWidthPx = with(density) { maxWidth.toPx() }
            pageHeightPx = pageWidthPx / pageRatio
            pageModifier = Modifier.fillMaxWidth().aspectRatio(pageRatio)
        } else {
            pageHeightPx = with(density) { maxHeight.toPx() }
            pageWidthPx = pageHeightPx * pageRatio
            pageModifier = Modifier.fillMaxHeight().aspectRatio(pageRatio)
        }

        fun clampPan(candidate: Offset, zoom: Float): Offset {
            if (zoom <= 1.001f) return Offset.Zero
            val maxX = (pageWidthPx * (zoom - 1f) / 2f).coerceAtLeast(0f)
            val maxY = (pageHeightPx * (zoom - 1f) / 2f).coerceAtLeast(0f)
            return Offset(
                candidate.x.coerceIn(-maxX, maxX),
                candidate.y.coerceIn(-maxY, maxY)
            )
        }

        fun applyTransform(zoomChange: Float, panChange: Offset) {
            val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
            if (nextScale <= 1.01f) {
                scale = 1f
                pan = Offset.Zero
            } else {
                scale = nextScale
                pan = clampPan(pan + panChange, nextScale)
            }
        }

        val gestureModifier = Modifier.pointerInput(pageIndex, fingerWritingEnabled, tool) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)

                    // Stylus/eraser input is owned by InkCanvasView. This recognizer is finger-only.
                    if (first.type != PointerType.Touch) {
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            pressed = event.changes.any { it.pressed }
                        }
                        return@awaitEachGesture
                    }

                    var pinched = false
                    var threeFingerGesture = false
                    var overviewTriggered = false
                    var threeFingerZoom = 1f
                    var twoFingerZoom = 1f
                    var twoFingerPanX = 0f
                    var twoFingerPanY = 0f
                    var dragX = 0f
                    var dragY = 0f
                    var pressed = true

                    while (pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val down = event.changes.filter { it.pressed }
                        pressed = down.isNotEmpty()
                        if (!pressed) break

                        when {
                            down.size >= 3 -> {
                                threeFingerGesture = true
                                val zoomChange = event.calculateZoom()
                                if (zoomChange.isFinite() && zoomChange > 0f) {
                                    threeFingerZoom *= zoomChange
                                }
                                if (!overviewTriggered && threeFingerZoom < 0.72f) {
                                    overviewTriggered = true
                                    onShowPageOverview()
                                }
                                event.changes.forEach { it.consume() }
                            }

                            down.size == 2 -> {
                                pinched = true
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                if (zoomChange.isFinite() && zoomChange > 0f) {
                                    twoFingerZoom *= zoomChange
                                }
                                twoFingerPanX += panChange.x
                                twoFingerPanY += panChange.y

                                // When the page is already enlarged, two fingers pan it normally.
                                // At fit-to-page scale we still collect the drag so a horizontal
                                // two-finger swipe can turn pages in Touch mode.
                                applyTransform(zoomChange, panChange)
                                event.changes.forEach { it.consume() }
                            }

                            down.size == 1 && !fingerWritingEnabled && tool != InkTool.TEXT -> {
                                val change = down.first()
                                val delta = change.positionChange()

                                if (pinched || scale > 1.01f) {
                                    applyTransform(1f, delta)
                                } else {
                                    dragX += delta.x
                                    dragY += delta.y
                                }
                                change.consume()
                            }
                        }
                    }

                    val thresholdPx = with(density) { 72.dp.toPx() }

                    if (
                        !fingerWritingEnabled &&
                        tool != InkTool.TEXT && tool != InkTool.IMAGE &&
                        !pinched &&
                        !threeFingerGesture &&
                        scale <= 1.01f &&
                        abs(dragX) >= thresholdPx &&
                        abs(dragX) > abs(dragY) * 1.15f
                    ) {
                        onPageSwipe(if (dragX < 0f) 1 else -1)
                    }

                    // Touch mode reserves one finger for writing, so page turning needs a
                    // two-finger gesture. Only treat it as a page swipe when there was little
                    // actual pinch zoom; otherwise the user's intent was zooming.
                    if (
                        pinched &&
                        !threeFingerGesture &&
                        scale <= 1.01f &&
                        twoFingerZoom in 0.88f..1.12f &&
                        abs(twoFingerPanX) >= thresholdPx &&
                        abs(twoFingerPanX) > abs(twoFingerPanY) * 1.15f
                    ) {
                        onPageSwipe(if (twoFingerPanX < 0f) 1 else -1)
                    }

                if (scale <= 1.01f) {
                    scale = 1f
                    pan = Offset.Zero
                } else {
                    pan = clampPan(pan, scale)
                }
            }
        }

        Surface(
            modifier = pageModifier
                .then(gestureModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y
                },
            shadowElevation = 3.dp,
            shape = RoundedCornerShape(4.dp),
            color = Color.White
        ) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_pdf_page, pageIndex + 1),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                val rect = highlight?.rect
                if (rect != null) {
                    val highlightFill = when (highlight.source) {
                        SearchSource.HANDWRITING -> Color(0x5538BDF8)
                        SearchSource.TEXT -> Color(0x5534D399)
                        SearchSource.PDF -> Color(0x66FFD54F)
                    }
                    val highlightStroke = when (highlight.source) {
                        SearchSource.HANDWRITING -> Color(0xCC0284C7)
                        SearchSource.TEXT -> Color(0xCC059669)
                        SearchSource.PDF -> Color(0xCCF59E0B)
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        val left = rect.left * size.width
                        val top = rect.top * size.height
                        val right = rect.right * size.width
                        val bottom = rect.bottom * size.height
                        drawRect(
                            color = highlightFill,
                            topLeft = Offset(left, top),
                            size = Size(
                                (right - left).coerceAtLeast(2f),
                                (bottom - top).coerceAtLeast(2f)
                            )
                        )
                        drawRect(
                            color = highlightStroke,
                            topLeft = Offset(left, top),
                            size = Size(
                                (right - left).coerceAtLeast(2f),
                                (bottom - top).coerceAtLeast(2f)
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                if (tool != InkTool.IMAGE) {
                    PageImageLayer(
                        images = images,
                        store = imageStore,
                        editable = false,
                        activeImageId = activeImageId,
                        onActivate = onActivateImage,
                        onUpdate = onUpdateImage,
                        onDelete = onDeleteImage,
                        onSaveSticker = onSaveSticker,
                    )
                }

                AndroidView(
                    factory = { ctx ->
                        InkCanvasView(ctx).apply {
                            this.tool = tool
                            this.inkColor = inkColor
                            this.penWidthDp = penWidthDp
                            this.highlighterWidthDp = highlighterWidthDp
                            this.shape = shape
                            this.brush = brush
                            this.eraserMode = eraserMode
                            this.eraserSizeDp = eraserSizeDp
                            this.fingerWritingEnabled = fingerWritingEnabled
                            setStrokes(strokes)
                            onStrokeCommitted = onStroke
                            onPageMutated = onMutated
                            this.onSelectionChanged = onSelectionChanged
                            onViewReady(this)
                        }
                    },
                    update = { view ->
                        view.tool = tool
                        view.inkColor = inkColor
                        view.penWidthDp = penWidthDp
                        view.highlighterWidthDp = highlighterWidthDp
                        view.shape = shape
                        view.brush = brush
                        view.eraserMode = eraserMode
                        view.eraserSizeDp = eraserSizeDp
                        view.fingerWritingEnabled = fingerWritingEnabled
                        view.onStrokeCommitted = onStroke
                        view.onPageMutated = onMutated
                        view.onSelectionChanged = onSelectionChanged
                        onViewReady(view)
                        if (view.snapshot() != strokes) view.setStrokes(strokes)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (tool == InkTool.IMAGE) {
                    PageImageLayer(
                        images = images,
                        store = imageStore,
                        editable = true,
                        activeImageId = activeImageId,
                        onActivate = onActivateImage,
                        onUpdate = onUpdateImage,
                        onDelete = onDeleteImage,
                        onSaveSticker = onSaveSticker,
                    )
                }

                if (tool == InkTool.TEXT) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(pageIndex, textBoxes.size) {
                                detectTapGestures { offset ->
                                    onAddTextBox(
                                        (offset.x / size.width).coerceIn(0f, 1f),
                                        (offset.y / size.height).coerceIn(0f, 1f)
                                    )
                                }
                            }
                    )
                }

                TextBoxLayer(
                    boxes = textBoxes,
                    editable = tool == InkTool.TEXT,
                    activeTextBoxId = activeTextBoxId,
                    onActivate = onActivateTextBox,
                    onUpdate = onUpdateTextBox,
                    onDelete = onDeleteTextBox,
                )

                PageLinkLayer(
                    links = links,
                    onNavigate = onNavigateLink,
                    onUpdate = onUpdateLink,
                )
            }
        }

        if (scale > 1.01f) {
            Surface(
                onClick = {
                    scale = 1f
                    pan = Offset.Zero
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 2.dp
            ) {
                Text(
                    stringResource(R.string.zoom_fit_label, (scale * 100).roundToInt()),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
