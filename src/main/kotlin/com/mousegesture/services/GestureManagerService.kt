package com.mousegesture.services

import com.intellij.openapi.components.*
import com.mousegesture.models.GestureDirection
import com.mousegesture.models.GestureVisualizationSettings
import com.mousegesture.models.MouseGesture


@Service(Service.Level.APP)
@State(
    name = "MouseGestureSettings",
    storages = [Storage("mouse-gestures.xml")]
)
class GestureManagerService : PersistentStateComponent<GestureManagerService.State> {

    data class GestureState(
        var id: String = "",
        var name: String = "",
        var pattern: String = "",
        var actionId: String = "",
        var actionName: String = "",
        var isEnabled: Boolean = true
    )

    data class State(
        var gestures: MutableList<GestureState> = mutableListOf(),
        var showTrail: Boolean = true,
        var showDirections: Boolean = true,
        var trailColor: String = "#7B68AB",
        var trailThickness: Float = 3.0f,
        var matchColor: String = "#4B8BBE",
        var isPluginEnabled: Boolean = true
    )

    private val gestures = mutableListOf<MouseGesture>()
    val visualizationSettings = GestureVisualizationSettings()
    var isPluginEnabled: Boolean = true

    override fun getState(): State {
        val state = State(
            showTrail = visualizationSettings.showTrail,
            showDirections = visualizationSettings.showDirections,
            trailColor = visualizationSettings.trailColor,
            trailThickness = visualizationSettings.trailThickness,
            matchColor = visualizationSettings.matchColor,
            isPluginEnabled = isPluginEnabled
        )
        state.gestures = gestures.map { g ->
            GestureState(
                id = g.id,
                name = g.name,
                pattern = g.pattern.joinToString(",") { it.name },
                actionId = g.actionId,
                actionName = g.actionName,
                isEnabled = g.isEnabled
            )
        }.toMutableList()
        return state
    }

    override fun loadState(state: State) {
        visualizationSettings.showTrail = state.showTrail
        visualizationSettings.showDirections = state.showDirections
        visualizationSettings.trailColor = state.trailColor
        visualizationSettings.trailThickness = state.trailThickness
        visualizationSettings.matchColor = state.matchColor
        isPluginEnabled = state.isPluginEnabled

        gestures.clear()
        state.gestures.forEach { gs ->
            val pattern = gs.pattern.split(",")
                .filter { it.isNotBlank() }
                .mapNotNull { runCatching { GestureDirection.valueOf(it) }.getOrNull() }
            gestures.add(
                MouseGesture(
                    id = gs.id,
                    name = gs.name,
                    pattern = pattern,
                    actionId = gs.actionId,
                    actionName = gs.actionName,
                    isEnabled = gs.isEnabled
                )
            )
        }
        if (gestures.isEmpty()) initDefaults()
    }

    fun getGestures(): List<MouseGesture> = gestures.toList()
    fun addGesture(gesture: MouseGesture) { gestures.add(gesture) }
    fun removeGesture(id: String) { gestures.removeIf { it.id == id } }
    fun updateGesture(gesture: MouseGesture) {
        val index = gestures.indexOfFirst { it.id == gesture.id }
        if (index >= 0) gestures[index] = gesture
    }

    fun findMatchingGesture(pattern: List<GestureDirection>): MouseGesture? =
        gestures.firstOrNull { it.isEnabled && it.matchesPattern(pattern) }

    fun findGestureWithSamePattern(pattern: List<GestureDirection>, excludeId: String? = null): MouseGesture? =
        gestures.firstOrNull { (excludeId == null || it.id != excludeId) && it.matchesPattern(pattern) }

    fun resetToDefaults() {
        gestures.clear()
        initDefaults()
        visualizationSettings.showTrail = true
        visualizationSettings.showDirections = true
        visualizationSettings.trailColor = "#7B68AB"
        visualizationSettings.trailThickness = 3.0f
        visualizationSettings.matchColor = "#4B8BBE"
        isPluginEnabled = true
    }

    private fun initDefaults() {
        gestures += MouseGesture(name = "Navigate Backward", pattern = listOf(GestureDirection.LEFT), actionId = "Back", actionName = "Navigate Backward")
        gestures += MouseGesture(name = "Navigate Forward", pattern = listOf(GestureDirection.RIGHT), actionId = "Forward", actionName = "Navigate Forward")
        gestures += MouseGesture(name = "Close Tab", pattern = listOf(GestureDirection.DOWN, GestureDirection.RIGHT), actionId = "CloseContent", actionName = "Close Tab")
        gestures += MouseGesture(name = "Comment Line", pattern = listOf(GestureDirection.DOWN), actionId = "CommentByLineComment", actionName = "Comment/Uncomment Line")
    }
}
