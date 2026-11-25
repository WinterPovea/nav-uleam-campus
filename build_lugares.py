"""Regenera app/src/main/assets/lugares.csv enriquecido con Facultad/Carrera.

Uso (desde la raíz del repo):
    python3 build_lugares.py [data/nodos_u.csv]

- Fuente de Facultad/Carrera: data/nodos_u.csv (nodos crudos actualizados).
- Los id_nodo se toman del lugares.csv actual; los nodos nuevos que no son de calle (DESTINO, ENTRADA,
  ZONA_SEGURA) presentes en nodos.csv se agregan como lugares nuevos (NUEVOS define su nombre y categoría).
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
    "Sotfware": "Software",
}
# Lugares nuevos (nodos agregados en QGIS): nombre visible, categoría y palabras clave
NUEVOS = {
    329: ("Dirección de Informática e Innovación Tecnológica (DIIT)", "ADMINISTRATIVO",
          "diit informatica sistemas ti tecnologia soporte"),
}
# Segundas entradas de un bloque: se nombran "(Bloque B-xx, acceso 2)"
ACCESO_2 = {314, 226, 227}
# B-21 (nodo 221) sin carreras por el momento; Psicología, Fisioterapia, etc. están en el B-20 (nodo 220).
# data/nodos_u.csv ya trae ese reparto; estas tablas quedan vacías para casos futuros.
CARRERAS_DE = {}                # nodo -> nodo del que toma las carreras
SIN_CARRERAS = {221}            # nodos cuya descripción es "No disponible por el momento"
BLOQUE_REAL = {}                # id_nodo -> bloque a mostrar si difiere del que trae el CSV (hoy ninguno)

# Sinónimos que ayudan a encontrar la facultad por palabras sueltas.
SINONIMOS = {
    "FCS": "salud",
    "FIIA": "ingenieria industria arquitectura",
    "FCSDB": "sociales derecho bienestar",
    "FETAH": "educacion turismo artes humanidades",
    "FACIVITEC": "ciencias vida tecnologias",
    "FCACC": "administrativas contables comercio",
}


# El CSV de QGIS se exportó sin tildes (la codificación fallaba). Para MOSTRAR la ortografía correcta en la app
# se restauran aquí las tildes palabra por palabra; la columna `busqueda` sigue sin tildes a propósito.
ACENTOS = {w: a for w, a in (p.split(":") for p in """
administracion:administración admision:admisión agroindustria:agroindustria alimentos:alimentos
arqueologia:arqueología auditoria:auditoría basica:básica bilingue:bilingüe billingue:bilingüe biologia:biología
clinico:clínico comunicacion:comunicación construccion:construcción contable:contable criminologia:criminología
dietetica:dietética direccion:dirección economia:economía educacion:educación enfermeria:enfermería
escenicas:escénicas estrategico:estratégico fisica:física fonoaudiologia:fonoaudiología gastronomia:gastronomía
gastronomico:gastronómico gestion:gestión hoteleria:hotelería informacion:información ingenieria:ingeniería
innovacion:innovación investigacion:investigación maritima:marítima mecanica:mecánica multiple:múltiple
nivelacion:nivelación nutricion:nutrición odontologia:odontología pacifico:pacífico pedagogia:pedagogía
plasticas:plásticas politicas:políticas produccion:producción psicologia:psicología publica:pública
publico:público raices:raíces secretaria:secretaría sociologia:sociología tecnologia:tecnología
tecnologias:tecnologías electrica:eléctrica fonoaudologia:fonoaudiología informatica:informática
""".split())}


def acentuar(text: str) -> str:
    """Restaura tildes en palabras conocidas conservando mayúsculas/minúsculas. Es idempotente."""
    def repl(m: re.Match) -> str:
        w = m.group(0)
        a = ACENTOS.get(w.lower())
        if a is None or (w.isupper() and len(w) > 1):  # siglas (FCS, DIOP...) no se tocan
            return w
        return a[0].upper() + a[1:] if w[0].isupper() else a
    return re.sub(r"[A-Za-zÁÉÍÓÚáéíóúÑñÜü]+", repl, text)


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

    # Nodos nuevos de nodos.csv que aún no están en el catálogo
    with (ASSETS / "nodos.csv").open(encoding="utf-8", newline="") as f:
        en_grafo = {int(r["id"]): r for r in csv.DictReader(f)}
    for nid, n in en_grafo.items():
        if n["tipo"] == "CONEXION" or str(nid) in vistos:
            continue
        nombre, cat, et = NUEVOS.get(nid, (re.sub(r"^(Ingreso|Entrada( hacia| a)?)\s+", "", n["descripcion_interna"]).strip(),
                                           "BLOQUE" if re.search(r"B-\d+", n["descripcion_interna"]) else "ADMINISTRATIVO", ""))
        m = re.search(r"B-\d+", n["descripcion_interna"])
        unicas.append(dict(id_nodo=str(nid), codigo=m.group(0) if m else "", nombre=nombre, descripcion="",
                           categoria=cat, etiquetas=et, busqueda="", activo="1"))
        vistos.add(str(nid))
        print("lugar nuevo:", nid, nombre)

    # Ajustes manuales de catálogo (se reaplican en cada ejecución).
    for r in unicas:
        if r["id_nodo"] == "305":  # Tasty Food 1 tiene dos entradas: 305 y 318
            r["nombre"], r["etiquetas"] = "Tasty Food 1 (entrada 1)", "tasty food cafeteria comida"
        elif r["id_nodo"] == "318":
            r["nombre"], r["etiquetas"] = "Tasty Food 1 (entrada 2)", "tasty food cafeteria comida"
        elif r["id_nodo"] == "312":  # El bosque es una cafetería, no un lugar cultural
            r["categoria"], r["etiquetas"] = "COMIDA", "cafeteria cafe comida"

    enriquecidas = 0
    for r in unicas:
        nid = int(r["id_nodo"])
        src = fuente.get(nid)
        fac_raw = (src or {}).get("Facultad", "No aplica").strip()
        if src is not None and fac_raw.lower() not in NO_APLICA:
            fac, sigla = split_facultad(fac_raw)
            carreras = fix(src["Carrera"])
            carreras = "" if carreras.lower() in NO_DISPONIBLE else carreras
            if nid in CARRERAS_DE:
                c2 = fix(fuente[CARRERAS_DE[nid]]["Carrera"])
                carreras = "" if c2.lower() in NO_DISPONIBLE else c2
            elif nid in SIN_CARRERAS:
                carreras = ""
            m = re.search(r"B-\d+", r["codigo"])  # idempotente: acepta "B-16" o "FCS·B-16"
            md = re.search(r"B-\d+", src.get("Descri", ""))  # el bloque sale de la descripción original del nodo
            bloque = BLOQUE_REAL.get(nid) or (md.group(0) if md else (m.group(0) if m else ""))
            if bloque:
                acceso2 = ", acceso 2" if nid in ACCESO_2 else ""
                if nid == 230:
                    r["nombre"] = f"Laboratorio gastronomico (Bloque {bloque})"
                else:
                    r["nombre"] = f"Facultad de {fac} (Bloque {bloque}{acceso2})"
                r["codigo"] = f"{sigla}·{bloque}"
                r["descripcion"] = carreras or (
                    "No disponible por el momento" if nid in SIN_CARRERAS else f"Facultad de {fac}")
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

    for r in unicas:
        r["nombre"] = acentuar(r["nombre"])
        r["descripcion"] = acentuar(r["descripcion"])
        r["busqueda"] = norm(r["busqueda"])  # sin tildes, siempre

    with (ASSETS / "lugares.csv").open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=campos, lineterminator="\n")
        w.writeheader()
        w.writerows(unicas)
    print(f"{len(unicas)} lugares, {enriquecidas} enriquecidos con facultad/carrera")


if __name__ == "__main__":
    main()
