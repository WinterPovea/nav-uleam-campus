#!/usr/bin/env python3
"""Genera la capa vectorial del mapa del campus (campus_shapes.json) y una vista previa PNG.

Los contornos se trazaron sobre la foto del mapa físico (coordenadas de foto, 900x1600) y se
calibran a lat/lon con un ajuste afín por mínimos cuadrados contra los nodos de nodos_u.csv.
La info de facultades sale del CSV actual (no de la leyenda de la foto).
Uso: python3 build_mapa.py [salida_png]
"""
import csv, json, math, sys
from pathlib import Path

ROOT = Path(__file__).parent
FAC_COLOR = {
    "FCS": "#2aa7e0", "FIIA": "#1b3aa6", "FACIVITEC": "#1f9d55",
    "FETAH": "#f28c3b", "FCACC": "#2f6bd8", "FCSDB": "#b64552", "OTRO": "#6f8f94",
}

# --- recortes de la foto: c1 = (60,270) y c2 = (60,700), ampliados x2 -> foto = origen + p/2
def c1(pts): return [(60 + x / 2, 270 + y / 2) for x, y in pts]
def c2(pts): return [(60 + x / 2, 700 + y / 2) for x, y in pts]

# (id, etiqueta, facultad, polígono en coordenadas de foto)
BLOQUES = [
    ("B-23", "B-23", "FETAH", c1([(245, 300), (275, 285), (305, 330), (285, 375), (255, 365)])),
    ("B-23", None, "FETAH", c1([(185, 370), (215, 365), (262, 395), (240, 420), (205, 400)])),
    ("B-01", "B-01", "FETAH", c1([(625, 545), (685, 500), (765, 572), (722, 612), (695, 600), (640, 570)])),
    ("B-02", "B-02", "FCSDB", c1([(630, 600), (715, 630), (690, 680), (625, 640)])),
    ("B-02", None, "FCSDB", c1([(610, 605), (645, 585), (630, 630)])),
    ("B-03", "B-03", "FCACC", c1([(612, 648), (672, 690), (655, 720), (600, 672)])),
    ("B-04", "B-04", "FCS", c1([(850, 525), (870, 495), (905, 430), (965, 480), (940, 520), (900, 600)])),
    ("B-05", "B-05", "FETAH", c1([(985, 590), (1030, 555), (1070, 610), (1015, 655)])),
    ("B-05", None, "FETAH", c1([(1060, 575), (1140, 545), (1155, 585), (1085, 610)])),
    ("B-22", "B-22", "FETAH", [(204, 712), (225, 700), (268, 692), (273, 708), (255, 726), (215, 728)]),
    ("B-06", "B-06", "FCSDB", [(443, 710), (475, 678), (510, 705), (478, 745)]),
    ("B-07", "B-07", "FIIA", c2([(830, 105), (900, 70), (965, 110), (955, 165), (905, 185)])),
    ("B-07", None, "FIIA", c2([(850, 150), (880, 150), (910, 195), (880, 205)])),
    ("B-08", "B-08", "FIIA", c2([(945, 225), (1000, 205), (1022, 225), (940, 425), (910, 405)])),
    ("B-09", "B-09", "FIIA", c2([(922, 478), (950, 468), (980, 570), (948, 582)])),
    ("B-15", "B-15", "FACIVITEC", c2([(795, 520), (840, 495), (880, 520), (850, 555), (820, 540)])),
    ("B-14", "B-14", "FCSDB", c2([(772, 615), (815, 588), (862, 640), (825, 692), (800, 660)])),
    ("B-13", "B-13", "FCACC", c2([(828, 695), (905, 588), (962, 652), (930, 680), (868, 722)])),
    ("B-10", "B-10", "FACIVITEC", c2([(930, 758), (970, 745), (995, 785), (965, 805)])),
    ("B-11", "B-11", "FIIA", c2([(985, 820), (1015, 818), (1030, 880), (1000, 885)])),
    ("B-12", "B-12", "FCACC", c2([(905, 870), (945, 852), (950, 890), (912, 892)])),
    ("B-12", None, "FCACC", c2([(950, 860), (990, 858), (995, 890), (955, 892)])),
    ("B-16", "B-16", "FACIVITEC", c2([(450, 315), (480, 285), (520, 285), (545, 315), (570, 345), (555, 385), (510, 395), (480, 350)])),
    ("B-17", "B-17", "FCSDB", c2([(260, 335), (320, 320), (345, 360), (335, 415), (290, 410), (262, 365)])),
    ("B-18", "B-18", "FCS", c2([(315, 455), (370, 440), (405, 455), (395, 495), (335, 500)])),
    ("B-19", "B-19", "FCS", c2([(340, 515), (395, 512), (405, 605), (350, 610)])),
    ("B-20", "B-20", "FCS", c2([(265, 750), (380, 740), (380, 795), (300, 810), (265, 800)])),
    ("B-21", "B-21", "FCS", c2([(140, 818), (190, 812), (205, 835), (190, 880), (145, 885)])),
    ("B-24", "B-24", "FCS", c2([(195, 480), (230, 480), (255, 520), (255, 610), (230, 617), (215, 550), (195, 520)])),
    ("B-25", "B-25", "FCS", c2([(280, 690), (360, 680), (370, 715), (300, 730), (278, 710)])),
]
# Edificios administrativos / otros (gris-azulado), con etiqueta de la leyenda
OTROS = [
    ("A-01", c1([(280, 490), (375, 440), (425, 545), (335, 600)])),
    ("I-09", c1([(365, 610), (430, 575), (450, 630), (380, 655)])),
    ("K-11", c1([(215, 555), (250, 545), (262, 595), (235, 600)])),
    ("C-03", c1([(745, 415), (800, 375), (865, 430), (805, 505)])),
    ("F-06", c1([(680, 775), (745, 685), (815, 745), (740, 825)])),
    ("E-05", c1([(478, 712), (560, 705), (595, 760), (520, 800), (490, 770)])),
    ("D-04", c1([(125, 420), (165, 400), (205, 425), (195, 470), (155, 475)])),
    ("J-10", c1([(925, 625), (990, 600), (995, 625), (940, 645)])),
    ("L-12", c1([(1140, 710), (1175, 695), (1185, 740), (1150, 755)])),
    ("Ñ-15", c2([(355, 370), (385, 355), (410, 380), (400, 415), (360, 410)])),
    ("G-07", c2([(485, 490), (520, 485), (550, 520), (520, 550), (490, 520)])),
    ("N-14", c2([(225, 205), (265, 190), (285, 225), (245, 235)])),
    ("N-14", c2([(315, 205), (345, 215), (335, 245), (305, 240)])),
    (None, c2([(285, 235), (330, 225), (335, 262), (290, 265)])),
    ("M-13", c2([(395, 230), (440, 215), (450, 255), (410, 270)])),
    ("H-08", c2([(425, 435), (455, 425), (465, 455), (435, 462)])),
    ("O-16", c2([(115, 895), (150, 880), (160, 925), (125, 935)])),
    ("CIEC", c1([(775, 255), (820, 220), (1125, 500), (1085, 555)])),  # Centro de Convenciones
    ("Juan Montalvo (antiguo)", c2([(565, 925), (630, 890), (665, 930), (600, 965)])),
    ("Juan Montalvo (nuevo)", c2([(700, 915), (765, 885), (775, 925), (715, 945)])),
]
COMIDA = [  # cafeterías (amarillo)
    c1([(1040, 775), (1110, 715), (1120, 790), (1070, 805)]),
    c2([(870, 425), (920, 415), (935, 460), (900, 485)]),
    c2([(800, 880), (855, 878), (852, 905), (800, 905)]),
]
CANCHAS = [  # (nombre, polígono)
    ("Canchas múltiples", c1([(1010, 665), (1140, 590), (1185, 640), (1060, 740)])),
    ("Canchas múltiples", c2([(640, 840), (750, 755), (805, 805), (700, 890)])),
    ("Cancha", [(218, 668), (256, 663), (258, 683), (219, 689)]),
    ("Cancha sintética", c2([(240, 880), (315, 875), (320, 970), (255, 985)])),
]
# Estadio: óvalo (centro, semiejes a>b, inclinación) en coordenadas de foto
ESTADIO = dict(cx=388, cy=782, a=96, b=84, rot=18, campo=(66, 36))
PARQUEOS = [  # símbolo P
    c1([(398, 425)])[0], c1([(690, 332)])[0], c1([(780, 675)])[0], c1([(990, 780)])[0], c1([(175, 670)])[0],
    c2([(350, 175)])[0], c2([(455, 205)])[0], c2([(560, 408)])[0], c2([(325, 655)])[0],
    c2([(205, 720)])[0], c2([(745, 720)])[0], c2([(180, 55)])[0], c2([(875, 880)])[0],
]
HIDRATACION = [
    c1([(228, 430)])[0], c1([(716, 513)])[0], c1([(595, 648)])[0], c1([(983, 673)])[0], c1([(885, 848)])[0],
    c2([(925, 255)])[0], c2([(555, 340)])[0], c2([(262, 385)])[0], c2([(800, 497)])[0],
    c2([(975, 740)])[0], c2([(630, 858)])[0], c2([(672, 912)])[0],
]


