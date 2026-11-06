package ec.edu.uleam.navigator.data

import ec.edu.uleam.navigator.routing.astar.CampusGraph
import ec.edu.uleam.navigator.routing.astar.GraphEdge
import ec.edu.uleam.navigator.routing.astar.GraphNode
import ec.edu.uleam.navigator.routing.astar.NodeType

fun NodoEntity.toGraphNode() = GraphNode(id, latitud, longitud, NodeType.fromDb(tipo))

fun AristaEntity.toGraphEdge() = GraphEdge(
    fromId = idOrigen,
    toId = idDestino,
    distanceM = distanciaM,
    bidirectional = bidireccional == 1,
    hasStairs = tieneEscaleras == 1
)

fun buildCampusGraph(nodos: List<NodoEntity>, aristas: List<AristaEntity>) =
    CampusGraph(nodos.map { it.toGraphNode() }, aristas.map { it.toGraphEdge() })