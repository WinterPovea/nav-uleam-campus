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
import ec.edu.uleam.navigator.routing.astar.NavigationGuidance
import ec.edu.uleam.navigator.routing.astar.NavigationInstruction
import ec.edu.uleam.navigator.routing.astar.RouteOptions
import ec.edu.uleam.navigator.routing.astar.RouteResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    var ui by mutableStateOf(UiState())
        private set

    /**
     * Suscripción reactiva optimizada para batería:
     * SharingStarted.WhileSubscribed(5000) detiene el hardware GPS 5s después
     * de que ningún observador UI esté activo (pantalla apagada o app minimizada).
     */
    val locationState: StateFlow<LocationState> = locationSource.updates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LocationState.Searching
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
        ui = ui.copy(destino = l, query = "", results = emptyList(), msg = null, infoMsg = null)
        recalcular()
    }

    fun elegirPuerta(n: Int) {
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
     */
    fun onGpsLocation(lat: Double, lon: Double) {
        if (ui.isSimulationMode || ui.loading) return
        val astar = repo.astar ?: return
        val snap = astar.snapToNode(lat, lon)
        if (snap != null) {
            ui = ui.copy(
                origenId = snap.nodeId,
                origenLabel = "Mi ubicación GPS (nodo ${snap.nodeId})",
                msg = null
            )
            recalcular()
        }
    }

    fun toggleSimulationMode(active: Boolean) {
        ui = ui.copy(
            isSimulationMode = active,
            infoMsg = if (active) "Modo Simulación activado: toca el mapa o elige una puerta." else "Modo GPS Real activado."
        )
        if (!active) {
            val currentPos = userPosition
            if (currentPos != null) {
                onGpsLocation(currentPos.lat, currentPos.lon)
            }
        }
    }

    fun toggleEscaleras(v: Boolean) {
        ui = ui.copy(avoidStairs = v)
        recalcular()
    }

    /**
     * Evacuación de emergencia: calcula ruta hacia la zona segura más cercana
     * utilizando Dijkstra en el grafo unificado sin depender de SQL.
     */
    fun zonaSegura() {
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
        ui = ui.copy(destino = null, route = null, instructions = emptyList(), msg = null, infoMsg = null)
    }

    private fun recalcular() {
        val o = ui.origenId ?: return
        val d = ui.destino ?: return
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
}
