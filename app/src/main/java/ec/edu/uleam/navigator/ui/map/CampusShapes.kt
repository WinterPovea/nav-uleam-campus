package ec.edu.uleam.navigator.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Figura del mapa del campus (huella de un edificio, estadio, cancha, parqueo, punto de hidratación...).
 * Es solo diseño: no interviene en el grafo ni en la ruta. Se carga de assets/campus_shapes.csv.
 * @property tipo EDIFICIO, OTRO, PISTA, CANCHA_ESTADIO, GRADA, CANCHA, CANCHA_VERDE, COMIDA, PARQUEO o AGUA.
 * @property grupo Sigla de la facultad (FCS, FIIA, FACIVITEC, FETAH, FCACC, FCSDB) u OTRO.
 */
data class CampusShape(val tipo: String, val codigo: String, val grupo: String, val puntos: List<GeoPoint>)

object CampusShapesParser {
    /** Formato: `tipo,codigo,grupo,"lat lon lat lon ..."` con cabecera. Las líneas inválidas se ignoran. */
    fun parse(lines: List<String>): List<CampusShape> = lines.drop(1).mapNotNull { line ->
        val c = line.split(',', limit = 4)
        if (c.size < 4) return@mapNotNull null
        val n = c[3].trim().trim('"').split(' ').filter { it.isNotBlank() }.mapNotNull { it.toDoubleOrNull() }
        if (n.isEmpty() || n.size % 2 != 0) return@mapNotNull null
        CampusShape(c[0].trim(), c[1].trim(), c[2].trim(), n.chunked(2).map { GeoPoint(it[0], it[1]) })
    }
}

/** Colores por facultad (los de la leyenda del mapa físico). */
internal fun facultyColor(grupo: String): Color = when (grupo) {
    "FCS" -> Color(0xFF29B6F6)
    "FIIA" -> Color(0xFF1E40AF)
    "FACIVITEC" -> Color(0xFF2E9E4F)
    "FETAH" -> Color(0xFFF08A3C)
    "FCACC" -> Color(0xFF2563EB)
    "FCSDB" -> Color(0xFFB94A5A)
    else -> Color(0xFF8FA6AE)
}

private val OUTLINE = Color(0xFF33434A)

/** Dibuja las figuras bajo la red peatonal. Los puntos (parqueo, agua) solo con zoom suficiente. */
fun DrawScope.drawCampusShapes(p: MapProjection, shapes: List<CampusShape>, zoom: Float) {
    val order = listOf("PISTA", "CANCHA_ESTADIO", "GRADA", "CANCHA_VERDE", "CANCHA", "COMIDA", "OTRO", "EDIFICIO")
    for (tipo in order) {
        for (s in shapes) {
            if (s.tipo != tipo || s.puntos.size < 3) continue
            val path = Path()
            s.puntos.forEachIndexed { i, g ->
                val o = p.toScreen(g.lat, g.lon)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            path.close()
            val fill = when (tipo) {
                "PISTA" -> Color(0xFFCFE8D5)
                "CANCHA_ESTADIO", "CANCHA_VERDE" -> Color(0xFF7FC98F)
                "GRADA" -> Color(0xFFB8C4C8)
                "CANCHA" -> Color(0xFF8FD3F4)
                "COMIDA" -> Color(0xFFF6D58E)
                else -> facultyColor(if (tipo == "OTRO") "OTRO" else s.grupo)
            }
            drawPath(path, fill, style = Fill)
            drawPath(path, OUTLINE.copy(alpha = 0.55f), style = Stroke(width = 1.5f))
        }
    }
    if (zoom >= 1.6f) {
        for (s in shapes) {
            if (s.puntos.size != 1) continue
            val o: Offset = p.toScreen(s.puntos[0].lat, s.puntos[0].lon)
            when (s.tipo) {
                "PARQUEO" -> { drawCircle(Color(0xFF1565C0), 9f, o); drawCircle(Color.White, 9f, o, style = Stroke(2f)) }
                "AGUA" -> { drawCircle(Color(0xFF29B6F6), 7f, o); drawCircle(Color.White, 7f, o, style = Stroke(2f)) }
            }
        }
    }
}
