package dev.munote.app.text

import android.content.Context
import dev.munote.app.ocr.OcrRect
import dev.munote.app.ocr.SearchHit
import dev.munote.app.ocr.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class TextBoxNote(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float = 0.42f,
    val height: Float = 0.16f,
    val text: String = "",
    val fontSizeSp: Float = 18f,
)

@Serializable
private data class TextDocument(
    val pages: Map<Int, List<TextBoxNote>> = emptyMap(),
)

class TextStore(
    context: Context,
    fingerprint: String,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val file = File(
        File(context.filesDir, "text-notes").apply { mkdirs() },
        "${fingerprint}.json"
    )
    private val pages = linkedMapOf<Int, MutableList<TextBoxNote>>()

    init {
        if (file.exists()) {
            runCatching {
                json.decodeFromString<TextDocument>(file.readText()).pages.forEach { (page, boxes) ->
                    pages[page] = boxes.toMutableList()
                }
            }
        }
    }

    fun page(index: Int): List<TextBoxNote> = pages[index]?.toList().orEmpty()

    suspend fun add(index: Int, x: Float, y: Float): TextBoxNote = withContext(Dispatchers.IO) {
        val box = TextBoxNote(
            id = UUID.randomUUID().toString(),
            x = x.coerceIn(0.02f, 0.76f),
            y = y.coerceIn(0.02f, 0.80f),
        )
        pages.getOrPut(index) { mutableListOf() }.add(box)
        persist()
        box
    }

    suspend fun update(index: Int, box: TextBoxNote) = withContext(Dispatchers.IO) {
        val list = pages.getOrPut(index) { mutableListOf() }
        val pos = list.indexOfFirst { it.id == box.id }
        if (pos >= 0) list[pos] = box else list.add(box)
        persist()
    }

    suspend fun delete(index: Int, id: String) = withContext(Dispatchers.IO) {
        val list = pages[index] ?: return@withContext
        list.removeAll { it.id == id }
        if (list.isEmpty()) pages.remove(index)
        persist()
    }

    fun search(query: String): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val hits = ArrayList<SearchHit>()
        for ((pageIndex, boxes) in pages.entries.sortedBy { it.key }) {
            for (box in boxes) {
                val at = box.text.indexOf(q, ignoreCase = true)
                if (at < 0) continue
                hits += SearchHit(
                    pageIndex = pageIndex,
                    snippet = snippet(box.text, at, q.length),
                    rect = OcrRect(
                        left = box.x,
                        top = box.y,
                        right = (box.x + box.width).coerceAtMost(1f),
                        bottom = (box.y + box.height).coerceAtMost(1f),
                    ),
                    source = SearchSource.TEXT,
                )
                if (hits.size >= MAX_HITS) return hits
            }
        }
        return hits
    }

    private fun snippet(text: String, at: Int, queryLength: Int): String {
        val start = (at - 18).coerceAtLeast(0)
        val end = (at + queryLength + 30).coerceAtMost(text.length)
        return text.substring(start, end)
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun persist() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(TextDocument(pages.mapValues { it.value.toList() })))
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    companion object {
        private const val MAX_HITS = 200
    }
}
