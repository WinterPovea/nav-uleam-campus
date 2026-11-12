# Nav-ULEAM Campus: Visión Senior, Brechas Técnicas y Distribución de Tareas por Integrante

> **Documento de Ingeniería y Plan Operativo**  
> **Rol:** Desarrollador Senior en Geolocalización, Diseño UI/UX Móvil y Arquitectura Full-Stack  
> **Objetivo:** Planificar el desarrollo restante, cerrar brechas entre la propuesta y la realidad, e incorporar características profesionales distribuidas entre los integrantes con trazabilidad en Git.  
> **Versión:** 1.0 (Noviembre 2026)

---

## 1. Diagnóstico de Brechas (Gap Analysis): Diapositivas vs. Estado Actual

Tras evaluar minuciosamente las 15 diapositivas de la presentación oficial frente a la base de código actual, se identifican las siguientes brechas técnicas a resolver:

```
[Presentación Teórica]                     [Estado Actual en Código]
- Tubería GeoJSON estandarizada ---------> Se cargan 3 archivos CSV manuales en assets
- Decisión de Renderizado (MapLibre/OSM)-> Canvas paramétrico 2D propio (muy ligero, pero sin tiles)
- Calibración física en terreno ---------> ✅ IMPLEMENTADO (T4.2: LocationFilter descarta saltos >20m/s e incertidumbre >30m)
- Pruebas End-to-End completas ----------> Existen 47 pruebas unitarias (A*, geometría y GNSS), faltan pruebas de UI Compose
- Orientación y Brújula peatonal -------> El usuario es un punto azul estático, no rota con el teléfono
- Recálculo de ruta en marcha (Reroute) -> ✅ IMPLEMENTADO (T3.1: distancia ortogonal >15m por >3s en segundo plano)
```

### Fortalezas Ya Construidas
- El algoritmo $A^*$ con heurística Haversine y Dijkstra para Zona Segura es matemáticamente riguroso y óptimo.
- El motor de detección de desvío (`GeoMath.distanceToSegment`, `AStarEngine.isOffRoute`) y recálculo automático (> 15 m por > 3 s) opera en segundo plano sin parpadeos.
- El filtro cinemático GNSS (`LocationFilter`) estabiliza la señal descartando rebotes *multipath* (> 20 m/s) y lecturas degradadas (> 30 m).
- El ciclo de vida de la ubicación con `SharingStarted.WhileSubscribed(5000)` previene efectivamente el drenaje de batería.
- La interfaz Compose cuenta con tarjeta colapsable, switch de accesibilidad y modo simulación funcional.

---

## 2. Propuestas de Innovación y Visión Experta Senior

Como especialista en sistemas de información geográfica móvil y aplicaciones nativas, propongo incorporar las siguientes 6 capacidades técnicas de alto impacto para elevar la aplicación a nivel de producción comercial:

### 2.1. Orientación por Brújula y Cono de Visión Dinámico (Sensor Fusion)
- **Problema Peatonal:** Un usuario parado en una intersección de caminos no sabe hacia dónde está apuntando su cuerpo respecto a la pantalla del teléfono.
- **Solución Técnica:** Integrar el sensor de vector de rotación de Android (`Sensor.TYPE_ROTATION_VECTOR` o combinación de acelerómetro + magnetómetro con `SensorManager.getRotationMatrix`). Dibujar sobre el *Pulsing Blue Dot* un **cono de visión translúcido (FOV) de 45°** que gire en tiempo real según el norte magnético/geográfico.

### 2.2. Recálculo Automático de Ruta por Desvío (Dynamic Off-Route Rerouting)
- **Problema Peatonal:** Si el usuario toma un camino alternativo, la ruta trazada queda obsoleta y confunde al caminante.
- **Solución Técnica:** En cada emisión del `LocationSource` mientras haya una ruta activa, calcular la distancia mínima ortogonal entre la posición actual y el segmento activo de la polilínea. Si la distancia supera los **15 metros durante más de 3 segundos consecutivos**, disparar silenciosamente `findRouteFromLocation` en una corrutina en segundo plano y actualizar la polilínea con una transición suave.

