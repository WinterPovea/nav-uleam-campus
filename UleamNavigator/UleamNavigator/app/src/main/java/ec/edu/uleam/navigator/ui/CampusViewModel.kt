package ec.edu.uleam.navigator.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ec.edu.uleam.navigator.data.*
import ec.edu.uleam.navigator.routing.Route
import kotlinx.coroutines.launch
import ec.edu.uleam.navigator.location.*
import kotlinx.coroutines.Job
import ec.edu.uleam.navigator.ui.map.UserPosition
import ec.edu.uleam.navigator.routing.astar.*

data class UiState(
    val loading: Boolean = true,
    val query: String = "",
    val results: List<LugarEntity> = emptyList(),
    val origenId: Int? = null,
    val origenLabel: String = "Toca el mapa o elige una puerta",
    val destino: LugarEntity? = null,
    val route: Route? = null,
    val avoidStairs: Boolean = false,
    val msg: String? = null
)

class CampusViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CampusRepository(app)
    var ui by mutableStateOf(UiState()); private set
    val nodos get() = repo.nodos
    val aristas get() = repo.aristas

    private val locationSource: LocationSource = FusedLocationSource(app)

    private var locationJob: Job? = null
    var gpsState by mutableStateOf<LocationState>(LocationState.Searching); private set
    var gpsActivo by mutableStateOf(true); private set
    fun toggleGps(v: Boolean) { gpsActivo = v }

    val userPosition: UserPosition?
        get() = when (val g = gpsState) {
            is LocationState.Fix -> UserPosition(g.lat, g.lon, g.accuracyM)
            is LocationState.LowAccuracy -> UserPosition(g.lat, g.lon, g.accuracyM)
            else -> null
        }

    fun iniciarUbicacion() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationSource.updates().collect { s ->
                gpsState = s
                if (s is LocationState.Fix && gpsActivo && !ui.loading) ubicacionSimulada(s.lat, s.lon)
            }
        }
    }
    val nodoById get() = repo.engine.nodes

    init {
        viewModelScope.launch {
            repo.load()
            ui = ui.copy(loading = false, origenId = 100, origenLabel = "Puerta 1")
        }
    }

    fun onQuery(q: String) { ui = ui.copy(query = q, results = repo.buscar(q)) }

    fun elegirDestino(l: LugarEntity) {
        ui = ui.copy(destino = l, query = "", results = emptyList(), msg = null)
        recalcular()
    }

    fun elegirPuerta(n: Int) {
        ui = ui.copy(origenId = 99 + n, origenLabel = "Puerta $n", msg = null); recalcular()
    }

    /** Simula el GPS: toque en el mapa → nodo de calle más cercano (≤100 m). */
    /** Simula el GPS: toque en el mapa → nodo de calle más cercano (≤100 m). */
    fun ubicacionSimulada(lat: Double, lon: Double) {
        val snap = repo.astar.snapToNode(lat, lon)
        if (snap == null) {
            ui = ui.copy(msg = mensajeDe(RouteResult.Reason.OUTSIDE_CAMPUS))
        } else {
            ui = ui.copy(origenId = snap.nodeId, origenLabel = "Mi ubicación (nodo ${snap.nodeId})", msg = null)
            recalcular()
        }
    }

    fun toggleEscaleras(v: Boolean) { ui = ui.copy(avoidStairs = v); recalcular() }

    fun zonaSegura() {
        val o = ui.origenId ?: return
        val r = repo.engine.nearestSafeZone(o, ui.avoidStairs)
        if (r == null) { ui = ui.copy(msg = "No hay ruta a una zona segura"); return }
        val lugar = repo.lugares.firstOrNull { it.idNodo == r.nodeIds.last() }
        ui = ui.copy(destino = lugar, route = r, msg = null)
    }

    fun limpiar() { ui = ui.copy(destino = null, route = null, msg = null) }

    private fun recalcular() {
        val o = ui.origenId; val d = ui.destino
        if (o == null || d == null) return
        when (val r = repo.astar.findRoute(o, d.idNodo, RouteOptions(avoidStairs = ui.avoidStairs))) {
            is RouteResult.Success -> ui = ui.copy(route = Route(r.nodeIds, r.distanceM), msg = null)
            is RouteResult.Failure -> ui = ui.copy(route = null, msg = mensajeDe(r.reason))
        }
    }

    private fun mensajeDe(reason: RouteResult.Reason) = when (reason) {
        RouteResult.Reason.OUTSIDE_CAMPUS -> "Pareces estar fuera del campus. Elige una puerta como origen."
        RouteResult.Reason.NO_PATH -> "No se encontró una ruta."
        RouteResult.Reason.ONLY_PATH_HAS_STAIRS -> "La única ruta incluye escaleras. ¿Desactivar el filtro?"
        RouteResult.Reason.UNKNOWN_ORIGIN, RouteResult.Reason.UNKNOWN_DESTINATION -> "Lugar no válido."
    }
}
