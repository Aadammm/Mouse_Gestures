package com.mousegesture.services

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import kotlin.math.sqrt

@Service(Service.Level.APP)
class MouseListenerService : Disposable {

    var onGestureStarted: ((Point) -> Unit)? = null
    var onGesturePointAdded: ((Point) -> Unit)? = null
    var onGestureEnded: (() -> Unit)? = null
    var onRightClickDetected: (() -> Unit)? = null

    private var isRightButtonDown = false
    private var isGestureActive = false
    private var gestureStartPoint: Point? = null
    private var isRecordingMode = false
    private var shouldIgnoreNextRightClick = false
    private var isRightMouseDown = false
    private val gestureStartThreshold = 5.0

    private val dispatcher = IdeEventQueue.EventDispatcher { event ->
        if (event is MouseEvent) handleMouseEvent(event) else false
    }

    init {
        IdeEventQueue.getInstance().addDispatcher(dispatcher, this)
    }

    fun setRecordingMode(recording: Boolean) {
        isRecordingMode = recording
        if (!recording) {
            isRightMouseDown = false
        }
    }

    fun resetState() {
        isRightMouseDown = false
        shouldIgnoreNextRightClick = true
    }

    private fun handleMouseEvent(event: MouseEvent): Boolean {
        return when {
            event.id == MouseEvent.MOUSE_PRESSED && SwingUtilities.isRightMouseButton(event) -> {
                handleRightButtonDown(Point(event.xOnScreen, event.yOnScreen))
                false
            }

            event.id == MouseEvent.MOUSE_RELEASED && SwingUtilities.isRightMouseButton(event) -> {
                val wasGestureActive = isGestureActive
                val wasRecordingMode = isRecordingMode
                handleRightButtonUp()
                wasGestureActive && !wasRecordingMode
            }

            event.id == MouseEvent.MOUSE_DRAGGED && isRightButtonDown -> {
                handleMouseMove(Point(event.xOnScreen, event.yOnScreen))
                false
            }

            else -> false
        }
    }

    private fun handleRightButtonDown(point: Point) {
        isRightButtonDown = true
        gestureStartPoint = point
        isGestureActive = false
    }

    private fun handleMouseMove(point: Point) {
        val start = gestureStartPoint ?: return

        if (!isGestureActive) {
            if (distance(start, point) >= gestureStartThreshold) {
                isGestureActive = true
                onGestureStarted?.invoke(start)
            }
        } else {
            onGesturePointAdded?.invoke(point)
        }
    }

    private fun handleRightButtonUp() {
        if (isGestureActive) {
            onGestureEnded?.invoke()
        } else if (isRightButtonDown) {
            onRightClickDetected?.invoke()
        }

        isRightButtonDown = false
        isGestureActive = false
        gestureStartPoint = null
    }

    private fun distance(p1: Point, p2: Point): Double {
        val dx = (p2.x - p1.x).toDouble()
        val dy = (p2.y - p1.y).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    override fun dispose() {
    }
}
