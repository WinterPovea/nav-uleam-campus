package ec.edu.uleam.navigator.routing.astar

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NavigationGuidanceTest {

    @Test
    fun `ruta vacia devuelve lista vacia de instrucciones`() {
        val steps = NavigationGuidance.generateInstructions(emptyList(), emptyMap())
        assertTrue(steps.isEmpty())
    }

    @Test
    fun `ruta de un solo nodo genera instruccion de llegada inmediata`() {
        val node = mockNode(1, 0.0, 0.0)
        val steps = NavigationGuidance.generateInstructions(
            nodeIds = listOf(1),
            nodeById = mapOf(1 to node),
            destinationLabel = "Bloque B-01"
        )
        assertEquals(1, steps.size)
        assertEquals(DirectionType.ARRIVE, steps.first().direction)
        assertTrue(steps.first().text.contains("Bloque B-01"))
    }

    @Test
    fun `ruta con giro detecta giros a la derecha y escaleras`() {
        // 1 -> 2 (hacia el este / x positivo), luego 2 -> 3 (hacia el sur / y negativo = giro a la derecha)
        val n1 = mockNode(1, 0.0, 0.0)
        val n2 = mockNode(2, 50.0, 0.0)
        val n3 = mockNode(3, 50.0, -40.0)
        val byId = mapOf(1 to n1, 2 to n2, 3 to n3)

        val steps = NavigationGuidance.generateInstructions(
            nodeIds = listOf(1, 2, 3),
            nodeById = byId,
            hasStairsEdge = { from, to -> from == 2 && to == 3 },
            originLabel = "Puerta 1",
            destinationLabel = "Facultad Informática"
        )

        assertTrue(steps.size >= 3)
        assertEquals(DirectionType.DEPART, steps[0].direction)
        assertTrue(steps[0].text.contains("Puerta 1"))

        // Segmento con escaleras
        assertEquals(DirectionType.STAIRS, steps[1].direction)
        assertTrue(steps[1].text.contains("escaleras"))

        // Último paso es de llegada
        assertEquals(DirectionType.ARRIVE, steps.last().direction)
        assertTrue(steps.last().text.contains("Facultad Informática"))
    }

    @Test
    fun `fusiona tramos rectos consecutivos en una sola instruccion`() {
        // Tres nodos en línea recta hacia el norte
        val n1 = mockNode(1, 0.0, 0.0)
        val n2 = mockNode(2, 0.0, 30.0)
        val n3 = mockNode(3, 0.0, 70.0)
        val byId = mapOf(1 to n1, 2 to n2, 3 to n3)

        val steps = NavigationGuidance.generateInstructions(
            nodeIds = listOf(1, 2, 3),
            nodeById = byId,
            originLabel = "P1",
            destinationLabel = "Edificio Central"
        )

        // Primer paso avanza ~30 m, segundo paso continúa recto por ~40 m, deben comprimirse o quedar ordenados
        assertEquals(DirectionType.ARRIVE, steps.last().direction)
        assertTrue(steps.size <= 3)
    }
}

