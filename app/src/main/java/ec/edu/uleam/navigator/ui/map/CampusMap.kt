package ec.edu.uleam.navigator.ui.map

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Explore
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ec.edu.uleam.navigator.data.AristaEntity
import ec.edu.uleam.navigator.data.LugarEntity
import ec.edu.uleam.navigator.data.NodoEntity
import ec.edu.uleam.navigator.ui.UserPosition
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.launch

/**
 * Componente cartográfico central para el campus universitario ULEAM.
 * Renderiza:
 * - Infraestructura física de edificios por categoría con colores institucionales.
 * - Red de camineras peatonales y tramos con escaleras.
 * - Etiquetas legibles para bloques y dependencias.
 * - Trazado de ruta óptima A* con halo de contraste.
 * - Marcador de usuario animado (pulsing blue dot) con cono de visión (FOV 45°) e incertidumbre GNSS.
 * - Soporte de rotación multitouch o alineación con brújula.
 */
@Composable
fun CampusMap(
    nodos: List<NodoEntity>,
    aristas: List<AristaEntity>,
    lugares: List<LugarEntity>,
    shapes: List<CampusShape> = emptyList(),
    nodoById: Map<Int, NodoEntity>,
    routeNodeIds: List<Int>?,
    originNodeId: Int?,
    destinationNodeId: Int?,
    userPosition: UserPosition?,
    isSimulationMode: Boolean,
    onMapTap: (lat: Double, lon: Double) -> Unit,
    onPlaceTap: (LugarEntity) -> Unit = {},
    modifier: Modifier = Modifier,
    azimuthDeg: Float? = null,
    navigating: Boolean = false,
    controlsBottomPadding: Dp = 16.dp,
    /** Estilo del mapa actual (el botón para alternarlo está en la pantalla principal, bajo las puertas). */
    mapStyle: MapStyle = MapStyle.CLARO,
    /** Tramo inicial de la ruta con GPS real: desde el camino junto al usuario hasta el primer nodo. */
    routeLead: List<GeoPoint> = emptyList()
) {
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    var zoom by remember { mutableFloatStateOf(1.2f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var mapRotationDeg by remember { mutableFloatStateOf(0f) }
    val currentOnTap by rememberUpdatedState(onMapTap)
    val currentOnPlaceTap by rememberUpdatedState(onPlaceTap)
    val currentNavigating by rememberUpdatedState(navigating)
    val scope = rememberCoroutineScope()

    // Posición con movimiento suave (el marcador se desliza entre lecturas GPS / pasos de simulación)
    val animatedUser = rememberAnimatedUserPosition(userPosition, if (isSimulationMode) 130 else 900)
    val latestUser by rememberUpdatedState(animatedUser)

    // Modo seguimiento: la cámara acompaña al usuario mientras se navega (como Google Maps)
    var followUser by remember { mutableStateOf(false) }
    var followZoom by remember { mutableFloatStateOf(3.4f) }
    // Mapa "rumbo arriba": durante la navegación gira para que el frente del usuario apunte hacia arriba
    var followHeading by remember { mutableStateOf(true) }

    // Forma real de las aristas (curvas dibujadas en QGIS, filas ARISTA de campus_shapes.csv)
    val edgeShapes = remember(shapes) { EdgeShapes.from(shapes) }

    // Puntos de la ruta siguiendo la forma de cada arista (también para el avance mientras se navega)
    val routePoints = remember(routeNodeIds, nodoById, edgeShapes, routeLead) {
        routeNodeIds?.let { ids ->
            val pts = edgeShapes.routePoints(ids) { id -> nodoById[id]?.let { GeoPoint(it.latitud, it.longitud) } }
            if (routeLead.size >= 2) routeLead + pts.drop(1) else pts
        }
    }
    val navProgress = if (navigating && routePoints != null) {
        animatedUser?.let { RouteProgressCalc.compute(routePoints, GeoPoint(it.lat, it.lon)) }
    } else null
    // En simulación el rumbo es el del tramo; con GPS real manda la brújula (si falla, el del tramo)
    val headingDeg: Float? =
        if (isSimulationMode) navProgress?.bearingDeg?.toFloat()
        else azimuthDeg ?: navProgress?.bearingDeg?.toFloat()
    val latestHeading by rememberUpdatedState(headingDeg)

    // Animación continua para el marcador de usuario 'pulsing blue dot'
    val infiniteTransition = rememberInfiniteTransition(label = "UserPulseTransition")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 14f,
        targetValue = 42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )

    val bounds = remember(nodos) {
        if (nodos.isEmpty()) doubleArrayOf(-0.956, -0.948, -80.748, -80.741)
        else doubleArrayOf(
            nodos.minOf { it.latitud }, nodos.maxOf { it.latitud },
            nodos.minOf { it.longitud }, nodos.maxOf { it.longitud }
        )
    }

    val ground = remember(shapes) { CampusShapesGeometry.ground(shapes) }
    // Pares de nodos marcados como vía doble (filas VIA_DOBLE de campus_shapes.csv, código "a-b")
    val viasDobles = remember(shapes) {
        shapes.filter { it.tipo == "VIA_DOBLE" }.mapNotNull { s ->
            val p = s.codigo.split('-').mapNotNull { it.toIntOrNull() }
            if (p.size == 2) minOf(p[0], p[1]) to maxOf(p[0], p[1]) else null
        }.toSet()
    }
    val shapeLabels = remember(shapes) { CampusShapesGeometry.labels(shapes) }
    val hasShapes = shapes.isNotEmpty()

    val lugaresByNodoId = remember(lugares) {
        lugares.associateBy { it.idNodo }
    }
    val currentLugares by rememberUpdatedState(lugaresByNodoId)

    // Paint nativo de Android optimizado para renderizar texto de alta legibilidad en Canvas
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }
    val labelBgPaint = remember {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = android.graphics.Color.argb(220, 255, 255, 255)
        }
    }

    fun proj(z: Float = zoom, p: Offset = pan, rot: Float = mapRotationDeg) =
        MapProjection(bounds[0], bounds[1], bounds[2], bounds[3], size, z, p, rot)

    /** Pan (previo a la rotación) que deja el punto (lat, lon) justo en el centro de la pantalla. */
    fun panToCenter(lat: Double, lon: Double, z: Float): Offset {
        val base = proj(1f, Offset.Zero, 0f)
        return -(base.toScreen(lat, lon) - base.center) * z
    }

    // Extensión visible del campus: nodos + edificios/canchas (los edificios del borde, como B-11 y B-12,
    // quedan fuera del rectángulo de los nodos)
    val cameraBounds = remember(bounds, shapes) {
        val pts = shapes.filter { it.puntos.size >= 3 && it.tipo !in CampusShapesGeometry.LINE_TYPES }.flatMap { it.puntos }
        if (pts.isEmpty()) bounds else doubleArrayOf(
            minOf(bounds[0], pts.minOf { it.lat }), maxOf(bounds[1], pts.maxOf { it.lat }),
            minOf(bounds[2], pts.minOf { it.lon }), maxOf(bounds[3], pts.maxOf { it.lon })
        )
    }
    val density = LocalDensity.current
    // Zonas tapadas por la interfaz: arriba la búsqueda + puertas + botón de estilo, abajo la tarjeta
    val topInsetPx = with(density) { 200.dp.toPx() }
    val bottomInsetPx = with(density) { controlsBottomPadding.toPx() }

    /**
     * Mantiene la cámara sobre el campus: se puede desplazar hasta que el borde del campus (con un margen
     * pequeño) quede justo bajo la búsqueda o justo sobre la tarjeta inferior, pero no más allá.
     * Así se ven completos los edificios de los bordes y el mapa no se aleja hacia zonas vacías.
     */
    fun clampPan(z: Float, pn: Offset): Offset {
        if (size.width <= 1) return pn
        val pr = proj(z, pn)
        val (lat, lon) = pr.toGeo(pr.center)
        val k = pr.base * z                      // píxeles por grado
        val m = CAMERA_MARGIN_DEG
        val h = size.height.toDouble(); val w = size.width.toDouble()
        fun limit(v: Double, lo: Double, hi: Double): Double = if (lo > hi) (lo + hi) / 2 else v.coerceIn(lo, hi)
        // Norte arriba: el borde norte puede bajar hasta topInset; el borde sur subir hasta h - bottomInset
        val cl = limit(
            lat,
            cameraBounds[0] - m + (h / 2 - bottomInsetPx) / k,
            cameraBounds[1] + m - (h / 2 - topInsetPx) / k
        )
        val co = limit(lon, cameraBounds[2] - m + (w / 2) / k, cameraBounds[3] + m - (w / 2) / k)
        return if (cl == lat && co == lon) pn else panToCenter(cl, co, z)
    }

    /** Anima la cámara (zoom + desplazamiento) hasta centrar la posición indicada. */
    fun animateCameraTo(lat: Double, lon: Double, targetZoom: Float) {
        val z0 = zoom
        val p0 = pan
        val z1 = targetZoom
        val p1 = clampPan(z1, panToCenter(lat, lon, z1))
        scope.launch {
            Animatable(0f).animateTo(1f, tween(450, easing = FastOutSlowInEasing)) {
                zoom = z0 + (z1 - z0) * value
                pan = p0 + (p1 - p0) * value
            }
        }
    }

    // Al empezar / terminar la ruta se activa / desactiva el seguimiento de cámara
    LaunchedEffect(navigating) {
        followUser = navigating
        followHeading = true
        if (navigating) followZoom = maxOf(zoom, 3.4f)
    }

    // Bucle de cámara: se acerca suavemente (ease-out) a la posición animada del usuario
    LaunchedEffect(followUser) {
        if (!followUser) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val u = latestUser
                if (u != null && size.width > 1) {
                    val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                    val k = 1f - exp(-9f * dt)
                    val targetPan = panToCenter(u.lat, u.lon, zoom)
                    pan += (targetPan - pan) * k
                    zoom += (followZoom - zoom) * k
                    // Gira hacia el rumbo del usuario por el camino más corto (con suavizado más lento)
                    val h = latestHeading
                    if (followHeading && h != null) {
                        val diff = ((-h - mapRotationDeg + 540f) % 360f + 360f) % 360f - 180f
                        mapRotationDeg = (mapRotationDeg + diff * (1f - exp(-5f * dt))) % 360f
                    }
                }
                last = now
            }
        }
    }

    // Con GPS real, ¿el usuario está fuera del campus? (más de ~110 m fuera del rectángulo de los nodos)
    val userOutsideCampus = !isSimulationMode && userPosition?.let { u ->
        val m = 0.001
        u.lat < bounds[0] - m || u.lat > bounds[1] + m || u.lon < bounds[2] - m || u.lon > bounds[3] + m
    } == true

    /** Centra el mapa en el usuario: reanuda el seguimiento si se navega, o anima una sola vez. */
    fun centrarEnUsuario() {
        if (userOutsideCampus) {
            // Fuera del campus no se lleva la cámara a la calle: se muestra todo el campus
            animateCameraTo((bounds[0] + bounds[1]) / 2, (bounds[2] + bounds[3]) / 2, 1.2f)
            return
        }
        val target = userPosition ?: originNodeId?.let { id ->
            nodoById[id]?.let { UserPosition(it.latitud, it.longitud) }
        } ?: return
        if (navigating) {
            followUser = true
            followZoom = maxOf(followZoom, 3.4f)
        } else {
            animateCameraTo(target.lat, target.lon, maxOf(zoom, 3.5f))
        }
    }

    // El usuario queda fuera de la pantalla (o la navegación dejó de seguirlo) -> mostrar "Mi ubicación"
    val userOffScreen = animatedUser?.let { u ->
        val o = proj().toScreen(u.lat, u.lon)
        val m = 24f
        o.x < m || o.y < m || o.x > size.width - m || o.y > size.height - m
    } ?: false
    val showMyLocationButton = userPosition != null && !userOutsideCampus && (userOffScreen || (navigating && !followUser))

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(if (shapes.isNotEmpty()) MapTheme.OUTSIDE else Color(0xFFF6F5F0)) // tono del entorno según el estilo del mapa
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, panChange, zoomChange, rotationChange ->
                        val oldZoom = zoom
                        val newZoom = (zoom * zoomChange).coerceIn(1.0f, 15f)
                        // Un arrastre con un dedo (sin zoom ni giro) suelta el seguimiento de la cámara
                        if (followUser && zoomChange == 1f && rotationChange == 0f && panChange != Offset.Zero) {
                            followUser = false
                        }
                        if (followUser) {
                            // Siguiendo al usuario: el zoom se aplica y la cámara sigue anclada a él
                            zoom = newZoom
                            followZoom = newZoom
                        } else {
                            // Zoom anclado al punto de pellizco: el mapa crece desde donde están los dedos
                            if (newZoom != oldZoom) pan = proj().panForZoomAt(centroid, newZoom)
                            zoom = newZoom
                            pan = clampPan(zoom, pan + proj().rotatePanDelta(panChange))
                        }
                        if (abs(rotationChange) > 0.05f) {
                            mapRotationDeg = (mapRotationDeg + rotationChange) % 360f
                            followHeading = false // el usuario tomó el control del giro
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        // Toque corto: si cae sobre un lugar (bloque, comida, etc.) lo elige como destino;
                        // en un espacio vacío se conserva el comportamiento anterior (fijar origen en simulación).
                        onTap = { offset ->
                            if (!currentNavigating) {
                                val p = proj()
                                val candidates = currentLugares.mapNotNull { (id, _) ->
                                    nodoById[id]?.let { id to p.toScreen(it.latitud, it.longitud) }
                                }
                                val hit = PlaceHitTest.nearest(candidates, offset, 28.dp.toPx())
                                val place = hit?.let { currentLugares[it] }
                                if (place != null) currentOnPlaceTap(place)
                                else {
                                    val (lat, lon) = p.toGeo(offset)
                                    currentOnTap(lat, lon)
                                }
                            }
                        },
                        // Mantener pulsado: fija el origen aunque haya un lugar bajo el dedo (Modo Simulación)
                        onLongPress = { offset ->
                            if (!currentNavigating) {
                                val (lat, lon) = proj().toGeo(offset)
                                currentOnTap(lat, lon)
                            }
                        }
                    )
                }
        ) {
            val p = proj()

            // 0. TERRENO Y VEGETACIÓN DEL CAMPUS (diseño del mapa físico)
            drawCampusGround(p, ground, zoom)

            // 1. RED PEATONAL. Con el diseño del mapa, los caminos se dibujan siguiendo las aristas (trazadas sobre
            //    OpenStreetMap) con estilo de vereda, y las escaleras con peldaños; sin diseño, líneas simples.
            if (hasShapes && shapes.none { it.tipo == "VIA" }) {
                val w = (4f * p.base * zoom / 111_320f).coerceIn(5f, 46f)
                // (puntos en pantalla siguiendo la forma de la arista, ¿escalera?, id1, id2);
                // las escaleras decorativas (tipo ESCALERA) usan ids negativos
                data class Seg(val pts: List<Offset>, val esc: Boolean, val ia: Int, val ib: Int)
                val segs = aristas.mapNotNull { e ->
                    val u = nodoById[e.idOrigen] ?: return@mapNotNull null
                    val v = nodoById[e.idDestino] ?: return@mapNotNull null
                    val esc = e.tieneEscaleras == 1
                    if (!PathVisibility.visible(u.id, v.id, u.tipo, v.tipo, esc)) return@mapNotNull null
                    val geo = edgeShapes.between(u.id, v.id) ?: listOf(GeoPoint(u.latitud, u.longitud), GeoPoint(v.latitud, v.longitud))
                    Seg(geo.map { p.toScreen(it.lat, it.lon) }, PathVisibility.drawnAsStairs(u.id, v.id, esc), u.id, v.id)
                } + shapes.filter { it.tipo == "ESCALERA" && it.puntos.size == 2 }.map { s ->
                    Seg(s.puntos.map { p.toScreen(it.lat, it.lon) }, true, -1, -2)
                }
                // Vías dobles (Doble_Via en QGIS) se dibujan más anchas que las veredas
                fun ancho(s: Seg) = if ((minOf(s.ia, s.ib) to maxOf(s.ia, s.ib)) in viasDobles) w * 1.7f else w
                fun polyline(pts: List<Offset>, color: Color, width: Float) {
                    for (i in 0 until pts.size - 1) drawLine(color, pts[i], pts[i + 1], strokeWidth = width, cap = StrokeCap.Round)
                }
                // bordes de todos los caminos primero y rellenos después: los cruces quedan limpios
                for (s in segs) if (!s.esc) polyline(s.pts, MapTheme.ROAD_CASING, ancho(s) + 4f)
                for (s in segs) if (!s.esc) polyline(s.pts, MapTheme.ROAD, ancho(s))
                // Una escalera que llega a un camino se recorta medio ancho para no tapar la vereda
                val nodosConCamino = segs.filter { !it.esc }.flatMap { listOf(it.ia, it.ib) }.toSet()
                fun recorte(desde: Offset, hacia: Offset): Offset {
                    val d = hacia - desde; val len = d.getDistance()
                    return if (len < 1f) desde else desde + d * ((w / 2f + 2f).coerceAtMost(len / 3f) / len)
                }
                // escaleras: base clara + peldaños (trazo grueso discontinuo = barras perpendiculares)
                val step = (1.2f * p.base * zoom / 111_320f).coerceIn(2.5f, 10f)
                for (s in segs) if (s.esc && s.pts.size >= 2) {
                    val pts = s.pts.toMutableList()
                    if (s.ia in nodosConCamino) pts[0] = recorte(pts[0], pts[1])
                    if (s.ib in nodosConCamino || s.ib == -2) pts[pts.lastIndex] = recorte(pts.last(), pts[pts.lastIndex - 1])
                    for (i in 0 until pts.size - 1) {
                        val a = pts[i]; val b = pts[i + 1]
                        drawLine(MapTheme.ROAD_CASING, a, b, strokeWidth = w + 4f, cap = StrokeCap.Butt)
                        drawLine(MapTheme.STAIRS_BASE, a, b, strokeWidth = w, cap = StrokeCap.Butt)
                        drawLine(MapTheme.STAIRS, a, b, strokeWidth = w * 0.9f, cap = StrokeCap.Butt,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(step * 0.45f, step * 0.55f)))
                    }
                }
            }
            if (!hasShapes) aristas.forEach { edge ->
                val u = nodoById[edge.idOrigen] ?: return@forEach
                val v = nodoById[edge.idDestino] ?: return@forEach
                val p1 = p.toScreen(u.latitud, u.longitud)
                val p2 = p.toScreen(v.latitud, v.longitud)

                if (edge.tieneEscaleras == 1) {
                    // Escaleras: línea más delgada de tono pizarra
                    drawLine(
                        color = Color(0xFF64748B),
                        start = p1,
                        end = p2,
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                } else {
                    // Calles/Camineras estándar
                    drawLine(
                        color = Color(0xFFD1D5DB),
                        start = p1,
                        end = p2,
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 1b. EDIFICIOS, ESTADIO, CANCHAS, PARQUEOS Y AGUA (sobre las vías, bajo ruta y etiquetas)
            // Etiquetas sin encimarse: el destino y el origen tienen prioridad, luego los bloques, luego el resto
            val placer = LabelPlacer()
            drawCampusShapes(p, shapes, zoom, shapeLabels, placer)

            // 2. DIBUJAR INFRAESTRUCTURA DE EDIFICIOS Y BLOQUES (GEOMETRÍA Y RELLENO)
            nodos.forEach { node ->
                if (node.tipo == "DESTINO" || node.tipo == "ENTRADA" || node.tipo == "ZONA_SEGURA") {
                    val center = p.toScreen(node.latitud, node.longitud)
                    val place = lugaresByNodoId[node.id]

                    when (node.tipo) {
                        "DESTINO" -> {
                            val category = place?.categoria ?: "BLOQUE"
                            val (fillColor, strokeColor) = when (category) {
                                "ADMINISTRATIVO" -> Color(0xFFEDE7F6) to Color(0xFF5E35B1) // Rectorado/Admin
                                "COMIDA" -> Color(0xFFFFF3E0) to Color(0xFFE65100)         // Cafeterías
                                "DEPORTE" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)        // Coliseo/Canchas
                                "CULTURA" -> Color(0xFFF3E5F5) to Color(0xFF8E24AA)        // Paraninfo/Auditorio
                                else -> Color(0xFFE3F2FD) to Color(0xFF1565C0)             // Bloques de aulas / FCVT
                            }

                            if (shapes.isNotEmpty()) {
                                // Con huellas dibujadas, la entrada del lugar es solo un punto sobre el edificio
                                drawCircle(Color.White, 10f, center)
                                drawCircle(strokeColor, 7f, center)
                                return@forEach
                            }
                            // Polígono/rectángulo de edificio
                            val w = 54f
                            val h = 38f
                            val rectOffset = center - Offset(w / 2f, h / 2f)

                            // Relleno
                            drawRoundRect(
                                color = fillColor,
                                topLeft = rectOffset,
                                size = Size(w, h),
                                cornerRadius = CornerRadius(8f, 8f),
                                style = Fill
                            )
                            // Borde
                            drawRoundRect(
                                color = strokeColor,
                                topLeft = rectOffset,
                                size = Size(w, h),
                                cornerRadius = CornerRadius(8f, 8f),
                                style = Stroke(width = 2.5f)
                            )
                        }

                        "ENTRADA" -> {
                            // Puertas de acceso institucional
                            drawCircle(
                                color = if (hasShapes) MapTheme.ARROW_YELLOW else Color(0xFF00796B),
                                radius = 13f,
                                center = center,
                                style = Fill
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 13f,
                                center = center,
                                style = Stroke(3f)
                            )
                        }

                        "ZONA_SEGURA" -> {
                            // Punto de encuentro / zona segura: señal verde de evacuación (flechas hacia las personas)
                            drawSafeZoneIcon(center)
                        }
                    }
                }
            }

            // 3. ETIQUETAS DE TEXTO LEGIBLES PARA BLOQUES Y EDIFICIOS
            // Se dibujan cuando el zoom permite lectura clara
            if (zoom >= 1.2f) {
                val ordenados = nodos.sortedBy { if (it.id == destinationNodeId || it.id == originNodeId) 0 else 1 }
                ordenados.forEach { node ->
                    val place = lugaresByNodoId[node.id] ?: return@forEach
                    // Con huellas dibujadas el código del bloque ya va dentro del edificio (salvo con zoom alto)
                    if (hasShapes && zoom < 2.8f && node.tipo == "DESTINO" && !place.codigo.isNullOrBlank()) return@forEach
                    val center = p.toScreen(node.latitud, node.longitud)

                    val label = when {
                        // Bloques de facultad: "Bloque B-16 (FACIVITEC)"
                        blockLabel(place.codigo, place.nombre) != null -> blockLabel(place.codigo, place.nombre)!!
                        node.tipo == "ENTRADA" -> place.nombre.replace("Puerta", "P").trim()
                        node.tipo == "ZONA_SEGURA" -> "Zona Segura"
                        else -> place.nombre
                    }

                    val textYOffset = if (node.tipo == "DESTINO") 5f else 32f

                    // Fondo de pastilla para contraste del texto
                    labelPaint.color = if (MapTheme.dark) android.graphics.Color.rgb(226, 232, 240) else android.graphics.Color.rgb(30, 41, 59)
                    labelBgPaint.color = if (MapTheme.dark) android.graphics.Color.argb(210, 30, 38, 42) else android.graphics.Color.argb(220, 255, 255, 255)
                    labelPaint.textSize = if (zoom > 3f) 30f else 24f

                    // Nombre completo, repartido en varias líneas (máx. ~18 caracteres por línea)
                    val lines = wrapLabel(label, 18)
                    val lineH = labelPaint.textSize * 1.12f
                    val textWidth = lines.maxOf { labelPaint.measureText(it) }
                    val bgRect = android.graphics.RectF(
                        center.x - textWidth / 2f - 8f,
                        center.y + textYOffset - labelPaint.textSize + 4f,
                        center.x + textWidth / 2f + 8f,
                        center.y + textYOffset + 8f + lineH * (lines.size - 1)
                    )

                    val prioritaria = node.id == destinationNodeId || node.id == originNodeId
                    if (!placer.tryPlace(bgRect.left, bgRect.top, bgRect.right, bgRect.bottom) && !prioritaria) return@forEach
                    drawContext.canvas.nativeCanvas.drawRoundRect(bgRect, 8f, 8f, labelBgPaint)
                    lines.forEachIndexed { i, line ->
                        drawContext.canvas.nativeCanvas.drawText(line, center.x, center.y + textYOffset + i * lineH, labelPaint)
                    }
                }
            }

            // 4. TRAZADO DE RUTA ACTIVA (A*)
            routeNodeIds?.let { _ ->
                // Durante la navegación el tramo ya recorrido se atenúa y solo lo que falta queda resaltado
                val progress = navProgress
                progress?.let { pr ->
                    val done = pr.traveled.map { p.toScreen(it.lat, it.lon) }
                    for (i in 0 until done.size - 1) {
                        drawLine(
                            color = Color(0x669E9E9E),
                            start = done[i],
                            end = done[i + 1],
                            strokeWidth = 8f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                val points = progress?.remaining?.map { p.toScreen(it.lat, it.lon) }
                    ?: (routePoints ?: emptyList()).map { p.toScreen(it.lat, it.lon) }
                // Con GPS real, línea punteada desde la posición del usuario hasta el inicio de la ruta
                val u = animatedUser
                if (progress == null && !isSimulationMode && u != null && points.isNotEmpty()) {
                    val us = p.toScreen(u.lat, u.lon)
                    if ((us - points.first()).getDistance() > 12f) {
                        drawLine(
                            color = if (hasShapes) Color(0xFF0288D1) else Color(0xFF1976D2),
                            start = us,
                            end = points.first(),
                            strokeWidth = 5f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 12f))
                        )
                    }
                }
                if (points.size >= 2) {
                    // Halo de contraste exterior
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = if (hasShapes) Color(0xCCFFFFFF) else Color(0x550D47A1),
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 15f,
                            cap = StrokeCap.Round
                        )
                    }
                    // Línea principal azul ULEAM
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = if (hasShapes) Color(0xFF0288D1) else Color(0xFF1976D2),
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 8.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // 5. MARCADOR DE ORIGEN Y DESTINO
            originNodeId?.let { nodoById[it] }?.let { orig ->
                val o = p.toScreen(orig.latitud, orig.longitud)
                drawCircle(Color.White, 16f, o)
                drawCircle(Color(0xFF2E7D32), 16f, o, style = Stroke(5f))
                drawCircle(Color(0xFF2E7D32), 6f, o)
            }

            destinationNodeId?.let { nodoById[it] }?.let { dest ->
                val o = p.toScreen(dest.latitud, dest.longitud)
                drawCircle(Color.White, 18f, o)
                drawCircle(Color(0xFFC2185B), 18f, o, style = Stroke(5f))
                drawCircle(Color(0xFFC2185B), 8f, o)
            }

            // 6. MARCADOR DE USUARIO EN VIVO (PULSING BLUE DOT + CONO DE VISIÓN 45° + INCERTIDUMBRE)
            animatedUser?.let { u ->
                val userScreen = p.toScreen(u.lat, u.lon)

                // Círculo de incertidumbre métrica GNSS
                u.accuracyM?.let { acc ->
                    val radiusPx = (acc / 111_320f) * p.base * zoom
                    val clampedRadius = radiusPx.coerceIn(12f, 350f)
                    drawCircle(
                        color = Color(0x221976D2),
                        radius = clampedRadius,
                        center = userScreen
                    )
                    drawCircle(
                        color = Color(0x441976D2),
                        radius = clampedRadius,
                        center = userScreen,
                        style = Stroke(1.5f)
                    )
                }

                // Cono de visión azimutal translúcido de 45° (FOV) orientado según la brújula
                (if (navigating && isSimulationMode) headingDeg else azimuthDeg)?.let { headingDeg ->
                    // En coordenadas geográficas 0° = Norte (arriba, -90° en Canvas)
                    val screenHeadingDeg = headingDeg + mapRotationDeg
                    val canvasCenterAngle = screenHeadingDeg - 90f
                    val fovDeg = 45f
                    val startAngle = canvasCenterAngle - (fovDeg / 2f)

                    val outerRadius = 72f
                    drawArc(
                        color = Color(0x2E1976D2),
                        startAngle = startAngle,
                        sweepAngle = fovDeg,
                        useCenter = true,
                        topLeft = userScreen - Offset(outerRadius, outerRadius),
                        size = Size(outerRadius * 2f, outerRadius * 2f)
                    )

                    val innerRadius = 46f
                    drawArc(
                        color = Color(0x521976D2),
                        startAngle = startAngle,
                        sweepAngle = fovDeg,
                        useCenter = true,
                        topLeft = userScreen - Offset(innerRadius, innerRadius),
                        size = Size(innerRadius * 2f, innerRadius * 2f)
                    )
                }

                // Onda expansiva animada (pulse ripple)
                drawCircle(
                    color = Color(0xFF1976D2).copy(alpha = pulseAlpha),
                    radius = pulseRadius,
                    center = userScreen
                )

                // Núcleo blanco de borde
                drawCircle(
                    color = Color.White,
                    radius = 12f,
                    center = userScreen
                )
                // Punto azul cian/índigo central
                drawCircle(
                    color = Color(0xFF0D47A1),
                    radius = 8f,
                    center = userScreen
                )
            }
        }

        // Crédito obligatorio de los datos del mapa (licencia ODbL de OpenStreetMap): botón ⓘ que lo muestra al tocarlo
        if (hasShapes) {
            var showCredits by remember { mutableStateOf(false) }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = controlsBottomPadding)
                    .size(28.dp)
                    .background(Color(0xD9FFFFFF), CircleShape)
                    .clickable { showCredits = true }
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "Créditos del mapa",
                    tint = Color(0xFF546E7A),
                    modifier = Modifier.size(18.dp)
                )
            }
            if (showCredits) {
                AlertDialog(
                    onDismissRequest = { showCredits = false },
                    confirmButton = { TextButton(onClick = { showCredits = false }) { Text("Cerrar") } },
                    title = { Text("Créditos del mapa") },
                    text = {
                        Text(
                            "Edificios, estadio y canchas: © OpenStreetMap contributors, " +
                                "disponibles bajo la licencia Open Database License (ODbL).\n" +
                                "openstreetmap.org/copyright\n\n" +
                                "Los datos están incluidos en la app: el mapa funciona sin conexión."
                        )
                    }
                )
            }
        }

        // Aviso cuando el GPS ubica al usuario fuera de la universidad (el mapa se queda en el campus)
        if (userOutsideCampus) {
            Text(
                text = "📍 No te encuentras en el campus ULEAM",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 196.dp)
                    .background(Color(0xE6A01B2C), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        // 7. BOTONES FLOTANTES DE BRÚJULA / NORTE Y RECENTRADO
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = controlsBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (abs(mapRotationDeg) > 0.5f || azimuthDeg != null) {
                SmallFloatingActionButton(
                    onClick = {
                        followHeading = false // tocar la brújula devuelve el control de la orientación al usuario
                        // Si el mapa está rotado vuelve al Norte (0°); si está en 0° y hay brújula, orienta el mapa al rumbo del usuario
                        mapRotationDeg = if (abs(mapRotationDeg) > 1f) {
                            0f
                        } else {
                            azimuthDeg?.let { -it } ?: 0f
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = if (MapTheme.dark) Color(0xFF64B5F6) else Color(0xFFA01B2C),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Orientar Norte / Brújula",
                        modifier = Modifier.rotate(mapRotationDeg)
                    )
                }
            }

            // Aparece cuando el usuario queda fuera de pantalla (o al soltar el seguimiento en plena ruta)
            if (showMyLocationButton) {
                ExtendedFloatingActionButton(
                    onClick = { centrarEnUsuario() },
                    containerColor = Color(0xFF1976D2),
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                    text = { Text("Mi ubicación") }
                )
            }

            if (!showMyLocationButton) {
                FloatingActionButton(
                    onClick = { centrarEnUsuario() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Centrar mapa"
                    )
                }
            }
        }
    }
}

