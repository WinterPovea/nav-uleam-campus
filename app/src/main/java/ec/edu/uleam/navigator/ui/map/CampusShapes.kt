package ec.edu.uleam.navigator.ui.map

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.Typeface

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
    else -> Color(0xFF5E8A8F)
}

/** Paleta "mapa físico": exterior oscuro, terreno del campus verdoso y vías claras. */
object MapTheme {
    val OUTSIDE = Color(0xFF2B3335)
    val GROUND = Color(0xFF4C6F68)
    val ROAD = Color(0xFF9DB7B3)
    val ROAD_CASING = Color(0xFF3A5450)
    val ARROW_YELLOW = Color(0xFFFFD21F)
}

/** Etiqueta que se dibuja sobre una figura (código de bloque, sigla de edificio, "Estadio"...). */
data class ShapeLabel(val texto: String, val tipo: String, val centro: GeoPoint)

/** Árbol decorativo (solo diseño): posición, radio en metros y tono (0..2). */
data class Tree(val lat: Double, val lon: Double, val radioM: Float, val tono: Int)

/** Terreno del campus: contorno convexo de todas las figuras + árboles decorativos deterministas. */
data class CampusGround(val hull: List<GeoPoint>, val trees: List<Tree>)

object CampusShapesGeometry {
    private fun cross(o: GeoPoint, a: GeoPoint, b: GeoPoint) =
        (a.lon - o.lon) * (b.lat - o.lat) - (a.lat - o.lat) * (b.lon - o.lon)

    /** Envolvente convexa (cadena monótona de Andrew), en sentido antihorario sobre (lon, lat). */
    fun convexHull(points: List<GeoPoint>): List<GeoPoint> {
        val pts = points.distinct().sortedWith(compareBy({ it.lon }, { it.lat }))
        if (pts.size < 3) return pts
        val lower = ArrayList<GeoPoint>()
        for (p in pts) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) lower.removeAt(lower.size - 1)
            lower.add(p)
        }
        val upper = ArrayList<GeoPoint>()
        for (p in pts.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) upper.removeAt(upper.size - 1)
            upper.add(p)
        }
        lower.removeAt(lower.size - 1); upper.removeAt(upper.size - 1)
        return lower + upper
    }

    /** ¿El punto está dentro (o en el borde) de un polígono convexo antihorario? */
    fun insideConvex(hull: List<GeoPoint>, p: GeoPoint): Boolean {
        if (hull.size < 3) return false
        for (i in hull.indices) if (cross(hull[i], hull[(i + 1) % hull.size], p) < 0) return false
        return true
    }

    /** Árboles repartidos con un generador congruencial fijo: el mapa se ve igual en cada arranque. */
    fun trees(hull: List<GeoPoint>, count: Int = 420, seed: Long = 20260518L): List<Tree> {
        if (hull.size < 3) return emptyList()
        val minLat = hull.minOf { it.lat }; val maxLat = hull.maxOf { it.lat }
        val minLon = hull.minOf { it.lon }; val maxLon = hull.maxOf { it.lon }
        var st = seed
        fun next(): Double { st = (st * 6364136223846793005L + 1442695040888963407L); return ((st ushr 11) and 0x1FFFFFFFFFFFFFL) / 9007199254740992.0 }
        val out = ArrayList<Tree>(count)
        var tries = 0
        while (out.size < count && tries < count * 12) {
            tries++
            val g = GeoPoint(minLat + next() * (maxLat - minLat), minLon + next() * (maxLon - minLon))
            if (insideConvex(hull, g)) out.add(Tree(g.lat, g.lon, (3.0 + next() * 4.5).toFloat(), (next() * 3).toInt().coerceIn(0, 2)))
        }
        return out
    }

    fun ground(shapes: List<CampusShape>): CampusGround {
        val hull = convexHull(shapes.flatMap { it.puntos })
        return CampusGround(hull, trees(hull))
    }

    private fun area(pts: List<GeoPoint>): Double {
        var a = 0.0
        for (i in pts.indices) { val p = pts[i]; val q = pts[(i + 1) % pts.size]; a += p.lon * q.lat - q.lon * p.lat }
        return kotlin.math.abs(a) / 2.0
    }

    /** Una etiqueta por código (sobre la figura más grande): bloques, edificios con sigla corta y el estadio. */
    fun labels(shapes: List<CampusShape>): List<ShapeLabel> {
        val out = ArrayList<ShapeLabel>()
        shapes.filter { (it.tipo == "EDIFICIO" || it.tipo == "OTRO") && it.codigo.length in 1..5 && it.puntos.size >= 3 }
            .groupBy { it.codigo }
            .forEach { (cod, list) ->
                val s = list.maxBy { area(it.puntos) }
                out.add(ShapeLabel(cod, s.tipo, GeoPoint(s.puntos.map { it.lat }.average(), s.puntos.map { it.lon }.average())))
            }
        shapes.firstOrNull { it.tipo == "CANCHA_ESTADIO" && it.puntos.size >= 3 }?.let { s ->
            out.add(ShapeLabel("Estadio\nUniversitario", "ESTADIO", GeoPoint(s.puntos.map { it.lat }.average(), s.puntos.map { it.lon }.average())))
        }
        return out
    }
}

private val OUTLINE = Color(0xFFFFFFFF)
private val labelPaint = Paint().apply {
    isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}

private fun pxPerMeter(p: MapProjection, zoom: Float) = p.base * zoom / 111_320f

