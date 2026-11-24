#!/usr/bin/env python3
"""Regenera app/src/main/assets/nodos.csv y aristas.csv desde las capas exportadas de QGIS.

Entradas:
  data/nodos_u.csv  -> ID, Categoria, Descri, Latitud, Longitud, Facultad, Carrera
  data/aristas.csv  -> ID_Origen, ID_Destino, Doble_Via, Notas
Reglas (las mismas que build_data.py, el generador original):
  - distancia = haversine entre los nodos; todas las aristas son bidireccionales (peatones)
  - tiene_escaleras = 1 si la nota menciona "escalera"
  - se omiten aristas con nodos inexistentes, duplicadas (en cualquier sentido) y nodos sin aristas
Al final imprime un informe de validación. Después ejecutar build_lugares.py y build_shapes_osm.py.
"""
import csv, math, re
from pathlib import Path

ROOT = Path(__file__).parent
DATA, ASSETS = ROOT / "data", ROOT / "app/src/main/assets"
FIXES = {"Aduitorio": "Auditorio", "adminision": "admision", "Tasty food": "Tasty Food", "Edmundi": "Edmundo"}


def hav(a, b):
    R = 6371000; p1, p2 = math.radians(a[0]), math.radians(b[0])
    h = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(b[1] - a[1]) / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


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
    rep, aristas, vistas = [], [], set()
    malas, dup = [], 0
    for r in csv.DictReader(open(DATA / "aristas.csv", encoding="utf-8")):
        a, b = int(r["ID_Origen"]), int(r["ID_Destino"])
        if a not in nodos or b not in nodos: malas.append((a, b)); continue
        k = (min(a, b), max(a, b))
        if k in vistas: dup += 1; continue
        vistas.add(k)
        nota = (r.get("Notas") or "").strip()
        aristas.append(dict(a=a, b=b, d=round(hav((nodos[a]["lat"], nodos[a]["lon"]), (nodos[b]["lat"], nodos[b]["lon"])), 2),
                            esc=int(bool(re.search("scalera", nota, re.I))), nota=nota,
                            doble=(r.get("Doble_Via") or "").strip().lower() == "si"))
    rep.append(f"Aristas con nodo inexistente (omitidas): {malas}")
    rep.append(f"Aristas duplicadas eliminadas: {dup}")
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
    # Vías dobles (solo diseño: se dibujan más anchas en el mapa)
    with open(DATA / "vias_dobles.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n"); w.writerow(["id_origen", "id_destino"])
        for x in aristas:
            if x["doble"]: w.writerow([x["a"], x["b"]])
    print("\n".join(rep)); print(len(usados), "nodos,", len(aristas), "aristas,", sum(x["esc"] for x in aristas), "con escaleras,",
                              sum(x["doble"] for x in aristas), "vías dobles")


if __name__ == "__main__":
    main()
