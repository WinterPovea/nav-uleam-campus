package ec.edu.uleam.navigator.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs

/**
 * Fuente reactiva de orientación peatonal (azimut en grados [0, 360) respecto al Norte).
 */
interface CompassSource {
    fun azimuthUpdates(): Flow<Float?>
}

/**
 * Implementación basada en SensorManager (TYPE_ROTATION_VECTOR con respaldo en
 * acelerómetro + magnetómetro) y filtro circular paso bajo para estabilizar el cono de visión.
 */
class SensorManagerCompassSource(
    context: Context,
    private val alpha: Float = DEFAULT_LOW_PASS_ALPHA,
    private val minEmitDeltaDeg: Float = DEFAULT_MIN_EMIT_DELTA_DEG
) : CompassSource {

    private val appContext = context.applicationContext

    override fun azimuthUpdates(): Flow<Float?> = callbackFlow {
        val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = if (rotationSensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
        val magSensor = if (rotationSensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) else null

        if (rotationSensor == null && (accelSensor == null || magSensor == null)) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false

        var smoothedAzimuth: Float? = null
        var lastEmittedAzimuth: Float? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rawAzimuthDeg: Float = when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val deg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        normalizeDegrees(deg)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, minOf(event.values.size, 3))
                        hasGravity = true
                        if (!hasGeomagnetic) return
                        if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) return
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val deg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        normalizeDegrees(deg)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, minOf(event.values.size, 3))
                        hasGeomagnetic = true
                        if (!hasGravity) return
                        if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) return
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val deg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        normalizeDegrees(deg)
                    }
                    else -> return
                }

                val filtered = lowPassAzimuth(smoothedAzimuth, rawAzimuthDeg, alpha)
                smoothedAzimuth = filtered

                val prevEmitted = lastEmittedAzimuth
                if (prevEmitted == null || abs(shortestAngleDelta(prevEmitted, filtered)) >= minEmitDeltaDeg) {
                    lastEmittedAzimuth = filtered
                    trySend(filtered)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    companion object {
        const val DEFAULT_LOW_PASS_ALPHA = 0.18f
        const val DEFAULT_MIN_EMIT_DELTA_DEG = 1.0f

        /** Normaliza cualquier ángulo en grados al intervalo [0, 360). */
        fun normalizeDegrees(degrees: Float): Float {
            if (!degrees.isFinite()) return 0f
            return ((degrees % 360f) + 360f) % 360f
        }

        /** Diferencia angular mínima con signo en [-180, 180) desde [fromDeg] hacia [toDeg]. */
        fun shortestAngleDelta(fromDeg: Float, toDeg: Float): Float {
            return ((toDeg - fromDeg + 540f) % 360f) - 180f
        }

        /**
         * Filtro paso bajo circular que evita saltos artificiales al cruzar el norte (359° <-> 0°).
         */
        fun lowPassAzimuth(
            currentDeg: Float?,
            targetDeg: Float,
            alpha: Float = DEFAULT_LOW_PASS_ALPHA
        ): Float {
            val normalizedTarget = normalizeDegrees(targetDeg)
            if (currentDeg == null || !currentDeg.isFinite()) return normalizedTarget
            val clampedAlpha = alpha.coerceIn(0.01f, 1.0f)
            val delta = shortestAngleDelta(currentDeg, normalizedTarget)
            return normalizeDegrees(currentDeg + clampedAlpha * delta)
        }
    }
}
