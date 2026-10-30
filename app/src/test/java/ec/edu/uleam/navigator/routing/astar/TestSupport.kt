package ec.edu.uleam.navigator.routing.astar

import java.io.File
import java.util.PriorityQueue

/** Crea un nodo a partir de metros (x = este, y = norte) respecto a un punto base del campus. */
fun mockNode(id: Int, xM: Double, yM: Double, type: NodeType = NodeType.CONEXION): GraphNode {
    val baseLat = -0.9546
    val baseLon = -80.7460
    val lat = baseLat + yM / 111_320.0
    val lon = baseLon + xM / (111_320.0 * Math.cos(Math.toRadians(baseLat)))
    return GraphNode(id, lat, lon, type)
}

/** Arista cuya distancia es la línea recta (o un valor explícito >= línea recta). */
fun mockEdge(
    graph: Map<Int, GraphNode>, a: Int, b: Int, distanceM: Double? = null,
    bidirectional: Boolean = true, stairs: Boolean = false
) = GraphEdge(
    a, b, distanceM ?: GeoMath.haversine(graph.getValue(a), graph.getValue(b)), bidirectional, stairs
)

/** Implementación de referencia (Dijkstra) con las MISMAS reglas que A*, para comparar resultados. */
fun referenceDistance(graph: CampusGraph, from: Int, to: Int, avoidStairs: Boolean): Double? {
    val dist = HashMap<Int, Double>().apply { put(from, 0.0) }
    val pq = PriorityQueue<Pair<Int, Double>>(compareBy { it.second })
    pq.add(from to 0.0)
    while (pq.isNotEmpty()) {
        val (u, d) = pq.poll()
        if (d > (dist[u] ?: Double.MAX_VALUE)) continue
        if (u == to) return d
        for (nb in graph.neighbors(u)) {
            if (avoidStairs && nb.hasStairs) continue
            if (nb.toId != to && graph.node(nb.toId)?.type == NodeType.DESTINO) continue
            val nd = d + nb.distanceM
            if (nd < (dist[nb.toId] ?: Double.MAX_VALUE)) { dist[nb.toId] = nd; pq.add(nb.toId to nd) }
        }
    }
    return null
}

/** Carga el grafo real desde los CSV de assets (los tests unitarios corren con directorio de trabajo = app/). */
object RealCampusData {
    private fun assetsDir(): File =
        listOf("src/main/assets", "app/src/main/assets").map(::File).first { it.resolve("nodos.csv").exists() }

    private fun rows(name: String): List<List<String>> =
        assetsDir().resolve(name).readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }.map(::splitCsv)

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                c == ',' && !q -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString()); return out
    }

    fun graph(): CampusGraph = CampusGraph(
        rows("nodos.csv").map { GraphNode(it[0].toInt(), it[1].toDouble(), it[2].toDouble(), NodeType.fromDb(it[3])) },
        rows("aristas.csv").map {
            GraphEdge(it[0].toInt(), it[1].toInt(), it[2].toDouble(), it[3].toInt() == 1, it[4].toInt() == 1)
        }
    )

    /** ids de nodo que son lugares buscables (columna id_nodo de lugares.csv). */
    fun placeNodeIds(): List<Int> = rows("lugares.csv").map { it[0].toInt() }
}
