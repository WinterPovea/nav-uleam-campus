package ec.edu.uleam.navigator.ui.map

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/** Punto geográfico mínimo, independiente de Android y de Room (se puede probar en JVM). */
data class GeoPoint(val lat: Double, val lon: Double)

/**
 * Avance del usuario sobre una ruta.
 * @property remaining Punto proyectado del usuario sobre la ruta + nodos que faltan por recorrer.
 * @property traveled Nodos ya recorridos + punto proyectado del usuario.
 * @property remainingM Metros que faltan hasta el destino siguiendo la ruta.
 * @property offRouteM Distancia perpendicular del usuario a la ruta (m).
 * @property bearingDeg Rumbo [0, 360) del tramo de la ruta que está recorriendo (0 = Norte, horario).
 */
data class RouteProgress(
    val remaining: List<GeoPoint>,
    val traveled: List<GeoPoint>,
    val remainingM: Double,
    val offRouteM: Double,
    val bearingDeg: Double = 0.0
)

/** Selección táctil de lugares sobre el mapa. */
object PlaceHitTest {
    /**
     * Devuelve el id del candidato más cercano al toque [tap] que esté dentro de [radiusPx],
     * o null si el toque cayó en un espacio vacío del mapa.
     */
    fun nearest(candidates: List<Pair<Int, androidx.compose.ui.geometry.Offset>>, tap: androidx.compose.ui.geometry.Offset, radiusPx: Float): Int? =
        candidates
            .map { (id, pos) -> id to (pos - tap).getDistance() }
            .filter { it.second <= radiusPx }
            .minByOrNull { it.second }
            ?.first
}

object RouteProgressCalc {
    private const val M_PER_DEG = 111_320.0

    /** Distancia aproximada (equirectangular) en metros; suficiente a escala de campus. */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val k = cos(Math.toRadians((a.lat + b.lat) / 2.0))
        return hypot((b.lat - a.lat) * M_PER_DEG, (b.lon - a.lon) * M_PER_DEG * k)
    }

    /** Rumbo de a hacia b en grados [0, 360): 0 = Norte, 90 = Este. */
    fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val k = cos(Math.toRadians((a.lat + b.lat) / 2.0))
        val dx = (b.lon - a.lon) * k
        val dy = b.lat - a.lat
        return (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
    }

    /** Proyecta [user] sobre el segmento a-b; devuelve el punto proyectado (fracción acotada a [0,1]). */
    private fun project(a: GeoPoint, b: GeoPoint, user: GeoPoint): GeoPoint {
        val k = cos(Math.toRadians(a.lat))
        val bx = (b.lon - a.lon) * M_PER_DEG * k
        val by = (b.lat - a.lat) * M_PER_DEG
        val ux = (user.lon - a.lon) * M_PER_DEG * k
        val uy = (user.lat - a.lat) * M_PER_DEG
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else ((ux * bx + uy * by) / len2).coerceIn(0.0, 1.0)
        return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    /** Calcula el avance de [user] sobre [route] (mínimo 2 puntos). Devuelve null si la ruta es inválida. */
    fun compute(route: List<GeoPoint>, user: GeoPoint): RouteProgress? {
        if (route.size < 2) return null
        var bestSeg = 0
        var bestPoint = route[0]
        var bestDist = Double.MAX_VALUE
        for (i in 0 until route.size - 1) {
            val p = project(route[i], route[i + 1], user)
            val d = distanceM(user, p)
            if (d < bestDist) { bestDist = d; bestSeg = i; bestPoint = p }
        }
        val remaining = listOf(bestPoint) + route.subList(bestSeg + 1, route.size)
        val traveled = route.subList(0, bestSeg + 1) + bestPoint
        val remainingM = remaining.zipWithNext().sumOf { (a, b) -> distanceM(a, b) }
        return RouteProgress(remaining, traveled, remainingM, bestDist, bearingDeg(route[bestSeg], route[bestSeg + 1]))
    }
}
