# Nav-ULEAM Campus: Estado Actual Técnico, Arquitectura y Código Fuente

> **Documento de Contexto Técnico para Desarrolladores y Modelos de IA**  
> **Estado del Repositorio:** Funcional, compilando al 100% (`BUILD SUCCESSFUL`), con pruebas unitarias verdes.  
> **Ramas Principales:** `main`, `contenido-grupo-4-5-6`, `feature/refactor-demo-grupo-6`.  
> **Última Actualización:** Noviembre 2026

---

## 1. Resumen Ejecutivo del Estado del Software

El proyecto se encuentra en un estado maduro y estable para la demostración en la Casa Abierta:
- **Compilación Limpia:** Compila sin errores ni advertencias bloqueantes tanto en `./gradlew test` (todas las pruebas unitarias pasadas) como en `./gradlew assembleDebug` (APK generado).
- **Deuda Técnica Eliminada:** Se erradicó por completo el motor antiguo `RoutingEngine.kt`, unificando toda la búsqueda de rutas óptimas y evacuación de emergencia (*Safe Zone*) en el motor `AStarEngine.kt`.
- **Compatibilidad de IDE:** Se corrigió el conflicto de módulos de Android Studio que causaba el error *"Compilation is not supported for the following modules: UleamNavigator"*, purgando carpetas duplicadas y reconfigurando `.idea/gradle.xml`.

---

## 2. Stack Tecnológico y Dependencias (`app/build.gradle.kts`)

| Componente | Versión / Detalle | Justificación Técnica |
| :--- | :--- | :--- |
| **Lenguaje** | **Kotlin 2.0.0** | Soporte oficial con el nuevo Compose Compiler integrado (`org.jetbrains.kotlin.plugin.compose`). |
| **Android SDK** | `minSdk = 26` (Android 8.0 Oreo)<br>`targetSdk = 34` (Android 14)<br>`compileSdk = 34` | `minSdk = 26` permite uso de APIs nativas modernas de Java Time y Location sin desugaring pesado. |
| **Build Tools** | **Gradle 8.7** + **AGP 8.5.0** | Compatible con JDK 17 (JBR 17.0.14 de Android Studio). |
| **UI Framework** | **Jetpack Compose BOM 2024.09.00** | Interfaz 100% declarativa y reactiva con Material 3. |
| **Librería de Íconos** | `androidx.compose.material:material-icons-extended` | Íconos vectoriales para direcciones (giros, escaleras, advertencias, GPS). |
| **Lifecycle & Flow** | `lifecycle-runtime-compose:2.8.6`<br>`lifecycle-viewmodel-compose:2.8.6` | Manejo seguro del ciclo de vida para recolectar flujos de ubicación sin fugas de memoria. |
| **Persistencia (BDD)** | **Room 2.6.1** (`room-runtime`, `room-ktx`) | Base de datos SQLite local para almacenamiento de nodos, aristas y lugares. Procesamiento de anotaciones vía KSP (`2.0.0-1.0.24`). |
| **Geolocalización** | `play-services-location:21.3.0` | API moderna de Google para `FusedLocationProviderClient` con optimización de consumo de batería. |
| **Testing** | **JUnit 5 (Jupiter 5.10.2)** | Pruebas unitarias de algoritmos con soporte para pruebas parametrizadas y contratos de datos. |

---

## 3. Estructura del Código y Responsabilidades por Módulo

Ruta base del código fuente:  
`app/src/main/java/ec/edu/uleam/navigator/`

