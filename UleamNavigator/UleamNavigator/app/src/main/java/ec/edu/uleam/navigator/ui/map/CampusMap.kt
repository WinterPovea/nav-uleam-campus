package ec.edu.uleam.navigator.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ec.edu.uleam.navigator.data.AristaEntity
import ec.edu.uleam.navigator.data.NodoEntity

/** Posición del usuario en tiempo real (la entrega G4). accuracyM = error estimado en metros. */
data class UserPosition(val lat: Double, val lon: Double, val accuracyM: Float? = null)

/** Proyección simple lat/lon -> píxeles (el campus es pequeño, no hace falta Mercator). */
private class MapProjection(
    val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double,
    val size: IntSize, val zoom: Float, val pan: Offset
) {
    private val margin = 24f
    val base: Float = minOf(
        (size.width - 2 * margin) / (maxLon - minLon).toFloat(),
        (size.height - 2 * margin) / (maxLat - minLat).toFloat()
    )
    val center = Offset(size.width / 2f, size.height / 2f)

    fun toScreen(lat: Double, lon: Double): Offset {
        val p = Offset(
            margin + ((lon - minLon) * base).toFloat(),
            margin + ((maxLat - lat) * base).toFloat()
        )
        return (p - center) * zoom + center + pan
    }

    fun toGeo(o: Offset): Pair<Double, Double> {
        val p = (o - pan - center) / zoom + center
        return (maxLat - (p.y - margin) / base) to (minLon + (p.x - margin) / base)
    }
}

/**
 * Mapa reutilizable del campus. No depende del ViewModel: recibe todo por parámetros.
 *
 * - Pellizcar = zoom, arrastrar = mover, tocar = onMapTap(lat, lon).
 * - Dibuja calles, la ruta activa, origen/destino y la posición en vivo del usuario.
 * - Si hay userPosition, aparece un botón para centrar el mapa en el usuario.
 */
@Composable
fun CampusMap(
    nodos: List<NodoEntity>,
    aristas: List<AristaEntity>,
    nodoById: Map<Int, NodoEntity>,
    routeNodeIds: List<Int>?,
    originNodeId: Int?,
    destinationNodeId: Int?,
    userPosition: UserPosition?,
    onMapTap: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val currentOnTap by rememberUpdatedState(onMapTap)

    val bounds = remember(nodos) {
        if (nodos.isEmpty()) doubleArrayOf(0.0, 1.0, 0.0, 1.0)
        else doubleArrayOf(
            nodos.minOf { it.latitud }, nodos.maxOf { it.latitud },
            nodos.minOf { it.longitud }, nodos.maxOf { it.longitud }
        )
    }
    fun proj(z: Float = zoom, p: Offset = pan) =
        MapProjection(bounds[0], bounds[1], bounds[2], bounds[3], size, z, p)

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF4F1EA))
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(1f, 12f)
                        pan += panChange
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        val (la, lo) = proj().toGeo(o)
                        currentOnTap(la, lo)
                    }
                }
        ) {
            val p = proj()

            // calles (las de escaleras, más delgadas y oscuras)
            aristas.forEach { e ->
                val a = nodoById[e.idOrigen] ?: return@forEach
                val b = nodoById[e.idDestino] ?: return@forEach
                drawLine(
                    if (e.tieneEscaleras == 1) Color(0xFF9E9E9E) else Color(0xFFBDBDBD),
                    p.toScreen(a.latitud, a.longitud), p.toScreen(b.latitud, b.longitud),
                    strokeWidth = if (e.tieneEscaleras == 1) 2f else 4f
                )
            }

            // ruta activa
            routeNodeIds?.mapNotNull { nodoById[it] }?.zipWithNext()?.forEach { (a, b) ->
                drawLine(
                    Color(0xFF1976D2),
                    p.toScreen(a.latitud, a.longitud), p.toScreen(b.latitud, b.longitud),
                    strokeWidth = 10f, cap = StrokeCap.Round
                )
            }

            // nodos
            nodos.forEach { n ->
                val o = p.toScreen(n.latitud, n.longitud)
                when (n.tipo) {
                    "DESTINO" -> drawCircle(Color(0xFFEF6C00), 8f, o)
                    "ENTRADA" -> drawCircle(Color(0xFF2E7D32), 12f, o)
                    "ZONA_SEGURA" -> drawCircle(Color(0xFFD32F2F), 14f, o)
                }
            }

            // origen
            originNodeId?.let { nodoById[it] }?.let {
                val o = p.toScreen(it.latitud, it.longitud)
                drawCircle(Color.White, 16f, o)
                drawCircle(Color(0xFF1976D2), 16f, o, style = Stroke(6f))
            }

            // destino
            destinationNodeId?.let { nodoById[it] }?.let {
                val o = p.toScreen(it.latitud, it.longitud)
                drawRect(Color(0xFF6A1B9A), o - Offset(14f, 14f), Size(28f, 28f))
            }

            // posición del usuario en vivo (círculo de precisión + punto)
            userPosition?.let { u ->
                val o = p.toScreen(u.lat, u.lon)
                u.accuracyM?.let { acc ->
                    // 1 grado de latitud ~ 111320 m; base = píxeles por grado
                    val radiusPx = (acc / 111_320f) * p.base * zoom
                    drawCircle(Color(0x331976D2), radiusPx.coerceIn(10f, 400f), o)
                }
                drawCircle(Color.White, 13f, o)
                drawCircle(Color(0xFF1565C0), 9f, o)
            }
        }

        // botón "centrar en mí"
        if (userPosition != null) {
            SmallFloatingActionButton(
                onClick = {
                    val target = 4f
                    val raw = proj(1f, Offset.Zero).toScreen(userPosition.lat, userPosition.lon)
                    zoom = target
                    pan = -(raw - proj(1f, Offset.Zero).center) * target
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) { Text("◎") }
        }
    }
}