import os
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls

def set_cell_background(cell, fill_hex):
    tcPr = cell._tc.get_or_add_tcPr()
    tcPr.append(parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>'))

def set_cell_margins(cell, top=100, bottom=100, left=150, right=150):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = parse_xml(f'<w:tcMar {nsdecls("w")}><w:top w:w="{top}" w:type="dxa"/><w:bottom w:w="{bottom}" w:type="dxa"/><w:left w:w="{left}" w:type="dxa"/><w:right w:w="{right}" w:type="dxa"/></w:tcMar>')
    tcPr.append(tcMar)

def add_title_block(doc, title_text, subtitle_text):
    p_title = doc.add_paragraph()
    p_title.paragraph_format.space_before = Pt(0)
    p_title.paragraph_format.space_after = Pt(2)
    run_title = p_title.add_run(title_text)
    run_title.font.name = 'Calibri'
    run_title.font.size = Pt(20)
    run_title.font.bold = True
    run_title.font.color.rgb = RGBColor(0x1A, 0x36, 0x5D)
    
    p_sub = doc.add_paragraph()
    p_sub.paragraph_format.space_before = Pt(0)
    p_sub.paragraph_format.space_after = Pt(14)
    run_sub = p_sub.add_run(subtitle_text)
    run_sub.font.name = 'Calibri'
    run_sub.font.size = Pt(11.5)
    run_sub.font.italic = True
    run_sub.font.color.rgb = RGBColor(0x55, 0x55, 0x55)

def add_styled_heading(doc, text, level):
    h = doc.add_heading(text, level=level)
    h.paragraph_format.keep_with_next = True
    run = h.runs[0]
    run.font.name = 'Calibri'
    if level == 1:
        run.font.size = Pt(15)
        run.font.bold = True
        run.font.color.rgb = RGBColor(0x1A, 0x36, 0x5D)
        h.paragraph_format.space_before = Pt(14)
        h.paragraph_format.space_after = Pt(4)
    elif level == 2:
        run.font.size = Pt(13)
        run.font.bold = True
        run.font.color.rgb = RGBColor(0x2C, 0x3E, 0x50)
        h.paragraph_format.space_before = Pt(12)
        h.paragraph_format.space_after = Pt(3)
    elif level == 3:
        run.font.size = Pt(11)
        run.font.bold = True
        run.font.color.rgb = RGBColor(0x34, 0x49, 0x5E)
        h.paragraph_format.space_before = Pt(9)
        h.paragraph_format.space_after = Pt(2)
    return h

def add_body_paragraph(doc, text, bold_prefix=None):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(5)
    p.paragraph_format.line_spacing = 1.15
    if bold_prefix:
        r_pre = p.add_run(bold_prefix)
        r_pre.font.name = 'Calibri'
        r_pre.font.size = Pt(10.5)
        r_pre.font.bold = True
        r_pre.font.color.rgb = RGBColor(0x22, 0x22, 0x22)
    run = p.add_run(text)
    run.font.name = 'Calibri'
    run.font.size = Pt(10.5)
    run.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
    return p

def add_bullet_item(doc, text, bold_prefix=None):
    p = doc.add_paragraph(style='List Bullet')
    p.paragraph_format.space_before = Pt(1)
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.15
    if bold_prefix:
        r_pre = p.add_run(bold_prefix)
        r_pre.font.name = 'Calibri'
        r_pre.font.size = Pt(10.5)
        r_pre.font.bold = True
        r_pre.font.color.rgb = RGBColor(0x22, 0x22, 0x22)
    run = p.add_run(text)
    run.font.name = 'Calibri'
    run.font.size = Pt(10.5)
    run.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
    return p

def add_code_block(doc, code_text):
    tbl = doc.add_table(rows=1, cols=1)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl.autofit = False
    cell = tbl.cell(0, 0)
    cell.width = Inches(6.5)
    set_cell_background(cell, 'F4F6F7')
    set_cell_margins(cell, top=120, bottom=120, left=180, right=180)
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.15
    run = p.add_run(code_text)
    run.font.name = 'Consolas'
    run.font.size = Pt(8.5)
    run.font.color.rgb = RGBColor(0x2C, 0x3E, 0x50)
    p_spacer = doc.add_paragraph()
    p_spacer.paragraph_format.space_before = Pt(0)
    p_spacer.paragraph_format.space_after = Pt(3)

def add_table_data(doc, headers, rows):
    tbl = doc.add_table(rows=len(rows) + 1, cols=len(headers))
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl.autofit = True
    
    for col_idx, header in enumerate(headers):
        cell = tbl.cell(0, col_idx)
        set_cell_background(cell, '1A365D')
        set_cell_margins(cell, top=120, bottom=120, left=140, right=140)
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p.paragraph_format.space_before = Pt(2)
        p.paragraph_format.space_after = Pt(2)
        r = p.add_run(header)
        r.font.name = 'Calibri'
        r.font.bold = True
        r.font.size = Pt(9.5)
        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        
    for row_idx, row_data in enumerate(rows):
        bg = 'FFFFFF' if row_idx % 2 == 0 else 'F8F9FA'
        for col_idx, text in enumerate(row_data):
            cell = tbl.cell(row_idx + 1, col_idx)
            set_cell_background(cell, bg)
            set_cell_margins(cell, top=100, bottom=100, left=140, right=140)
            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.space_after = Pt(2)
            r = p.add_run(text)
            r.font.name = 'Calibri'
            r.font.size = Pt(9)
            r.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
            
    p_spacer = doc.add_paragraph()
    p_spacer.paragraph_format.space_before = Pt(0)
    p_spacer.paragraph_format.space_after = Pt(4)


# ==============================================================================
# DOCUMENTO 1: GRUPO 4 - UBICACIÓN Y SENSORES
# ==============================================================================
def build_doc_grupo_4(output_path):
    doc = docx.Document()
    for s in doc.sections:
        s.top_margin = Inches(1)
        s.bottom_margin = Inches(1)
        s.left_margin = Inches(1)
        s.right_margin = Inches(1)
        
    add_title_block(doc, "Módulo de Ubicación Nativa, Sensores GNSS y Eficiencia Energética",
                    "Documentación Técnica del Grupo 4 — Sistema Offline de Navegación Peatonal Campus ULEAM")
    
    add_styled_heading(doc, "1. Resumen Ejecutivo", 1)
    add_body_paragraph(doc, "El Grupo 4 tiene la responsabilidad crítica de capturar, procesar y entregar la posición geográfica del usuario en tiempo real dentro del campus de la ULEAM. Para cumplir con la filosofía del proyecto (100% offline, costo $0 y máxima eficiencia energética), el módulo encapsula el hardware satelital (GPS, GLONASS, Galileo) mediante la API moderna FusedLocationProviderClient de Google Play Services.")
    add_body_paragraph(doc, "El módulo desacopla la fuente de ubicación física a través de una interfaz reactiva (LocationSource) y un modelo sellado de estados (LocationState). Además, introduce un mecanismo de apagado automático de sensores coordinado con el ciclo de vida de la aplicación y provee una implementación simulada (FakeLocationSource) para permitir pruebas sin señal satelital en emuladores o durante demostraciones en interiores.")

    add_styled_heading(doc, "2. Qué se agregó al proyecto", 1)
    add_styled_heading(doc, "Código del Módulo", 2)
    add_body_paragraph(doc, "Ruta base: app/src/main/java/ec/edu/uleam/navigator/location/")
    
    headers_archivos = ["Archivo", "Ubicación", "Función Técnica"]
    rows_archivos = [
        ["LocationSource.kt", "location/", "Define la interfaz reactiva LocationSource, la sealed interface LocationState con sus cuatro estados, la implementación real FusedLocationSource con callbackFlow y la implementación de prueba FakeLocationSource."],
        ["AndroidManifest.xml", "app/src/main/", "Declaración de permisos nativos ACCESS_FINE_LOCATION y ACCESS_COARSE_LOCATION para el acceso a hardware satelital."],
        ["build.gradle.kts", "app/", "Incorporación de la dependencia com.google.android.gms:play-services-location:21.3.0."]
    ]
    add_table_data(doc, headers_archivos, rows_archivos)
    
    add_styled_heading(doc, "Configuración en build.gradle.kts", 2)
    add_code_block(doc, """dependencies {
    // Servicios de ubicación optimizados
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
}""")

    add_styled_heading(doc, "3. Cómo funciona el módulo", 1)
    add_styled_heading(doc, "Modelo Sellado de Estados (LocationState)", 2)
    add_body_paragraph(doc, "En lugar de emitir objetos crudos de tipo android.location.Location con campos nulos o excepciones no controladas, el módulo modela la ubicación como un flujo de estados determinísticos:")
    
    headers_estados = ["Estado", "Descripción Técnica", "Acción Derivada en la UI"]
    rows_estados = [
        ["LocationState.Disabled", "El usuario tiene apagado el chip de ubicación en los ajustes del dispositivo.", "Muestra advertencia sugiriendo encender el GPS o pasar al Modo Simulación."],
        ["LocationState.PermissionDenied", "El usuario rechazó los permisos de ubicación en tiempo de ejecución.", "Despliega el banner de solicitud con botón de reintento o apertura de Ajustes."],
        ["LocationState.Locating", "Permisos concedidos; el sensor GNSS está sincronizando satélites (TTFF).", "Muestra indicador de carga o conserva el último punto conocido."],
        ["LocationState.Available(lat, lon, accuracy)", "Fijación satelital exitosa. Contiene coordenadas WGS84 y radio de precisión en metros.", "Actualiza el Pulsing Blue Dot en el mapa y ejecuta el snap-to-node del motor A*."]
    ]
    add_table_data(doc, headers_estados, rows_estados)

    add_styled_heading(doc, "Implementación con Corrutinas (callbackFlow)", 2)
    add_body_paragraph(doc, "FusedLocationSource transforma las llamadas asíncronas tradicionales por callback de Android en un Kotlin Flow frío y reactivo. La función awaitClose garantiza la remoción inmediata del callback en el hardware tan pronto el flujo deja de tener recolectores activos:")
    add_code_block(doc, """class FusedLocationSource(private val context: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    override fun locationUpdates(intervalMs: Long): Flow<LocationState> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(1.0f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    trySend(LocationState.Available(loc.latitude, loc.longitude, loc.accuracy))
                }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}""")

    add_styled_heading(doc, "Protección Contra Drenaje de Batería (Lifecycle Safety)", 2)
    add_body_paragraph(doc, "Uno de los problemas más graves en apps de geolocalización es mantener el GPS activo mientras la aplicación está minimizada. En Nav-ULEAM Campus, el ViewModel conecta el flujo de ubicación mediante WhileSubscribed(5000):")
    add_bullet_item(doc, "SharingStarted.WhileSubscribed(5000): Mantiene activa la suscripción mientras al menos un componente Compose esté visible en pantalla.", "Estrategia: ")
    add_bullet_item(doc, "Si el usuario cambia de app o bloquea el teléfono, transcurren 5 segundos de gracia y la suscripción se cancela automáticamente, ordenando al hardware satelital apagarse por completo.", "Comportamiento: ")

    add_styled_heading(doc, "4. Guía de uso y contratos con otros grupos", 1)
    add_body_paragraph(doc, "El Grupo 4 entrega un contrato desacoplado que alimenta directamente a los grupos 3, 5 y 6:")
    add_bullet_item(doc, "findRouteFromLocation(lat, lon, destId) utiliza directamente la latitud y longitud emitidas por LocationState.Available para ejecutar el algoritmo de snap-to-node hacia la arista transitable más cercana en un radio de 100 m.", "Conexión con G3 (Motor A*): ")
    add_bullet_item(doc, "CampusMap recibe el LocationState. Si es Available, dibuja el Pulsing Blue Dot y traza el círculo de incertidumbre con el radio exacto entregado por loc.accuracy.", "Conexión con G5 (Canvas Map): ")
    add_bullet_item(doc, "CampusViewModel orquesta el conmutador de simulación. Si el usuario activa el switch de Modo Demo, la suscripción física se ignora y el origen pasa a ser el punto marcado manualmente por el usuario.", "Conexión con G6 (UI/ViewModel): ")

    add_styled_heading(doc, "5. Pruebas y validaciones", 1)
    add_body_paragraph(doc, "Se verificó el comportamiento del módulo mediante:")
    add_bullet_item(doc, "Simulación de recorridos continuos dentro del campus sin necesidad de hardware GNSS, validando la estabilidad de los observadores Compose.", "Pruebas con FakeLocationSource: ")
    add_bullet_item(doc, "Inyección de rutas GPX con la herramienta Extended Controls de Android Studio sobre el campus ULEAM Manta (-0.953, -80.747), confirmando precisión de 3 a 5 metros.", "Pruebas en Emulador con Coordenadas Reales: ")
    add_bullet_item(doc, "Inspección de procesos en background con Android Profiler, constatando consumo de energía cero al minimizar la actividad.", "Pruebas de Ciclo de Vida: ")

    add_styled_heading(doc, "6. Pendientes y recomendaciones para siguientes versiones", 1)
    add_bullet_item(doc, "Integrar SensorManager con el vector de rotación para entregar no solo posición (lat, lon) sino también rumbo azimutal (heading) para el cono de visión.", "Sensor de Brújula (Compass): ")
    add_bullet_item(doc, "Implementar un filtro paso bajo o filtro de Kalman ligero para descartar saltos bruscos provocados por rebote de señal en muros altos de facultades.", "Filtro de Ruido GNSS: ")

    doc.save(output_path)
    print(f"Generado exitosamente: {output_path}")


# ==============================================================================
# DOCUMENTO 2: GRUPO 5 - RENDERIZADO CARTOGRÁFICO
# ==============================================================================
def build_doc_grupo_5(output_path):
    doc = docx.Document()
    for s in doc.sections:
        s.top_margin = Inches(1)
        s.bottom_margin = Inches(1)
        s.left_margin = Inches(1)
        s.right_margin = Inches(1)
        
    add_title_block(doc, "Módulo de Renderizado Cartográfico y Canvas en Jetpack Compose",
                    "Documentación Técnica del Grupo 5 — Sistema Offline de Navegación Peatonal Campus ULEAM")
    
    add_styled_heading(doc, "1. Resumen Ejecutivo", 1)
    add_body_paragraph(doc, "El Grupo 5 es responsable de la representación visual del campus universitario en la pantalla del dispositivo móvil. Para garantizar una solución 100% offline, con costo cero de licenciamiento y sin dependencia de SDKs de terceros pesados (como Mapbox o Google Maps), el equipo implementó un motor de dibujo vectorial completo sobre el componente Canvas de Jetpack Compose.")
    add_body_paragraph(doc, "El módulo resuelve la proyección matemática bidimensional de coordenadas geográficas WGS84, soporta gestos táctiles de zoom y paneo continuo, renderiza la infraestructura por categorías con la paleta de colores institucional de la ULEAM, dibuja etiquetas tipográficas de alto contraste legibles bajo la luz solar, traza la polilínea óptima de la ruta calculada por A* y anima el marcador de posición del usuario mediante un Pulsing Blue Dot con radio de incertidumbre métrica.")

    add_styled_heading(doc, "2. Qué se agregó al proyecto", 1)
    add_styled_heading(doc, "Código del Módulo", 2)
    add_body_paragraph(doc, "Ruta base: app/src/main/java/ec/edu/uleam/navigator/ui/map/")
    
    headers_archivos = ["Archivo", "Componente", "Función Técnica"]
    rows_archivos = [
        ["CampusMap.kt", "Composable CampusMap", "Lienzo Canvas interactivo principal. Maneja transformaciones de gestos (pointerInput), dibuja caminerías, polígonos de bloques, etiquetas nativas, ruta calculada y marcador de usuario."],
        ["CampusMap.kt", "Clase MapProjection", "Calcula el bounding box del campus y traduce pares (latitud, longitud) a píxeles (X, Y) considerando la escala y el centro de la vista."],
        ["CampusMap.kt", "Clase UserPosition", "Estructura de datos que encapsula la posición relativa proyectada y la precisión métrica en píxeles del usuario."]
    ]
    add_table_data(doc, headers_archivos, rows_archivos)

    add_styled_heading(doc, "3. Cómo funciona el módulo", 1)
    add_styled_heading(doc, "Proyección Cartográfica Paramétrica (MapProjection)", 2)
    add_body_paragraph(doc, "Dado que el campus de la ULEAM abarca un área de aproximadamente 500x500 metros, la curvatura de la Tierra a esta escala es despreciable. MapProjection utiliza una proyección equirectangular centrada localmente, normalizando las coordenadas en un plano euclidiano relativas al centro geométrico del campus:")
    add_code_block(doc, """// Factor de corrección de longitud por latitud local (-0.953°)
val latRad = Math.toRadians(centerLat)
val cosLat = Math.cos(latRad)

// Proyección de coordenada geográfica a píxel en pantalla
val x = screenWidth / 2f + (lon - centerLon) * scale * cosLat + panOffsetX
val y = screenHeight / 2f - (lat - centerLat) * scale + panOffsetY""")

    add_styled_heading(doc, "Gestos Multitáctiles Fluidos", 2)
    add_body_paragraph(doc, "El lienzo intercepta eventos de puntero mediante Modifier.pointerInput con detectTransformGestures, permitiendo pellizcar para hacer zoom (con límites acotados entre 0.5x y 5.0x) y arrastrar para desplazar la cámara cartográfica por todo el campus universitario.")

    add_styled_heading(doc, "Paleta de Colores Institucional por Categoría", 2)
    add_body_paragraph(doc, "Los bloques y áreas del campus se diferencian cromáticamente para facilitar la orientación visual inmediata de los evaluadores y visitantes:")
    
    headers_colores = ["Categoría de Lugar", "Color Institucional", "Código Hex", "Ejemplos en el Campus"]
    rows_colores = [
        ["BLOQUE / AULAS", "Azul Pizarra", "#2C3E50", "Bloques de aulas B-01 a B-12, Laboratorios."],
        ["ADMINISTRATIVO", "Azul Profundo", "#1A365D", "Rectorado, Vicerrectorado, Decanatos."],
        ["COMIDA", "Naranja Cálido", "#D35400", "Comedor Universitario, Cafeterías."],
        ["DEPORTE", "Verde Bosque", "#27AE60", "Estadio Universitario, Coliseo, Canchas."],
        ["CULTURA", "Púrpura", "#8E44AD", "Paraninfo Alfonso Aguilar Ruilova, Museo."],
        ["ZONA_SEGURA", "Verde Esmeralda", "#2ECC71", "Canchas abiertas, explanadas de evacuación."],
        ["PUERTA / ACCESO", "Rojo Ladrillo", "#C0392B", "Puerta 1 (Principal), Puerta 2, 3 y 4."]
    ]
    add_table_data(doc, headers_colores, rows_colores)

    add_styled_heading(doc, "Etiquetas Tipográficas de Alto Contraste", 2)
    add_body_paragraph(doc, "Para evitar el problema común de ilegibilidad de texto en pantallas móviles bajo el sol de Manta, se integró android.graphics.Paint nativo a través del método drawIntoCanvas:")
    add_bullet_item(doc, "Se calcula el ancho y alto del texto del edificio.", "Cálculo de Bounds: ")
    add_bullet_item(doc, "Se dibuja un rectángulo redondeado de fondo (píldora blanca semitransparente con borde oscuro).", "Píldora de Contraste: ")
    add_bullet_item(doc, "Se imprime la tipografía en color oscuro sobre la píldora, asegurando contraste 100% legible sin importar los elementos subyacentes.", "Tipografía Nítida: ")

    add_styled_heading(doc, "Marcador Vivo: Pulsing Blue Dot con Radio de Incertidumbre", 2)
    add_body_paragraph(doc, "El marcador del usuario sustituye el ícono estático por una experiencia visual moderna inspirada en estándares cartográficos internacionales:")
    add_bullet_item(doc, "Una animación infinita (rememberInfiniteTransition) expande una onda translúcida desde 12dp hasta 30dp de radio con desvanecimiento de opacidad (alpha).", "Efecto Pulsante: ")
    add_bullet_item(doc, "Un círculo de color azul con 10% de opacidad se proyecta con el radio métrico exacto entregado por la precisión del GPS (accuracy), permitiendo al usuario conocer visualmente el margen de error satelital.", "Círculo de Incertidumbre: ")
    add_bullet_item(doc, "Un punto central azul intenso (#1976D2) con borde blanco puro de 2dp fija el centro exacto.", "Núcleo Sólido: ")

    add_styled_heading(doc, "Trazado de Trayectoria Óptima A*", 2)
    add_body_paragraph(doc, "La polilínea de la ruta calculada por G3 se dibuja con una técnica de casing doble: primero un trazo base más ancho de color oscuro (borde de contraste de 7dp) y encima la línea principal de color verde azulado brillante (5dp), evitando que la ruta se pierda al cruzar caminerías de colores similares.")

    add_styled_heading(doc, "4. Guía de uso y contratos con otros grupos", 1)
    add_bullet_item(doc, "CampusMap consume directamente las listas de GraphNode y GraphEdge construidas por el mapper de G2.", "Entrada desde G1 y G2: ")
    add_bullet_item(doc, "CampusMap recibe routeNodes: List<GraphNode>. Si la lista no está vacía, itera sobre los nodos consecutivos y proyecta los segmentos de la ruta.", "Entrada desde G3 (Motor A*): ")
    add_bullet_item(doc, "Recibe el estado LocationState. Si es Available, proyecta las coordenadas WGS84 al punto del usuario.", "Entrada desde G4 (GPS): ")
    add_bullet_item(doc, "CampusMap expone la lambda onMapTap: (lat: Double, lon: Double) -> Unit. En Modo Demo, cuando el usuario toca la pantalla, se calcula la coordenada geográfica inversa y se envía a G6 para fijar el origen manual.", "Salida hacia G6 (UI): ")

    add_styled_heading(doc, "5. Rendimiento y optimizaciones", 1)
    add_body_paragraph(doc, "El componente fue optimizado para sostener tasas de refresco de 60 FPS a 120 FPS en dispositivos gama media:")
    add_bullet_item(doc, "El objeto MapProjection y los cálculos de coordenadas se memorizan (remember) y solo se recalculan cuando cambian las dimensiones del lienzo o la escala.", "Cero Asignaciones en Dibujo: ")
    add_bullet_item(doc, "Solo los elementos dentro del viewport proyectado con un margen de seguridad de 50px son procesados por el pipeline de dibujo.", "Culling de Elementos Fuera de Pantalla: ")

    add_styled_heading(doc, "6. Pendientes y recomendaciones (Roadmap G5)", 1)
    add_bullet_item(doc, "Agregar soporte para dibujar el cono de visión rotando con la brújula peatonal.", "Cono Azimutal: ")
    add_bullet_item(doc, "Incorporar renderizado de polígonos complejos con esquinas reales de edificios a partir del archivo vectorial de G1.", "Huella Física Completa: ")

    doc.save(output_path)
    print(f"Generado exitosamente: {output_path}")


# ==============================================================================
# DOCUMENTO 3: GRUPO 6 - UI/UX, INTEGRACIÓN Y REFACTOR
# ==============================================================================
def build_doc_grupo_6(output_path):
    doc = docx.Document()
    for s in doc.sections:
        s.top_margin = Inches(1)
        s.bottom_margin = Inches(1)
        s.left_margin = Inches(1)
        s.right_margin = Inches(1)
        
    add_title_block(doc, "Módulo de UI/UX, Integración de Módulos y Refactorización Final",
                    "Documentación Técnica del Grupo 6 — Sistema Offline de Navegación Peatonal Campus ULEAM")
    
    add_styled_heading(doc, "1. Resumen Ejecutivo", 1)
    add_body_paragraph(doc, "El Grupo 6 desempeña el rol integrador y articulador del proyecto Nav-ULEAM Campus. Su misión consistió en ensamblar los datos de Room (G2), el motor A* (G3), la geolocalización nativa (G4) y el renderizado cartográfico Canvas (G5) en una aplicación Android fluida, accesible, intuitiva y visualmente atractiva para los evaluadores en la Casa Abierta.")
    add_body_paragraph(doc, "Asimismo, el Grupo 6 lideró la refactorización crítica final del repositorio, eliminando el código muerto acoplado (RoutingEngine.kt), integrando la evacuación rápida por Dijkstra a Zonas Seguras dentro de AStarEngine, implementando la tarjeta flotante colapsable, diseñando el generador de instrucciones Turn-by-Turn, solucionando el conflicto de módulos no-Gradle en Android Studio y asegurando una compilación 100% exitosa con pruebas unitarias verdes.")

    add_styled_heading(doc, "2. Qué se agregó y refactorizó en el proyecto", 1)
    add_styled_heading(doc, "Archivos Nuevos y Modificados", 2)
    add_body_paragraph(doc, "Ruta base: app/src/main/java/ec/edu/uleam/navigator/")
    
    headers_archivos = ["Archivo", "Acción Realizada", "Impacto en la Arquitectura"]
    rows_archivos = [
        ["ui/MainActivity.kt", "Modificado", "Implementación de tarjeta flotante colapsable, conmutador de simulación, switch de escaleras, botón rojo de evacuación, lista Turn-by-Turn y flujo resiliente de permisos."],
        ["ui/CampusViewModel.kt", "Modificado", "Orquestación central del UiState, recolección con WhileSubscribed(5000), conmutador isSimulationMode, búsqueda sin tildes y cálculo de rutas."],
        ["routing/astar/NavigationGuidance.kt", "Creado", "Motor de instrucciones paso a paso que traduce nodos a giros textuales comprensibles (avanza, gira a la derecha, usa escaleras, llega)."],
        ["routing/astar/GeoMath.kt", "Modificado", "Incorporación de funciones bearing() (rumbo) y turnAngle() (ángulo de giro) para sustentar el Turn-by-Turn."],
        ["routing/astar/AStarEngine.kt", "Modificado", "Migración de la lógica de búsqueda de Zonas Seguras por Dijkstra (findNearestSafeZone), excluyendo nodos tipo DESTINO como atajos de emergencia."],
        ["data/CampusRepository.kt", "Refactorizado", "Desacoplamiento total: eliminación de referencias al prototipo y consumo exclusivo de CampusGraph y AStarEngine."],
        ["routing/RoutingEngine.kt", "ELIMINADO", "Deprecación y erradicación definitiva del archivo antiguo acoplado del prototipo."],
        ["build.gradle.kts", "Modificado", "Actualización de minSdk = 26, dependencias de Play Services, Lifecycle Compose y Material Icons Extended."],
        [".idea/gradle.xml / modules.xml", "Saneado", "Eliminación de módulos Java no-Gradle residuales que bloqueaban la compilación en Android Studio."]
    ]
    add_table_data(doc, headers_archivos, rows_archivos)

    add_styled_heading(doc, "3. Cómo funciona el módulo", 1)
    add_styled_heading(doc, "Tarjeta Flotante Colapsable (CollapsibleNavigationCard)", 2)
    add_body_paragraph(doc, "En lugar de paneles estáticos rígidos que tapaban la visibilidad del mapa, se diseñó un contenedor flotante ergonómico basado en Material 3:")
    add_bullet_item(doc, "Distancia métrica total formateada (ej. '245 m') y tiempo estimado de caminata en minutos (ej. '3 min') calculados dinámicamente según la longitud de aristas.", "Cabecera de Métricas: ")
    add_bullet_item(doc, "Permite contraer el panel para dejar ver el 90% del mapa o expandirlo para consultar las instrucciones detalladas.", "Control de Expansión: ")
    add_bullet_item(doc, "Interruptor accesible que recalcula instantáneamente la ruta descartando tramos con gradas.", "Switch 'Evitar Escaleras': ")
    add_bullet_item(doc, "Conmutador vital para demostraciones en interiores o carpas donde no hay señal satelital, permitiendo marcar el origen manualmente.", "Switch 'Modo Simulación': ")
    add_bullet_item(doc, "Botón rojo de alta prominencia que desencadena la ruta de evacuación inmediata al punto de encuentro seguro más cercano.", "Botón de Evacuación 'Zona Segura': ")

    add_styled_heading(doc, "Motor de Instrucciones Turn-by-Turn (NavigationGuidance)", 2)
    add_body_paragraph(doc, "El componente convierte la secuencia geométrica de nodos en instrucciones de navegación humana asistida, calculando la deflexión angular entre segmentos sucesivos:")
    add_code_block(doc, """val bearing1 = GeoMath.bearing(pPrev, pCurr)
val bearing2 = GeoMath.bearing(pCurr, pNext)
val delta = GeoMath.turnAngle(bearing1, bearing2)

val type = when {
    edge.hasStairs -> DirectionType.STAIRS
    delta in -35.0..35.0 -> DirectionType.STRAIGHT
    delta > 35.0 -> DirectionType.TURN_RIGHT
    else -> DirectionType.TURN_LEFT
}""")
    add_body_paragraph(doc, "Además, implementa compresión de tramos rectos: si el usuario debe avanzar por 3 nodos consecutivos sin girar, el generador unifica la instrucción en un solo mensaje ('Avanza 75 m recto') para no saturar al usuario.")

    add_styled_heading(doc, "Manejo Resiliente de Permisos de Ubicación", 2)
    add_body_paragraph(doc, "Utilizando ActivityResultContracts.RequestMultiplePermissions con rememberLauncherForActivityResult:")
    add_bullet_item(doc, "Si el usuario deniega los permisos la primera vez, se muestra un banner explicativo con botón de 'Reintentar'.", "Flujo Amigable: ")
    add_bullet_item(doc, "Si el usuario marca 'No volver a preguntar' (denegación permanente), el sistema lo detecta y muestra el botón 'Abrir Ajustes', el cual lanza un Intent directo hacia Settings.ACTION_APPLICATION_DETAILS_SETTINGS.", "Recuperación de Denegación Permanente: ")

    add_styled_heading(doc, "Modo Demostración / Simulación (Event Resilience)", 2)
    add_body_paragraph(doc, "Durante la Casa Abierta, los stands suelen ubicarse bajo carpas o estructuras metálicas que bloquean la señal GNSS. El modo simulación garantiza el éxito de la demo:")
    add_bullet_item(doc, "Al activarse el conmutador, se silencia la lectura del GPS físico.", "Aislamiento de Hardware: ")
    add_bullet_item(doc, "El usuario puede presionar cualquiera de los botones de puertas rápidas (Puerta 1, 2, 3 o 4) o simplemente tocar cualquier punto del mapa.", "Selección Táctil de Origen: ")
    add_bullet_item(doc, "La ruta hacia el destino se genera de forma inmediata e idéntica a si estuviera caminando en el terreno.", "Cálculo Instantáneo: ")

    add_styled_heading(doc, "Evacuación Inmediata a Zona Segura (Safe Zone)", 2)
    add_body_paragraph(doc, "En cumplimiento con los protocolos de seguridad universitaria ante sismos:")
    add_bullet_item(doc, "AStarEngine ejecuta un Dijkstra multidestino buscando el nodo ZONA_SEGURA de menor costo acumulado.", "Dijkstra Multidestino: ")
    add_bullet_item(doc, "El algoritmo ignora nodos tipo DESTINO como pasos intermedios, impidiendo que una ruta de evacuación obligue al usuario a entrar o atravesar un edificio para salir.", "Regla de Seguridad: ")

    add_styled_heading(doc, "4. Integración y Pruebas Unitarias", 1)
    add_body_paragraph(doc, "Para blindar el módulo de integración se crearon y ejecutaron suites de pruebas automatizadas:")
    add_bullet_item(doc, "Valida giros a la derecha, izquierda, escaleras y compresión de tramos.", "NavigationGuidanceTest.kt (8 pruebas): ")
    add_bullet_item(doc, "Valida que todas las puertas alcancen todas las facultades en el grafo real de 135 nodos.", "RealCampusDataTest.kt (29 pruebas): ")
    add_bullet_item(doc, "Confirmación de cero errores de compilación y empaquetado de APK listo para distribución.", "Gradle assembleDebug: BUILD SUCCESSFUL: ")

    add_styled_heading(doc, "5. Conclusiones y Próximos Pasos (Roadmap G6)", 1)
    add_bullet_item(doc, "Conectar las instrucciones de NavigationGuidance con el motor TextToSpeech en español.", "Asistente de Voz: ")
    add_bullet_item(doc, "Incorporar chips horizontales para filtrar por Aulas, Cafeterías, Baños o Zonas Seguras.", "Búsqueda por Categorías: ")

    doc.save(output_path)
    print(f"Generado exitosamente: {output_path}")

if __name__ == '__main__':
    out_dir = r'C:\Users\HOME\Desktop\Anthony\Proyectos\nav-uleam-campus\docs'
    os.makedirs(out_dir, exist_ok=True)
    build_doc_grupo_4(os.path.join(out_dir, 'Documentacion_Grupo_4_Ubicacion_y_Sensores.docx'))
    build_doc_grupo_5(os.path.join(out_dir, 'Documentacion_Grupo_5_Renderizado_Cartografico_Canvas.docx'))
    build_doc_grupo_6(os.path.join(out_dir, 'Documentacion_Grupo_6_UI_Integracion_y_Refactor.docx'))
