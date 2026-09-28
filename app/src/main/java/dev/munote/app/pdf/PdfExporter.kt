package dev.munote.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import dev.munote.app.R
import dev.munote.app.image.ImageStore
import dev.munote.app.image.PageImageNote
import dev.munote.app.ink.InkBrush
import dev.munote.app.ink.InkPoint
import dev.munote.app.ink.InkShape
import dev.munote.app.ink.InkStore
import dev.munote.app.ink.InkStroke
import dev.munote.app.text.TextBoxNote
import dev.munote.app.text.TextStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

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
        textStore: TextStore,
        imageStore: ImageStore,
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
                    ImagePdfRenderer.draw(
                        canvas = page.canvas,
                        store = imageStore,
                        images = imageStore.page(pageIndex),
                        pageWidth = bitmap.width.toFloat(),
                        pageHeight = bitmap.height.toFloat(),
                    )
                    InkPdfRenderer.draw(
                        canvas = page.canvas,
                        strokes = inkStore.page(pageIndex),
                        pageWidth = bitmap.width.toFloat(),
                        pageHeight = bitmap.height.toFloat(),
                    )
                    TextPdfRenderer.draw(
                        canvas = page.canvas,
                        boxes = textStore.page(pageIndex),
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
                requireNotNull(output) { context.getString(R.string.error_output_create) }
                document.writeTo(output)
            }
            session.pageCount
        } finally {
            document.close()
        }
    }
}

private object ImagePdfRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    suspend fun draw(
        canvas: Canvas,
        store: ImageStore,
        images: List<PageImageNote>,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        images.forEach { image ->
            val targetWidth = (image.width * pageWidth).toInt().coerceAtLeast(180)
            val bitmap = store.loadBitmap(image, targetWidth) ?: return@forEach
            try {
                val left = image.x * pageWidth
                val top = image.y * pageHeight
                val width = image.width * pageWidth
                val height = image.height * pageHeight
                val destination = RectF(left, top, left + width, top + height)

                canvas.save()
                canvas.rotate(
                    image.rotationDegrees,
                    destination.centerX(),
                    destination.centerY(),
                )
                canvas.drawBitmap(bitmap, null, destination, paint)
                canvas.restore()
            } finally {
                bitmap.recycle()
            }
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
        paint.alpha = when {
            stroke.highlighter -> 82
            stroke.brush == InkBrush.PENCIL -> 175
            else -> 255
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        if (stroke.shape != null && points.size >= 2) {
            drawShape(canvas, stroke, pageWidth, pageHeight)
            return
        }

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

    private fun drawShape(
        canvas: Canvas,
        stroke: InkStroke,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        val start = stroke.points.firstOrNull() ?: return
        val end = stroke.points.lastOrNull() ?: return
        val x1 = start.x * pageWidth
        val y1 = start.y * pageHeight
        val x2 = end.x * pageWidth
        val y2 = end.y * pageHeight
        val scale = minOf(pageWidth, pageHeight) / 850f
        paint.strokeWidth = (stroke.baseWidthDp * scale).coerceAtLeast(1f)

        when (stroke.shape) {
            InkShape.LINE -> canvas.drawLine(x1, y1, x2, y2, paint)

            InkShape.RECTANGLE -> canvas.drawRect(
                min(x1, x2),
                min(y1, y2),
                max(x1, x2),
                max(y1, y2),
                paint,
            )

            InkShape.ELLIPSE -> canvas.drawOval(
                RectF(
                    min(x1, x2),
                    min(y1, y2),
                    max(x1, x2),
                    max(y1, y2),
                ),
                paint,
            )

            InkShape.TRIANGLE -> {
                val left = min(x1, x2)
                val right = max(x1, x2)
                val top = min(y1, y2)
                val bottom = max(y1, y2)
                path.reset()
                path.moveTo((left + right) * 0.5f, top)
                path.lineTo(right, bottom)
                path.lineTo(left, bottom)
                path.close()
                canvas.drawPath(path, paint)
            }

            InkShape.ARROW -> {
                canvas.drawLine(x1, y1, x2, y2, paint)
                val dx = x2 - x1
                val dy = y2 - y1
                val length = hypot(dx, dy)
                if (length > 1f) {
                    val angle = atan2(dy.toDouble(), dx.toDouble())
                    val head = min(28f * scale, length * 0.32f)
                    val wingAngle = PI / 7.0
                    val leftX = x2 - cos(angle - wingAngle).toFloat() * head
                    val leftY = y2 - sin(angle - wingAngle).toFloat() * head
                    val rightX = x2 - cos(angle + wingAngle).toFloat() * head
                    val rightY = y2 - sin(angle + wingAngle).toFloat() * head
                    canvas.drawLine(x2, y2, leftX, leftY, paint)
                    canvas.drawLine(x2, y2, rightX, rightY, paint)
                }
            }

            null -> Unit
        }
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
        return when (stroke.brush) {
            InkBrush.BALLPOINT ->
                (stroke.baseWidthDp * scale).coerceAtLeast(0.65f * scale)

            InkBrush.PENCIL -> {
                val factor = 0.58f + pressure * 0.34f
                (stroke.baseWidthDp * scale * factor)
                    .coerceIn(0.55f * scale, 3.6f * scale)
            }

            InkBrush.FOUNTAIN -> {
                val pressureCurve = pressure.toDouble().pow(0.58).toFloat()
                val speedReference = (3.0f * scale).coerceAtLeast(0.1f)
                val velocityThin = (speedPxMs / speedReference).coerceIn(0f, 0.24f)
                val factor = (0.48f + 0.90f * pressureCurve) * (1f - velocityThin)
                (stroke.baseWidthDp * scale * factor)
                    .coerceIn(0.7f * scale, 4.2f * scale)
            }
        }
    }
}


private object TextPdfRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(28, 29, 31)
        style = Paint.Style.FILL
    }

    fun draw(
        canvas: Canvas,
        boxes: List<TextBoxNote>,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        if (boxes.isEmpty()) return
        val scale = minOf(pageWidth, pageHeight) / 850f

        boxes.forEach { box ->
            val text = box.text
            if (text.isBlank()) return@forEach

            paint.textSize = (box.fontSizeSp * scale).coerceAtLeast(8f)
            val startX = box.x * pageWidth
            val startY = box.y * pageHeight + paint.textSize
            val maxWidth = (box.width * pageWidth).coerceAtLeast(30f)
            val lineHeight = paint.textSize * 1.28f

            val lines = wrapText(text, maxWidth)
            lines.forEachIndexed { index, line ->
                val y = startY + index * lineHeight
                if (y <= pageHeight) {
                    canvas.drawText(line, startX, y, paint)
                }
            }
        }
    }

    private fun wrapText(text: String, maxWidth: Float): List<String> {
        val output = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            if (paragraph.isEmpty()) {
                output += ""
                return@forEach
            }
            val line = StringBuilder()
            paragraph.forEach { ch ->
                val candidate = line.toString() + ch
                if (line.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                    output += line.toString()
                    line.clear()
                }
                line.append(ch)
            }
            if (line.isNotEmpty()) output += line.toString()
        }
        return output
    }
}
