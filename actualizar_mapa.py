#!/usr/bin/env python3
"""Actualiza TODOS los datos del mapa de la app en un solo paso, directo desde las capas de QGIS.

Uso (desde la carpeta del repo):
    python actualizar_mapa.py
    python actualizar_mapa.py --gpkg "C:/ruta/nodos_u.gpkg" --shp "C:/ruta/aristas_uleam.shp"

Entradas (por defecto en data/qgis/):
  nodos_u.gpkg        capa de puntos de nodos (ID, Categoria, Descri, Facultad, Carrera). La posición se toma
                      de la GEOMETRÍA del punto (lo que se ve en QGIS), no de las columnas Latitud/Longitud.
  aristas_uleam.shp   capa de líneas de aristas (+ .dbf/.shx/.prj) con ID_Origen, ID_Destino, Doble_Via, Notas.

Qué hace:
  1. Escribe data/nodos_u.csv y data/aristas.csv (con las correcciones fijas de abajo).
  2. Ejecuta build_grafo.py  -> assets/nodos.csv, assets/aristas.csv (+ forma de cada arista) e informe de validación.
  3. Ejecuta build_lugares.py -> assets/lugares.csv (búsqueda).
  4. Ejecuta build_shapes_osm.py -> assets/campus_shapes.csv (si están los GeoJSON de OSM en data/osm/).
Solo usa la biblioteca estándar de Python (el paso 4 además necesita scipy; si falta, se avisa y se omite).
Al abrir la app, el grafo se recarga solo porque cambian los CSV (CsvSeeder compara la huella).
"""
import argparse, csv, sqlite3, struct, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).parent
DATA = ROOT / "data"
QGIS = DATA / "qgis"

# Correcciones que valen aunque la capa de QGIS diga otra cosa (la versión de la app es la correcta).
# B-20: solo Psicología. B-21: el resto de carreras de Ciencias de la Salud que antes estaban en el B-20.
CARRERAS_FIJAS = {
    "220": "Psicologia",
    "221": "Fisioterapia, Fonoaudiologia, Terapia Ocupacional, Nutricion y Dietetica",
}
# Aristas que faltan en la capa de QGIS y se agregan siempre (origen, destino, doble vía, nota).
ARISTAS_EXTRA = [
    ("433", "434", "No", "Conexion de camino hacia Puerta 3 (oeste de Bloque B-13)"),
]
CAMPOS_NODOS = ["ID", "Categoria", "Descri", "Latitud", "Longitud", "Facultad", "Carrera"]
CAMPOS_ARISTAS = ["ID_Origen", "ID_Destino", "Doble_Via", "Notas"]


def punto_gpkg(blob):
    """Geometría de punto de GeoPackage (cabecera GP + WKB) -> (lat, lon)."""
    if not blob or blob[:2] != b"GP":
        return None
    flags = blob[3]
    env = (flags >> 1) & 0x07
    o = 8 + {0: 0, 1: 32, 2: 48, 3: 48, 4: 64}.get(env, 0)
    end = "<" if blob[o] == 1 else ">"
    tipo = struct.unpack(end + "I", blob[o + 1:o + 5])[0] % 1000
    if tipo != 1:
        return None
    x, y = struct.unpack(end + "dd", blob[o + 5:o + 21])
    return y, x


def leer_nodos(gpkg):
    con = sqlite3.connect(f"file:{gpkg}?mode=ro", uri=True)
    try:
        capas = [r[0] for r in con.execute("SELECT table_name FROM gpkg_contents WHERE data_type='features'")]
        if not capas:
            sys.exit(f"{gpkg}: no tiene capas de puntos")
        capa = capas[0]
        geom = con.execute("SELECT column_name FROM gpkg_geometry_columns WHERE table_name=?", (capa,)).fetchone()[0]
        cols = [r[1] for r in con.execute(f'PRAGMA table_info("{capa}")')]
        filas = con.execute(f'SELECT * FROM "{capa}"').fetchall()
    finally:
        con.close()
    nodos, sin_geom = [], []
    for f in filas:
        d = dict(zip(cols, f))
        nid = str(d.get("ID") or "").strip()
        if not nid:
            continue
        nid = nid[:-2] if nid.endswith(".0") else nid
        p = punto_gpkg(d.get(geom))
        if p is None:
            sin_geom.append(nid); continue
        nodos.append({
            "ID": nid, "Categoria": (d.get("Categoria") or "").strip(), "Descri": (d.get("Descri") or "").strip(),
            "Latitud": f"{p[0]:.7f}", "Longitud": f"{p[1]:.7f}",
            "Facultad": (d.get("Facultad") or "No aplica").strip(),
            "Carrera": CARRERAS_FIJAS.get(nid, (d.get("Carrera") or "No aplica").strip()),
        })
    if sin_geom:
        print(f"  ! nodos sin geometría (omitidos): {', '.join(sin_geom)}")
    return capa, nodos