### 2.3. Asistente de Voz y Accesibilidad con Text-To-Speech (TTS)
- **Problema Peatonal:** Caminar mirando fijamente la pantalla del teléfono bajo el sol provoca accidentes y fatiga visual.
- **Solución Técnica:** Utilizar el motor nativo `android.speech.tts.TextToSpeech` en español para narrar las instrucciones generadas por `NavigationGuidance` ("A 20 metros, gira a la derecha hacia la Facultad de Ciencias de la Vida"). Incluir un botón de silenciar/activar voz en la UI.

### 2.4. Retroalimentación Háptica Diferenciada (Haptic Feedback)
- **Solución Técnica:** Utilizar `Vibrator` / `VibrationEffect`:
  - **Giro inminente (a 10 metros):** Dos pulsos cortos suaves (`VibrationEffect.createWaveform`).
  - **Llegada a destino o zona segura:** Un pulso largo de confirmación.
  - **Desvío de ruta:** Tres pulsos rápidos de alerta.

### 2.5. Navegación Vertical e Indoor (Pisos de Edificios)
- **Problema Peatonal:** Los bloques de la ULEAM tienen 2 a 4 plantas. El destino "Aula 204" no está a nivel de calle sino en el segundo piso.
- **Solución Técnica:** Ampliar el esquema de nodos con la propiedad `piso: Int` (0 = PB, 1 = Piso 1, 2 = Piso 2). Modelar las aristas verticales de escaleras conectando nodos del mismo bloque con diferente piso. En la UI, mostrar un selector de nivel (PB, 1, 2) cuando la ruta involucre interiores.

### 2.6. Búsqueda Enriquecida, Categorías y Ficha de Lugar (Bottom Sheet)
- **Solución Técnica:** Implementar chips horizontales de categorías (`Aulas`, `Administración`, `Cafeterías`, `Canchas`, `Baños`, `Zonas Seguras`). Al seleccionar un lugar, desplegar un `ModalBottomSheet` con información ampliada (descripción, carreras o departamentos que funcionan ahí, horario de atención y botón directo de "Cómo llegar").

---

## 3. Plan de Distribución de Trabajo por Integrante

Para que cada desarrollador tenga una contribución clara, medible y comprobable mediante commits y Pull Requests sin pisar el código de sus compañeros, el trabajo restante se divide en **12 tareas atómicas asignadas a perfiles específicos**:

---

### Módulo 1: Cartografía, Datos Vectoriales y GeoJSON (G1)

#### Tarea 1.1: Parser e Importador Nativo de GeoJSON a Room
- **Asignado a:** Desarrollador 1 (Especialista en Datos Geoespaciales)
- **Rama Git:** `feature/g1-geojson-importer`
- **Archivos a crear/modificar:**
  - `data/geojson/GeoJsonModels.kt` (Crear)
  - `data/geojson/GeoJsonParser.kt` (Crear)
  - `data/CsvSeeder.kt` (Modificar para soportar tanto GeoJSON como CSV)
- **Contrato de Entrada/Salida:**  
  Entrada: Archivo `campus_uleam.geojson` en `assets/`.  
  Salida: Inserción en `AppDatabase` de nodos y aristas conservando propiedades extendidas (`categoria`, `escaleras`, `piso`).
- **Commits sugeridos:**
  - `feat(data): definir modelos de datos para FeatureCollection GeoJSON`
  - `feat(data): implementar parser ligero de GeoJSON con org.json`
  - `test(data): agregar pruebas unitarias de parseo de GeoJSON del campus`

#### Tarea 1.2: Polígonos de Bloques y Geometría Topológica de Edificios
- **Asignado a:** Desarrollador 2 (Cartógrafo SIG)
- **Rama Git:** `feature/g1-campus-polygons`
- **Archivos a crear/modificar:**
  - `assets/edificios_poligonos.json` (Crear con coordenadas perimetrales de cada bloque)
  - `data/Entities.kt` (Agregar entidad `EdificioPolygonEntity`)
  - `data/CampusDao.kt` (Agregar query `obtenerPoligonosEdificios()`)
- **Contrato de Entrada/Salida:**  
  Permite al motor gráfico dibujar la huella física exacta de los edificios y áreas verdes en lugar de puntos aislados.
- **Commits sugeridos:**
  - `feat(sig): levantar coordenadas perimetrales de bloques universitarios`
  - `feat(data): agregar entidad y DAO para persistencia de polígonos de infraestructura`

---