/** Terreno verdoso del campus con vegetación, sobre el fondo oscuro. Se dibuja ANTES que las vías. */
fun DrawScope.drawCampusGround(p: MapProjection, ground: CampusGround, zoom: Float) {
    if (ground.hull.size < 3) return
    val k = pxPerMeter(p, zoom)
    val path = Path()
    ground.hull.forEachIndexed { i, g ->
        val o = p.toScreen(g.lat, g.lon)
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    path.close()
    // El trazo grueso infla el contorno unos metros para que el terreno envuelva los edificios del borde
    drawPath(path, MapTheme.GROUND, style = Stroke(width = 26f * k, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawPath(path, MapTheme.GROUND, style = Fill)
    val tones = listOf(Color(0x883F7A55), Color(0x882F6446), Color(0x775E8F6B))
    for (t in ground.trees) {
        val r = t.radioM * k
        if (r < 1.5f) continue
        drawCircle(tones[t.tono], r, p.toScreen(t.lat, t.lon))
    }
}

/** Anchura de una vía peatonal en píxeles (≈5 m, con mínimo legible). */
fun roadWidthPx(p: MapProjection, zoom: Float): Float = (5f * pxPerMeter(p, zoom)).coerceIn(4f, 60f)

/** Edificios, estadio, canchas, parqueos y agua. Va DESPUÉS de las vías para que las tape en las huellas. */
fun DrawScope.drawCampusShapes(p: MapProjection, shapes: List<CampusShape>, zoom: Float, labels: List<ShapeLabel> = emptyList()) {
    val order = listOf("PISTA", "CANCHA_ESTADIO", "GRADA", "CANCHA_VERDE", "CANCHA", "COMIDA", "OTRO", "EDIFICIO")
    val k = pxPerMeter(p, zoom)
    val shadow = Offset(2.5f, 3.5f)
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
                "PISTA" -> Color(0xFFBFD8C8)
                "CANCHA_ESTADIO", "CANCHA_VERDE" -> Color(0xFF6FC27F)
                "GRADA" -> Color(0xFFB8C4C8)
                "CANCHA" -> Color(0xFF4FB3E8)
                "COMIDA" -> Color(0xFFF1CF7A)
                else -> facultyColor(if (tipo == "OTRO") "OTRO" else s.grupo)
            }
            val flat = tipo == "PISTA" || tipo == "CANCHA_ESTADIO" || tipo == "CANCHA_VERDE" || tipo == "CANCHA"
            if (!flat) {
                // sombra proyectada: la misma huella desplazada unos píxeles
                drawContext.canvas.save(); drawContext.canvas.translate(shadow.x, shadow.y)
                drawPath(path, Color(0x66000000), style = Fill); drawContext.canvas.restore()
            }
            drawPath(path, fill, style = Fill)
            drawPath(path, OUTLINE.copy(alpha = if (flat) 0.55f else 0.9f), style = Stroke(width = if (flat) 1.5f else 2f))
        }
    }
    // Centro del campo del estadio: círculo central blanco
    shapes.firstOrNull { it.tipo == "CANCHA_ESTADIO" && it.puntos.size >= 3 }?.let { s ->
        val c = p.toScreen(s.puntos.map { it.lat }.average(), s.puntos.map { it.lon }.average())
        drawCircle(Color.White.copy(alpha = 0.8f), 9f * k, c, style = Stroke(width = 1.6f))
    }
    // Etiquetas dentro de las figuras (con zoom alto las sustituyen las del nodo: sigla · carrera)
    if (zoom >= 1.0f) {
        val nc = drawContext.canvas.nativeCanvas
        labelPaint.textSize = if (zoom >= 2f) 30f else 23f
        for (l in labels) {
            val isBlock = l.tipo == "EDIFICIO"
            if (isBlock && zoom >= 2.8f) continue
            if (!isBlock && l.tipo != "ESTADIO" && zoom < 1.6f) continue
            val o = p.toScreen(l.centro.lat, l.centro.lon)
            val lines = l.texto.split('\n')
            lines.forEachIndexed { i, t ->
                val y = o.y + labelPaint.textSize * 0.35f + (i - (lines.size - 1) / 2f) * labelPaint.textSize * 1.05f
                labelPaint.color = android.graphics.Color.argb(150, 0, 0, 0)
                nc.drawText(t, o.x + 1.5f, y + 1.5f, labelPaint)
                labelPaint.color = if (l.tipo == "ESTADIO") android.graphics.Color.rgb(20, 70, 30) else android.graphics.Color.WHITE
                nc.drawText(t, o.x, y, labelPaint)
            }
        }
    }
    // Iconos puntuales: parqueo (P) y punto de hidratación (gota)
    if (zoom >= 1.6f) {
        val nc = drawContext.canvas.nativeCanvas
        labelPaint.textSize = 22f
        for (s in shapes) {
            if (s.puntos.size != 1) continue
            val o: Offset = p.toScreen(s.puntos[0].lat, s.puntos[0].lon)
            when (s.tipo) {
                "PARQUEO" -> {
                    drawRoundRect(Color.White, o - Offset(15f, 15f), Size(30f, 30f), CornerRadius(7f, 7f))
                    drawRoundRect(Color(0xFF1E6FD9), o - Offset(13f, 13f), Size(26f, 26f), CornerRadius(6f, 6f))
                    labelPaint.color = android.graphics.Color.WHITE
                    nc.drawText("P", o.x, o.y + 8f, labelPaint)
                }
                "AGUA" -> {
                    drawCircle(Color.White, 15f, o)
                    drawCircle(Color(0xFF1AA7EC), 13f, o)
                    val gota = Path().apply {
                        moveTo(o.x, o.y - 8f)
                        cubicTo(o.x + 7f, o.y + 1f, o.x + 6f, o.y + 8f, o.x, o.y + 8f)
                        cubicTo(o.x - 6f, o.y + 8f, o.x - 7f, o.y + 1f, o.x, o.y - 8f)
                        close()
                    }
                    drawPath(gota, Color.White, style = Fill)
                }
            }
        }
    }
}
