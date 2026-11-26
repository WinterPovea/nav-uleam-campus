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
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import ec.edu.uleam.navigator.ui.map.GeoPoint
import ec.edu.uleam.navigator.ui.map.RouteProgressCalc
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import ec.edu.uleam.navigator.ui.map.blockLabel
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import ec.edu.uleam.navigator.ui.map.MapTheme
import ec.edu.uleam.navigator.ui.map.MapStylePrefs
import ec.edu.uleam.navigator.ui.map.MapStyle
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import android.widget.Toast
import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.onFocusChanged
import ec.edu.uleam.navigator.ui.voice.VoiceGuide

class MainActivity : ComponentActivity() {
    private val vm: CampusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Se decide el modo oscuro ANTES del primer cuadro: así no se ve un destello claro al abrir en modo oscuro.
        val sistemaOscuro = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val oscuro = (MapStylePrefs(this).load() ?: MapStyle.CLARO).esOscuro(sistemaOscuro)
        MapTheme.dark = oscuro
        if (oscuro) setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(if (oscuro) 0xFF12171A.toInt() else 0xFFFFFFFF.toInt())
        setContent {
            // La interfaz sigue el estilo del mapa (claro / oscuro / sistema): MapTheme.dark es estado de Compose
            MaterialTheme(colorScheme = if (MapTheme.dark) UleamDarkColors else UleamLightColors) {
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

/** Esquemas de color de la interfaz (búsqueda, tarjetas, diálogos) para modo claro y oscuro. */
private val UleamLightColors = lightColorScheme()
private val UleamDarkColors = darkColorScheme(
    primary = Color(0xFF64B5F6),
    background = Color(0xFF12171A),
    surface = Color(0xFF1E2427),
    surfaceVariant = Color(0xFF2A3236),
    onSurface = Color(0xFFE3E7EA),
    onSurfaceVariant = Color(0xFFB8C2C7),
    outlineVariant = Color(0xFF3A4448)
)

/** Color de la interfaz según el modo: [light] en claro y [dark] en oscuro. */
private fun tc(light: Long, dark: Long): Color = Color(if (MapTheme.dark) dark else light)

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    // Actualizar la ubicación real en el ViewModel
    LaunchedEffect(locationState) {
        if (locationState is LocationState.PermissionDenied && hasLocationPermission(context)) {
            permissionDenied = false
            permanentlyDenied = false
            vm.refreshLocationPermissionState()
        }
        when (val s = locationState) {
            is LocationState.Fix -> vm.onGpsLocation(s.lat, s.lon, s.accuracyM)
            is LocationState.LowAccuracy -> vm.onGpsLocation(s.lat, s.lon, s.accuracyM)
            else -> {}
        }
    }

    val s = vm.ui
    if (s.loading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = tc(0xFFA01B2C, 0xFF64B5F6))
                Spacer(Modifier.height(12.dp))
                Text("Cargando cartografía ULEAM…", fontWeight = FontWeight.Medium)
            }
        }
        return
    }

    // Al elegir un destino (lista de búsqueda o toque en el mapa) se cierra el teclado y se quita el foco
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(s.destino?.idNodo) {
        if (s.destino != null) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    // ESTILO DEL MAPA: se pregunta la primera vez; luego el botón del mapa alterna Claro → Oscuro → Sistema
    val stylePrefs = remember { MapStylePrefs(context) }
    var mapStyle by remember { mutableStateOf<MapStyle?>(stylePrefs.load()) }
    val systemDark = isSystemInDarkTheme()
    val effectiveStyle = mapStyle ?: MapStyle.CLARO
    SideEffect { MapTheme.dark = effectiveStyle.esOscuro(systemDark) }
    fun guardarEstilo(st: MapStyle) {
        mapStyle = st
        stylePrefs.save(st)
    }
    if (mapStyle == null) {
        MapStyleDialog(onElegir = { guardarEstilo(it) })
    }

    // GUÍA POR VOZ: el ViewModel emite frases (inicio, giros, escaleras, llegada) y aquí se dicen con TextToSpeech
    val voiceGuide = remember { VoiceGuide(context) }
    DisposableEffect(Unit) { onDispose { voiceGuide.shutdown() } }
    LaunchedEffect(Unit) {
        vm.voz.collect { frase -> if (vm.ui.vozActiva) voiceGuide.speak(frase) }
    }
    LaunchedEffect(s.vozActiva) { if (!s.vozActiva) voiceGuide.stop() }

    var searchFocused by remember { mutableStateOf(false) }
    // Sugerencias abiertas (recientes, filtros o resultados): se cierran con la X, tocando el mapa o con "Atrás"
    val busquedaAbierta = searchFocused || s.categoria != null || s.results.isNotEmpty()
    fun cerrarBusqueda() {
        focusManager.clearFocus()
        keyboard?.hide()
        vm.cerrarBusqueda()
    }
    BackHandler(enabled = busquedaAbierta) { cerrarBusqueda() }

    // Altura de la tarjeta inferior: los botones flotantes del mapa quedan siempre por encima de ella
    // (se guarda en un estado que solo se lee al ubicar los botones: animar la tarjeta no redibuja todo el mapa)
    val cardHeightPx = remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        // CAPA 1: MAPA CANVAS BASE
        CampusMap(
            nodos = vm.nodos,
            aristas = vm.aristas,
            lugares = vm.lugares,
            shapes = vm.shapes,
            nodoById = vm.nodoById,
            routeNodeIds = s.route?.nodeIds,
            originNodeId = s.origenId,
            destinationNodeId = s.destino?.idNodo,
            userPosition = vm.userPosition,
            onMapTap = { _, _ -> if (busquedaAbierta) cerrarBusqueda() },
            onPlaceTap = { l -> if (busquedaAbierta) cerrarBusqueda() else vm.onPlaceTap(l) },
            modifier = Modifier.fillMaxSize(),
            azimuthDeg = azimuthDeg,
            navigating = s.navigating,
            controlsBottomPx = { cardHeightPx.intValue },
            mapStyle = effectiveStyle,
            routeLead = vm.leadActivo()
        )

        // CAPA 2: CONTROLES SUPERIORES (Búsqueda + Selector de Puertas + Conmutador Demo)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Tarjeta de Búsqueda: se oculta mientras se navega y reaparece al terminar o llegar al destino
            AnimatedVisibility(visible = !s.navigating) {
              Column {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 0.dp)) {
                    OutlinedTextField(
                        value = s.query,
                        onValueChange = vm::onQuery,
                        singleLine = true,
                        placeholder = {
                            Text(
                                "Buscar Bloque, Facultad, Carrera, Otros…",
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = tc(0xFFA01B2C, 0xFF64B5F6)) },
                        trailingIcon = {
                            if (s.query.isNotEmpty() || busquedaAbierta) {
                                IconButton(onClick = { cerrarBusqueda() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cerrar búsqueda")
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { searchFocused = it.isFocused }
                    )

                    // Filtros rápidos por categoría (al tocar la búsqueda o con un filtro activo)
                    if (searchFocused || s.categoria != null) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Categorias.CHIPS.forEach { (cat, etiqueta) ->
                                FilterChip(
                                    selected = s.categoria == cat,
                                    onClick = { vm.filtrarCategoria(cat) },
                                    label = { Text(etiqueta, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = tc(0xFFF6DADD, 0xFF1E3A50)
                                    )
                                )
                            }
                        }
                    }

                    // Resultados de búsqueda, o los destinos recientes si la búsqueda está vacía
                    val mostrarRecientes = s.results.isEmpty() && searchFocused && s.query.isEmpty() &&
                        s.categoria == null && s.recientes.isNotEmpty()
                    val lista = if (mostrarRecientes) s.recientes else s.results
                    if (lista.isNotEmpty()) {
                        HorizontalDivider()
                        if (mostrarRecientes) {
                            Row(Modifier.padding(start = 14.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Recientes", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        LazyColumn(Modifier.heightIn(max = 240.dp).fillMaxWidth()) {
                            items(lista) { place ->
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
                                        // Subtítulo: bloque + carreras/referencias para confirmar el destino
                                        val detalle = listOfNotNull(
                                            place.codigo?.takeIf { it.isNotBlank() },
                                            place.descripcion?.takeIf { it.isNotBlank() }
                                        ).joinToString(" · ").ifBlank { place.categoria }
                                        Text(
                                            detalle,
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        Icons.Default.AltRoute,
                                        contentDescription = "Ir",
                                        tint = tc(0xFF1565C0, 0xFF90CAF9),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
              }
            }

            // ESTILO DEL MAPA (arriba a la izquierda, bajo la búsqueda): alterna Claro → Oscuro → Sistema
            Spacer(Modifier.height(8.dp))
            SmallFloatingActionButton(
                onClick = {
                    val nuevo = effectiveStyle.siguiente()
                    guardarEstilo(nuevo)
                    Toast.makeText(context, "Mapa: ${nuevo.etiqueta}", Toast.LENGTH_SHORT).show()
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = tc(0xFFA01B2C, 0xFF64B5F6),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = when (effectiveStyle) {
                        MapStyle.CLARO -> Icons.Default.LightMode
                        MapStyle.OSCURO -> Icons.Default.DarkMode
                        MapStyle.SISTEMA -> Icons.Default.BrightnessAuto
                    },
                    contentDescription = "Estilo del mapa: ${effectiveStyle.etiqueta}"
                )
            }

            // AVISO RESILIENTE DE PERMISOS DENEGADOS (ROL 2)
            if (permissionDenied) {
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
                                "Para trazar rutas desde donde estás, abre los ajustes del sistema y permite el acceso a la ubicación."
                            else
                                "Permite el acceso a tu ubicación para trazar rutas desde donde estés en el campus.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                .onSizeChanged { cardHeightPx.intValue = it.height }
        )
    }
}

/**
 * Tarjeta flotante colapsable con diseño institucional ULEAM.
 * Contiene:
 * - Resumen métrico (distancia y minutos estimados a 1.2 m/s).
 * - Indicador de estado del GPS.
 * - Desglose guiado Turn-by-Turn con iconos direccionales y distancias.
 * - Interruptores para Evitar escaleras y Guía por voz.
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
        // Un solo movimiento suave de la tarjeta al expandir/colapsar (sin animaciones encimadas por dentro)
        modifier = modifier.animateContentSize(
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        )
    ) {
        // Navegando y sin expandir: tarjeta compacta (destino corto + metros restantes) para no tapar el mapa
        val compact = s.navigating && !expanded
        Column(Modifier.padding(if (compact) 10.dp else 14.dp)) {
            // CABECERA: Título, estado y botón de colapso
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (compact) s.destino?.let { blockLabel(it.codigo, it.nombre) ?: it.nombre } ?: ""
                            else s.destino?.nombre ?: "Selecciona un destino",
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 15.sp else 17.sp,
                        maxLines = if (compact) 1 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                        color = if (s.destino != null) tc(0xFFA01B2C, 0xFF90CAF9) else MaterialTheme.colorScheme.onSurface
                    )
                    if (!compact) s.destino?.descripcion?.takeIf { it.isNotBlank() }?.let { d ->
                        Text(
                            text = d,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!compact) Text(
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
                        tint = tc(0xFFA01B2C, 0xFF64B5F6)
                    )
                }
            }

            // YA ESTÁ EN EL DESTINO (GPS a pocos metros o dentro del edificio): no hace falta ruta
            if (s.enDestino && s.destino != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Ya estás en tu destino",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tc(0xFFE8F5E9, 0xFF1B3A24), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = tc(0xFF2E7D32, 0xFF81C784)
                )
            }

            // RESUMEN MÉTRICO PRINCIPAL DE RUTA (navegando, lo reemplaza "Faltan X m")
            if (s.route != null && !compact) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tc(0xFFE8F0FE, 0xFF1C2B44), RoundedCornerShape(10.dp))
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
                        text = (vm.routeDistanceM() ?: s.route.distanceM).let { d ->
                            "%.0f m  ·  ≈ %.1f min a pie".format(d, s.route.estimatedMinutes * d / s.route.distanceM.coerceAtLeast(0.1))
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = tc(0xFF0D47A1, 0xFF90CAF9)
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

            // EMPEZAR RUTA (como Google Maps) / NAVEGANDO CON METROS RESTANTES
            if (s.route != null) {
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                if (!s.navigating) {
                    Button(
                        onClick = vm::empezarRuta,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Empezar ruta", fontWeight = FontWeight.Bold)
                    }
                } else {
                    val remainingM = vm.userPosition?.let { u ->
                        vm.routeGeometry()?.let { pts -> RouteProgressCalc.compute(pts, GeoPoint(u.lat, u.lon))?.remainingM }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Navegando…", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1976D2))
                            Text(
                                text = remainingM?.let { "Faltan %.0f m  ·  ≈ %.1f min".format(it, it / 1.2 / 60.0) }
                                    ?: "Esperando tu ubicación…",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        if (compact) {
                            // Zona Segura sigue a mano aunque la tarjeta esté compacta
                            IconButton(onClick = vm::zonaSegura) {
                                Icon(Icons.Default.Security, contentDescription = "Zona Segura", tint = tc(0xFFD32F2F, 0xFF1E88E5))
                            }
                        }
                        OutlinedButton(onClick = vm::terminarRuta) { Text("Terminar") }
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
            if (!compact) s.infoMsg?.let { infoText ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = infoText,
                    color = tc(0xFF2E7D32, 0xFF81C784),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // SECCIÓN EXPANDIDA: INSTRUCCIONES TURN-BY-TURN Y CONTROLES AVANZADOS
            if (expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    // ESTADO DEL GPS
                    Text(
                        text = when (locationState) {
                            is LocationState.Fix -> "GPS: Señal fija (±%.0f m)".format((locationState as LocationState.Fix).accuracyM)
                            is LocationState.LowAccuracy -> "GPS: Señal débil"
                            LocationState.Searching -> "GPS: Buscando satélites…"
                            LocationState.PermissionDenied -> "GPS: Sin permisos"
                            LocationState.ProviderDisabled -> "GPS: Desactivado"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tc(0xFF1565C0, 0xFF64B5F6)
                    )
                    Spacer(Modifier.height(4.dp))

                    // SWITCH EVITAR ESCALERAS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Accesibilidad (Evitar escaleras):", fontSize = 13.sp)
                        UleamSwitch(checked = s.avoidStairs, onCheckedChange = vm::toggleEscaleras)
                    }

                    // SWITCH GUÍA POR VOZ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Guía por voz:", fontSize = 13.sp)
                        }
                        UleamSwitch(checked = s.vozActiva, onCheckedChange = vm::toggleVoz)
                    }

                    // LISTADO DE INSTRUCCIONES TURN-BY-TURN (ROL 4)
                    if (s.instructions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Instrucciones de recorrido:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier
                                .heightIn(max = 160.dp)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
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

            if (!compact) Spacer(Modifier.height(10.dp))

            // ACCIONES PRINCIPALES: ZONA SEGURA (EVACUACIÓN) Y LIMPIAR (en la tarjeta compacta, Zona Segura va junto a Terminar)
            if (!compact) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Botón Zona Segura destacado en Rojo Evacuación ULEAM
                Button(
                    onClick = vm::zonaSegura,
                    colors = ButtonDefaults.buttonColors(containerColor = tc(0xFFD32F2F, 0xFF1E88E5)),
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
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Primera ejecución: el usuario elige el estilo del mapa (se puede cambiar luego con el botón del mapa). */
@Composable
private fun MapStyleDialog(onElegir: (MapStyle) -> Unit) {
    AlertDialog(
        onDismissRequest = { onElegir(MapStyle.SISTEMA) },
        title = { Text("Estilo del mapa") },
        text = {
            Column {
                Text("¿Cómo quieres ver el mapa del campus? Puedes cambiarlo cuando quieras con el botón ☀/🌙 del mapa.")
                Spacer(Modifier.height(12.dp))
                listOf(
                    MapStyle.CLARO to "Modo claro",
                    MapStyle.OSCURO to "Modo oscuro",
                    MapStyle.SISTEMA to "Usar la configuración del sistema"
                ).forEach { (st, texto) ->
                    OutlinedButton(
                        onClick = { onElegir(st) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = when (st) {
                                MapStyle.CLARO -> Icons.Default.LightMode
                                MapStyle.OSCURO -> Icons.Default.DarkMode
                                MapStyle.SISTEMA -> Icons.Default.BrightnessAuto
                            },
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(texto)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

/**
 * Interruptor con borde visible: apagado = pista transparente con contorno gris (se ve claramente
 * como "desactivado"); encendido = pista del color de acento con un check en el círculo.
 */
@Composable
private fun UleamSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val acento = tc(0xFFA01B2C, 0xFF1E88E5)
    val apagado = tc(0xFF79747E, 0xFF8A96A3)
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = if (checked) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = acento) }
        } else null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = acento,
            checkedBorderColor = acento,
            uncheckedThumbColor = apagado,
            uncheckedTrackColor = Color.Transparent,
            uncheckedBorderColor = apagado
        )
    )
}
