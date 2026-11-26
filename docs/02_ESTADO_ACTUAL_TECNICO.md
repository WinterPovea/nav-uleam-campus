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
├── location/                  # Abstracción de sensores, brújula, filtrado GNSS y hardware
│   ├── CompassSource.kt       # Sensor de orientación (TYPE_ROTATION_VECTOR) con filtro paso bajo
│   ├── LocationFilter.kt      # Filtro de incertidumbre (>30m) y saltos multipath (>20 m/s)
│   └── LocationSource.kt      # Interfaz reactiva, FusedLocationSource y FakeLocationSource
│
├── routing/astar/             # Motor algorítmico A* y navegación Turn-by-Turn
│   ├── GraphModels.kt         # Modelos de grafo: GraphNode, GraphEdge, RoutingResult
│   ├── GeoMath.kt             # Haversine, cálculo de rumbo (bearing), giros y distancia a segmento
│   ├── CampusGraph.kt         # Grafo inmutable validado en memoria
│   ├── AStarEngine.kt         # Algoritmo A*, Snap-to-node, isOffRoute y Dijkstra para Zona Segura
│   └── NavigationGuidance.kt  # Generador de instrucciones de texto paso a paso
│
└── ui/                        # Capa de presentación Jetpack Compose
    ├── CampusViewModel.kt     # Estado global de la pantalla (UiState) y orquestación
    ├── MainActivity.kt        # Actividad principal, permisos y tarjeta flotante colapsable
    └── map/
        ├── CampusMap.kt            # Canvas: capa base del campus, caminos, ruta, etiquetas, usuario y botones
        ├── CampusShapes.kt         # Carga y dibujo de la capa base (edificios, estadio, canchas, plaza, íconos),
        │                           # paleta MapTheme, etiquetas y PathVisibility (qué aristas se pintan)
        ├── MapProjection.kt        # Proyección WGS84 <-> Pantalla con zoom, paneo y rotación
        ├── RouteProgress.kt        # Progreso sobre la ruta, rumbo y hit-test de lugares
        ├── OriginPicker.kt         # Origen GPS: entrada del edificio donde está el usuario o mejor nodo cercano; llegada
        ├── MapStyle.kt             # Estilo del mapa (Claro / Oscuro / Sistema) y su preferencia guardada
        └── AnimatedUserPosition.kt # Desplazamiento suave del marcador del usuario
