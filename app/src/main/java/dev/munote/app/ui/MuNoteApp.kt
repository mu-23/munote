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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import dev.munote.app.ink.InkCanvasView
import dev.munote.app.ink.InkStore
import dev.munote.app.ink.InkTool
import dev.munote.app.handwriting.ChineseHandwritingRecognizer
import dev.munote.app.handwriting.HandwritingIndexStore
import dev.munote.app.handwriting.HandwritingModelState
import dev.munote.app.ocr.ChineseOcrEngine
import dev.munote.app.ocr.OcrIndexStore
import dev.munote.app.ocr.SearchHit
import dev.munote.app.ocr.SearchSource
import dev.munote.app.pdf.LibraryEntry
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
    val handwritingRecognizer = remember { ChineseHandwritingRecognizer(context) }

    var session by remember { mutableStateOf<PdfSession?>(null) }
    var currentEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var indexStore by remember { mutableStateOf<OcrIndexStore?>(null) }
    var handwritingIndexStore by remember { mutableStateOf<HandwritingIndexStore?>(null) }
    var inkStore by remember { mutableStateOf<InkStore?>(null) }
    var textStore by remember { mutableStateOf<TextStore?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var ocrDone by remember { mutableIntStateOf(0) }
    var ocrRunning by remember { mutableStateOf(false) }
    var ocrRevision by remember { mutableIntStateOf(0) }
    var handwritingModelState by remember { mutableStateOf(HandwritingModelState.NOT_READY) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    var coverTarget by remember { mutableStateOf<LibraryEntry?>(null) }

    fun attachSession(next: PdfSession, entry: LibraryEntry) {
        session?.close()
        session = next
        currentEntry = entry
        indexStore = OcrIndexStore(context, next.fingerprint)
        handwritingIndexStore = HandwritingIndexStore(context, next.fingerprint)
        inkStore = InkStore(context, next.fingerprint)
        textStore = TextStore(context, next.fingerprint)
        ocrDone = indexStore?.completedPages() ?: 0
        ocrRevision++
        libraryRevision++
    }

    fun openPdf(uri: Uri) {
        scope.launch {
            loadError = null
            runCatching {
                val title = library.displayName(uri)
                val next = PdfSession.open(context, uri)
                val entry = library.registerImported(next.fingerprint, title)
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
                val next = PdfSession.openStored(context, entry.fingerprint)
                val touched = library.touch(entry)
                attachSession(next, touched)
            }.onFailure {
                loadError = it.message ?: context.getString(R.string.error_local_pdf_open)
                libraryRevision++
            }
        }
    }

    fun closeDocument() {
        session?.close()
        session = null
        currentEntry = null
        indexStore = null
        handwritingIndexStore = null
        inkStore = null
        textStore = null
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
            handwritingRecognizer.close()
        }
    }

    LaunchedEffect(Unit) {
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
        closeDocument()
    }

    LaunchedEffect(session?.fingerprint) {
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
            error = loadError,
            onImport = { picker.launch(arrayOf("application/pdf")) },
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
        )
    } else {
        ReaderScreen(
            documentTitle = currentEntry?.title ?: context.getString(R.string.default_pdf_note),
            initialPage = currentEntry?.lastPage ?: 0,
            session = session!!,
            indexStore = indexStore!!,
            handwritingIndexStore = handwritingIndexStore!!,
            handwritingRecognizer = handwritingRecognizer,
            handwritingModelState = handwritingModelState,
            inkStore = inkStore!!,
            textStore = textStore!!,
            ocrDone = ocrDone,
            ocrRunning = ocrRunning,
            ocrRevision = ocrRevision,
            bookmarks = currentEntry?.bookmarks ?: emptySet(),
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
            onOpenPdf = { picker.launch(arrayOf("application/pdf")) }
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryHome(
    library: PdfLibrary,
    entries: List<LibraryEntry>,
    error: String?,
    onImport: () -> Unit,
    onOpen: (LibraryEntry) -> Unit,
    onRename: (LibraryEntry, String) -> Unit,
    onDelete: (LibraryEntry) -> Unit,
    onSetCover: (LibraryEntry) -> Unit,
    onResetCover: (LibraryEntry) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<LibraryEntry?>(null) }

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
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(entry)
                        deleteTarget = null
                    }
                ) {
                    Text(
                        stringResource(R.string.action_delete),
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
                LanguageMenu()
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

        if (entries.isEmpty()) {
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
                        FilledTonalIconButton(onClick = onImport, modifier = Modifier.size(58.dp)) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = stringResource(R.string.action_import_pdf)
                            )
                        }
                        Text(stringResource(R.string.home_empty_title))
                        Text(
                            stringResource(R.string.home_empty_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            Text(
                stringResource(R.string.recent_documents),
                modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp),
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
                    count = entries.size,
                    key = { entries[it].fingerprint }
                ) { index ->
                    val entry = entries[index]
                    LibraryDocumentCard(
                        library = library,
                        entry = entry,
                        onClick = { onOpen(entry) },
                        onRename = {
                            renameText = entry.title
                            renameTarget = entry
                        },
                        onSetCover = { onSetCover(entry) },
                        onResetCover = { onResetCover(entry) },
                        onDelete = { deleteTarget = entry },
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
    onClick: () -> Unit,
    onRename: () -> Unit,
    onSetCover: () -> Unit,
    onResetCover: () -> Unit,
    onDelete: () -> Unit,
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
                        Text("PDF", style = MaterialTheme.typography.titleLarge)
                    }
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
                            text = { Text(stringResource(R.string.action_delete)) },
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
            if (entry.lastPage > 0) {
                stringResource(R.string.last_viewed_page, entry.lastPage + 1)
            } else {
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
    ocrDone: Int,
    ocrRunning: Boolean,
    ocrRevision: Int,
    bookmarks: Set<Int>,
    onToggleBookmark: (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onClose: () -> Unit,
    onOpenPdf: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var activeTextBoxId by remember { mutableStateOf<String?>(null) }
    var activeInkView by remember { mutableStateOf<InkCanvasView?>(null) }
    var lassoSelectionActive by remember { mutableStateOf(false) }
    var showThumbnails by remember { mutableStateOf(false) }
    var showBookmarksOnly by remember { mutableStateOf(false) }
    var showPageOverview by remember { mutableStateOf(false) }
    var overviewOriginPage by remember { mutableIntStateOf(0) }
    var previousLocation by remember { mutableStateOf<Int?>(null) }
    val inputPrefs = remember { context.getSharedPreferences("editor_preferences", android.content.Context.MODE_PRIVATE) }
    var fingerWriting by remember { mutableStateOf(inputPrefs.getBoolean("finger_writing", false)) }
    var showPageJump by remember { mutableStateOf(false) }
    var pageJumpText by remember { mutableStateOf("") }
    var showPenOptions by remember { mutableStateOf(false) }
    var exportRunning by remember { mutableStateOf(false) }
    var exportCompleted by remember { mutableIntStateOf(0) }
    var exportTotal by remember { mutableIntStateOf(0) }
    var exportJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

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

    var penColor by remember { mutableIntStateOf(0xFF1C1D1F.toInt()) }
    var highlighterColor by remember { mutableIntStateOf(0xFFFFD54F.toInt()) }
    var penWidth by remember { mutableStateOf(2.15f) }
    var highlighterWidth by remember { mutableStateOf(12f) }

    suspend fun jumpToPage(page: Int, rememberLocation: Boolean = true) {
        val target = page.coerceIn(0, session.pageCount - 1)
        if (target == pager.currentPage) return
        if (rememberLocation) previousLocation = pager.currentPage
        pager.animateScrollToPage(target)
    }

    suspend fun goToHit(index: Int) {
        if (hits.isEmpty()) return
        selectedHit = ((index % hits.size) + hits.size) % hits.size
        jumpToPage(hits[selectedHit].pageIndex)
    }

    fun openPageOverview() {
        overviewOriginPage = pager.currentPage
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

    if (showPageOverview) {
        PageOverviewDialog(
            session = session,
            currentPage = pager.currentPage,
            bookmarks = bookmarks,
            onDismiss = { showPageOverview = false },
            onSelectPage = { page ->
                previousLocation = overviewOriginPage
                showPageOverview = false
                scope.launch { jumpToPage(page, rememberLocation = false) }
            }
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
                    activeTextBoxId = activeTextBoxId,
                    highlight = hits.getOrNull(selectedHit)?.takeIf { it.pageIndex == page },
                    inkColor = if (tool == InkTool.HIGHLIGHTER) highlighterColor else penColor,
                    penWidthDp = penWidth,
                    highlighterWidthDp = highlighterWidth,
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

        if (showPenOptions && (tool == InkTool.PEN || tool == InkTool.HIGHLIGHTER)) {
            PenOptionsBar(
                tool = tool,
                penColor = penColor,
                highlighterColor = highlighterColor,
                penWidth = penWidth,
                highlighterWidth = highlighterWidth,
                onPenColor = { penColor = it },
                onHighlighterColor = { highlighterColor = it },
                onPenWidth = { penWidth = it },
                onHighlighterWidth = { highlighterWidth = it },
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
                if (previousLocation != null) {
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            val target = previousLocation ?: return@IconButton
                            val current = pager.currentPage
                            previousLocation = current
                            scope.launch { jumpToPage(target, rememberLocation = false) }
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
            }
        }
    }
}

@Composable
private fun PageOverviewDialog(
    session: PdfSession,
    currentPage: Int,
    bookmarks: Set<Int>,
    onDismiss: () -> Unit,
    onSelectPage: (Int) -> Unit,
) {
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
                            stringResource(R.string.page_overview_hint),
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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        count = session.pageCount,
                        key = { it }
                    ) { page ->
                        ThumbnailCard(
                            session = session,
                            pageIndex = page,
                            selected = page == currentPage,
                            bookmarked = page in bookmarks,
                            onClick = { onSelectPage(page) }
                        )
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
    onPenColor: (Int) -> Unit,
    onHighlighterColor: (Int) -> Unit,
    onPenWidth: (Float) -> Unit,
    onHighlighterWidth: (Float) -> Unit,
) {
    val isHighlighter = tool == InkTool.HIGHLIGHTER
    val currentWidth = if (isHighlighter) highlighterWidth else penWidth
    val widths = if (isHighlighter) listOf(8f, 12f, 16f) else listOf(1.4f, 2.15f, 3.0f)
    val colors = if (isHighlighter) {
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
        )
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
            widths.forEach { width ->
                AssistChip(
                    onClick = {
                        if (isHighlighter) onHighlighterWidth(width) else onPenWidth(width)
                    },
                    label = {
                        Text(
                            if (isHighlighter) "${width.toInt()}" else String.format("%.1f", width)
                        )
                    },
                    leadingIcon = if (currentWidth == width) {
                        { Text("●") }
                    } else null
                )
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
private fun PdfInkPage(
    session: PdfSession,
    pageIndex: Int,
    tool: InkTool,
    strokes: List<dev.munote.app.ink.InkStroke>,
    textBoxes: List<TextBoxNote>,
    activeTextBoxId: String?,
    highlight: SearchHit?,
    inkColor: Int,
    penWidthDp: Float,
    highlighterWidthDp: Float,
    fingerWritingEnabled: Boolean,
    onShowPageOverview: () -> Unit,
    onAddTextBox: (Float, Float) -> Unit,
    onUpdateTextBox: (TextBoxNote) -> Unit,
    onDeleteTextBox: (String) -> Unit,
    onActivateTextBox: (String) -> Unit,
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

                    if (
                        !fingerWritingEnabled &&
                        tool != InkTool.TEXT &&
                        !pinched &&
                        !threeFingerGesture &&
                        scale <= 1.01f
                    ) {
                        val thresholdPx = with(density) { 72.dp.toPx() }
                        if (abs(dragX) >= thresholdPx && abs(dragX) > abs(dragY) * 1.15f) {
                            onPageSwipe(if (dragX < 0f) 1 else -1)
                        }
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

                AndroidView(
                    factory = { ctx ->
                        InkCanvasView(ctx).apply {
                            this.tool = tool
                            this.inkColor = inkColor
                            this.penWidthDp = penWidthDp
                            this.highlighterWidthDp = highlighterWidthDp
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
                        view.fingerWritingEnabled = fingerWritingEnabled
                        view.onStrokeCommitted = onStroke
                        view.onPageMutated = onMutated
                        view.onSelectionChanged = onSelectionChanged
                        onViewReady(view)
                        if (view.snapshot() != strokes) view.setStrokes(strokes)
                    },
                    modifier = Modifier.fillMaxSize()
                )

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