def leer_aristas(shp_base):
    sys.path.insert(0, str(ROOT))
    from build_grafo import read_shp_lines
    filas = []
    for attrs, _ in read_shp_lines(shp_base):
        o, d = str(attrs.get("ID_Origen", "")).strip(), str(attrs.get("ID_Destino", "")).strip()
        if o and d:
            filas.append({"ID_Origen": o, "ID_Destino": d, "Doble_Via": attrs.get("Doble_Via", "No") or "No",
                          "Notas": attrs.get("Notas", "")})
    pares = {frozenset((r["ID_Origen"], r["ID_Destino"])) for r in filas}
    for o, d, dv, nota in ARISTAS_EXTRA:
        if frozenset((o, d)) not in pares:
            filas.append({"ID_Origen": o, "ID_Destino": d, "Doble_Via": dv, "Notas": nota})
            print(f"  + arista {o}-{d} agregada (falta en la capa de QGIS)")
    return filas


def escribir(path, campos, filas):
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=campos, lineterminator="\n")
        w.writeheader()
        w.writerows(filas)


def correr(script):
    print(f"\n== {script} ==")
    r = subprocess.run([sys.executable, str(ROOT / script)], cwd=ROOT)
    return r.returncode == 0


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--gpkg", default=str(QGIS / "nodos_u.gpkg"), help="capa de nodos (GeoPackage)")
    ap.add_argument("--shp", default=str(QGIS / "aristas_uleam.shp"), help="capa de aristas (shapefile)")
    a = ap.parse_args()
    gpkg, shp = Path(a.gpkg), Path(a.shp)
    shp_base = shp.with_suffix("")
    for p in (gpkg, shp_base.with_suffix(".shp"), shp_base.with_suffix(".dbf")):
        if not p.exists():
            sys.exit(f"No se encontró {p}")

    capa, nodos = leer_nodos(gpkg)
    print(f"Nodos: {len(nodos)} (capa '{capa}' de {gpkg.name})")
    aristas = leer_aristas(shp_base)
    print(f"Aristas: {len(aristas)} (de {shp_base.name}.shp)")

    escribir(DATA / "nodos_u.csv", CAMPOS_NODOS, nodos)
    escribir(DATA / "aristas.csv", CAMPOS_ARISTAS, aristas)
    # build_grafo.py lee la forma de las aristas desde data/qgis/aristas_uleam.*: se copia si vino de otra carpeta
    destino = QGIS / "aristas_uleam"
    if shp_base.resolve() != destino.resolve():
        QGIS.mkdir(parents=True, exist_ok=True)
        for ext in (".shp", ".shx", ".dbf", ".prj"):
            src = shp_base.with_suffix(ext)
            if src.exists():
                destino.with_suffix(ext).write_bytes(src.read_bytes())

    ok = correr("build_grafo.py") and correr("build_lugares.py")
    if not ok:
        sys.exit("\nSe detuvo por un error (ver arriba).")
    if (DATA / "osm" / "osm_poligonos.geojson").exists():
        try:
            import scipy  # noqa: F401
            correr("build_shapes_osm.py")
        except ImportError:
            print("\n(build_shapes_osm.py omitido: falta scipy -> pip install scipy)")
    print("\nListo. Revisa el informe de validación de arriba y compila la app.")


if __name__ == "__main__":
    main()
