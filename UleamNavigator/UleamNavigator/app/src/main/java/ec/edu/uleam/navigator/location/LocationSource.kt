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

/** Estados que G4 le entrega a G6. G6 decide qué mensaje mostrar. */
sealed interface LocationState {
    /** Posición utilizable (precisión suficiente). */
    data class Fix(val lat: Double, val lon: Double, val accuracyM: Float) : LocationState

    /** Hay posición, pero con poca precisión. Mejor no calcular ruta con ella. */
    data class LowAccuracy(val lat: Double, val lon: Double, val accuracyM: Float) : LocationState

    /** Aún no hay posición (buscando satélites). */
    data object Searching : LocationState

    /** El usuario no dio permiso de ubicación. */
    data object PermissionDenied : LocationState

    /** El GPS del teléfono está apagado. */
    data object ProviderDisabled : LocationState
}

/** Contrato: G6 solo depende de esta interfaz, no de Google Play Services. */
interface LocationSource {
    fun updates(): Flow<LocationState>
}

/** Implementación real con FusedLocationProviderClient (funciona sin internet). */
class FusedLocationSource(
    context: Context,
    private val maxAccuracyM: Float = 30f,
    private val intervalMs: Long = 2000L
) : LocationSource {

    private val appContext = context.applicationContext

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun gpsEnabled(): Boolean {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    @SuppressLint("MissingPermission") // se comprueba arriba con hasPermission()
    override fun updates(): Flow<LocationState> = callbackFlow {
        if (!hasPermission()) {
            trySend(LocationState.PermissionDenied); close(); return@callbackFlow
        }
        if (!gpsEnabled()) {
            trySend(LocationState.ProviderDisabled); close(); return@callbackFlow
        }
        trySend(LocationState.Searching)

        val client = LocationServices.getFusedLocationProviderClient(appContext)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val good = loc.hasAccuracy() && loc.accuracy <= maxAccuracyM
                trySend(
                    if (good) LocationState.Fix(loc.latitude, loc.longitude, loc.accuracy)
                    else LocationState.LowAccuracy(
                        loc.latitude, loc.longitude,
                        if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE
                    )
                )
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) trySend(LocationState.Searching)
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            trySend(LocationState.PermissionDenied); close()
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }
}

/**
 * Fuente falsa para probar G5/G6 sin salir al campus (Mock Data del plan).
 * Emite los puntos en orden, uno cada [delayMs].
 */
class FakeLocationSource(
    private val points: List<Pair<Double, Double>>,
    private val delayMs: Long = 1500L,
    private val accuracyM: Float = 8f
) : LocationSource {
    override fun updates(): Flow<LocationState> = flow {
        emit(LocationState.Searching)
        for ((lat, lon) in points) {
            delay(delayMs)
            emit(LocationState.Fix(lat, lon, accuracyM))
        }
    }
}