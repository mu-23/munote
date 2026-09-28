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
data class OcrRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Serializable
data class OcrBlock(
    val text: String,
    val rect: OcrRect,
)

@Serializable
data class OcrPage(
    val text: String,
    val blocks: List<OcrBlock> = emptyList(),
)

@Serializable
data class OcrDocument(
    val pages: Map<Int, OcrPage> = emptyMap(),
)

@Serializable
private data class LegacyOcrDocument(
    val pages: Map<Int, String> = emptyMap(),
)

data class OcrRecognition(
    val text: String,
    val blocks: List<OcrBlock>,
)

data class SearchHit(
    val pageIndex: Int,
    val snippet: String,
    val rect: OcrRect? = null,
)

class ChineseOcrEngine {
    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    suspend fun recognize(bitmap: Bitmap): OcrRecognition = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                if (!cont.isActive) return@addOnSuccessListener
                val width = bitmap.width.toFloat().coerceAtLeast(1f)
                val height = bitmap.height.toFloat().coerceAtLeast(1f)
                val blocks = result.textBlocks
                    .flatMap { it.lines }
                    .mapNotNull { line ->
                        val box = line.boundingBox ?: return@mapNotNull null
                        if (line.text.isBlank()) return@mapNotNull null
                        OcrBlock(
                            text = line.text,
                            rect = OcrRect(
                                left = (box.left / width).coerceIn(0f, 1f),
                                top = (box.top / height).coerceIn(0f, 1f),
                                right = (box.right / width).coerceIn(0f, 1f),
                                bottom = (box.bottom / height).coerceIn(0f, 1f),
                            )
                        )
                    }
                cont.resume(OcrRecognition(result.text, blocks))
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(OcrRecognition("", emptyList()))
            }
    }

    fun close() = recognizer.close()
}

class OcrIndexStore(
    context: Context,
    fingerprint: String,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val file = File(File(context.filesDir, "indexes").apply { mkdirs() }, "${fingerprint}.json")
    private val pages = linkedMapOf<Int, OcrPage>()

    init {
        if (file.exists()) {
            val source = runCatching { file.readText() }.getOrNull()
            if (source != null) {
                val current = runCatching { json.decodeFromString<OcrDocument>(source) }.getOrNull()
                if (current != null) {
                    pages.putAll(current.pages)
                } else {
                    // Migrate the earliest MVP index, which stored only one text string per page.
                    runCatching { json.decodeFromString<LegacyOcrDocument>(source) }
                        .getOrNull()
                        ?.pages
                        ?.forEach { (page, text) -> pages[page] = OcrPage(text = text) }
                }
            }
        }
    }

    fun hasPage(index: Int): Boolean = pages.containsKey(index)
    fun needsRefresh(index: Int): Boolean {\n        val page = pages[index] ?: return true\n        return page.text.isNotBlank() && page.blocks.isEmpty()\n    }\n    fun pageText(index: Int): String? = pages[index]?.text\n    fun completedPages(): Int = pages.size\n
    suspend fun put(index: Int, recognition: OcrRecognition) = withContext(Dispatchers.IO) {
        pages[index] = OcrPage(recognition.text, recognition.blocks)
        persist()
    }

    fun search(query: String): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val hits = ArrayList<SearchHit>()
        for ((pageIndex, page) in pages.entries.sortedBy { it.key }) {
            var positionedMatches = 0
            for (block in page.blocks) {
                val at = block.text.indexOf(q, ignoreCase = true)
                if (at < 0) continue
                hits += SearchHit(
                    pageIndex = pageIndex,
                    snippet = snippet(block.text, at, q.length),
                    rect = block.rect,
                )
                positionedMatches++
                if (hits.size >= MAX_HITS) return hits
            }

            // Some OCR engines occasionally return useful full-page text without a bounding box.
            // Keep that content searchable, but do not duplicate a page that already has positioned hits.
            if (positionedMatches == 0) {
                val at = page.text.indexOf(q, ignoreCase = true)
                if (at >= 0) {
                    hits += SearchHit(
                        pageIndex = pageIndex,
                        snippet = snippet(page.text, at, q.length),
                        rect = null,
                    )
                    if (hits.size >= MAX_HITS) return hits
                }
            }
        }
        return hits
    }

    private fun snippet(text: String, at: Int, queryLength: Int): String {
        val start = (at - 24).coerceAtLeast(0)
        val end = (at + queryLength + 38).coerceAtMost(text.length)
        return text.substring(start, end)
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun persist() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(OcrDocument(pages.toMap())))
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    companion object {
        private const val MAX_HITS = 200
    }
}
