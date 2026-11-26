package ec.edu.uleam.navigator.routing.astar

import ec.edu.uleam.navigator.data.CampusRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Pruebas con el grafo REAL del campus (archivos CSV de assets). Verifican los datos y el motor juntos. */
class RealCampusDataTest {
    private val graph = RealCampusData.graph()
    private val engine = AStarEngine(graph)
    private val entradas = graph.nodes.filter { it.type == NodeType.ENTRADA }.map { it.id }
    private val destinos = (RealCampusData.placeNodeIds() + graph.nodes.filter { it.type == NodeType.ZONA_SEGURA }.map { it.id }).distinct()

    @Test fun `la heuristica Haversine es admisible en todas las aristas reales`() =
        assertTrue(graph.inadmissibleEdges().isEmpty(), "Aristas con distancia < línea recta: ${graph.inadmissibleEdges()}")

    @Test fun `desde cada puerta se llega a todos los lugares`() {
        assertTrue(entradas.isNotEmpty())
        for (o in entradas) for (d in destinos) {
            assertInstanceOf(RouteResult.Success::class.java, engine.findRoute(o, d), "$o -> $d")
        }
    }

    @Test fun `A estrella coincide con Dijkstra en todas las combinaciones puerta-lugar`() {
        for (avoid in listOf(false, true)) for (o in entradas) for (d in destinos) {
            val expected = referenceDistance(graph, o, d, avoid)
            val actual = engine.findRoute(o, d, RouteOptions(avoid))
            if (expected == null) assertInstanceOf(RouteResult.Failure::class.java, actual, "$o->$d avoid=$avoid")
            else assertEquals(expected, (actual as RouteResult.Success).distanceM, 1e-6, "$o->$d avoid=$avoid")
        }
    }

    @Test fun `con evitar escaleras nunca se devuelve una ruta con escaleras`() {
        for (o in entradas) for (d in destinos) {
            val r = engine.findRoute(o, d, RouteOptions(avoidStairs = true))
            if (r is RouteResult.Success) assertFalse(r.usesStairs, "$o->$d")
            else assertEquals(RouteResult.Reason.ONLY_PATH_HAS_STAIRS, (r as RouteResult.Failure).reason, "$o->$d")
        }
    }

    @Test fun `la ruta empieza y termina en los nodos pedidos y cada tramo existe en el grafo`() {
        val o = entradas.first(); val d = destinos.last()
        val r = engine.findRoute(o, d) as RouteResult.Success
        assertEquals(o, r.nodeIds.first()); assertEquals(d, r.nodeIds.last())
        r.nodeIds.zipWithNext().forEach { (a, b) ->
            assertTrue(graph.neighbors(a).any { it.toId == b }, "No existe arista $a->$b")
        }
    }

    @Test fun `el snap sobre un nodo de calle devuelve ese mismo nodo`() {
        val n = graph.nodes.first { it.type == NodeType.CONEXION }
        val s = engine.snapToNode(n.latitude, n.longitude)!!
        assertEquals(0.0, s.distanceM, 0.01)
    }

    @Test fun `desde cada puerta se puede evacuar a una zona segura valida`() {
        val safeZoneIds = graph.nodes.filter { it.type == NodeType.ZONA_SEGURA }.map { it.id }.toSet()
        assertTrue(safeZoneIds.isNotEmpty(), "Deben existir zonas seguras en el campus real")
        for (o in entradas) {
            val r = engine.findNearestSafeZone(o)
            val ok = assertInstanceOf(RouteResult.Success::class.java, r, "Fallo al evacuar desde puerta $o")
            assertTrue(ok.nodeIds.last() in safeZoneIds, "El destino final ${ok.nodeIds.last()} debe ser una zona segura")
        }
    }
    // ---------- Catálogo de lugares: búsqueda por Facultad / Carrera / Sigla / Bloque ----------

    private val lugares = RealCampusData.lugares()

    private fun buscar(q: String) = CampusRepository.filtrar(lugares, q).map { it.idNodo }

    @Test fun `todos los lugares apuntan a un nodo existente y no hay id_nodo repetidos`() {
        val nodeIds = graph.nodes.map { it.id }.toSet()
        val ids = lugares.map { it.idNodo }
        assertTrue(nodeIds.containsAll(ids), "id_nodo sin nodo: ${ids.filter { it !in nodeIds }}")
        assertEquals(ids.size, ids.toSet().size, "id_nodo repetidos: ${ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys}")
    }

