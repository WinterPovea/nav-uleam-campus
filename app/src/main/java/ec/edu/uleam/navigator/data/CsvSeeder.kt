package ec.edu.uleam.navigator.data

import android.content.Context

/**
 * Carga los CSV de assets en Room. Nodos y aristas solo la primera vez; el catálogo de
 * lugares se refresca en cada arranque para que las instalaciones previas reciban los
 * nombres de facultades, carreras y palabras clave más recientes de lugares.csv.
 */
object CsvSeeder {
    suspend fun seedIfEmpty(ctx: Context, dao: CampusDao) {
        if (dao.countNodos() > 0) {
            refreshLugares(ctx, dao)
            return
        }
        dao.insertNodos(read(ctx, "nodos.csv").map {
            NodoEntity(it[0].toInt(), it[1].toDouble(), it[2].toDouble(), it[3], it[4])
        })
        dao.insertAristas(read(ctx, "aristas.csv").map {
            AristaEntity(
                idOrigen = it[0].toInt(), idDestino = it[1].toInt(), distanciaM = it[2].toDouble(),
                bidireccional = it[3].toInt(), tieneEscaleras = it[4].toInt(), nota = it[5].ifBlank { null }
            )
        })
        dao.insertLugares(readLugares(ctx))
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
