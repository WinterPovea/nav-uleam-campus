# ULEAM Campus Navigator (Nav-ULEAM Campus)

Sistema Offline de Orientación y Navegación Peatonal Inteligente para el Campus Matriz de la **Universidad Laica Eloy Alfaro de Manabí (ULEAM)** en Manta, Ecuador.

**100% Offline | Costo $0 | Kotlin 2.0.0 | Jetpack Compose | Room SQLite | Algoritmo A***

---

## Documentación y Contexto para Desarrolladores e Inteligencias Artificiales

Para optimizar el uso de tokens y brindar contexto exhaustivo e inmediato a cualquier miembro del equipo o modelo de IA (Claude, ChatGPT, Gemini, DeepSeek), consulta los documentos especializados en el directorio [`docs/`](file:///c:/Users/HOME/Desktop/Anthony/Proyectos/nav-uleam-campus/docs/):

1. 📘 [**`01_CONTEXTO_PROYECTO_ULEAM.md`**](file:///c:/Users/HOME/Desktop/Anthony/Proyectos/nav-uleam-campus/docs/01_CONTEXTO_PROYECTO_ULEAM.md):  
   Contexto institucional ULEAM, objetivos para la Casa Abierta, filosofía 100% Offline y costo $0, y desglose minucioso de las 15 diapositivas de la presentación oficial del proyecto.
2. ⚙️ [**`02_ESTADO_ACTUAL_TECNICO.md`**](file:///c:/Users/HOME/Desktop/Anthony/Proyectos/nav-uleam-campus/docs/02_ESTADO_ACTUAL_TECNICO.md):  
   Arquitectura técnica detallada, librerías, dependencias (`minSdk = 26`, Compose BOM, Room 2.6.1, Play Services Location), estructura de archivos, motor A* con Safe Zone, GPS con optimización de batería (`WhileSubscribed(5000)`), Canvas institucional y Turn-by-Turn.
3. 🚀 [**`03_ROADMAP_Y_DISTRIBUCION_EQUIPO.md`**](file:///c:/Users/HOME/Desktop/Anthony/Proyectos/nav-uleam-campus/docs/03_ROADMAP_Y_DISTRIBUCION_EQUIPO.md):  
   Visión de ingeniería senior en geolocalización, diseño móvil y full-stack: análisis de brechas, innovaciones propuestas (brújula, recálculo dinámico de desvíos, asistente de voz TTS, háptica, navegación por pisos) y distribución de 12 tareas atómicas con ramas Git, contratos y commits para el equipo.

---

## Cómo Ejecutar el Proyecto
1. Abrir la carpeta raíz del repositorio en **Android Studio (Koala, Ladybug o más reciente)**.
2. Asegurarse de tener configurado **JDK 17** (incluido en Android Studio JBR).
3. Sincronizar Gradle (`File > Sync Project with Gradle Files`).
4. Compilar y ejecutar (`Run 'app'`) en un emulador o dispositivo físico con Android 8.0+ (`minSdk = 26`).

## Verificación de Calidad
- **Pruebas unitarias:** `./gradlew test` (ejecuta la suite de pruebas de A*, Dijkstra, GeoMath y Turn-by-Turn).
- **Compilación de APK Debug:** `./gradlew assembleDebug`.
