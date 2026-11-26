#!/usr/bin/env python3
"""Regenera app/src/main/assets/nodos.csv y aristas.csv desde las capas exportadas de QGIS.

Entradas:
  data/nodos_u.csv  -> ID, Categoria, Descri, Latitud, Longitud, Facultad, Carrera
  data/aristas.csv  -> ID_Origen, ID_Destino, Doble_Via, Notas
  data/qgis/aristas_uleam.shp (+ .shx/.dbf/.prj, opcional) -> forma real de cada arista (curvas)
Reglas (las mismas que build_data.py, el generador original):
  - distancia = largo de la forma dibujada en QGIS (o haversine si no hay forma); todas las aristas son
    bidireccionales (peatones). La forma se guarda en data/aristas_geom.csv para el dibujo del mapa
  - tiene_escaleras = 1 si la nota menciona "escalera"
  - se omiten aristas con nodos inexistentes, duplicadas (en cualquier sentido) y nodos sin aristas
Al final imprime un informe de validación. Después ejecutar build_lugares.py y build_shapes_osm.py.
"""
import csv, math, re, struct
from pathlib import Path

ROOT = Path(__file__).parent
DATA, ASSETS = ROOT / "data", ROOT / "app/src/main/assets"
FIXES = {"Aduitorio": "Auditorio", "adminision": "admision", "Tasty food": "Tasty Food", "Edmundi": "Edmundo"}


def hav(a, b):
    R = 6371000; p1, p2 = math.radians(a[0]), math.radians(b[0])
    h = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(b[1] - a[1]) / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


def read_shp_lines(base):
    """Lee un shapefile de polilíneas sin dependencias: [(atributos dbf, [(lat, lon), ...]), ...]."""
    b = open(str(base) + ".dbf", "rb").read()
    n = struct.unpack("<I", b[4:8])[0]; hl, rl = struct.unpack("<HH", b[8:12])
    fields, o = [], 32
    while b[o] != 0x0D:
        fields.append((b[o:o + 11].split(b"\0")[0].decode("latin-1"), b[o + 16])); o += 32
    attrs = []
    for i in range(n):
        r = b[hl + i * rl: hl + (i + 1) * rl]; p, d = 1, {}
        for nm, ln in fields:
            raw = r[p:p + ln]
            try: d[nm] = raw.decode("utf-8").strip()
            except UnicodeDecodeError: d[nm] = raw.decode("latin-1").strip()
            p += ln
        attrs.append(None if r[:1] == b"*" else d)
    s = open(str(base) + ".shp", "rb").read(); o, geoms = 100, []
    while o < len(s):
        _, cl = struct.unpack(">ii", s[o:o + 8]); o += 8; rec = s[o:o + cl * 2]; o += cl * 2
        if struct.unpack("<i", rec[:4])[0] == 0: geoms.append(None); continue
        npart, npt = struct.unpack("<ii", rec[36:44]); q = 44 + 4 * npart
        geoms.append([struct.unpack("<dd", rec[q + 16 * i: q + 16 * i + 16])[::-1] for i in range(npt)])
    return [(a, g) for a, g in zip(attrs, geoms) if a is not None]


def dist_m(a, b):
    return math.hypot((a[0] - b[0]) * 111320, (a[1] - b[1]) * 111320 * math.cos(math.radians(a[0])))


def fix(t):
    for k, v in FIXES.items(): t = t.replace(k, v)
    return t


