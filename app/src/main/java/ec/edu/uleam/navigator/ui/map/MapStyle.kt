package ec.edu.uleam.navigator.ui.map

import android.content.Context

/** Estilo visual del mapa elegido por el usuario. SISTEMA sigue el modo claro/oscuro del teléfono. */
enum class MapStyle(val etiqueta: String) {
    CLARO("Claro"), OSCURO("Oscuro"), SISTEMA("Según el sistema");

    /** Siguiente estilo al tocar el botón del mapa: Claro → Oscuro → Sistema → Claro. */
    fun siguiente(): MapStyle = entries[(ordinal + 1) % entries.size]

    fun esOscuro(sistemaOscuro: Boolean): Boolean = when (this) {
        CLARO -> false
        OSCURO -> true
        SISTEMA -> sistemaOscuro
    }

    companion object {
        fun fromName(name: String?): MapStyle? = entries.firstOrNull { it.name == name }
    }
}

/** Preferencia guardada en el teléfono (SharedPreferences, sin dependencias nuevas). null = aún no elegida. */
class MapStylePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)

    fun load(): MapStyle? = MapStyle.fromName(prefs.getString(KEY, null))

    fun save(style: MapStyle) {
        prefs.edit().putString(KEY, style.name).apply()
    }

    private companion object {
        const val KEY = "map_style"
    }
}
