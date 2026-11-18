"""Regenera app/src/main/assets/lugares.csv enriquecido con Facultad/Carrera.

Uso (desde la raíz del repo):
    python3 build_lugares.py [data/nodos_u.csv]

- Fuente de Facultad/Carrera: data/nodos_u.csv (nodos crudos actualizados).
- Los id_nodo se toman del lugares.csv actual: no se agregan ni quitan nodos, de modo que
  nodos.csv y aristas.csv (y el cálculo de rutas) no se alteran.
- Cada fila se escribe con `busqueda` en minúsculas, sin tildes y sin signos de puntuación.
"""
import csv
import re
import sys
import unicodedata
from pathlib import Path

ASSETS = Path("app/src/main/assets")
SRC = Path(sys.argv[1] if len(sys.argv) > 1 else "data/nodos_u.csv")
NO_APLICA = {"", "no aplica"}
NO_DISPONIBLE = {"no disponible", "no aplica", ""}

# Correcciones ortográficas del archivo fuente (se aplican a Facultad y Carrera).
FIXES = {
    "Comerico": "Comercio",
    "Ingeneria": "Ingenieria",
    "Billingue": "Bilingue",
}
# Sinónimos que ayudan a encontrar la facultad por palabras sueltas.
SINONIMOS = {
    "FCS": "salud",
    "FIIA": "ingenieria industria arquitectura",
    "FCSDB": "sociales derecho bienestar",
    "FETAH": "educacion turismo artes humanidades",
    "FACIVITEC": "ciencias vida tecnologias",
    "FCACC": "administrativas contables comercio",
}


def fix(text: str) -> str:
    for bad, good in FIXES.items():
        text = text.replace(bad, good)
    return re.sub(r"\s+", " ", text).strip()


def norm(text: str) -> str:
    """minúsculas + sin tildes + sin puntuación (conserva guion de b-01)."""
    t = unicodedata.normalize("NFD", text.lower())
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    t = re.sub(r"[^a-z0-9\- ]+", " ", t)
    t = re.sub(r"(?<![a-z0-9])-|-(?![a-z0-9])", " ", t)
    return " ".join(dict.fromkeys(t.split()))  # tokens únicos, en orden


def split_facultad(raw: str):
    m = re.match(r"^(.*?)\s*\(([A-Z]+)\)\s*$", fix(raw))
    if not m:
        raise ValueError(f"Facultad sin sigla: {raw!r}")
    return m.group(1).strip(), m.group(2)


def main() -> None:
    fuente = {}
    with SRC.open(encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            fuente[int(r["ID"])] = r

    with (ASSETS / "lugares.csv").open(encoding="utf-8", newline="") as f:
        rd = csv.DictReader(f)
        campos = rd.fieldnames
        filas = list(rd)

    # id_nodo 313 estaba duplicado (Gradas de estadio / Estadio): una sola fila.
    vistos, unicas = set(), []
    for r in filas:
        if r["id_nodo"] == "313":
            if "313" in vistos:
                continue
            r["nombre"] = "Estadio (gradas)"
            r["etiquetas"] = "estadio gradas coliseo futbol"
        vistos.add(r["id_nodo"])
        unicas.append(r)

    enriquecidas = 0
    for r in unicas:
        nid = int(r["id_nodo"])
        src = fuente.get(nid)
        fac_raw = (src or {}).get("Facultad", "No aplica").strip()
        if src is not None and fac_raw.lower() not in NO_APLICA:
            fac, sigla = split_facultad(fac_raw)
            carreras = fix(src["Carrera"])
            carreras = "" if carreras.lower() in NO_DISPONIBLE else carreras
            m = re.search(r"B-\d+", r["codigo"])  # idempotente: acepta "B-16" o "FCS·B-16"
            bloque = m.group(0) if m else ""
            if bloque:
                acceso2 = ", acceso 2" if nid == 314 else ""
                if nid == 230:
                    r["nombre"] = f"Laboratorio gastronomico (Bloque {bloque})"
                else:
                    r["nombre"] = f"Facultad de {fac} (Bloque {bloque}{acceso2})"
                r["codigo"] = f"{sigla}·{bloque}"
                r["descripcion"] = carreras or f"Facultad de {fac}"
            else:
                r["descripcion"] = carreras or f"Facultad de {fac}"
            r["etiquetas"] = f"{sigla.lower()} {SINONIMOS.get(sigla, '')}".strip()
            partes = [r["nombre"], sigla, f"facultad de {fac}", carreras, r["etiquetas"]]
            if bloque:
                partes.append(f"bloque {bloque} {bloque}")
            r["busqueda"] = norm(" ".join(p for p in partes if p))
            enriquecidas += 1
        else:
            r["busqueda"] = norm(" ".join([r["nombre"], r["codigo"], r["etiquetas"]]))

    with (ASSETS / "lugares.csv").open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=campos, lineterminator="\n")
        w.writeheader()
        w.writerows(unicas)
    print(f"{len(unicas)} lugares, {enriquecidas} enriquecidos con facultad/carrera")


if __name__ == "__main__":
    main()