```
ec.edu.uleam.navigator/
├── data/                      # Capa de persistencia Room y mappers de grafo
│   ├── Entities.kt            # Entidades Room: Nodo, Arista, Lugar
│   ├── CampusDao.kt           # Consultas SQL optimizadas y búsqueda sin tildes
│   ├── AppDatabase.kt         # Instancia Room Database + Singleton
│   ├── CsvSeeder.kt           # Carga automática de CSVs en primer arranque
│   ├── GraphMappers.kt        # Conversión de entidades Room a CampusGraph
│   └── CampusRepository.kt    # Repositorio unificado para la UI
│
├── location/                  # Abstracción de sensores, filtrado GNSS y hardware
│   ├── LocationFilter.kt      # Filtro de incertidumbre (>30m) y saltos multipath (>20 m/s)
│   └── LocationSource.kt      # Interfaz reactiva, FusedLocationSource y FakeLocationSource
│
├── routing/astar/             # Motor algorítmico A* y navegación Turn-by-Turn
│   ├── GraphModels.kt         # Modelos de grafo: GraphNode, GraphEdge, RoutingResult
│   ├── GeoMath.kt             # Haversine, cálculo de rumbo (bearing) y giros relativos
│   ├── CampusGraph.kt         # Grafo inmutable validado en memoria
│   ├── AStarEngine.kt         # Algoritmo A*, Snap-to-node y Dijkstra para Zona Segura
│   └── NavigationGuidance.kt  # Generador de instrucciones de texto paso a paso
│
└── ui/                        # Capa de presentación Jetpack Compose
    ├── CampusViewModel.kt     # Estado global de la pantalla (UiState) y orquestación
    ├── MainActivity.kt        # Actividad principal, permisos y tarjeta flotante colapsable
    └── map/
        └── CampusMap.kt       # Canvas gráfico con infraestructura institucional y pulsing dot
```

---

## 4. Detalle Técnico de los Componentes Clave

### 4.1. Base de Datos y Datos Cartográficos (`data/`)
- **Archivos de Origen (`app/src/main/assets/`):**
  - `nodos.csv`: 135 nodos con columnas `id, lat, lon, tipo` (`CALLE`, `DESTINO`, `ZONA_SEGURA`), `tiene_escaleras`, `etiqueta`.
  - `aristas.csv`: 185 conexiones peatonales con `id_origen, id_destino, distancia_metros, tiene_escaleras`.
  - `lugares.csv`: Puntos de interés del campus con `id_nodo, codigo, nombre, descripcion, categoria, busqueda`.
- **`CsvSeeder.kt`:** Durante la primera inicialización de la base de datos Room, lee los archivos CSV de `assets/` e inserta los registros en una transacción atómica.
- **`CampusDao.kt`:** Realiza consultas directas a SQLite. La búsqueda de lugares normaliza el texto eliminando diacríticos/tildes para coincidencias rápidas como "bloque b-01" o "fcvt".
- **`GraphMappers.kt`:** Es el único puente desacoplador entre las tablas de Room y el motor algorítmico, convirtiendo `NodoEntity` y `AristaEntity` en las clases de datos puras `GraphNode` y `GraphEdge`.

### 4.2. Motor Algorítmico, Turn-by-Turn y Recálculo Dinámico (`routing/astar/`)
- **`AStarEngine.kt`:**
  - `findRoute(startNodeId, goalNodeId, avoidStairs)`: Ejecuta $A^*$ clásico utilizando una `PriorityQueue` ordenada por $f(n) = g(n) + h(n)$.
  - `findRouteFromLocation(location, goalNodeId, avoidStairs)`: Encuentra el nodo transitable de tipo `CALLE` más cercano dentro de un umbral de 100 metros (*snap-to-node*). Si el usuario está fuera de rango, retorna `RouteResult.Failure(OUTSIDE_CAMPUS)`.
  - `findNearestSafeZone(startNodeId, avoidStairs)`: Algoritmo de Dijkstra multidestino que calcula la distancia más corta hacia cualquier nodo clasificado como `ZONA_SEGURA`. Ignora nodos intermedios de tipo `DESTINO` para evitar atravesar interiores de edificios durante una evacuación.
  - `minDistanceToRoute(latitude, longitude, routeNodeIds)` e `isOffRoute(latitude, longitude, routeNodeIds, thresholdMeters = 15.0)`: Evalúan la desviación ortogonal mínima respecto a todos los segmentos de la polilínea activa para detectar desvíos reales.
