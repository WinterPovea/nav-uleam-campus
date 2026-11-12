# Nav-ULEAM Campus: Contexto Institucional, Misión y Diapositivas Oficiales

> **Documento de Contexto para Inteligencia Artificial y Equipo de Desarrollo**  
> **Proyecto:** Sistema Offline de Orientación y Navegación Peatonal Inteligente para el Campus Universitario  
> **Institución:** Universidad Laica Eloy Alfaro de Manabí (ULEAM) — Manta, Ecuador  
> **Evento de Lanzamiento:** Casa Abierta Universitaria  
> **Versión del Contexto:** 1.0 (Noviembre 2026)

---

## 1. Ficha Técnica y Propósito del Proyecto

El proyecto **Nav-ULEAM Campus** es una aplicación móvil nativa para Android diseñada específicamente para resolver el problema de orientación, localización y navegación peatonal dentro del campus matriz de la **Universidad Laica Eloy Alfaro de Manabí (ULEAM)** en Manta.

### Pilares Fundacionales (Innegociables)
1. **100% Offline (Sin Conexión a Internet):**  
   Durante ferias masivas como la Casa Abierta, la saturación de redes celulares y la cobertura WiFi intermitente en áreas exteriores del campus inutilizan las soluciones dependientes de la nube. Toda la cartografía, el motor de rutas, la base de datos de lugares y la lógica de evacuación residen en el almacenamiento y procesamiento local del dispositivo móvil.
2. **Cero Costos de Infraestructura y Licenciamiento ($0):**  
   Eliminación total de dependencias de APIs de pago como *Google Maps Platform (Directions API, Places API)* o *Mapbox SDK*. El sistema utiliza un grafo espacial propio ($G = (V, E)$), almacenamiento en SQLite/Room y renderizado nativo en Jetpack Compose Canvas.
3. **Nativo Android de Alto Rendimiento:**  
   Desarrollado en **Kotlin 2.0.0**, utilizando **Jetpack Compose** para la interfaz declarativa, **Coroutines y Flow** para el manejo reactivo y asíncrono, y **Android Architecture Components** bajo principios estrictos de Arquitectura Limpia (*Clean Architecture*).

---

## 2. El Desafío en el Campus Universitario ULEAM

El campus matriz de la ULEAM cuenta con decenas de facultades, bloques de aulas dispersos, áreas deportivas, auditorios, comedores y múltiples puertas de acceso peatonal y vehicular:
- **Nomenclatura Compleja y Desorientación:** Visitantes, postulantes, estudiantes de primeros semestres y docentes externos se desorientan frecuentemente al buscar bloques específicos (por ejemplo: Bloque B-01 de Aulas, Edificios Administrativos, Facultad de Ciencias de la Vida y Tecnologías - FCVT, Paraninfo, Coliseo Universitario).
- **Inclusión y Accesibilidad Universal:** El campus presenta desniveles topográficos, rampas y escaleras. El sistema debe permitir a personas con sillas de ruedas o movilidad reducida activar el filtro **"Evitar Escaleras"**, garantizando rutas accesibles mediante rampas y caminos llanos.
- **Seguridad y Evacuación Rápida:** Al ubicarse en la zona costera ecuatoriana, el campus cuenta con **Zonas Seguras / Puntos de Encuentro** oficialmente designados ante sismos o emergencias. La aplicación debe incorporar un sistema de evacuación de emergencia (*Safe Zone*) que calcule instantáneamente el trayecto más corto a la zona segura más próxima.

---

## 3. Desglose Exhaustivo de las 15 Diapositivas de la Presentación Oficial

La presentación ejecutiva del proyecto (*"Sistema Offline de Orientación y Navegación Peatonal Inteligente"* / Gemini Notebook) establece los fundamentos teóricos, metodológicos y organizacionales con los que fue concebido el software. A continuación se desglosa el contenido de cada una de las 15 diapositivas:

