package dev.munote.app.ink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Tablet-first stylus layer.
 *
 * The live path deliberately stays native Android View/Canvas instead of Compose pointer input:
 * historical digitizer samples are preserved, finger gestures fall through to the PDF pager,
 * pressure is filtered separately from position, and the coordinate filter becomes more responsive
 * as speed rises so fast Chinese handwriting does not trail the pen.
 */
class InkCanvasView(context: Context) : View(context) {
    var tool: InkTool = InkTool.PEN
        set(value) { field = value; invalidate() }
    var inkColor: Int = Color.rgb(28, 29, 31)
    var onStrokeCommitted: ((InkStroke) -> Unit)? = null
    var onPageMutated: ((List<InkStroke>) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val committed = mutableListOf<InkStroke>()
    private val active = mutableListOf<InkPoint>()

    private var sx = 0f
    private var sy = 0f
    private var rawX = 0f
    private var rawY = 0f
    private var smoothPressure = 0.5f
    private var smoothSpeedPxMs = 0f
    private var lastInputTime = 0L
    private var drawing = false
    private var gestureTool = InkTool.PEN

    init {
        isClickable = false
        isFocusable = false
    }

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

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(event)
                parent?.requestDisallowInterceptTouchEvent(true)
                drawing = true
                active.clear()
                gestureTool = if (toolType == MotionEvent.TOOL_TYPE_ERASER) InkTool.ERASER else tool

                if (gestureTool == InkTool.ERASER) {
                    eraseAt(event.x, event.y)
                } else {
                    addPoint(event.x, event.y, event.pressure, event.eventTime, first = true)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!drawing) return true

                // Historical samples are the difference between a 60/120 Hz event stream and the
                // substantially higher-rate digitizer data many Android tablets actually provide.
                for (h in 0 until event.historySize) {
                    val x = event.getHistoricalX(0, h)
                    val y = event.getHistoricalY(0, h)
                    val p = event.getHistoricalPressure(0, h)
                    val t = event.getHistoricalEventTime(h)
                    if (gestureTool == InkTool.ERASER) eraseAt(x, y)
                    else addPoint(x, y, p, t, first = false)
                }

                if (gestureTool == InkTool.ERASER) eraseAt(event.x, event.y)
                else addPoint(event.x, event.y, event.pressure, event.eventTime, first = false)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val canceled = event.flags and MotionEvent.FLAG_CANCELED != 0
                if (!canceled && drawing && gestureTool != InkTool.ERASER && active.isNotEmpty()) {
                    val stroke = InkStroke(
                        points = active.toList(),
                        colorArgb = inkColor,
                        baseWidthDp = if (gestureTool == InkTool.HIGHLIGHTER) 12f else 2.15f,
                        highlighter = gestureTool == InkTool.HIGHLIGHTER,
                    )
                    committed.add(stroke)
                    onStrokeCommitted?.invoke(stroke)
                }
                finishGesture()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                // Palm rejection and parent gesture arbitration can end a stream with CANCEL.
                // Never persist such a stroke.
                finishGesture()
                return true
            }
        }
        return true
    }

    private fun finishGesture() {
        active.clear()
        drawing = false
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    private fun addPoint(
        xRaw: Float,
        yRaw: Float,
        pressureRaw: Float,
        time: Long,
        first: Boolean,
    ) {
        if (width <= 0 || height <= 0) return

        if (first) {
            sx = xRaw
            sy = yRaw
            rawX = xRaw
            rawY = yRaw
            smoothPressure = pressureRaw.coerceIn(0.03f, 1f)
            smoothSpeedPxMs = 0f
            lastInputTime = time
        } else {
            val dt = (time - lastInputTime).coerceAtLeast(1L).toFloat()
            val rawSpeed = hypot(xRaw - rawX, yRaw - rawY) / dt
            smoothSpeedPxMs += (rawSpeed - smoothSpeedPxMs) * 0.32f

            // Slow movement gets stronger stabilization; fast strokes get lower latency.
            val speedRatio = (smoothSpeedPxMs / (1.9f * density)).coerceIn(0f, 1f)
            val positionAlpha = 0.42f + speedRatio * 0.36f
            sx += (xRaw - sx) * positionAlpha
            sy += (yRaw - sy) * positionAlpha

            val p = pressureRaw.coerceIn(0.03f, 1f)
            smoothPressure += (p - smoothPressure) * 0.38f

            rawX = xRaw
            rawY = yRaw
            lastInputTime = time
        }

        val point = InkPoint(
            x = sx / width,
            y = sy / height,
            pressure = smoothPressure,
            timeMs = time,
        )

        // Avoid huge duplicate runs when the digitizer repeats a stationary sample.
        val last = active.lastOrNull()
        if (last == null || kotlin.math.abs(last.x - point.x) > 0.00002f ||
            kotlin.math.abs(last.y - point.y) > 0.00002f ||
            kotlin.math.abs(last.pressure - point.pressure) > 0.004f
        ) {
            active += point
        }
    }

    private fun eraseAt(x: Float, y: Float) {
        if (width <= 0 || height <= 0) return
        val nx = x / width
        val ny = y / height
        val radiusPx = 18f * density
        val rx = radiusPx / width
        val ry = radiusPx / height
        val before = committed.size
        committed.removeAll { stroke ->
            stroke.points.any { point ->
                val dx = (point.x - nx) / rx
                val dy = (point.y - ny) / ry
                dx * dx + dy * dy <= 1f
            }
        }
        if (before != committed.size) onPageMutated?.invoke(committed.toList())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        committed.forEach { drawStroke(canvas, it) }
        if (active.isNotEmpty() && gestureTool != InkTool.ERASER) {
            drawStroke(
                canvas,
                InkStroke(
                    points = active,
                    colorArgb = inkColor,
                    baseWidthDp = if (gestureTool == InkTool.HIGHLIGHTER) 12f else 2.15f,
                    highlighter = gestureTool == InkTool.HIGHLIGHTER,
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
        paint.style = Paint.Style.STROKE

        if (pts.size == 1) {
            paint.style = Paint.Style.FILL
            val r = widthFor(stroke, pts[0], pts[0], 0f) / 2f
            canvas.drawCircle(pts[0].x * width, pts[0].y * height, r, paint)
            paint.style = Paint.Style.STROKE
            return
        }

        if (pts.size == 2) {
            val speed = speedBetween(pts[0], pts[1])
            paint.strokeWidth = widthFor(stroke, pts[0], pts[1], speed)
            canvas.drawLine(
                pts[0].x * width,
                pts[0].y * height,
                pts[1].x * width,
                pts[1].y * height,
                paint,
            )
            return
        }

        // Quadratic midpoint smoothing keeps the live stroke visually continuous while still
        // allowing width to respond sample-by-sample to pressure and speed.
        var fromX = pts[0].x * width
        var fromY = pts[0].y * height
        for (i in 1 until pts.lastIndex) {
            val control = pts[i]
            val next = pts[i + 1]
            val endX = (control.x + next.x) * 0.5f * width
            val endY = (control.y + next.y) * 0.5f * height

            path.reset()
            path.moveTo(fromX, fromY)
            path.quadTo(control.x * width, control.y * height, endX, endY)
            paint.strokeWidth = widthFor(
                stroke,
                pts[i - 1],
                control,
                speedBetween(pts[i - 1], control),
            )
            canvas.drawPath(path, paint)
            fromX = endX
            fromY = endY
        }

        val last = pts.last()
        val beforeLast = pts[pts.lastIndex - 1]
        paint.strokeWidth = widthFor(stroke, beforeLast, last, speedBetween(beforeLast, last))
        canvas.drawLine(fromX, fromY, last.x * width, last.y * height, paint)
    }

    private fun speedBetween(a: InkPoint, b: InkPoint): Float {
        val dt = (b.timeMs - a.timeMs).coerceAtLeast(1L).toFloat()
        return hypot((b.x - a.x) * width, (b.y - a.y) * height) / dt
    }

    private fun widthFor(
        stroke: InkStroke,
        a: InkPoint,
        b: InkPoint,
        speedPxMs: Float,
    ): Float {
        if (stroke.highlighter) return stroke.baseWidthDp * density

        val pressure = ((a.pressure + b.pressure) * 0.5f).coerceIn(0.03f, 1f)
        val pressureCurve = pressure.toDouble().pow(0.58).toFloat()
        val velocityThin = (speedPxMs / (3.0f * density)).coerceIn(0f, 0.24f)
        val factor = (0.48f + 0.90f * pressureCurve) * (1f - velocityThin)
        return (stroke.baseWidthDp * density * factor)
            .coerceIn(0.72f * density, 3.2f * density)
    }
}
