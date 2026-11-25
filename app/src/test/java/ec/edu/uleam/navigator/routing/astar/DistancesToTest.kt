package ec.edu.uleam.navigator.routing.astar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** El Dijkstra inverso (una pasada) debe dar exactamente las mismas distancias que un A* por cada origen. */
class DistancesToTest {
    private val graph = RealCampusData.graph()
    private val astar = AStarEngine(graph)

    private fun comparar(avoidStairs: Boolean) {
        val opts = RouteOptions(avoidStairs = avoidStairs)
        for (dest in RealCampusData.placeNodeIds().distinct()) {
            val dist = astar.distancesTo(dest, opts)
            for (o in graph.nodes) {
                val a = (astar.findRoute(o.id, dest, opts) as? RouteResult.Success)?.distanceM
                val r = dist[o.id]
                if (a == null) assertNull(r, "origen ${o.id} -> $dest")
                else assertEquals(a, r!!, 1e-6, "origen ${o.id} -> $dest")
            }
        }
    }

    @Test fun igualQueAStar() = comparar(avoidStairs = false)

    @Test fun igualQueAStarEvitandoEscaleras() = comparar(avoidStairs = true)

    @Test fun destinoDesconocidoDaMapaVacio() {
        assertEquals(emptyMap<Int, Double>(), astar.distancesTo(-1))
    }
}
