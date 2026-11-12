package ec.edu.uleam.navigator.ui.map

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ec.edu.uleam.navigator.data.AristaEntity
import ec.edu.uleam.navigator.data.LugarEntity
import ec.edu.uleam.navigator.data.NodoEntity
import ec.edu.uleam.navigator.ui.UserPosition
import kotlin.math.abs

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
    nodoById: Map<Int, NodoEntity>,
    routeNodeIds: List<Int>?,
    originNodeId: Int?,
    destinationNodeId: Int?,
    userPosition: UserPosition?,
    isSimulationMode: Boolean,
    onMapTap: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
    azimuthDeg: Float? = null
) {
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    var zoom by remember { mutableFloatStateOf(1.2f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var mapRotationDeg by remember { mutableFloatStateOf(0f) }
    val currentOnTap by rememberUpdatedState(onMapTap)

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

    val lugaresByNodoId = remember(lugares) {
        lugares.associateBy { it.idNodo }
    }

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

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF6F5F0)) // Fondo crema cartográfico suave institucional
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, rotationChange ->
                        zoom = (zoom * zoomChange).coerceIn(0.9f, 15f)
                        if (abs(rotationChange) > 0.05f) {
                            mapRotationDeg = (mapRotationDeg + rotationChange) % 360f
                        }
                        pan += proj().rotatePanDelta(panChange)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val (lat, lon) = proj().toGeo(offset)
                        currentOnTap(lat, lon)
                    }
                }
        ) {
            val p = proj()

            // 1. DIBUJAR RED PEATONAL Y ARISTAS
            aristas.forEach { edge ->
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
                                color = Color(0xFF00796B),
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
                            // Zonas seguras de evacuación rápida
                            drawCircle(
                                color = Color(0xFFFFEBEE),
                                radius = 22f,
                                center = center,
                                style = Fill
                            )
                            drawCircle(
                                color = Color(0xFFD32F2F),
                                radius = 22f,
                                center = center,
                                style = Stroke(4f)
                            )
                            drawCircle(
                                color = Color(0xFFD32F2F),
                                radius = 10f,
                                center = center,
                                style = Fill
                            )
                        }
                    }
                }
            }

            // 3. ETIQUETAS DE TEXTO LEGIBLES PARA BLOQUES Y EDIFICIOS
            // Se dibujan cuando el zoom permite lectura clara
            if (zoom >= 1.2f) {
                nodos.forEach { node ->
                    val place = lugaresByNodoId[node.id] ?: return@forEach
                    val center = p.toScreen(node.latitud, node.longitud)

                    val label = when {
                        !place.codigo.isNullOrBlank() -> place.codigo!!
                        node.tipo == "ENTRADA" -> place.nombre.replace("Puerta", "P").trim()
                        node.tipo == "ZONA_SEGURA" -> "Zona Segura"
                        place.nombre.length > 14 -> place.nombre.take(13) + "…"
                        else -> place.nombre
                    }

                    val textYOffset = if (node.tipo == "DESTINO") 5f else 32f

                    // Fondo de pastilla para contraste del texto
                    labelPaint.color = android.graphics.Color.rgb(30, 41, 59)
                    labelPaint.textSize = if (zoom > 3f) 30f else 24f

                    val textWidth = labelPaint.measureText(label)
                    val bgRect = android.graphics.RectF(
                        center.x - textWidth / 2f - 8f,
                        center.y + textYOffset - labelPaint.textSize + 4f,
                        center.x + textWidth / 2f + 8f,
                        center.y + textYOffset + 8f
                    )

                    drawContext.canvas.nativeCanvas.drawRoundRect(bgRect, 8f, 8f, labelBgPaint)
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        center.x,
                        center.y + textYOffset,
                        labelPaint
                    )
                }
            }

            // 4. TRAZADO DE RUTA ACTIVA (A*)
            routeNodeIds?.let { ids ->
                val points = ids.mapNotNull { id -> nodoById[id]?.let { p.toScreen(it.latitud, it.longitud) } }
                if (points.size >= 2) {
                    // Halo de contraste exterior
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = Color(0x550D47A1),
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 15f,
                            cap = StrokeCap.Round
                        )
                    }
                    // Línea principal azul ULEAM
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = Color(0xFF1976D2),
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
            userPosition?.let { u ->
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
                azimuthDeg?.let { headingDeg ->
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

        // 7. BOTONES FLOTANTES DE BRÚJULA / NORTE Y RECENTRADO
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (abs(mapRotationDeg) > 0.5f || azimuthDeg != null) {
                SmallFloatingActionButton(
                    onClick = {
                        // Si el mapa está rotado vuelve al Norte (0°); si está en 0° y hay brújula, orienta el mapa al rumbo del usuario
                        mapRotationDeg = if (abs(mapRotationDeg) > 1f) {
                            0f
                        } else {
                            azimuthDeg?.let { -it } ?: 0f
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Color(0xFFA01B2C),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Orientar Norte / Brújula",
                        modifier = Modifier.rotate(mapRotationDeg)
                    )
                }
            }

            FloatingActionButton(
                onClick = {
                    val targetPos = userPosition ?: originNodeId?.let { id ->
                        nodoById[id]?.let { UserPosition(it.latitud, it.longitud) }
                    }
                    if (targetPos != null) {
                        val targetZoom = 3.5f
                        val raw = proj(1f, Offset.Zero, 0f).toScreen(targetPos.lat, targetPos.lon)
                        zoom = targetZoom
                        pan = -(raw - proj(1f, Offset.Zero, 0f).center) * targetZoom
                    }
                },
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

