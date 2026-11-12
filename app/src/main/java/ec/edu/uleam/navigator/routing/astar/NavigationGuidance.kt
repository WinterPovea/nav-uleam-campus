package ec.edu.uleam.navigator.routing.astar

import java.util.Locale

enum class DirectionType {
    DEPART,
    STRAIGHT,
    TURN_RIGHT,
    TURN_LEFT,
    TURN_SHARP_RIGHT,
    TURN_SHARP_LEFT,
    STAIRS,
    ARRIVE
}

data class NavigationInstruction(
    val stepIndex: Int,
    val text: String,
    val distanceM: Double,
    val direction: DirectionType,
    val landmark: String? = null
)

object NavigationGuidance {

    /**
     * Genera instrucciones textuales paso a paso (turn-by-turn) analizando
     * las desviaciones angulares (azimut) y cambios de cota (escaleras) en la ruta.
     */
    fun generateInstructions(
        nodeIds: List<Int>,
        nodeById: Map<Int, GraphNode>,
        hasStairsEdge: (fromId: Int, toId: Int) -> Boolean = { _, _ -> false },
        originLabel: String = "tu ubicación",
        destinationLabel: String? = null
    ): List<NavigationInstruction> {
        if (nodeIds.isEmpty()) return emptyList()

        if (nodeIds.size == 1) {
            val dest = destinationLabel ?: "tu destino"
            return listOf(
                NavigationInstruction(
                    stepIndex = 1,
                    text = "Has llegado a $dest.",
                    distanceM = 0.0,
                    direction = DirectionType.ARRIVE,
                    landmark = dest
                )
            )
        }

        val rawSteps = mutableListOf<NavigationInstruction>()
        var prevBearing: Double? = null

        for (i in 0 until nodeIds.size - 1) {
            val uId = nodeIds[i]
            val vId = nodeIds[i + 1]
            val u = nodeById[uId] ?: continue
            val v = nodeById[vId] ?: continue

            val distance = GeoMath.haversine(u, v)
            val bearing = GeoMath.bearing(u.latitude, u.longitude, v.latitude, v.longitude)
            val stairs = hasStairsEdge(uId, vId)

            val direction: DirectionType
            val text: String

            if (i == 0) {
                if (stairs) {
                    direction = DirectionType.STAIRS
                    text = String.format(Locale.US, "Inicia usando las escaleras (%.0f m)", distance)
                } else {
                    direction = DirectionType.DEPART
                    text = String.format(Locale.US, "Inicia desde %s y avanza %.0f m", originLabel, distance)
                }
            } else {
                val turn = GeoMath.turnAngle(prevBearing ?: bearing, bearing)
                when {
                    stairs -> {
                        direction = DirectionType.STAIRS
                        text = String.format(Locale.US, "Usa las escaleras y avanza %.0f m", distance)
                    }
                    turn in 30.0..120.0 -> {
                        direction = DirectionType.TURN_RIGHT
                        text = String.format(Locale.US, "Gira a la derecha y avanza %.0f m", distance)
                    }
                    turn > 120.0 -> {
                        direction = DirectionType.TURN_SHARP_RIGHT
                        text = String.format(Locale.US, "Giro pronunciado a la derecha (%.0f m)", distance)
                    }
                    turn in -120.0..-30.0 -> {
                        direction = DirectionType.TURN_LEFT
                        text = String.format(Locale.US, "Gira a la izquierda y avanza %.0f m", distance)
                    }
                    turn < -120.0 -> {
                        direction = DirectionType.TURN_SHARP_LEFT
                        text = String.format(Locale.US, "Giro pronunciado a la izquierda (%.0f m)", distance)
                    }
                    else -> {
                        direction = DirectionType.STRAIGHT
                        text = String.format(Locale.US, "Continúa recto por %.0f m", distance)
                    }
                }
            }

            rawSteps.add(
                NavigationInstruction(
                    stepIndex = rawSteps.size + 1,
                    text = text,
                    distanceM = distance,
                    direction = direction
                )
            )
            prevBearing = bearing
        }

        // Fusión de pasos consecutivos rectos para evitar instrucciones redundantes
        val compressedSteps = mutableListOf<NavigationInstruction>()
        for (step in rawSteps) {
            val last = compressedSteps.lastOrNull()
            if (last != null && last.direction == DirectionType.STRAIGHT && step.direction == DirectionType.STRAIGHT) {
                val totalDist = last.distanceM + step.distanceM
                val combined = NavigationInstruction(
                    stepIndex = last.stepIndex,
                    text = String.format(Locale.US, "Continúa recto por %.0f m", totalDist),
                    distanceM = totalDist,
                    direction = DirectionType.STRAIGHT
                )
                compressedSteps[compressedSteps.lastIndex] = combined
            } else {
                compressedSteps.add(step.copy(stepIndex = compressedSteps.size + 1))
            }
        }

        // Paso final de llegada
        val finalDestination = destinationLabel ?: "tu destino"
        compressedSteps.add(
            NavigationInstruction(
                stepIndex = compressedSteps.size + 1,
                text = "Has llegado al destino ($finalDestination).",
                distanceM = 0.0,
                direction = DirectionType.ARRIVE
            )
        )

        return compressedSteps
    }
}

