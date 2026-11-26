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

    /** Margen (m) sobre el camino más cercano para considerar otros caminos cercanos. */
    const val EDGE_CANDIDATE_MARGIN_M = 15.0

    /**
     * @param building figura dentro de la cual está el usuario (null si está en exteriores).
     * @param lead tramo inicial dibujado antes de la ruta: desde el punto del camino más cercano al usuario
     *   hasta [nodeId], siguiendo la forma del camino (vacío si la ruta parte de una entrada de edificio).
     */
    data class Pick(val nodeId: Int, val walkM: Double, val building: CampusShape?, val lead: List<GeoPoint> = emptyList()) {
        /** Largo (m) del tramo inicial sobre el camino. */
        val leadM: Double get() = (0 until lead.size - 1).sumOf { RouteProgressCalc.distanceM(lead[it], lead[it + 1]) }
    }

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

        // Exteriores: el usuario se proyecta sobre el camino (arista) más cercano y la ruta empieza en ese punto.
        // Se descartan caminos a los que solo se llega atravesando un edificio.
        val edgeShapes = EdgeShapes.from(shapes)
        val buildings = shapes.filter { it.tipo in BUILDING_TYPES && it.puntos.size >= 3 }
        data class Snap(val a: Int, val b: Int, val q: GeoPoint, val d: Double, val toA: List<GeoPoint>, val toB: List<GeoPoint>)
        val snaps = ArrayList<Snap>()
        for (u in graph.nodes) {
            if (!u.type.isStreet) continue
            for (nb in graph.neighbors(u.id)) {
                val v = graph.node(nb.toId) ?: continue
                if (!v.type.isStreet || v.id < u.id && graph.neighbors(v.id).any { it.toId == u.id }) continue
                val line = edgeShapes.between(u.id, v.id)
                    ?: listOf(GeoPoint(u.latitude, u.longitude), GeoPoint(v.latitude, v.longitude))
                val (q, idx) = nearestOnLine(p, line)
                val d = distanceM(p, q)
                if (d > MAX_SNAP_M) continue
                val toA = listOf(q) + line.subList(0, idx + 1).asReversed()
                val toB = listOf(q) + line.subList(idx + 1, line.size)
                snaps.add(Snap(u.id, v.id, q, d, toA, toB))
            }
        }
        if (snaps.isEmpty()) return null
        val libres = snaps.filter { s -> buildings.none { crosses(p, s.q, it.puntos) } }.ifEmpty { snaps }
        val dMin = libres.minOf { it.d }
        val cercanos = libres.filter { it.d <= dMin + EDGE_CANDIDATE_MARGIN_M }.sortedBy { it.d }.take(MAX_STREET_CANDIDATES)
        var best: Pick? = null
        var bestCost = Double.MAX_VALUE
        for (s in cercanos) {
            for ((id, lead) in listOf(s.a to s.toA, s.b to s.toB)) {
                val pick = Pick(id, s.d, null, lead)
                val rest = if (routeCost == null) 0.0 else routeCost(id) ?: continue
                val cost = s.d + pick.leadM + rest
                if (cost < bestCost) { bestCost = cost; best = pick }
            }
        }
        return best
    }

    /** Punto más cercano a [p] sobre la polilínea y el índice del segmento (line[i]..line[i+1]) en que cae. */
    fun nearestOnLine(p: GeoPoint, line: List<GeoPoint>): Pair<GeoPoint, Int> {
        val kx = M_PER_DEG * cos(Math.toRadians(p.lat))
        var best = line.first() to 0
        var bestD = Double.MAX_VALUE
        for (i in 0 until line.size - 1) {
            val a = line[i]; val b = line[i + 1]
            val ax = (a.lon - p.lon) * kx; val ay = (a.lat - p.lat) * M_PER_DEG
            val dx = (b.lon - a.lon) * kx; val dy = (b.lat - a.lat) * M_PER_DEG
            val len2 = dx * dx + dy * dy
            val t = if (len2 < 1e-9) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(ax + t * dx, ay + t * dy)
            if (d < bestD) {
                bestD = d
                best = GeoPoint(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon)) to i
            }
        }
        return best
    }

    /** ¿El segmento a–b atraviesa el contorno del polígono? */
    fun crosses(a: GeoPoint, b: GeoPoint, poly: List<GeoPoint>): Boolean {
        fun side(p: GeoPoint, q: GeoPoint, r: GeoPoint) =
            (q.lon - p.lon) * (r.lat - p.lat) - (q.lat - p.lat) * (r.lon - p.lon)
        for (i in poly.indices) {
            val c = poly[i]; val d = poly[(i + 1) % poly.size]
            val d1 = side(a, b, c); val d2 = side(a, b, d); val d3 = side(c, d, a); val d4 = side(c, d, b)
            if (d1 * d2 < 0 && d3 * d4 < 0) return true
        }
        return false
    }

    /**
     * ¿El usuario ya está en el destino [destId]?
     *
     * - Sí, si está a [radiusM] o menos de su nodo.
     * - Sí, si está DENTRO del edificio al que pertenece esa entrada (el edificio más cercano a ella, ver
     *   [entrancesOf]) y, de las entradas de ese edificio, la más cercana a él es la del mismo lugar
     *   ([samePlace]). Así, dentro de Tasty Food 1 no "llega" al Vicerrectorado (su entrada es de otro
     *   edificio), y dentro del ala oeste de B-05 no "llega" al Laboratorio gastronómico (que tiene su propia
     *   entrada al este): se le traza la ruta.
     *
     * @param samePlace ¿dos nodos de entrada son del mismo lugar? (p. ej. los dos accesos de B-13).
     */
    fun isAtDestination(
        lat: Double,
        lon: Double,
        destId: Int,
        graph: CampusGraph,
        shapes: List<CampusShape>,
        radiusM: Double,
        samePlace: (Int, Int) -> Boolean = { a, b -> a == b }
    ): Boolean {
        val p = GeoPoint(lat, lon)
        val dest = graph.node(destId) ?: return false
        if (distanceM(p, GeoPoint(dest.latitude, dest.longitude)) <= radiusM) return true
        val b = buildingAt(p, shapes) ?: return false
        val entradas = entrancesOf(b, graph, shapes)
        if (destId !in entradas) return false
        val masCercana = entradas.minBy { id -> graph.node(id)?.let { distanceM(p, GeoPoint(it.latitude, it.longitude)) } ?: Double.MAX_VALUE }
        return masCercana == destId || samePlace(masCercana, destId)
    }

    /** ¿Dos nombres de lugar son el mismo lugar con otro acceso? ("…(Bloque B-13, acceso 2)", "Tasty Food 1 (entrada 2)"). */
    fun samePlaceName(a: String, b: String): Boolean {
        fun base(n: String) = n.lowercase()
            .replace(Regex(",\\s*acceso\\s*\\d+"), "")
            .replace(Regex("\\s*\\(entrada\\s*\\d+\\)"), "")
            .trim()
        return base(a) == base(b)
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
