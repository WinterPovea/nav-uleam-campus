package ec.edu.uleam.navigator.location

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompassSourceTest {

    @Test
    fun `normalizeDegrees mantiene angulos en el rango 0 a 360`() {
        assertEquals(0f, SensorManagerCompassSource.normalizeDegrees(0f), 1e-4f)
        assertEquals(0f, SensorManagerCompassSource.normalizeDegrees(360f), 1e-4f)
        assertEquals(350f, SensorManagerCompassSource.normalizeDegrees(-10f), 1e-4f)
        assertEquals(45f, SensorManagerCompassSource.normalizeDegrees(405f), 1e-4f)
    }

    @Test
    fun `lowPassAzimuth interpola por el camino mas corto al cruzar el norte 359 a 1 grado`() {
        // Si estamos en 358° y la lectura apunta a 2°, el delta corto es +4°, no -356°.
        // Con alpha = 0.5 debe avanzar a 0° (Norte), nunca hacia 180° (Sur).
        val filtered = SensorManagerCompassSource.lowPassAzimuth(
            currentDeg = 358f,
            targetDeg = 2f,
            alpha = 0.5f
        )
        assertEquals(0f, filtered, 1e-3f)
    }

    @Test
    fun `lowPassAzimuth interpola correctamente en sentido antihorario cruzando 0 grados`() {
        // De 2° hacia 358° con alpha = 0.25 -> delta = -4°, resultado = 1°
        val filtered = SensorManagerCompassSource.lowPassAzimuth(
            currentDeg = 2f,
            targetDeg = 358f,
            alpha = 0.25f
        )
        assertEquals(1f, filtered, 1e-3f)
    }

    @Test
    fun `lowPassAzimuth converge progresivamente hacia el rumbo objetivo`() {
        var current: Float? = null
        repeat(25) {
            current = SensorManagerCompassSource.lowPassAzimuth(current, 90f, alpha = 0.25f)
        }
        val finalAngle = current ?: -1f
        assertTrue(kotlin.math.abs(finalAngle - 90f) < 0.1f)
    }
}
