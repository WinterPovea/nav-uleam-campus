package ec.edu.uleam.navigator.data

import android.content.Context
import ec.edu.uleam.navigator.routing.astar.AStarEngine
import ec.edu.uleam.navigator.routing.astar.CampusGraph
import java.text.Normalizer

/** Carga el grafo completo UNA vez en memoria y gestiona el acceso a datos del campus. */
class CampusRepository(private val ctx: Context) {
    var nodos: List<NodoEntity> = emptyList()
        private set
    var aristas: List<AristaEntity> = emptyList()
        private set
    var lugares: List<LugarEntity> = emptyList()
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
        val g = buildCampusGraph(nodos, aristas)
        graph = g
        astar = AStarEngine(g)
        isLoaded = true
    }

    /** Busca en la columna `busqueda` (minúsculas, sin tildes). */
    fun buscar(texto: String): List<LugarEntity> {
        val q = normalizar(texto)
        if (q.isBlank()) return emptyList()
        return lugares.filter { l -> q.split(" ").all { it in l.busqueda } }
    }

    companion object {
        fun normalizar(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()
    }
}
