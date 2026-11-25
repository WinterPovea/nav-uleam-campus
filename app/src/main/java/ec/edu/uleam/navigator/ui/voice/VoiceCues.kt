package ec.edu.uleam.navigator.ui.voice

import ec.edu.uleam.navigator.routing.astar.GeoMath
import ec.edu.uleam.navigator.routing.astar.GraphNode
import ec.edu.uleam.navigator.ui.map.GeoPoint
import ec.edu.uleam.navigator.ui.map.RouteProgressCalc
import kotlin.math.abs
import kotlin.math.roundToInt

/** Aviso de voz ligado a un punto de la ruta (se dice al acercarse a él). */
data class VoiceCue(val nodeIndex: Int, val point: GeoPoint, val text: String)

/**
 * Planifica los avisos de voz de una ruta: giros (mismo criterio de ángulos que NavigationGuidance)
 * y escaleras adelante. Lógica pura, sin Android.
 */
object VoiceCues {
    /** Giros menores a este ángulo (grados) no se anuncian. */
    const val MIN_TURN_DEG = 30.0

    fun plan(
        nodeIds: List<Int>,
        nodeById: (Int) -> GraphNode?,
        hasStairs: (Int, Int) -> Boolean
    ): List<VoiceCue> {
        val nodes = nodeIds.mapNotNull(nodeById)
        if (nodes.size < 2 || nodes.size != nodeIds.size) return emptyList()
        val cues = ArrayList<VoiceCue>()
        for (i in 1 until nodes.size - 1) {
            val prev = nodes[i - 1]; val cur = nodes[i]; val next = nodes[i + 1]
            val p = GeoPoint(cur.latitude, cur.longitude)
            val stairsNext = hasStairs(cur.id, next.id)
            val stairsPrev = hasStairs(prev.id, cur.id)
            if (stairsNext && !stairsPrev) {
                cues.add(VoiceCue(i, p, "Escaleras adelante"))
                continue
            }
            val b1 = GeoMath.bearing(prev.latitude, prev.longitude, cur.latitude, cur.longitude)
            val b2 = GeoMath.bearing(cur.latitude, cur.longitude, next.latitude, next.longitude)
            val turn = GeoMath.turnAngle(b1, b2)
            if (abs(turn) < MIN_TURN_DEG) continue
            val text = when {
                turn > 120 -> "Gira completamente a la derecha"
                turn > 0 -> "Gira a la derecha"
                turn < -120 -> "Gira completamente a la izquierda"
                else -> "Gira a la izquierda"
            }
            cues.add(VoiceCue(i, p, text))
        }
        // El primer tramo con escaleras también se avisa al salir.
        if (hasStairs(nodes[0].id, nodes[1].id)) {
            cues.add(0, VoiceCue(0, GeoPoint(nodes[0].latitude, nodes[0].longitude), "Escaleras adelante"))
        }
        return cues
    }

    /** Frase al iniciar la ruta. */
    fun startText(destino: String, distanceM: Double): String =
        "Ruta a $destino iniciada. ${metros(distanceM)}."

    fun arrivalText(destino: String): String = "Llegaste a $destino"

    fun metros(d: Double): String {
        val m = (d / 5.0).roundToInt() * 5
        return if (m >= 1000) "%.1f kilómetros".format(m / 1000.0).replace('.', ',') else "$m metros"
    }
}

/**
 * Lleva la cuenta de qué avisos ya se dijeron. Un aviso se dispara al estar a [triggerM] metros o menos
 * de su punto; los avisos anteriores al disparado se dan por pasados (si el GPS se los saltó, no se dicen tarde).
 */
class VoiceCueTracker(private val cues: List<VoiceCue>, private val triggerM: Double = 15.0) {
    private var nextIdx = 0

    fun check(user: GeoPoint): VoiceCue? {
        for (i in nextIdx until cues.size) {
            if (RouteProgressCalc.distanceM(user, cues[i].point) <= triggerM) {
                nextIdx = i + 1
                return cues[i]
            }
        }
        return null
    }
}
