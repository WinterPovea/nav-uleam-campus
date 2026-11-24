package ec.edu.uleam.navigator.ui.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapStyleTest {
    @Test
    fun elBotonAlternaClaroOscuroSistema() {
        assertEquals(MapStyle.OSCURO, MapStyle.CLARO.siguiente())
        assertEquals(MapStyle.SISTEMA, MapStyle.OSCURO.siguiente())
        assertEquals(MapStyle.CLARO, MapStyle.SISTEMA.siguiente())
    }

    @Test
    fun sistemaSigueAlTelefono() {
        assertFalse(MapStyle.CLARO.esOscuro(sistemaOscuro = true))
        assertTrue(MapStyle.OSCURO.esOscuro(sistemaOscuro = false))
        assertTrue(MapStyle.SISTEMA.esOscuro(sistemaOscuro = true))
        assertFalse(MapStyle.SISTEMA.esOscuro(sistemaOscuro = false))
    }

    @Test
    fun preferenciaGuardadaSeLeePorNombre() {
        assertEquals(MapStyle.OSCURO, MapStyle.fromName("OSCURO"))
        assertNull(MapStyle.fromName(null))
        assertNull(MapStyle.fromName("otro"))
    }
}

class EdgeShapesTest {
    private val curva = CampusShape("ARISTA", "1-2", "OTRO", listOf(GeoPoint(0.0, 0.0), GeoPoint(0.5, 1.0), GeoPoint(1.0, 1.0)))
    private val nodos = mapOf(1 to GeoPoint(0.0, 0.0), 2 to GeoPoint(1.0, 1.0), 3 to GeoPoint(2.0, 1.0))
    private val shapes = EdgeShapes.from(listOf(curva))

    @Test
    fun formaEnAmbosSentidos() {
        assertEquals(curva.puntos, shapes.between(1, 2))
        assertEquals(curva.puntos.reversed(), shapes.between(2, 1))
        assertNull(shapes.between(2, 3))
    }

    @Test
    fun rutaSigueLasCurvasSinRepetirNodos() {
        val pts = shapes.routePoints(listOf(3, 2, 1)) { nodos[it] }
        assertEquals(listOf(GeoPoint(2.0, 1.0), GeoPoint(1.0, 1.0), GeoPoint(0.5, 1.0), GeoPoint(0.0, 0.0)), pts)
    }
}