```

---

## 4. Detalle Técnico de los Componentes Clave

### 4.1. Base de Datos y Datos Cartográficos (`data/`)
- **Archivos de Origen (`app/src/main/assets/`):**
  - `nodos.csv`: 135 nodos con columnas `id, lat, lon, tipo` (`CALLE`, `DESTINO`, `ZONA_SEGURA`), `tiene_escaleras`, `etiqueta`.
  - `aristas.csv`: 185 conexiones peatonales con `id_origen, id_destino, distancia_metros, tiene_escaleras`.
  - `campus_shapes.csv`: capa base visual del mapa (solo diseño, no interviene en las rutas). Columnas `tipo, codigo, grupo, puntos` (lista `lat lon` separada por espacios). Tipos: `EDIFICIO` (bloques B-xx, `grupo` = sigla de facultad para el color), `OTRO` (edificios administrativos), `COMIDA`, `PISTA`, `CANCHA_ESTADIO`, `GRADA`, `CANCHA`, `CANCHA_VERDE`, `PLAZA`, y puntos `PARQUEO`, `AGUA`, `CAFE` y `ESCALERA` (escalera decorativa de 2 puntos). Se genera con `build_shapes_osm.py` a partir de `data/osm/osm_poligonos.geojson` (exportado de QGIS con QuickOSM: `building` / `highway` / `leisure`). Datos © OpenStreetMap contributors (ODbL); se incluyen en la app, por lo que el mapa sigue siendo 100% offline. `build_shapes.py` (trazado manual sobre fotos) queda obsoleto.
  - `lugares.csv`: Puntos de interés del campus con `id_nodo, codigo, nombre, descripcion, categoria, etiquetas, busqueda, activo`. Los bloques B-01..B-25 se buscan por **Facultad, sigla (FETAH, FCSDB, FCACC, FIIA, FACIVITEC, FCS), carreras y código de bloque**; `codigo` tiene la forma `SIGLA·B-xx`. Se genera con `build_lugares.py` a partir de `data/nodos_u.csv` (sin alterar `nodos.csv` ni `aristas.csv`). Como el CSV de QGIS no trae tildes, el script las restaura al mostrar (`nombre`, `descripcion`) con un diccionario de palabras (`ACENTOS`); `busqueda` queda siempre sin tildes. Para agregar una palabra nueva basta añadirla a `ACENTOS` y volver a ejecutar el script.
- **`CsvSeeder.kt`:** En la primera inicialización de Room inserta nodos, aristas y lugares. En arranques posteriores refresca solo la tabla `lugares` (`clearLugares()` + `insertLugares()`), de modo que las instalaciones existentes reciben el catálogo de facultades sin tocar el grafo.
- **`CampusDao.kt`:** Realiza consultas directas a SQLite. La búsqueda (`CampusRepository.filtrar`) normaliza a minúsculas, sin tildes ni puntuación, y exige que todos los términos aparezcan en `busqueda` ("facultad ciencias tecnologia", "facivitec", "odontologia", "b-16").
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

### 4.3. Sensores, Brújula Peatonal, Filtrado GNSS, Ciclo de Vida y Batería (`location/`)
- **`CompassSource.kt`:**
  - `SensorManagerCompassSource`: Implementado con `callbackFlow` sobre `Sensor.TYPE_ROTATION_VECTOR` (y respaldo en `TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD`).
  - Aplica un filtro circular paso bajo (`lowPassAzimuth`) interpolando por el arco angular más corto (`shortestAngleDelta`) para evitar saltos bruscos al cruzar el Norte ($359^\circ \leftrightarrow 0^\circ$), con umbral de emisión de $1.0^\circ$.
- **`LocationFilter.kt`:**
  - Evalúa cada muestra entrante (`GnssSample`) frente a la última posición válida aceptada (`lastAccepted`).
  - Descarta lecturas corruptas (`Invalid`), clasifica lecturas degradadas (`LowAccuracy` $> 30\text{ m}$) sin sobrescribir la posición válida previa, y rechaza saltos cinemáticos irreales (`RejectedJump` $> 20\text{ m/s}$) producidos por rebote de señal entre edificios (*multipath*).
  - Provee `filterTrace(samples)` para limpieza de trazas ruidosas y `reset()` ante pérdida de satélites.
- **`LocationSource.kt`:**
  - Sealed Interface `LocationState`: Estados explícitos de ubicación (`ProviderDisabled`, `PermissionDenied`, `Searching`, `Fix(lat, lon, accuracyM)`, `LowAccuracy(lat, lon, accuracyM)`).
  - `FusedLocationSource`: Implementado con `callbackFlow` sobre `FusedLocationProviderClient` integrado con `LocationFilter` y lectura inmediata inicial mediante `client.lastLocation`.
  - **Reinicio en Caliente y Eficiencia Energética:** En `CampusViewModel`, tanto `locationState` (con `flatMapLatest` para recarga inmediata tras conceder permisos sin reiniciar la app) como `azimuthDeg` utilizan `SharingStarted.WhileSubscribed(5000)`, apagando el GPS y la brújula 5 segundos después de minimizar la aplicación.
  - **Recálculo Inteligente Fuera de Ruta (`CampusViewModel.kt`):** Durante la navegación con GPS real, la ruta se mantiene estable mientras el usuario camine dentro del corredor de 15 m. Si se desvía $>15\text{ m}$ por más de 3 segundos consecutivos (`OFF_ROUTE_CONFIRMATION_MS = 3_000L`), recalcula la ruta asíncronamente en `Dispatchers.Default` sin parpadeo de UI, y valida de inmediato si el usuario se encuentra fuera del campus ($>100\text{ m}$).
  - `FakeLocationSource`: Implementación para emuladores o entornos sin GPS real.

### 4.4. Renderizado Cartográfico en Compose (`ui/map/CampusMap.kt` y `MapProjection.kt`)
- **Canvas Paramétrico y Rotación Afín (`MapProjection.kt`):** Proyecta las coordenadas geográficas reales del campus (latitud aproximada $-0.953$, longitud $-80.747$) a coordenadas de pantalla $(X, Y)$ con soporte interactivo para zoom, paneo compensado y rotación con dos dedos o alineada con la brújula.
- **Capa base estilo OpenStreetMap (`CampusShapes.kt`):** terreno crema sobre fondo oscuro con vegetación decorativa; edificios con la huella real de OSM, sombra, borde blanco y color por facultad (FCS celeste, FIIA azul oscuro, FACIVITEC verde, FETAH naranja, FCACC azul, FCSDB vino; administrativos gris azulado); estadio con pista, gradas y cancha con líneas; canchas, Plaza Cultural Centenario, cafeterías en amarillo, íconos de parqueo (P), hidratación y cafetería (El bosque, Tasty Food 2). Solo se rotulan los bloques del catálogo (B-xx), el estadio, la plaza y las Tasty.
- **Caminos:** se dibujan sobre las aristas del grafo (trazadas en QGIS sobre OSM) como veredas blancas con borde gris; las escaleras, en terracota con peldaños y recortadas para no tapar el camino. `PathVisibility` decide qué se pinta (solo visual, A* usa todas las aristas): oculta los accesos finales a lugares (`DESTINO`/`ZONA_SEGURA`) y tramos marcados por el equipo, pinta como escalera tramos que prolongan escaleras reales y agrega escaleras decorativas (`ESCALERA`).
- **Estilo claro / oscuro (`MapStyle.kt` + `MapTheme`):** `MapTheme` es la paleta del mapa y `MapTheme.dark` es estado de Compose, así que el Canvas se redibuja al cambiarlo. En la primera ejecución un diálogo pregunta el estilo (modo claro, modo oscuro o usar la configuración del sistema) y se guarda en `SharedPreferences` (`map_prefs`). Un botón arriba a la izquierda alterna Claro → Oscuro → Sistema. Los edificios conservan su color por facultad en ambos modos. La interfaz completa (búsqueda, tarjeta inferior, diálogos) usa el mismo modo: `MaterialTheme` con `UleamLightColors` / `UleamDarkColors` en `MainActivity.kt`; en oscuro los íconos (lupa, brújula, ubicación, estilo) y el botón Zona Segura pasan a azul.
- **Cámara limitada al campus:** zoom mínimo 1.0 y `clampPan` impide arrastrar el mapa fuera del área de edificios y nodos; descuenta lo que tapan la búsqueda (arriba) y la tarjeta (abajo) para que los edificios del borde (B-11, B-12, etc.) se vean completos. El fondo alrededor del campus usa un tono cercano al terreno.
- **Zonas seguras:** se dibujan como la señal verde de "Punto de encuentro" (cuadro verde con flechas hacia una persona).
- **Crédito OSM:** botón ⓘ en la esquina inferior izquierda que abre el diálogo "Créditos del mapa" (licencia ODbL).
- **Infraestructura Diferenciada por Colores Institucionales (modo sin capa base):**
  - `BLOQUE`: Azul pizarra (`0xFF2C3E50`)
  - `ADMINISTRATIVO`: Azul profundo (`0xFF1A365D`)
  - `COMIDA`: Naranja cálido (`0xFFD35400`)
  - `DEPORTE`: Verde bosque (`0xFF27AE60`)
  - `CULTURA`: Púrpura institucional (`0xFF8E44AD`)
  - `ZONA_SEGURA`: Verde esmeralda de seguridad (`0xFF2ECC71`)
  - `ENTRADA / PUERTA`: Rojo ladrillo (`0xFFC0392B`)
- **Etiquetas de Texto Nativas:** Utiliza `android.graphics.Paint` para dibujar nombres de edificios con fondo tipo píldora de alto contraste, garantizando legibilidad en pantalla exterior.
- **Pulsing Blue Dot y Cono de Visión (FOV 45°):** Marcador del usuario con animación infinita (`rememberInfiniteTransition`), halo de incertidumbre GNSS en metros y **cono de visión azimutal translúcido de 45°** que rota en tiempo real con la orientación física del teléfono.
- **Botones Flotantes de Brújula/Norte y Recentrado:** Botones FAB para restablecer el Norte / alinear con la vista de la brújula y recentrar la cámara sobre la posición actual del usuario.

### 4.5. Interfaz de Usuario y Flujo de Permisos (`ui/MainActivity.kt`)
- **Manejo Resiliente de Permisos:** Utiliza `ActivityResultContracts.RequestMultiplePermissions` solicitando `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION` con activación inmediata en caliente (`vm.refreshLocationPermissionState()`).
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
8. **Filtro de Saltos GNSS Anómalos (`T4.2`):** Creación de `LocationFilter.kt` (descarte de saltos $>20\text{ m/s}$ y lecturas de baja precisión $>30\text{ m}$), integración en `FusedLocationSource` y suite de pruebas con trazas ruidosas en `LocationFilterTest.kt`.
9. **Brújula Peatonal, Cono de Visión y Rotación de Mapa (`T4.1` + `T5.1`):** Creación de `CompassSource.kt` con filtro circular paso bajo, `MapProjection.kt` con rotación afín, renderizado del cono de visión de 45° y botón de brújula en `CampusMap.kt`, recarga en caliente de permisos GPS y pruebas unitarias en `CompassSourceTest.kt` (51 pruebas unitarias en verde).
10. **Búsqueda por Facultades/Carreras:** `lugares.csv` enriquecido (30 lugares con facultad/carreras), recarga segura del catálogo en `CsvSeeder`, normalización de puntuación en `CampusRepository`, subtítulo con bloque y carreras en los resultados y etiquetas del mapa `SIGLA·B-xx` (con primera carrera a zoom ≥ 2.8). Pruebas nuevas en `RealCampusDataTest`.
11. **Zoom anclado, "Mi ubicación" y "Empezar ruta":** el pellizco hace zoom sobre el punto bajo los dedos (`MapProjection.panForZoomAt`); botón extendido "Mi ubicación" cuando el usuario queda fuera de pantalla (y los botones flotantes ya no quedan tapados por la tarjeta inferior); botón "Empezar ruta" en la tarjeta de destino que activa el modo navegación (`CampusViewModel.empezarRuta/terminarRuta`): la cámara sigue al usuario, el marcador se desliza entre lecturas (`AnimatedUserPosition.kt`), el tramo recorrido se atenúa y se muestran los metros restantes (`RouteProgress.kt`). Durante la navegación el mapa gira suavemente para dejar el rumbo del usuario hacia arriba (GPS real: brújula, con el rumbo del tramo como respaldo; simulación: rumbo del tramo); se desactiva si el usuario gira el mapa a mano o toca la brújula. En Modo Simulación un caminante virtual recorre la ruta a 1,4 m/s (paso humano); en GPS real se detecta la llegada a ≤ 15 m del destino. Pruebas en `MapMathTest`.
12. **Elegir destino tocando el mapa:** un toque sobre un lugar (radio de 28 dp, `PlaceHitTest`) lo selecciona como destino, traza la ruta de vista previa y la tarjeta inferior muestra sus carreras y el botón "Empezar ruta". Un toque en espacio vacío conserva el comportamiento anterior (fijar origen en Modo Simulación) y mantener pulsado fija el origen aunque haya un lugar debajo. Los toques se ignoran mientras se navega.
13. **Ajustes de búsqueda y catálogo:** la tarjeta de búsqueda se oculta mientras se navega y reaparece al terminar o llegar; el teclado se cierra al elegir un destino; placeholder "Buscar Bloque, Facultad, Carrera, Otros…"; Tasty Food 1 figura con sus dos entradas (nodos 305 y 318) y El bosque pasa a categoría COMIDA (cafetería).
14. **Mapa del campus con datos de OpenStreetMap:** capa base vectorial (`campus_shapes.csv` + `CampusShapes.kt`) generada desde QGIS/QuickOSM con `build_shapes_osm.py`: edificios con dimensiones reales y color por facultad, estadio, canchas, Plaza Cultural, CIEC, Vicerrectorado y Agromarket como edificios, íconos de parqueo, agua y cafetería. Caminos dibujados sobre las aristas con estilo de vereda y escaleras con peldaños; tramos sobrantes ocultos y escaleras ajustadas según capturas del equipo. Crédito OSM en un botón ⓘ.
15. **Catálogo B-20 / B-21:** el B-20 (nodo 220) agrupa Psicología, Fisioterapia, Fonoaudiología, Terapia Ocupacional y Nutrición; el B-21 (nodo 221) figura como "No disponible por el momento" (`CARRERAS_DE` / `SIN_CARRERAS` en `build_lugares.py`).
16. **Interfaz:** barra de búsqueda más compacta (placeholder en una línea) y la barra de Puertas (P1–P4) también se oculta mientras se navega.
17. **Origen GPS realista (`OriginPicker.kt`):** si el GPS ubica al usuario dentro de la huella de un edificio, la ruta parte de una entrada de ese edificio (nodos DESTINO a ≤ 12 m del contorno), la que deja la ruta más corta; fuera de edificios se comparan los nodos de calle cercanos (hasta 30 m más lejos que el más cercano) y se elige el que minimiza caminar + ruta. Con GPS real se dibuja una línea punteada desde la posición del usuario hasta el inicio de la ruta. Elegir una puerta o un origen manual no se reemplaza por el GPS.
18. **Aviso de llegada:** al elegir un destino estando a ≤ 20 m de él (o dentro de su edificio) la tarjeta muestra "📍 Ya estás en tu destino" en lugar de la ruta; durante la navegación la llegada se detecta con el mismo criterio (`ARRIVAL_RADIUS_M = 20`).
19. **Fuera del campus:** con GPS real y el usuario a más de ~110 m del campus aparece "📍 No te encuentras en el campus ULEAM"; la cámara se queda en la universidad y "Mi ubicación" muestra el campus completo.
20. **Modo oscuro del mapa y cámara limitada:** ver la sección 4.4 (estilo Claro / Oscuro / Sistema, límite de desplazamiento y nuevo ícono de zonas seguras).
21. **Pruebas y Verificación:** 51 pruebas previas en verde; las nuevas pruebas (`RealCampusDataTest`, `MapMathTest`, `CampusShapesTest`, `OriginPickerTest`: salida por la entrada del edificio, llegada y fuera del campus; `MapStyleTest`: ciclo de estilos y modo sistema) deben ejecutarse con `./gradlew :app:testDebugUnitTest` en un entorno con Android SDK.
22. **Actualización del grafo desde QGIS (nov-2026):** nuevo generador `build_grafo.py` (`data/nodos_u.csv` + `data/aristas.csv` → `nodos.csv`/`aristas.csv`, con informe de validación). Se agregaron la segunda entrada de B-16 (nodo 226), la Dirección de Informática e Innovación Tecnológica (DIIT, nodo 329, edificio trazado sobre la captura de QGIS) y el nodo 495; las aristas con `Doble_Via = Si` se dibujan como vías más anchas (`VIA_DOBLE` en `campus_shapes.csv`). `CsvSeeder` ahora recarga nodos y aristas cuando cambian los CSV (huella SHA-256 en SharedPreferences), así las instalaciones existentes reciben el grafo nuevo sin desinstalar. La forma real de las aristas se toma del shapefile `data/qgis/aristas_uleam.shp` (107 aristas con curvas): la distancia de cada arista es el largo de esa forma y el mapa dibuja caminos y ruta siguiéndola (filas `ARISTA` de `campus_shapes.csv`, clase `EdgeShapes`). Orden para regenerar: `build_grafo.py` → `build_lugares.py` → `build_shapes_osm.py`.
23. **Calles de OpenStreetMap:** `data/osm/calles_osm.geojson` (QuickOSM, clave `highway`) se convierte en filas `VIA` (calles, con la clase OSM en `grupo`: primaria naranja, terciaria, residencial y de servicio blancas), `PELDANOS` (escaleras) de `campus_shapes.csv`, recortadas al terreno de la universidad (lo que queda fuera no se dibuja). Las veredas (`footway`) se dibujan como filas `VEREDA` (ver punto 24). Se agregó la arista 433–434 (camino al oeste del B-13 hacia la Puerta 3). Con esas filas el mapa dibuja las calles reales de OSM en lugar de los caminos a partir de las aristas; la ruta azul sigue usando las aristas (con su forma de QGIS). Si se borra `calles_osm.geojson` y se regenera, vuelve el dibujo por aristas.
24. **Solo la universidad:** `build_shapes_osm.py` calcula el contorno cóncavo del campus (alpha shape con `scipy` sobre nodos, aristas, bloques y edificios con entrada) y lo usa para quitar las figuras y tramos de calle de fuera (casas y calles vecinas); el fondo crema conserva la forma redondeada. Las veredas de OSM se dibujan como caminos color arena, más angostos y sin borde, distintos de las calles. Los nombres de los lugares se muestran completos, en varias líneas (`wrapLabel`). Los bloques se rotulan "Bloque B-16 (FACIVITEC)" y la barra de Puertas solo aparece en Modo Simulación.
25. **Interfaz en modo oscuro:** la búsqueda, los resultados, la barra de Puertas, la tarjeta inferior (destino, metros/minutos, instrucciones, "Ya estás en tu destino") y los diálogos siguen el estilo elegido para el mapa. En modo oscuro el acento rojo institucional cambia a azul (`#64B5F6`) en íconos y el botón Zona Segura a `#1E88E5`; en modo claro se conserva el rojo ULEAM.
26. **Inicio de ruta desde el usuario y llegada más estricta:** fuera de edificios, `OriginPicker` proyecta la posición GPS sobre el camino (arista, con su forma de QGIS) más cercano que se alcance sin atravesar un edificio y evalúa ambos extremos de ese camino; la ruta se dibuja desde ese punto (`routeLead`), y la distancia/minutos y los metros restantes lo incluyen. Dentro de un edificio sale por la entrada que deja la ruta más corta (p. ej. B-13 → Puerta 3 por la entrada norte, nodo 314). La llegada ahora se marca a ≤ 8 m del destino (antes 20 m) o dentro de su edificio. El ícono de Tasty Food 2 se corrió a su ubicación real.
27. **Actualización desde QGIS (25-nov-2026):** nodos leídos directamente de `nodos_u.gpkg` (geometría) y aristas del shapefile: segunda entrada de B-08 (nodo 227, "Bloque B-08, acceso 2"), entradas de B-05 (205) y B-08 (208) reubicadas, nodos 230 y 326 (Tasty Food 2) ajustados; aristas nuevas 205–405, 227–424, 227–426 (se quitó 205–409). Se conserva la arista 433–434 (Puerta 3) y el reparto de carreras B-20/B-21 de la app.
28. **GPS más estable, rendimiento, voz y búsqueda (nov-2026):**
    - **GPS estable** (`location/GpsSmoother.kt`): las lecturas se suavizan con un filtro de Kalman simple que pesa cada una por su precisión (el punto azul deja de saltar sin quedarse atrás al caminar; un salto > 60 m reinicia el filtro). El origen sin destino solo cambia si el nuevo nodo se mantiene ≥ 3 s (`OriginStabilizer`). El radio de llegada depende de la precisión reportada: entre 6 y 12 m (`arrivalRadiusM`), además de "dentro del edificio del destino".
    - **Rendimiento:** `AStarEngine.distancesTo(destino)` calcula en una sola pasada (Dijkstra inverso, mismas reglas de DESTINO y escaleras) la distancia de todos los nodos al destino; `OriginPicker` la usa en cada lectura GPS en lugar de hasta 16 A*. Se guarda en caché mientras no cambien el destino ni "Evitar escaleras". La prueba `DistancesToTest` comprueba que coincide con A* para todos los pares origen–lugar.
    - **Guía por voz** (`ui/voice/`): con el `TextToSpeech` de Android (sin librerías nuevas, sin internet si el teléfono tiene la voz en español) se anuncian el inicio ("Ruta a Bloque B-16 iniciada. 240 metros"), los giros ("Gira a la derecha") y "Escaleras adelante" al estar a ≤ 15 m del punto, y "Llegaste a …". Funciona también en Modo Simulación. Se activa/desactiva con el interruptor "Guía por voz" de la tarjeta (se recuerda).
    - **Búsqueda:** al tocar la búsqueda aparecen chips de categoría (Bloques, Dónde comer —patios de comidas Tasty Food, cafetería El bosque, Agromarket y Laboratorio gastronómico—, Administrativos, Zonas seguras, Deporte y cultura) y los 5 destinos recientes (guardados en el teléfono). Las sugerencias se cierran con la X, tocando el mapa o con "Atrás". "fcvt" encuentra la FACIVITEC.
    - **Mapa:** las etiquetas ya no se enciman con zoom bajo (`LabelPlacer`: la que choca con otra ya dibujada se omite; el origen y el destino siempre se muestran). Al abrir en modo oscuro ya no hay un destello claro (el tema se decide antes del primer cuadro).
    - **Un solo script de datos:** `python actualizar_mapa.py [--gpkg ruta] [--shp ruta]` lee `data/qgis/nodos_u.gpkg` (posición desde la geometría) y `data/qgis/aristas_uleam.shp`, escribe `data/nodos_u.csv` y `data/aristas.csv` (manteniendo la arista 433–434 y las carreras de B-20/B-21 de la app) y ejecuta `build_grafo.py`, `build_lugares.py` y `build_shapes_osm.py`.
    - **Integración continua:** `.github/workflows/android.yml` corre `:app:testDebugUnitTest` y `:app:assembleDebug` (JDK 17, Gradle 8.7) en cada push y pull request y deja el APK debug como artefacto descargable.