### Diapositiva 1: Portada y Declaración de Misión
- **Título:** SISTEMA OFFLINE DE ORIENTACIÓN Y NAVEGACIÓN PEATONAL INTELIGENTE.
- **Subtítulo:** Proyecto de Aplicación Android Nativa para el Campus Universitario.
- **Distintivos:** 100% Offline | Costo $0 | Kotlin 2.0.0.
- **Arte Gráfico:** Plano isométrico del campus universitario con un trazado de ruta vectorial en color institucional naranja conectando puertas, caminerías y bloques.

### Diapositiva 2: Superando las Limitaciones de la Cartografía Tradicional
Contrasta las alternativas comerciales estándar frente a los 4 pilares de Nav-ULEAM:
1. **100% Offline:** Operación independiente sin conectividad. Datos locales en la memoria del terminal.
2. **Nativo Android:** Aprovechamiento óptimo de CPU, memoria y hardware GNSS mediante Kotlin 2.0.0 y Jetpack Compose.
3. **Cero Costos:** Supresión de costos recurrentes por peticiones a proveedores privados.
4. **Enrutamiento Inteligente:** Representación formal del campus mediante una estructura matemática de grafo $G = (V, E)$, donde $V$ son nodos (puntos de decisión, intersecciones, puertas y destinos) y $E$ son aristas (caminerías peatonales reales).

### Diapositiva 3: El Motor Matemático — Algoritmo A* con Heurística Espacial
Explica la formulación del algoritmo $A^*$ aplicado a la topología del campus:
- **Función de Costo:**  
  $$f(n) = g(n) + h(n)$$
- **$g(n)$ (Costo Real Acumulado):** Suma ponderada de la distancia física recorrida sobre las caminerías habilitadas (aristas) desde el punto de origen hasta el nodo $n$.
- **$h(n)$ (Heurística Espacial de Haversine):** Estimación de distancia ortodrómica en línea recta calculada a partir de las coordenadas geográficas (latitud, longitud en grados WGS84) entre el nodo actual $n$ y el destino final.
- **Admisibilidad:** La distancia Haversine en línea recta jamás sobrestima la distancia real a pie ($h(n) \le d_{\text{real}}$), garantizando matemáticamente que $A^*$ encuentre la ruta óptima.
- **Snap-to-Node:** Algoritmo de acoplamiento que proyecta la coordenada cruda del GPS hacia el nodo de calle transitable más cercano en un radio máximo de 100 metros.

### Diapositiva 4: Un Ecosistema de Desarrollo Moderno y Escalable
Especificación formal del stack de herramientas y dependencias:
- **Motor de Compilación:** Android Studio (Koala / Ladybug) | Gradle 8.7 | Android Gradle Plugin (AGP) 8.5.0.
- **Lenguaje Base:** Kotlin 2.0.0 (Soporte desde Android 8.0 Oreo, `minSdk = 26` / `targetSdk = 34`).
- **Persistencia y Datos:** Room Database 2.6.1 (SQLite local nativo) y `kotlinx.serialization 1.6.3`.
- **Interfaz UI Reactiva:** Jetpack Compose (BOM 2024.09.00) y Navigation-Compose 2.8.0.
- **Sensores:** `play-services-location:21.3.0` (Fused Location Provider).
- **Pruebas:** JUnit 5.10.x (JUnit Jupiter Platform).
- **Estándar Cartográfico:** Formato vectorial unificado GeoJSON / CSV estructurado.

### Diapositiva 5: Arquitectura Técnica — Del Sensor de Hardware a la Pantalla
Diagrama de tubería de datos (*data pipeline*) en 4 fases sucesivas:
1. **Captura:** Señal GNSS/GPS nativa del dispositivo móvil + Geometría topográfica del campus.
2. **Almacenamiento:** Base de datos Room SQLite local, transformando coordenadas y tablas a un grafo estructurado $G = (V, E)$.
3. **Lógica:** Motor algorítmico $A^*$ ejecutando cálculo de ruta óptima y Dijkstra sobre las entidades locales.
4. **Visualización:** Motor de renderizado en Compose Canvas, superponiendo el mapa base institucional, la polilínea de la ruta calculada y el marcador vivo del usuario.

