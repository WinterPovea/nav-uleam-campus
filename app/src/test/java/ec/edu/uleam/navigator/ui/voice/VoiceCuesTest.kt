package ec.edu.uleam.navigator.ui.voice

import ec.edu.uleam.navigator.routing.astar.GraphNode
import ec.edu.uleam.navigator.routing.astar.NodeType
import ec.edu.uleam.navigator.ui.map.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VoiceCuesTest {
    private val m = 1.0 / 111_320.0
    // Ruta en "L": 1 -> 2 hacia el norte 50 m, 2 -> 3 hacia el este 50 m (giro a la derecha), 3 -> 4 recto con escaleras
    private val nodos = mapOf(
        1 to GraphNode(1, 0.0, 0.0, NodeType.CONEXION),
        2 to GraphNode(2, 50 * m, 0.0, NodeType.CONEXION),
        3 to GraphNode(3, 50 * m, 50 * m, NodeType.CONEXION),
        4 to GraphNode(4, 50 * m, 100 * m, NodeType.DESTINO)
    )

    @Test
    fun giroYEscaleras() {
        val cues = VoiceCues.plan(listOf(1, 2, 3, 4), nodos::get) { a, b -> a == 3 && b == 4 }
        assertEquals(listOf("Gira a la derecha", "Escaleras adelante"), cues.map { it.text })
        assertEquals(listOf(1, 2), cues.map { it.nodeIndex })
    }

    @Test
    fun avisoSeDiceUnaVezYSoloCerca() {
        val cues = VoiceCues.plan(listOf(1, 2, 3, 4), nodos::get) { _, _ -> false }
        val t = VoiceCueTracker(cues, triggerM = 15.0)
        assertNull(t.check(GeoPoint(0.0, 0.0)))                       // a 50 m del giro
        assertEquals("Gira a la derecha", t.check(GeoPoint(40 * m, 0.0))?.text)
        assertNull(t.check(GeoPoint(45 * m, 0.0)))                    // ya dicho
    }

    @Test
    fun metrosRedondeados() {
        assertEquals("125 metros", VoiceCues.metros(123.0))
        assertEquals("1,2 kilómetros", VoiceCues.metros(1210.0).replace('.', ','))
    }
}
