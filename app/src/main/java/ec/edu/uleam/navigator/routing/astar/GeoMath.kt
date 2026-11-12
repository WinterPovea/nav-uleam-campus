package ec.edu.uleam.navigator.routing.astar

import kotlin.math.*

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Distancia en línea recta (círculo máximo) en metros entre dos coordenadas WGS84. */
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun haversine(a: GraphNode, b: GraphNode): Double =
        haversine(a.latitude, a.longitude, b.latitude, b.longitude)

    /** Azimut inicial en grados [0, 360) desde el punto 1 al punto 2. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        val degrees = Math.toDegrees(atan2(y, x))
        return (degrees + 360.0) % 360.0
    }

    /**
     * Ángulo de giro relativo en grados [-180, 180] entre dos rumbos sucesivos.
     * Positivo = Giro hacia la derecha.
     * Negativo = Giro hacia la izquierda.
     */
    fun turnAngle(bearing1: Double, bearing2: Double): Double {
        var diff = (bearing2 - bearing1) % 360.0
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        return diff
    }
}
