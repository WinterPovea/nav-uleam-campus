package ec.edu.uleam.navigator.ui.map

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Figura del mapa del campus (huella de un edificio, estadio, cancha, parqueo, punto de hidratación...).
 * Es solo diseño: no interviene en el grafo ni en la ruta. Se carga de assets/campus_shapes.csv.
 * @property tipo EDIFICIO, OTRO, PISTA, CANCHA_ESTADIO, GRADA, CANCHA, CANCHA_VERDE, COMIDA, PLAZA, VIA, VEREDA,
 *   PARQUEO, AGUA o CAFE. Las figuras salen de OpenStreetMap (© OpenStreetMap contributors, ODbL).
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
    else -> MapTheme.OTHER_BUILDING
}

/**
 * Paleta del mapa en modo claro (terreno crema como OpenStreetMap) u oscuro.
 * [dark] es estado de Compose: al cambiarlo el Canvas se vuelve a dibujar con la otra paleta.
 */
object MapTheme {
    var dark by mutableStateOf(false)
    private fun c(claro: Long, oscuro: Long) = Color(if (dark) oscuro else claro)

    /** Alrededor del campus: un tono apenas distinto del terreno, para que no se vea un fondo negro. */
    val OUTSIDE get() = c(0xFFE9E5CB, 0xFF161D20)
    val GROUND get() = c(0xFFF5F1D3, 0xFF222C30)
    val ROAD get() = c(0xFFFFFFFF, 0xFF44535A)
    val ROAD_CASING get() = c(0xFF9C977F, 0xFF12181A)
    val SIDEWALK get() = c(0xFFE0796B, 0xFFB86A5E)
    val STAIRS_BASE get() = c(0xFFF3E3D3, 0xFF4E4038)
    val STAIRS get() = c(0xFFB5654F, 0xFFD9906F)
    val PLAZA get() = c(0xFFC9CDE8, 0xFF3A3F63)
    val ARROW_YELLOW = Color(0xFFFFD21F)
    val TRACK get() = c(0xFFDCEDE8, 0xFF2D4741)
    val TRACK_INNER get() = c(0xFFC9E2D6, 0xFF263D37)
    val PITCH get() = c(0xFF74C283, 0xFF2F6E40)
    val STANDS get() = c(0xFFC4CFD2, 0xFF47555B)
    val COURT get() = c(0xFF6FB6E0, 0xFF2D5F7C)
    val FOOD get() = c(0xFFF1D29A, 0xFF8C6E3C)
    val OTHER_BUILDING get() = c(0xFF5E8A8F, 0xFF4C6A6E)
    val OUTLINE get() = c(0xFFFFFFFF, 0xFF0E1315)
    /** Relleno de calle según su clase OSM (primaria naranja, terciaria amarilla clara, resto blanco). */
    fun roadFill(clase: String): Color = when (clase) {
        "primary", "primary_link" -> c(0xFFFCD6A4, 0xFF7A5A33)
        "secondary" -> c(0xFFF7FABF, 0xFF66673A)
        "tertiary", "tertiary_link" -> c(0xFFFFFFFF, 0xFF55646B)
        else -> ROAD
    }
    fun roadCasing(clase: String): Color = when (clase) {
        "primary", "primary_link" -> c(0xFFC48A3B, 0xFF0F1416)
        else -> ROAD_CASING
    }
    val TREES get() = if (dark) listOf(Color(0x66336B48), Color(0x6629583C), Color(0x55417F56))
        else listOf(Color(0x8878B98A), Color(0x8868A87A), Color(0x7788C497))
}

/**
 * Qué aristas del grafo se pintan como camino en el mapa. Es solo visual: el cálculo de rutas usa todas.
 * - Las escaleras se pintan siempre.
 * - Los accesos finales a un lugar (aristas que tocan un nodo DESTINO o ZONA_SEGURA) no se pintan: el edificio
 *   o el ícono ya marca el lugar y esos tramos cruzaban jardines y edificios.
 * - Tramos entre conexiones que no existen como camino visible (marcados por el equipo sobre el mapa).
 */