29. **Sin Modo Simulación (nov-2026, rama `Uleam-go-map`):** se eliminó el modo demo (interruptor "Demo", barra de Puertas P1–P4, origen manual tocando el mapa y caminante virtual). La app usa siempre el GPS real: al abrir, el origen es "Obteniendo tu ubicación…" y luego "Tu ubicación" (o "Tu ubicación (B-06)" si estás dentro de un bloque), sin mostrar números de nodo. Fuera del campus el mensaje es solo "Pareces estar fuera del campus ULEAM." Los interruptores de Evitar escaleras y Guía por voz tienen contorno visible cuando están apagados y un check cuando están encendidos.
30. **Ruta por el centro de la calle:** `snap_aristas.py` (llamado desde `build_shapes_osm.py`) ajusta el DIBUJO de las aristas entre dos nodos de calle (CONEXION) a la línea central de la calle de OSM por la que corren (67 de 95). La ruta se ve curva y por la carretera en vez de en zigzag por la vereda. Solo se ajusta si la arista y la calle van juntas en todo su largo (≤ 13 m) y con largo parecido; las que llegan a entradas, puertas o zonas seguras conservan la forma de QGIS. La distancia usada por A* no cambia. `EdgeShapes.routePoints` une los tramos ajustados por la calle (sin desviarse hacia el nodo, que puede estar en la vereda).
31. **Menos atraso del GPS y llegada correcta junto a otros edificios:** lecturas GPS cada 1 s (mínimo 0,5 s; antes 2 s), filtro de suavizado más ágil (≈ 1,4 m de atraso caminando) y animación del punto azul de 450 ms (antes 900 ms). Durante la navegación la llegada también se evalúa con la lectura sin suavizar. "Ya estás en tu destino" por estar dentro de un edificio solo cuenta si la entrada del destino pertenece a ESE edificio (el más cercano a la entrada): estar en Tasty Food 1 ya no marca como llegado al Vicerrectorado (317), cuya entrada queda a 9 m. Además, si un edificio tiene entradas de lugares distintos, solo "llegas" al lugar cuya entrada es la más cercana a ti dentro del edificio (los accesos del mismo lugar, como "acceso 2" o "entrada 2", cuentan como uno): dentro de B-05 ya no marca llegado al Laboratorio gastronómico.
32. **Datos de QGIS del 26-nov-2026:** nodos reubicados (102, 210, 225, 304, 305, 314, 322, 409, 433, 434) y aristas nuevas 225–466, 323–327, 206–419, 314–436 y 314–434 (se mantiene la 433–434). B-20 queda solo con Psicología y B-21 (ya disponible) con Fisioterapia, Fonoaudiología, Terapia Ocupacional y Nutrición y Dietética (`CARRERAS_FIJAS` en `actualizar_mapa.py`). Numeración real de los Tasty Food (`TASTY_NOMBRE` en `build_lugares.py`): 2 junto al B-20 (321), 3 junto al B-08 (326), 4 junto a la Empresa pública (327). Todos los lugares siguen teniendo ruta desde las 4 puertas.

### Vista del mapa

| Modo claro | Modo oscuro |
|---|---|
| ![Mapa en modo claro](mapa_preview.png) | ![Mapa en modo oscuro](mapa_preview_oscuro.png) |

*Vistas generadas a partir de `campus_shapes.csv` (edificios, calles y escaleras de OpenStreetMap recortados al campus), `nodos.csv` y `aristas.csv`, con la misma paleta de la app y sin la interfaz.*
