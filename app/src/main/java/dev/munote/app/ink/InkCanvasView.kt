package dev.munote.app.ink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

/**
 * Tablet-first stylus layer.
 *
 * Historical digitizer samples are preserved, finger gestures fall through to the PDF UI,
 * pressure is filtered separately from position, and smoothing becomes more responsive as speed
 * rises so fast Chinese handwriting does not visibly trail the pen.
 *
 * Lasso selection is intentionally kept in this native view as well: selected vector strokes can
 * be moved without rasterizing them or interfering with finger pan/zoom gestures.
 */
class InkCanvasView(context: Context) : View(context) {
    var tool: InkTool = InkTool.PEN
        set(value) {
            if (field != value && value != InkTool.LASSO) {
                clearSelection()
            }
            field = value
            invalidate()
        }

    var inkColor: Int = Color.rgb(28, 29, 31)
    var penWidthDp: Float = 2.15f
    var highlighterWidthDp: Float = 12f
    var fingerWritingEnabled: Boolean = false
    var shape: InkShape = InkShape.LINE
    var brush: InkBrush = InkBrush.FOUNTAIN
    var eraserMode: EraserMode = EraserMode.STROKE
    var eraserSizeDp: Float = 18f

    var onStrokeCommitted: ((InkStroke) -> Unit)? = null
    var onPageMutated: ((List<InkStroke>) -> Unit)? = null
    var onSelectionChanged: ((Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.5f * density
        color = Color.rgb(2, 132, 199)
        pathEffect = DashPathEffect(floatArrayOf(7f * density, 5f * density), 0f)
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
    private var activeInputWasFinger = false

    // Erasing is batched into one persisted edit per gesture. This makes undo useful and avoids
    // writing the ink JSON repeatedly while the eraser moves across a page.
    private var eraserBefore: List<InkStroke>? = null
    private var eraserDirty = false

    // Lasso state is not persisted; only a resulting move mutates the stored vector strokes.
    private val lassoPoints = mutableListOf<PointF>()
    private val selectedIndices = linkedSetOf<Int>()
    private var movingSelection = false
    private var moveStartX = 0f
    private var moveStartY = 0f
    private var moveOrigin = emptyMap<Int, InkStroke>()
    private var selectionDirty = false

    init {
        isClickable = false
        isFocusable = false
    }

    fun setStrokes(strokes: List<InkStroke>) {
        committed.clear()
        committed.addAll(strokes)
        active.clear()
        clearSelection()
        invalidate()
    }

    fun snapshot(): List<InkStroke> = committed.toList()

    fun hasSelection(): Boolean = selectedIndices.isNotEmpty()

    fun deleteSelection(): Boolean {
        if (selectedIndices.isEmpty()) return false
        selectedIndices.sortedDescending().forEach { index ->
            if (index in committed.indices) committed.removeAt(index)
        }
        clearSelection()
        onPageMutated?.invoke(committed.toList())
        invalidate()
        return true
    }

    fun duplicateSelection(
        offsetX: Float = 0.025f,
        offsetY: Float = 0.025f,
    ): Boolean {
        if (selectedIndices.isEmpty()) return false
        val copies = selectedIndices
            .filter { it in committed.indices }
            .map { committed[it] }
            .map { stroke ->
                stroke.copy(
                    points = stroke.points.map { point ->
                        point.copy(
                            x = (point.x + offsetX).coerceIn(0f, 1f),
                            y = (point.y + offsetY).coerceIn(0f, 1f),
                        )
                    }
                )
            }
        if (copies.isEmpty()) return false

        val firstNew = committed.size
        committed.addAll(copies)
        selectedIndices.clear()
        for (index in firstNew until committed.size) selectedIndices += index
        notifySelectionChanged()
        onPageMutated?.invoke(committed.toList())
        invalidate()
        return true
    }

    fun scaleSelection(factor: Float): Boolean {
        if (selectedIndices.isEmpty()) return false
        val bounds = selectionBounds() ?: return false
        val centerX = (bounds.left + bounds.right) * 0.5f
        val centerY = (bounds.top + bounds.bottom) * 0.5f
        val scale = factor.coerceIn(0.5f, 1.5f)

        selectedIndices.toList().forEach { index ->
            if (index !in committed.indices) return@forEach
            val stroke = committed[index]
            committed[index] = stroke.copy(
                points = stroke.points.map { point ->
                    point.copy(
                        x = (centerX + (point.x - centerX) * scale).coerceIn(0f, 1f),
                        y = (centerY + (point.y - centerY) * scale).coerceIn(0f, 1f),
                    )
                }
            )
        }
        onPageMutated?.invoke(committed.toList())
        invalidate()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (tool == InkTool.TEXT || tool == InkTool.IMAGE) return false

        // In touch-writing mode a single finger acts like the pen, but the moment a second finger
        // arrives the tentative stroke is cancelled so the parent can own pan/zoom gestures.
        if (activeInputWasFinger && event.pointerCount >= 2) {
            cancelActiveGesture()
            parent?.requestDisallowInterceptTouchEvent(false)
            return false
        }

        val pointer = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val toolType = event.getToolType(pointer)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
            toolType == MotionEvent.TOOL_TYPE_ERASER
        val isFinger = toolType == MotionEvent.TOOL_TYPE_FINGER
        val acceptsFinger = fingerWritingEnabled && isFinger && event.pointerCount == 1
        if (!isStylus && !acceptsFinger) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(event)
                parent?.requestDisallowInterceptTouchEvent(true)
                drawing = true
                activeInputWasFinger = acceptsFinger
                active.clear()
                gestureTool = if (toolType == MotionEvent.TOOL_TYPE_ERASER) InkTool.ERASER else tool

                when (gestureTool) {
                    InkTool.ERASER -> {
                        eraserBefore = committed.toList()
                        eraserDirty = false
                        eraseAt(event.x, event.y)
                    }

                    InkTool.LASSO -> {
                        eraserBefore = null
                        startLassoGesture(event.x, event.y)
                    }

                    InkTool.SHAPE, InkTool.RULER -> {
                        eraserBefore = null
                        startShapeGesture(event.x, event.y, event.eventTime)
                    }

                    else -> {
                        eraserBefore = null
                        addPoint(event.x, event.y, event.pressure, event.eventTime, first = true)
                    }
                }
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
                    when (gestureTool) {
                        InkTool.ERASER -> eraseAt(x, y)
                        InkTool.LASSO -> moveLassoGesture(x, y)
                        InkTool.SHAPE, InkTool.RULER -> updateShapeGesture(x, y, t)
                        else -> addPoint(x, y, p, t, first = false)
                    }
                }

                when (gestureTool) {
                    InkTool.ERASER -> eraseAt(event.x, event.y)
                    InkTool.LASSO -> moveLassoGesture(event.x, event.y)
                    InkTool.SHAPE, InkTool.RULER ->
                        updateShapeGesture(event.x, event.y, event.eventTime)
                    else -> addPoint(
                        event.x,
                        event.y,
                        event.pressure,
                        event.eventTime,
                        first = false
                    )
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val canceled = event.flags and MotionEvent.FLAG_CANCELED != 0
                if (!canceled && drawing) {
                    when (gestureTool) {
                        InkTool.ERASER -> {
                            if (eraserDirty) onPageMutated?.invoke(committed.toList())
                        }

                        InkTool.LASSO -> finishLassoGesture(event.x, event.y)

                        InkTool.SHAPE, InkTool.RULER -> {
                            updateShapeGesture(event.x, event.y, event.eventTime)
                            if (active.size >= 2) {
                                val stroke = InkStroke(
                                    points = listOf(active.first(), active.last()),
                                    colorArgb = inkColor,
                                    baseWidthDp = penWidthDp,
                                    highlighter = false,
                                    shape = if (gestureTool == InkTool.RULER) {
                                        InkShape.LINE
                                    } else {
                                        shape
                                    },
                                    brush = brush,
                                )
                                committed.add(stroke)
                                onStrokeCommitted?.invoke(stroke)
                            }
                        }

                        else -> {
                            if (active.isNotEmpty()) {
                                val stroke = InkStroke(
                                    points = active.toList(),
                                    colorArgb = inkColor,
                                    baseWidthDp = currentBaseWidth(),
                                    highlighter = gestureTool == InkTool.HIGHLIGHTER,
                                    brush = brush,
                                )
                                committed.add(stroke)
                                onStrokeCommitted?.invoke(stroke)
                            }
                        }
                    }
                }
                finishGesture()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelActiveGesture()
                return true
            }
        }
        return true
    }