### Módulo 2: Base de Datos y Persistencia Avanzada (G2)

#### Tarea 2.1: Historial de Búsquedas y Lugares Favoritos
- **Asignado a:** Desarrollador 3 (Desarrollador de Persistencia Android)
- **Rama Git:** `feature/g2-favorites-history`
- **Archivos a crear/modificar:**
  - `data/Entities.kt` (Agregar tablas `HistorialBusquedaEntity` y `FavoritoEntity`)
  - `data/CampusDao.kt` (Queries para insertar, eliminar y listar favoritos)
  - `data/CampusRepository.kt` (Exponer `Flow<List<LugarEntity>>` para favoritos)
- **Contrato de Entrada/Salida:**  
  La UI podrá marcar lugares con estrella y recordar las últimas 5 búsquedas del usuario de forma 100% offline.
- **Commits sugeridos:**
  - `feat(db): crear entidades de historial y lugares favoritos en Room`
  - `feat(repository): implementar operaciones reactivas de favoritos con Flow`
  - `test(db): pruebas unitarias de persistencia en Room para favoritos`

#### Tarea 2.2: Migración de Esquema Room y Soporte de Pisos (Navegación 3D/Vertical)
- **Asignado a:** Desarrollador 4 (Especialista en SQLite / Room)
- **Rama Git:** `feature/g2-schema-migration-v2`
- **Archivos a crear/modificar:**
  - `data/AppDatabase.kt` (Actualizar versión de base de datos a `version = 2` con `Migration(1, 2)`)
  - `data/Entities.kt` (Agregar campo `piso: Int = 0` en `NodoEntity` y `peso_accesibilidad: Double` en `AristaEntity`)
- **Commits sugeridos:**
  - `feat(db): agregar campo piso y peso de accesibilidad en entidades`
  - `refactor(db): implementar Migration(1, 2) en AppDatabase`

---

### Módulo 3: Motor Algorítmico y Navegación Dinámica (G3)

#### Tarea 3.1: Detección de Desvío y Recálculo Automático de Ruta (Rerouting) — ✅ COMPLETADA
- **Estado:** Implementada y verificada en dispositivo físico y pruebas unitarias.
- **Asignado a:** Desarrollador 5 (Especialista en Algoritmos de Grafos)
- **Rama Git:** `feature/g3-dynamic-rerouting`
- **Archivos modificados:**
  - `routing/astar/GeoMath.kt` (`distanceToSegment(point, segStart, segEnd)`)
  - `routing/astar/AStarEngine.kt` (`minDistanceToRoute()` e `isOffRoute(currentLoc, routeNodeIds, thresholdMeters = 15.0)`)
  - `ui/CampusViewModel.kt` (Ventana de confirmación `OFF_ROUTE_CONFIRMATION_MS = 3_000L`, recálculo asíncrono en `Dispatchers.Default` y validación inmediata de ubicación fuera del campus)
- **Contrato de Entrada/Salida:**  
  Evalúa si la coordenada GPS actual se alejó de la trayectoria activa. Emite evento de recálculo sin parpadeo de pantalla.
- **Commits sugeridos:**
  - `feat(routing): agregar calculo de distancia ortogonal punto-segmento`
  - `feat(routing): implementar algoritmo de deteccion de desvio de ruta`
  - `test(routing): pruebas unitarias para deteccion de fuera de ruta`

#### Tarea 3.2: Ponderación Temporal y Penalización de Caminos
- **Asignado a:** Desarrollador 6 (Matemático / Desarrollador Backend)
- **Rama Git:** `feature/g3-routing-penalties`
- **Archivos a crear/modificar:**
  - `routing/astar/GraphModels.kt` (Agregar `RoutingOptions(avoidStairs, preferShade, walkingSpeedMps)`)
  - `routing/astar/AStarEngine.kt` (Ajustar cálculo de costo $g(n)$ considerando velocidad peatonal y factor de inclinación)
- **Commits sugeridos:**
  - `feat(routing): incorporar opciones avanzadas de ponderacion en A*`
  - `test(routing): validar variaciones de tiempo segun perfil de caminata`

---

### Módulo 4: Sensores, Brújula y Calibración GNSS (G4)

