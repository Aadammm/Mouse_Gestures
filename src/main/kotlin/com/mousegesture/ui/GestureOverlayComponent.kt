package com.mousegesture.ui

import com.mousegesture.models.GestureDirection
import com.mousegesture.models.GestureVisualizationSettings
import java.awt.*
import javax.swing.JComponent

@Suppress("UseJBColor")
class GestureOverlayComponent : JComponent() {

    private val points = mutableListOf<Point>()
    private val directions = mutableListOf<GestureDirection>()
    private var matchedCommand: String? = null
    private var settings = GestureVisualizationSettings()
    private var isRecordingMode = false

    init {
        isOpaque = false
        isVisible = false
    }

    override fun contains(x: Int, y: Int) = false

    fun startGesture(point: Point, recordingMode: Boolean, visualSettings: GestureVisualizationSettings) {
        isRecordingMode = recordingMode
        settings = visualSettings
        points.clear()
        directions.clear()
        matchedCommand = null
        points.add(point)
        isVisible = true
        repaint()
    }

    fun addPoint(point: Point) {
        points.add(point); repaint()
    }

    fun updateDirections(newDirections: List<GestureDirection>) {
        directions.clear(); directions.addAll(newDirections); repaint()
    }

    fun updateMatchedCommand(cmd: String?) {
        matchedCommand = cmd; repaint()
    }

    fun endGesture() {
        isVisible = false; points.clear(); directions.clear(); matchedCommand = null; repaint()
    }

    override fun paintComponent(g: Graphics) {
        if (points.size < 2) return
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        if (settings.showTrail || isRecordingMode) drawTrail(g2)
        if ((settings.showDirections || isRecordingMode) && directions.isNotEmpty()) drawDirectionIndicator(g2)
    }

    private fun drawTrail(g2: Graphics2D) {
        val color = settings.getTrailColor()
        val thickness = if (isRecordingMode) settings.trailThickness + 2f else settings.trailThickness

        g2.stroke = BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.color = color
        for (i in 1 until points.size) {
            g2.drawLine(points[i - 1].x, points[i - 1].y, points[i].x, points[i].y)
        }
    }

    private fun drawDirectionIndicator(g2: Graphics2D) {
        val lastPoint = points.last()
        val dirText = directions.joinToString(" ") { it.arrow() }
        val hasMatch = matchedCommand != null

        val font = Font("Segoe UI", Font.BOLD, 16)
        g2.font = font
        val fm = g2.fontMetrics
        val pad = 10

        val dirW = fm.stringWidth(dirText) + pad * 2
        val dirH = fm.height + pad
        val baseX = lastPoint.x + 20
        val baseY = if (hasMatch) lastPoint.y - dirH - 8 else lastPoint.y - dirH / 2

        val matchRgb = settings.getMatchColor()
        val bgColor = if (hasMatch) Color(matchRgb.red, matchRgb.green, matchRgb.blue, 220) else Color(40, 40, 40, 220)
        val borderColor = if (hasMatch) Color(
            minOf(matchRgb.red + 60, 255), minOf(matchRgb.green + 60, 255), minOf(matchRgb.blue + 60, 255)
        ) else Color(200, 200, 200)

        drawRoundBox(g2, baseX, baseY, dirW, dirH, bgColor, borderColor)
        g2.color = Color.WHITE
        g2.drawString(dirText, baseX + pad, baseY + fm.ascent + pad / 2)

        if (hasMatch) {
            val cmdFont = Font("Segoe UI", Font.PLAIN, 14)
            g2.font = cmdFont
            val fm2 = g2.fontMetrics
            val cmd = matchedCommand!!
            val cmdW = fm2.stringWidth(cmd) + pad * 2
            val cmdH = fm2.height + pad
            val cmdY = lastPoint.y + 5
            drawRoundBox(g2, baseX, cmdY, cmdW, cmdH, bgColor, borderColor)
            g2.color = Color.WHITE
            g2.drawString(cmd, baseX + pad, cmdY + fm2.ascent + pad / 2)
        }
    }

    private fun drawRoundBox(g2: Graphics2D, x: Int, y: Int, w: Int, h: Int, bg: Color, border: Color) {
        val oldComposite = g2.composite
        g2.composite = AlphaComposite.SrcOver
        g2.color = bg
        g2.fillRoundRect(x, y, w, h, 10, 10)
        g2.stroke = BasicStroke(1.5f)
        g2.color = border
        g2.drawRoundRect(x, y, w, h, 10, 10)
        g2.composite = oldComposite
    }
}
