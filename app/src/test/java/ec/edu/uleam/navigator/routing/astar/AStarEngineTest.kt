package ec.edu.uleam.navigator.routing.astar

import ec.edu.uleam.navigator.routing.astar.RouteResult.Reason
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Pruebas con "datos falsos" (mock): no dependen de Room, de la UI ni de los CSV reales. */
class AStarEngineTest {

    private fun engine(nodes: List<GraphNode>, edges: (Map<Int, GraphNode>) -> List<GraphEdge>) =
        AStarEngine(CampusGraph(nodes, edges(nodes.associateBy { it.id })))

    private fun RouteResult.success() = assertInstanceOf(RouteResult.Success::class.java, this)
    private fun RouteResult.failure() = assertInstanceOf(RouteResult.Failure::class.java, this)

    @Test fun `ruta en linea recta devuelve nodos en orden`() {
        val e = engine(listOf(mockNode(1, 0.0, 0.0), mockNode(2, 50.0, 0.0), mockNode(3, 100.0, 0.0))) { g ->
            listOf(mockEdge(g, 1, 2), mockEdge(g, 2, 3))
        }
        val r = e.findRoute(1, 3).success()
        assertEquals(listOf(1, 2, 3), r.nodeIds)
        assertEquals(100.0, r.distanceM, 1.0)
        assertFalse(r.usesStairs)
        assertEquals(100.0 / 1.2 / 60.0, r.estimatedMinutes, 0.02)
    }

    @Test fun `elige el camino mas corto entre dos alternativas`() {
        val e = engine(
            listOf(mockNode(1, 0.0, 0.0), mockNode(2, 50.0, 30.0), mockNode(3, 50.0, 0.0), mockNode(4, 100.0, 0.0))
        ) { g ->
            listOf(
                mockEdge(g, 1, 2, 60.0), mockEdge(g, 2, 4, 60.0),   // por arriba: 120 m
                mockEdge(g, 1, 3), mockEdge(g, 3, 4)                // por abajo: ~100 m
            )
        }
        assertEquals(listOf(1, 3, 4), e.findRoute(1, 4).success().nodeIds)
    }

    @Test fun `origen igual a destino devuelve ruta de un solo nodo`() {
        val e = engine(listOf(mockNode(1, 0.0, 0.0))) { emptyList() }
        val r = e.findRoute(1, 1).success()
        assertEquals(listOf(1), r.nodeIds)
        assertEquals(0.0, r.distanceM, 1e-9)
    }

    @Test fun `ids inexistentes devuelven Failure con la razon correcta`() {
        val e = engine(listOf(mockNode(1, 0.0, 0.0), mockNode(2, 10.0, 0.0))) { listOf(mockEdge(it, 1, 2)) }
        assertEquals(Reason.UNKNOWN_ORIGIN, e.findRoute(99, 2).failure().reason)
        assertEquals(Reason.UNKNOWN_DESTINATION, e.findRoute(1, 99).failure().reason)
    }

    @Test fun `grafo desconectado devuelve NO_PATH`() {
        val e = engine(listOf(mockNode(1, 0.0, 0.0), mockNode(2, 500.0, 0.0))) { emptyList() }
        assertEquals(Reason.NO_PATH, e.findRoute(1, 2).failure().reason)
    }

    @Test fun `respeta aristas de un solo sentido`() {
        val e = engine(listOf(mockNode(1, 0.0, 0.0), mockNode(2, 50.0, 0.0))) {
            listOf(mockEdge(it, 1, 2, bidirectional = false))
        }
        e.findRoute(1, 2).success()
        assertEquals(Reason.NO_PATH, e.findRoute(2, 1).failure().reason)
    }

