package ec.edu.uleam.navigator.ui.map

/**
 * Evita que las etiquetas del mapa se encimen: cada etiqueta reserva su rectángulo en pantalla y la que
 * choque con una ya dibujada se omite (al acercar el mapa hay espacio y vuelve a aparecer).
 * Se crea uno nuevo en cada cuadro; el orden de llamada es la prioridad. Lógica pura, sin Android.
 */
class LabelPlacer(private val paddingPx: Float = 4f) {
    private val rects = ArrayList<FloatArray>()

    /** Reserva el rectángulo si está libre; devuelve false si se encima con otro (no dibujar). */
    fun tryPlace(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        val l = left - paddingPx; val t = top - paddingPx; val r = right + paddingPx; val b = bottom + paddingPx
        if (rects.any { l < it[2] && r > it[0] && t < it[3] && b > it[1] }) return false
        rects.add(floatArrayOf(l, t, r, b))
        return true
    }

    val count: Int get() = rects.size
}