object PathVisibility {
    private val OCULTOS: Set<Pair<Int, Int>> = setOf(
        436 to 437, 437 to 442,                 // B-10 / B-11 / Tasty Food 3
        429 to 430, 430 to 444, 433 to 444,     // B-13 / B-14 / B-15
        455 to 457,                             // Cine / Instituto de Idiomas
        462 to 472,                             // junto a B-17
        451 to 452, 453 to 473,                 // junto a la escalera de Infraestructura
        456 to 457, 454 to 455, 458 to 472,     // restos de camino junto al Cine
        446 to 447,                             // bajo la escalera de B-15
        407 to 409,                             // la escalera de la cancha múltiple solo llega a media cuesta
    ).map { (a, b) -> minOf(a, b) to maxOf(a, b) }.toSet()

    /** Tramos que se pintan como escalera aunque en el grafo no lo sean (prolongan escaleras reales). */
    private val COMO_ESCALERA: Set<Pair<Int, Int>> = setOf(
        300 to 401,                             // escalera del Paraninfo hasta el edificio
        406 to 407,                             // bajada de la cancha múltiple / B-05
    ).map { (a, b) -> minOf(a, b) to maxOf(a, b) }.toSet()

    /** Accesos a lugares que sí se pintan como camino. */
    private val SIEMPRE: Set<Pair<Int, Int>> = setOf(317 to 423)   // camino al Vicerrectorado
        .map { (a, b) -> minOf(a, b) to maxOf(a, b) }.toSet()

    fun drawnAsStairs(a: Int, b: Int, escaleras: Boolean): Boolean =
        escaleras || (minOf(a, b) to maxOf(a, b)) in COMO_ESCALERA

    private val SIN_CAMINO = setOf("DESTINO", "ZONA_SEGURA")

    fun visible(a: Int, b: Int, tipoA: String?, tipoB: String?, escaleras: Boolean): Boolean {
        val k = minOf(a, b) to maxOf(a, b)
        if (escaleras || k in COMO_ESCALERA || k in SIEMPRE) return true
        if (tipoA in SIN_CAMINO || tipoB in SIN_CAMINO) return false
        return k !in OCULTOS
    }
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
    fun trees(hull: List<GeoPoint>, count: Int = 140, seed: Long = 20260518L): List<Tree> {
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
            if (insideConvex(hull, g)) out.add(Tree(g.lat, g.lon, (2.5 + next() * 3.5).toFloat(), (next() * 3).toInt().coerceIn(0, 2)))
        }
        return out
    }

    /** Tipos que son líneas (calles, veredas, formas de aristas): no definen el contorno del campus. */
    val LINE_TYPES = setOf("VIA", "VEREDA", "PELDANOS", "ARISTA", "VIA_DOBLE", "ESCALERA")

    fun ground(shapes: List<CampusShape>): CampusGround {
        val hull = convexHull(shapes.filter { it.tipo !in LINE_TYPES }.flatMap { it.puntos })
        return CampusGround(hull, trees(hull))
    }

    private fun area(pts: List<GeoPoint>): Double {
        var a = 0.0
        for (i in pts.indices) { val p = pts[i]; val q = pts[(i + 1) % pts.size]; a += p.lon * q.lat - q.lon * p.lat }
        return kotlin.math.abs(a) / 2.0
    }

    /** Una etiqueta por bloque del catálogo (B-xx, sobre la figura más grande), más estadio, plaza y cafeterías. Los códigos del mapa físico que no están en el CSV no se rotulan. */
    fun labels(shapes: List<CampusShape>): List<ShapeLabel> {
        val out = ArrayList<ShapeLabel>()
        shapes.filter { it.tipo == "EDIFICIO" && it.codigo.isNotBlank() && it.puntos.size >= 3 }
            .groupBy { it.codigo }
            .forEach { (cod, list) ->
                val s = list.maxBy { area(it.puntos) }
                out.add(ShapeLabel(cod, s.tipo, GeoPoint(s.puntos.map { it.lat }.average(), s.puntos.map { it.lon }.average())))
            }
        fun centro(s: CampusShape) = GeoPoint(s.puntos.map { it.lat }.average(), s.puntos.map { it.lon }.average())
        shapes.firstOrNull { it.tipo == "CANCHA_ESTADIO" && it.puntos.size >= 3 }?.let { out.add(ShapeLabel("Estadio\nUniversitario", "ESTADIO", centro(it))) }
        shapes.firstOrNull { it.tipo == "PLAZA" && it.puntos.size >= 3 }?.let { out.add(ShapeLabel("Plaza\nCultural", "PLAZA", centro(it))) }
        shapes.filter { it.tipo == "COMIDA" && it.puntos.size >= 3 && it.codigo.startsWith("Tasty") }.forEach { s ->
            out.add(ShapeLabel("Tasty " + s.codigo.filter { c -> c.isDigit() }, "COMIDA", centro(s)))
        }
        return out
    }
}