#### Tarea 4.1: Sensor de Orientación (Magnetómetro / Brújula Peatonal)
- **Asignado a:** Desarrollador 7 (Especialista en Sensores de Hardware)
- **Rama Git:** `feature/g4-compass-sensor`
- **Archivos a crear/modificar:**
  - `location/CompassSource.kt` (Crear interfaz y clase `SensorManagerCompassSource` con `callbackFlow`)
  - `location/LocationSource.kt` (Integrar rumbo / `bearing` en el estado de ubicación)
  - `ui/CampusViewModel.kt` (Exponer `azimuth` en grados $[0, 360)$ hacia la UI)
- **Contrato de Entrada/Salida:**  
  Emite el ángulo de orientación del dispositivo en tiempo real aplicando un filtro de suavizado paso bajo (*low-pass filter*) para evitar vibraciones en la aguja.
- **Commits sugeridos:**
  - `feat(sensors): implementar CompassSource reactivo utilizando SensorManager`
  - `feat(sensors): aplicar filtro paso bajo para estabilizar azimuth de brujula`

#### Tarea 4.2: Filtro de Suavizado GPS y Calibración en Campo — ✅ COMPLETADA
- **Estado:** Implementada e integrada en `FusedLocationSource` con 5 pruebas unitarias en `LocationFilterTest.kt`.
- **Asignado a:** Desarrollador 8 (Ingeniero de Calidad y Hardware)
- **Rama Git:** `feature/g4-gnss-filtering`
- **Archivos creados/modificados:**
  - `location/LocationFilter.kt` (Filtro de precisión mínima $\le 30\text{ m}$ y descarte de saltos anómalos $>20 \text{ m/s}$)
  - `location/LocationSource.kt` (Aplicación del filtro antes de emitir estados a Compose y reinicio al perder satélites)
  - `test/.../location/LocationFilterTest.kt` (Pruebas unitarias con trazas GNSS ruidosas)
- **Commits sugeridos:**
  - `feat(location): implementar filtro para descarte de saltos GPS anomalos`
  - `test(location): pruebas unitarias con trazas GNSS ruidosas`

---

### Módulo 5: Renderizado y Cartografía en Compose (G5)

#### Tarea 5.1: Cono de Visión y Rotación Dinámica de Mapa
- **Asignado a:** Desarrollador 9 (Especialista en Gráficos y Animaciones Compose)
- **Rama Git:** `feature/g5-compass-canvas`
- **Archivos a crear/modificar:**
  - `ui/map/CampusMap.kt` (Modificar Canvas para dibujar cono de visión y opción de rotar mapa con dos dedos o según brújula)
  - `ui/map/MapProjection.kt` (Añadir transformaciones afines de rotación sobre el centro de pantalla)
- **Contrato de Entrada/Salida:**  
  Dibuja un cono de luz semitransparente que acompaña al *Pulsing Dot*, girando con el teléfono.
- **Commits sugeridos:**
  - `feat(map): proyectar cono de orientacion azimutal sobre el pulsing dot`
  - `feat(map): implementar rotacion fluida de camara con gestos multitouch`

#### Tarea 5.2: Dibujo de Polígonos de Bloques, Áreas Verdes y Vías Peatonales
- **Asignado a:** Desarrollador 10 (Diseñador UI / Gráficos)
- **Rama Git:** `feature/g5-infrastructure-polygons`
- **Archivos a crear/modificar:**
  - `ui/map/CampusMap.kt` (Renderizar polígonos rellenos de edificios, canchas y jardines con sombreado sutil)
- **Commits sugeridos:**
  - `feat(map): renderizar huellas poligonales de edificios y areas verdes`
  - `style(map): aplicar paleta institucional ULEAM en la cartografia base`

---

### Módulo 6: UI/UX, Accesibilidad y Voz (G6)

#### Tarea 6.1: Asistente de Voz Turn-by-Turn (Text-To-Speech) y Háptica
- **Asignado a:** Desarrollador 11 (Especialista en Accesibilidad y Android Framework)
- **Rama Git:** `feature/g6-voice-and-haptics`
- **Archivos a crear/modificar:**
  - `ui/guidance/VoiceGuidanceManager.kt` (Crear integrando `android.speech.tts.TextToSpeech`)
  - `ui/guidance/HapticFeedbackManager.kt` (Crear integrando `Vibrator`)
  - `ui/MainActivity.kt` (Conectar al cambio de instrucciones paso a paso)