- **`GeoMath.kt`:**
  - `haversine(lat1, lon1, lat2, lon2)`: Distancia en metros considerando el radio terrestre medio ($6,371,000 \text{ m}$).
  - `bearing(from, to)`: Rumbo azimutal en grados $[0^\circ, 360^\circ)$ entre dos puntos cartográficos.
  - `turnAngle(bearing1, bearing2)`: Diferencia angular relativa $[-180^\circ, 180^\circ]$ para inferir si el usuario debe continuar recto, girar a la derecha o girar a la izquierda.
  - `distanceToSegment(pointLat, pointLon, segStartLat, segStartLon, segEndLat, segEndLon)`: Distancia mínima ortogonal en metros proyectada sobre el plano tangente equirectangular local hacia un tramo de caminería.
- **`NavigationGuidance.kt`:**
  - Convierte la lista ordenada de nodos de una ruta en pasos legibles (`NavigationStep`):
    - `DEPART`: "Inicia tu recorrido en [Origen]".
    - `STRAIGHT`: "Avanza X metros recto".
    - `TURN_RIGHT`: "Gira a la derecha y avanza X metros".
    - `TURN_LEFT`: "Gira a la izquierda y avanza X metros".
    - `STAIRS`: "Usa las escaleras (precaución)".
    - `ARRIVE`: "Has llegado a [Destino]".
  - Incorpora compresión de segmentos rectos consecutivos para no saturar al usuario con indicaciones repetitivas.

### 4.3. Sensores, Filtrado GNSS, Ciclo de Vida y Batería (`location/`)
- **`LocationFilter.kt`:**
  - Evalúa cada muestra entrante (`GnssSample`) frente a la última posición válida aceptada (`lastAccepted`).
  - Descarta lecturas corruptas (`Invalid`), clasifica lecturas degradadas (`LowAccuracy` $> 30\text{ m}$) sin sobrescribir la posición válida previa, y rechaza saltos cinemáticos irreales (`RejectedJump` $> 20\text{ m/s}$) producidos por rebote de señal entre edificios (*multipath*).
  - Provee `filterTrace(samples)` para limpieza de trazas ruidosas y `reset()` ante pérdida de satélites.
- **`LocationSource.kt`:**
  - Sealed Interface `LocationState`: Estados explícitos de ubicación (`ProviderDisabled`, `PermissionDenied`, `Searching`, `Fix(lat, lon, accuracyM)`, `LowAccuracy(lat, lon, accuracyM)`).
  - `FusedLocationSource`: Implementado con `callbackFlow` sobre `FusedLocationProviderClient` integrado con `LocationFilter`.
  - **Eficiencia Energética:** La suscripción en `CampusViewModel` está configurada con:
    ```kotlin
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LocationState.Searching
    )
    ```
    Si el usuario minimiza la aplicación o apaga la pantalla, el hardware GNSS se desactiva pasados 5 segundos, protegiendo la batería del teléfono.
  - **Recálculo Inteligente Fuera de Ruta (`CampusViewModel.kt`):** Durante la navegación con GPS real, la ruta se mantiene estable mientras el usuario camine dentro del corredor de 15 m. Si se desvía $>15\text{ m}$ por más de 3 segundos consecutivos (`OFF_ROUTE_CONFIRMATION_MS = 3_000L`), recalcula la ruta asíncronamente en `Dispatchers.Default` sin parpadeo de UI, y valida de inmediato si el usuario se encuentra fuera del campus ($>100\text{ m}$).
  - `FakeLocationSource`: Implementación para emuladores o entornos sin GPS real.

### 4.4. Renderizado Cartográfico en Compose (`ui/map/CampusMap.kt`)
- **Canvas Paramétrico:** Proyecta las coordenadas geográficas reales del campus (latitud aproximada $-0.953$, longitud $-80.747$) a coordenadas de pantalla $(X, Y)$ con soporte interactivo para zoom (pellizco) y paneo (arrastre).
- **Infraestructura Diferenciada por Colores Institucionales:**
  - `BLOQUE`: Azul pizarra (`0xFF2C3E50`)
  - `ADMINISTRATIVO`: Azul profundo (`0xFF1A365D`)
  - `COMIDA`: Naranja cálido (`0xFFD35400`)
  - `DEPORTE`: Verde bosque (`0xFF27AE60`)
  - `CULTURA`: Púrpura institucional (`0xFF8E44AD`)
  - `ZONA_SEGURA`: Verde esmeralda de seguridad (`0xFF2ECC71`)
  - `ENTRADA / PUERTA`: Rojo ladrillo (`0xFFC0392B`)
