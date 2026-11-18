package ec.edu.uleam.navigator.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ec.edu.uleam.navigator.ui.UserPosition

/** Interpola linealmente entre dos posiciones (fracción t en [0,1]). La precisión se conserva en Double. */
fun lerpPosition(from: UserPosition, to: UserPosition, t: Float): UserPosition = UserPosition(
    lat = from.lat + (to.lat - from.lat) * t,
    lon = from.lon + (to.lon - from.lon) * t,
    accuracyM = to.accuracyM
)

/**
 * Posición del usuario con movimiento suave: cada vez que llega una posición nueva el marcador
 * se desliza hasta ella en [durationMs] en vez de "teletransportarse".
 */
@Composable
fun rememberAnimatedUserPosition(target: UserPosition?, durationMs: Int): UserPosition? {
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target?.lat, target?.lon) {
        val to = target
        val from = shown
        when {
            to == null -> shown = null
            from == null -> shown = to
            else -> Animatable(0f).animateTo(1f, tween(durationMs, easing = LinearEasing)) {
                shown = lerpPosition(from, to, value)
            }
        }
    }
    return shown
}
