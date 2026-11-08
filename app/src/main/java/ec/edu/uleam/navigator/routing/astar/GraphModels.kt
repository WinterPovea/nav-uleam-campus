package ec.edu.uleam.navigator.routing.astar

/** Velocidad de caminata usada para estimar minutos (m/s). */
const val DEFAULT_WALKING_SPEED_MPS = 1.2

/** Tipos de nodo del grafo. Coinciden con la columna `tipo` de nodos.csv. */
enum class NodeType {
    CONEXION, ENTRADA, DESTINO, ZONA_SEGURA;

    /** Solo los nodos de calle pueden recibir la posición del GPS (snap-to-node). */
    val isStreet: Boolean get() = this == CONEXION || this == ENTRADA

    companion object {
        fun fromDb(value: String): NodeType = valueOf(value.trim().uppercase())
    }
}

/** Vértice del grafo (independiente de Room). */
data class GraphNode(
    val id: Int,
    val latitude: Double,
    val longitude: Double,
    val type: NodeType
)

/** Arista del grafo. Si [bidirectional] es false solo se recorre de [fromId] a [toId]. */
data class GraphEdge(
    val fromId: Int,
    val toId: Int,
    val distanceM: Double,
    val bidirectional: Boolean = true,
    val hasStairs: Boolean = false
)

data class RouteOptions(val avoidStairs: Boolean = false)

/** Nodo de calle más cercano al GPS y a cuántos metros está. */
data class SnapResult(val nodeId: Int, val distanceM: Double)

/** Resultado de un cálculo de ruta. "No hay ruta" no lanza excepción: devuelve [Failure]. */
sealed interface RouteResult {

    /**
     * @property nodeIds lista ORDENADA de ids, origen -> destino (ambos incluidos).
     * @property distanceM suma real de las aristas.
     * @property usesStairs true si algún tramo tiene escaleras.
     * @property nodesExplored nodos expandidos por A* (útil para el informe).
     */
    data class Success(
        val nodeIds: List<Int>,
        val distanceM: Double,
        val usesStairs: Boolean,
        val nodesExplored: Int
    ) : RouteResult {
        val estimatedMinutes: Double get() = distanceM / DEFAULT_WALKING_SPEED_MPS / 60.0
    }

    data class Failure(val reason: Reason) : RouteResult

    enum class Reason {
        UNKNOWN_ORIGIN,
        UNKNOWN_DESTINATION,
        OUTSIDE_CAMPUS,          // GPS a más de 100 m de cualquier nodo de calle
        NO_PATH,
        ONLY_PATH_HAS_STAIRS     // existe ruta, pero solo con escaleras y el usuario pidió evitarlas
    }
}