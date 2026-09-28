package dev.munote.app.handwriting

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import dev.munote.app.ink.InkStroke
import dev.munote.app.ocr.OcrRect
import dev.munote.app.ocr.SearchHit
import dev.munote.app.ocr.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

enum class HandwritingModelState {
    NOT_READY,
    DOWNLOADING,
    READY,
    ERROR,
}

@Serializable
data class HandwritingBlock(
    val text: String,
    val rect: OcrRect,
)

@Serializable
data class HandwritingPage(
    val blocks: List<HandwritingBlock> = emptyList(),
)

@Serializable
private data class HandwritingDocument(
    val pages: Map<Int, HandwritingPage> = emptyMap(),
)

/**
 * Local ML Kit digital-ink recognizer for the vector strokes MuNote already stores.
 *
 * The Chinese model is downloaded once (roughly 20 MB according to ML Kit docs), then recognition
 * runs locally on the device. Strokes are split into rough writing lines before recognition because
 * ML Kit's writing-area hint is most useful for a single line.
 */
class ChineseHandwritingRecognizer {
    private val identifier = requireNotNull(
        DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hani-CN")
    ) { "没有找到简体中文数字墨水模型" }

    private val model = DigitalInkRecognitionModel.builder(identifier).build()
    private val modelManager = RemoteModelManager.getInstance()
    private val recognizer: DigitalInkRecognizer = DigitalInkRecognition.getClient(
        DigitalInkRecognizerOptions.builder(model)
            .setMaxResultCount(3)
            .build()
    )

    suspend fun isReady(): Boolean = runCatching {
        modelManager.isModelDownloaded(model).awaitTask()
    }.getOrDefault(false)

    suspend fun ensureReady(): Boolean {
        if (isReady()) return true
        return runCatching {
            modelManager.download(
                model,
                DownloadConditions.Builder().build()
            ).awaitTask()
            true
        }.getOrDefault(false)
    }

    suspend fun recognizePage(strokes: List<InkStroke>): List<HandwritingBlock> {
        val penStrokes = strokes.filter { !it.highlighter && it.points.isNotEmpty() }
        if (penStrokes.isEmpty()) return emptyList()
        if (!isReady()) return emptyList()

        val lines = clusterIntoLines(penStrokes)
        val out = ArrayList<HandwritingBlock>(lines.size)

        for (line in lines) {
            val bounds = boundsOf(line)
            val inkBuilder = Ink.builder()

            line.forEach { stroke ->
                val strokeBuilder = Ink.Stroke.builder()
                stroke.points.forEach { point ->
                    strokeBuilder.addPoint(
                        Ink.Point.create(
                            point.x * PAGE_COORDS,
                            (point.y - bounds.top) * PAGE_COORDS,
                            point.timeMs,
                        )
                    )
                }
                inkBuilder.addStroke(strokeBuilder.build())
            }

            val lineHeight = ((bounds.bottom - bounds.top) * PAGE_COORDS)
                .coerceAtLeast(MIN_WRITING_HEIGHT)
            val context = RecognitionContext.builder()
                .setWritingArea(
                    WritingArea(
                        PAGE_COORDS,
                        (lineHeight * 1.7f).coerceAtMost(PAGE_COORDS),
                    )
                )
                .build()

            val result = runCatching {
                recognizer.recognize(inkBuilder.build(), context).awaitTask()
            }.getOrNull() ?: continue

            val text = result.candidates.firstOrNull()?.text?.trim().orEmpty()
            if (text.isBlank()) continue

            out += HandwritingBlock(
                text = text,
                rect = OcrRect(
                    left = bounds.left.coerceIn(0f, 1f),
                    top = bounds.top.coerceIn(0f, 1f),
                    right = bounds.right.coerceIn(0f, 1f),
                    bottom = bounds.bottom.coerceIn(0f, 1f),
                )
            )
        }

        return out
    }

    fun close() = recognizer.close()

