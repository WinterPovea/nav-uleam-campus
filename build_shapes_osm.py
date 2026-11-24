#!/usr/bin/env python3
"""Genera app/src/main/assets/campus_shapes.csv a partir de datos reales de OpenStreetMap.

Entradas (exportadas de QGIS con QuickOSM, EPSG:4326):
  data/osm/osm_poligonos.geojson  -> edificios (building), estadio/canchas (leisure), plaza (highway=pedestrian)
  data/osm/osm_lineas.geojson     -> calles y veredas (highway)   [opcional: si falta, se conservan las vías actuales]
Se conservan del CSV actual los íconos puntuales (parqueos, agua, cafetería) y, si no hay líneas OSM, las vías.

Datos © OpenStreetMap contributors, licencia ODbL (https://www.openstreetmap.org/copyright).
Uso: python3 build_shapes_osm.py
"""
import csv, json, math, re
from pathlib import Path

ROOT = Path(__file__).parent
ASSETS = ROOT / "app/src/main/assets"
OSM = ROOT / "data/osm"
OUT = ASSETS / "campus_shapes.csv"
DATA_DIR = ROOT / "data"

BLOQUE = re.compile(r"^B-\d{2}$")
CODIGO = re.compile(r"^[A-ZÑ]-\d{2}$")
FUERA = {"Reef Hostel", "Chocolate Cyber", "Manadialisis", "Rico Ice", "Las Delicias del Niño Rodri",
         "Edifcio Crema", "Edificio Crema", "Comedor “Santy”", "Parque Los Eléctricos"}
# Tipos de vía OSM que se dibujan y su ancho en metros. Las veredas (footway) se pintan aparte, punteadas.
ANCHO_VIA = {"service": 5.0, "residential": 6.0, "unclassified": 6.0, "tertiary": 7.0, "living_street": 5.0,
             "pedestrian": 4.0, "path": 2.5, "track": 3.0, "cycleway": 2.5, "steps": 2.0}
VEREDA = {"footway"}
# Polígonos OSM que se incluyen siempre aunque su centro quede fuera de la envolvente de nodos
INCLUIR = {"1515520316": ("OTRO", "CIEC")}   # Centro de Innovación, Emprendimiento y Convenciones (en construcción en OSM)


def m_xy(lat, lon, lat0=-0.9525):
    return (lon * 111320.0 * math.cos(math.radians(lat0)), lat * 111320.0)


def hull(pts):
    pts = sorted(set(pts))
    def cr(o, a, b): return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lo, up = [], []
    for p in pts:
        while len(lo) >= 2 and cr(lo[-2], lo[-1], p) <= 0: lo.pop()
        lo.append(p)
    for p in reversed(pts):
        while len(up) >= 2 and cr(up[-2], up[-1], p) <= 0: up.pop()
        up.append(p)
    return lo[:-1] + up[:-1]


def dist_to_hull(h, p):
    """Distancia (m) de p al polígono convexo h (0 si está dentro). h y p en metros, antihorario."""
    inside = all((h[(i + 1) % len(h)][0] - h[i][0]) * (p[1] - h[i][1]) - (h[(i + 1) % len(h)][1] - h[i][1]) * (p[0] - h[i][0]) >= 0
                 for i in range(len(h)))
    if inside: return 0.0
    return min(seg_dist(p, h[i], h[(i + 1) % len(h)]) for i in range(len(h)))


def seg_dist(p, a, b):
    bx, by = b[0] - a[0], b[1] - a[1]; px, py = p[0] - a[0], p[1] - a[1]
    t = max(0.0, min(1.0, (px * bx + py * by) / max(bx * bx + by * by, 1e-9)))
    return math.hypot(px - t * bx, py - t * by)


def ring_of(geom):
    """Anillo exterior del primer polígono, sin el punto de cierre, como [(lat, lon)]."""
    c = geom["coordinates"]
    if geom["type"] == "MultiPolygon": c = c[0]
    ring = [(round(q[1], 6), round(q[0], 6)) for q in c[0]]
    return ring[:-1] if len(ring) > 1 and ring[0] == ring[-1] else ring


def lines_of(geom):
    if geom["type"] == "LineString": cs = [geom["coordinates"]]
    elif geom["type"] == "MultiLineString": cs = geom["coordinates"]
    else: return []
    return [[(round(q[1], 6), round(q[0], 6)) for q in c] for c in cs]


def centroid(r): return (sum(p[0] for p in r) / len(r), sum(p[1] for p in r) / len(r))


