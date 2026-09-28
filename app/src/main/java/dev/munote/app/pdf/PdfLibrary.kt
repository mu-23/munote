package dev.munote.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import dev.munote.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

@Serializable
enum class DocumentKind { PDF, NOTE }

@Serializable
enum class PageTemplate { BLANK, RULED, GRID, DOT }

@Serializable
data class LibraryEntry(
    val fingerprint: String,
    val title: String,
    val importedAt: Long,
    val lastOpenedAt: Long,
    val lastPage: Int = 0,
    val bookmarks: Set<Int> = emptySet(),
    val hasCustomCover: Boolean = false,
    val kind: DocumentKind = DocumentKind.PDF,
    val pageTemplate: PageTemplate = PageTemplate.BLANK,
    val notePageCount: Int = 0,
    val pageTemplates: List<PageTemplate> = emptyList(),
    val favorite: Boolean = false,
    val folderId: String? = null,
    val trashedAt: Long? = null,
)

@Serializable
data class LibraryFolder(
    val id: String,
    val name: String,
    val createdAt: Long,
)

@Serializable
private data class LibraryIndex(
    val entries: List<LibraryEntry> = emptyList(),
    val folders: List<LibraryFolder> = emptyList(),
)

/**
 * Local-first document library.
 *
 * Imported PDFs live under files/documents/<id>.pdf. Native notebooks use a stable UUID-backed ID
 * so their background PDF can be regenerated when pages are appended without changing the ink,
 * text, OCR, or cover keys associated with the document.
 */
