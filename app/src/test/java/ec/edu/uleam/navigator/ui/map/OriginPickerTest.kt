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

    private fun llega(p: GeoPoint, dest: Int, r: Double = 8.0) = OriginPicker.isAtDestination(p.lat, p.lon, dest, graph, shapes, r)

    @Test
    fun llegadaPorCercaniaODentroDelEdificioDestino() {
        val dest = graph.node(216)!!    // entrada de B-16
        assertTrue(llega(dentroDe("B-16"), 216, 20.0))
        assertTrue(llega(GeoPoint(dest.latitude + 0.0001, dest.longitude), 216, 20.0)) // ~11 m
        val lejos = graph.node(222)!!   // B-22
        assertFalse(llega(GeoPoint(lejos.latitude, lejos.longitude), 216, 20.0))
    }

    @Test
    fun dentroDeTastyFood1NoEstaEnElVicerrectorado() {
        // La entrada del Vicerrectorado (317) queda a ~9 m de Tasty Food 1, pero pertenece a su propio edificio
        val poly = shapes.filter { it.codigo == "Tasty Food 1" }.maxBy { it.puntos.size }.puntos
        val c = GeoPoint(poly.map { it.lat }.average(), poly.map { it.lon }.average())
        assertTrue(OriginPicker.contains(poly, c))
        assertFalse(llega(c, 317))
        // Tasty Food 1 tiene dos entradas del mismo lugar: cualquiera cuenta como llegada
        val mismo = { a: Int, b: Int -> setOf(a, b) == setOf(305, 318) || a == b }
        assertTrue(OriginPicker.isAtDestination(c.lat, c.lon, 305, graph, shapes, 8.0, mismo))
        assertTrue(OriginPicker.isAtDestination(c.lat, c.lon, 318, graph, shapes, 8.0, mismo))
    }

    @Test
    fun dentroDeB05NoEstaEnElLaboratorioGastronomico() {
        val p = dentroDe("B-05")
        assertFalse(llega(p, 230))   // el laboratorio tiene su propia entrada: se traza la ruta
        assertTrue(llega(p, 205))
    }

    @Test
    fun mismoLugarConOtroAcceso() {
        assertTrue(OriginPicker.samePlaceName("Facultad X (Bloque B-13)", "Facultad X (Bloque B-13, acceso 2)"))
        assertTrue(OriginPicker.samePlaceName("Tasty Food 1 (entrada 1)", "Tasty Food 1 (entrada 2)"))
        assertFalse(OriginPicker.samePlaceName("Laboratorio gastronómico (Bloque B-05)", "Facultad de Educación (Bloque B-05)"))
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
