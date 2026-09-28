package dev.munote.app.ink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Tablet-first stylus layer.
 *
 * It consumes stylus events only so finger gestures remain available for paging,
 * preserves historical digitizer samples, applies light causal smoothing, and
 * mixes pressure + velocity into width.
 */
class InkCanvasView(context: Context) : View(context) {
    var tool: InkTool = InkTool.PEN
        set(value) { field = value; invalidate() }
    var inkColor: Int = Color.rgb(28, 29, 31)
    var onStrokeCommitted: ((InkStroke) -> Unit)? = null
    var onPageMutated: ((List<InkStroke>) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val committed = mutableListOf<InkStroke>()
    private val active = mutableListOf<InkPoint>()
    private var sx = 0f
    private var sy = 0f
    private var drawing = false

    fun setStrokes(strokes: List<InkStroke>) {
        committed.clear()
        committed.addAll(strokes)
        active.clear()
        invalidate()
    }

    fun snapshot(): List<InkStroke> = committed.toList()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointer = event.actionIndex.coerceAtLeast(0)
        val toolType = event.getToolType(pointer)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
            toolType == MotionEvent.TOOL_TYPE_ERASER
        if (!isStylus) return false

        if (toolType == MotionEvent.TOOL_TYPE_ERASER) tool = InkTool.ERASER

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                drawing = true
                active.clear()
                sx = event.x
                sy = event.y
                if (tool == InkTool.ERASER) eraseAt(event.x, event.y)
                else addPoint(event.x, event.y, event.pressure, event.eventTime, true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!drawing) return true
                for (h in 0 until event.historySize) {
                    val x = event.getHistoricalX(0, h)
                    val y = event.getHistoricalY(0, h)
                    val p = event.getHistoricalPressure(0, h)
                    val t = event.getHistoricalEventTime(h)
                    if (tool == InkTool.ERASER) eraseAt(x, y) else addPoint(x, y, p, t, false)
                }
                if (tool == InkTool.ERASER) eraseAt(event.x, event.y)
                else addPoint(event.x, event.y, event.pressure, event.eventTime, false)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (drawing && tool != InkTool.ERASER && active.isNotEmpty()) {
                    val stroke = InkStroke(
                        points = active.toList(),
                        colorArgb = inkColor,
                        baseWidthDp = if (tool == InkTool.HIGHLIGHTER) 12f else 2.2f,
                        highlighter = tool == InkTool.HIGHLIGHTER
                    )
                    committed.add(stroke)
                    onStrokeCommitted?.invoke(stroke)
                }
                active.clear()
                drawing = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return true
    }

    private fun addPoint(xRaw: Float, yRaw: Float, pressureRaw: Float, time: Long, first: Boolean) {
        if (width <= 0 || height <= 0) return
        if (first) {
            sx = xRaw
            sy = yRaw
        } else {
            val alpha = 0.58f
            sx += (xRaw - sx) * alpha
            sy += (yRaw - sy) * alpha
        }
        active += InkPoint(
            x = sx / width,
            y = sy / height,
            pressure = pressureRaw.coerceIn(0.03f, 1f),
            timeMs = time
        )
    }

    private fun eraseAt(x: Float, y: Float) {
        if (width <= 0 || height <= 0) return
        val nx = x / width
        val ny = y / height
        val radiusPx = 18f * density
        val rx = radiusPx / width
        val ry = radiusPx / height
        val before = committed.size
        committed.removeAll { s ->
            s.points.any { p ->
                val dx = (p.x - nx) / rx
                val dy = (p.y - ny) / ry
                dx * dx + dy * dy <= 1f
            }
        }
        if (before != committed.size) onPageMutated?.invoke(committed.toList())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        committed.forEach { drawStroke(canvas, it) }
        if (active.isNotEmpty() && tool != InkTool.ERASER) {
            drawStroke(
                canvas,
                InkStroke(
                    points = active,
                    colorArgb = inkColor,
                    baseWidthDp = if (tool == InkTool.HIGHLIGHTER) 12f else 2.2f,
                    highlighter = tool == InkTool.HIGHLIGHTER
                )
            )
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: InkStroke) {
        val pts = stroke.points
        if (pts.isEmpty()) return
        paint.color = stroke.colorArgb
        paint.alpha = if (stroke.highlighter) 82 else 255
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        if (pts.size == 1) {
            paint.style = Paint.Style.FILL
            val r = widthFor(stroke, pts[0], pts[0], 0f) / 2f
            canvas.drawCircle(pts[0].x * width, pts[0].y * height, r, paint)
            paint.style = Paint.Style.STROKE
            return
        }

        var prev = pts[0]
        for (i in 1 until pts.size) {
            val cur = pts[i]
            val x0 = prev.x * width
            val y0 = prev.y * height
            val x1 = cur.x * width
            val y1 = cur.y * height
            val dt = (cur.timeMs - prev.timeMs).coerceAtLeast(1L).toFloat()
            val speedPxMs = hypot(x1 - x0, y1 - y0) / dt
            paint.strokeWidth = widthFor(stroke, prev, cur, speedPxMs)
            canvas.drawLine(x0, y0, x1, y1, paint)
            prev = cur
        }
    }

    private fun widthFor(stroke: InkStroke, a: InkPoint, b: InkPoint, speedPxMs: Float): Float {
        if (stroke.highlighter) return stroke.baseWidthDp * density
        val pressure = ((a.pressure + b.pressure) * 0.5f).coerceIn(0.03f, 1f)
        val pressureCurve = pressure.toDouble().pow(0.62).toFloat()
        val velocityFactor = 1f - (speedPxMs / (2.8f * density)).coerceIn(0f, 0.28f)
        return stroke.baseWidthDp * density * (0.50f + 0.86f * pressureCurve) * velocityFactor
    }
}
