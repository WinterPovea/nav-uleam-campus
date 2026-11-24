package ec.edu.uleam.navigator.ui.map

import ec.edu.uleam.navigator.routing.astar.CampusGraph
import ec.edu.uleam.navigator.routing.astar.NodeType
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Elige el nodo de origen para una posición GPS real.
 *
 * - Si el usuario está DENTRO de un edificio (huella de campus_shapes), sale por una de sus entradas
 *   (nodos DESTINO pegados al contorno), no por el camino más cercano al otro lado de la pared.
 * - Si está fuera, compara los nodos de calle cercanos y elige el que minimiza
 *   "caminar hasta el nodo + ruta desde el nodo", en vez de tomar siempre el más cercano.
 *
 * Es lógica pura (sin Android) para poder probarla en tests unitarios.
 */
object OriginPicker {
    /** Distancia máxima (m) entre una entrada (nodo DESTINO) y la huella del edificio al que pertenece. */
    const val ENTRANCE_TO_BUILDING_M = 12.0
    /** Margen (m) sobre el nodo de calle más cercano para considerar otros candidatos. */
    const val STREET_CANDIDATE_MARGIN_M = 30.0
    const val MAX_STREET_CANDIDATES = 8
    const val MAX_SNAP_M = 100.0

    private val BUILDING_TYPES = setOf("EDIFICIO", "OTRO", "COMIDA")

    /** @param building figura dentro de la cual está el usuario (null si está en exteriores). */
    data class Pick(val nodeId: Int, val walkM: Double, val building: CampusShape?)

    /**
     * @param routeCost distancia de la ruta desde un nodo candidato hasta el destino (null = sin ruta).
     *   Si no hay destino se pasa null y se elige el candidato más cercano.
     */
    fun pick(
        lat: Double,
        lon: Double,
        graph: CampusGraph,
        shapes: List<CampusShape>,
        routeCost: ((Int) -> Double?)? = null
    ): Pick? {
        if (!lat.isFinite() || !lon.isFinite()) return null
        val p = GeoPoint(lat, lon)
        fun walk(id: Int): Double = graph.node(id)?.let { distanceM(p, GeoPoint(it.latitude, it.longitude)) } ?: Double.MAX_VALUE
        fun best(ids: List<Int>): Int? {
            if (ids.isEmpty()) return null
            if (routeCost == null) return ids.minByOrNull(::walk)
            return ids.mapNotNull { id -> routeCost(id)?.let { id to walk(id) + it } }.minByOrNull { it.second }?.first
        }

        buildingAt(p, shapes)?.let { b ->
            val entrances = entrancesOf(b, graph, shapes)
            best(entrances)?.let { return Pick(it, walk(it), b) }
        }

        val street = graph.nodes.filter { it.type.isStreet }
            .map { it.id to distanceM(p, GeoPoint(it.latitude, it.longitude)) }
            .sortedBy { it.second }
        val nearest = street.firstOrNull() ?: return null
        if (nearest.second > MAX_SNAP_M) return null
        val candidates = street.takeWhile { it.second <= nearest.second + STREET_CANDIDATE_MARGIN_M }
            .take(MAX_STREET_CANDIDATES).map { it.first }
        val id = best(candidates) ?: nearest.first
        return Pick(id, walk(id), null)
    }

    /**
     * ¿El usuario ya está en el destino? Sí, si está a [radiusM] o menos del nodo del destino,
     * o dentro del edificio al que pertenece esa entrada.
     */
    fun isAtDestination(lat: Double, lon: Double, destLat: Double, destLon: Double, shapes: List<CampusShape>, radiusM: Double): Boolean {
        val p = GeoPoint(lat, lon)
        val d = GeoPoint(destLat, destLon)
        if (distanceM(p, d) <= radiusM) return true
        val b = buildingAt(p, shapes) ?: return false
        return distanceToPolygonM(d, b.puntos) <= ENTRANCE_TO_BUILDING_M
    }

    /** Edificio (huella) que contiene el punto, o null. */
    fun buildingAt(p: GeoPoint, shapes: List<CampusShape>): CampusShape? =
        shapes.firstOrNull { it.tipo in BUILDING_TYPES && it.puntos.size >= 3 && contains(it.puntos, p) }

    /**
     * Nodos DESTINO pegados al contorno del edificio: sus entradas. Una entrada pertenece solo al edificio
     * más cercano (p. ej. la del DIIT, a 3 m de B-21, no cuenta como puerta de B-21).
     */
    fun entrancesOf(b: CampusShape, graph: CampusGraph, shapes: List<CampusShape> = listOf(b)): List<Int> {
        val edificios = shapes.filter { it.tipo in BUILDING_TYPES && it.puntos.size >= 3 }
        return graph.nodes.filter {
            if (it.type != NodeType.DESTINO) return@filter false
            val g = GeoPoint(it.latitude, it.longitude)
            val d = distanceToPolygonM(g, b.puntos)
            d <= ENTRANCE_TO_BUILDING_M && edificios.none { o -> o !== b && o.codigo != b.codigo && distanceToPolygonM(g, o.puntos) < d }
        }.map { it.id }
    }

    /** Punto en polígono (ray casting) sobre (lon, lat). */
    fun contains(poly: List<GeoPoint>, p: GeoPoint): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.lat > p.lat) != (b.lat > p.lat) &&
                p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon
            ) inside = !inside
            j = i
        }
        return inside
    }

    /** Distancia (m) de un punto al contorno de un polígono (0 si está dentro). */
    fun distanceToPolygonM(p: GeoPoint, poly: List<GeoPoint>): Double {
        if (poly.size >= 3 && contains(poly, p)) return 0.0
        val kx = M_PER_DEG * cos(Math.toRadians(p.lat))
        var best = Double.MAX_VALUE
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[(i + 1) % poly.size]
            val ax = (a.lon - p.lon) * kx; val ay = (a.lat - p.lat) * M_PER_DEG
            val bx = (b.lon - p.lon) * kx; val by = (b.lat - p.lat) * M_PER_DEG
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 < 1e-9) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            best = minOf(best, hypot(ax + t * dx, ay + t * dy))
        }
        return best
    }

    private fun distanceM(a: GeoPoint, b: GeoPoint): Double = RouteProgressCalc.distanceM(a, b)

    private const val M_PER_DEG = 111_320.0
}
