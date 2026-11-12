package ec.edu.uleam.navigator.routing.astar

import java.util.PriorityQueue

/**
 * Motor de ruteo A* — Kotlin puro (sin Android, sin Room, sin UI).
 *
 *   f(n) = g(n) + h(n)
 *   g(n): distancia real acumulada por las aristas.
 *   h(n): distancia Haversine en línea recta hasta el destino.
 *
 * Reglas:
 *  - Los nodos DESTINO (edificios) son "hojas": se puede salir si es el origen y entrar
 *    si es el destino, pero no se atraviesan como paso intermedio.
 *  - Con avoidStairs se descartan las aristas con escaleras.
 *  - No guarda estado entre llamadas (thread-safe).
 */
class AStarEngine(private val graph: CampusGraph) {

    private class QueueEntry(val nodeId: Int, val g: Double, val f: Double)
    private class Step(val fromId: Int, val hasStairs: Boolean)

    /** Ruta óptima entre dos ids de nodo. */
    fun findRoute(originId: Int, destinationId: Int, options: RouteOptions = RouteOptions()): RouteResult {
        val origin = graph.node(originId) ?: return RouteResult.Failure(RouteResult.Reason.UNKNOWN_ORIGIN)
        val goal = graph.node(destinationId) ?: return RouteResult.Failure(RouteResult.Reason.UNKNOWN_DESTINATION)

        search(origin, goal, options.avoidStairs)?.let { return it }

        // Distingue "no existe ruta" de "existe pero solo con escaleras" para que la UI pueda explicarlo.
        val reason = if (options.avoidStairs && search(origin, goal, avoidStairs = false) != null)
            RouteResult.Reason.ONLY_PATH_HAS_STAIRS
        else
            RouteResult.Reason.NO_PATH
        return RouteResult.Failure(reason)
    }

    /** Flujo real: coordenada GPS cruda -> snap-to-node -> A*. */
    fun findRouteFromLocation(
        latitude: Double,
        longitude: Double,
        destinationId: Int,
        options: RouteOptions = RouteOptions(),
        maxSnapDistanceM: Double = DEFAULT_MAX_SNAP_M
    ): RouteResult {
        val snap = snapToNode(latitude, longitude, maxSnapDistanceM)
            ?: return RouteResult.Failure(RouteResult.Reason.OUTSIDE_CAMPUS)
        return findRoute(snap.nodeId, destinationId, options)
    }

    /** Ruta de evacuación rápida hacia la zona segura más cercana por red caminera (Dijkstra multi-objetivo). */
    fun findNearestSafeZone(originId: Int, options: RouteOptions = RouteOptions()): RouteResult {
        val origin = graph.node(originId) ?: return RouteResult.Failure(RouteResult.Reason.UNKNOWN_ORIGIN)
        if (graph.nodes.none { it.type == NodeType.ZONA_SEGURA }) {
            return RouteResult.Failure(RouteResult.Reason.NO_PATH)
        }

        searchNearestSafeZone(origin, options.avoidStairs)?.let { return it }

        val reason = if (options.avoidStairs && searchNearestSafeZone(origin, avoidStairs = false) != null)
            RouteResult.Reason.ONLY_PATH_HAS_STAIRS
        else
            RouteResult.Reason.NO_PATH
        return RouteResult.Failure(reason)
    }

    /** Evacuación rápida hacia zona segura desde coordenada GPS cruda. */
    fun findNearestSafeZoneFromLocation(
        latitude: Double,
        longitude: Double,
        options: RouteOptions = RouteOptions(),
        maxSnapDistanceM: Double = DEFAULT_MAX_SNAP_M
    ): RouteResult {
        val snap = snapToNode(latitude, longitude, maxSnapDistanceM)
            ?: return RouteResult.Failure(RouteResult.Reason.OUTSIDE_CAMPUS)
        return findNearestSafeZone(snap.nodeId, options)
    }