### Diapositiva 6: Orquestando a 27 Mentes en 6 Nodos de Trabajo
Muestra cómo la arquitectura modular del sistema organiza el trabajo colaborativo de **27 estudiantes** organizados en **6 grupos especializados**:
- **Grupo 1 (G1):** Captura SIG y Georreferenciación (5 desarrolladores).
- **Grupo 2 (G2):** Base de Datos Local y Grafo Room (5 desarrolladores).
- **Grupo 3 (G3):** Motor de IA y Algoritmo A* (5 desarrolladores).
- **Grupo 4 (G4):** Ubicación Nativa y Sensores GNSS (4 desarrolladores).
- **Grupo 5 (G5):** Renderizado Cartográfico y Canvas (4 desarrolladores).
- **Grupo 6 (G6):** UI/UX, ViewModel e Integración Final (4 desarrolladores).

### Diapositiva 7: Módulos Fundacionales — De la Topografía al Grafo Local
- **G1 (Captura SIG):**  
  - *Herramientas:* JOSM (Java OpenStreetMap Editor), QGIS 3.34 LTR, Google Earth Pro.  
  - *Misión:* Levantamiento topográfico peatonal en campo, digitalización de aceras, puertas, bloques y asignación de atributos (tipo de vía, escaleras, longitud métrica en WGS84).  
  - *Entregable:* Capas vectoriales GeoJSON/CSV (`nodos.csv`, `aristas.csv`, `lugares.csv`).
- **G2 (Base de Datos Local):**  
  - *Tecnología:* Room 2.6.1, SQLite, CsvSeeder.  
  - *Misión:* Parsear los archivos tabulares, poblar la base de datos local y proveer un `CampusRepository` y DAOs con queries normalizadas y búsqueda sin tildes.  
  - *Entregable:* Capa de persistencia desacoplada y validada.

### Diapositiva 8: Módulos Lógicos — Cálculo Algorítmico y Sensores
- **G3 (Motor de IA — Algoritmo A*):**  
  - *Tecnología:* Kotlin Standard Library puro (sin dependencias del framework Android ni Room) + JUnit 5.  
  - *Misión:* Implementación de `AStarEngine`, cálculo Haversine en `GeoMath`, `CampusGraph` inmutable y pruebas de admisibilidad.  
  - *Entregable:* Módulo aislado de caja negra: `findRoute(startId, goalId, avoidStairs)`.
- **G4 (Ubicación Nativa / Sensores):**  
  - *Tecnología:* `play-services-location:21.3.0`.  
  - *Misión:* Configurar `FusedLocationProviderClient`, gestionar permisos en tiempo de ejecución, filtrar saltos de señal y entregar actualizaciones fluidas de latitud y longitud.  
  - *Entregable:* Fuente de ubicación reactiva (`LocationSource`).

### Diapositiva 9: Módulos Visuales — Renderizado y Experiencia de Usuario
- **G5 (Renderizado Cartográfico):**  
  - *Tecnología:* Jetpack Compose Canvas, transformación matemática de proyecciones geográficas a coordenadas de pantalla (`MapProjection`).  
  - *Misión:* Dibujar polígonos de bloques, etiquetas de edificios con contraste, trazado de aristas, polilínea de ruta activa y el marcador animado del usuario (*Pulsing Blue Dot*).  
  - *Entregable:* Componente `CampusMap` declarativo, desacoplado y reactivo.
- **G6 (UI/UX e Integración Final):**  
  - *Tecnología:* Jetpack Compose Material 3, Navigation-Compose, Architecture Components (ViewModel, StateFlow).  
  - *Misión:* Integrar los módulos de G1-G5, panel de navegación flotante, conmutadores de configuración, evacuación a Zona Segura y paquete APK listo para la Casa Abierta.

### Diapositiva 10: Mapa de Dependencias Funcionales
Establece el flujo crítico de desbloqueo entre módulos:
- **Fase 1 (Día Cero):** G1 inicia captura cartográfica y G4 monta el servicio de ubicación GPS.
- **Fase 2 (Desbloqueo de Datos):** El mapa de G1 desbloquea la base de datos de G2 y la geometría visual de G5.
- **Fase 3 (Lógica):** G2 provee el grafo a G3 ($A^*$). G4 provee coordenadas al motor.
- **Fase 4 (Convergencia):** G6 unifica mapa (G5), rutas (G3), base de datos (G2) y GPS (G4) en la UI final.