private val labelPaint = Paint().apply {
    isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}

private fun pxPerMeter(p: MapProjection, zoom: Float) = p.base * zoom / 111_320f

/** Terreno claro del campus con vegetación, sobre el fondo oscuro. Se dibuja ANTES que las vías. */
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
    val tones = MapTheme.TREES
    for (t in ground.trees) {
        val r = t.radioM * k
        if (r < 1.5f) continue
        drawCircle(tones[t.tono], r, p.toScreen(t.lat, t.lon))
    }
}

/** Anchura de una vía peatonal en píxeles (≈5 m, con mínimo legible). */
fun roadWidthPx(p: MapProjection, zoom: Float): Float = (5f * pxPerMeter(p, zoom)).coerceIn(4f, 60f)

private fun DrawScope.pathOf(p: MapProjection, s: CampusShape): Path {
    val path = Path()
    s.puntos.forEachIndexed { i, g ->
        val o = p.toScreen(g.lat, g.lon)
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    path.close()
    return path
}

/** Marcas del campo del estadio (línea central, círculo, áreas) a partir de su rectángulo p0-p1-p2-p3. */
private fun DrawScope.drawPitchMarks(p: MapProjection, s: CampusShape) {
    if (s.puntos.size != 4) return
    val q = s.puntos.map { p.toScreen(it.lat, it.lon) }
    val m1 = (q[0] + q[1]) / 2f; val m2 = (q[2] + q[3]) / 2f   // centros de las dos líneas de meta
    val w = q[1] - q[0]; val e = m2 - m1
    val line = Color.White.copy(alpha = 0.75f); val st = Stroke(width = 1.6f)
    fun rect(c: Offset, hw: Float, depth: Float, dir: Float) {
        val a = c - w * hw; val b = c + w * hw
        val pa = Path().apply {
            moveTo(a.x, a.y); lineTo(a.x + e.x * depth * dir, a.y + e.y * depth * dir)
            lineTo(b.x + e.x * depth * dir, b.y + e.y * depth * dir); lineTo(b.x, b.y)
        }
        drawPath(pa, line, style = st)
    }
    rect(m1, 0.28f, 0.17f, 1f); rect(m1, 0.13f, 0.06f, 1f)
    rect(m2, 0.28f, 0.17f, -1f); rect(m2, 0.13f, 0.06f, -1f)
    val c = (m1 + m2) / 2f
    drawLine(line, c - w / 2f, c + w / 2f, strokeWidth = 1.6f)
    drawCircle(line, w.getDistance() * 0.17f, c, style = st)
}

/** Gradas: bloque con franjas a lo largo del lado largo. */
private fun DrawScope.drawStands(p: MapProjection, s: CampusShape) {
    if (s.puntos.size != 4) return
    val q = s.puntos.map { p.toScreen(it.lat, it.lon) }
    val n = 14
    for (i in 1 until n) {
        val t = i / n.toFloat()
        drawLine(if (MapTheme.dark) Color(0x66C8D2D6) else Color(0x66344650), q[0] + (q[3] - q[0]) * t, q[1] + (q[2] - q[1]) * t, strokeWidth = 1.4f)
    }
}

/** Símbolo dorado de la Plaza Cultural (dos brazos curvos sobre un tronco). */
private fun DrawScope.drawPlazaIcon(c: Offset, k: Float) {
    val r = 6f * k
    val gold = Color(0xFFD9B84A)
    val st = Stroke(width = (1.3f * k).coerceAtLeast(2.5f), cap = StrokeCap.Round)
    val left = Path().apply { moveTo(c.x - r, c.y - r); quadraticBezierTo(c.x - r * 0.2f, c.y, c.x, c.y + r * 0.2f) }
    val right = Path().apply { moveTo(c.x + r, c.y - r); quadraticBezierTo(c.x + r * 0.2f, c.y, c.x, c.y + r * 0.2f) }
    drawPath(left, gold, style = st); drawPath(right, gold, style = st)
    drawLine(gold, Offset(c.x, c.y + r * 0.2f), Offset(c.x, c.y + r), strokeWidth = st.width, cap = StrokeCap.Round)
}

/** Vías decorativas (tipo VIA: `codigo` = ancho en metros). Son diseño: la ruta real sigue las aristas del grafo. */
private fun DrawScope.drawCampusRoads(p: MapProjection, shapes: List<CampusShape>, k: Float) {
    val round = androidx.compose.ui.graphics.StrokeJoin.Round
    fun pathOfLine(s: CampusShape) = Path().apply {
        s.puntos.forEachIndexed { i, g ->
            val o = p.toScreen(g.lat, g.lon)
            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
        }
    }
    // Calles (tipo VIA, `grupo` = clase OSM): de menor a mayor jerarquía para que las principales queden encima
    val rango = mapOf("service" to 0, "track" to 0, "pedestrian" to 0, "living_street" to 1, "residential" to 1,
        "unclassified" to 1, "tertiary_link" to 2, "tertiary" to 2, "secondary" to 3, "primary_link" to 3, "primary" to 4)
    val vias = shapes.filter { it.tipo == "VIA" && it.puntos.size >= 2 }.sortedBy { rango[it.grupo] ?: 0 }
        .map { Triple(it.grupo, ((it.codigo.toFloatOrNull() ?: 6f) * k).coerceAtLeast(if (it.grupo == "service") 3f else 5f), pathOfLine(it)) }
    // primero todos los bordes y luego los rellenos, para que los cruces se fundan sin costuras
    for ((clase, w, path) in vias) drawPath(path, MapTheme.roadCasing(clase), style = Stroke(width = w + 3f, cap = StrokeCap.Round, join = round))
    for ((clase, w, path) in vias) drawPath(path, MapTheme.roadFill(clase), style = Stroke(width = w, cap = StrokeCap.Round, join = round))
    // Veredas y ciclovías: línea punteada (salmón / azul) como en OpenStreetMap
    for (s in shapes) {
        if (s.tipo != "VEREDA" || s.puntos.size < 2) continue
        val ciclo = s.grupo == "cycleway"
        drawPath(pathOfLine(s), if (ciclo) Color(0xFF3F6FD8) else MapTheme.SIDEWALK, style = Stroke(
            width = (1.6f * k).coerceIn(2f, 5f), cap = StrokeCap.Butt, join = round,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
        ))
    }
    // Escaleras (highway=steps): base clara + peldaños
    for (s in shapes) {
        if (s.tipo != "PELDANOS" || s.puntos.size < 2) continue
        val path = pathOfLine(s)
        val w = (3f * k).coerceIn(5f, 30f)
        val step = (1.0f * k).coerceIn(2.5f, 9f)
        drawPath(path, MapTheme.STAIRS_BASE, style = Stroke(width = w, cap = StrokeCap.Butt, join = round))
        drawPath(path, MapTheme.STAIRS, style = Stroke(width = w * 0.9f, cap = StrokeCap.Butt, join = round,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(step * 0.45f, step * 0.55f))))
    }
}