    /** Nodo de calle (CONEXION o ENTRADA) más cercano. Null si la coordenada es inválida o está muy lejos. */
    fun snapToNode(latitude: Double, longitude: Double, maxDistanceM: Double = DEFAULT_MAX_SNAP_M): SnapResult? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        var best: SnapResult? = null
        for (n in graph.nodes) {
            if (!n.type.isStreet) continue
            val d = GeoMath.haversine(latitude, longitude, n.latitude, n.longitude)
            if (best == null || d < best.distanceM) best = SnapResult(n.id, d)
        }
        return best?.takeIf { it.distanceM <= maxDistanceM }
    }

    private fun search(origin: GraphNode, goal: GraphNode, avoidStairs: Boolean): RouteResult.Success? {
        val open = PriorityQueue<QueueEntry>(compareBy<QueueEntry> { it.f }.thenByDescending { it.g })
        val bestG = HashMap<Int, Double>()
        val cameFrom = HashMap<Int, Step>()

        bestG[origin.id] = 0.0
        open.add(QueueEntry(origin.id, 0.0, GeoMath.haversine(origin, goal)))
        var explored = 0

        while (open.isNotEmpty()) {
            val cur = open.poll()!!
            // Entrada obsoleta: ya se encontró un camino mejor a este nodo.
            if (cur.g > (bestG[cur.nodeId] ?: Double.MAX_VALUE)) continue
            explored++

            if (cur.nodeId == goal.id) return buildResult(origin.id, goal.id, cur.g, cameFrom, explored)

            for (nb in graph.neighbors(cur.nodeId)) {
                if (avoidStairs && nb.hasStairs) continue
                val next = graph.node(nb.toId) ?: continue
                if (next.id != goal.id && next.type == NodeType.DESTINO) continue

                val ng = cur.g + nb.distanceM
                if (ng < (bestG[next.id] ?: Double.MAX_VALUE)) {
                    bestG[next.id] = ng
                    cameFrom[next.id] = Step(cur.nodeId, nb.hasStairs)
                    open.add(QueueEntry(next.id, ng, ng + GeoMath.haversine(next, goal)))
                }
            }
        }
        return null
    }

    private fun searchNearestSafeZone(origin: GraphNode, avoidStairs: Boolean): RouteResult.Success? {
        if (origin.type == NodeType.ZONA_SEGURA) {
            return RouteResult.Success(listOf(origin.id), 0.0, false, 0)
        }

        val open = PriorityQueue<QueueEntry>(compareBy<QueueEntry> { it.g })
        val bestG = HashMap<Int, Double>()
        val cameFrom = HashMap<Int, Step>()

        bestG[origin.id] = 0.0
        open.add(QueueEntry(origin.id, 0.0, 0.0))
        var explored = 0

        while (open.isNotEmpty()) {
            val cur = open.poll()!!
            if (cur.g > (bestG[cur.nodeId] ?: Double.MAX_VALUE)) continue
            explored++

            val node = graph.node(cur.nodeId) ?: continue
            if (node.type == NodeType.ZONA_SEGURA) {
                return buildResult(origin.id, cur.nodeId, cur.g, cameFrom, explored)
            }

            for (nb in graph.neighbors(cur.nodeId)) {
                if (avoidStairs && nb.hasStairs) continue
                val next = graph.node(nb.toId) ?: continue
                if (next.type == NodeType.DESTINO) continue

                val ng = cur.g + nb.distanceM
                if (ng < (bestG[next.id] ?: Double.MAX_VALUE)) {
                    bestG[next.id] = ng
                    cameFrom[next.id] = Step(cur.nodeId, nb.hasStairs)
                    open.add(QueueEntry(next.id, ng, ng))
                }
            }
        }
        return null
    }

    private fun buildResult(
        originId: Int, goalId: Int, distance: Double,
        cameFrom: Map<Int, Step>, explored: Int
    ): RouteResult.Success {
        val path = ArrayList<Int>()
        var usesStairs = false
        var current = goalId
        path.add(current)
        while (current != originId) {
            val step = cameFrom.getValue(current)
            if (step.hasStairs) usesStairs = true
            current = step.fromId
            path.add(current)
        }
        path.reverse()
        return RouteResult.Success(path, distance, usesStairs, explored)
    }

    companion object {
        const val DEFAULT_MAX_SNAP_M = 100.0
    }
}