    private fun cancelActiveGesture() {
        when (gestureTool) {
            InkTool.ERASER -> {
                eraserBefore?.let { before ->
                    committed.clear()
                    committed.addAll(before)
                }
            }

            InkTool.LASSO -> {
                if (movingSelection && moveOrigin.isNotEmpty()) {
                    restoreMoveOrigin()
                }
                lassoPoints.clear()
            }

            else -> Unit
        }
        finishGesture()
    }

    private fun finishGesture() {
        active.clear()
        lassoPoints.clear()
        drawing = false
        activeInputWasFinger = false
        eraserBefore = null
        eraserDirty = false
        movingSelection = false
        moveOrigin = emptyMap()
        selectionDirty = false
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    private fun currentBaseWidth(): Float =
        if (gestureTool == InkTool.HIGHLIGHTER) highlighterWidthDp else penWidthDp

    private fun startShapeGesture(xRaw: Float, yRaw: Float, time: Long) {
        if (width <= 0 || height <= 0) return
        active.clear()
        val start = InkPoint(
            x = (xRaw / width).coerceIn(0f, 1f),
            y = (yRaw / height).coerceIn(0f, 1f),
            pressure = 1f,
            timeMs = time,
        )
        active += start
        active += start
    }

    private fun updateShapeGesture(xRaw: Float, yRaw: Float, time: Long) {
        if (active.isEmpty() || width <= 0 || height <= 0) return
        val start = active.first()
        val end = if (gestureTool == InkTool.RULER) {
            snapRulerEndpoint(start, xRaw, yRaw, time)
        } else {
            InkPoint(
                x = (xRaw / width).coerceIn(0f, 1f),
                y = (yRaw / height).coerceIn(0f, 1f),
                pressure = 1f,
                timeMs = time,
            )
        }
        if (active.size == 1) active += end else active[active.lastIndex] = end
    }

    private fun snapRulerEndpoint(
        start: InkPoint,
        xRaw: Float,
        yRaw: Float,
        time: Long,
    ): InkPoint {
        val startX = start.x * width
        val startY = start.y * height
        val dx = xRaw - startX
        val dy = yRaw - startY
        val length = hypot(dx, dy)
        if (length < 0.5f) {
            return start.copy(timeMs = time)
        }

        val step = PI / 12.0
        val angle = atan2(dy.toDouble(), dx.toDouble())
        val snapped = round(angle / step) * step
        val endX = startX + cos(snapped).toFloat() * length
        val endY = startY + sin(snapped).toFloat() * length
        return InkPoint(
            x = (endX / width).coerceIn(0f, 1f),
            y = (endY / height).coerceIn(0f, 1f),
            pressure = 1f,
            timeMs = time,
        )
    }

    private fun addPoint(
        xRaw: Float,
        yRaw: Float,
        pressureRaw: Float,
        time: Long,
        first: Boolean,
    ) {
        if (width <= 0 || height <= 0) return

        val inputPressure = if (activeInputWasFinger) {
            // Finger MotionEvent pressure is highly device-specific and is often pinned near 1.0.
            // Use a stable virtual pressure so touch-writing does not become an extra-thick marker.
            0.52f
        } else {
            pressureRaw.coerceIn(0.03f, 1f)
        }

        if (first) {
            sx = xRaw
            sy = yRaw
            rawX = xRaw
            rawY = yRaw
            smoothPressure = inputPressure
            smoothSpeedPxMs = 0f
            lastInputTime = time
        } else {
            val dt = (time - lastInputTime).coerceAtLeast(1L).toFloat()
            val rawSpeed = hypot(xRaw - rawX, yRaw - rawY) / dt
            smoothSpeedPxMs += (rawSpeed - smoothSpeedPxMs) * 0.32f

            // Slow movement gets stronger stabilization; fast strokes get lower latency.
            val speedRatio = (smoothSpeedPxMs / (1.9f * density)).coerceIn(0f, 1f)
            val positionAlpha = if (activeInputWasFinger) {
                // A fingertip is wider/noisier than a stylus tip, so apply a little more
                // stabilization at low speed while still letting fast strokes catch up.
                0.34f + speedRatio * 0.32f
            } else {
                0.42f + speedRatio * 0.36f
            }
            sx += (xRaw - sx) * positionAlpha
            sy += (yRaw - sy) * positionAlpha

            smoothPressure += (inputPressure - smoothPressure) * 0.38f

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

        val last = active.lastOrNull()
        if (last == null || abs(last.x - point.x) > 0.00002f ||
            abs(last.y - point.y) > 0.00002f ||
            abs(last.pressure - point.pressure) > 0.004f
        ) {
            active += point
        }
    }

    private fun eraseAt(x: Float, y: Float) {
        if (width <= 0 || height <= 0) return
        val nx = x / width
        val ny = y / height
        val radiusPx = eraserSizeDp.coerceIn(6f, 64f) * density
        val rx = radiusPx / width
        val ry = radiusPx / height

        fun touched(point: InkPoint): Boolean {
            val dx = (point.x - nx) / rx
            val dy = (point.y - ny) / ry
            return dx * dx + dy * dy <= 1f
        }

        when (eraserMode) {
            EraserMode.STROKE -> {
                val before = committed.size
                committed.removeAll { stroke ->
                    val probes = if (stroke.shape == null) stroke.points else shapeProbePoints(stroke)
                    probes.any(::touched)
                }
                if (before != committed.size) {
                    eraserDirty = true
                    clearSelection()
                }
            }

            EraserMode.PIXEL -> {
                val next = mutableListOf<InkStroke>()
                var changed = false
                committed.forEach { stroke ->
                    if (stroke.shape != null) {
                        val hit = shapeProbePoints(stroke).any(::touched)
                        if (hit) changed = true else next += stroke
                        return@forEach
                    }

                    var chunk = mutableListOf<InkPoint>()
                    fun flushChunk() {
                        if (chunk.isNotEmpty()) {
                            next += stroke.copy(points = chunk.toList())
                            chunk = mutableListOf()
                        }
                    }

                    stroke.points.forEach { point ->
                        if (touched(point)) {
                            changed = true
                            flushChunk()
                        } else {
                            chunk += point
                        }
                    }
                    flushChunk()
                }

                if (changed) {
                    committed.clear()
                    committed.addAll(next)
                    eraserDirty = true
                    clearSelection()
                }
            }
        }
    }

    private fun shapeProbePoints(stroke: InkStroke): List<InkPoint> {
        val start = stroke.points.firstOrNull() ?: return emptyList()
        val end = stroke.points.lastOrNull() ?: return listOf(start)
        val now = end.timeMs
        fun point(x: Float, y: Float) = InkPoint(x, y, 1f, now)

        return when (stroke.shape) {
            InkShape.LINE -> List(17) { i ->
                val t = i / 16f
                point(
                    start.x + (end.x - start.x) * t,
                    start.y + (end.y - start.y) * t,
                )
            }

            InkShape.RECTANGLE -> {
                val left = min(start.x, end.x)
                val right = max(start.x, end.x)
                val top = min(start.y, end.y)
                val bottom = max(start.y, end.y)
                buildList {
                    repeat(9) { i ->
                        val t = i / 8f
                        add(point(left + (right - left) * t, top))
                        add(point(left + (right - left) * t, bottom))
                        add(point(left, top + (bottom - top) * t))
                        add(point(right, top + (bottom - top) * t))
                    }
                }
            }

            InkShape.ELLIPSE -> {
                val cx = (start.x + end.x) * 0.5f
                val cy = (start.y + end.y) * 0.5f
                val rxShape = abs(end.x - start.x) * 0.5f
                val ryShape = abs(end.y - start.y) * 0.5f
                List(32) { i ->
                    val angle = 2.0 * PI * i / 32.0
                    point(
                        cx + cos(angle).toFloat() * rxShape,
                        cy + sin(angle).toFloat() * ryShape,
                    )
                }
            }

            InkShape.TRIANGLE -> {
                val left = min(start.x, end.x)
                val right = max(start.x, end.x)
                val top = min(start.y, end.y)
                val bottom = max(start.y, end.y)
                val a = point((left + right) * 0.5f, top)
                val b = point(right, bottom)
                val c = point(left, bottom)
                sampleSegments(listOf(a, b, c, a), 8)
            }

            InkShape.ARROW -> {
                val base = sampleSegments(listOf(start, end), 16).toMutableList()
                val dx = end.x - start.x
                val dy = end.y - start.y
                val len = hypot(dx, dy).coerceAtLeast(0.0001f)
                val ux = dx / len
                val uy = dy / len
                val head = min(0.08f, len * 0.28f)
                val wing = head * 0.55f
                val left = point(
                    end.x - ux * head - uy * wing,
                    end.y - uy * head + ux * wing,
                )
                val right = point(
                    end.x - ux * head + uy * wing,
                    end.y - uy * head - ux * wing,
                )
                base += sampleSegments(listOf(left, end, right), 8)
                base
            }

            null -> stroke.points
        }
    }

    private fun sampleSegments(points: List<InkPoint>, steps: Int): List<InkPoint> {
        if (points.size < 2) return points
        return buildList {
            for (i in 0 until points.lastIndex) {
                val a = points[i]
                val b = points[i + 1]
                repeat(steps + 1) { stepIndex ->
                    val t = stepIndex / steps.toFloat()
                    add(
                        InkPoint(
                            x = a.x + (b.x - a.x) * t,
                            y = a.y + (b.y - a.y) * t,
                            pressure = 1f,
                            timeMs = b.timeMs,
                        )
                    )
                }
            }
        }
    }

    private fun startLassoGesture(xRaw: Float, yRaw: Float) {
        if (width <= 0 || height <= 0) return
        val nx = (xRaw / width).coerceIn(0f, 1f)
        val ny = (yRaw / height).coerceIn(0f, 1f)

        val bounds = selectionBounds()
        if (selectedIndices.isNotEmpty() && bounds != null && bounds.contains(nx, ny)) {
            movingSelection = true
            moveStartX = nx
            moveStartY = ny
            moveOrigin = selectedIndices
                .filter { it in committed.indices }
                .associateWith { committed[it] }
            selectionDirty = false
        } else {
            movingSelection = false
            selectedIndices.clear()
            lassoPoints.clear()
            lassoPoints += PointF(nx, ny)
        }
    }

    private fun moveLassoGesture(xRaw: Float, yRaw: Float) {
        if (width <= 0 || height <= 0) return
        val nx = (xRaw / width).coerceIn(0f, 1f)
        val ny = (yRaw / height).coerceIn(0f, 1f)

        if (movingSelection) {
            moveSelectionTo(nx, ny)
            return
        }

        val last = lassoPoints.lastOrNull()
        if (last == null || hypot(nx - last.x, ny - last.y) > 0.0025f) {
            lassoPoints += PointF(nx, ny)
        }
    }

    private fun finishLassoGesture(xRaw: Float, yRaw: Float) {
        if (movingSelection) {
            moveLassoGesture(xRaw, yRaw)
            if (selectionDirty) onPageMutated?.invoke(committed.toList())
            return
        }

        moveLassoGesture(xRaw, yRaw)
        selectStrokesInsideLasso()
    }

    private fun selectStrokesInsideLasso() {
        selectedIndices.clear()
        if (lassoPoints.size < 3) return

        committed.forEachIndexed { index, stroke ->
            if (stroke.points.any { pointInPolygon(it.x, it.y, lassoPoints) }) {
                selectedIndices += index
            } else {
                val bounds = strokeBounds(stroke)
                if (bounds != null) {
                    val centerX = (bounds.left + bounds.right) * 0.5f
                    val centerY = (bounds.top + bounds.bottom) * 0.5f
                    if (pointInPolygon(centerX, centerY, lassoPoints)) {
                        selectedIndices += index
                    }
                }
            }
        }
        notifySelectionChanged()
    }

    private fun moveSelectionTo(nx: Float, ny: Float) {
        if (moveOrigin.isEmpty()) return

        val originBounds = boundsOfStrokes(moveOrigin.values.toList()) ?: return
        var dx = nx - moveStartX
        var dy = ny - moveStartY
        dx = dx.coerceIn(-originBounds.left, 1f - originBounds.right)
        dy = dy.coerceIn(-originBounds.top, 1f - originBounds.bottom)

        moveOrigin.forEach { (index, stroke) ->
            if (index !in committed.indices) return@forEach
            committed[index] = stroke.copy(
                points = stroke.points.map { point ->
                    point.copy(
                        x = (point.x + dx).coerceIn(0f, 1f),
                        y = (point.y + dy).coerceIn(0f, 1f),
                    )
                }
            )
        }
        selectionDirty = abs(dx) > 0.0002f || abs(dy) > 0.0002f
    }

    private fun restoreMoveOrigin() {
        moveOrigin.forEach { (index, stroke) ->
            if (index in committed.indices) committed[index] = stroke
        }
    }

    private fun clearSelection() {
        val hadSelection = selectedIndices.isNotEmpty()
        selectedIndices.clear()
        lassoPoints.clear()
        movingSelection = false
        moveOrigin = emptyMap()
        selectionDirty = false
        if (hadSelection) notifySelectionChanged()
    }

    private fun notifySelectionChanged() {
        onSelectionChanged?.invoke(selectedIndices.isNotEmpty())
    }

    private fun selectionBounds(): RectF? {
        val selected = selectedIndices
            .filter { it in committed.indices }
            .map { committed[it] }
        val bounds = boundsOfStrokes(selected) ?: return null
        val pad = 0.012f
        return RectF(
            (bounds.left - pad).coerceAtLeast(0f),
            (bounds.top - pad).coerceAtLeast(0f),
            (bounds.right + pad).coerceAtMost(1f),
            (bounds.bottom + pad).coerceAtMost(1f),
        )
    }

    private fun boundsOfStrokes(strokes: List<InkStroke>): RectF? {
        if (strokes.isEmpty()) return null
        var left = 1f
        var top = 1f
        var right = 0f
        var bottom = 0f
        var found = false

        strokes.forEach { stroke ->
            stroke.points.forEach { point ->
                found = true
                left = min(left, point.x)
                top = min(top, point.y)
                right = max(right, point.x)
                bottom = max(bottom, point.y)
            }
        }
        return if (found) RectF(left, top, right, bottom) else null
    }

    private fun strokeBounds(stroke: InkStroke): RectF? =
        boundsOfStrokes(listOf(stroke))

    private fun pointInPolygon(x: Float, y: Float, polygon: List<PointF>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y

            val crosses = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { abs(it) > 1e-6f }
                    ?: 1e-6f) + xi)
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        committed.forEach { drawStroke(canvas, it) }

        if (active.isNotEmpty() &&
            gestureTool != InkTool.ERASER &&
            gestureTool != InkTool.LASSO
        ) {
            drawStroke(
                canvas,
                InkStroke(
                    points = active,
                    colorArgb = inkColor,
                    baseWidthDp = currentBaseWidth(),
                    highlighter = gestureTool == InkTool.HIGHLIGHTER,
                    shape = when (gestureTool) {
                        InkTool.RULER -> InkShape.LINE
                        InkTool.SHAPE -> shape
                        else -> null
                    },
                    brush = brush,
                )
            )
        }

        if (tool == InkTool.LASSO || gestureTool == InkTool.LASSO) {
            drawLassoOverlay(canvas)
        }
    }