/** Edificios, estadio, canchas, plaza, parqueos y agua. Va DESPUÉS de las vías para que las tape en las huellas. */
fun DrawScope.drawCampusShapes(p: MapProjection, shapes: List<CampusShape>, zoom: Float, labels: List<ShapeLabel> = emptyList()) {
    val order = listOf("PISTA", "PISTA_INT", "CANCHA_ESTADIO", "GRADA", "PLAZA", "CANCHA_VERDE", "CANCHA", "COMIDA", "OTRO", "EDIFICIO")
    val flatTypes = setOf("PISTA", "PISTA_INT", "CANCHA_ESTADIO", "GRADA", "PLAZA", "CANCHA_VERDE", "CANCHA")
    val k = pxPerMeter(p, zoom)
    val shadow = Offset(2.5f, 3.5f)
    drawCampusRoads(p, shapes, k)
    for (tipo in order) {
        for (s in shapes) {
            if (s.tipo != tipo || s.puntos.size < 3) continue
            val path = pathOf(p, s)
            val fill = when (tipo) {
                "PISTA" -> MapTheme.TRACK
                "PISTA_INT" -> MapTheme.TRACK_INNER
                "CANCHA_ESTADIO", "CANCHA_VERDE" -> MapTheme.PITCH
                "GRADA" -> MapTheme.STANDS
                "PLAZA" -> MapTheme.PLAZA
                "CANCHA" -> MapTheme.COURT
                "COMIDA" -> MapTheme.FOOD
                else -> facultyColor(if (tipo == "OTRO") "OTRO" else s.grupo)
            }
            val flat = tipo in flatTypes
            if (!flat) {
                // sombra proyectada: la misma huella desplazada unos píxeles
                drawContext.canvas.save(); drawContext.canvas.translate(shadow.x, shadow.y)
                drawPath(path, Color(0x55000000), style = Fill); drawContext.canvas.restore()
            }
            drawPath(path, fill, style = Fill)
            val base = if (MapTheme.dark && flat) Color.White.copy(alpha = 0.35f) else MapTheme.OUTLINE
            val outline = when (tipo) {
                "PISTA" -> base.copy(alpha = base.alpha * 0.9f)
                "PISTA_INT" -> base.copy(alpha = base.alpha * 0.75f)
                else -> base.copy(alpha = base.alpha * if (flat) 0.55f else 0.9f)
            }
            drawPath(path, outline, style = Stroke(width = if (tipo == "PISTA") 2.5f else if (flat) 1.5f else 2f))
            if (tipo == "CANCHA_ESTADIO") drawPitchMarks(p, s)
            if (tipo == "GRADA") drawStands(p, s)
        }
    }
    shapes.firstOrNull { it.tipo == "PLAZA" && it.puntos.size >= 3 }?.let { s ->
        val c = p.toScreen(s.puntos.map { it.lat }.average(), s.puntos.map { it.lon }.average())
        drawPlazaIcon(c - Offset(0f, 9f * k), k)
    }
    // Etiquetas dentro de las figuras (con zoom alto las sustituyen las del nodo: sigla · carrera)
    if (zoom >= 1.0f) {
        val nc = drawContext.canvas.nativeCanvas
        for (l in labels) {
            val isBlock = l.tipo == "EDIFICIO"
            if (isBlock && zoom >= 2.8f) continue
            val minZoom = when (l.tipo) { "EDIFICIO", "ESTADIO" -> 1.0f; "PLAZA" -> 1.4f; else -> 1.6f }
            if (zoom < minZoom) continue
            labelPaint.textSize = when (l.tipo) { "COMIDA" -> 19f; else -> if (zoom >= 2f) 30f else 23f }
            val o = p.toScreen(l.centro.lat, l.centro.lon)
            val lines = l.texto.split('\n')
            val dark = l.tipo == "ESTADIO" || l.tipo == "PLAZA"
            lines.forEachIndexed { i, t ->
                val y = o.y + labelPaint.textSize * 0.35f + (i - (lines.size - 1) / 2f) * labelPaint.textSize * 1.05f +
                    (if (l.tipo == "PLAZA") 6f * k else 0f)
                if (!dark) {
                    labelPaint.color = android.graphics.Color.argb(150, 0, 0, 0)
                    nc.drawText(t, o.x + 1.5f, y + 1.5f, labelPaint)
                }
                labelPaint.color = if (MapTheme.dark) when (l.tipo) {
                    "ESTADIO" -> android.graphics.Color.rgb(200, 235, 205)
                    "PLAZA" -> android.graphics.Color.rgb(215, 220, 255)
                    "COMIDA" -> android.graphics.Color.rgb(255, 228, 180)
                    else -> android.graphics.Color.WHITE
                } else when (l.tipo) {
                    "ESTADIO" -> android.graphics.Color.rgb(20, 70, 30)
                    "PLAZA" -> android.graphics.Color.rgb(55, 60, 110)
                    "COMIDA" -> android.graphics.Color.rgb(110, 70, 20)
                    else -> android.graphics.Color.WHITE
                }
                nc.drawText(t, o.x, y, labelPaint)
            }
        }
    }
    // Cafeterías sin huella propia (El bosque): taza sobre el nodo, visible desde zoom bajo
    if (zoom >= 1.0f) {
        for (s in shapes) {
            if (s.tipo != "CAFE" || s.puntos.size != 1) continue
            val n = p.toScreen(s.puntos[0].lat, s.puntos[0].lon)
            drawCafeIcon(Offset(n.x, n.y - 30f))
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
                    drawRoundRect(Color(0xFF1E88E5), o - Offset(13f, 13f), Size(26f, 26f), CornerRadius(6f, 6f))
                    labelPaint.color = android.graphics.Color.WHITE
                    nc.drawText("P", o.x, o.y + 8f, labelPaint)
                }
                "AGUA" -> {
                    drawCircle(Color.White, 15f, o)
                    drawCircle(Color(0xFF29B6F6), 13f, o)
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

/** Ícono de cafetería: círculo café con una taza blanca humeante. */
private fun DrawScope.drawCafeIcon(o: Offset) {
    drawCircle(Color.White, 18f, o)
    drawCircle(Color(0xFF6D4C41), 16f, o)
    // taza
    drawRoundRect(Color.White, Offset(o.x - 8f, o.y - 3f), Size(13f, 11f), CornerRadius(2.5f, 2.5f))
    drawCircle(Color.White, 4.2f, Offset(o.x + 6f, o.y + 2f), style = Stroke(width = 2.2f))
    // plato
    drawLine(Color.White, Offset(o.x - 10f, o.y + 10f), Offset(o.x + 8f, o.y + 10f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    // vapor
    for (dx in listOf(-5f, 0f)) {
        drawLine(Color.White.copy(alpha = 0.9f), Offset(o.x + dx, o.y - 6f), Offset(o.x + dx + 1.5f, o.y - 11f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    }
}

/**
 * Forma real de las aristas dibujada en QGIS (filas ARISTA de campus_shapes.csv, código "a-b" con a < b y
 * puntos de a hacia b). Solo afecta el dibujo: el cálculo de rutas usa los nodos y la distancia de aristas.csv.
 */
class EdgeShapes private constructor(private val byPair: Map<Pair<Int, Int>, List<GeoPoint>>) {
    /** Polilínea de [from] a [to] (invertida si hace falta), o null si la arista es recta. */
    fun between(from: Int, to: Int): List<GeoPoint>? =
        if (from <= to) byPair[from to to] else byPair[to to from]?.asReversed()

    /** Puntos de una ruta de nodos siguiendo la forma de cada tramo, sin repetir los nodos de unión. */
    fun routePoints(ids: List<Int>, nodePoint: (Int) -> GeoPoint?): List<GeoPoint> {
        val out = ArrayList<GeoPoint>()
        for (i in ids.indices) {
            if (i == 0) { nodePoint(ids[0])?.let(out::add); continue }
            val tramo = between(ids[i - 1], ids[i])
            if (tramo != null) out.addAll(tramo.drop(1)) else nodePoint(ids[i])?.let(out::add)
        }
        return out
    }

    val size: Int get() = byPair.size

    companion object {
        fun from(shapes: List<CampusShape>): EdgeShapes = EdgeShapes(
            shapes.filter { it.tipo == "ARISTA" && it.puntos.size >= 2 }.mapNotNull { s ->
                val ids = s.codigo.split('-').mapNotNull { it.toIntOrNull() }
                if (ids.size == 2 && ids[0] < ids[1]) (ids[0] to ids[1]) to s.puntos else null
            }.toMap()
        )
    }
}
