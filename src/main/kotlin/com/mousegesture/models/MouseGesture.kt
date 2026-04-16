package com.mousegesture.models

import java.util.UUID

data class MouseGesture(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var pattern: List<GestureDirection> = emptyList(),
    var actionId: String = "",
    var actionName: String = "",
    var isEnabled: Boolean = true
) {

    val patternDescription: String
        get() = pattern.joinToString(" ") { it.arrow() }

    fun matchesPattern(detectedPattern: List<GestureDirection>): Boolean =
        pattern == detectedPattern
}
