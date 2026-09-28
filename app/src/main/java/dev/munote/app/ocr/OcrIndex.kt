package dev.munote.app.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.resume

@Serializable
data class OcrDocument(
    val pages: Map<Int, String> = emptyMap()
)

data class SearchHit(
    val pageIndex: Int,
    val snippet: String
)

class ChineseOcrEngine {
    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
            .addOnFailureListener { if (cont.isActive) cont.resume("") }
    }

    fun close() = recognizer.close()
}

class OcrIndexStore(
    context: Context,
    fingerprint: String,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val file = File(File(context.filesDir, "indexes").apply { mkdirs() }, "${fingerprint}.json")
    private val pages = linkedMapOf<Int, String>()

    init {
        if (file.exists()) {
            runCatching {
                pages.putAll(json.decodeFromString<OcrDocument>(file.readText()).pages)
            }
        }
    }

    fun hasPage(index: Int): Boolean = pages.containsKey(index)
    fun pageText(index: Int): String? = pages[index]
    fun completedPages(): Int = pages.size

    suspend fun put(index: Int, text: String) = withContext(Dispatchers.IO) {
        pages[index] = text
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(OcrDocument(pages.toMap())))
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    fun search(query: String): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return pages.entries
            .sortedBy { it.key }
            .mapNotNull { (page, text) ->
                val at = text.indexOf(q, ignoreCase = true)
                if (at < 0) return@mapNotNull null
                val start = (at - 24).coerceAtLeast(0)
                val end = (at + q.length + 38).coerceAtMost(text.length)
                val snippet = text.substring(start, end)
                    .replace('\n', ' ')
                    .replace(Regex("\\s+"), " ")
                SearchHit(page, snippet)
            }
    }
}
