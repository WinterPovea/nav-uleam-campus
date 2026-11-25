package ec.edu.uleam.navigator.ui

import ec.edu.uleam.navigator.data.CampusRepository
import ec.edu.uleam.navigator.routing.astar.RealCampusData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CategoriasTest {
    private val lugares = RealCampusData.lugares()

    @Test
    fun cafeteriasYZonasSeguras() {
        val comer = Categorias.filtrar(lugares, "COMIDA").map { it.idNodo }
        // Patios de comidas (Tasty Food), la cafetería El bosque y el Laboratorio gastronómico
        assertTrue(comer.containsAll(listOf(305, 318, 326, 327, 321, 312, 230)))
        assertTrue(Categorias.filtrar(lugares, "COMIDA").all { it.categoria == "COMIDA" || it.idNodo == 230 })
        assertEquals(3, Categorias.filtrar(lugares, "ZONA_SEGURA").size)
    }

    @Test
    fun bloquesSinAccesoDuplicado() {
        val bloques = Categorias.filtrar(lugares, "BLOQUE")
        assertTrue(bloques.none { it.nombre.contains("acceso 2") })
        assertTrue(bloques.any { it.idNodo == 230 }) // Laboratorio gastronómico (mismo bloque B-05) sigue en la lista
    }

    @Test
    fun deporteIncluyeCultura() {
        val cats = Categorias.filtrar(lugares, "DEPORTE").map { it.categoria }.toSet()
        assertEquals(setOf("DEPORTE", "CULTURA"), cats)
    }

    @Test
    fun recientesSinRepetirYConLimite() {
        assertEquals(listOf(3, 1, 2), Recientes.agregar(listOf(1, 2, 3), 3))
        assertEquals(listOf(9, 1, 2, 3, 4), Recientes.agregar(listOf(1, 2, 3, 4, 5), 9))
    }

    @Test
    fun aliasFcvtEncuentraFacivitec() {
        val r = CampusRepository.filtrar(lugares, "fcvt")
        assertTrue(r.isNotEmpty())
        assertTrue(r.any { it.idNodo == 216 })
        assertTrue(r.none { it.codigo.orEmpty().startsWith("FCS") })
    }
}
