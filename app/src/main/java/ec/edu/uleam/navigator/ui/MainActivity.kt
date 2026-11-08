package ec.edu.uleam.navigator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

class MainActivity : ComponentActivity() {
    private val vm: CampusViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { CampusApp(vm) } } }
    }
}

@Composable
fun CampusApp(vm: CampusViewModel) {
    val s = vm.ui
    if (s.loading) { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }; return }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = s.query, onValueChange = vm::onQuery, singleLine = true,
            label = { Text("Buscar bloque, cafetería, puerta…") },
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )
        if (s.results.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 220.dp).fillMaxWidth()) {
                items(s.results) { l ->
                    Text(
                        "${l.nombre}  ·  ${l.categoria}",
                        Modifier.fillMaxWidth().clickable { vm.elegirDestino(l) }.padding(12.dp)
                    )
                    HorizontalDivider()
                }
            }
        }
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Origen:", Modifier.align(Alignment.CenterVertically))
            (1..4).forEach { n -> OutlinedButton({ vm.elegirPuerta(n) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("P$n") } }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { MapCanvas(vm) }
        InfoPanel(vm)
    }
}

@Composable
fun InfoPanel(vm: CampusViewModel) {
    val s = vm.ui
    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Origen: ${s.origenLabel}")
            s.destino?.let { Text("Destino: ${it.nombre}" + (it.codigo?.let { c -> " ($c)" } ?: "")) }
            s.route?.let { Text("%.0f m  ·  ≈ %.0f min a pie".format(it.distanceM, it.minutes)) }
            s.msg?.let { Text(it, color = Color(0xFFB00020)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(s.avoidStairs, vm::toggleEscaleras); Spacer(Modifier.width(8.dp)); Text("Evitar escaleras")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(vm::zonaSegura, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text("Zona segura") }
                OutlinedButton(vm::limpiar) { Text("Limpiar") }
            }
        }
    }
}

/** Proyección simple lat/lon → píxeles (el campus es pequeño, no hace falta Mercator). */
private class Proj(
    val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double,
    val size: IntSize, val zoom: Float, val pan: Offset
) {
    private val m = 24f
    private val base = minOf(
        (size.width - 2 * m) / (maxLon - minLon).toFloat(),
        (size.height - 2 * m) / (maxLat - minLat).toFloat()
    )
    private val c = Offset(size.width / 2f, size.height / 2f)
    fun toScreen(lat: Double, lon: Double): Offset {
        val p = Offset(m + ((lon - minLon) * base).toFloat(), m + ((maxLat - lat) * base).toFloat())
        return (p - c) * zoom + c + pan
    }
    fun toGeo(o: Offset): Pair<Double, Double> {
        val p = (o - pan - c) / zoom + c
        return (maxLat - (p.y - m) / base) to (minLon + (p.x - m) / base)
    }
}

@Composable
fun MapCanvas(vm: CampusViewModel) {
    val s = vm.ui
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val nodos = vm.nodos; val byId = vm.nodoById
    val bounds = remember(nodos) {
        doubleArrayOf(nodos.minOf { it.latitud }, nodos.maxOf { it.latitud }, nodos.minOf { it.longitud }, nodos.maxOf { it.longitud })
    }
    fun proj() = Proj(bounds[0], bounds[1], bounds[2], bounds[3], size, zoom, pan)

    Canvas(
        Modifier.fillMaxSize().background(Color(0xFFF4F1EA)).onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    zoom = (zoom * zoomChange).coerceIn(1f, 12f); pan += panChange
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { o -> val (la, lo) = proj().toGeo(o); vm.ubicacionSimulada(la, lo) }
            }
    ) {
        val p = proj()
        // calles
        vm.aristas.forEach { e ->
            val a = byId[e.idOrigen] ?: return@forEach; val b = byId[e.idDestino] ?: return@forEach
            drawLine(
                if (e.tieneEscaleras == 1) Color(0xFF9E9E9E) else Color(0xFFBDBDBD),
                p.toScreen(a.latitud, a.longitud), p.toScreen(b.latitud, b.longitud),
                strokeWidth = if (e.tieneEscaleras == 1) 2f else 4f
            )
        }
        // ruta
        s.route?.nodeIds?.map { byId[it]!! }?.zipWithNext()?.forEach { (a, b) ->
            drawLine(Color(0xFF1976D2), p.toScreen(a.latitud, a.longitud), p.toScreen(b.latitud, b.longitud),
                strokeWidth = 10f, cap = StrokeCap.Round)
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
        // origen y destino
        s.origenId?.let { byId[it] }?.let {
            val o = p.toScreen(it.latitud, it.longitud)
            drawCircle(Color.White, 16f, o); drawCircle(Color(0xFF1976D2), 16f, o, style = Stroke(6f))
        }
        s.destino?.let { byId[it.idNodo] }?.let {
            val o = p.toScreen(it.latitud, it.longitud)
            drawRect(Color(0xFF6A1B9A), o - Offset(14f, 14f), Size(28f, 28f))
        }
    }
}
