package ec.edu.uleam.navigator.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.sin

/**
 * Proyección 2D (WGS84 <-> Píxeles de pantalla) para el campus de la ULEAM.
 * Soporta zoom, desplazamiento (pan) y transformación afín de rotación sobre el centro de pantalla.
 */
class MapProjection(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val size: IntSize,
    val zoom: Float,
    val pan: Offset,
    val rotationDeg: Float = 0f
) {
    private val margin = 32f
    val base: Float = minOf(
        (size.width - 2 * margin) / (maxLon - minLon).toFloat(),
        (size.height - 2 * margin) / (maxLat - minLat).toFloat()
    )
    val center = Offset(size.width / 2f, size.height / 2f)

    private val rad = Math.toRadians(rotationDeg.toDouble())
    private val cosR = cos(rad).toFloat()
    private val sinR = sin(rad).toFloat()

    /** Proyecta coordenadas geográficas (lat, lon) a coordenadas de pantalla (X, Y). */
    fun toScreen(lat: Double, lon: Double): Offset {
        val p = Offset(
            margin + ((lon - minLon) * base).toFloat(),
            margin + ((maxLat - lat) * base).toFloat()
        )
        val v = (p - center) * zoom + pan
        if (rotationDeg == 0f) return v + center
        val rx = v.x * cosR - v.y * sinR
        val ry = v.x * sinR + v.y * cosR
        return Offset(center.x + rx, center.y + ry)
    }

    /** Inversa exacta: convierte un toque en pantalla (X, Y) a coordenadas (lat, lon) considerando rotación. */
    fun toGeo(o: Offset): Pair<Double, Double> {
        val rel = o - center
        val unrotated = if (rotationDeg == 0f) {
            rel
        } else {
            // Rotación inversa por -rotationDeg
            Offset(
                rel.x * cosR + rel.y * sinR,
                -rel.x * sinR + rel.y * cosR
            )
        }
        val p = (unrotated - pan) / zoom + center
        return (maxLat - (p.y - margin) / base) to (minLon + (p.x - margin) / base)
    }

    /**
     * Transforma un vector de arrastre en pantalla al sistema de coordenadas previo a la rotación
     * para que el paneo táctil siga exactamente la dirección del dedo aunque el mapa esté rotado.
     */
    fun rotatePanDelta(screenDelta: Offset): Offset {
        if (rotationDeg == 0f) return screenDelta
        return Offset(
            screenDelta.x * cosR + screenDelta.y * sinR,
            -screenDelta.x * sinR + screenDelta.y * cosR
        )
    }
}