class PdfLibrary(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val documentsDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val indexFile = File(documentsDir, "library.json")
    private val entries = linkedMapOf<String, LibraryEntry>()
    private val folders = linkedMapOf<String, LibraryFolder>()

    init {
        reload()
    }

    @Synchronized
    fun reload() {
        entries.clear()
        folders.clear()
        if (!indexFile.exists()) return
        runCatching {
            val index = json.decodeFromString<LibraryIndex>(indexFile.readText())
            index.entries.forEach { entry -> entries[entry.fingerprint] = entry }
            index.folders.forEach { folder -> folders[folder.id] = folder }
        }
    }

    @Synchronized
    fun entries(): List<LibraryEntry> =
        entries.values
            .filter { it.trashedAt == null && documentFile(it.fingerprint).exists() }
            .sortedWith(compareByDescending<LibraryEntry> { it.favorite }.thenByDescending { it.lastOpenedAt })

    @Synchronized
    fun trashEntries(): List<LibraryEntry> =
        entries.values
            .filter { it.trashedAt != null && documentFile(it.fingerprint).exists() }
            .sortedByDescending { it.trashedAt }

    @Synchronized
    fun folders(): List<LibraryFolder> =
        folders.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        val fromProvider = runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
        }.getOrNull()

        fromProvider
            ?.takeIf { it.isNotBlank() }
            ?.removeSuffix(".pdf")
            ?: context.getString(R.string.default_pdf_note)
    }

    suspend fun registerImported(fingerprint: String, title: String): LibraryEntry =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val previous = synchronized(this@PdfLibrary) { entries[fingerprint] }
            val entry = LibraryEntry(
                fingerprint = fingerprint,
                title = title.ifBlank {
                    previous?.title ?: context.getString(R.string.default_pdf_note)
                },
                importedAt = previous?.importedAt ?: now,
                lastOpenedAt = now,
                lastPage = previous?.lastPage ?: 0,
                bookmarks = previous?.bookmarks ?: emptySet(),
                hasCustomCover = previous?.hasCustomCover ?: coverFile(fingerprint).exists(),
                kind = previous?.kind ?: DocumentKind.PDF,
                pageTemplate = previous?.pageTemplate ?: PageTemplate.BLANK,
                notePageCount = previous?.notePageCount ?: 0,
                pageTemplates = previous?.pageTemplates ?: emptyList(),
                favorite = previous?.favorite ?: false,
                folderId = previous?.folderId,
                trashedAt = previous?.trashedAt,
            )
            synchronized(this@PdfLibrary) {
                entries[fingerprint] = entry
                persistLocked()
            }
            entry
        }

    suspend fun createNotebook(
        title: String,
        template: PageTemplate,
    ): LibraryEntry = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val fingerprint = "note-" + UUID.randomUUID().toString().replace("-", "")
        val normalizedTitle = title.trim().ifBlank {
            context.getString(R.string.default_notebook_title)
        }
        writeNotebookPdf(
            file = documentFile(fingerprint),
            templates = listOf(template),
        )
        val entry = LibraryEntry(
            fingerprint = fingerprint,
            title = normalizedTitle,
            importedAt = now,
            lastOpenedAt = now,
            kind = DocumentKind.NOTE,
            pageTemplate = template,
            notePageCount = 1,
            pageTemplates = listOf(template),
        )
        synchronized(this@PdfLibrary) {
            entries[fingerprint] = entry
            persistLocked()
        }
        entry
    }

    suspend fun appendNotebookPage(entry: LibraryEntry): LibraryEntry =
        withContext(Dispatchers.IO) {
            require(entry.kind == DocumentKind.NOTE) {
                context.getString(R.string.error_not_notebook)
            }
            val current = synchronized(this@PdfLibrary) {
                entries[entry.fingerprint] ?: entry
            }
            val templates = effectivePageTemplates(current).toMutableList().apply {
                add(current.pageTemplate)
            }
            writeNotebookPdf(
                file = documentFile(current.fingerprint),
                templates = templates,
            )
            update(
                current.copy(
                    notePageCount = templates.size,
                    pageTemplates = templates,
                    lastOpenedAt = System.currentTimeMillis(),
                )
            )
        }


    suspend fun createFolder(name: String): LibraryFolder = withContext(Dispatchers.IO) {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Folder name cannot be empty" }
        val now = System.currentTimeMillis()
        val folder = LibraryFolder(
            id = "folder-" + UUID.randomUUID().toString().replace("-", ""),
            name = normalized,
            createdAt = now,
        )
        synchronized(this@PdfLibrary) {
            folders[folder.id] = folder
            persistLocked()
        }
        folder
    }

    suspend fun renameFolder(folder: LibraryFolder, name: String): LibraryFolder =
        withContext(Dispatchers.IO) {
            val normalized = name.trim()
            require(normalized.isNotEmpty()) { "Folder name cannot be empty" }
            val updated = folder.copy(name = normalized)
            synchronized(this@PdfLibrary) {
                folders[folder.id] = updated
                persistLocked()
            }
            updated
        }

    suspend fun deleteFolder(folder: LibraryFolder) = withContext(Dispatchers.IO) {
        synchronized(this@PdfLibrary) {
            folders.remove(folder.id)
            entries.replaceAll { _, entry ->
                if (entry.folderId == folder.id) entry.copy(folderId = null) else entry
            }
            persistLocked()
        }
    }

    suspend fun moveToFolder(entry: LibraryEntry, folderId: String?): LibraryEntry =
        withContext(Dispatchers.IO) {
            val normalized = folderId?.takeIf { id ->
                synchronized(this@PdfLibrary) { folders.containsKey(id) }
            }
            val current = synchronized(this@PdfLibrary) {
                entries[entry.fingerprint] ?: entry
            }
            update(current.copy(folderId = normalized))
        }

    suspend fun toggleFavorite(entry: LibraryEntry): LibraryEntry =
        withContext(Dispatchers.IO) {
            val current = synchronized(this@PdfLibrary) {
                entries[entry.fingerprint] ?: entry
            }
            update(current.copy(favorite = !current.favorite))
        }

    suspend fun restoreFromTrash(entry: LibraryEntry): LibraryEntry =
        withContext(Dispatchers.IO) {
            val current = synchronized(this@PdfLibrary) {
                entries[entry.fingerprint] ?: entry
            }
            update(current.copy(trashedAt = null, lastOpenedAt = System.currentTimeMillis()))
        }

    suspend fun deleteNotebookPage(entry: LibraryEntry, pageIndex: Int): LibraryEntry =
        withContext(Dispatchers.IO) {
            require(entry.kind == DocumentKind.NOTE) {
                context.getString(R.string.error_not_notebook)
            }
            val current = synchronized(this@PdfLibrary) {
                entries[entry.fingerprint] ?: entry
            }
            val templates = effectivePageTemplates(current).toMutableList()
            require(templates.size > 1) { "A notebook must keep at least one page" }
            require(pageIndex in templates.indices) { "Page out of range" }

            templates.removeAt(pageIndex)
            writeNotebookPdf(documentFile(current.fingerprint), templates)

            val bookmarks = current.bookmarks.mapNotNull { page ->
                when {
                    page == pageIndex -> null
                    page > pageIndex -> page - 1
                    else -> page
                }
            }.toSet()
            val lastPage = when {
                current.lastPage > pageIndex -> current.lastPage - 1
                current.lastPage == pageIndex -> pageIndex.coerceAtMost(templates.lastIndex)
                else -> current.lastPage
            }

            update(
                current.copy(
                    notePageCount = templates.size,
                    pageTemplates = templates,
                    bookmarks = bookmarks,
                    lastPage = lastPage.coerceIn(0, templates.lastIndex),
                    lastOpenedAt = System.currentTimeMillis(),
                )
            )
        }

    suspend fun duplicateNotebookPage(entry: LibraryEntry, pageIndex: Int): LibraryEntry =
        withContext(Dispatchers.IO) {
            require(entry.kind == DocumentKind.NOTE) {
                context.getString(R.string.error_not_notebook)
            }
            val current = synchronized(this@PdfLibrary) {
                entries[entry.fingerprint] ?: entry
            }
            val templates = effectivePageTemplates(current).toMutableList()
            require(pageIndex in templates.indices) { "Page out of range" }
            templates.add(pageIndex + 1, templates[pageIndex])
            writeNotebookPdf(documentFile(current.fingerprint), templates)

            val bookmarks = current.bookmarks.map { page ->
                if (page > pageIndex) page + 1 else page
            }.toMutableSet()
            if (pageIndex in current.bookmarks) bookmarks += pageIndex + 1

            update(
                current.copy(
                    notePageCount = templates.size,
                    pageTemplates = templates,
                    bookmarks = bookmarks,
                    lastPage = pageIndex + 1,
                    lastOpenedAt = System.currentTimeMillis(),
                )
            )
        }

    suspend fun moveNotebookPage(
        entry: LibraryEntry,
        fromIndex: Int,
        toIndex: Int,
    ): LibraryEntry = withContext(Dispatchers.IO) {
        require(entry.kind == DocumentKind.NOTE) {
            context.getString(R.string.error_not_notebook)
        }
        val current = synchronized(this@PdfLibrary) {
            entries[entry.fingerprint] ?: entry
        }
        val templates = effectivePageTemplates(current).toMutableList()
        require(fromIndex in templates.indices && toIndex in templates.indices) {
            "Page out of range"
        }
        if (fromIndex == toIndex) return@withContext current

        val moved = templates.removeAt(fromIndex)
        templates.add(toIndex, moved)
        writeNotebookPdf(documentFile(current.fingerprint), templates)

        fun remap(page: Int): Int = when {
            page == fromIndex -> toIndex
            fromIndex < toIndex && page in (fromIndex + 1)..toIndex -> page - 1
            fromIndex > toIndex && page in toIndex until fromIndex -> page + 1
            else -> page
        }

        update(
            current.copy(
                notePageCount = templates.size,
                pageTemplates = templates,
                bookmarks = current.bookmarks.map(::remap).toSet(),
                lastPage = remap(current.lastPage).coerceIn(0, templates.lastIndex),
                lastOpenedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun setNotebookPageTemplate(
        entry: LibraryEntry,
        pageIndex: Int,
        template: PageTemplate,
    ): LibraryEntry = withContext(Dispatchers.IO) {
        require(entry.kind == DocumentKind.NOTE) {
            context.getString(R.string.error_not_notebook)
        }
        val current = synchronized(this@PdfLibrary) {
            entries[entry.fingerprint] ?: entry
        }
        val templates = effectivePageTemplates(current).toMutableList()
        require(pageIndex in templates.indices) { "Page out of range" }
        if (templates[pageIndex] == template) return@withContext current

        templates[pageIndex] = template
        writeNotebookPdf(documentFile(current.fingerprint), templates)
        update(
            current.copy(
                pageTemplates = templates,
                notePageCount = templates.size,
                lastOpenedAt = System.currentTimeMillis(),
            )
        )
    }

    fun pageTemplateAt(entry: LibraryEntry, pageIndex: Int): PageTemplate =
        effectivePageTemplates(entry).getOrElse(pageIndex) { entry.pageTemplate }

    suspend fun touch(entry: LibraryEntry): LibraryEntry = withContext(Dispatchers.IO) {
        update(entry.copy(lastOpenedAt = System.currentTimeMillis()))
    }

    suspend fun updateLastPage(entry: LibraryEntry, pageIndex: Int): LibraryEntry =
        withContext(Dispatchers.IO) {
            val current = synchronized(this@PdfLibrary) {
                entries[entry.fingerprint] ?: entry
            }
            if (current.lastPage == pageIndex) return@withContext current
            update(current.copy(lastPage = pageIndex.coerceAtLeast(0)))
        }

    suspend fun rename(entry: LibraryEntry, newTitle: String): LibraryEntry =
        withContext(Dispatchers.IO) {
            val title = newTitle.trim().ifBlank {
                if (entry.kind == DocumentKind.NOTE) {
                    context.getString(R.string.default_notebook_title)
                } else {
                    context.getString(R.string.default_pdf_note)
                }
            }
            val current = synchronized(this@PdfLibrary) {
                entries[entry.fingerprint] ?: entry
            }
            update(current.copy(title = title))
        }

    suspend fun toggleBookmark(entry: LibraryEntry, pageIndex: Int): LibraryEntry =
        withContext(Dispatchers.IO) {
            val current = synchronized(this@PdfLibrary) {
                entries[entry.fingerprint] ?: entry
            }
            val bookmarks = current.bookmarks.toMutableSet()
            if (!bookmarks.add(pageIndex)) bookmarks.remove(pageIndex)
            update(current.copy(bookmarks = bookmarks))
        }

    suspend fun setCustomCover(entry: LibraryEntry, uri: Uri): LibraryEntry =
        withContext(Dispatchers.IO) {
            val file = coverFile(entry.fingerprint)
            val temp = File(coversDir, "${entry.fingerprint}.cover.tmp")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { context.getString(R.string.error_cover_open) }
                FileOutputStream(temp).use { output -> input.copyTo(output, 512 * 1024) }
            }
            if (file.exists()) file.delete()
            check(temp.renameTo(file)) { context.getString(R.string.error_cover_save) }
            val current = synchronized(this@PdfLibrary) {
                entries[entry.fingerprint] ?: entry
            }
            update(current.copy(hasCustomCover = true))
        }

    suspend fun clearCustomCover(entry: LibraryEntry): LibraryEntry =
        withContext(Dispatchers.IO) {
            runCatching { coverFile(entry.fingerprint).delete() }
            val current = synchronized(this@PdfLibrary) {
                entries[entry.fingerprint] ?: entry
            }
            update(current.copy(hasCustomCover = false))
        }

    suspend fun renderCover(
        entry: LibraryEntry,
        targetWidthPx: Int = 360,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val width = targetWidthPx.coerceIn(160, 900)
        if (entry.hasCustomCover && coverFile(entry.fingerprint).exists()) {
            decodeSampledBitmap(coverFile(entry.fingerprint), width)?.let {
                return@withContext it
            }
        }
        renderFirstPdfPage(documentFile(entry.fingerprint), width)
    }

    suspend fun delete(entry: LibraryEntry): LibraryEntry = withContext(Dispatchers.IO) {
        val current = synchronized(this@PdfLibrary) {
            entries[entry.fingerprint] ?: entry
        }
        update(current.copy(trashedAt = System.currentTimeMillis()))
    }

    suspend fun deletePermanently(entry: LibraryEntry) = withContext(Dispatchers.IO) {
        synchronized(this@PdfLibrary) {
            entries.remove(entry.fingerprint)
            persistLocked()
        }

        listOf(
            documentFile(entry.fingerprint),
            coverFile(entry.fingerprint),
            File(File(context.filesDir, "ink"), "${entry.fingerprint}.json"),
            File(File(context.filesDir, "indexes"), "${entry.fingerprint}.json"),
            File(File(context.filesDir, "handwriting-indexes"), "${entry.fingerprint}.json"),
            File(File(context.filesDir, "text-notes"), "${entry.fingerprint}.json"),
            File(File(context.filesDir, "images"), entry.fingerprint),
            File(File(context.filesDir, "links"), "${entry.fingerprint}.json"),
            File(File(context.filesDir, "outlines"), "${entry.fingerprint}.json"),
        ).forEach { file ->
            runCatching {
                if (file.isDirectory) file.deleteRecursively()
                else if (file.exists()) file.delete()
            }
        }
    }

    fun documentFile(fingerprint: String): File =
        File(documentsDir, "${fingerprint}.pdf")

    private fun coverFile(fingerprint: String): File =
        File(coversDir, "${fingerprint}.cover")

    private fun writeNotebookPdf(
        file: File,
        templates: List<PageTemplate>,
    ) {
        val normalizedTemplates = templates.ifEmpty { listOf(PageTemplate.BLANK) }
        val temp = File(file.parentFile, file.name + ".tmp")
        val document = PdfDocument()
        try {
            normalizedTemplates.forEachIndexed { pageIndex, template ->
                val info = PdfDocument.PageInfo.Builder(
                    NOTE_PAGE_WIDTH,
                    NOTE_PAGE_HEIGHT,
                    pageIndex + 1,
                ).create()
                val page = document.startPage(info)
                try {
                    page.canvas.drawColor(Color.WHITE)
                    drawTemplate(page.canvas, template)
                } finally {
                    document.finishPage(page)
                }
            }
            FileOutputStream(temp).use { document.writeTo(it) }
        } finally {
            document.close()
        }

        if (file.exists()) file.delete()
        check(temp.renameTo(file)) { context.getString(R.string.error_notebook_save) }
    }

    private fun drawTemplate(
        canvas: android.graphics.Canvas,
        template: PageTemplate,
    ) {
        if (template == PageTemplate.BLANK) return

        val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(218, 222, 229)
            strokeWidth = 1.4f
            style = Paint.Style.STROKE
        }
        val step = 72f
        val top = 110f
        val bottom = NOTE_PAGE_HEIGHT - 70f

        when (template) {
            PageTemplate.BLANK -> Unit
            PageTemplate.RULED -> {
                var y = top
                while (y <= bottom) {
                    canvas.drawLine(70f, y, NOTE_PAGE_WIDTH - 70f, y, guidePaint)
                    y += step
                }
            }

            PageTemplate.GRID -> {
                var y = top
                while (y <= bottom) {
                    canvas.drawLine(55f, y, NOTE_PAGE_WIDTH - 55f, y, guidePaint)
                    y += step
                }
                var x = 55f
                while (x <= NOTE_PAGE_WIDTH - 55f) {
                    canvas.drawLine(x, top, x, bottom, guidePaint)
                    x += step
                }
            }

            PageTemplate.DOT -> {
                guidePaint.style = Paint.Style.FILL
                var y = top
                while (y <= bottom) {
                    var x = 70f
                    while (x <= NOTE_PAGE_WIDTH - 70f) {
                        canvas.drawCircle(x, y, 2.2f, guidePaint)
                        x += step
                    }
                    y += step
                }
            }
        }
    }

    private fun effectivePageTemplates(entry: LibraryEntry): List<PageTemplate> {
        val count = entry.notePageCount.coerceAtLeast(1)
        if (entry.pageTemplates.size >= count) {
            return entry.pageTemplates.take(count)
        }
        return buildList(count) {
            addAll(entry.pageTemplates)
            while (size < count) add(entry.pageTemplate)
        }
    }

    private fun renderFirstPdfPage(file: File, targetWidthPx: Int): Bitmap? {
        if (!file.exists()) return null
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(descriptor)
            try {
                if (renderer.pageCount <= 0) return null
                val page = renderer.openPage(0)
                try {
                    val ratio = page.height.toFloat() / page.width.toFloat()
                    val height = max(1, (targetWidthPx * ratio).toInt())
                    return Bitmap.createBitmap(
                        targetWidthPx,
                        height,
                        Bitmap.Config.ARGB_8888
                    ).also { bitmap ->
                        bitmap.eraseColor(Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                    }
                } finally {
                    page.close()
                }
            } finally {
                renderer.close()
            }
        } finally {
            descriptor.close()
        }
    }

    private fun decodeSampledBitmap(file: File, targetWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidthPx) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    @Synchronized
    private fun update(entry: LibraryEntry): LibraryEntry {
        entries[entry.fingerprint] = entry
        persistLocked()
        return entry
    }

    private fun persistLocked() {
        val tmp = File(indexFile.parentFile, indexFile.name + ".tmp")
        tmp.writeText(
            json.encodeToString(
                LibraryIndex(
                    entries = entries.values.toList(),
                    folders = folders.values.toList(),
                )
            )
        )
        if (indexFile.exists()) indexFile.delete()
        tmp.renameTo(indexFile)
    }

    companion object {
        private const val NOTE_PAGE_WIDTH = 1240
        private const val NOTE_PAGE_HEIGHT = 1754
    }
}
