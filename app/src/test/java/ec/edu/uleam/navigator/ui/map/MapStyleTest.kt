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
