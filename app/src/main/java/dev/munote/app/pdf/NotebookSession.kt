package dev.munote.app.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class NotebookSession(
    override val fingerprint: String,
    templates: List<PageTemplate>,
) : DocumentSession {
    private var pageTemplates: List<PageTemplate> =
        templates.ifEmpty { listOf(PageTemplate.BLANK) }

    override val pageCount: Int
        get() = pageTemplates.size

    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun templates(): List<PageTemplate> = pageTemplates.toList()

    fun updateTemplates(templates: List<PageTemplate>) {
        pageTemplates = templates.ifEmpty { listOf(PageTemplate.BLANK) }
        cache.evictAll()
    }

    fun templateAt(index: Int): PageTemplate =
        pageTemplates.getOrElse(index) { PageTemplate.BLANK }

    override suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap =
        withContext(Dispatchers.Default) {
            require(index in 0 until pageCount) { "页面不存在" }
            val width = targetWidthPx.coerceIn(160, 2400)
            val template = templateAt(index)
            val key = "${index}:${width}:${template.name}"
            cache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }

            val height = (width * A4_HEIGHT_RATIO).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            drawTemplate(canvas, width.toFloat(), height.toFloat(), template)
            cache.put(key, bitmap)
            bitmap
        }

    override fun clearRenderCache() {
        cache.evictAll()
    }

    override fun close() {
        cache.evictAll()
    }

    private fun drawTemplate(
        canvas: Canvas,
        width: Float,
        height: Float,
        template: PageTemplate,
    ) {
        if (template == PageTemplate.BLANK) return

        val scale = width / 1000f
        val margin = 54f * scale
        val spacing = when (template) {
            PageTemplate.RULED -> 54f * scale
            PageTemplate.GRID,
            PageTemplate.DOT -> 44f * scale
            PageTemplate.BLANK -> return
        }.coerceAtLeast(14f)

        when (template) {
            PageTemplate.RULED -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(221, 226, 233)
                    strokeWidth = (1.15f * scale).coerceAtLeast(1f)
                }
                var y = margin + spacing
                while (y < height - margin) {
                    canvas.drawLine(margin, y, width - margin, y, paint)
                    y += spacing
                }
            }

            PageTemplate.GRID -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(229, 233, 239)
                    strokeWidth = (1.0f * scale).coerceAtLeast(1f)
                }
                var x = margin
                while (x <= width - margin) {
                    canvas.drawLine(x, margin, x, height - margin, paint)
                    x += spacing
                }
                var y = margin
                while (y <= height - margin) {
                    canvas.drawLine(margin, y, width - margin, y, paint)
                    y += spacing
                }
            }

            PageTemplate.DOT -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(205, 211, 221)
                    style = Paint.Style.FILL
                }
                val radius = (1.55f * scale).coerceAtLeast(1.05f)
                var y = margin
                while (y <= height - margin) {
                    var x = margin
                    while (x <= width - margin) {
                        canvas.drawCircle(x, y, radius, paint)
                        x += spacing
                    }
                    y += spacing
                }
            }

            PageTemplate.BLANK -> Unit
        }
    }

    companion object {
        private const val A4_HEIGHT_RATIO = 1.41421356f
    }
}