    // ---------- escaleras ----------
    private fun stairsGraph(withDetour: Boolean) = engine(
        listOf(mockNode(1, 0.0, 0.0), mockNode(2, 50.0, 0.0), mockNode(3, 100.0, 0.0), mockNode(4, 50.0, -40.0))
    ) { g ->
        buildList {
            add(mockEdge(g, 1, 2, stairs = true)); add(mockEdge(g, 2, 3))
            if (withDetour) { add(mockEdge(g, 1, 4, 65.0)); add(mockEdge(g, 4, 3, 65.0)) }
        }
    }

    @Test fun `sin evitar escaleras usa el atajo con escaleras`() {
        val r = stairsGraph(withDetour = true).findRoute(1, 3).success()
        assertEquals(listOf(1, 2, 3), r.nodeIds)
        assertTrue(r.usesStairs)
    }

    @Test fun `evitando escaleras toma el desvio aunque sea mas largo`() {
        val r = stairsGraph(withDetour = true).findRoute(1, 3, RouteOptions(avoidStairs = true)).success()
        assertEquals(listOf(1, 4, 3), r.nodeIds)
        assertEquals(130.0, r.distanceM, 1e-9)
        assertFalse(r.usesStairs)
    }

    @Test fun `si la unica ruta tiene escaleras se informa ONLY_PATH_HAS_STAIRS`() {
        val r = stairsGraph(withDetour = false).findRoute(1, 3, RouteOptions(avoidStairs = true)).failure()
        assertEquals(Reason.ONLY_PATH_HAS_STAIRS, r.reason)
    }

    // ---------- edificios (DESTINO) ----------
    private fun buildingGraph() = engine(
        listOf(
            mockNode(1, 0.0, 0.0), mockNode(2, 100.0, 0.0),
            mockNode(3, 50.0, 0.0, NodeType.DESTINO),      // edificio en medio (atajo prohibido)
            mockNode(4, 50.0, 60.0)                         // desvío por la calle
        )
    ) { g ->
        listOf(mockEdge(g, 1, 3), mockEdge(g, 3, 2), mockEdge(g, 1, 4, 80.0), mockEdge(g, 4, 2, 80.0))
    }

    @Test fun `no atraviesa un edificio ajeno como paso intermedio`() {
        assertEquals(listOf(1, 4, 2), buildingGraph().findRoute(1, 2).success().nodeIds)
    }

    @Test fun `puede terminar en un edificio y salir de un edificio`() {
        assertEquals(listOf(1, 3), buildingGraph().findRoute(1, 3).success().nodeIds)
        assertEquals(listOf(3, 2), buildingGraph().findRoute(3, 2).success().nodeIds)
    }

    // ---------- snap-to-node ----------
    private val snapEngine = engine(
        listOf(
            mockNode(1, 0.0, 0.0), mockNode(2, 200.0, 0.0),
            mockNode(3, 5.0, 5.0, NodeType.DESTINO),        // más cercano al punto, pero no es de calle
            mockNode(4, 100.0, 0.0, NodeType.ENTRADA)
        )
    ) { listOf(mockEdge(it, 1, 4), mockEdge(it, 4, 2)) }

    @Test fun `snap ignora nodos DESTINO y elige el nodo de calle mas cercano`() {
        val p = mockNode(0, 6.0, 6.0)
        val s = snapEngine.snapToNode(p.latitude, p.longitude)
        assertNotNull(s)
        assertEquals(1, s!!.nodeId)
        assertEquals(8.5, s.distanceM, 0.5)
    }

    @Test fun `snap devuelve null si el GPS esta a mas de 100 m de toda calle`() {
        val far = mockNode(0, 0.0, 500.0)
        assertNull(snapEngine.snapToNode(far.latitude, far.longitude))
    }

    @Test fun `snap devuelve null con coordenadas NaN`() = assertNull(snapEngine.snapToNode(Double.NaN, 0.0))

    @Test fun `findRouteFromLocation encadena snap y A estrella`() {
        val p = mockNode(0, 3.0, 3.0)
        val r = snapEngine.findRouteFromLocation(p.latitude, p.longitude, destinationId = 2).success()
        assertEquals(listOf(1, 4, 2), r.nodeIds)
    }

