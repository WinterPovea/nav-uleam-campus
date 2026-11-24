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

class BlockLabelTest {
    @Test
    fun bloquesSeRotulanConBloqueYSigla() {
        assertEquals("Bloque B-16 (FACIVITEC)", blockLabel("FACIVITEC·B-16", "Facultad de Ciencias de la Vida y Tecnologías (Bloque B-16)"))
        assertEquals("Bloque B-13 (FCACC)", blockLabel("FCACC·B-13", "Facultad de Ciencias Administrativas, Contables y Comercio (Bloque B-13, acceso 2)"))
        assertNull(blockLabel("FETAH·B-05", "Laboratorio gastronómico (Bloque B-05)"))   // no es la facultad: conserva su nombre
        assertNull(blockLabel(null, "Paraninfo"))
    }
}
