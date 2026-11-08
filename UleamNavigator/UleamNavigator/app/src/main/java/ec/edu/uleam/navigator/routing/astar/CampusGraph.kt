package ec.edu.uleam.navigator.routing.astar

/**
 * Grafo en memoria G=(V,E) con lista de adyacencia. Valida los datos al construirse
 * (falla rápido con IllegalArgumentException si la BD o el mock tienen errores).
 */
class CampusGraph(nodes: Collection<GraphNode>, edges: Collection<GraphEdge>) {

    /** Arista dirigida ya expandida (una bidireccional genera dos Neighbor). */
    data class Neighbor(val toId: Int, val distanceM: Double, val hasStairs: Boolean)

    private val nodeById = HashMap<Int, GraphNode>(nodes.size * 2)
    private val adjacency = HashMap<Int, MutableList<Neighbor>>()

    val edges: List<GraphEdge> = edges.toList()
    val nodes: Collection<GraphNode> get() = nodeById.values

    init {
        for (n in nodes) {
            require(nodeById.put(n.id, n) == null) { "Nodo duplicado: ${n.id}" }
            require(n.latitude.isFinite() && n.longitude.isFinite()) { "Coordenadas inválidas en nodo ${n.id}" }
        }
        for (e in this.edges) {
            require(e.fromId in nodeById) { "Arista con origen inexistente: ${e.fromId}" }
            require(e.toId in nodeById) { "Arista con destino inexistente: ${e.toId}" }
            require(e.distanceM.isFinite() && e.distanceM >= 0.0) {
                "Distancia inválida (${e.distanceM}) en arista ${e.fromId}->${e.toId}"
            }
            adjacency.getOrPut(e.fromId) { ArrayList() }.add(Neighbor(e.toId, e.distanceM, e.hasStairs))
            if (e.bidirectional) {
                adjacency.getOrPut(e.toId) { ArrayList() }.add(Neighbor(e.fromId, e.distanceM, e.hasStairs))
            }
        }
    }

    fun node(id: Int): GraphNode? = nodeById[id]

    fun neighbors(id: Int): List<Neighbor> = adjacency[id] ?: emptyList()

    /**
     * Aristas cuya distancia es MENOR que la línea recta entre sus extremos. Si existe alguna,
     * la heurística deja de ser admisible y A* podría devolver rutas no óptimas.
     */
    fun inadmissibleEdges(toleranceM: Double = 0.5): List<GraphEdge> =
        edges.filter { e ->
            GeoMath.haversine(nodeById.getValue(e.fromId), nodeById.getValue(e.toId)) > e.distanceM + toleranceM
        }
}