    @Test fun `la columna busqueda esta normalizada sin tildes ni puntuacion`() {
        lugares.forEach {
            assertEquals(CampusRepository.normalizar(it.busqueda), it.busqueda, "busqueda no normalizada en nodo ${it.idNodo}")
            assertEquals(it.busqueda, it.busqueda.lowercase(), "busqueda con mayusculas en nodo ${it.idNodo}")
        }
    }

    @Test fun `los 25 bloques B-01 a B-25 siguen siendo buscables por su codigo`() {
        for (n in 1..25) {
            val b = "b-%02d".format(n)
            assertTrue(buscar(b).isNotEmpty(), "Sin resultados para $b")
            assertTrue(buscar("bloque $b").isNotEmpty(), "Sin resultados para bloque $b")
        }
    }

    @Test fun `se encuentra el lugar por facultad, sigla, carrera y bloque`() {
        assertTrue(216 in buscar("Facultad de Ciencias de la Vida y Tecnologías"))
        assertTrue(216 in buscar("facultad ciencias tecnologia"))
        assertTrue(216 in buscar("FACIVITEC"))
        assertTrue(216 in buscar("Software"))
        assertTrue(216 in buscar("B-16"))
        assertTrue(204 in buscar("Odontología"))
        assertTrue(218 in buscar("Enfermería"))
        assertTrue(206 in buscar("Derecho"))
        assertTrue(209 in buscar("Ingeniería Industrial"))
        assertTrue(220 in buscar("psicologia"))   // Psicología está en el B-20 (el B-21 no está disponible por el momento)
        assertTrue(313 in buscar("estadio"))
        assertTrue(buscar("salud").containsAll(listOf(204, 218, 219, 220)))
    }

    @Test fun `los nombres mostrados llevan tildes pero la busqueda las ignora`() {
        val l = lugares.first { it.idNodo == 216 }
        assertTrue("Tecnologías" in l.nombre, l.nombre)
        assertTrue("Información" in l.descripcion!!, l.descripcion)
        assertTrue(216 in buscar("tecnologias") && 216 in buscar("Tecnologías") && 216 in buscar("INFORMACIÓN"))
        assertTrue(lugares.all { p -> p.busqueda.all { it.code < 128 } }, "busqueda con caracteres no ASCII")
        assertTrue(lugares.any { it.nombre.contains("Dirección") } && lugares.any { it.nombre.contains("Pacífico") })
    }

    @Test fun `Tasty Food 1 tiene dos entradas y El bosque es cafeteria`() {
        assertTrue(buscar("tasty food 1").containsAll(listOf(305, 318)))
        assertTrue(lugares.first { it.idNodo == 318 }.nombre.startsWith("Tasty Food 1"))
        assertEquals("COMIDA", lugares.first { it.idNodo == 312 }.categoria)
        assertTrue(312 in buscar("cafeteria") && 305 !in buscar("cafeteria"))   // Tasty Food es patio de comidas, no cafetería
        assertTrue(buscar("patio de comidas").containsAll(listOf(305, 318, 326, 327, 321, 230)))
    }

    @Test fun `numeracion real de los Tasty Food y carreras de B-20 y B-21`() {
        val nombre = { id: Int -> lugares.first { it.idNodo == id }.nombre }
        assertEquals("Tasty Food 2", nombre(321))   // junto al B-20
        assertEquals("Tasty Food 3", nombre(326))   // junto al B-08
        assertEquals("Tasty Food 4", nombre(327))   // junto a la Empresa pública
        assertTrue(220 in buscar("psicologia") && 221 !in buscar("psicologia"))
        assertTrue(221 in buscar("fisioterapia") && 220 !in buscar("fisioterapia"))
        assertTrue(221 in buscar("nutricion") && 221 in buscar("fonoaudiologia"))
    }

    @Test fun `la busqueda ignora puntuacion y mayusculas`() {
        assertEquals(buscar("fcs"), buscar("  (FCS), "))
        assertTrue(301 in buscar("(CIECP)"))
        assertTrue(buscar("zzzz inexistente").isEmpty())
    }

    @Test fun `cada resultado de busqueda por facultad tiene ruta optima desde todas las puertas`() {
        val ids = listOf("facivitec", "fcs", "fiia", "fcsdb", "fetah", "fcacc", "software", "odontologia").flatMap(::buscar).distinct()
        assertTrue(ids.isNotEmpty())
        for (d in ids) for (o in entradas) {
            val expected = referenceDistance(graph, o, d, false)
            val actual = engine.findRoute(o, d)
            if (expected == null) assertInstanceOf(RouteResult.Failure::class.java, actual, "$o->$d")
            else assertEquals(expected, (actual as RouteResult.Success).distanceM, 1e-6, "$o->$d")
        }
    }
}
