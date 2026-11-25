package ec.edu.uleam.navigator.ui

import ec.edu.uleam.navigator.data.LugarEntity

/** Chips de filtro rápido de la búsqueda. Lógica pura para poder probarla. */
object Categorias {
    /** (categoría en lugares.csv, etiqueta del chip). DEPORTE y CULTURA van juntos en "Deporte y cultura". */
    val CHIPS = listOf(
        "BLOQUE" to "Bloques",
        "COMIDA" to "Cafeterías",
        "ADMINISTRATIVO" to "Administrativos",
        "ZONA_SEGURA" to "Zonas seguras",
        "DEPORTE" to "Deporte y cultura"
    )

    fun filtrar(lugares: List<LugarEntity>, categoria: String): List<LugarEntity> {
        val cats = if (categoria == "DEPORTE") setOf("DEPORTE", "CULTURA") else setOf(categoria)
        return lugares.filter { it.categoria in cats }
            // Un bloque con dos accesos aparece una sola vez en la lista (el acceso principal).
            .filterNot { it.nombre.contains("acceso 2", ignoreCase = true) }
            .sortedBy { it.codigo?.substringAfter('·')?.ifBlank { null } ?: it.nombre }
    }
}

/** Lista de destinos recientes: el último primero, sin repetidos, como máximo [max]. */
object Recientes {
    fun agregar(actuales: List<Int>, nuevo: Int, max: Int = 5): List<Int> =
        (listOf(nuevo) + actuales.filter { it != nuevo }).take(max)
}