- **Etiquetas de Texto Nativas:** Utiliza `android.graphics.Paint` para dibujar nombres de edificios con fondo tipo píldora de alto contraste, garantizando legibilidad en pantalla exterior.
- **Pulsing Blue Dot:** Marcador del usuario con animación infinita (`rememberInfiniteTransition`) que dibuja un halo pulsante y un círculo de radio de incertidumbre proporcional a la precisión en metros entregada por el sensor GNSS.
- **Botón FAB de Recentrado:** Botón flotante para recentrar inmediatamente la cámara sobre la posición actual del usuario.

### 4.5. Interfaz de Usuario y Flujo de Permisos (`ui/MainActivity.kt`)
- **Manejo Resiliente de Permisos:** Utiliza `ActivityResultContracts.RequestMultiplePermissions` solicitando `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION`.
  - Si el usuario deniega el permiso, se despliega un banner informativo con botón de reintento.
  - Si marca "No volver a preguntar", el botón redirige al usuario a la pantalla de configuración del sistema operativo (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).
- **Tarjeta Flotante Colapsable (`CollapsibleNavigationCard`):**
  - Métricas en tiempo real: Distancia total en metros y tiempo estimado de caminata en minutos.
  - Conmutador **"Modo Simulación (Demo)"**: Permite seleccionar el origen tocando directamente sobre cualquier punto del mapa o eligiendo una puerta, garantizando que la aplicación funcione en interiores o carpas cerradas durante la Casa Abierta.
  - Conmutador **"Evitar Escaleras"**: Recalcula la ruta en caliente respetando accesibilidad física.
  - Botón de Emergencia **"Zona Segura"**: Botón rojo destacado que activa de inmediato la evacuación al punto seguro más próximo.
  - Lista Desplegable Paso a Paso: Visualización interactiva de cada instrucción del recorrido con íconos vectoriales específicos.

---

## 5. Resumen de las Últimas Acciones Realizadas en el Código

1. **Refactorización del Módulo de Dominio:** Se removió el viejo `RoutingEngine.kt` y se migró su lógica a `AStarEngine.kt` y `CampusRepository.kt`.
2. **Actualización de Configuración:** `minSdk = 26` en `app/build.gradle.kts` con dependencias de Play Services y Lifecycle Compose.
3. **Optimización de Sensores:** Creación de `LocationSource` desacoplado y blindaje del ciclo de vida en `CampusViewModel`.
4. **Enriquecimiento Visual:** Implementación del Canvas cartográfico en `CampusMap.kt` con etiquetas nativas legibles y *Pulsing Dot*.
5. **UI de Navegación Completa:** Tarjeta colapsable en `MainActivity.kt` con Turn-by-Turn, switch de simulación y diálogo de permisos.
6. **Resolución de Error de IDE:** Eliminación de metadatos Java no-Gradle residuales (`.idea/modules.xml`, `.idea/UleamNavigator.iml`) y saneamiento del árbol de Git.
7. **Recálculo Inteligente Fuera de Ruta (`T3.1`):** Implementación de `GeoMath.distanceToSegment()`, `AStarEngine.isOffRoute()` ($>15\text{ m}$), confirmación temporal ($>3\text{ s}$) en `CampusViewModel`, validación inmediata de GPS fuera del campus al conmutar modo Demo y pruebas unitarias en `GeoMathTest` y `AStarEngineTest`.
8. **Filtro de Saltos GNSS Anómalos (`T4.2`):** Creación de `LocationFilter.kt` (descarte de saltos $>20\text{ m/s}$ y lecturas de baja precisión $>30\text{ m}$), integración en `FusedLocationSource` y suite de pruebas con trazas ruidosas en `LocationFilterTest.kt` (47 pruebas unitarias en verde).
9. **Pruebas y Verificación:** Ejecución exitosa de pruebas unitarias y generación de APK Debug.
