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
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import ec.edu.uleam.navigator.ui.map.CampusMap
import ec.edu.uleam.navigator.location.LocationState
class MainActivity : ComponentActivity() {
    private val vm: CampusViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { CampusApp(vm) } } }
    }
}

@Composable
fun CampusApp(vm: CampusViewModel) {
    val permisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.iniciarUbicacion() }
    LaunchedEffect(Unit) {
        permisos.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }
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
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CampusMap(
                nodos = vm.nodos,
                aristas = vm.aristas,
                nodoById = vm.nodoById,
                routeNodeIds = s.route?.nodeIds,
                originNodeId = s.origenId,
                destinationNodeId = s.destino?.idNodo,
                userPosition = vm.userPosition,
                onMapTap = vm::ubicacionSimulada
            )
        }
        InfoPanel(vm)
    }
}

@Composable
fun InfoPanel(vm: CampusViewModel) {
    val s = vm.ui
    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Origen: ${s.origenLabel}")
            Text(when (vm.gpsState) {
                is LocationState.Fix -> "GPS: ubicación activa"
                is LocationState.LowAccuracy -> "GPS: señal débil"
                LocationState.Searching -> "GPS: buscando…"
                LocationState.PermissionDenied -> "GPS: permiso denegado"
                LocationState.ProviderDisabled -> "GPS: apagado, actívalo"
            })
            s.destino?.let { Text("Destino: ${it.nombre}" + (it.codigo?.let { c -> " ($c)" } ?: "")) }
            s.route?.let { Text("%.0f m  ·  ≈ %.0f min a pie".format(it.distanceM, it.minutes)) }
            s.msg?.let { Text(it, color = Color(0xFFB00020)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(s.avoidStairs, vm::toggleEscaleras); Spacer(Modifier.width(8.dp)); Text("Evitar escaleras")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(vm.gpsActivo, vm::toggleGps); Spacer(Modifier.width(8.dp)); Text("Usar GPS como origen")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(vm::zonaSegura, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text("Zona segura") }
                OutlinedButton(vm::limpiar) { Text("Limpiar") }
            }
        }
    }
}