def load_nodes():
    rows = list(csv.DictReader(open(ROOT / "data/nodos_u.csv", encoding="utf-8")))
    nodes = {r["ID"]: (float(r["Latitud"]), float(r["Longitud"])) for r in rows}
    return rows, nodes


def poly_centroid(pts):
    return (sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts))


LAT0, LON0 = -0.9525, -80.745
M_LAT = 111320.0
M_LON = 111320.0 * math.cos(math.radians(LAT0))


def to_m(g):  # (lat, lon) -> metros locales (este, norte)
    return ((g[1] - LON0) * M_LON, (g[0] - LAT0) * M_LAT)


def fit_similarity(pairs, weights):
    """Semejanza foto -> metros (giro + escala uniforme + traslación): conserva las formas.
    X = a*px - b*qy + tx ; Y = b*px + a*qy + ty, con qy = -py (la foto tiene y hacia abajo)."""
    N = [[0.0] * 4 for _ in range(4)]; r = [0.0] * 4
    for (px, py), g, w in zip([p for p, _ in pairs], [g for _, g in pairs], weights):
        X, Y = to_m(g); qy = -py
        for row, y in (((px, -qy, 1.0, 0.0), X), ((qy, px, 0.0, 1.0), Y)):
            for i in range(4):
                r[i] += w * row[i] * y
                for j in range(4):
                    N[i][j] += w * row[i] * row[j]
    M = [N[i] + [r[i]] for i in range(4)]
    for i in range(4):
        p = max(range(i, 4), key=lambda k: abs(M[k][i])); M[i], M[p] = M[p], M[i]
        for k in range(4):
            if k != i:
                f = M[k][i] / M[i][i]
                for c in range(i, 5): M[k][c] -= f * M[i][c]
    return [M[i][4] / M[i][i] for i in range(4)]


