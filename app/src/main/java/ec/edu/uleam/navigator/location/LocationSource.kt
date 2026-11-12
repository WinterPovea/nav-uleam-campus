package ec.edu.uleam.navigator.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow

/**
 * Estados del sensor de ubicación consumidos por la capa de presentación.
 */
sealed interface LocationState {
    /** Posición válida con precisión aceptable (<= maxAccuracyM). */
    data class Fix(val lat: Double, val lon: Double, val accuracyM: Float) : LocationState

    /** Posición detectada pero con precisión degradada (> maxAccuracyM). */
    data class LowAccuracy(val lat: Double, val lon: Double, val accuracyM: Float) : LocationState

    /** En búsqueda activa de fijación GNSS. */
    data object Searching : LocationState

    /** Permisos de ubicación denegados por el usuario. */
    data object PermissionDenied : LocationState

    /** Antena GPS o servicio de ubicación desactivado a nivel de sistema. */
    data object ProviderDisabled : LocationState
}

/**
 * Contrato desacoplado para fuentes de ubicación (GPS nativo o Mock/Simulación).
 */
interface LocationSource {
    fun updates(): Flow<LocationState>
}

/**
 * Implementación de hardware basada en FusedLocationProviderClient.
 * Diseñada para liberar el hardware del dispositivo al cancelar la recolección del Flow.
 */
class FusedLocationSource(
    context: Context,
    private val maxAccuracyM: Float = LocationFilter.DEFAULT_MAX_ACCURACY_M,
    private val maxSpeedMps: Double = LocationFilter.DEFAULT_MAX_SPEED_MPS,
    private val intervalMs: Long = 2000L
) : LocationSource {

    private val appContext = context.applicationContext

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    fun gpsEnabled(): Boolean {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    @SuppressLint("MissingPermission")
    override fun updates(): Flow<LocationState> = callbackFlow {
        if (!hasPermission()) {
            trySend(LocationState.PermissionDenied)
            close()
            return@callbackFlow
        }
        if (!gpsEnabled()) {
            trySend(LocationState.ProviderDisabled)
            close()
            return@callbackFlow
        }

        trySend(LocationState.Searching)

        val filter = LocationFilter(
            maxAccuracyM = maxAccuracyM,
            maxSpeedMps = maxSpeedMps
        )
        val client = LocationServices.getFusedLocationProviderClient(appContext)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val accuracy = if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE
                val timestampMs = if (loc.time > 0L) loc.time else System.currentTimeMillis()

                when (val decision = filter.process(loc.latitude, loc.longitude, accuracy, timestampMs)) {
                    is FilterResult.Accepted -> {
                        trySend(LocationState.Fix(decision.lat, decision.lon, decision.accuracyM))
                    }
                    is FilterResult.LowAccuracy -> {
                        // Solo emitir LowAccuracy si aún no se tiene una posición válida previa fijada,
                        // evitando que picos de incertidumbre degraden una ruta ya estable.
                        if (filter.lastAccepted == null) {
                            trySend(LocationState.LowAccuracy(decision.lat, decision.lon, decision.accuracyM))
                        }
                    }
                    is FilterResult.RejectedJump,
                    FilterResult.Invalid -> {
                        // Se descartan silenciosamente los saltos multipath (>20 m/s) y lecturas inválidas
                    }
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    filter.reset()
                    trySend(LocationState.Searching)
                }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            trySend(LocationState.PermissionDenied)
            close(e)
        }

        awaitClose {
            client.removeLocationUpdates(callback)
        }
    }
}

/**
 * Fuente simulada para pruebas offline o demostración en interiores sin satélites.
 */
class FakeLocationSource(
    private val points: List<Pair<Double, Double>>,
    private val delayMs: Long = 1500L,
    private val accuracyM: Float = 5f
) : LocationSource {
    override fun updates(): Flow<LocationState> = flow {
        emit(LocationState.Searching)
        for ((lat, lon) in points) {
            delay(delayMs)
            emit(LocationState.Fix(lat, lon, accuracyM))
        }
    }
}