### Diapositiva 11: Flujo de Trabajo Paralelo — Eliminando Tiempos Muertos
- **Estrategia de Mocks:** G3 y G5 no esperan a que G1 o G2 terminen la captura real. Mediante **Contratos de Datos Falsos (Mock Data)** y grafos sintéticos, se programa la lógica de búsqueda y la interfaz visual desde el primer día.
- **Integraciones Semanales:** G6 realiza integraciones incrementales continuas, impidiendo el "infierno de integración" en la recta final.

### Diapositiva 12: Control de Versiones y Reglas Estrictas de Integración
- **`main` (Protegida):** Rama de producción bloqueada; solo recibe código estable y verificado.
- **`develop` (Integración):** Rama central de integración donde G6 orquesta las uniones parciales.
- **`feature/grupoX-[modulo]`:** Ramas de trabajo aisladas de cada equipo (ej. `feature/g3-algoritmo-astar`).
- **Regla de Oro del Merge:** Prohibido el merge directo sin Pull Request. Cada PR requiere obligatoriamente la revisión y aprobación técnica de al menos un integrante de otro grupo.

### Diapositiva 13: Contratos de Datos — Columna Vertebral del Desarrollo
Define las 3 interfaces estables del sistema:
1. **Esquema de Datos (G1 $\rightarrow$ G2/G5):** Convención inmutable de atributos (`id_nodo`, `lat`, `lon`, `tipo`, `tiene_escaleras`, `longitud_metros`).
2. **Interfaz de Grafo (`GrafoRepository` / `CampusDao`) (G2 $\rightarrow$ G3):** Métodos predecibles para consultar nodos, aristas y vecinos sin acoplar SQL al algoritmo.
3. **Caja Negra de Enrutamiento (`AStarEngine`) (G3 $\rightarrow$ G5/G6):** Función pura:  
   `findRoute(originId, destinationId, options) -> RoutingResult`.

### Diapositiva 14: Diagnóstico de Cuellos de Botella y Matriz de Mitigación
| Riesgo Identificado | Grupo | Impacto | Estrategia de Mitigación |
| :--- | :--- | :--- | :--- |
| **Retraso en captura completa** | G1 | Bloqueo de G2 y G5 | Entregas por fases (un bloque inicial en Semana 1 para validar la tubería completa). |
| **Incompatibilidad de renderizado** | G5 | Fallo de despliegue en Canvas | Decisión arquitectónica temprana por renderizado nativo sobre Canvas paramétrico 100% offline. |
| **Desalineación GNSS con el mapa** | G4 + G5 | El punto del usuario cae fuera de los caminos | Calibración física en terreno (G1+G4) para sincronizar coordenadas WGS84 del GPS con los nodos cartografiados. |
| **Caos de integración final** | G6 | Bugs inesperados en la demo | Integración continua con *checkpoints* semanales y tests unitarios automatizados en Gradle. |

### Diapositiva 15: Destino Final — Lanzamiento Impecable en Casa Abierta
- **Visión de Éxito:** 27 estudiantes, 6 módulos técnicos, 1 sola aplicación sólida.
- Un sistema real de navegación peatonal inteligente, sin consumo de saldo ni dependencias externas, listo para guiar a la comunidad universitaria en la Casa Abierta.

---

## 4. Guía para Instruir a una IA con este Contexto

Si utilizas este documento como prompt de sistema o contexto para cualquier modelo de lenguaje (Claude, ChatGPT, Gemini, DeepSeek):
1. **Indícale a la IA:** *"Estás trabajando en el proyecto Nav-ULEAM Campus descrito en este documento. Respeta la filosofía 100% Offline, costo $0, arquitectura modular y las restricciones de accesibilidad y evacuación institucional."*
2. **Prioriza siempre:** Soluciones locales en memoria, código Kotlin nativo sin SDKs externos que requieran llaves de API o conexión a internet, y pruebas unitarias que garanticen la fiabilidad del sistema en la Casa Abierta.
