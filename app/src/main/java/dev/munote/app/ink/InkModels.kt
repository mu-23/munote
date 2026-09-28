package dev.munote.app.ink

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

enum class InkTool { PEN, HIGHLIGHTER, ERASER, LASSO }

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

/**
 * Persistent page ink with lightweight in-memory history.
 *
 * Undo/redo stores page snapshots rather than trying to infer the inverse of each edit. This keeps
 * erasing, future lasso transforms, and ordinary pen strokes on the same history path.
 */
class InkStore(context: Context, fingerprint: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(File(context.filesDir, "ink").apply { mkdirs() }, "${fingerprint}.json")
    private val pages = linkedMapOf<Int, MutableList<InkStroke>>()
    private val undoStacks = mutableMapOf<Int, ArrayDeque<List<InkStroke>>>()
    private val redoStacks = mutableMapOf<Int, ArrayDeque<List<InkStroke>>>()

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
    fun pageIndices(): List<Int> = pages.entries
        .filter { it.value.isNotEmpty() }
        .map { it.key }
        .sorted()

    fun canUndo(index: Int): Boolean = !undoStacks[index].isNullOrEmpty()
    fun canRedo(index: Int): Boolean = !redoStacks[index].isNullOrEmpty()

    suspend fun replacePage(index: Int, strokes: List<InkStroke>) = withContext(Dispatchers.IO) {
        val before = page(index)
        if (before == strokes) return@withContext
        pushUndo(index, before)
        redoStacks[index]?.clear()
        pages[index] = strokes.toMutableList()
        persist()
    }

    suspend fun append(index: Int, stroke: InkStroke) = withContext(Dispatchers.IO) {
        val before = page(index)
        pushUndo(index, before)
        redoStacks[index]?.clear()
        pages.getOrPut(index) { mutableListOf() }.add(stroke)
        persist()
    }

    suspend fun undo(index: Int): List<InkStroke> = withContext(Dispatchers.IO) {
        val stack = undoStacks[index]
        val previous = stack?.removeLastOrNull() ?: return@withContext page(index)
        pushRedo(index, page(index))
        pages[index] = previous.toMutableList()
        persist()
        previous
    }

    suspend fun redo(index: Int): List<InkStroke> = withContext(Dispatchers.IO) {
        val stack = redoStacks[index]
        val next = stack?.removeLastOrNull() ?: return@withContext page(index)
        pushUndo(index, page(index), clearRedo = false)
        pages[index] = next.toMutableList()
        persist()
        next
    }

    private fun pushUndo(index: Int, snapshot: List<InkStroke>, clearRedo: Boolean = false) {
        val stack = undoStacks.getOrPut(index) { ArrayDeque() }
        stack.addLast(snapshot)
        while (stack.size > HISTORY_LIMIT) stack.removeFirst()
        if (clearRedo) redoStacks[index]?.clear()
    }

    private fun pushRedo(index: Int, snapshot: List<InkStroke>) {
        val stack = redoStacks.getOrPut(index) { ArrayDeque() }
        stack.addLast(snapshot)
        while (stack.size > HISTORY_LIMIT) stack.removeFirst()
    }

    private fun persist() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(InkDocument(pages.mapValues { it.value.toList() })))
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    companion object {
        private const val HISTORY_LIMIT = 40
    }
}
