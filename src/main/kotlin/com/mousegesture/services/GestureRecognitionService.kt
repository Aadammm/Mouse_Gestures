package com.mousegesture.services

import com.mousegesture.models.GestureDirection
import java.awt.Point
import kotlin.math.abs
import kotlin.math.sqrt


class GestureRecognitionService {

    private val points = mutableListOf<Point>()
    private val minimumDistance = 20.0

    fun startGesture(startPoint: Point) {
        points.clear()
        points.add(startPoint)
    }

    fun addPoint(point: Point) {
        points.add(point)
    }

    fun endGesture(): List<GestureDirection> {
       return recognizeDirections()
    }

    fun recognizeDirections(): List<GestureDirection> {
        val directions = mutableListOf<GestureDirection>()
        if (points.size < 2) {
            return directions
        }

        var lastSignificant = points[0]

        for (i in 1 until points.size) {
            val current = points[i]
            val dx = (current.x - lastSignificant.x).toDouble()
            val dy = (current.y - lastSignificant.y).toDouble()
            val distance = sqrt(dx * dx + dy * dy)

            if (distance < minimumDistance) {
                continue
            }

            val direction = determineDirection(dx, dy)
            if (directions.isEmpty() || directions.last() != direction) {
                directions.add(direction)
            }
            lastSignificant = current
        }

        return directions
    }

    private fun determineDirection(dx: Double, dy: Double): GestureDirection {
        return if (abs(dx) > abs(dy)) {
            if (dx > 0) GestureDirection.RIGHT else GestureDirection.LEFT
        } else {
            if (dy > 0) GestureDirection.DOWN else GestureDirection.UP
        }
    }
}
