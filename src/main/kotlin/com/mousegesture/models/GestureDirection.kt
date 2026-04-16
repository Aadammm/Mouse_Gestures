package com.mousegesture.models

enum class GestureDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT;

    fun arrow(): String = when (this) {
        UP    -> "↑"
        DOWN  -> "↓"
        LEFT  -> "←"
        RIGHT -> "→"
    }
}