- **Commits sugeridos:**
  - `feat(accessibility): implementar VoiceGuidanceManager con TTS en espanol`
  - `feat(accessibility): agregar retroalimentacion haptica en giros y emergencias`

#### Tarea 6.2: Ficha Desplegable de Detalles del Lugar (Modal Bottom Sheet) y Categorías
- **Asignado a:** Desarrollador 12 (Diseñador de Interfaz Móvil Compose)
- **Rama Git:** `feature/g6-place-details-sheet`
- **Archivos a crear/modificar:**
  - `ui/components/PlaceDetailBottomSheet.kt` (Crear)
  - `ui/components/CategoryFilterChips.kt` (Crear)
  - `ui/MainActivity.kt` (Integrar en la pantalla principal)
- **Commits sugeridos:**
  - `feat(ui): implementar chips de filtro rapido por categoria de lugar`
  - `feat(ui): disenar ModalBottomSheet con detalles de facultades y bloques`

---

## 4. Matriz Resumen de Asignación y Commits Trazables

| ID | Módulo | Tarea Principal | Responsable | Rama Git | Entregable Clave |
| :---: | :---: | :--- | :--- | :--- | :--- |
| **T1.1** | G1 | Parser GeoJSON a Room | Dev 1 | `feature/g1-geojson-importer` | `GeoJsonParser.kt` |
| **T1.2** | G1 | Polígonos de Edificios | Dev 2 | `feature/g1-campus-polygons` | `edificios_poligonos.json` |
| **T2.1** | G2 | Favoritos e Historial | Dev 3 | `feature/g2-favorites-history` | Entidades y DAOs favoritos |
| **T2.2** | G2 | Migración Room y Pisos | Dev 4 | `feature/g2-schema-migration-v2` | `Migration(1, 2)` |
| **T3.1** | G3 | Recálculo Fuera de Ruta ✅ | Dev 5 | `feature/g3-dynamic-rerouting` | `isOffRoute()` y tests (Completado) |
| **T3.2** | G3 | Ponderación Peatonal | Dev 6 | `feature/g3-routing-penalties` | Opciones de caminata |
| **T4.1** | G4 | Brújula y Orientación | Dev 7 | `feature/g4-compass-sensor` | `CompassSource.kt` |
| **T4.2** | G4 | Filtro Suavizado GNSS ✅ | Dev 8 | `feature/g4-gnss-filtering` | `LocationFilter.kt` (Completado) |
| **T5.1** | G5 | Cono de Visión y Rotación| Dev 9 | `feature/g5-compass-canvas` | Canvas con FOV |
| **T5.2** | G5 | Renderizado de Áreas | Dev 10 | `feature/g5-infrastructure-polygons`| Paleta y polígonos |
| **T6.1** | G6 | Voz TTS y Vibración | Dev 11 | `feature/g6-voice-and-haptics` | `VoiceGuidanceManager` |
| **T6.2** | G6 | Bottom Sheet de Detalles | Dev 12 | `feature/g6-place-details-sheet` | BottomSheet y chips |

---

## 5. Protocolo de Trabajo en Git y Criterios de Aceptación (DoD)

Para asegurar la calidad y evitar conflictos durante el trabajo simultáneo:

### Reglas de Commits (Conventional Commits)
Cada commit debe usar el estándar:
- `feat(alcance): nueva funcionalidad`
- `fix(alcance): corrección de un bug`
- `refactor(alcance): refactorización sin cambio funcional`
- `test(alcance): adición o mejora de pruebas`
- `docs(alcance): cambios en documentación`

### Regla de Oro del Pull Request
1. Ningún desarrollador hace `push` directo a `main` ni a `contenido-grupo-4-5-6`.
2. Se crea una rama a partir de `feature/refactor-demo-grupo-6` o `contenido-grupo-4-5-6`.
3. Al terminar la tarea, se abre un Pull Request hacia la rama de integración.
4. **Criterios de Aceptación (Definition of Done):**
   - El código compila limpiamente sin errores (`./gradlew assembleDebug`).
   - Pasan todas las pruebas unitarias existentes y las nuevas (`./gradlew test`).
   - El código incluye comentarios claros en funciones públicas y no degrada el consumo de batería.
   - Aprobación técnica de al menos un revisor de otro grupo antes del merge.
