package ec.edu.uleam.navigator.location

import kotlin.math.cos
import kotlin.math.hypot

/**
 * Suavizado de la posición GPS con un filtro de Kalman simple (posición constante + ruido de proceso
 * proporcional al tiempo, como una persona caminando).
 *
 * Cada lectura pesa según su precisión: una lectura de ±5 m mueve el punto casi por completo, una de ±25 m
 * apenas lo corre. Así el punto azul y el origen dejan de "saltar" entre lecturas sin quedarse atrás cuando
 * el usuario camina. Si la lectura nueva cae muy lejos ([resetJumpM]) se asume un cambio real y se reinicia.
 *
 * Lógica pura (sin Android) para poder probarla en tests unitarios.
 */
class GpsSmoother(
    /** Cuánto puede moverse el usuario (m/s) entre lecturas; ≈ paso rápido. */
    private val processSpeedMps: Double = 2.0,
    private val resetJumpM: Double = 60.0,
    private val minAccuracyM: Double = 3.0
) {
    data class Smoothed(val lat: Double, val lon: Double, val accuracyM: Float)

    private var lat = 0.0
    private var lon = 0.0
    private var variance = -1.0 // < 0: sin estado
    private var lastMs = 0L

    fun reset() { variance = -1.0 }

    fun update(measLat: Double, measLon: Double, accuracyM: Float?, nowMs: Long): Smoothed {
        val acc = (accuracyM?.toDouble() ?: DEFAULT_ACCURACY_M).coerceAtLeast(minAccuracyM)
        if (variance < 0 || distanceM(lat, lon, measLat, measLon) > resetJumpM) {
            lat = measLat; lon = measLon; variance = acc * acc; lastMs = nowMs
            return Smoothed(lat, lon, acc.toFloat())
        }
        val dtS = ((nowMs - lastMs).coerceAtLeast(0L)) / 1000.0
        lastMs = nowMs
        variance += dtS * processSpeedMps * processSpeedMps
        val k = variance / (variance + acc * acc)
        lat += k * (measLat - lat)
        lon += k * (measLon - lon)
        variance *= (1 - k)
        return Smoothed(lat, lon, kotlin.math.sqrt(variance).toFloat())
    }

    private fun distanceM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val k = cos(Math.toRadians((aLat + bLat) / 2))
        return hypot((bLat - aLat) * M_PER_DEG, (bLon - aLon) * M_PER_DEG * k)
    }

    companion object {
        const val DEFAULT_ACCURACY_M = 10.0
        private const val M_PER_DEG = 111_320.0
    }
}

/**
 * Histéresis del origen: un nodo de origen nuevo solo se acepta cuando el selector lo elige de forma
 * continua durante [holdMs] (evita que el origen y la ruta cambien con cada lectura ruidosa).
 */
class OriginStabilizer(private val holdMs: Long = 3_000L) {
    private var pendingId: Int? = null
    private var pendingSinceMs = 0L

    fun reset() { pendingId = null }

    /** @return true si se debe pasar a [candidateId] ahora. */
    fun offer(candidateId: Int, currentId: Int?, nowMs: Long): Boolean {
        if (currentId == null) { pendingId = null; return true }
        if (candidateId == currentId) { pendingId = null; return false }
        if (candidateId != pendingId) { pendingId = candidateId; pendingSinceMs = nowMs; return false }
        if (nowMs - pendingSinceMs >= holdMs) { pendingId = null; return true }
        return false
    }
}

/**
 * Radio de llegada según la precisión que reporta el GPS: con buena señal se exige estar más cerca,
 * con señal mala se acepta un poco más lejos (sin pasar de [maxM], para no "llegar" desde otro bloque).
 */
fun arrivalRadiusM(accuracyM: Float?, minM: Double = 6.0, maxM: Double = 12.0): Double =
    (accuracyM?.toDouble() ?: 8.0).coerceIn(minM, maxM)
