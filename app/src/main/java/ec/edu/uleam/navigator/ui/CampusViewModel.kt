package ec.edu.uleam.navigator.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ec.edu.uleam.navigator.data.AristaEntity
import ec.edu.uleam.navigator.data.CampusRepository
import ec.edu.uleam.navigator.data.LugarEntity
import ec.edu.uleam.navigator.data.NodoEntity
import ec.edu.uleam.navigator.location.FusedLocationSource
import ec.edu.uleam.navigator.location.GpsSmoother
import ec.edu.uleam.navigator.location.OriginStabilizer
import ec.edu.uleam.navigator.location.arrivalRadiusM
import ec.edu.uleam.navigator.location.LocationState
import ec.edu.uleam.navigator.location.SensorManagerCompassSource
import ec.edu.uleam.navigator.routing.astar.AStarEngine
import ec.edu.uleam.navigator.routing.astar.NavigationGuidance
import ec.edu.uleam.navigator.routing.astar.NavigationInstruction
import ec.edu.uleam.navigator.routing.astar.RouteOptions
import ec.edu.uleam.navigator.routing.astar.RouteResult
import ec.edu.uleam.navigator.ui.map.CampusShape
import ec.edu.uleam.navigator.ui.map.GeoPoint
import ec.edu.uleam.navigator.ui.map.OriginPicker
import ec.edu.uleam.navigator.ui.map.EdgeShapes
import ec.edu.uleam.navigator.ui.map.RouteProgressCalc
import ec.edu.uleam.navigator.ui.voice.VoiceCueTracker
import ec.edu.uleam.navigator.ui.voice.VoiceCues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UserPosition(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float? = null
)

data class UiState(
    val loading: Boolean = true,
    val query: String = "",
    val results: List<LugarEntity> = emptyList(),
    /** Filtro rápido por categoría (chips bajo la búsqueda); null = sin filtro. */
    val categoria: String? = null,
    /** Últimos destinos elegidos (se muestran al tocar la búsqueda vacía). */
    val recientes: List<LugarEntity> = emptyList(),
    /** Guía por voz (TextToSpeech) durante la navegación. */
    val vozActiva: Boolean = true,
    val origenId: Int? = null,
    val origenLabel: String = "Puerta 1",
    val destino: LugarEntity? = null,
    val route: RouteResult.Success? = null,
    val instructions: List<NavigationInstruction> = emptyList(),
    val avoidStairs: Boolean = false,
    val isSimulationMode: Boolean = true, // Modo demo activo por defecto para resiliencia en interiores
    val msg: String? = null,
    val infoMsg: String? = null,
    /** true mientras el usuario recorre la ruta (botón "Empezar ruta"). */
    val navigating: Boolean = false,
    /** true si el GPS indica que el usuario ya está en el destino elegido (no hace falta ruta). */
    val enDestino: Boolean = false,
    /** Tramo inicial (GPS real): del punto del camino más cercano al usuario hasta [routeLeadNode]. */
    val routeLead: List<GeoPoint> = emptyList(),
    val routeLeadNode: Int? = null
)

class CampusViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CampusRepository(app)
    private val locationSource = FusedLocationSource(app)
    private val compassSource = SensorManagerCompassSource(app)
    private val locationSessionTrigger = MutableStateFlow(0)
    private val prefs = app.getSharedPreferences("map_prefs", android.content.Context.MODE_PRIVATE)

    var ui by mutableStateOf(UiState())
        private set

    /** Control temporal de desvío de ruta (> 3 segundos consecutivos a > 15 m). */
    private var offRouteSinceMs: Long? = null
    private var offRouteRerouteJob: Job? = null
    private var lastGpsLatLon: Pair<Double, Double>? = null
    private var lastGpsAccuracyM: Float? = null

    /** Suavizado de lecturas GPS y histéresis del origen (evitan saltos del punto azul y de la ruta). */
    private val smoother = GpsSmoother()
    private val originStabilizer = OriginStabilizer()
    private var gpsSmoothed by mutableStateOf<UserPosition?>(null)

    /** Distancias de cada nodo al destino actual (Dijkstra inverso), reutilizadas en cada lectura GPS. */
    private var distanciasCache: Triple<Int, Boolean, Map<Int, Double>>? = null

    /** Frases para la guía por voz; la Activity las dice con TextToSpeech si el usuario la tiene activada. */
    private val _voz = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val voz: SharedFlow<String> = _voz
    private var cueTracker: VoiceCueTracker? = null

    /** Posición simulada del caminante (solo en Modo Simulación mientras se navega). */
    private var simPosition by mutableStateOf<UserPosition?>(null)
    private var walkJob: Job? = null

    /**
     * Suscripción reactiva optimizada para batería y permisos en caliente:
     * - Se reinicia automáticamente cuando el usuario concede permisos en tiempo de ejecución.
     * - SharingStarted.WhileSubscribed(5000) detiene el hardware GPS 5s después
     *   de que ningún observador UI esté activo (pantalla apagada o app minimizada).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val locationState: StateFlow<LocationState> = locationSessionTrigger
        .flatMapLatest { locationSource.updates() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LocationState.Searching
        )

    /**
     * Orientación peatonal (azimut en grados [0, 360) respecto al Norte) suavizada con filtro paso bajo.
     * Se desactiva automáticamente tras 5s sin suscriptores activos en UI para proteger batería.
     */
    val azimuthDeg: StateFlow<Float?> = compassSource.azimuthUpdates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val nodos: List<NodoEntity> get() = repo.nodos
    val aristas: List<AristaEntity> get() = repo.aristas
    val lugares: List<LugarEntity> get() = repo.lugares
    val shapes: List<CampusShape> get() = repo.shapes
    val nodoById: Map<Int, NodoEntity> get() = nodos.associateBy { it.id }

    val userPosition: UserPosition?
        get() {
            if (ui.isSimulationMode) {
                simPosition?.let { return it }
                // En modo simulación, el punto azul sigue al origen seleccionado
                val node = ui.origenId?.let { nodoById[it] }
                return if (node != null) UserPosition(node.latitud, node.longitud, 5f) else null
            }
            return when (val state = locationState.value) {
                is LocationState.Fix -> gpsSmoothed ?: UserPosition(state.lat, state.lon, state.accuracyM)
                is LocationState.LowAccuracy -> gpsSmoothed ?: UserPosition(state.lat, state.lon, state.accuracyM)
                else -> null
            }
        }

    init {
        viewModelScope.launch {
            repo.load()
            ui = ui.copy(
                loading = false,
                recientes = cargarRecientes(),
                vozActiva = prefs.getBoolean(KEY_VOZ, true),
                origenId = 100,
                origenLabel = "Puerta 1"
            )
        }
    }

    fun onQuery(q: String) {
        ui = ui.copy(query = q, results = repo.buscar(q), categoria = null)
    }

    /** Cierra la búsqueda: borra el texto, los resultados y el filtro de categoría. */
    fun cerrarBusqueda() {
        ui = ui.copy(query = "", results = emptyList(), categoria = null)
    }

    /** Chip de categoría: muestra todos los lugares de esa categoría (tocarlo de nuevo lo quita). */
    fun filtrarCategoria(cat: String?) {
        val nueva = if (cat == ui.categoria) null else cat
        ui = ui.copy(
            categoria = nueva,
            query = "",
            results = if (nueva == null) emptyList() else Categorias.filtrar(repo.lugares, nueva)
        )
    }

    private fun cargarRecientes(): List<LugarEntity> {
        val ids = prefs.getString(KEY_RECIENTES, "").orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
        val porId = repo.lugares.associateBy { it.idNodo }
        return ids.mapNotNull { porId[it] }
    }

    fun toggleVoz(on: Boolean) {
        prefs.edit().putBoolean(KEY_VOZ, on).apply()
        ui = ui.copy(vozActiva = on)
    }

    private fun guardarReciente(l: LugarEntity) {
        val ids = Recientes.agregar(ui.recientes.map { it.idNodo }, l.idNodo)
        prefs.edit().putString(KEY_RECIENTES, ids.joinToString(",")).apply()
        ui = ui.copy(recientes = cargarRecientes())
    }

    fun elegirDestino(l: LugarEntity) {
        if (l.categoria != "PUERTA") guardarReciente(l)
        resetOffRouteTracking()
        detenerNavegacion()
        ui = ui.copy(destino = l, query = "", results = emptyList(), categoria = null, msg = null, infoMsg = null, enDestino = false)
        recalcular()
    }

    /**
     * Toque sobre un lugar del mapa: lo elige como destino y traza la ruta de vista previa
     * (la tarjeta inferior ofrece "Empezar ruta"). Se ignora si ya es el destino o se está navegando.
     */
    fun onPlaceTap(l: LugarEntity) {
        if (ui.navigating || ui.destino?.idNodo == l.idNodo) return
        elegirDestino(l)
    }

    fun elegirPuerta(n: Int) {
        resetOffRouteTracking()
        detenerNavegacion()
        val puertaId = 99 + n
        ui = ui.copy(
            origenId = puertaId,
            origenLabel = "Puerta $n",
            msg = null,
            infoMsg = "Origen fijado en Puerta $n"
        )
        recalcular(usarGps = false)
    }

    fun elegirOrigenManual(nodoId: Int, label: String) {
        resetOffRouteTracking()
        detenerNavegacion()
        ui = ui.copy(
            origenId = nodoId,
            origenLabel = label,
            msg = null,
            infoMsg = "Origen manual: $label"
        )
        recalcular(usarGps = false)
    }

    /**
     * Interacción táctil en el mapa:
     * En Modo Simulación, fija el origen manual en el nodo de calle más cercano (<= 100 m).
     */
    fun onMapTap(lat: Double, lon: Double) {
        if (!ui.isSimulationMode) {
            ui = ui.copy(infoMsg = "Activa 'Modo Simulación' para mover el origen manualmente tocando el mapa.")
            return
        }
        val astar = repo.astar ?: return
        val snap = astar.snapToNode(lat, lon)
        if (snap == null) {
            ui = ui.copy(msg = mensajeDe(RouteResult.Reason.OUTSIDE_CAMPUS))
        } else {
            resetOffRouteTracking()
            detenerNavegacion()
            val place = repo.lugares.firstOrNull { it.idNodo == snap.nodeId }
            val label = place?.nombre ?: "Nodo ${snap.nodeId} (calle)"
            ui = ui.copy(
                origenId = snap.nodeId,
                origenLabel = label,
                msg = null,
                infoMsg = "Origen ubicado en $label"
            )
            recalcular()
        }
    }

    /**
     * Recibe actualizaciones del GPS cuando no se está en modo simulación.
     * - Si no existe ruta activa, fija el nodo de origen más cercano.
     * - Si existe una ruta activa, evalúa si el usuario se desvió > 15 m durante > 3 s
     *   consecutivos antes de disparar un recálculo en segundo plano sin parpadeo.
     */
    fun onGpsLocation(rawLat: Double, rawLon: Double, accuracyM: Float? = null, nowMs: Long = System.currentTimeMillis()) {
        if (ui.isSimulationMode || ui.loading) return
        val astar = repo.astar ?: return
        val sm = smoother.update(rawLat, rawLon, accuracyM, nowMs)
        val lat = sm.lat
        val lon = sm.lon
        gpsSmoothed = UserPosition(lat, lon, accuracyM ?: sm.accuracyM)
        lastGpsLatLon = lat to lon
        lastGpsAccuracyM = accuracyM

        val activeRoute = ui.route
        val currentDestino = ui.destino

        // Ya está en el destino (a pocos metros o dentro de su edificio): se avisa en vez de trazar ruta
        if (currentDestino != null && yaEnDestino(lat, lon)) {
            resetOffRouteTracking()
            if (ui.navigating) {
                llegarAlDestino(currentDestino)
            } else if (!ui.enDestino || ui.route != null) {
                ui = ui.copy(enDestino = true, route = null, instructions = emptyList(), msg = null, infoMsg = null)
            }
            return
        }

        // Sin ruta activa: origen = entrada del edificio donde está el usuario o el mejor nodo de calle cercano
        if (activeRoute == null || currentDestino == null) {
            resetOffRouteTracking()
            val origen = origenGps(lat, lon)
            if (origen == null) {
                ui = ui.copy(
                    origenId = null,
                    origenLabel = "GPS fuera del campus",
                    route = null,
                    instructions = emptyList(),
                    msg = mensajeDe(RouteResult.Reason.OUTSIDE_CAMPUS),
                    infoMsg = null,
                    enDestino = false
                )
            } else if (ui.enDestino || originStabilizer.offer(origen.first, ui.origenId, nowMs)) {
                ui = ui.copy(origenId = origen.first, origenLabel = origen.second, msg = null, enDestino = false)
                if (currentDestino != null) recalcular(usarGps = false)
            }
            return
        }

        if (ui.navigating) avisarVoz(lat, lon)

        // Con ruta activa: verificar si el usuario se desvió más de 15 metros de los segmentos
        val offRoute = astar.isOffRoute(
            latitude = lat,
            longitude = lon,
            routeNodeIds = activeRoute.nodeIds,
            thresholdMeters = AStarEngine.DEFAULT_OFF_ROUTE_THRESHOLD_M
        )

        if (!offRoute) {
            resetOffRouteTracking()
            return
        }

        val startMs = offRouteSinceMs
        if (startMs == null) {
            offRouteSinceMs = nowMs
            offRouteRerouteJob?.cancel()
            offRouteRerouteJob = viewModelScope.launch {
                delay(OFF_ROUTE_CONFIRMATION_MS)
                val (latestLat, latestLon) = lastGpsLatLon ?: return@launch
                val routeNow = ui.route ?: return@launch
                if (!ui.isSimulationMode && astar.isOffRoute(latestLat, latestLon, routeNow.nodeIds)) {
                    ejecutarRerouteDesdeUbicacion(latestLat, latestLon)
                }
            }
        } else if (nowMs - startMs >= OFF_ROUTE_CONFIRMATION_MS) {
            resetOffRouteTracking()
            viewModelScope.launch {
                ejecutarRerouteDesdeUbicacion(lat, lon)
            }
        }
    }

    private sealed interface RerouteOutcome {
        data class Updated(
            val originId: Int,
            val originLabel: String,
            val route: RouteResult.Success,
            val instructions: List<NavigationInstruction>
        ) : RerouteOutcome

        data class Failed(val reason: RouteResult.Reason) : RerouteOutcome
    }

    private suspend fun ejecutarRerouteDesdeUbicacion(lat: Double, lon: Double) {
        val destinoActual = ui.destino ?: return
        val astar = repo.astar ?: return
        val graph = repo.graph ?: return
        val avoid = ui.avoidStairs

        val origen = origenGps(lat, lon)
        val outcome = withContext(Dispatchers.Default) {
            if (origen == null) return@withContext RerouteOutcome.Failed(RouteResult.Reason.OUTSIDE_CAMPUS)
            when (val result = astar.findRoute(origen.first, destinoActual.idNodo, RouteOptions(avoidStairs = avoid))) {
                is RouteResult.Success -> {
                    val newOriginId = origen.first
                    val newOriginLabel = origen.second
                    val instructions = NavigationGuidance.generateInstructions(
                        nodeIds = result.nodeIds,
                        nodeById = graph.nodes.associateBy { it.id },
                        hasStairsEdge = ::edgeHasStairs,
                        originLabel = newOriginLabel,
                        destinationLabel = destinoActual.nombre
                    )
                    RerouteOutcome.Updated(newOriginId, newOriginLabel, result, instructions)
                }
                is RouteResult.Failure -> RerouteOutcome.Failed(result.reason)
            }
        }

        when (outcome) {
            is RerouteOutcome.Updated -> {
                if (ui.route?.nodeIds != outcome.route.nodeIds) {
                    ui = ui.copy(
                        origenId = outcome.originId,
                        origenLabel = outcome.originLabel,
                        route = outcome.route,
                        instructions = outcome.instructions,
                        msg = null,
                        infoMsg = "Ruta recalculada automáticamente por desvío"
                    )
                }
            }
            is RerouteOutcome.Failed -> {
                ui = ui.copy(
                    origenId = if (outcome.reason == RouteResult.Reason.OUTSIDE_CAMPUS) null else ui.origenId,
                    origenLabel = if (outcome.reason == RouteResult.Reason.OUTSIDE_CAMPUS) "GPS fuera del campus" else ui.origenLabel,
                    route = if (outcome.reason == RouteResult.Reason.OUTSIDE_CAMPUS) null else ui.route,
                    instructions = if (outcome.reason == RouteResult.Reason.OUTSIDE_CAMPUS) emptyList() else ui.instructions,
                    msg = mensajeDe(outcome.reason),
                    infoMsg = null
                )
            }
        }
    }

    private fun resetOffRouteTracking() {
        offRouteSinceMs = null
        offRouteRerouteJob?.cancel()
        offRouteRerouteJob = null
    }

    /**
     * Reinicia el flujo de ubicación en caliente tras conceder permisos o reactivar el GPS,
     * sin necesidad de cerrar y volver a abrir la aplicación.
     */
    fun refreshLocationPermissionState() {
        locationSessionTrigger.value++
    }

    fun toggleSimulationMode(active: Boolean) {
        resetOffRouteTracking()
        smoother.reset()
        originStabilizer.reset()
        detenerNavegacion()
        if (!active && (locationState.value is LocationState.PermissionDenied || locationState.value is LocationState.ProviderDisabled)) {
            refreshLocationPermissionState()
        }

        ui = ui.copy(
            isSimulationMode = active,
            origenId = if (active && ui.origenId == null) 100 else ui.origenId,
            origenLabel = if (active && ui.origenId == null) "Puerta 1" else ui.origenLabel,
            msg = null,
            infoMsg = if (active) "Modo Simulación activado: toca el mapa o elige una puerta." else "Modo GPS Real activado."
        )
        if (!active) {
            val currentPos = userPosition
            if (currentPos != null) {
                // Al cambiar a GPS real, validar de inmediato la posición real frente al campus
                lastGpsLatLon = currentPos.lat to currentPos.lon
                val origen = origenGps(currentPos.lat, currentPos.lon)
                if (origen == null) {
                    ui = ui.copy(
                        origenId = null,
                        origenLabel = "GPS fuera del campus",
                        route = null,
                        instructions = emptyList(),
                        msg = mensajeDe(RouteResult.Reason.OUTSIDE_CAMPUS),
                        infoMsg = null
                    )
                } else {
                    ui = ui.copy(origenId = origen.first, origenLabel = origen.second, msg = null)
                    recalcular()
                }
            } else {
                // Mientras llega la primera muestra (o lastLocation inmediata), limpiar el origen simulado
                ui = ui.copy(
                    origenId = null,
                    origenLabel = "Obteniendo ubicación GPS…",
                    route = null,
                    instructions = emptyList()
                )
            }
        } else {
            recalcular()
        }
    }

    fun toggleEscaleras(v: Boolean) {
        resetOffRouteTracking()
        detenerNavegacion()
        ui = ui.copy(avoidStairs = v)
        recalcular()
    }

    /**
     * Evacuación de emergencia: calcula ruta hacia la zona segura más cercana
     * utilizando Dijkstra en el grafo unificado sin depender de SQL.
     */
    fun zonaSegura() {
        resetOffRouteTracking()
        detenerNavegacion()
        val o = ui.origenId ?: run {
            ui = ui.copy(msg = "Define un origen para evacuar a una zona segura.")
            return
        }
        val astar = repo.astar ?: return
        val graph = repo.graph ?: return

        when (val result = astar.findNearestSafeZone(o, RouteOptions(avoidStairs = ui.avoidStairs))) {
            is RouteResult.Success -> {
                val destNodeId = result.nodeIds.last()
                val lugar = repo.lugares.firstOrNull { it.idNodo == destNodeId } ?: LugarEntity(
                    idNodo = destNodeId,
                    codigo = "ZS",
                    nombre = "Zona Segura",
                    descripcion = "Punto de encuentro de emergencia",
                    categoria = "ZONA_SEGURA",
                    etiquetas = null,
                    busqueda = "zona segura"
                )
                val instructions = NavigationGuidance.generateInstructions(
                    nodeIds = result.nodeIds,
                    nodeById = graph.nodes.associateBy { it.id },
                    hasStairsEdge = ::edgeHasStairs,
                    originLabel = ui.origenLabel,
                    destinationLabel = lugar.nombre
                )
                ui = ui.copy(
                    destino = lugar,
                    route = result,
                    instructions = instructions,
                    msg = null,
                    infoMsg = "🚨 Evacuación rápida a ${lugar.nombre}"
                )
            }
            is RouteResult.Failure -> {
                ui = ui.copy(msg = "No hay ruta a zona segura: ${mensajeDe(result.reason)}")
            }
        }
    }

    /**
     * "Empezar ruta": activa el modo navegación (la cámara sigue al usuario y se muestra el avance).
     * En Modo Simulación el usuario virtual camina solo por la ruta para poder probarlo sin GPS.
     */
    fun empezarRuta() {
        val route = ui.route ?: return
        walkJob?.cancel()
        resetOffRouteTracking()
        ui = ui.copy(navigating = true, msg = null, infoMsg = "Ruta iniciada. ¡Buen viaje!")
        prepararVoz(route)
        if (ui.isSimulationMode) caminarSimulado(route)
    }

    /** Planifica los avisos de giro/escaleras de la ruta y anuncia el inicio. */
    private fun prepararVoz(route: RouteResult.Success) {
        val graph = repo.graph
        cueTracker = if (graph == null) null else VoiceCueTracker(VoiceCues.plan(route.nodeIds, graph::node, ::edgeHasStairs))
        val destino = ui.destino?.let(::nombreCorto) ?: "tu destino"
        _voz.tryEmit(VoiceCues.startText(destino, routeDistanceM() ?: route.distanceM))
    }

    private fun avisarVoz(lat: Double, lon: Double) {
        cueTracker?.check(GeoPoint(lat, lon))?.let { _voz.tryEmit(it.text) }
    }

    /** Nombre para la voz: "Bloque B-16" en los bloques de facultad; si no, el nombre sin paréntesis. */
    private fun nombreCorto(l: LugarEntity): String {
        val bloque = l.codigo?.substringAfter('·')?.takeIf { it.startsWith("B-") }
        return if (bloque != null) "Bloque $bloque" else l.nombre.substringBefore(" (").trim().ifEmpty { l.nombre }
    }

    /** "Terminar": sale del modo navegación pero conserva la ruta trazada. */
    fun terminarRuta() {
        detenerNavegacion()
        ui = ui.copy(infoMsg = "Navegación finalizada")
    }

    private fun detenerNavegacion() {
        cueTracker = null
        walkJob?.cancel()
        walkJob = null
        simPosition = null
        if (ui.navigating) ui = ui.copy(navigating = false)
    }

    /** El origen pasa a ser el destino alcanzado; la ruta se da por terminada. */
    private fun llegarAlDestino(destino: LugarEntity) {
        if (ui.navigating) _voz.tryEmit(VoiceCues.arrivalText(nombreCorto(destino)))
        cueTracker = null
        walkJob?.cancel()
        walkJob = null
        simPosition = null
        resetOffRouteTracking()
        ui = ui.copy(
            navigating = false,
            origenId = destino.idNodo,
            origenLabel = destino.nombre,
            destino = null,
            route = null,
            instructions = emptyList(),
            msg = null,
            infoMsg = "🏁 Llegaste a ${destino.nombre}",
            enDestino = false
        )
    }

    /** Camina por la ruta a velocidad de demostración, avanzando cada [WALK_TICK_MS] ms. */
    private fun caminarSimulado(route: RouteResult.Success) {
        val byId = nodoById
        val points = route.nodeIds.mapNotNull { id -> byId[id]?.let { GeoPoint(it.latitud, it.longitud) } }
        val destino = ui.destino ?: return
        if (points.size < 2) return
        walkJob = viewModelScope.launch {
            val stepM = DEMO_WALK_SPEED_MPS * WALK_TICK_MS / 1000.0
            simPosition = UserPosition(points[0].lat, points[0].lon, 5f)
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                val len = RouteProgressCalc.distanceM(a, b)
                var walked = 0.0
                while (walked < len) {
                    delay(WALK_TICK_MS)
                    walked = minOf(len, walked + stepM)
                    val t = if (len == 0.0) 1.0 else walked / len
                    simPosition = UserPosition(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t, 5f)
                    simPosition?.let { avisarVoz(it.lat, it.lon) }
                }
            }
            llegarAlDestino(destino)
        }
    }

    fun limpiar() {
        resetOffRouteTracking()
        detenerNavegacion()
        ui = ui.copy(destino = null, route = null, instructions = emptyList(), msg = null, infoMsg = null, enDestino = false)
    }

    /**
     * @param usarGps con GPS real (fuera del Modo Simulación) vuelve a elegir el origen según la posición
     *   actual y el destino; false cuando el usuario fijó el origen a mano (puerta o toque).
     */
    private fun recalcular(usarGps: Boolean = true) {
        val d = ui.destino ?: return
        val gps = lastGpsLatLon
        if (usarGps && !ui.isSimulationMode && !ui.navigating && gps != null) {
            if (yaEnDestino(gps.first, gps.second)) {
                ui = ui.copy(enDestino = true, route = null, instructions = emptyList(), msg = null)
                return
            }
            origenGps(gps.first, gps.second)?.let { (id, label) ->
                ui = ui.copy(origenId = id, origenLabel = label, enDestino = false)
            }
        }
        val o = ui.origenId ?: run {
            ui = ui.copy(
                route = null,
                instructions = emptyList(),
                msg = mensajeDe(RouteResult.Reason.OUTSIDE_CAMPUS)
            )
            return
        }
        val astar = repo.astar ?: return
        val graph = repo.graph ?: return

        when (val result = astar.findRoute(o, d.idNodo, RouteOptions(avoidStairs = ui.avoidStairs))) {
            is RouteResult.Success -> {
                val instructions = NavigationGuidance.generateInstructions(
                    nodeIds = result.nodeIds,
                    nodeById = graph.nodes.associateBy { it.id },
                    hasStairsEdge = ::edgeHasStairs,
                    originLabel = ui.origenLabel,
                    destinationLabel = d.nombre
                )
                ui = ui.copy(route = result, instructions = instructions, msg = null)
            }
            is RouteResult.Failure -> {
                ui = ui.copy(route = null, instructions = emptyList(), msg = mensajeDe(result.reason))
            }
        }
    }

    /**
     * Origen para una posición GPS real: si el usuario está dentro de un edificio, la entrada de ese edificio
     * que deja la ruta más corta; si está fuera, el nodo de calle cercano que minimiza caminar + ruta.
     */
    private fun origenGps(lat: Double, lon: Double): Pair<Int, String>? {
        val graph = repo.graph ?: return null
        val astar = repo.astar ?: return null
        val cost: ((Int) -> Double?)? = ui.destino?.let { d ->
            val dist = distanciasAl(d.idNodo, astar)
            val porNodo: (Int) -> Double? = { id -> dist[id] }
            porNodo
        }
        val pick = OriginPicker.pick(lat, lon, graph, repo.shapes, cost) ?: return null
        ui = ui.copy(routeLead = pick.lead, routeLeadNode = pick.nodeId)
        val label = if (pick.building != null) {
            val l = repo.lugares.firstOrNull { it.idNodo == pick.nodeId }
            val nombre = l?.codigo?.substringAfter('·')?.takeIf { it.isNotBlank() } ?: l?.nombre ?: "edificio"
            "Mi ubicación GPS (entrada de $nombre)"
        } else "Mi ubicación GPS (nodo ${pick.nodeId})"
        return pick.nodeId to label
    }

    /** Distancias de todos los nodos al destino; se recalculan solo si cambia el destino, "evitar escaleras" o el grafo. */
    private fun distanciasAl(destId: Int, astar: AStarEngine): Map<Int, Double> {
        val avoid = ui.avoidStairs
        distanciasCache?.let { (id, av, m) -> if (id == destId && av == avoid && m.isNotEmpty()) return m }
        return astar.distancesTo(destId, RouteOptions(avoidStairs = avoid)).also { distanciasCache = Triple(destId, avoid, it) }
    }

    /** Tramo inicial vigente: solo con GPS real y mientras el origen sea el nodo al que lleva ese tramo. */
    fun leadActivo(): List<GeoPoint> =
        if (!ui.isSimulationMode && ui.origenId != null && ui.origenId == ui.routeLeadNode) ui.routeLead else emptyList()

    private var edgeShapesCache: EdgeShapes? = null
    private fun edgeShapes(): EdgeShapes =
        edgeShapesCache?.takeIf { it.size > 0 || repo.shapes.isEmpty() } ?: EdgeShapes.from(repo.shapes).also { edgeShapesCache = it }

    /** Geometría completa de la ruta: tramo inicial + forma de cada arista. */
    fun routeGeometry(): List<GeoPoint>? {
        val r = ui.route ?: return null
        val pts = edgeShapes().routePoints(r.nodeIds) { id -> nodoById[id]?.let { GeoPoint(it.latitud, it.longitud) } }
        val lead = leadActivo()
        return if (lead.size >= 2) lead + pts.drop(1) else pts
    }

    /** Distancia total a mostrar (ruta + tramo inicial). */
    fun routeDistanceM(): Double? {
        val r = ui.route ?: return null
        val lead = leadActivo()
        return r.distanceM + (0 until lead.size - 1).sumOf { RouteProgressCalc.distanceM(lead[it], lead[it + 1]) }
    }

    private fun yaEnDestino(lat: Double, lon: Double): Boolean {
        val d = ui.destino ?: return false
        val n = nodoById[d.idNodo] ?: return false
        return OriginPicker.isAtDestination(lat, lon, n.latitud, n.longitud, repo.shapes, arrivalRadiusM(lastGpsAccuracyM))
    }

    private fun edgeHasStairs(fromId: Int, toId: Int): Boolean {
        return aristas.any {
            ((it.idOrigen == fromId && it.idDestino == toId) ||
                    (it.bidireccional == 1 && it.idOrigen == toId && it.idDestino == fromId)) &&
                    it.tieneEscaleras == 1
        }
    }

    private fun mensajeDe(reason: RouteResult.Reason) = when (reason) {
        RouteResult.Reason.OUTSIDE_CAMPUS -> "Pareces estar fuera del campus (>100 m). Elige una puerta como origen."
        RouteResult.Reason.NO_PATH -> "No se encontró una ruta disponible."
        RouteResult.Reason.ONLY_PATH_HAS_STAIRS -> "La única ruta incluye escaleras. Desactiva 'Evitar escaleras'."
        RouteResult.Reason.UNKNOWN_ORIGIN, RouteResult.Reason.UNKNOWN_DESTINATION -> "Ubicación o destino no válido."
    }

    companion object {
        private const val KEY_RECIENTES = "recientes"
        private const val KEY_VOZ = "voz"
        const val OFF_ROUTE_CONFIRMATION_MS = 3_000L

        /**
         * Radio de llegada de referencia (m). El real depende de la precisión del GPS: entre 6 y 12 m
         * (ver arrivalRadiusM); también cuenta estar dentro del edificio del destino.
         */
        const val ARRIVAL_RADIUS_M = 8.0

        /** Velocidad del caminante virtual en Modo Simulación (m/s): paso humano normal (≈ 5 km/h). */
        const val DEMO_WALK_SPEED_MPS = 1.4
        const val WALK_TICK_MS = 100L
    }
}