def poly_dist_m(ring, p):
    pm = m_xy(*p); rm = [m_xy(*q) for q in ring]
    return min(seg_dist(pm, rm[i], rm[(i + 1) % len(rm)]) for i in range(len(rm)))


def pitch_rect(ring):
    """Rectángulo orientado (4 vértices) con el primer lado corto, como espera drawPitchMarks."""
    pts = ring[:4] if len(ring) >= 4 else ring
    d01 = math.dist(m_xy(*pts[0]), m_xy(*pts[1])); d12 = math.dist(m_xy(*pts[1]), m_xy(*pts[2]))
    return pts if d01 <= d12 else pts[1:] + pts[:1]


def main():
    nodos = {r["id"]: (float(r["latitud"]), float(r["longitud"])) for r in csv.DictReader(open(ASSETS / "nodos.csv", encoding="utf-8"))}
    lugares = list(csv.DictReader(open(ASSETS / "lugares.csv", encoding="utf-8")))
    viejo = list(csv.reader(open(OUT, encoding="utf-8")))[1:]
    grupo_bloque = {r[1]: r[2] for r in viejo if r[0] == "EDIFICIO"}

    # Zona del campus: envolvente convexa de los nodos (incluye puertas) con 12 m de margen
    campus = hull([m_xy(*v) for v in nodos.values()])
    # Un polígono entra si su centro cae en el campus o si toca la entrada de un lugar del catálogo
    # (p. ej. el CIEC, cuyo centro queda fuera de la envolvente de nodos)
    lugar_xy = [m_xy(*nodos[l["id_nodo"]]) for l in lugares if l["id_nodo"] in nodos]
    def dentro(ring):
        if dist_to_hull(campus, m_xy(*centroid(ring))) <= 12.0: return True
        rm = [m_xy(*q) for q in ring]
        return any(min(seg_dist(n, rm[i], rm[(i + 1) % len(rm)]) for i in range(len(rm))) <= 10.0 for n in lugar_xy)

    feats = json.load(open(OSM / "osm_poligonos.geojson", encoding="utf-8"))["features"]
    out = []   # [tipo, codigo, grupo, ring]
    for f in feats:
        p = f["properties"]; ring = ring_of(f["geometry"])
        if len(ring) < 3: continue
        if str(p.get("osm_id")) in INCLUIR:
            t, c = INCLUIR[str(p.get("osm_id"))]; out.append([t, c, "OTRO", ring]); continue
        if not dentro(ring) or (p.get("name") or "") in FUERA: continue
        name = (p.get("name") or "").strip(); b = p.get("building"); lei = p.get("leisure"); hw = p.get("highway")
        if hw == "pedestrian":
            if "Plaza" in name: out.append(["PLAZA", "Plaza Cultural", "OTRO", ring])
            continue
        if lei == "track": out.append(["PISTA", "Estadio", "OTRO", ring]); continue
        if lei == "pitch":
            if "Estadio" in name: out.append(["CANCHA_ESTADIO", "Estadio", "OTRO", pitch_rect(ring)])
            elif p.get("surface") in ("grass", "artificial_turf"): out.append(["CANCHA_VERDE", "Cancha", "OTRO", ring])
            else: out.append(["CANCHA", "Cancha", "OTRO", ring])
            continue
        if lei == "swimming_pool": out.append(["CANCHA", "Piscina", "OTRO", ring]); continue
        if lei == "bleachers" or b == "grandstand": out.append(["GRADA", "Gradas", "OTRO", ring]); continue
        if lei in ("playground", "park", "garden"): continue   # parques vecinos (Parque Los Eléctricos), fuera de la U
        if not b: continue
        if BLOQUE.match(name): out.append(["EDIFICIO", name, grupo_bloque.get(name, "OTRO"), ring])
        elif p.get("amenity") in ("restaurant", "fast_food") and "Agro" not in name: out.append(["COMIDA", name or "Cafetería", "OTRO", ring])
        else: out.append(["OTRO", name if CODIGO.match(name) else (name or ""), "OTRO", ring])

    # Cafeterías del catálogo sin etiqueta en OSM: el edificio más cercano a su entrada (<= 8 m) pasa a COMIDA;
    # si no hay edificio cerca, se dibuja un ícono de cafetería sobre su nodo
    sin_huella = []
    for l in lugares:
        if l["categoria"] != "COMIDA" or l["id_nodo"] not in nodos: continue
        nombre = re.sub(r"\s*\(entrada \d\)", "", l["nombre"])
        if nombre in ("El bosque", "Agromarket"): continue
        cand = [o for o in out if o[0] in ("OTRO", "COMIDA")]
        if not cand: continue
        best = min(cand, key=lambda o: poly_dist_m(o[3], nodos[l["id_nodo"]]))
        if poly_dist_m(best[3], nodos[l["id_nodo"]]) <= 8.0:
            best[0], best[1] = "COMIDA", nombre
        else:
            sin_huella.append((nombre, nodos[l["id_nodo"]]))

    # Vías: líneas OSM si existen; si no, se conservan las actuales
    vias = []
    lin = OSM / "osm_lineas.geojson"
    lineas = json.load(open(lin, encoding="utf-8"))["features"] if lin.exists() else []
    lineas = [f for f in lineas if f["geometry"]["type"] in ("LineString", "MultiLineString")]
    if lineas:
        for f in lineas:
            hwy = f["properties"].get("highway")
            for line in lines_of(f["geometry"]):
                if len(line) < 2: continue
                n_in = sum(dist_to_hull(campus, m_xy(*q)) <= 15.0 for q in line)
                if n_in < max(1, len(line) // 2): continue
                if hwy in VEREDA: vias.append(["VEREDA", "2.0", "OTRO", line])
                elif hwy in ANCHO_VIA: vias.append(["VIA", "%.1f" % ANCHO_VIA[hwy], "OTRO", line])
    else:
        vias = []   # sin líneas OSM la app dibuja los caminos a partir de las aristas del grafo
    puntos = [r for r in viejo if r[0] in ("PARQUEO", "AGUA", "CAFE")]
    # Escaleras que no son arista del grafo (solo dibujo), ajustadas sobre capturas del equipo
    ESCALERAS = [
        ("Infraestructura", nodos["453"], (-0.952416, -80.744401)),   # sigue la arista 453-328 hasta la vía
        ("Cancha multiple", nodos["407"], (-0.953478, -80.746871)),   # media cuesta de la cancha múltiple
    ]
    for nombre, a, b in ESCALERAS:
        puntos.append(["ESCALERA", nombre, "OTRO", "%.6f %.6f %.6f %.6f" % (*a, *b)])
    for nombre, g in sin_huella:
        if not any(r[0] == "CAFE" and r[1] == nombre for r in puntos):
            puntos.append(["CAFE", nombre, "OTRO", "%.6f %.6f" % g])

    # Edificios que no están en los polígonos de OSM: trazados sobre una captura de QGIS georreferenciada con
    # los nodos 470 (px 200,456) y 329 (px 554,299) -> ~0,12 m por píxel, norte arriba
    def px(x, y):
        return (round(-0.950933 - (y - 456) * 1.0637e-6, 6), round(-80.743391 + (x - 200) * 1.0763e-6, 6))
    MANUALES = [
        ("OTRO", "DIIT", [px(632, 207), px(722, 272), px(640, 384), px(556, 320), px(574, 284), px(562, 275), px(570, 262)]),
    ]
    for t, c, ring in MANUALES:
        out.append([t, c, "OTRO", ring])

    # Vías dobles (Doble_Via = Si en QGIS): se dibujan más anchas que las veredas. Sale de build_grafo.py
    vd = DATA_DIR / "vias_dobles.csv"
    if vd.exists():
        for r in csv.DictReader(open(vd, encoding="utf-8")):
            a, b = r["id_origen"], r["id_destino"]
            if a in nodos and b in nodos:
                puntos.append(["VIA_DOBLE", "%s-%s" % (a, b), "OTRO", "%.6f %.6f %.6f %.6f" % (*nodos[a], *nodos[b])])

    fmt = lambda ring: " ".join("%.6f %.6f" % q for q in ring)
    with open(OUT, "w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow(["tipo", "codigo", "grupo", "puntos"])
        for v in vias: w.writerow([v[0], v[1], v[2], v[4] if v[3] is None else fmt(v[3])])
        for o in out: w.writerow([o[0], o[1], o[2], fmt(o[3])])
        for r in puntos: w.writerow(r)

    # Informe: cada bloque del catálogo debe tener huella OSM y su entrada cerca
    from collections import Counter
    print("figuras:", Counter(o[0] for o in out), "| vias:", Counter(v[0] for v in vias), "| puntos:", len(puntos))
    for l in lugares:
        m = re.search(r"B-\d+", l["codigo"] or "")
        if not m or l["id_nodo"] == "230": continue
        hs = [o for o in out if o[0] == "EDIFICIO" and o[1] == m.group(0)]
        d = min((poly_dist_m(o[3], nodos[l["id_nodo"]]) for o in hs), default=None)
        if d is None or d > 6: print("  revisar", m.group(0), "nodo", l["id_nodo"], "->", "sin huella" if d is None else "%.1f m" % d)


if __name__ == "__main__":
    main()
