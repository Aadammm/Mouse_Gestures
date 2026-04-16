package com.mousegesture.services

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.mousegesture.models.GestureDirection
import com.mousegesture.ui.GestureOverlayComponent
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Point
import javax.swing.JFrame
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities

@Service(Service.Level.APP)
class GestureOrchestratorService {

    private val recognitionService = GestureRecognitionService()
    private var overlay: GestureOverlayComponent? = null
    private var activeLayeredPane: JLayeredPane? = null
    private var isRecordingMode = false
    private var recordingCallback: ((List<GestureDirection>) -> Unit)? = null
    private var isSettingsOpen = false

    private val managerService get() = ApplicationManager.getApplication().service<GestureManagerService>()
    private val mouseService get() = ApplicationManager.getApplication().service<MouseListenerService>()

    fun start() {
        mouseService.onGestureStarted = ::onGestureStarted
        mouseService.onGesturePointAdded = ::onGesturePointAdded
        mouseService.onGestureEnded = ::onGestureEnded
        mouseService.onRightClickDetected = ::onRightClickDetected
    }

    private fun onGestureStarted(screenPoint: Point) {
        if (!managerService.isPluginEnabled) {
            mouseService.resetState()
            return
        }
        if (isSettingsOpen && !isRecordingMode) return

        recognitionService.startGesture(screenPoint)
        showOverlay(screenPoint)
    }

    private fun onGesturePointAdded(screenPoint: Point) {
        if (!managerService.isPluginEnabled) return
        if (isSettingsOpen && !isRecordingMode) return

        recognitionService.addPoint(screenPoint)

        val localPoint = toLocalPoint(screenPoint)
        overlay?.addPoint(localPoint)

        if (managerService.visualizationSettings.showDirections || isRecordingMode) {
            val directions = recognitionService.recognizeDirections()
            overlay?.updateDirections(directions)
            val match = managerService.findMatchingGesture(directions)
            overlay?.updateMatchedCommand(match?.name)
        }
    }

    private fun onGestureEnded() {
        val pattern = recognitionService.endGesture()
        hideOverlay()

        if (isRecordingMode) {
            val callback = recordingCallback
            recordingCallback = null
            isRecordingMode = false
            mouseService.setRecordingMode(false)
            mouseService.resetState()
            callback?.invoke(pattern)
        } else if (!isSettingsOpen && pattern.isNotEmpty()) {
            managerService.findMatchingGesture(pattern)?.let { gesture ->
                executeAction(gesture.actionId)
            }
        }
    }

    private fun onRightClickDetected() {
        hideOverlay()
    }

    private fun showOverlay(screenPoint: Point) {
        val frame = getActiveFrame() ?: return
        val layeredPane = frame.rootPane.layeredPane

        val component = GestureOverlayComponent()
        component.setBounds(0, 0, layeredPane.width, layeredPane.height)

        val localPoint = screenToLocal(screenPoint, layeredPane)
        component.startGesture(localPoint, isRecordingMode, managerService.visualizationSettings)

        layeredPane.add(component, JLayeredPane.POPUP_LAYER as Any)
        layeredPane.repaint()

        overlay = component
        activeLayeredPane = layeredPane
    }

    private fun hideOverlay() {
        overlay?.endGesture()
        activeLayeredPane?.remove(overlay)
        activeLayeredPane?.repaint()
        overlay = null
        activeLayeredPane = null
    }

    private fun toLocalPoint(screenPoint: Point): Point {
        val pane = activeLayeredPane ?: return screenPoint
        return screenToLocal(screenPoint, pane)
    }

    private fun screenToLocal(screenPoint: Point, component: javax.swing.JComponent): Point {
        return try {
            val loc = component.locationOnScreen
            Point(screenPoint.x - loc.x, screenPoint.y - loc.y)
        } catch (e: Exception) {
            screenPoint
        }
    }

    private fun getActiveFrame(): JFrame? {
        var window: java.awt.Window? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
        while (window != null) {
            if (window is JFrame) return window
            window = window.owner
        }
        return Frame.getFrames().filterIsInstance<JFrame>().firstOrNull { it.isVisible }
    }

    private fun executeAction(actionId: String) {
        val action = ActionManager.getInstance().getAction(actionId) ?: return
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        SwingUtilities.invokeLater {
            try {
                ActionManager.getInstance().tryToExecute(
                    action, null, focusOwner, ActionPlaces.KEYBOARD_SHORTCUT, true
                )
            } catch (e: Exception) { }
        }
    }

    fun startGestureRecording(callback: (List<GestureDirection>) -> Unit) {
        isRecordingMode = true
        recordingCallback = callback
        mouseService.setRecordingMode(true)
    }

    fun stopGestureRecording() {
        isRecordingMode = false
        recordingCallback = null
        mouseService.setRecordingMode(false)
        mouseService.resetState()
        hideOverlay()
    }

    fun setSettingsOpen(open: Boolean) {
        isSettingsOpen = open
    }
}
