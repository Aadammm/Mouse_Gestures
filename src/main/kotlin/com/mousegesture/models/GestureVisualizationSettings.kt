package com.mousegesture.models

import com.intellij.ui.JBColor
import java.awt.Color

data class GestureVisualizationSettings(
    var showTrail: Boolean = true,
    var showDirections: Boolean = true,
    var trailColor: String = "#7B68AB",
    var trailThickness: Float = 3.0f,
    var matchColor: String = "#4B8BBE"
) {
    fun getTrailColor(): Color = runCatching { Color.decode(trailColor) }.getOrDefault(JBColor(Color(123, 104, 171), Color(150, 130, 200)) )
    fun getMatchColor(): Color = runCatching { Color.decode(matchColor) }.getOrDefault(JBColor(Color(75, 139, 190), Color(100, 160, 210)))
}
