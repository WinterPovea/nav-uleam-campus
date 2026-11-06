package ec.edu.uleam.navigator.routing

import ec.edu.uleam.navigator.data.*
import java.util.PriorityQueue
import kotlin.math.*

data class Route(val nodeIds: List<Int>, val distanceM: Double) {
    /** Caminata a 1,2 m/s (se calcula, no se guarda). */
    val minutes: Double get() = distanceM / 1.2 / 60.0
}

fun haversine(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
    val r = 6371000.0
    val p1 = Math.toRadians(la1); val p2 = Math.toRadians(la2)
    val dp = p2 - p1; val dl = Math.toRadians(lo2 - lo1)
    val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

/** Kotlin puro: recibe el grafo ya en memoria, no consulta SQL. */
class RoutingEngine(nodes: List<NodoEntity>, edges: List<AristaEntity>) {
    val nodes: Map<Int, NodoEntity> = nodes.associateBy { it.id }
    private class E(val to: Int, val d: Double, val stairs: Boolean)
    private val adj = HashMap<Int, MutableList<E>>()

    init {
        for (e in edges) {
            adj.getOrPut(e.idOrigen) { mutableListOf() }.add(E(e.idDestino, e.distanciaM, e.tieneEscaleras == 1))
            if (e.bidireccional == 1)
                adj.getOrPut(e.idDestino) { mutableListOf() }.add(E(e.idOrigen, e.distanciaM, e.tieneEscaleras == 1))
        }
    }

    /** El GPS se ajusta solo a nodos de calle (CONEXION o ENTRADA). Devuelve (idNodo, metros) o null si >100 m. */
    fun snapToStreet(lat: Double, lon: Double, maxM: Double = 100.0): Pair<Int, Double>? =
        nodes.values.filter { it.tipo == "CONEXION" || it.tipo == "ENTRADA" }
            .map { it.id to haversine(lat, lon, it.latitud, it.longitud) }
            .minByOrNull { it.second }?.takeIf { it.second <= maxM }

    /** A*: heurística = distancia en línea recta (admisible porque las aristas son Haversine). */
    fun aStar(from: Int, to: Int, avoidStairs: Boolean = false): Route? {
        val goal = nodes[to] ?: return null
        val g = HashMap<Int, Double>().apply { put(from, 0.0) }
        val prev = HashMap<Int, Int>()
        val open = PriorityQueue<Pair<Int, Double>>(compareBy { it.second })
        open.add(from to 0.0)
        val closed = HashSet<Int>()
        while (open.isNotEmpty()) {
            val (u, _) = open.poll()
            if (u == to) return build(prev, to, g[to]!!)
            if (!closed.add(u)) continue
            for (e in adj[u].orEmpty()) {
                if (avoidStairs && e.stairs) continue
                // no se cruza un DESTINO ajeno: los edificios son "hojas"
                if (e.to != to && nodes[e.to]?.tipo == "DESTINO") continue
                val ng = g[u]!! + e.d
                if (ng < (g[e.to] ?: Double.MAX_VALUE)) {
                    g[e.to] = ng; prev[e.to] = u
                    val n = nodes[e.to]!!
                    open.add(e.to to ng + haversine(n.latitud, n.longitud, goal.latitud, goal.longitud))
                }
            }
        }
        return null
    }

    /** Dijkstra hasta el primer nodo ZONA_SEGURA: la más cercana POR RUTA, no en línea recta. */
    fun nearestSafeZone(from: Int, avoidStairs: Boolean = false): Route? {
        val dist = HashMap<Int, Double>().apply { put(from, 0.0) }
        val prev = HashMap<Int, Int>()
        val pq = PriorityQueue<Pair<Int, Double>>(compareBy { it.second })
        pq.add(from to 0.0)
        val done = HashSet<Int>()
        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll()
            if (!done.add(u)) continue
            if (nodes[u]?.tipo == "ZONA_SEGURA") return build(prev, u, d)
            for (e in adj[u].orEmpty()) {
                if (avoidStairs && e.stairs) continue
                if (nodes[e.to]?.tipo == "DESTINO") continue
                val nd = d + e.d
                if (nd < (dist[e.to] ?: Double.MAX_VALUE)) { dist[e.to] = nd; prev[e.to] = u; pq.add(e.to to nd) }
            }
        }
        return null
    }

    private fun build(prev: Map<Int, Int>, end: Int, d: Double): Route {
        val path = ArrayDeque<Int>(); var c: Int? = end
        while (c != null) { path.addFirst(c); c = prev[c] }
        return Route(path.toList(), d)
    }
}