def main():
    nodos = {}
    for r in csv.DictReader(open(DATA / "nodos_u.csv", encoding="utf-8")):
        d = fix(r["Descri"].strip())
        if r["ID"] == "492": d = d.replace("B-25", "B-23")
        nodos[int(r["ID"])] = dict(lat=float(r["Latitud"]), lon=float(r["Longitud"]),
                                   tipo=r["Categoria"].strip().replace(" ", "_"), desc=d)
    # Forma de las aristas dibujada en QGIS: (min, max) -> polilínea orientada de min a max
    formas, malas_formas = {}, []
    shp = DATA / "qgis" / "aristas_uleam"
    if shp.with_suffix(".shp").exists():
        for at, pts in read_shp_lines(shp):
            try: a, b = int(at["ID_Origen"]), int(at["ID_Destino"])
            except (KeyError, ValueError): continue
            if a not in nodos or b not in nodos or not pts or len(pts) < 2: continue
            na, nb = (nodos[a]["lat"], nodos[a]["lon"]), (nodos[b]["lat"], nodos[b]["lon"])
            if dist_m(pts[0], nb) + dist_m(pts[-1], na) < dist_m(pts[0], na) + dist_m(pts[-1], nb): pts = pts[::-1]
            err = dist_m(pts[0], na) + dist_m(pts[-1], nb)
            if err > 3.0:
                # La línea no calza con los ID de su tabla, pero sus extremos caen sobre otros nodos:
                # actualizar_mapa.py usa esos nodos para la arista, así que la forma va con ellos.
                ga, gb = (min(nodos, key=lambda i: dist_m((nodos[i]["lat"], nodos[i]["lon"]), q)) for q in (pts[0], pts[-1]))
                ega = dist_m((nodos[ga]["lat"], nodos[ga]["lon"]), pts[0]); egb = dist_m((nodos[gb]["lat"], nodos[gb]["lon"]), pts[-1])
                e1, e2 = dist_m(pts[0], na), dist_m(pts[-1], nb)
                if min(e1, e2) <= 3.0 and max(e1, e2) <= 25.0:
                    pass  # un extremo quedó suelto al mover el nodo: se pega al nodo (abajo)
                elif ga != gb and ega + egb <= 3.0 and {ga, gb} != {a, b}:
                    a, b = ga, gb
                    na, nb = (nodos[a]["lat"], nodos[a]["lon"]), (nodos[b]["lat"], nodos[b]["lon"])
                    err = ega + egb
                else:
                    malas_formas.append((a, b, round(err, 1))); continue
            k = (min(a, b), max(a, b))
            pts = [na] + [tuple(round(c, 7) for c in q) for q in pts[1:-1]] + [nb]   # extremos = nodos exactos
            if a > b: pts = pts[::-1]
            if k not in formas or err < formas[k][0]: formas[k] = (err, pts)
    rep, aristas, vistas = [], [], set()
    malas, dup = [], 0
    for r in csv.DictReader(open(DATA / "aristas.csv", encoding="utf-8")):
        a, b = int(r["ID_Origen"]), int(r["ID_Destino"])
        if a not in nodos or b not in nodos: malas.append((a, b)); continue
        k = (min(a, b), max(a, b))
        if k in vistas: dup += 1; continue
        vistas.add(k)
        nota = (r.get("Notas") or "").strip()
        forma = formas.get(k, (0, None))[1]
        largo = sum(hav(forma[i], forma[i + 1]) for i in range(len(forma) - 1)) if forma else \
            hav((nodos[a]["lat"], nodos[a]["lon"]), (nodos[b]["lat"], nodos[b]["lon"]))
        aristas.append(dict(a=a, b=b, d=round(largo, 2), forma=forma,
                            esc=int(bool(re.search("scalera", nota, re.I))), nota=nota,
                            doble=(r.get("Doble_Via") or "").strip().lower() == "si"))
    rep.append(f"Aristas con nodo inexistente (omitidas): {malas}")
    rep.append(f"Aristas duplicadas eliminadas: {dup}")
    rep.append(f"Aristas con forma de QGIS: {sum(1 for x in aristas if x['forma'] and len(x['forma']) > 2)} con curvas, "
               f"{sum(1 for x in aristas if x['forma'])} en total; formas descartadas (extremos lejos de sus nodos): {malas_formas}")
    usados = {x["a"] for x in aristas} | {x["b"] for x in aristas}
    rep.append(f"Nodos sin aristas (omitidos): {sorted(set(nodos) - usados)}")
    vec = {i: set() for i in usados}
    for x in aristas: vec[x["a"]].add(x["b"]); vec[x["b"]].add(x["a"])
    rep.append("DESTINO sin vecino de calle: " + str([i for i in sorted(usados) if nodos[i]["tipo"] == "DESTINO"
                                                       and not any(nodos[j]["tipo"] != "DESTINO" for j in vec[i])]))
    rep.append(f"Aristas > 80 m: {[(x['a'], x['b'], x['d']) for x in aristas if x['d'] > 80]}")

    with open(ASSETS / "aristas.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["id_origen", "id_destino", "distancia_m", "bidireccional", "tiene_escaleras", "nota"])
        for x in aristas: w.writerow([x["a"], x["b"], x["d"], 1, x["esc"], x["nota"]])
    with open(ASSETS / "nodos.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["id", "latitud", "longitud", "tipo", "descripcion_interna"])
        for i in sorted(usados, key=lambda i: list(nodos).index(i)):
            n = nodos[i]; w.writerow([i, n["lat"], n["lon"], n["tipo"], n["desc"]])
    # Forma de las aristas con vértices intermedios (solo diseño del mapa y de la ruta dibujada)
    with open(DATA / "aristas_geom.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n"); w.writerow(["id_a", "id_b", "puntos"])
        for x in aristas:
            if x["forma"] and len(x["forma"]) > 2:
                k = (min(x["a"], x["b"]), max(x["a"], x["b"]))
                w.writerow([k[0], k[1], " ".join("%.7f %.7f" % q for q in x["forma"])])
    # Vías dobles (solo diseño: se dibujan más anchas en el mapa)
    with open(DATA / "vias_dobles.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n"); w.writerow(["id_origen", "id_destino"])
        for x in aristas:
            if x["doble"]: w.writerow([x["a"], x["b"]])
    print("\n".join(rep)); print(len(usados), "nodos,", len(aristas), "aristas,", sum(x["esc"] for x in aristas), "con escaleras,",
                              sum(x["doble"] for x in aristas), "vías dobles")


if __name__ == "__main__":
    main()
