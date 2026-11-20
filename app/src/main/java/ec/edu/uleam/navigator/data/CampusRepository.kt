package ec.edu.uleam.navigator.data

import android.content.Context
import ec.edu.uleam.navigator.routing.astar.AStarEngine
import ec.edu.uleam.navigator.routing.astar.CampusGraph
import ec.edu.uleam.navigator.ui.map.CampusShape
import ec.edu.uleam.navigator.ui.map.CampusShapesParser
import java.text.Normalizer

/** Carga el grafo completo UNA vez en memoria y gestiona el acceso a datos del campus. */
class CampusRepository(private val ctx: Context) {
    var nodos: List<NodoEntity> = emptyList()
        private set
    var aristas: List<AristaEntity> = emptyList()
        private set
    var lugares: List<LugarEntity> = emptyList()
        private set
    var shapes: List<CampusShape> = emptyList()
        private set
    var graph: CampusGraph? = null
        private set
    var astar: AStarEngine? = null
        private set
    var isLoaded: Boolean = false
        private set

    suspend fun load() {
        val dao = AppDatabase.get(ctx).dao()
        CsvSeeder.seedIfEmpty(ctx, dao)
        nodos = dao.nodos()
        aristas = dao.aristas()
        lugares = dao.lugares()
        // Huellas del mapa (solo diseño): si faltara el archivo el mapa se dibuja como antes
        shapes = runCatching {
            ctx.assets.open("campus_shapes.csv").bufferedReader(Charsets.UTF_8).readLines()
                .let(CampusShapesParser::parse)
        }.getOrDefault(emptyList())
        val g = buildCampusGraph(nodos, aristas)
        graph = g
        astar = AStarEngine(g)
        isLoaded = true
    }

    /** Busca en la columna `busqueda` (minúsculas, sin tildes ni puntuación). */
    fun buscar(texto: String): List<LugarEntity> = filtrar(lugares, texto)

    companion object {
        private val DIACRITICS = Regex("\\p{Mn}+")
        private val NO_ALFANUM = Regex("[^a-z0-9\\- ]+")
        private val GUION_SUELTO = Regex("(?<![a-z0-9])-|-(?![a-z0-9])")
        private val ESPACIOS = Regex("\\s+")

        /** Minúsculas, sin tildes y sin signos de puntuación (conserva el guion de "b-01"). */
        fun normalizar(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(DIACRITICS, "")
                .replace(NO_ALFANUM, " ")
                .replace(GUION_SUELTO, " ")
                .replace(ESPACIOS, " ")
                .trim()

        /** Todos los términos de [texto] deben aparecer en `busqueda` (facultad, sigla, carrera o bloque). */
        fun filtrar(lugares: List<LugarEntity>, texto: String): List<LugarEntity> {
            val q = normalizar(texto)
            if (q.isBlank()) return emptyList()
            val terminos = q.split(" ")
            return lugares.filter { l ->
                val b = normalizar(l.busqueda)
                terminos.all { it in b }
            }
        }
    }
}