    @Test fun `findRouteFromLocation fuera del campus devuelve OUTSIDE_CAMPUS`() {
        val far = mockNode(0, 0.0, 900.0)
        assertEquals(Reason.OUTSIDE_CAMPUS, snapEngine.findRouteFromLocation(far.latitude, far.longitude, 2).failure().reason)
    }

    // ---------- validación del grafo ----------
    @Test fun `el grafo rechaza datos invalidos`() {
        val a = mockNode(1, 0.0, 0.0); val b = mockNode(2, 10.0, 0.0)
        assertThrows(IllegalArgumentException::class.java) { CampusGraph(listOf(a, a), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { CampusGraph(listOf(a), listOf(GraphEdge(1, 9, 5.0))) }
        assertThrows(IllegalArgumentException::class.java) { CampusGraph(listOf(a, b), listOf(GraphEdge(1, 2, -1.0))) }
    }

    @Test fun `detecta aristas que vuelven inadmisible la heuristica`() {
        val a = mockNode(1, 0.0, 0.0); val b = mockNode(2, 100.0, 0.0)
        val g = CampusGraph(listOf(a, b), listOf(GraphEdge(1, 2, 50.0)))   // 50 m < 100 m en línea recta
        assertEquals(1, g.inadmissibleEdges().size)
    }

    // ---------- propiedad: A* == Dijkstra ----------
    @Test fun `A estrella coincide con Dijkstra en grafos aleatorios`() {
        val rnd = Random(42)
        repeat(20) { round ->
            val nodes = (1..60).map { mockNode(it, rnd.nextDouble(0.0, 600.0), rnd.nextDouble(0.0, 600.0)) }
            val byId = nodes.associateBy { it.id }
            val edges = mutableListOf<GraphEdge>()
            for (n in nodes) {
                nodes.filter { it.id != n.id }.sortedBy { GeoMath.haversine(n, it) }.take(3).forEach { m ->
                    val d = GeoMath.haversine(n, m) * (1.0 + rnd.nextDouble(0.0, 0.3))
                    edges += GraphEdge(n.id, m.id, d, bidirectional = rnd.nextInt(4) != 0, hasStairs = rnd.nextInt(6) == 0)
                }
            }
            val graph = CampusGraph(byId.values, edges)
            val engine = AStarEngine(graph)
            repeat(40) {
                val from = rnd.nextInt(1, 61); val to = rnd.nextInt(1, 61)
                for (avoid in listOf(false, true)) {
                    val expected = referenceDistance(graph, from, to, avoid)
                    val actual = engine.findRoute(from, to, RouteOptions(avoid))
                    if (expected == null) {
                        assertInstanceOf(RouteResult.Failure::class.java, actual, "ronda $round $from->$to avoid=$avoid")
                    } else {
                        val ok = assertInstanceOf(RouteResult.Success::class.java, actual, "ronda $round $from->$to avoid=$avoid")
                        assertEquals(expected, ok.distanceM, 1e-6, "ronda $round $from->$to avoid=$avoid")
                        assertEquals(from, ok.nodeIds.first()); assertEquals(to, ok.nodeIds.last())
                    }
                }
            }
        }
    }

    // ---------- Safe Zone (Evacuación Rápida) ----------
    @Test fun `evacuacion rapida encuentra la zona segura mas cercana por distancia caminera`() {
        val e = engine(
            listOf(
                mockNode(1, 0.0, 0.0),                          // Origen
                mockNode(2, 40.0, 0.0),
                mockNode(10, 50.0, 0.0, NodeType.ZONA_SEGURA),  // ZS cercana (~50 m)
                mockNode(3, 0.0, 100.0),
                mockNode(20, 0.0, 150.0, NodeType.ZONA_SEGURA)  // ZS lejana (~150 m)
            )
        ) { g ->
            listOf(
                mockEdge(g, 1, 2), mockEdge(g, 2, 10),
                mockEdge(g, 1, 3), mockEdge(g, 3, 20)
            )
        }

        val r = e.findNearestSafeZone(1).success()
        assertEquals(listOf(1, 2, 10), r.nodeIds)
        assertEquals(10, r.nodeIds.last())
        assertEquals(50.0, r.distanceM, 1.0)
    }

    @Test fun `evacuacion rapida evita escaleras cuando se solicita`() {
        val e = engine(
            listOf(
                mockNode(1, 0.0, 0.0),
                mockNode(2, 30.0, 0.0),
                mockNode(10, 40.0, 0.0, NodeType.ZONA_SEGURA),  // Más cerca pero con escaleras
                mockNode(3, 0.0, 60.0),
                mockNode(20, 0.0, 80.0, NodeType.ZONA_SEGURA)   // Más lejos pero sin escaleras
            )
        ) { g ->
            listOf(
                mockEdge(g, 1, 2, stairs = true), mockEdge(g, 2, 10),
                mockEdge(g, 1, 3), mockEdge(g, 3, 20)
            )
        }

        // Sin evitar escaleras: va a la ZS 10
        val withStairs = e.findNearestSafeZone(1, RouteOptions(avoidStairs = false)).success()
        assertEquals(10, withStairs.nodeIds.last())
        assertTrue(withStairs.usesStairs)

        // Evitando escaleras: va a la ZS 20
        val withoutStairs = e.findNearestSafeZone(1, RouteOptions(avoidStairs = true)).success()
        assertEquals(20, withoutStairs.nodeIds.last())
        assertFalse(withoutStairs.usesStairs)
    }

    @Test fun `evacuacion rapida no atraviesa edificios ajenos`() {
        val e = engine(
            listOf(
                mockNode(1, 0.0, 0.0),
                mockNode(2, 40.0, 0.0, NodeType.DESTINO),      // Edificio en el camino directo
                mockNode(10, 80.0, 0.0, NodeType.ZONA_SEGURA),
                mockNode(3, 40.0, 40.0)                         // Desvío por la calle
            )
        ) { g ->
            listOf(
                mockEdge(g, 1, 2), mockEdge(g, 2, 10),
                mockEdge(g, 1, 3), mockEdge(g, 3, 10)
            )
        }

        val r = e.findNearestSafeZone(1).success()
        assertEquals(listOf(1, 3, 10), r.nodeIds)
    }

    // ---------- Detección de desvío (isOffRoute) ----------
    @Test fun `isOffRoute devuelve false cuando el usuario camina dentro del umbral de 15m`() {
        val e = engine(
            listOf(mockNode(1, 0.0, 0.0), mockNode(2, 100.0, 0.0), mockNode(3, 100.0, 100.0))
        ) { g ->
            listOf(mockEdge(g, 1, 2), mockEdge(g, 2, 3))
        }
        val onSecondSegment = mockNode(99, 108.0, 50.0) // A 8m del tramo (2 -> 3)
        assertFalse(e.isOffRoute(onSecondSegment.latitude, onSecondSegment.longitude, listOf(1, 2, 3)))
        assertEquals(8.0, e.minDistanceToRoute(onSecondSegment.latitude, onSecondSegment.longitude, listOf(1, 2, 3)), 0.5)
    }

    @Test fun `isOffRoute devuelve true cuando el usuario supera los 15m de todos los segmentos`() {
        val e = engine(
            listOf(mockNode(1, 0.0, 0.0), mockNode(2, 100.0, 0.0), mockNode(3, 100.0, 100.0))
        ) { g ->
            listOf(mockEdge(g, 1, 2), mockEdge(g, 2, 3))
        }
        val deviated = mockNode(99, 50.0, -22.0) // A 22m al sur del tramo (1 -> 2)
        assertTrue(e.isOffRoute(deviated.latitude, deviated.longitude, listOf(1, 2, 3)))
        assertEquals(22.0, e.minDistanceToRoute(deviated.latitude, deviated.longitude, listOf(1, 2, 3)), 0.5)
    }
}
