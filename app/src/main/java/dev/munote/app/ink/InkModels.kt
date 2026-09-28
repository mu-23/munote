package dev.munote.app.ink

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

enum class InkTool { PEN, HIGHLIGHTER, ERASER }

@Serializable
data class InkPoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val timeMs: Long
)

@Serializable
data class InkStroke(
    val points: List<InkPoint>,
    val colorArgb: Int,
    val baseWidthDp: Float,
    val highlighter: Boolean = false
)

@Serializable
data class InkDocument(
    val pages: Map<Int, List<InkStroke>> = emptyMap()
)

class InkStore(context: Context, fingerprint: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(File(context.filesDir, "ink").apply { mkdirs() }, "${fingerprint}.json")
    private val pages = linkedMapOf<Int, MutableList<InkStroke>>()

    init {
        if (file.exists()) {
            runCatching {
                json.decodeFromString<InkDocument>(file.readText()).pages.forEach { (k, v) ->
                    pages[k] = v.toMutableList()
                }
            }
        }
    }

    fun page(index: Int): List<InkStroke> = pages[index]?.toList().orEmpty()

    suspend fun replacePage(index: Int, strokes: List<InkStroke>) = withContext(Dispatchers.IO) {
        pages[index] = strokes.toMutableList()
        persist()
    }

    suspend fun append(index: Int, stroke: InkStroke) = withContext(Dispatchers.IO) {
        pages.getOrPut(index) { mutableListOf() }.add(stroke)
        persist()
    }

    suspend fun undo(index: Int): List<InkStroke> = withContext(Dispatchers.IO) {
        val list = pages.getOrPut(index) { mutableListOf() }
        if (list.isNotEmpty()) list.removeAt(list.lastIndex)
        persist()
        list.toList()
    }

    private fun persist() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(InkDocument(pages.mapValues { it.value.toList() })))
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }
}