    private fun drawLassoOverlay(canvas: Canvas) {
        if (lassoPoints.size >= 2 && !movingSelection) {
            path.reset()
            path.moveTo(lassoPoints[0].x * width, lassoPoints[0].y * height)
            for (i in 1 until lassoPoints.size) {
                path.lineTo(lassoPoints[i].x * width, lassoPoints[i].y * height)
            }
            canvas.drawPath(path, overlayPaint)
        }

        val bounds = selectionBounds() ?: return
        canvas.drawRect(
            bounds.left * width,
            bounds.top * height,
            bounds.right * width,
            bounds.bottom * height,
            overlayPaint
        )
    }

    private fun drawStroke(canvas: Canvas, stroke: InkStroke) {
        val pts = stroke.points
        if (pts.isEmpty()) return

        paint.color = stroke.colorArgb
        paint.alpha = when {
            stroke.highlighter -> 82
            stroke.brush == InkBrush.PENCIL -> 175
            else -> 255
        }
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE
        paint.pathEffect = null

        if (stroke.shape != null && pts.size >= 2) {
            drawShape(canvas, stroke)
            return
        }

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

    private fun drawShape(canvas: Canvas, stroke: InkStroke) {
        val start = stroke.points.firstOrNull() ?: return
        val end = stroke.points.lastOrNull() ?: return
        val x1 = start.x * width
        val y1 = start.y * height
        val x2 = end.x * width
        val y2 = end.y * height
        paint.strokeWidth = (stroke.baseWidthDp * density).coerceAtLeast(1f)

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
                    val head = min(28f * density, length * 0.32f)
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
        return when (stroke.brush) {
            InkBrush.BALLPOINT ->
                (stroke.baseWidthDp * density).coerceAtLeast(0.65f * density)

            InkBrush.PENCIL -> {
                val factor = 0.58f + pressure * 0.34f
                (stroke.baseWidthDp * density * factor)
                    .coerceIn(0.55f * density, 3.6f * density)
            }

            InkBrush.FOUNTAIN -> {
                val pressureCurve = pressure.toDouble().pow(0.58).toFloat()
                val velocityThin = (speedPxMs / (3.0f * density)).coerceIn(0f, 0.24f)
                val factor = (0.48f + 0.90f * pressureCurve) * (1f - velocityThin)
                (stroke.baseWidthDp * density * factor)
                    .coerceIn(0.72f * density, 4.2f * density)
            }
        }
    }
}
