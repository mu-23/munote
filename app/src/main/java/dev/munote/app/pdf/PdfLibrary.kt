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
)

@Serializable
private data class LibraryIndex(
    val entries: List<LibraryEntry> = emptyList(),
)

/**
 * Tiny local-first document library.
 *
 * Imported PDFs already live under files/documents/<sha256>.pdf. This index only keeps user-facing
 * metadata so the app can reopen them without asking the system file picker every time.
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
            )
            synchronized(this@PdfLibrary) {
                entries[fingerprint] = entry
                persistLocked()
            }
            entry
        }

    suspend fun touch(entry: LibraryEntry): LibraryEntry = withContext(Dispatchers.IO) {
        val updated = entry.copy(lastOpenedAt = System.currentTimeMillis())
        synchronized(this@PdfLibrary) {
            entries[entry.fingerprint] = updated
            persistLocked()
        }
        updated
    }

    fun documentFile(fingerprint: String): File =
        File(documentsDir, "${fingerprint}.pdf")

    private fun persistLocked() {
        val tmp = File(indexFile.parentFile, indexFile.name + ".tmp")
        tmp.writeText(json.encodeToString(LibraryIndex(entries.values.toList())))
        if (indexFile.exists()) indexFile.delete()
        tmp.renameTo(indexFile)
    }
}