/** Margen (grados, ≈ 45 m) que se deja ver alrededor del campus al limitar la cámara. */
private const val CAMERA_MARGIN_DEG = 0.0004

/**
 * Señal de "Zona segura / Punto de encuentro" como la de evacuación: cuadro verde con borde blanco,
 * cuatro flechas blancas que apuntan al centro y una persona en medio.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSafeZoneIcon(c: Offset) {
    val half = 22f
    drawRoundRect(Color(0x55000000), c - Offset(half - 2f, half - 3f), Size(half * 2, half * 2), CornerRadius(7f, 7f))
    drawRoundRect(Color.White, c - Offset(half + 2f, half + 2f), Size(half * 2 + 4f, half * 2 + 4f), CornerRadius(8f, 8f))
    drawRoundRect(Color(0xFF1B8A3A), c - Offset(half, half), Size(half * 2, half * 2), CornerRadius(6f, 6f))
    // flechas hacia el centro (arriba, abajo, izquierda, derecha)
    val tip = 9f; val base = 17f; val w = 5f
    for ((dx, dy) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) {
        val px = -dy; val py = dx
        val arrow = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x + dx * tip, c.y + dy * tip)
            lineTo(c.x + dx * base + px * w, c.y + dy * base + py * w)
            lineTo(c.x + dx * base - px * w, c.y + dy * base - py * w)
            close()
        }
        drawPath(arrow, Color.White)
    }
    // personas en el centro
    drawCircle(Color.White, 2.8f, c + Offset(0f, -4.6f))
    drawRoundRect(Color.White, c + Offset(-4f, -0.8f), Size(8f, 6.8f), CornerRadius(4f, 3f))
}

/** Etiqueta de un bloque de facultad en el mapa: código "FACIVITEC·B-16" → "Bloque B-16 (FACIVITEC)". */
internal fun blockLabel(codigo: String?, nombre: String): String? {
    if (codigo.isNullOrBlank() || !nombre.startsWith("Facultad")) return null
    val bloque = Regex("B-\\d+").find(codigo)?.value ?: return null
    val sigla = codigo.substringBefore('·', "").trim()
    return if (sigla.isEmpty()) "Bloque $bloque" else "Bloque $bloque ($sigla)"
}

/** Parte una etiqueta en líneas de hasta [max] caracteres sin cortar palabras (una palabra más larga va sola). */
internal fun wrapLabel(text: String, max: Int): List<String> {
    val out = ArrayList<String>()
    var cur = StringBuilder()
    for (w in text.split(' ').filter { it.isNotEmpty() }) {
        if (cur.isNotEmpty() && cur.length + 1 + w.length > max) { out.add(cur.toString()); cur = StringBuilder() }
        if (cur.isNotEmpty()) cur.append(' ')
        cur.append(w)
    }
    if (cur.isNotEmpty()) out.add(cur.toString())
    return out.ifEmpty { listOf(text) }
}
