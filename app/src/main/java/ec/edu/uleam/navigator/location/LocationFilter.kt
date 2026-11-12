package ec.edu.uleam.navigator.location

import ec.edu.uleam.navigator.routing.astar.GeoMath

/**
 * Muestra cruda proveniente del proveedor GNSS antes de filtrado.
 */
data class GnssSample(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val timestampMs: Long
)

/**
 * Resultado de evaluar una lectura GNSS en [LocationFilter].
 */
sealed interface FilterResult {
    /** Lectura válida dentro de precisión y velocidad peatonal/vehicular razonable en campus. */
    data class Accepted(
        val lat: Double,
        val lon: Double,
        val accuracyM: Float,
        val speedMps: Double
    ) : FilterResult

    /** Lectura con incertidumbre superior al umbral permitido (> maxAccuracyM). */
    data class LowAccuracy(
        val lat: Double,
        val lon: Double,
        val accuracyM: Float
    ) : FilterResult

    /** Lectura descartada por salto cinemático anómalo (> maxSpeedMps, ej. rebote multipath). */
    data class RejectedJump(
        val distanceM: Double,
        val speedMps: Double
    ) : FilterResult

    /** Coordenadas corruptas (NaN, infinitas o fuera de rango geográfico). */
    data object Invalid : FilterResult
}

/**
 * Filtro de calidad y suavizado cinemático para lecturas GNSS en el campus ULEAM.
 *
 * Reglas:
 * 1. Descarta coordenadas no finitas o fuera del dominio WGS84.
 * 2. Clasifica como [FilterResult.LowAccuracy] lecturas con incertidumbre > [maxAccuracyM] (30 m).
 * 3. Descarta saltos bruscos de posición cuya velocidad implícita supere [maxSpeedMps] (20 m/s)
 *    respecto a la última lectura válida aceptada.
 */
class LocationFilter(
    val maxAccuracyM: Float = DEFAULT_MAX_ACCURACY_M,
    val maxSpeedMps: Double = DEFAULT_MAX_SPEED_MPS
) {
    var lastAccepted: GnssSample? = null
        private set

    fun process(sample: GnssSample): FilterResult =
        process(sample.lat, sample.lon, sample.accuracyM, sample.timestampMs)

    fun process(
        lat: Double,
        lon: Double,
        accuracyM: Float,
        timestampMs: Long
    ): FilterResult {
        if (!lat.isFinite() || !lon.isFinite() ||
            lat !in -90.0..90.0 || lon !in -180.0..180.0 ||
            !accuracyM.isFinite() || accuracyM < 0f
        ) {
            return FilterResult.Invalid
        }

        if (accuracyM > maxAccuracyM) {
            return FilterResult.LowAccuracy(lat, lon, accuracyM)
        }

        val prev = lastAccepted
        if (prev != null) {
            val distM = GeoMath.haversine(prev.lat, prev.lon, lat, lon)
            val dtSec = (timestampMs - prev.timestampMs) / 1000.0
            val speedMps = when {
                dtSec > 0.0 -> distM / dtSec
                distM > 0.0 -> Double.POSITIVE_INFINITY
                else -> 0.0
            }

            if (speedMps > maxSpeedMps) {
                return FilterResult.RejectedJump(distanceM = distM, speedMps = speedMps)
            }

            lastAccepted = GnssSample(lat, lon, accuracyM, timestampMs)
            return FilterResult.Accepted(lat, lon, accuracyM, speedMps)
        }

        lastAccepted = GnssSample(lat, lon, accuracyM, timestampMs)
        return FilterResult.Accepted(lat, lon, accuracyM, 0.0)
    }

    /**
     * Filtra una secuencia completa de lecturas GNSS ruidosas devolviendo únicamente
     * las muestras válidas que superan los controles de precisión y velocidad máxima.
     */
    fun filterTrace(samples: Iterable<GnssSample>): List<GnssSample> {
        val accepted = mutableListOf<GnssSample>()
        for (sample in samples) {
            if (process(sample) is FilterResult.Accepted) {
                accepted.add(sample)
            }
        }
        return accepted
    }

    fun reset() {
        lastAccepted = null
    }

    companion object {
        const val DEFAULT_MAX_ACCURACY_M = 30f
        const val DEFAULT_MAX_SPEED_MPS = 20.0
    }
}