def apply(c, p):
    a, b, tx, ty = c; qy = -p[1]
    X = a * p[0] - b * qy + tx; Y = b * p[0] + a * qy + ty
    return (LAT0 + Y / M_LAT, LON0 + X / M_LON)


def calibrate(nodes):
    pairs, w = [], []
    # puertas: punta de la flecha amarilla en la foto
    gates = {"100": (345, 452), "103": (110, 542), "101": (135, 815), "102": (567, 1027)}
    for k, p in gates.items():
        pairs.append((p, nodes[k])); w.append(4.0)
    # entradas de bloque vs centro del polígono (sesgo ~ 10-15 m, se promedia)
    cent = {}
    for bid, _, _, poly in BLOQUES:
        cent.setdefault(bid, []).append(poly_centroid(poly))
    for bid, cs in cent.items():
        num = int(bid.split("-")[1]); nid = str(200 + num)
        if nid in nodes:
            c = poly_centroid(cs); pairs.append((c, nodes[nid])); w.append(1.0)
    return fit_similarity(pairs, w), pairs


def build(nodes):
    cal, pairs = calibrate(nodes)
    g = lambda poly: [list(apply(cal, p)) for p in poly]
    shapes = {"bloques": [], "otros": [], "comida": [], "canchas": [], "parqueos": [], "agua": []}
    for bid, label, fac, poly in BLOQUES:
        shapes["bloques"].append({"codigo": bid, "etiqueta": label, "facultad": fac, "color": FAC_COLOR[fac], "poly": g(poly)})
    for label, poly in OTROS:
        shapes["otros"].append({"etiqueta": label, "color": FAC_COLOR["OTRO"], "poly": g(poly)})
    for poly in COMIDA:
        shapes["comida"].append({"poly": g(poly)})
    for name, poly in CANCHAS:
        shapes["canchas"].append({"nombre": name, "poly": g(poly)})
    shapes["parqueos"] = [list(apply(cal, p)) for p in PARQUEOS]
    shapes["agua"] = [list(apply(cal, p)) for p in HIDRATACION]
    e = ESTADIO; n = 48
    ring = []
    for i in range(n):
        t = 2 * math.pi * i / n; x, y = e["a"] * math.cos(t), e["b"] * math.sin(t)
        r = math.radians(e["rot"]); ring.append((e["cx"] + x * math.cos(r) - y * math.sin(r), e["cy"] + x * math.sin(r) + y * math.cos(r)))
    shapes["estadio"] = {"pista": g(ring)}
    r = math.radians(e["rot"]); hw, hh = e["campo"]
    campo = [(e["cx"] + x * math.cos(r) - y * math.sin(r), e["cy"] + x * math.sin(r) + y * math.cos(r))
             for x, y in [(-hw, -hh), (hw, -hh), (hw, hh), (-hw, hh)]]
    shapes["estadio"]["campo"] = g(campo)
    return shapes, cal, pairs


