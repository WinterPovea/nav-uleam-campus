package ec.edu.uleam.navigator.location

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class GpsSmootherTest {
    private val lat0 = -0.9525
    private val lon0 = -80.7445
    private val m = 1.0 / 111_320.0 // grados por metro (latitud)

    @Test
    fun primeraLecturaSeUsaTalCual() {
        val s = GpsSmoother().update(lat0, lon0, 5f, 0L)
        assertEquals(lat0, s.lat, 1e-12)
        assertEquals(lon0, s.lon, 1e-12)
    }

    @Test
    fun ruidoQuietoSeAmortigua() {
        val g = GpsSmoother()
        g.update(lat0, lon0, 10f, 0L)
        // Lecturas alternando ±8 m alrededor del mismo punto, cada segundo
        var maxDesvio = 0.0
        for (i in 1..20) {
            val r = g.update(lat0 + (if (i % 2 == 0) 8 else -8) * m, lon0, 10f, i * 1000L)
            maxDesvio = maxOf(maxDesvio, abs(r.lat - lat0) / m)
        }
        assertTrue(maxDesvio < 5.0, "desvío suavizado $maxDesvio m")
    }

    @Test
    fun alCaminarSigueAlUsuario() {
        val g = GpsSmoother()
        var last = g.update(lat0, lon0, 5f, 0L)
        // 1.4 m/s hacia el norte durante 20 s con buena precisión
        for (i in 1..20) last = g.update(lat0 + 1.4 * i * m, lon0, 5f, i * 1000L)
        val atraso = (lat0 + 28 * m - last.lat) / m
        assertTrue(atraso < 5.0, "atraso $atraso m")
    }

    @Test
    fun saltoGrandeReinicia() {
        val g = GpsSmoother()
        g.update(lat0, lon0, 5f, 0L)
        val r = g.update(lat0 + 100 * m, lon0, 5f, 1000L)
        assertEquals(lat0 + 100 * m, r.lat, 1e-9)
    }

    @Test
    fun origenSoloCambiaSiSeMantiene() {
        val st = OriginStabilizer(holdMs = 3000)
        assertTrue(st.offer(410, null, 0))          // sin origen: se acepta de una
        assertFalse(st.offer(410, 410, 500))        // mismo origen: nada que cambiar
        assertFalse(st.offer(420, 410, 1000))       // nuevo candidato: espera
        assertFalse(st.offer(420, 410, 2500))
        assertFalse(st.offer(410, 410, 3000))       // vuelve al actual: se olvida el pendiente
        assertFalse(st.offer(420, 410, 3500))
        assertTrue(st.offer(420, 410, 6600))        // 3.1 s seguidos: cambia
    }

    @Test
    fun radioDeLlegadaSegunPrecision() {
        assertEquals(6.0, arrivalRadiusM(3f))
        assertEquals(9.0, arrivalRadiusM(9f))
        assertEquals(12.0, arrivalRadiusM(40f))
        assertEquals(8.0, arrivalRadiusM(null))
    }
}
