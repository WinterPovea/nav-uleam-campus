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

    @Test
    fun envolventeConvexaIgnoraPuntosInteriores() {
        val cuadrado = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(1.0, 1.0), GeoPoint(1.0, 0.0))
        val hull = CampusShapesGeometry.convexHull(cuadrado + GeoPoint(0.5, 0.5) + GeoPoint(0.2, 0.7))
        assertEquals(4, hull.size)
        assertTrue(CampusShapesGeometry.insideConvex(hull, GeoPoint(0.5, 0.5)))
        assertTrue(!CampusShapesGeometry.insideConvex(hull, GeoPoint(1.5, 0.5)))
    }

    @Test
    fun arbolesSonDeterministasYEstanDentroDelTerreno() {
        val ground = CampusShapesGeometry.ground(realShapes())
        val otra = CampusShapesGeometry.ground(realShapes())
        assertEquals(ground.trees, otra.trees)
        assertTrue(ground.trees.size > 100)
        assertTrue(ground.trees.all { CampusShapesGeometry.insideConvex(ground.hull, GeoPoint(it.lat, it.lon)) })
    }

    @Test
    fun unaEtiquetaPorBloqueYEstadio() {
        val labels = CampusShapesGeometry.labels(realShapes())
        val bloques = labels.filter { it.tipo == "EDIFICIO" }.map { it.texto }
        assertEquals(bloques.size, bloques.toSet().size, "etiquetas de bloque repetidas")
        assertTrue("B-04" in bloques && "B-25" in bloques)
        assertTrue(labels.any { it.tipo == "ESTADIO" })
    }

    @Test
    fun viasConAnchoValidoYSoloSeRotulanLosBloquesDelCatalogo() {
        val shapes = realShapes()
        assertTrue(shapes.filter { it.tipo == "VIA" }.all { (it.codigo.toFloatOrNull() ?: 0f) in 2f..20f })
        val etiquetas = CampusShapesGeometry.labels(shapes).filter { it.tipo == "EDIFICIO" }.map { it.texto }
        assertTrue(etiquetas.all { Regex("B-\\d{2}").matches(it) }, "etiquetas fuera del catálogo: $etiquetas")
    }

    @Test
    fun laHuellaDeCadaBloqueTocaElNodoDeSuEntrada() {
        val shapes = realShapes().filter { it.tipo == "EDIFICIO" }
        val g = RealCampusData.graph()
        // el nodo 230 es el laboratorio gastronómico (J-10), que lleva el código B-05 pero no es la entrada del bloque
        for (l in RealCampusData.lugares().filter { it.codigo?.contains("B-") == true && it.idNodo != 230 }) {
            val b = Regex("B-\\d+").find(l.codigo!!)!!.value
            val n = g.node(l.idNodo)!!
            val d = shapes.filter { it.codigo == b }.minOf { s ->
                s.puntos.indices.minOf { i ->
                    val a = s.puntos[i]; val c = s.puntos[(i + 1) % s.puntos.size]
                    distanciaASegmentoM(GeoPoint(n.latitude, n.longitude), a, c)
                }
            }
            assertTrue(d < 12.0, "$b: su entrada está a ${d.toInt()} m de la huella")
        }
    }

    private fun distanciaASegmentoM(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val kx = 111_320.0 * Math.cos(Math.toRadians(p.lat))
        val px = (p.lon - a.lon) * kx; val py = (p.lat - a.lat) * 111_320.0
        val bx = (b.lon - a.lon) * kx; val by = (b.lat - a.lat) * 111_320.0
        val t = ((px * bx + py * by) / (bx * bx + by * by).coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
        return Math.hypot(px - t * bx, py - t * by)
    }

    @Test
    fun caminosVisiblesOcultanAccesosFinalesYTramosMarcados() {
        assertTrue(PathVisibility.visible(400, 401, "CONEXION", "CONEXION", false))
        assertTrue(!PathVisibility.visible(201, 485, "DESTINO", "CONEXION", false))
        assertTrue(!PathVisibility.visible(500, 400, "ZONA_SEGURA", "CONEXION", false))
        assertTrue(PathVisibility.visible(309, 490, "DESTINO", "CONEXION", true))    // escaleras siempre
        assertTrue(!PathVisibility.visible(442, 437, "CONEXION", "CONEXION", false)) // en cualquier sentido
    }
}
