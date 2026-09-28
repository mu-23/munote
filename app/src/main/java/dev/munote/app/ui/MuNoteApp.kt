package dev.munote.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.munote.app.ink.InkCanvasView
import dev.munote.app.ink.InkStore
import dev.munote.app.ink.InkTool
import dev.munote.app.ocr.ChineseOcrEngine
import dev.munote.app.ocr.OcrIndexStore
import dev.munote.app.ocr.SearchHit
import dev.munote.app.pdf.PdfSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MuNoteApp(initialPdf: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf<PdfSession?>(null) }
    var indexStore by remember { mutableStateOf<OcrIndexStore?>(null) }
    var inkStore by remember { mutableStateOf<InkStore?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var ocrDone by remember { mutableIntStateOf(0) }
    var ocrRunning by remember { mutableStateOf(false) }
    var ocrRevision by remember { mutableIntStateOf(0) }

    fun openPdf(uri: Uri) {
        scope.launch {
            loadError = null
            runCatching {
                val next = PdfSession.open(context, uri)
                session?.close()
                session = next
                indexStore = OcrIndexStore(context, next.fingerprint)
                inkStore = InkStore(context, next.fingerprint)
                ocrDone = indexStore?.completedPages() ?: 0
            }.onFailure {
                loadError = it.message ?: "PDF 打开失败"
            }
        }
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
        onDispose { session?.close() }
    }

    LaunchedEffect(session?.fingerprint) {
        val pdf = session ?: return@LaunchedEffect
        val store = indexStore ?: return@LaunchedEffect
        val engine = ChineseOcrEngine()
        ocrRunning = true
        try {
            for (page in 0 until pdf.pageCount) {
                if (store.hasPage(page)) {
                    ocrDone = store.completedPages()
                    continue
                }
                val bitmap = pdf.renderPage(page, 1500)
                val text = engine.recognize(bitmap)
                store.put(page, text)
                ocrDone = store.completedPages()
                ocrRevision++
            }
        } finally {
            engine.close()
            ocrRunning = false
        }
    }

    if (session == null) {
        EmptyHome(
            error = loadError,
            onOpenPdf = { picker.launch(arrayOf("application/pdf")) }
        )
    } else {
        ReaderScreen(
            session = session!!,
            indexStore = indexStore!!,
            inkStore = inkStore!!,
            ocrDone = ocrDone,
            ocrRunning = ocrRunning,
            ocrRevision = ocrRevision,
            onOpenPdf = { picker.launch(arrayOf("application/pdf")) }
        )
    }
}

@Composable
private fun EmptyHome(
    error: String?,
    onOpenPdf: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.padding(28.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 36.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("MuNote", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "手写优先 · 扫描 PDF 可搜索",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalIconButton(onClick = onOpenPdf, modifier = Modifier.size(58.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "打开 PDF")
                }
                TextButton(onClick = onOpenPdf) { Text("打开 PDF") }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    session: PdfSession,
    indexStore: OcrIndexStore,
    inkStore: InkStore,
    ocrDone: Int,
    ocrRunning: Boolean,
    ocrRevision: Int,
    onOpenPdf: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { session.pageCount })
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var tool by remember { mutableStateOf(InkTool.PEN) }
    var inkRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(query, ocrRevision) {
        hits = indexStore.search(query)
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onOpenPdf) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "打开 PDF")
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("搜索 PDF 图片里的文字") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(18.dp)
                    )
                    if (ocrRunning) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                "${ocrDone}/${session.pageCount}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                if (query.isNotBlank()) {
                    if (hits.isEmpty()) {
                        Text(
                            if (ocrRunning) "正在继续识别，当前暂无匹配" else "没有找到",
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
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
                            hits.forEach { hit ->
                                AssistChip(
                                    onClick = { scope.launch { pager.animateScrollToPage(hit.pageIndex) } },
                                    label = {
                                        Text(
                                            "第 ${hit.pageIndex + 1} 页 · ${hit.snippet}",
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

        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            beyondViewportPageCount = 1
        ) { page ->
            PdfInkPage(
                session = session,
                pageIndex = page,
                tool = tool,
                strokes = remember(inkRevision, page) { inkStore.page(page) },
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

        Surface(
            tonalElevation = 3.dp,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                ToolButton(
                    selected = tool == InkTool.PEN,
                    label = "钢笔",
                    icon = { Icon(Icons.Default.Brush, contentDescription = null) },
                    onClick = { tool = InkTool.PEN }
                )
                Spacer(Modifier.width(8.dp))
                ToolButton(
                    selected = tool == InkTool.HIGHLIGHTER,
                    label = "荧光",
                    icon = { Text("▰") },
                    onClick = { tool = InkTool.HIGHLIGHTER }
                )
                Spacer(Modifier.width(8.dp))
                ToolButton(
                    selected = tool == InkTool.ERASER,
                    label = "橡皮",
                    icon = { Icon(Icons.Default.Clear, contentDescription = null) },
                    onClick = { tool = InkTool.ERASER }
                )
                Spacer(Modifier.width(18.dp))
                IconButton(
                    onClick = {
                        scope.launch {
                            inkStore.undo(pager.currentPage)
                            inkRevision++
                        }
                    }
                ) {
                    Icon(Icons.Default.Undo, contentDescription = "撤销")
                }
                Spacer(Modifier.width(18.dp))
                IconButton(
                    onClick = {
                        scope.launch {
                            pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0))
                        }
                    }
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "上一页")
                }
                Text(
                    "${pager.currentPage + 1} / ${session.pageCount}",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
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
    onStroke: (dev.munote.app.ink.InkStroke) -> Unit,
    onMutated: (List<dev.munote.app.ink.InkStroke>) -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, session.fingerprint, pageIndex) {
        value = withContext(Dispatchers.IO) {
            session.renderPage(pageIndex, 1800)
        }
    }

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

        val pageRatio = bmp.width.toFloat() / bmp.height.toFloat()
        val containerRatio = maxWidth.value / maxHeight.value
        val pageModifier = if (containerRatio <= pageRatio) {
            Modifier.fillMaxWidth().aspectRatio(pageRatio)
        } else {
            Modifier.fillMaxHeight().aspectRatio(pageRatio)
        }

        Surface(
            modifier = pageModifier,
            shadowElevation = 3.dp,
            shape = RoundedCornerShape(4.dp),
            color = androidx.compose.ui.graphics.Color.White
        ) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF 第 ${pageIndex + 1} 页",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                AndroidView(
                    factory = { ctx ->
                        InkCanvasView(ctx).apply {
                            this.tool = tool
                            setStrokes(strokes)
                            onStrokeCommitted = onStroke
                            onPageMutated = onMutated
                        }
                    },
                    update = { view ->
                        view.tool = tool
                        view.onStrokeCommitted = onStroke
                        view.onPageMutated = onMutated
                        if (view.snapshot() != strokes) view.setStrokes(strokes)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
