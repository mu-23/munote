package dev.munote.app.ink

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

enum class InkTool { PEN, HIGHLIGHTER, ERASER, LASSO, TEXT, IMAGE, SHAPE, RULER }

@Serializable
enum class InkShape { LINE, RECTANGLE, ELLIPSE, ARROW, TRIANGLE }

@Serializable
enum class InkBrush { FOUNTAIN, BALLPOINT, PENCIL }

enum class EraserMode { STROKE, PIXEL }

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
    val highlighter: Boolean = false,
    val shape: InkShape? = null,
    val brush: InkBrush = InkBrush.FOUNTAIN,
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
    private val lock = Any()

    init {
        if (file.exists()) {
            runCatching {
                val decoded = json.decodeFromString<InkDocument>(file.readText())
                synchronized(lock) {
                    decoded.pages.forEach { (page, strokes) ->
                        val safe = strokes.mapNotNull(::sanitizeStroke)
                        if (safe.isNotEmpty()) pages[page] = safe.toMutableList()
                    }
                }
            }.onFailure {
                // Keep the original bytes for diagnosis/recovery, but never let one malformed
                // sidecar make the source PDF/notebook permanently unopenable.
                runCatching {
                    val quarantine = File(
                        file.parentFile,
                        file.nameWithoutExtension + ".corrupt-" +
                            System.currentTimeMillis() + ".json"
                    )
                    file.copyTo(quarantine, overwrite = false)
                }
            }
        }
    }

    fun page(index: Int): List<InkStroke> = synchronized(lock) {
        pages[index]?.toList().orEmpty()
    }

    fun pageIndices(): List<Int> = synchronized(lock) {
        pages.entries
            .filter { it.value.isNotEmpty() }
            .map { it.key }
            .sorted()
    }

    fun canUndo(index: Int): Boolean = synchronized(lock) {
        !undoStacks[index].isNullOrEmpty()
    }

    fun canRedo(index: Int): Boolean = synchronized(lock) {
        !redoStacks[index].isNullOrEmpty()
    }

    suspend fun replacePage(index: Int, strokes: List<InkStroke>) = withContext(Dispatchers.IO) {
        val safe = strokes.mapNotNull(::sanitizeStroke)
        synchronized(lock) {
            val before = pages[index]?.toList().orEmpty()
            if (before == safe) return@withContext
            pushUndoLocked(index, before)
            redoStacks[index]?.clear()
            pages[index] = safe.toMutableList()
        }
        persist()
    }

    suspend fun append(index: Int, stroke: InkStroke) = withContext(Dispatchers.IO) {
        val safe = sanitizeStroke(stroke) ?: return@withContext
        synchronized(lock) {
            val before = pages[index]?.toList().orEmpty()
            pushUndoLocked(index, before)
            redoStacks[index]?.clear()
            pages.getOrPut(index) { mutableListOf() }.add(safe)
        }
        persist()
    }

    suspend fun undo(index: Int): List<InkStroke> = withContext(Dispatchers.IO) {
        val previous = synchronized(lock) {
            val stack = undoStacks[index]
            val value = stack?.removeLastOrNull() ?: return@synchronized null
            pushRedoLocked(index, pages[index]?.toList().orEmpty())
            pages[index] = value.toMutableList()
            value
        } ?: return@withContext page(index)
        persist()
        previous
    }

    suspend fun redo(index: Int): List<InkStroke> = withContext(Dispatchers.IO) {
        val next = synchronized(lock) {
            val stack = redoStacks[index]
            val value = stack?.removeLastOrNull() ?: return@synchronized null
            pushUndoLocked(index, pages[index]?.toList().orEmpty())
            pages[index] = value.toMutableList()
            value
        } ?: return@withContext page(index)
        persist()
        next
    }

    suspend fun deletePage(index: Int) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val remapped = linkedMapOf<Int, MutableList<InkStroke>>()
            pages.entries.sortedBy { it.key }.forEach { (page, strokes) ->
                when {
                    page < index -> remapped[page] = strokes.toMutableList()
                    page > index -> remapped[page - 1] = strokes.toMutableList()
                }
            }
            pages.clear()
            pages.putAll(remapped)
            undoStacks.clear()
            redoStacks.clear()
        }
        persist()
    }

    suspend fun duplicatePage(index: Int) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val source = pages[index]?.toList().orEmpty()
            val remapped = linkedMapOf<Int, MutableList<InkStroke>>()
            pages.entries.sortedBy { it.key }.forEach { (page, strokes) ->
                remapped[if (page > index) page + 1 else page] = strokes.toMutableList()
            }
            if (source.isNotEmpty()) remapped[index + 1] = source.toMutableList()
            pages.clear()
            pages.putAll(remapped.toSortedMap())
            undoStacks.clear()
            redoStacks.clear()
        }
        persist()
    }

    suspend fun movePage(fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        if (fromIndex == toIndex) return@withContext
        fun remap(page: Int): Int = when {
            page == fromIndex -> toIndex
            fromIndex < toIndex && page in (fromIndex + 1)..toIndex -> page - 1
            fromIndex > toIndex && page in toIndex until fromIndex -> page + 1
            else -> page
        }
        synchronized(lock) {
            val moved = linkedMapOf<Int, MutableList<InkStroke>>()
            pages.forEach { (page, strokes) ->
                moved[remap(page)] = strokes.toMutableList()
            }
            pages.clear()
            pages.putAll(moved.toSortedMap())
            undoStacks.clear()
            redoStacks.clear()
        }
        persist()
    }

    private fun pushUndoLocked(index: Int, snapshot: List<InkStroke>) {
        val stack = undoStacks.getOrPut(index) { ArrayDeque() }
        stack.addLast(snapshot)
        while (stack.size > HISTORY_LIMIT) stack.removeFirst()
    }

    private fun pushRedoLocked(index: Int, snapshot: List<InkStroke>) {
        val stack = redoStacks.getOrPut(index) { ArrayDeque() }
        stack.addLast(snapshot)
        while (stack.size > HISTORY_LIMIT) stack.removeFirst()
    }

    private fun sanitizeStroke(stroke: InkStroke): InkStroke? {
        val safeWidth = stroke.baseWidthDp
            .takeIf { it.isFinite() }
            ?.coerceIn(0.2f, 64f)
            ?: return null
        val safePoints = stroke.points.mapNotNull { point ->
            if (!point.x.isFinite() || !point.y.isFinite() || !point.pressure.isFinite()) {
                null
            } else {
                point.copy(
                    x = point.x.coerceIn(0f, 1f),
                    y = point.y.coerceIn(0f, 1f),
                    pressure = point.pressure.coerceIn(0.01f, 1f),
                    timeMs = point.timeMs.coerceAtLeast(0L),
                )
            }
        }
        if (safePoints.isEmpty()) return null
        return stroke.copy(
            points = safePoints,
            baseWidthDp = safeWidth,
        )
    }

    private fun persist() {
        val document = synchronized(lock) {
            InkDocument(pages.mapValues { (_, value) -> value.toList() })
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(document))
        if (file.exists()) file.delete()
        check(tmp.renameTo(file)) { "Unable to persist ink" }
    }

    companion object {
        private const val HISTORY_LIMIT = 40
    }
}
