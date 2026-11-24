package ec.edu.uleam.navigator.data

import android.content.Context
import androidx.room.withTransaction

/**
 * Carga los CSV de assets en Room.
 * - Nodos y aristas se cargan la primera vez y se VUELVEN A CARGAR cuando nodos.csv o aristas.csv
 *   cambian (huella guardada en SharedPreferences), así una actualización del grafo hecha en QGIS
 *   llega a las instalaciones existentes sin desinstalar la app.
 * - El catálogo de lugares se refresca en cada arranque.
 */
object CsvSeeder {
    private const val PREFS = "campus_db"
    private const val KEY_HUELLA = "huella_grafo"

    suspend fun seedIfEmpty(ctx: Context, dao: CampusDao) {
        val huella = huellaGrafo(ctx)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val vacia = dao.countNodos() == 0
        if (!vacia && prefs.getString(KEY_HUELLA, null) == huella) {
            refreshLugares(ctx, dao)
            return
        }
        val nodos = read(ctx, "nodos.csv").map {
            NodoEntity(it[0].toInt(), it[1].toDouble(), it[2].toDouble(), it[3], it[4])
        }
        val aristas = read(ctx, "aristas.csv").map {
            AristaEntity(
                idOrigen = it[0].toInt(), idDestino = it[1].toInt(), distanciaM = it[2].toDouble(),
                bidireccional = it[3].toInt(), tieneEscaleras = it[4].toInt(), nota = it[5].ifBlank { null }
            )
        }
        val lugares = readLugares(ctx)
        if (nodos.isEmpty() || aristas.isEmpty()) { // no vaciar la base si los assets no se pudieron leer
            if (!vacia) refreshLugares(ctx, dao)
            return
        }
        // Reemplazo completo en una transacción (orden por claves foráneas: lugares -> aristas -> nodos)
        AppDatabase.get(ctx).withTransaction {
            dao.clearLugares()
            dao.clearAristas()
            dao.clearNodos()
            dao.insertNodos(nodos)
            dao.insertAristas(aristas)
            dao.insertLugares(lugares)
        }
        prefs.edit().putString(KEY_HUELLA, huella).apply()
    }

    /** Huella del contenido de nodos.csv + aristas.csv: cambia cuando se regenera el grafo. */
    private fun huellaGrafo(ctx: Context): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        for (f in listOf("nodos.csv", "aristas.csv")) ctx.assets.open(f).use { md.update(it.readBytes()) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Reemplaza solo la tabla `lugares` con el contenido actual de lugares.csv. */
    private suspend fun refreshLugares(ctx: Context, dao: CampusDao) {
        val lugares = readLugares(ctx)
        if (lugares.isEmpty()) return // no vaciar el catálogo si el asset no se pudo leer
        dao.clearLugares()
        dao.insertLugares(lugares)
    }

    private fun readLugares(ctx: Context): List<LugarEntity> = read(ctx, "lugares.csv").map {
        LugarEntity(
            idNodo = it[0].toInt(), codigo = it[1].ifBlank { null }, nombre = it[2],
            descripcion = it[3].ifBlank { null }, categoria = it[4], etiquetas = it[5].ifBlank { null },
            busqueda = it[6], activo = it[7].toInt()
        )
    }

    private fun read(ctx: Context, file: String): List<List<String>> =
        ctx.assets.open(file).bufferedReader(Charsets.UTF_8).readLines().drop(1)
            .filter { it.isNotBlank() }.map(::splitCsv)

    /** Divide una línea CSV respetando comillas. */
    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                c == ',' && !q -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString()); return out
    }
}
