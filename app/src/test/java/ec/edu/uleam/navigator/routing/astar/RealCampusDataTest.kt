package ec.edu.uleam.navigator.routing.astar

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Pruebas con el grafo REAL del campus (archivos CSV de assets). Verifican los datos y el motor juntos. */
class RealCampusDataTest {
    private val graph = RealCampusData.graph()
    private val engine = AStarEngine(graph)
    private val entradas = graph.nodes.filter { it.type == NodeType.ENTRADA }.map { it.id }
    private val destinos = (RealCampusData.placeNodeIds() + graph.nodes.filter { it.type == NodeType.ZONA_SEGURA }.map { it.id }).distinct()

    @Test fun `la heuristica Haversine es admisible en todas las aristas reales`() =
        assertTrue(graph.inadmissibleEdges().isEmpty(), "Aristas con distancia < línea recta: ${graph.inadmissibleEdges()}")

    @Test fun `desde cada puerta se llega a todos los lugares`() {
        assertTrue(entradas.isNotEmpty())
        for (o in entradas) for (d in destinos) {
            assertInstanceOf(RouteResult.Success::class.java, engine.findRoute(o, d), "$o -> $d")
        }
    }

    @Test fun `A estrella coincide con Dijkstra en todas las combinaciones puerta-lugar`() {
        for (avoid in listOf(false, true)) for (o in entradas) for (d in destinos) {
            val expected = referenceDistance(graph, o, d, avoid)
            val actual = engine.findRoute(o, d, RouteOptions(avoid))
            if (expected == null) assertInstanceOf(RouteResult.Failure::class.java, actual, "$o->$d avoid=$avoid")
            else assertEquals(expected, (actual as RouteResult.Success).distanceM, 1e-6, "$o->$d avoid=$avoid")
        }
    }

    @Test fun `con evitar escaleras nunca se devuelve una ruta con escaleras`() {
        for (o in entradas) for (d in destinos) {
            val r = engine.findRoute(o, d, RouteOptions(avoidStairs = true))
            if (r is RouteResult.Success) assertFalse(r.usesStairs, "$o->$d")
            else assertEquals(RouteResult.Reason.ONLY_PATH_HAS_STAIRS, (r as RouteResult.Failure).reason, "$o->$d")
        }
    }

    @Test fun `la ruta empieza y termina en los nodos pedidos y cada tramo existe en el grafo`() {
        val o = entradas.first(); val d = destinos.last()
        val r = engine.findRoute(o, d) as RouteResult.Success
        assertEquals(o, r.nodeIds.first()); assertEquals(d, r.nodeIds.last())
        r.nodeIds.zipWithNext().forEach { (a, b) ->
            assertTrue(graph.neighbors(a).any { it.toId == b }, "No existe arista $a->$b")
        }
    }

    @Test fun `el snap sobre un nodo de calle devuelve ese mismo nodo`() {
        val n = graph.nodes.first { it.type == NodeType.CONEXION }
        val s = engine.snapToNode(n.latitude, n.longitude)!!
        assertEquals(0.0, s.distanceM, 0.01)
    }

    @Test fun `desde cada puerta se puede evacuar a una zona segura valida`() {
        val safeZoneIds = graph.nodes.filter { it.type == NodeType.ZONA_SEGURA }.map { it.id }.toSet()
        assertTrue(safeZoneIds.isNotEmpty(), "Deben existir zonas seguras en el campus real")
        for (o in entradas) {
            val r = engine.findNearestSafeZone(o)
            val ok = assertInstanceOf(RouteResult.Success::class.java, r, "Fallo al evacuar desde puerta $o")
            assertTrue(ok.nodeIds.last() in safeZoneIds, "El destino final ${ok.nodeIds.last()} debe ser una zona segura")
        }
    }
}
