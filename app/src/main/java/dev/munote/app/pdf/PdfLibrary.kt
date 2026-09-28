package dev.munote.app.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class LibraryEntry(
    val fingerprint: String,
    val title: String,
    val importedAt: Long,
    val lastOpenedAt: Long,
    val lastPage: Int = 0,
    val bookmarks: Set<Int> = emptySet(),
)

@Serializable
private data class LibraryIndex(
    val entries: List<LibraryEntry> = emptyList(),
)

/**
 * Local-first document library.
 *
 * Imported PDFs live under files/documents/<sha256>.pdf. The library index keeps user-facing
 * metadata, resume position, and enough information to manage the document without asking the
 * system file picker again.
 */
class PdfLibrary(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val documentsDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val indexFile = File(documentsDir, "library.json")
    private val entries = linkedMapOf<String, LibraryEntry>()

    init {
        if (indexFile.exists()) {
            runCatching {
                json.decodeFromString<LibraryIndex>(indexFile.readText()).entries.forEach { entry ->
                    entries[entry.fingerprint] = entry
                }
            }
        }
    }

    @Synchronized
    fun entries(): List<LibraryEntry> =
        entries.values
            .filter { documentFile(it.fingerprint).exists() }
            .sortedByDescending { it.lastOpenedAt }

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
            ?: "PDF 笔记"
    }

    suspend fun registerImported(fingerprint: String, title: String): LibraryEntry =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val previous = synchronized(this@PdfLibrary) { entries[fingerprint] }
            val entry = LibraryEntry(
                fingerprint = fingerprint,
                title = title.ifBlank { previous?.title ?: "PDF 笔记" },
                importedAt = previous?.importedAt ?: now,
                lastOpenedAt = now,
                lastPage = previous?.lastPage ?: 0,
                bookmarks = previous?.bookmarks ?: emptySet(),
            )
            synchronized(this@PdfLibrary) {
                entries[fingerprint] = entry
                persistLocked()
            }
            entry
        }

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
            val title = newTitle.trim().ifBlank { "PDF 笔记" }
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

    suspend fun delete(entry: LibraryEntry) = withContext(Dispatchers.IO) {
        synchronized(this@PdfLibrary) {
            entries.remove(entry.fingerprint)
            persistLocked()
        }

        // Document-related files are all keyed by the SHA-256 fingerprint, so deletion is scoped to
        // this one library item. Failures are best-effort and do not leave a ghost entry in the UI.
        listOf(
            documentFile(entry.fingerprint),
            File(File(context.filesDir, "ink"), "${entry.fingerprint}.json"),
            File(File(context.filesDir, "indexes"), "${entry.fingerprint}.json"),
            File(File(context.filesDir, "handwriting-indexes"), "${entry.fingerprint}.json"),
        ).forEach { file ->
            runCatching { if (file.exists()) file.delete() }
        }
    }

    fun documentFile(fingerprint: String): File =
        File(documentsDir, "${fingerprint}.pdf")

    @Synchronized
    private fun update(entry: LibraryEntry): LibraryEntry {
        entries[entry.fingerprint] = entry
        persistLocked()
        return entry
    }

    private fun persistLocked() {
        val tmp = File(indexFile.parentFile, indexFile.name + ".tmp")
        tmp.writeText(json.encodeToString(LibraryIndex(entries.values.toList())))
        if (indexFile.exists()) indexFile.delete()
        tmp.renameTo(indexFile)
    }
}
