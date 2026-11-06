# ULEAM Campus Navigator — prototipo v0.1
Kotlin + Jetpack Compose + Room. Funciona sin internet.

## Abrir
1. Android Studio (Koala o más nuevo) → File > Open → carpeta `UleamNavigator`.
2. Esperar el Gradle Sync (JDK 17). Run en un emulador o teléfono (minSdk 24).

## Qué hace
- Carga `assets/*.csv` en Room la primera vez (CsvSeeder).
- Buscador con texto normalizado (sin tildes) sobre `lugares.busqueda`.
- Ruta con A* (origen: puerta o toque en el mapa = GPS simulado) y zona segura por Dijkstra.
- Interruptor "Evitar escaleras" (columna `tiene_escaleras`).
- Mapa dibujado con Canvas (pellizcar = zoom, arrastrar = mover).

## Estructura
data/ (Room + CSV) · routing/ (A*, Dijkstra, Haversine) · ui/ (ViewModel + Compose)
