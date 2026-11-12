package ec.edu.uleam.navigator.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ec.edu.uleam.navigator.location.LocationState
import ec.edu.uleam.navigator.routing.astar.DirectionType
import ec.edu.uleam.navigator.routing.astar.NavigationInstruction
import ec.edu.uleam.navigator.ui.map.CampusMap

class MainActivity : ComponentActivity() {
    private val vm: CampusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CampusApp(vm)
                }
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun CampusApp(vm: CampusViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Suscripción segura al ciclo de vida para optimización de batería
    val locationState by vm.locationState.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.STARTED
    )
    val azimuthDeg by vm.azimuthDeg.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.STARTED
    )

    // Estados para el manejo resiliente de permisos
    var permissionDenied by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            permissionDenied = false
            permanentlyDenied = false
            vm.refreshLocationPermissionState()
        } else {
            permissionDenied = true
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) ||
                        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_COARSE_LOCATION)
            } ?: false
            permanentlyDenied = !shouldShowRationale
        }
    }

    // Comprobación inicial de permisos
    LaunchedEffect(Unit) {
        if (!hasLocationPermission(context)) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Actualizar ubicación real al ViewModel cuando no está en modo simulación
    LaunchedEffect(locationState, vm.ui.isSimulationMode) {
        if (locationState is LocationState.PermissionDenied && hasLocationPermission(context)) {
            permissionDenied = false
            permanentlyDenied = false
            vm.refreshLocationPermissionState()
        }
        if (!vm.ui.isSimulationMode) {
            when (val s = locationState) {
                is LocationState.Fix -> vm.onGpsLocation(s.lat, s.lon)
                is LocationState.LowAccuracy -> vm.onGpsLocation(s.lat, s.lon)
                else -> {}
            }
        }
    }

    val s = vm.ui
    if (s.loading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFA01B2C))
                Spacer(Modifier.height(12.dp))
                Text("Cargando cartografía ULEAM…", fontWeight = FontWeight.Medium)
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        // CAPA 1: MAPA CANVAS BASE
        CampusMap(
            nodos = vm.nodos,
            aristas = vm.aristas,
            lugares = vm.lugares,
            nodoById = vm.nodoById,
            routeNodeIds = s.route?.nodeIds,
            originNodeId = s.origenId,
            destinationNodeId = s.destino?.idNodo,
            userPosition = vm.userPosition,
            isSimulationMode = s.isSimulationMode,
            onMapTap = vm::onMapTap,
            modifier = Modifier.fillMaxSize(),
            azimuthDeg = azimuthDeg
        )

        // CAPA 2: CONTROLES SUPERIORES (Búsqueda + Selector de Puertas + Conmutador Demo)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Tarjeta de Búsqueda Institucional
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(6.dp)) {
                    OutlinedTextField(
                        value = s.query,
                        onValueChange = vm::onQuery,
                        singleLine = true,
                        placeholder = { Text("Buscar bloque, cafetería, puerta, FCVT…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color(0xFFA01B2C)) },
                        trailingIcon = {
                            if (s.query.isNotEmpty()) {
                                IconButton(onClick = { vm.onQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar")
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Resultados de búsqueda
                    if (s.results.isNotEmpty()) {
                        HorizontalDivider()
                        LazyColumn(Modifier.heightIn(max = 240.dp).fillMaxWidth()) {
                            items(s.results) { place ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.elegirDestino(place) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            place.nombre,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            place.categoria + (place.codigo?.let { " · $it" } ?: ""),
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Icon(
                                        Icons.Default.AltRoute,
                                        contentDescription = "Ir",
                                        tint = Color(0xFF1565C0),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                HorizontalDivider(color = Color(0xFFEEEEEE))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Barra rápida de selección de puertas origen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCCFFFFFF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Puertas:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                (1..4).forEach { n ->
                    val isSelected = s.origenId == (99 + n)
                    OutlinedButton(
                        onClick = { vm.elegirPuerta(n) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = if (isSelected) ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(
                            "P$n",
                            fontSize = 12.sp,
                            color = if (isSelected) Color.White else Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // AVISO RESILIENTE DE PERMISOS DENEGADOS (ROL 2)
            if (permissionDenied && !s.isSimulationMode) {
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD32F2F))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (permanentlyDenied) "Permiso de ubicación denegado permanentemente"
                                else "Permiso de ubicación requerido",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD32F2F),
                                fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (permanentlyDenied)
                                "Para usar el GPS en vivo, abre los ajustes del sistema. También puedes utilizar el Modo Simulación sin permisos."
                            else
                                "Permite el acceso a tu ubicación para trazar rutas desde donde estés en el campus.",
                            fontSize = 12.sp,
                            color = Color(0xFF424242)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (permanentlyDenied) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Ajustes", fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reintentar", fontSize = 12.sp)
                                }
                            }
                            OutlinedButton(
                                onClick = { vm.toggleSimulationMode(true) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Usar Modo Simulación", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // CAPA 3: TARJETA FLOTANTE DE DESTINO Y NAVEGACIÓN COLAPSABLE (ROL 4)
        CollapsibleNavigationCard(
            vm = vm,
            locationState = locationState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
        )
    }
}

/**
 * Tarjeta flotante colapsable con diseño institucional ULEAM.
 * Contiene:
 * - Resumen métrico (distancia y minutos estimados a 1.2 m/s).
 * - Indicador de estado del sensor GPS vs Modo Simulación.
 * - Desglose guiado Turn-by-Turn con iconos direccionales y distancias.
 * - Conmutadores para Evitar Escaleras y Modo Simulación (Demo).
 * - Botón de evacuación rápida hacia Zonas Seguras.
 */
@Composable
fun CollapsibleNavigationCard(
    vm: CampusViewModel,
    locationState: LocationState,
    modifier: Modifier = Modifier
) {
    val s = vm.ui
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        modifier = modifier.animateContentSize()
    ) {
        Column(Modifier.padding(14.dp)) {
            // CABECERA: Título, estado y botón de colapso
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = s.destino?.nombre ?: "Selecciona un destino",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = if (s.destino != null) Color(0xFFA01B2C) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Origen: ${s.origenLabel}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                // Botón expandir / colapsar
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (expanded) "Colapsar" else "Expandir",
                        tint = Color(0xFFA01B2C)
                    )
                }
            }

            // RESUMEN MÉTRICO PRINCIPAL DE RUTA
            if (s.route != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8F0FE), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        tint = Color(0xFF1976D2),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "%.0f m  ·  ≈ %.1f min a pie".format(s.route.distanceM, s.route.estimatedMinutes),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF0D47A1)
                    )
                    if (s.route.usesStairs) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "(con escaleras)",
                            fontSize = 11.sp,
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // MENSAJES DE ERROR O AVISOS INFORMATIVOS
            s.msg?.let { errorText ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = errorText,
                    color = Color(0xFFD32F2F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            s.infoMsg?.let { infoText ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = infoText,
                    color = Color(0xFF2E7D32),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // SECCIÓN EXPANDIDA: INSTRUCCIONES TURN-BY-TURN Y CONTROLES AVANZADOS
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(Modifier.padding(top = 10.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    // ESTADO GNSS / SIMULACIÓN (ROL 2 & 3)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (s.isSimulationMode) "Modo Simulación (Demo)" else when (locationState) {
                                is LocationState.Fix -> "GPS: Señal fija (±%.0f m)".format((locationState as LocationState.Fix).accuracyM)
                                is LocationState.LowAccuracy -> "GPS: Señal débil"
                                LocationState.Searching -> "GPS: Buscando satélites…"
                                LocationState.PermissionDenied -> "GPS: Sin permisos"
                                LocationState.ProviderDisabled -> "GPS: Desactivado"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (s.isSimulationMode) Color(0xFFE65100) else Color(0xFF1565C0)
                        )

                        // SWITCH MODO SIMULACIÓN (DEMO TOGGLE)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Demo", fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Switch(
                                checked = s.isSimulationMode,
                                onCheckedChange = vm::toggleSimulationMode,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE65100))
                            )
                        }
                    }

                    // SWITCH EVITAR ESCALERAS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Accesibilidad (Evitar escaleras):", fontSize = 13.sp)
                        Switch(
                            checked = s.avoidStairs,
                            onCheckedChange = vm::toggleEscaleras,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFA01B2C))
                        )
                    }

                    // LISTADO DE INSTRUCCIONES TURN-BY-TURN (ROL 4)
                    if (s.instructions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Instrucciones de recorrido:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF333333)
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier
                                .heightIn(max = 160.dp)
                                .fillMaxWidth()
                                .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(s.instructions) { inst ->
                                InstructionRow(inst)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ACCIONES PRINCIPALES: ZONA SEGURA (EVACUACIÓN) Y LIMPIAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Botón Zona Segura destacado en Rojo Evacuación ULEAM
                Button(
                    onClick = vm::zonaSegura,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Zona Segura", fontWeight = FontWeight.Bold)
                }

                // Botón Limpiar
                OutlinedButton(
                    onClick = vm::limpiar,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Limpiar")
                }
            }
        }
    }
}

@Composable
fun InstructionRow(instruction: NavigationInstruction) {
    val icon = when (instruction.direction) {
        DirectionType.DEPART -> Icons.Default.DirectionsWalk
        DirectionType.STRAIGHT -> Icons.Default.Straight
        DirectionType.TURN_RIGHT -> Icons.Default.TurnRight
        DirectionType.TURN_LEFT -> Icons.Default.TurnLeft
        DirectionType.TURN_SHARP_RIGHT -> Icons.Default.TurnSharpRight
        DirectionType.TURN_SHARP_LEFT -> Icons.Default.TurnSharpLeft
        DirectionType.STAIRS -> Icons.Default.AltRoute
        DirectionType.ARRIVE -> Icons.Default.Security
    }

    val iconColor = when (instruction.direction) {
        DirectionType.ARRIVE -> Color(0xFF2E7D32)
        DirectionType.STAIRS -> Color(0xFFE65100)
        else -> Color(0xFF1976D2)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(iconColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = instruction.text,
            fontSize = 12.sp,
            color = Color(0xFF263238),
            modifier = Modifier.weight(1f)
        )
    }
}
