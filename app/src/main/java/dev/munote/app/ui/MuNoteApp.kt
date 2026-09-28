package dev.munote.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    val handwritingRecognizer = remember { ChineseHandwritingRecognizer() }

    var session by remember { mutableStateOf<PdfSession?>(null) }
    var currentEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var indexStore by remember { mutableStateOf<OcrIndexStore?>(null) }
    var handwritingIndexStore by remember { mutableStateOf<HandwritingIndexStore?>(null) }
    var inkStore by remember { mutableStateOf<InkStore?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var ocrDone by remember { mutableIntStateOf(0) }
    var ocrRunning by remember { mutableStateOf(false) }
    var ocrRevision by remember { mutableIntStateOf(0) }
    var handwritingModelState by remember { mutableStateOf(HandwritingModelState.NOT_READY) }
    var libraryRevision by remember { mutableIntStateOf(0) }

    fun attachSession(next: PdfSession, entry: LibraryEntry) {
        session?.close()
        session = next
        currentEntry = entry
        indexStore = OcrIndexStore(context, next.fingerprint)
        handwritingIndexStore = HandwritingIndexStore(context, next.fingerprint)
        inkStore = InkStore(context, next.fingerprint)
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
                loadError = it.message ?: "PDF 打开失败"
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
                loadError = it.message ?: "本地 PDF 打开失败"
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
            entries = remember(libraryRevision) { library.entries() },
            error = loadError,
            onImport = { picker.launch(arrayOf("application/pdf")) },
            onOpen = ::openStored,
            onRename = { entry, title ->
                scope.launch {
                    runCatching { library.rename(entry, title) }
                        .onSuccess { libraryRevision++ }
                        .onFailure { loadError = it.message ?: "重命名失败" }
                }
            },
            onDelete = { entry ->
                scope.launch {
                    runCatching { library.delete(entry) }
                        .onSuccess { libraryRevision++ }
                        .onFailure { loadError = it.message ?: "删除失败" }
                }
            },
        )
    } else {
        ReaderScreen(
            documentTitle = currentEntry?.title ?: "PDF 笔记",
            initialPage = currentEntry?.lastPage ?: 0,
            session = session!!,
            indexStore = indexStore!!,
            handwritingIndexStore = handwritingIndexStore!!,
            handwritingRecognizer = handwritingRecognizer,
            handwritingModelState = handwritingModelState,
            inkStore = inkStore!!,
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
private fun LibraryHome(
    entries: List<LibraryEntry>,
    error: String?,
    onImport: () -> Unit,
    onOpen: (LibraryEntry) -> Unit,
    onRename: (LibraryEntry, String) -> Unit,
    onDelete: (LibraryEntry) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<LibraryEntry?>(null) }

    renameTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("文档名称") }
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
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这个文档？") },
            text = {
                Text("会删除 MuNote 本地保存的 PDF、手写笔迹和识别索引。原来文件管理器里的源 PDF 不受影响。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(entry)
                        deleteTarget = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
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
                        "手写优先 · PDF OCR · 手写可搜索",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
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
                        Text("导入 PDF", style = MaterialTheme.typography.labelLarge)
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
                            Icon(Icons.Default.FolderOpen, contentDescription = "导入 PDF")
                        }
                        Text("把教材或扫描 PDF 放进来")
                        Text(
                            "首次导入后会保存在本机资料库，之后直接打开，并记住上次阅读位置。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            Text(
                "最近文档",
                modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    count = entries.size,
                    key = { entries[it].fingerprint }
                ) { index ->
                    val entry = entries[index]
                    LibraryDocumentRow(
                        entry = entry,
                        onClick = { onOpen(entry) },
                        onRename = {
                            renameText = entry.title
                            renameTarget = entry
                        },
                        onDelete = { deleteTarget = entry },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryDocumentRow(
    entry: LibraryEntry,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("PDF", style = MaterialTheme.typography.labelLarge)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    if (entry.lastPage > 0) {
                        "上次看到第 ${entry.lastPage + 1} 页 · 本地保存"
                    } else {
                        "本地保存 · 打开后自动继续 OCR"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "文档菜单")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
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
    var showThumbnails by remember { mutableStateOf(false) }
    var showBookmarksOnly by remember { mutableStateOf(false) }
    var showPageJump by remember { mutableStateOf(false) }
    var pageJumpText by remember { mutableStateOf("") }
    var showPenOptions by remember { mutableStateOf(false) }
    var exportRunning by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                exportRunning = true
                try {
                    PdfExporter.exportFlattened(
                        context = context,
                        session = session,
                        inkStore = inkStore,
                        destination = uri,
                    )
                    Toast.makeText(context, "已导出带批注 PDF", Toast.LENGTH_SHORT).show()
                } catch (error: Throwable) {
                    Toast.makeText(
                        context,
                        "导出失败：${error.message ?: "未知错误"}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    exportRunning = false
                }
            }
        }
    }

    var penColor by remember { mutableIntStateOf(0xFF1C1D1F.toInt()) }
    var highlighterColor by remember { mutableIntStateOf(0xFFFFD54F.toInt()) }
    var penWidth by remember { mutableStateOf(2.15f) }
    var highlighterWidth by remember { mutableStateOf(12f) }

    suspend fun goToHit(index: Int) {
        if (hits.isEmpty()) return
        selectedHit = ((index % hits.size) + hits.size) % hits.size
        pager.animateScrollToPage(hits[selectedHit].pageIndex)
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

    LaunchedEffect(query, ocrRevision, handwritingRevision) {
        hits = (indexStore.search(query) + handwritingIndexStore.search(query))
            .sortedWith(compareBy<SearchHit> { it.pageIndex }.thenBy { it.source.ordinal })
        selectedHit = selectedHit.coerceIn(0, (hits.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(pager.currentPage) {
        onPageChanged(pager.currentPage)
    }

    if (showPageJump) {
        AlertDialog(
            onDismissRequest = { showPageJump = false },
            title = { Text("跳转到页面") },
            text = {
                OutlinedTextField(
                    value = pageJumpText,
                    onValueChange = { value ->
                        pageJumpText = value.filter { it.isDigit() }.take(6)
                    },
                    singleLine = true,
                    label = { Text("页码 1-${session.pageCount}") }
                )
            },
            confirmButton = {
                val target = pageJumpText.toIntOrNull()
                TextButton(
                    enabled = target != null && target in 1..session.pageCount,
                    onClick = {
                        val page = (pageJumpText.toIntOrNull() ?: 1) - 1
                        scope.launch { pager.animateScrollToPage(page) }
                        showPageJump = false
                    }
                ) {
                    Text("跳转")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPageJump = false }) { Text("取消") }
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回资料库")
                    }
                    Text(
                        documentTitle,
                        modifier = Modifier.width(132.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge
                    )
                    IconButton(onClick = onOpenPdf) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "导入另一个 PDF")
                    }
                    IconButton(
                        enabled = !exportRunning,
                        onClick = {
                            exportLauncher.launch(
                                documentTitle.take(80).ifBlank { "MuNote" } + "-批注.pdf"
                            )
                        }
                    ) {
                        if (exportRunning) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.FileDownload, contentDescription = "导出带批注 PDF")
                        }
                    }
                    IconButton(onClick = { showThumbnails = !showThumbnails }) {
                        Icon(Icons.Default.List, contentDescription = "页面缩略图")
                    }
                    if (showThumbnails) {
                        IconButton(
                            enabled = bookmarks.isNotEmpty(),
                            onClick = { showBookmarksOnly = !showBookmarksOnly }
                        ) {
                            Icon(
                                if (showBookmarksOnly) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (showBookmarksOnly) {
                                    "显示全部页面"
                                } else {
                                    "只看书签页"
                                }
                            )
                        }
                    }
                    IconButton(onClick = { onToggleBookmark(pager.currentPage) }) {
                        Icon(
                            if (pager.currentPage in bookmarks) Icons.Default.Star
                            else Icons.Default.StarBorder,
                            contentDescription = if (pager.currentPage in bookmarks) {
                                "取消书签"
                            } else {
                                "添加书签"
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
                        placeholder = { Text("搜索 PDF 和手写内容") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(18.dp)
                    )
                    if (hits.isNotEmpty()) {
                        IconButton(onClick = { scope.launch { goToHit(selectedHit - 1) } }) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一个搜索结果")
                        }
                        Text(
                            "${selectedHit + 1}/${hits.size}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        IconButton(onClick = { scope.launch { goToHit(selectedHit + 1) } }) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一个搜索结果")
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
                            "手写模型下载中",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                        HandwritingModelState.ERROR -> Text(
                            "手写识别不可用",
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
                                ocrRunning -> "正在继续识别 PDF，当前暂无匹配"
                                handwritingModelState == HandwritingModelState.DOWNLOADING ->
                                    "手写识别模型下载中，PDF 搜索仍可用"
                                else -> "没有找到"
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
                                        val sourceLabel =
                                            if (hit.source == SearchSource.HANDWRITING) "手写" else "PDF"
                                        Text(
                                            "第 ${hit.pageIndex + 1} 页 · $sourceLabel · ${hit.snippet}",
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
                        scope.launch { pager.animateScrollToPage(page) }
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
                    highlight = hits.getOrNull(selectedHit)?.takeIf { it.pageIndex == page },
                    inkColor = if (tool == InkTool.HIGHLIGHTER) highlighterColor else penColor,
                    penWidthDp = penWidth,
                    highlighterWidthDp = highlighterWidth,
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
                    selected = tool == InkTool.PEN,
                    label = "钢笔",
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
                    label = "荧光",
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
                    label = "橡皮",
                    icon = { Icon(Icons.Default.Clear, contentDescription = null) },
                    onClick = {
                        tool = InkTool.ERASER
                        showPenOptions = false
                    }
                )
                Spacer(Modifier.width(8.dp))
                ToolButton(
                    selected = tool == InkTool.LASSO,
                    label = "套索",
                    icon = { Icon(Icons.Default.Gesture, contentDescription = null) },
                    onClick = {
                        tool = InkTool.LASSO
                        showPenOptions = false
                    }
                )
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
                    Icon(Icons.Default.Undo, contentDescription = "撤销")
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
                    Icon(Icons.Default.Redo, contentDescription = "重做")
                }
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = {
                        scope.launch {
                            pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0))
                        }
                    }
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "上一页")
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
                            pager.animateScrollToPage(
                                (pager.currentPage + 1).coerceAtMost(session.pageCount - 1)
                            )
                        }
                    }
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "下一页")
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
                    contentDescription = "第 ${pageIndex + 1} 页缩略图",
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
                if (isHighlighter) "荧光笔" else "钢笔",
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
private fun PdfInkPage(
    session: PdfSession,
    pageIndex: Int,
    tool: InkTool,
    strokes: List<dev.munote.app.ink.InkStroke>,
    highlight: SearchHit?,
    inkColor: Int,
    penWidthDp: Float,
    highlighterWidthDp: Float,
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

        val gestureModifier = Modifier.pointerInput(pageIndex) {
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
                    var dragX = 0f
                    var dragY = 0f
                    var pressed = true

                    while (pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val down = event.changes.filter { it.pressed }
                        pressed = down.isNotEmpty()
                        if (!pressed) break

                        if (down.size >= 2) {
                            pinched = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            applyTransform(zoomChange, panChange)
                            event.changes.forEach { it.consume() }
                        } else if (down.size == 1) {
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

                    if (!pinched && scale <= 1.01f) {
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
                    contentDescription = "PDF 第 ${pageIndex + 1} 页",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                val rect = highlight?.rect
                if (rect != null) {
                    val highlightFill = if (highlight.source == SearchSource.HANDWRITING) {
                        Color(0x5538BDF8)
                    } else {
                        Color(0x66FFD54F)
                    }
                    val highlightStroke = if (highlight.source == SearchSource.HANDWRITING) {
                        Color(0xCC0284C7)
                    } else {
                        Color(0xCCF59E0B)
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
                            setStrokes(strokes)
                            onStrokeCommitted = onStroke
                            onPageMutated = onMutated
                        }
                    },
                    update = { view ->
                        view.tool = tool
                        view.inkColor = inkColor
                        view.penWidthDp = penWidthDp
                        view.highlighterWidthDp = highlighterWidthDp
                        view.onStrokeCommitted = onStroke
                        view.onPageMutated = onMutated
                        if (view.snapshot() != strokes) view.setStrokes(strokes)
                    },
                    modifier = Modifier.fillMaxSize()
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
                    "${(scale * 100).roundToInt()}% · 适合页面",
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
