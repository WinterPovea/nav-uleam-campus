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
import ec.edu.uleam.navigator.location.LocationState
import ec.edu.uleam.navigator.location.SensorManagerCompassSource
import ec.edu.uleam.navigator.routing.astar.AStarEngine
import ec.edu.uleam.navigator.routing.astar.NavigationGuidance
import ec.edu.uleam.navigator.routing.astar.NavigationInstruction
import ec.edu.uleam.navigator.routing.astar.RouteOptions
import ec.edu.uleam.navigator.routing.astar.RouteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    val origenId: Int? = null,
    val origenLabel: String = "Puerta 1",
    val destino: LugarEntity? = null,
    val route: RouteResult.Success? = null,
    val instructions: List<NavigationInstruction> = emptyList(),
    val avoidStairs: Boolean = false,
    val isSimulationMode: Boolean = true, // Modo demo activo por defecto para resiliencia en interiores
    val msg: String? = null,
    val infoMsg: String? = null
)

class CampusViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CampusRepository(app)
    private val locationSource = FusedLocationSource(app)
    private val compassSource = SensorManagerCompassSource(app)
    private val locationSessionTrigger = MutableStateFlow(0)

    var ui by mutableStateOf(UiState())
        private set

    /** Control temporal de desvío de ruta (> 3 segundos consecutivos a > 15 m). */
    private var offRouteSinceMs: Long? = null
    private var offRouteRerouteJob: Job? = null
    private var lastGpsLatLon: Pair<Double, Double>? = null

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
    val nodoById: Map<Int, NodoEntity> get() = nodos.associateBy { it.id }

    val userPosition: UserPosition?
        get() {
            if (ui.isSimulationMode) {
                // En modo simulación, el punto azul sigue al origen seleccionado
                val node = ui.origenId?.let { nodoById[it] }
                return if (node != null) UserPosition(node.latitud, node.longitud, 5f) else null
            }
            return when (val state = locationState.value) {
                is LocationState.Fix -> UserPosition(state.lat, state.lon, state.accuracyM)
                is LocationState.LowAccuracy -> UserPosition(state.lat, state.lon, state.accuracyM)
                else -> null
            }
        }

    init {
        viewModelScope.launch {
            repo.load()
            ui = ui.copy(
                loading = false,
                origenId = 100,
                origenLabel = "Puerta 1"
            )
        }
    }

    fun onQuery(q: String) {
        ui = ui.copy(query = q, results = repo.buscar(q))
    }

    fun elegirDestino(l: LugarEntity) {
        resetOffRouteTracking()
        ui = ui.copy(destino = l, query = "", results = emptyList(), msg = null, infoMsg = null)
        recalcular()
    }

    fun elegirPuerta(n: Int) {
        resetOffRouteTracking()
        val puertaId = 99 + n
        ui = ui.copy(
            origenId = puertaId,
            origenLabel = "Puerta $n",
            msg = null,
            infoMsg = "Origen fijado en Puerta $n"
        )
        recalcular()
    }

    fun elegirOrigenManual(nodoId: Int, label: String) {
        resetOffRouteTracking()
        ui = ui.copy(
            origenId = nodoId,
            origenLabel = label,
            msg = null,
            infoMsg = "Origen manual: $label"
        )
        recalcular()
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
    fun onGpsLocation(lat: Double, lon: Double, nowMs: Long = System.currentTimeMillis()) {
        if (ui.isSimulationMode || ui.loading) return
        val astar = repo.astar ?: return
        lastGpsLatLon = lat to lon

        val activeRoute = ui.route
        val currentDestino = ui.destino

        // Sin ruta activa: acoplar origen al nodo de calle más cercano o alertar si está fuera del campus
        if (activeRoute == null || currentDestino == null) {
            resetOffRouteTracking()
            val snap = astar.snapToNode(lat, lon)
            if (snap == null) {
                ui = ui.copy(
                    origenId = null,
                    origenLabel = "GPS fuera del campus",
                    route = null,
                    instructions = emptyList(),
                    msg = mensajeDe(RouteResult.Reason.OUTSIDE_CAMPUS),
                    infoMsg = null
                )
            } else if (ui.origenId != snap.nodeId) {
                ui = ui.copy(
                    origenId = snap.nodeId,
                    origenLabel = "Mi ubicación GPS (nodo ${snap.nodeId})",
                    msg = null
                )
                if (currentDestino != null) {
                    recalcular()
                }
            }
            return
        }

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

        val outcome = withContext(Dispatchers.Default) {
            when (val result = astar.findRouteFromLocation(
                latitude = lat,
                longitude = lon,
                destinationId = destinoActual.idNodo,
                options = RouteOptions(avoidStairs = avoid)
            )) {
                is RouteResult.Success -> {
                    val newOriginId = result.nodeIds.first()
                    val newOriginLabel = "Mi ubicación GPS (nodo $newOriginId)"
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
                val astar = repo.astar
                val snap = astar?.snapToNode(currentPos.lat, currentPos.lon)
                if (snap == null) {
                    ui = ui.copy(
                        origenId = null,
                        origenLabel = "GPS fuera del campus",
                        route = null,
                        instructions = emptyList(),
                        msg = mensajeDe(RouteResult.Reason.OUTSIDE_CAMPUS),
                        infoMsg = null
                    )
                } else {
                    ui = ui.copy(
                        origenId = snap.nodeId,
                        origenLabel = "Mi ubicación GPS (nodo ${snap.nodeId})",
                        msg = null
                    )
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
        ui = ui.copy(avoidStairs = v)
        recalcular()
    }

    /**
     * Evacuación de emergencia: calcula ruta hacia la zona segura más cercana
     * utilizando Dijkstra en el grafo unificado sin depender de SQL.
     */
    fun zonaSegura() {
        resetOffRouteTracking()
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

    fun limpiar() {
        resetOffRouteTracking()
        ui = ui.copy(destino = null, route = null, instructions = emptyList(), msg = null, infoMsg = null)
    }

    private fun recalcular() {
        val d = ui.destino ?: return
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
        const val OFF_ROUTE_CONFIRMATION_MS = 3_000L
    }
}
