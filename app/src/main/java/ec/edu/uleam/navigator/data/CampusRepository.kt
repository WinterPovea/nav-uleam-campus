package ec.edu.uleam.navigator.data

import android.content.Context
import ec.edu.uleam.navigator.routing.RoutingEngine
import java.text.Normalizer

/** Carga el grafo completo UNA vez en memoria. */
class CampusRepository(private val ctx: Context) {
    lateinit var nodos: List<NodoEntity>; private set
    lateinit var aristas: List<AristaEntity>; private set
    lateinit var lugares: List<LugarEntity>; private set
    lateinit var engine: RoutingEngine; private set

    suspend fun load() {
        val dao = AppDatabase.get(ctx).dao()
        CsvSeeder.seedIfEmpty(ctx, dao)
        nodos = dao.nodos(); aristas = dao.aristas(); lugares = dao.lugares()
        engine = RoutingEngine(nodos, aristas)
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