def render(shapes, nodes, png):
    from PIL import Image, ImageDraw, ImageFont
    allp = [p for k in ("bloques", "otros", "comida", "canchas") for s in shapes[k] for p in s["poly"]]
    la0, la1 = min(p[0] for p in allp) - 0.0004, max(p[0] for p in allp) + 0.0004
    lo0, lo1 = min(p[1] for p in allp) - 0.0004, max(p[1] for p in allp) + 0.0004
    kx = math.cos(math.radians(-0.952))
    W = 1500; S = W / ((lo1 - lo0) * kx); H = int((la1 - la0) * S)
    sc = 2
    img = Image.new("RGB", (W * sc, H * sc), "#eef1ec"); d = ImageDraw.Draw(img)
    tr = lambda p: (((p[1] - lo0) * kx) * S * sc, (la1 - p[0]) * S * sc)
    try:
        F = lambda s: ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", int(s * sc))
    except Exception:
        F = lambda s: ImageFont.load_default()
    # vías (aristas) bajo los edificios
    ar = list(csv.DictReader(open(ROOT / "app/src/main/assets/aristas.csv", encoding="utf-8")))
    nd = {r["id"]: (float(r["latitud"]), float(r["longitud"])) for r in csv.DictReader(open(ROOT / "app/src/main/assets/nodos.csv", encoding="utf-8"))}
    for a in ar:
        if a["id_origen"] in nd and a["id_destino"] in nd:
            d.line([tr(nd[a["id_origen"]]), tr(nd[a["id_destino"]])], fill="#d3d9d0", width=int(9 * sc))
    for a in ar:
        if a["id_origen"] in nd and a["id_destino"] in nd:
            d.line([tr(nd[a["id_origen"]]), tr(nd[a["id_destino"]])], fill="#b8c0b4", width=int(1.5 * sc))
    st = shapes["estadio"]
    d.polygon([tr(p) for p in st["pista"]], fill="#c9d9c2", outline="#9db393")
    d.polygon([tr(p) for p in st["campo"]], fill="#8fcb86", outline="#ffffff")
    d.text(tr(apply_center(st["campo"])), "Estadio\nUniversitario", fill="#25562a", font=F(12), anchor="mm", align="center")
    for s in shapes["canchas"]:
        d.polygon([tr(p) for p in s["poly"]], fill="#8ec9ec", outline="#ffffff")
    for s in shapes["otros"]:
        d.polygon([tr(p) for p in s["poly"]], fill=s["color"], outline="#ffffff")
        if s["etiqueta"] and len(s["etiqueta"]) <= 5:
            d.text(tr(poly_centroid(s["poly"])), s["etiqueta"], fill="#ffffff", font=F(9), anchor="mm")
    for s in shapes["comida"]:
        d.polygon([tr(p) for p in s["poly"]], fill="#f1d38a", outline="#ffffff")
    for s in shapes["bloques"]:
        d.polygon([tr(p) for p in s["poly"]], fill=s["color"], outline="#ffffff")
        if s["etiqueta"]:
            d.text(tr(poly_centroid(s["poly"])), s["etiqueta"], fill="#ffffff", font=F(11), anchor="mm")
    for p in shapes["parqueos"]:
        x, y = tr(p); r = 8 * sc
        d.rounded_rectangle([x - r, y - r, x + r, y + r], radius=3 * sc, fill="#1f6fd1"); d.text((x, y), "P", fill="#fff", font=F(10), anchor="mm")
    for p in shapes["agua"]:
        x, y = tr(p); r = 5 * sc; d.ellipse([x - r, y - r, x + r, y + r], fill="#27b3ee", outline="#fff")
    # nodos de entrada a lugares (verificación de calibración)
    for nid, p in nodes.items():
        if nid.startswith("2") and len(nid) == 3:
            x, y = tr(p); r = 2.5 * sc; d.ellipse([x - r, y - r, x + r, y + r], fill="#ffffff", outline="#222")
    img = img.resize((W, H), Image.LANCZOS); img.save(png); print("PNG", png, img.size)


def apply_center(poly):
    return poly_centroid(poly)


if __name__ == "__main__":
    _, nodes = load_nodes()
    shapes, cal, pairs = build(nodes)
    kx = 111320.0
    print("Residuos de calibración (m):")
    for (p, g), name in zip(pairs, ["P1", "P4", "P2", "P3"] + ["bloque"] * 99):
        la, lo = apply(cal, p); print(f"  {name:7s} {math.hypot((la - g[0]) * kx, (lo - g[1]) * kx * math.cos(math.radians(g[0]))):5.1f}")
    out = ROOT / "app/src/main/assets/campus_shapes.json"
    if "--json" in sys.argv:
        json.dump(shapes, open(out, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":")); print("JSON", out)
    render(shapes, nodes, sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else "preview_mapa.png")