    private fun clusterIntoLines(strokes: List<InkStroke>): List<List<InkStroke>> {
        if (strokes.isEmpty()) return emptyList()

        val lines = mutableListOf<MutableList<InkStroke>>()
        val lineBounds = mutableListOf<StrokeBounds>()

        for (stroke in strokes) {
            val bounds = boundsOf(listOf(stroke))
            val centerY = (bounds.top + bounds.bottom) * 0.5f

            var bestIndex = -1
            var bestDistance = Float.MAX_VALUE

            for (i in lines.indices) {
                val lb = lineBounds[i]
                val lineCenter = (lb.top + lb.bottom) * 0.5f
                val lineHeight = (lb.bottom - lb.top).coerceAtLeast(0.02f)
                val strokeHeight = (bounds.bottom - bounds.top).coerceAtLeast(0.01f)
                val verticalTolerance = max(0.042f, max(lineHeight, strokeHeight) * 0.85f)

                val overlapsVertically =
                    min(lb.bottom, bounds.bottom) - max(lb.top, bounds.top) > -0.012f
                val distance = kotlin.math.abs(centerY - lineCenter)

                if ((overlapsVertically || distance <= verticalTolerance) && distance < bestDistance) {
                    bestIndex = i
                    bestDistance = distance
                }
            }

            if (bestIndex == -1) {
                lines += mutableListOf(stroke)
                lineBounds += bounds
            } else {
                lines[bestIndex] += stroke
                lineBounds[bestIndex] = lineBounds[bestIndex].union(bounds)
            }
        }

        return lines
            .zip(lineBounds)
            .sortedWith(compareBy({ it.second.top }, { it.second.left }))
            .map { (line, _) -> line.sortedBy { it.points.firstOrNull()?.timeMs ?: Long.MAX_VALUE } }
    }

    private fun boundsOf(strokes: List<InkStroke>): StrokeBounds {
        var left = 1f
        var top = 1f
        var right = 0f
        var bottom = 0f

        strokes.forEach { stroke ->
            stroke.points.forEach { p ->
                left = min(left, p.x)
                top = min(top, p.y)
                right = max(right, p.x)
                bottom = max(bottom, p.y)
            }
        }

        if (right < left || bottom < top) {
            return StrokeBounds(0f, 0f, 0f, 0f)
        }

        val padX = 0.008f
        val padY = 0.012f
        return StrokeBounds(
            left = (left - padX).coerceAtLeast(0f),
            top = (top - padY).coerceAtLeast(0f),
            right = (right + padX).coerceAtMost(1f),
            bottom = (bottom + padY).coerceAtMost(1f),
        )
    }

    private data class StrokeBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        fun union(other: StrokeBounds) = StrokeBounds(
            left = min(left, other.left),
            top = min(top, other.top),
            right = max(right, other.right),
            bottom = max(bottom, other.bottom),
        )
    }

    companion object {
        private const val PAGE_COORDS = 1000f
        private const val MIN_WRITING_HEIGHT = 90f
    }
}

class HandwritingIndexStore(
    context: Context,
    fingerprint: String,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val file = File(
        File(context.filesDir, "handwriting-indexes").apply { mkdirs() },
        "${fingerprint}.json"
    )
    private val pages = linkedMapOf<Int, HandwritingPage>()

    init {
        if (file.exists()) {
            runCatching {
                pages.putAll(json.decodeFromString<HandwritingDocument>(file.readText()).pages)
            }
        }
    }

    fun pageText(index: Int): String =
        pages[index]?.blocks?.joinToString(" ") { it.text }.orEmpty()

    suspend fun put(index: Int, blocks: List<HandwritingBlock>) = withContext(Dispatchers.IO) {
        if (blocks.isEmpty()) pages.remove(index)
        else pages[index] = HandwritingPage(blocks)
        persist()
    }

    fun search(query: String): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val hits = ArrayList<SearchHit>()
        for ((pageIndex, page) in pages.entries.sortedBy { it.key }) {
            for (block in page.blocks) {
                val at = block.text.indexOf(q, ignoreCase = true)
                if (at < 0) continue
                hits += SearchHit(
                    pageIndex = pageIndex,
                    snippet = snippet(block.text, at, q.length),
                    rect = block.rect,
                    source = SearchSource.HANDWRITING,
                )
                if (hits.size >= MAX_HITS) return hits
            }
        }
        return hits
    }

    private fun snippet(text: String, at: Int, queryLength: Int): String {
        val start = (at - 14).coerceAtLeast(0)
        val end = (at + queryLength + 24).coerceAtMost(text.length)
        return text.substring(start, end)
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun persist() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(HandwritingDocument(pages.toMap())))
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    companion object {
        private const val MAX_HITS = 200
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { value ->
        if (cont.isActive) cont.resume(value)
    }
    addOnFailureListener { error ->
        if (cont.isActive) cont.resumeWithException(error)
    }
}
