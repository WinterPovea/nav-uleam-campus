package ec.edu.uleam.navigator.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ec.edu.uleam.navigator.data.*
import ec.edu.uleam.navigator.routing.Route
import kotlinx.coroutines.launch

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
    fun ubicacionSimulada(lat: Double, lon: Double) {
        val snap = repo.engine.snapToStreet(lat, lon)
        if (snap == null) {
            ui = ui.copy(msg = "Pareces estar fuera del campus. Elige una puerta como origen.")
        } else {
            ui = ui.copy(origenId = snap.first, origenLabel = "Mi ubicación (nodo ${snap.first})", msg = null)
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
        val r = repo.engine.aStar(o, d.idNodo, ui.avoidStairs)
        ui = ui.copy(route = r, msg = if (r == null) "No se encontró ruta" else null)
    }
}
