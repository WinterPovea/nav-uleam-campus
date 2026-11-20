package ec.edu.uleam.navigator.ui.map

import ec.edu.uleam.navigator.routing.astar.RealCampusData
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CampusShapesTest {
    private fun realShapes(): List<CampusShape> {
        val f = listOf("src/main/assets", "app/src/main/assets").map(::File)
            .first { it.resolve("campus_shapes.csv").exists() }.resolve("campus_shapes.csv")
        return CampusShapesParser.parse(f.readLines(Charsets.UTF_8))
    }

    @Test
    fun parseaFigurasYDescartaLineasInvalidas() {
        val lines = listOf(
            "tipo,codigo,grupo,puntos",
            "EDIFICIO,B-01,FETAH,-0.954 -80.745 -0.955 -80.746 -0.953 -80.746",
            "AGUA,Agua,OTRO,-0.95 -80.74",
            "EDIFICIO,B-99,FCS,-0.95 -80.74 -0.95",   // número impar de valores
            "basura"
        )
        val r = CampusShapesParser.parse(lines)
        assertEquals(2, r.size)
        assertEquals(3, r[0].puntos.size)
        assertEquals(GeoPoint(-0.954, -80.745), r[0].puntos[0])
        assertEquals("AGUA", r[1].tipo)
    }

    @Test
    fun cadaBloqueDelCatalogoTieneHuellaCercaDeSuEntrada() {
        val shapes = realShapes().filter { it.tipo == "EDIFICIO" }
        val g = RealCampusData.graph()
        val bloques = RealCampusData.lugares().filter { it.codigo?.contains("B-") == true }
        for (l in bloques) {
            val b = Regex("B-\\d+").find(l.codigo!!)!!.value
            val huellas = shapes.filter { it.codigo == b }
            assertTrue(huellas.isNotEmpty(), "Sin huella para $b")
            val n = g.node(l.idNodo)!!
            val cerca = huellas.minOf { s ->
                val la = s.puntos.map { it.lat }.average(); val lo = s.puntos.map { it.lon }.average()
                RouteProgressCalc.distanceM(GeoPoint(la, lo), GeoPoint(n.latitude, n.longitude))
            }
            assertTrue(cerca < 70.0, "$b: la huella está a ${cerca.toInt()} m de su entrada")
        }
    }

    @Test
    fun todasLasFigurasEstanDentroDelCampusAmpliado() {
        val g = RealCampusData.graph()
        val ids = RealCampusData.placeNodeIds().mapNotNull { g.node(it) }
        val minLat = ids.minOf { it.latitude } - 0.0015; val maxLat = ids.maxOf { it.latitude } + 0.0015
        val minLon = ids.minOf { it.longitude } - 0.0015; val maxLon = ids.maxOf { it.longitude } + 0.0015
        for (s in realShapes()) for (p in s.puntos) {
            assertTrue(p.lat in minLat..maxLat && p.lon in minLon..maxLon, "${s.codigo} fuera del campus")
        }
    }
}
