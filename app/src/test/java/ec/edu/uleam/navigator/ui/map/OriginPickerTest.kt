package ec.edu.uleam.navigator.ui.map

import ec.edu.uleam.navigator.routing.astar.AStarEngine
import ec.edu.uleam.navigator.routing.astar.RealCampusData
import ec.edu.uleam.navigator.routing.astar.RouteOptions
import ec.edu.uleam.navigator.routing.astar.RouteResult
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OriginPickerTest {
    private val graph = RealCampusData.graph()
    private val astar = AStarEngine(graph)
    private val shapes: List<CampusShape> = run {
        val f = listOf("src/main/assets/campus_shapes.csv", "app/src/main/assets/campus_shapes.csv").map(::File).first { it.exists() }
        CampusShapesParser.parse(f.readLines(Charsets.UTF_8))
    }
    private fun cost(dest: Int): (Int) -> Double? =
        { id -> (astar.findRoute(id, dest, RouteOptions()) as? RouteResult.Success)?.distanceM }

    /** Punto interior de la huella más grande de un bloque (entre su centro y su vértice más al oeste). */
    private fun dentroDe(bloque: String): GeoPoint {
        val poly = shapes.filter { it.tipo == "EDIFICIO" && it.codigo == bloque }.maxBy { it.puntos.size }.puntos
        val c = GeoPoint(poly.map { it.lat }.average(), poly.map { it.lon }.average())
        val w = poly.minBy { it.lon }
        val p = GeoPoint(w.lat * 0.3 + c.lat * 0.7, w.lon * 0.3 + c.lon * 0.7)
        assertTrue(OriginPicker.contains(poly, p))
        return p
    }

    @Test
    fun dentroDeUnBloqueSaleSiemprePorSuEntrada() {
        val p = dentroDe("B-16")
        // B-16 tiene dos entradas (216 y 226): se usa la que deja la ruta más corta
        val esperado = mapOf(328 to 226, 315 to 216, 222 to 226)   // Auditorio, Agromarket, B-22
        for ((dest, entrada) in esperado) {
            val pick = OriginPicker.pick(p.lat, p.lon, graph, shapes, cost(dest))
            assertNotNull(pick)
            assertEquals(entrada, pick!!.nodeId, "destino $dest")
            assertEquals("B-16", pick.building?.codigo)
        }
    }

    @Test
    fun unaEntradaPerteneceSoloAlEdificioMasCercano() {
        val b21 = shapes.filter { it.tipo == "EDIFICIO" && it.codigo == "B-21" }.maxBy { it.puntos.size }
        val diit = shapes.first { it.codigo == "DIIT" }
        assertTrue(329 !in OriginPicker.entrancesOf(b21, graph, shapes))
        assertTrue(329 in OriginPicker.entrancesOf(diit, graph, shapes))
    }

    @Test
    fun fueraDeEdificiosEligeUnNodoDeCalleCercano() {
        val n = graph.node(474)!!
        val pick = OriginPicker.pick(n.latitude, n.longitude, graph, shapes, cost(328))!!
        assertEquals(null, pick.building)
        assertTrue(pick.walkM <= OriginPicker.STREET_CANDIDATE_MARGIN_M)
        assertTrue(graph.node(pick.nodeId)!!.type.isStreet)
    }

    @Test
    fun sinDestinoTomaElNodoMasCercano() {
        val n = graph.node(474)!!
        assertEquals(474, OriginPicker.pick(n.latitude, n.longitude, graph, shapes)!!.nodeId)
    }

    @Test
    fun lejosDelCampusNoHayOrigen() {
        assertEquals(null, OriginPicker.pick(-0.9300, -80.7300, graph, shapes))
    }

    @Test
    fun llegadaPorCercaniaODentroDelEdificioDestino() {
        val dest = graph.node(216)!!    // entrada de B-16
        val p = dentroDe("B-16")
        assertTrue(OriginPicker.isAtDestination(p.lat, p.lon, dest.latitude, dest.longitude, shapes, 20.0))
        assertTrue(OriginPicker.isAtDestination(dest.latitude + 0.0001, dest.longitude, dest.latitude, dest.longitude, shapes, 20.0)) // ~11 m
        val lejos = graph.node(222)!!   // B-22
        assertFalse(OriginPicker.isAtDestination(lejos.latitude, lejos.longitude, dest.latitude, dest.longitude, shapes, 20.0))
    }

    @Test
    fun dentroDeTastyFood1NoEstaEnElVicerrectorado() {
        // La entrada del Vicerrectorado (317) queda a ~9 m de Tasty Food 1, pero pertenece a su propio edificio
        val vice = graph.node(317)!!
        val poly = shapes.filter { it.codigo == "Tasty Food 1" }.maxBy { it.puntos.size }.puntos
        val c = GeoPoint(poly.map { it.lat }.average(), poly.map { it.lon }.average())
        assertTrue(OriginPicker.contains(poly, c))
        assertFalse(OriginPicker.isAtDestination(c.lat, c.lon, vice.latitude, vice.longitude, shapes, 8.0))
        val tasty = graph.node(305)!!
        assertTrue(OriginPicker.isAtDestination(c.lat, c.lon, tasty.latitude, tasty.longitude, shapes, 8.0))
    }

    @Test
    fun dentroDeB13HaciaPuerta3SalePorLaEntradaNorte() {
        val poly = shapes.filter { it.tipo == "EDIFICIO" && it.codigo == "B-13" }.maxBy { it.puntos.size }.puntos
        val c = GeoPoint(poly.map { it.lat }.average(), poly.map { it.lon }.average())
        assertEquals(314, OriginPicker.pick(c.lat, c.lon, graph, shapes, cost(102))!!.nodeId)
    }

    @Test
    fun fueraDeEdificiosLaRutaEmpiezaEnElCaminoJuntoAlUsuarioSinAtravesarEdificios() {
        // Justo al sur de B-06: el nodo 419 queda al otro lado del edificio y no debe elegirse
        val poly = shapes.filter { it.tipo == "EDIFICIO" && it.codigo == "B-06" }.maxBy { it.puntos.size }.puntos
        val sur = poly.minBy { it.lat }
        val p = GeoPoint(sur.lat - 4 / 111_320.0, sur.lon)
        val pick = OriginPicker.pick(p.lat, p.lon, graph, shapes, cost(208))!!
        assertTrue(pick.nodeId != 419, "no debe cruzar B-06")
        assertTrue(pick.lead.size >= 2)
        assertTrue(shapes.filter { it.tipo == "EDIFICIO" }.none { OriginPicker.crosses(p, pick.lead.first(), it.puntos) })
        val n = graph.node(pick.nodeId)!!
        assertTrue(RouteProgressCalc.distanceM(pick.lead.last(), GeoPoint(n.latitude, n.longitude)) < 0.5)
    }
}
