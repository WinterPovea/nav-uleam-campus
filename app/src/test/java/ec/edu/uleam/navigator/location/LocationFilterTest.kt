package ec.edu.uleam.navigator.location

import ec.edu.uleam.navigator.routing.astar.mockNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocationFilterTest {

    @Test
    fun `acepta lecturas sucesivas a velocidad peatonal normal`() {
        val filter = LocationFilter()
        val p1 = mockNode(1, 0.0, 0.0)
        val p2 = mockNode(2, 2.4, 0.0) // 2.4 m en 2 segundos = 1.2 m/s

        val r1 = assertInstanceOf(
            FilterResult.Accepted::class.java,
            filter.process(p1.latitude, p1.longitude, 6f, 1_000L)
        )
        assertEquals(0.0, r1.speedMps, 1e-6)

        val r2 = assertInstanceOf(
            FilterResult.Accepted::class.java,
            filter.process(p2.latitude, p2.longitude, 5f, 3_000L)
        )
        assertEquals(1.2, r2.speedMps, 0.05)
        assertEquals(p2.latitude, filter.lastAccepted?.lat)
    }

    @Test
    fun `rechaza salto GPS anomalo superior a 20 metros por segundo`() {
        val filter = LocationFilter()
        val p1 = mockNode(1, 0.0, 0.0)
        val jump = mockNode(2, 120.0, 0.0) // 120 m en 2 segundos = 60 m/s (> 20 m/s)
        val p3 = mockNode(3, 4.0, 0.0)     // 4 m desde p1 en 4 segundos = 1.0 m/s

        filter.process(p1.latitude, p1.longitude, 5f, 1_000L)

        val rejected = assertInstanceOf(
            FilterResult.RejectedJump::class.java,
            filter.process(jump.latitude, jump.longitude, 8f, 3_000L)
        )
        assertEquals(120.0, rejected.distanceM, 1.0)
        // La última posición aceptada sigue siendo p1
        assertEquals(p1.latitude, filter.lastAccepted?.lat)
        assertEquals(p1.longitude, filter.lastAccepted?.lon)

        // La siguiente lectura real cercana a p1 se acepta normalmente
        val r3 = assertInstanceOf(
            FilterResult.Accepted::class.java,
            filter.process(p3.latitude, p3.longitude, 5f, 5_000L)
        )
        assertEquals(1.0, r3.speedMps, 0.05)
    }

    @Test
    fun `clasifica como LowAccuracy lecturas con incertidumbre mayor a 30m sin alterar lastAccepted`() {
        val filter = LocationFilter(maxAccuracyM = 30f)
        val p1 = mockNode(1, 0.0, 0.0)
        val noisy = mockNode(2, 15.0, 15.0)

        filter.process(p1.latitude, p1.longitude, 8f, 1_000L)

        assertInstanceOf(
            FilterResult.LowAccuracy::class.java,
            filter.process(noisy.latitude, noisy.longitude, 48f, 3_000L)
        )
        assertEquals(p1.latitude, filter.lastAccepted?.lat)
    }

    @Test
    fun `rechaza coordenadas NaN o fuera de rango geografico`() {
        val filter = LocationFilter()
        assertEquals(FilterResult.Invalid, filter.process(Double.NaN, -80.746, 5f, 1_000L))
        assertEquals(FilterResult.Invalid, filter.process(-95.0, -80.746, 5f, 1_000L))
        assertNull(filter.lastAccepted)
    }

    @Test
    fun `filterTrace limpia una traza GNSS ruidosa conservando solo el camino real`() {
        val filter = LocationFilter()
        val n0 = mockNode(1, 0.0, 0.0)
        val n1 = mockNode(2, 2.5, 0.0)
        val multipathSpike = mockNode(99, 250.0, -180.0) // Salto multipath de >300 m en 2s
        val lowAccSpike = mockNode(98, 5.0, 5.0)         // Incertidumbre de 65 m
        val n2 = mockNode(3, 5.0, 0.0)
        val n3 = mockNode(4, 7.5, 0.0)

        val rawTrace = listOf(
            GnssSample(n0.latitude, n0.longitude, 6f, 0L),
            GnssSample(n1.latitude, n1.longitude, 6f, 2_000L),
            GnssSample(multipathSpike.latitude, multipathSpike.longitude, 10f, 4_000L),
            GnssSample(lowAccSpike.latitude, lowAccSpike.longitude, 65f, 5_000L),
            GnssSample(n2.latitude, n2.longitude, 5f, 6_000L),
            GnssSample(n3.latitude, n3.longitude, 5f, 8_000L)
        )

        val clean = filter.filterTrace(rawTrace)
        assertEquals(4, clean.size)
        assertEquals(listOf(0L, 2_000L, 6_000L, 8_000L), clean.map { it.timestampMs })
    }
}
