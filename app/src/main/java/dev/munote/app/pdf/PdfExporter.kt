package dev.munote.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.net.Uri
import dev.munote.app.ink.InkPoint
import dev.munote.app.ink.InkStore
import dev.munote.app.ink.InkStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Exports the current document as a flattened PDF with MuNote ink baked into each page.
 *
 * The source PDF remains untouched. This first exporter intentionally flattens pages so the result
 * opens consistently in ordinary PDF readers. MuNote's own OCR/search index remains attached to the
 * source document inside the app; a searchable OCR text layer can be added to exported files later.
 */
object PdfExporter {
    suspend fun exportFlattened(
        context: Context,
        session: PdfSession,
        inkStore: InkStore,
        destination: Uri,
        targetWidthPx: Int = 1600,
        onProgress: (completedPages: Int, totalPages: Int) -> Unit = { _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            for (pageIndex in 0 until session.pageCount) {
                currentCoroutineContext().ensureActive()
                val bitmap = session.renderPage(pageIndex, targetWidthPx)
                val pageInfo = PdfDocument.PageInfo.Builder(
                    bitmap.width,
                    bitmap.height,
                    pageIndex + 1
                ).create()
                val page = document.startPage(pageInfo)
                try {
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    InkPdfRenderer.draw(
                        canvas = page.canvas,
                        strokes = inkStore.page(pageIndex),
                        pageWidth = bitmap.width.toFloat(),
                        pageHeight = bitmap.height.toFloat(),
                    )
                } finally {
                    document.finishPage(page)
                }
                withContext(Dispatchers.Main.immediate) {
                    onProgress(pageIndex + 1, session.pageCount)
                }
            }

            currentCoroutineContext().ensureActive()
            context.contentResolver.openOutputStream(destination, "w").use { output ->
                requireNotNull(output) { "无法创建导出文件" }
                document.writeTo(output)
            }
            session.pageCount
        } finally {
            document.close()
        }
    }
}

private object InkPdfRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    fun draw(
        canvas: Canvas,
        strokes: List<InkStroke>,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        strokes.forEach { stroke ->
            drawStroke(canvas, stroke, pageWidth, pageHeight)
        }
    }

    private fun drawStroke(
        canvas: Canvas,
        stroke: InkStroke,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        val points = stroke.points
        if (points.isEmpty()) return

        paint.color = stroke.colorArgb
        paint.alpha = if (stroke.highlighter) 82 else 255
        paint.style = Paint.Style.STROKE

        if (points.size == 1) {
            paint.style = Paint.Style.FILL
            val radius = widthFor(
                stroke = stroke,
                a = points[0],
                b = points[0],
                speedPxMs = 0f,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
            ) / 2f
            canvas.drawCircle(
                points[0].x * pageWidth,
                points[0].y * pageHeight,
                radius,
                paint
            )
            paint.style = Paint.Style.STROKE
            return
        }

        if (points.size == 2) {
            val speed = speedBetween(points[0], points[1], pageWidth, pageHeight)
            paint.strokeWidth = widthFor(
                stroke,
                points[0],
                points[1],
                speed,
                pageWidth,
                pageHeight,
            )
            canvas.drawLine(
                points[0].x * pageWidth,
                points[0].y * pageHeight,
                points[1].x * pageWidth,
                points[1].y * pageHeight,
                paint,
            )
            return
        }

        var fromX = points[0].x * pageWidth
        var fromY = points[0].y * pageHeight
        for (i in 1 until points.lastIndex) {
            val control = points[i]
            val next = points[i + 1]
            val endX = (control.x + next.x) * 0.5f * pageWidth
            val endY = (control.y + next.y) * 0.5f * pageHeight

            path.reset()
            path.moveTo(fromX, fromY)
            path.quadTo(
                control.x * pageWidth,
                control.y * pageHeight,
                endX,
                endY
            )
            paint.strokeWidth = widthFor(
                stroke,
                points[i - 1],
                control,
                speedBetween(points[i - 1], control, pageWidth, pageHeight),
                pageWidth,
                pageHeight,
            )
            canvas.drawPath(path, paint)
            fromX = endX
            fromY = endY
        }

        val last = points.last()
        val beforeLast = points[points.lastIndex - 1]
        paint.strokeWidth = widthFor(
            stroke,
            beforeLast,
            last,
            speedBetween(beforeLast, last, pageWidth, pageHeight),
            pageWidth,
            pageHeight,
        )
        canvas.drawLine(fromX, fromY, last.x * pageWidth, last.y * pageHeight, paint)
    }

    private fun speedBetween(
        a: InkPoint,
        b: InkPoint,
        pageWidth: Float,
        pageHeight: Float,
    ): Float {
        val dt = (b.timeMs - a.timeMs).coerceAtLeast(1L).toFloat()
        return hypot(
            (b.x - a.x) * pageWidth,
            (b.y - a.y) * pageHeight
        ) / dt
    }

    private fun widthFor(
        stroke: InkStroke,
        a: InkPoint,
        b: InkPoint,
        speedPxMs: Float,
        pageWidth: Float,
        pageHeight: Float,
    ): Float {
        val scale = minOf(pageWidth, pageHeight) / 850f
        if (stroke.highlighter) {
            return (stroke.baseWidthDp * scale).coerceAtLeast(3f)
        }

        val pressure = ((a.pressure + b.pressure) * 0.5f).coerceIn(0.03f, 1f)
        val pressureCurve = pressure.toDouble().pow(0.58).toFloat()
        val speedReference = (3.0f * scale).coerceAtLeast(0.1f)
        val velocityThin = (speedPxMs / speedReference).coerceIn(0f, 0.24f)
        val factor = (0.48f + 0.90f * pressureCurve) * (1f - velocityThin)
        return (stroke.baseWidthDp * scale * factor)
            .coerceIn(0.7f * scale, 4.2f * scale)
    }
}
