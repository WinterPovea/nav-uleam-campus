# -*- coding: utf-8 -*-
"""OBSOLETO: las figuras ahora salen de OpenStreetMap con build_shapes_osm.py (este script sobrescribe ese resultado).
Genera app/src/main/assets/campus_shapes.csv (capa vectorial del mapa del campus, solo diseno).

Los contornos se trazaron sobre la foto del mapa fisico (1200x1600 px). Se calibran con los nodos reales
(data/nodos_u.csv) con una semejanza (giro + escala + traslacion) refinada por ICP: cada entrada de bloque
se acerca al borde mas cercano de su edificio. Ajustes de diseno pedidos (B-20/B-21 invertidos en el mapa,
Tasty Food 1 entre sus dos entradas, I-09 y B-23 desplazados) se aplican en metros tras la calibracion.
Uso: python3 build_shapes.py   (requiere numpy)
"""
import csv, math, os, re
import numpy as np

ROOT = os.path.dirname(os.path.abspath(__file__))
LA0, LO0 = -0.9525, -80.745
KLAT = 111320.0
KLON = KLAT * math.cos(math.radians(0.9525))

# Trazado sobre la foto 1200x1600 (mapa fisico, version detallada)
E=[]
def b(c,pts): E.append(('EDIFICIO',c,pts))
b('B-01',[(639,435),(676,384),(761,444),(739,484),(702,469),(697,457)])
b('B-02',[(650,475),(665,467),(716,505),(694,544),(637,504)])
b('B-02',[(623,479),(645,456),(657,469),(635,497)])
b('B-03',[(627,512),(650,530),(667,537),(680,550),(662,580),(642,555),(634,530)])
b('B-04',[(851,326),(869,319),(942,379),(900,440),(870,475),(834,434),(847,422),(852,397)])
b('B-05',[(955,457),(1000,435),(1027,490),(970,522)])
b('B-05',[(1015,455),(1042,442),(1065,435),(1100,440),(1090,480),(1043,488)])
b('B-06',[(757,717),(802,661),(874,716),(806,777),(774,747)])
b('B-07',[(809,796),(860,759),(890,735),(922,772),(930,790),(920,810),(872,860),(827,835),(812,812)])
b('B-07',[(825,834),(867,860),(882,872),(875,892),(861,895),(845,880)])
b('B-08',[(931,880),(981,901),(902,1074),(856,1050)])
b('B-09',[(889,1114),(916,1107),(931,1135),(939,1202),(915,1197),(906,1182),(895,1165),(890,1150)])
b('B-10',[(891,1361),(920,1349),(952,1365),(969,1382),(942,1404)])
b('B-11',[(939,1412),(970,1407),(977,1467),(952,1462),(955,1452),(940,1440)])
b('B-11',[(910,1445),(945,1442),(945,1472),(910,1472)])
b('B-12',[(867,1442),(882,1432),(902,1435),(907,1472),(872,1467)])
b('B-13',[(836,1236),(872,1209),(925,1260),(930,1277),(895,1300),(837,1336),(805,1304),(830,1280),(850,1260)])
b('B-14',[(755,1232),(785,1210),(795,1215),(810,1225),(825,1240),(835,1267),(805,1297),(785,1300),(772,1272),(757,1250)])
b('B-15',[(773,1134),(812,1111),(852,1137),(822,1186),(802,1186)])
b('B-16',[(481,970),(500,945),(520,935),(550,965),(580,995),(585,1015),(555,1040),(522,1020),(510,995),(495,987)])
b('B-17',[(324,995),(345,970),(393,990),(381,1017),(405,1035),(392,1060),(370,1058),(335,1030)])
b('B-18',[(362,1087),(390,1072),(430,1067),(442,1077),(440,1115),(395,1125),(385,1107),(367,1097)])
b('B-19',[(387,1140),(432,1135),(442,1215),(400,1220)])
b('B-22',[(341,706),(451,676),(467,720),(430,727),(408,712),(406,760),(386,761),(382,730),(362,722)])
b('B-23',[(325,202),(350,210),(367,232),(362,255),(350,280),(312,247)])
b('B-23',[(265,271),(295,284),(329,294),(322,307),(282,302)])
b('B-24',[(269,1105),(296,1103),(297,1135),(316,1145),(320,1222),(300,1224),(293,1200),(274,1205),(272,1147),(269,1140)])
b('B-25',[(335,1287),(350,1282),(365,1287),(412,1275),(415,1295),(397,1300),(380,1312),(360,1310),(350,1300),(335,1297)])
O=[]
def o(c,pts,t='OTRO'): O.append((t,c,pts))
o('A-01',[(342,372),(425,332),(470,437),(395,472)])
o('I-09',[(416,476),(474,450),(495,482),(486,496),(465,496),(456,512),(434,505)])
o('K-11',[(291,436),(312,431),(326,467),(312,475)])
o('C-03',[(747,325),(795,275),(847,325),(800,392),(777,372)])
o('CIEC',[(842,138),(1097,393),(1033,457),(778,202)])
o('D-04',[(217,315),(245,305),(272,325),(280,347),(250,365),(232,350),(220,330)])
o('E-05',[(510,564),(549,562),(552,585),(595,597),(585,620),(555,630),(522,612)])
o('F-06',[(680,620),(737,547),(805,607),(762,665)])
o('J-10',[(900,500),(940,475),(962,470),(962,492),(940,490),(937,507),(915,510)])
o('I-12',[(1090,570),(1115,560),(1122,590),(1100,605)])
o('M-13',[(425,912),(457,885),(482,900),(472,925),(450,930)])
o('N-14',[(300,880),(322,870),(332,885),(315,902),(302,895)])
o('N-14',[(335,870),(357,862),(370,877),(350,890)])
o('N-14',[(365,870),(390,877),(407,885),(397,900),(375,890)])
o('',[(350,895),(375,887),(387,902),(377,920),(360,912)])
o('Ñ-15',[(405,1015),(425,1000),(450,1015),(445,1040),(425,1050),(407,1040)])
o('H-08',[(455,1077),(472,1057),(487,1067),(470,1087)])
o('G-07',[(515,1120),(527,1115),(565,1147),(557,1162),(545,1165),(512,1135)])
o('I-09',[(447,1130),(462,1122),(477,1132),(500,1147),(515,1165),(510,1185),(495,1192),(477,1182),(470,1157),(450,1147),(440,1140)])
o('O-16',[(207,1455),(232,1452),(242,1470),(232,1490),(207,1485)])
o('JUAN MONTALVO ANTIGUO',[(580,1492),(607,1470),(645,1465),(650,1500)])
o('JUAN MONTALVO NUEVO',[(695,1500),(712,1472),(750,1462),(755,1500)])
o('EP ULEAM',[(764,1447),(820,1440),(823,1465),(767,1470)])
o('Canchas multiples',[(978,590),(1005,520),(1115,478),(1140,545)],'CANCHA')
o('Canchas multiples',[(650,1425),(735,1362),(800,1405),(715,1462)],'CANCHA')
o('Cancha',[(370,642),(437,632),(440,672),(375,675)],'CANCHA')
o('Cancha cesped',[(320,1437),(367,1435),(362,1500),(312,1497)],'CANCHA_VERDE')
o('Tasty Food 2',[(845,1065),(862,1060),(900,1085),(902,1100),(885,1117),(870,1112),(850,1100)],'COMIDA')
o('Tasty Food 3',[(767,1470),(823,1465),(827,1487),(772,1490)],'COMIDA')
o('Tasty Food 4',[(322,1410),(377,1405),(375,1420),(335,1425)],'COMIDA')
o('Gradas',[(687,715),(712,707),(795,805),(772,820)],'GRADA')
o('Plaza Cultural',[(446,350),(500,342),(572,485),(480,448)],'PLAZA')
PITCH=[(547,812),(639,722),(795,882),(697,967)]
PARK=[(692,250),(445,315),(252,532),(770,540),(952,630),(252,742),(405,840),(487,872),(575,1047),(375,1252),(272,1310),(730,1322),(850,1462)]
WATER=[(297,325),(717,395),(610,515),(952,535),(857,690),(900,925),(575,987),(320,1027),(782,1132),(935,1345),(632,1437),(672,1482)]
# Vias decorativas trazadas sobre la foto (ancho en px de foto, polilinea). Es solo diseno: NO son las aristas del grafo.
ROADS=[
 (45,[(175,410),(260,382),(330,352),(400,322),(470,298),(540,268),(605,278)]),
 (50,[(520,505),(530,570),(560,650),(600,715)]),
 (42,[(605,278),(660,300),(720,335),(780,385),(830,450),(870,530),(895,610),(925,680),(955,735),(945,800),(925,870),(885,935),(860,1000),(840,1060),(835,1100)]),
 (30,[(830,450),(890,480),(960,478),(1020,455)]),
 (36,[(835,1100),(800,1150),(745,1190),(722,1250),(740,1310),(800,1340),(880,1345),(950,1320),(985,1285)]),
 (42,[(235,885),(250,840),(310,805),(400,795),(470,820),(520,860),(570,900),(620,945),(680,1000),(740,1050),(790,1080),(835,1100)]),
 (34,[(230,925),(300,952),(400,987),(500,1030),(580,1062),(650,1068),(700,1040)]),
 (40,[(215,880),(240,960),(275,1050),(310,1130),(332,1200),(320,1270),(300,1340),(280,1410),(262,1490)]),
 (38,[(545,715),(480,760),(400,787),(320,797),(250,822),(215,865)]),
]
# Entrada (nodo DESTINO) de cada edificio que no es bloque: la huella se acerca a su nodo
OTRO_NODE={'Agromarket':315,'A-01':309,'I-09':[308,319],'C-03':300,'CIEC':301,'K-11':310,'F-06':306,'E-05':302,'J-10':230,'M-13':324,
 'N-14':303,'Ñ-15':328,'G-07':320,'I-12':317,'EP ULEAM':323,'Tasty Food 2':326,'Tasty Food 3':327,'Tasty Food 4':321,
 'Cancha cesped':322,'Canchas multiples':304}

# ======================= Referencia OpenStreetMap (665x725 px, norte arriba) =======================
# Huellas trazadas sobre la captura de OSM (mismas dimensiones reales que el campus). Tienen prioridad sobre las de la foto.
OSM_B=[
 ('B-01',[(180,545),(215,538),(228,570),(195,588)]),
 ('B-02',[(216,535),(245,530),(250,555),(225,565)]),
 ('B-03',[(210,502),(250,496),(258,520),(225,530)]),
 ('B-04',[(90,545),(125,522),(140,545),(128,580),(100,585)]),
 ('B-05',[(18,495),(46,492),(47,512),(20,513)]),
 ('B-05',[(54,490),(92,486),(95,520),(58,522)]),
 ('B-06',[(168,403),(200,398),(212,425),(185,440),(168,430)]),
 ('B-07',[(146,330),(185,320),(198,345),(190,380),(150,385)]),
 ('B-08',[(205,210),(222,218),(160,292),(140,280)]),
 ('B-09',[(162,150),(185,148),(198,205),(172,215)]),
 ('B-10',[(222,48),(262,42),(268,68),(235,75),(222,65)]),
 ('B-11',[(215,3),(262,3),(262,40),(228,48),(215,40)]),
 ('B-13',[(224,98),(283,88),(290,120),(268,138),(222,128)]),
 ('B-14',[(263,125),(305,118),(315,165),(275,170)]),
 ('B-15',[(240,180),(275,175),(285,200),(255,208)]),
 ('B-16',[(353,295),(385,288),(392,320),(380,345),(358,340)]),
 ('B-17',[(462,325),(490,320),(497,352),(470,365)]),
 ('B-18',[(434,278),(462,271),(470,298),(445,308)]),
 ('B-19',[(455,238),(480,230),(487,262),(458,268)]),
 ('B-20',[(495,155),(530,148),(540,175),(505,185)]),
 ('B-21',[(582,148),(612,143),(620,170),(592,178)]),
 ('B-22',[(358,468),(398,466),(415,488),(412,515),(362,518)]),
 ('B-23',[(325,690),(360,685),(365,715),(330,722)]),
 ('B-24',[(498,240),(528,236),(530,300),(508,322),(498,300)]),
 ('B-25',[(498,188),(530,184),(535,205),(503,210)]),
]
OSM_O=[
 ('OTRO','A-01',[(316,600),(358,596),(366,640),(325,652)]),
 ('OTRO','I-09',[(310,565),(340,560),(342,588),(312,592)]),
 ('OTRO','I-09',[(423,232),(445,225),(455,250),(432,258)]),
 ('OTRO','K-11',[(374,613),(398,611),(400,634),(377,636)]),
 ('OTRO','C-03',[(122,585),(150,568),(162,598),(140,620),(120,610)]),
 ('OTRO','CIEC',[(38,555),(70,540),(158,655),(150,700),(125,692)]),
 ('OTRO','F-06',[(180,466),(234,461),(238,494),(186,500)]),
 ('OTRO','E-05',[(270,490),(295,484),(302,508),(280,520)]),
 ('OTRO','J-10',[(98,476),(128,473),(130,494),(100,496)]),
 ('OTRO','M-13',[(383,368),(402,362),(407,385),(388,390)]),
 ('OTRO','N-14',[(412,392),(440,383),(455,395),(445,414),(418,413)]),
 ('OTRO','Ñ-15',[(427,312),(448,305),(460,330),(440,340)]),
 ('OTRO','G-07',[(386,232),(402,226),(410,248),(392,254)]),
 ('OTRO','I-12',[(40,408),(68,405),(72,432),(44,437)]),
 ('OTRO','Agromarket',[(316,302),(334,298),(338,316),(320,320)]),
 ('OTRO','EP ULEAM',[(312,58),(378,52),(382,95),(318,100)]),
 ('OTRO','JUAN MONTALVO ANTIGUO',[(300,8),(335,8),(338,42),(302,42)]),
 ('OTRO','JUAN MONTALVO NUEVO',[(398,57),(432,50),(445,90),(405,100)]),
 ('CANCHA_VERDE','Cancha cesped',[(557,75),(582,91),(553,130),(534,111)]),
 ('CANCHA','Canchas multiples',[(14,445),(50,443),(52,487),(16,488)]),
 ('CANCHA','Canchas multiples',[(52,443),(90,440),(91,478),(53,480)]),
 ('PLAZA','Plaza Cultural',[(266,558),(300,558),(302,655),(268,655)]),
 ('GRADA','Gradas',[(221,384),(237,382),(251,421),(237,426)]),
]
OSM_STADIUM=dict(c=(285.0,375.0),u=(0.386,0.922),hl=66.0,hw=38.0,track_hl=97.0,track_hw=62.0)
OSM_PARK=[(271,61),(321,135),(485,211),(341,297),(395,278),(370,376),(403,414),(445,485),(169,500),(311,626),(405,591),(170,655)]
OSM_ROADS=[  # (ancho en metros, polilinea en px de OSM)
 (6,[(268,97),(300,108),(320,140),(318,168),(298,190),(268,215),(240,235)]),
 (6,[(240,236),(215,262),(185,288),(150,310),(132,350),(127,400),(135,450),(160,480),(200,500)]),
 (6,[(305,292),(360,292),(405,310),(440,345),(472,395),(492,425),(496,440)]),
 (6,[(165,470),(220,490),(290,492),(340,480),(400,455),(450,440),(496,436)]),
 (6,[(535,203),(515,228),(502,255),(500,300),(498,360),(496,436),(498,470)]),
 (5,[(590,112),(560,140),(530,175),(512,205)]),
 (5,[(180,505),(165,540),(175,590),(210,630),(245,660),(255,690),(262,725)]),
 (5,[(250,655),(300,665),(350,672),(400,660),(440,655),(470,650)]),
 (5,[(190,92),(250,82),(300,70),(340,62),(400,50),(435,40),(470,25)]),
 (5,[(235,232),(205,205),(195,150),(190,100)]),
 (5,[(240,236),(262,248),(300,270),(318,292)]),
 (5,[(318,292),(360,285),(395,278),(440,268),(498,265)]),
 (4,[(395,330),(368,350),(362,375),(380,400),(440,420),(495,425)]),
 (4,[(205,300),(212,350),(220,400),(230,450),(245,470)]),
 (4,[(130,392),(170,390),(210,385)]),
 (5,[(75,530),(110,527),(150,520),(175,505)]),
 (4,[(338,480),(335,560),(330,600)]),
 (5,[(345,525),(400,525),(440,520),(470,530)]),
 (5,[(470,480),(465,560),(460,650)]),
]
# puntas de las flechas amarillas de ingreso -> nodos 100..103
GATES={100:(595,287),103:(192,420),101:(227,877),102:(937,1274)}


# ---------------------------------------------------------------- geometria auxiliar
def rrect(cx, cy, ux, uy, hl, hw, r, n=10):
    """Rectangulo redondeado (eje largo ux,uy; semiejes hl,hw; radio r) como poligono."""
    vx, vy = -uy, ux
    pts = []
    for sl, sw in [(1, 1), (-1, 1), (-1, -1), (1, -1)]:     # esquinas en sentido antihorario
        ccx, ccy = sl * (hl - r), sw * (hw - r)
        base = math.atan2(sw, sl) - math.pi / 4
        pts += [(ccx + r * math.cos(base + k * math.pi / 2 / n), ccy + r * math.sin(base + k * math.pi / 2 / n))
                for k in range(n + 1)]
    return [(cx + l * ux + w * vx, cy + l * uy + w * vy) for l, w in pts]


def seg_dist(p, a, b):
    ab = b - a
    t = max(0.0, min(1.0, np.dot(p - a, ab) / max(np.dot(ab, ab), 1e-9)))
    q = a + t * ab
    return q, float(np.linalg.norm(p - q))


def nearest_on(polys, p):
    best, bd = None, 1e18
    for poly in polys:
        P = np.array(poly, float)
        for i in range(len(P)):
            q, d = seg_dist(p, P[i], P[(i + 1) % len(P)])
            if d < bd: best, bd = q, d
    return best, bd


def load_nodes():
    nodes = {}
    with open(os.path.join(ROOT, 'data', 'nodos_u.csv'), encoding='utf-8', errors='replace', newline='') as f:
        for r in csv.DictReader(f):
            nodes[int(r['ID'])] = (r['Descri'], float(r['Latitud']), float(r['Longitud']))
    return nodes


def to_m(la, lo): return ((lo - LO0) * KLON, (la - LA0) * KLAT)
def to_ll(x, y): return (LA0 + y / KLAT, LO0 + x / KLON)


class Sim:
    """Semejanza foto -> metros: X = a*px - b*qy + tx ; Y = b*px + a*qy + ty (qy = -py)."""
    def __init__(self, s): self.s = s
    def fwd(self, p):
        a, b, tx, ty = self.s; qy = -p[1]
        return np.array([a * p[0] - b * qy + tx, b * p[0] + a * qy + ty])
    def inv(self, g):
        a, b, tx, ty = self.s; d = a * a + b * b
        X, Y = g[0] - tx, g[1] - ty
        return np.array([(a * X + b * Y) / d, -((-b * X + a * Y) / d)])

def fit_sim(P, G, W):
    M, y = [], []
    for (px, py), (gx, gy), w in zip(P, G, W):
        qy = -py
        M.append([w * px, -w * qy, w, 0]); y.append(w * gx)
        M.append([w * qy, w * px, 0, w]); y.append(w * gy)
    return Sim(np.linalg.lstsq(np.array(M), np.array(y), rcond=None)[0])


def calibrate(nodes):
    byblock = {}
    for t, c, p in E:
        if t == 'EDIFICIO': byblock.setdefault(c, []).append(p)
    # B-20/B-21: los nodos 220/221 siguen la rotulacion fisica del mapa (ver BLOQUE_REAL en build_lugares.py)
    ctrl = {}  # nodo -> codigo de bloque del edificio donde esta fisicamente
    for nid, (d, la, lo) in nodes.items():
        m = re.search(r'Entrada Bloque B-(\d+)$', d.strip())
        if m:
            k = 'B-%02d' % int(m.group(1))
            ctrl[nid] = k
    P, G, W = [], [], []
    for nid, k in ctrl.items():
        if k in byblock:
            P.append(np.mean([np.mean(p, axis=0) for p in byblock[k]], axis=0))
            G.append(to_m(*nodes[nid][1:])); W.append(1.0)
    for g, pt in GATES.items():
        P.append(np.array(pt, float)); G.append(to_m(*nodes[g][1:])); W.append(3.0)
    sim = fit_sim(P, G, W)
    for _ in range(12):  # ICP: cada entrada -> borde mas cercano de su edificio
        P2, G2, W2 = [], [], []
        for nid, k in ctrl.items():
            if k not in byblock: continue
            q, _d = nearest_on(byblock[k], sim.inv(np.array(to_m(*nodes[nid][1:]))))
            P2.append(q); G2.append(to_m(*nodes[nid][1:])); W2.append(1.0)
        for g, pt in GATES.items():
            P2.append(np.array(pt, float)); G2.append(to_m(*nodes[g][1:])); W2.append(3.0)
        sim = fit_sim(P2, G2, W2)
    errs = []
    for nid, k in ctrl.items():
        if k in byblock:
            q, d = nearest_on(byblock[k], sim.inv(np.array(to_m(*nodes[nid][1:])))); errs.append(d * np.hypot(*sim.s[:2]))
    print('distancia entrada-edificio: media %.1f m, max %.1f m  | %.3f m/px' % (np.mean(errs), max(errs), np.hypot(*sim.s[:2])))
    return sim


def pt_in_poly(p, poly):
    x, y = p; ins = False; n = len(poly)
    for i in range(n):
        x1, y1 = poly[i]; x2, y2 = poly[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1: ins = not ins
    return ins


def build_osm(nodes):
    """Calibra la captura de OSM con los nodos (semejanza + ICP) y devuelve (figuras geograficas, codigos, parqueos, calles, sim)."""
    shapes = [['EDIFICIO', c, [tuple(map(float, q)) for q in p]] for c, p in OSM_B]
    shapes += [[t, c, [tuple(map(float, q)) for q in p]] for t, c, p in OSM_O]
    ctrl = []   # (codigo, nodo)
    for nid, (d, la, lo) in nodes.items():
        m = re.search(r'Entrada Bloque B-(\d+)$', d.strip())
        if m and 200 <= nid < 300: ctrl.append(('B-%02d' % int(m.group(1)), nid))
    for code, ids in OTRO_NODE.items():
        for nid in (ids if isinstance(ids, list) else [ids]): ctrl.append((code, nid))
    polys = lambda code: [sh[2] for sh in shapes if sh[1] == code]
    ctrl = [(c, n) for c, n in ctrl if polys(c)]
    def nearest_poly(code, g):
        best = None
        for P in polys(code):
            q, d = nearest_on([P], g)
            if best is None or d < best[1]: best = (q, d)
        return best
    P0 = [np.mean([np.mean(P, axis=0) for P in polys(c)], axis=0) for c, n in ctrl]
    G0 = [to_m(*nodes[n][1:]) for c, n in ctrl]
    sim = fit_sim(P0, G0, [1.0] * len(P0))
    for _ in range(15):
        P2 = [nearest_poly(c, sim.inv(np.array(to_m(*nodes[n][1:]))))[0] for c, n in ctrl]
        sim = fit_sim(P2, G0, [1.0] * len(P2))
    errs = [nearest_poly(c, sim.inv(np.array(to_m(*nodes[n][1:]))))[1] * np.hypot(*sim.s[:2]) for c, n in ctrl]
    print('OSM: distancia entrada-edificio media %.1f m, max %.1f m | %.3f m/px' % (np.mean(errs), max(errs), np.hypot(*sim.s[:2])))
    st = OSM_STADIUM; c = np.array(st['c']); u = np.array(st['u']); v = np.array([-u[1], u[0]])
    shapes.append(['PISTA', 'Estadio', rrect(c[0], c[1], u[0], u[1], st['track_hl'], st['track_hw'], 58)])
    shapes.append(['PISTA_INT', 'Estadio', rrect(c[0], c[1], u[0], u[1], st['track_hl'] - 9, st['track_hw'] - 9, 50)])
    pitch = [c - st['hl'] * u - st['hw'] * v, c - st['hl'] * u + st['hw'] * v, c + st['hl'] * u + st['hw'] * v, c + st['hl'] * u - st['hw'] * v]
    shapes.append(['CANCHA_ESTADIO', 'Estadio', [tuple(map(float, q)) for q in pitch]])
    geo = [[t, c_, [to_ll(*sim.fwd(np.array(q))) for q in p]] for t, c_, p in shapes]
    park = [to_ll(*sim.fwd(np.array(p, float))) for p in OSM_PARK]
    k = float(np.hypot(*sim.s[:2]))
    roads = [(w, [to_ll(*sim.fwd(np.array(q, float))) for q in pts]) for w, pts in OSM_ROADS]
    ring = rrect(c[0], c[1], u[0], u[1], st['track_hl'] + 16, st['track_hw'] + 16, 70)
    roads.append((6, [to_ll(*sim.fwd(np.array(q, float))) for q in ring + [ring[0]]]))
    return geo, {sh[1] for sh in shapes}, park, roads


def main():
    nodes = load_nodes(); sim = calibrate(nodes)
    # ---- ajustes en espacio de foto (px) ----
    shapes = []   # (tipo, codigo, [(x,y) px])
    for t, c, p in E:
        shapes.append([t, c, [tuple(map(float, q)) for q in p]])
    for t, c, p in O:
        shapes.append([t, c, [tuple(map(float, q)) for q in p]])
    # A-01 e I-09 (junto a la Plaza Cultural): se separan un poco. A-01 sigue conteniendo la entrada del Rectorado (309)
    # e I-09 pasa a contener la del Departamento de Bienestar (308), que antes quedaba en el hueco entre ambos.
    n308 = sim.inv(np.array(to_m(*nodes[308][1:]))); n309 = sim.inv(np.array(to_m(*nodes[309][1:])))
    for s_ in shapes:
        if s_[1] == 'A-01': s_[2] = [(x - 10, y - 6) for x, y in s_[2]]; print('A-01 contiene al nodo 309 (Rectorado):', pt_in_poly(n309, s_[2]))
        if s_[1] == 'I-09' and s_[0] == 'OTRO' and s_[2][0][1] < 600:
            s_[2] = [(x + 2, y - 14) for x, y in s_[2]]; print('I-09 contiene al nodo 308 (Bienestar):', pt_in_poly(n308, s_[2]))
    # Estadio: pista (anillo), borde interior de pista y cancha con marcas
    pc = np.mean(PITCH, axis=0); u = np.array(PITCH[1], float) - np.array(PITCH[0]) if False else np.array(PITCH[2], float) - np.array(PITCH[1])
    L = np.linalg.norm(u); u = u / L
    hl, hw = L / 2, np.linalg.norm(np.array(PITCH[1], float) - np.array(PITCH[0])) / 2
    shapes.append(['PISTA', 'Estadio', rrect(pc[0], pc[1], u[0], u[1], hl + 30, hw + 30, 80)])
    shapes.append(['PISTA_INT', 'Estadio', rrect(pc[0], pc[1], u[0], u[1], hl + 12, hw + 12, 62)])
    shapes.append(['CANCHA_ESTADIO', 'Estadio', [tuple(map(float, q)) for q in PITCH]])
    # ---- a geografia ----
    out = []
    for t, c, p in shapes:
        out.append([t, c, [to_ll(*sim.fwd(np.array(q))) for q in p]])
    # Referencia OSM: sus huellas, calles, estadio y parqueos sustituyen a las trazadas sobre la foto
    osm_geo, osm_codes, osm_park, osm_roads = build_osm(nodes)
    out = [o for o in out if o[1] not in osm_codes] + osm_geo
    # Cada edificio se acerca a su nodo de entrada (DESTINO): la huella debe tocar el nodo, sin saltos grandes (<= 16 m)
    def snap(code, nid, tipos):
        polys = [o for o in out if o[1] == code and o[0] in tipos]
        if not polys: return
        n = np.array(to_m(*nodes[nid][1:]))
        best = min(polys, key=lambda o: nearest_on([[to_m(*q) for q in o[2]]], n)[1])
        target = [best] if code not in BLOQUES_MULTI else polys
        q, d = nearest_on([[to_m(*pt) for pt in o[2]] for o in target], n)
        inside = any(pt_in_poly(n, [to_m(*pt) for pt in o[2]]) for o in target)
        if inside or d <= 1.5: return
        step = min(d - 1.0, 10.0); v = (n - q) / d * step
        for o in target: o[2] = [to_ll(*(np.array(to_m(*pt)) + v)) for pt in o[2]]
        print('  %-18s nodo %d: acercado %.1f m (estaba a %.1f m)' % (code, nid, step, d))
    BLOQUES_MULTI = {'B-02', 'B-05', 'B-07', 'B-11', 'B-23'}
    for nid, (d_, la, lo) in nodes.items():
        m = re.search(r'Entrada Bloque B-(\d+)$', d_.strip())
        if m and 200 <= nid < 300:
            code = 'B-%02d' % int(m.group(1))
            snap(code, nid, ('EDIFICIO',))
    for code, ids in OTRO_NODE.items():
        for nid in (ids if isinstance(ids, list) else [ids]):
            snap(code, nid, ('OTRO', 'CANCHA', 'COMIDA', 'CANCHA_VERDE'))
    # Tasty Food 1: un solo edificio entre sus dos entradas (305 y 318)
    a = np.array(to_m(*nodes[305][1:])); b = np.array(to_m(*nodes[318][1:])); mid = (a + b) / 2
    hy = abs(a[1] - b[1]) / 2 - 2.0; hx = 6.5
    tasty = [(mid[0] - hx, mid[1] - hy), (mid[0] + hx, mid[1] - hy), (mid[0] + hx, mid[1] + hy), (mid[0] - hx, mid[1] + hy)]
    out.append(['COMIDA', 'Tasty Food 1', [to_ll(*q) for q in tasty]])
    for w, pts in osm_roads:
        out.append(['VIA', '%.1f' % w, pts])
    for g in osm_park: out.append(['PARQUEO', 'P', [g]])
    # Ajustes finos pedidos (metros este, norte) despues de pegar cada edificio a su nodo
    MANUAL_SHIFT_M = {'B-06': (0.0, 8.0)}   # B-06 queda junto a B-07, como en OSM
    for o in out:
        if o[1] in MANUAL_SHIFT_M and o[0] == 'EDIFICIO':
            dv = np.array(MANUAL_SHIFT_M[o[1]])
            o[2] = [to_ll(*(np.array(to_m(*pt)) + dv)) for pt in o[2]]
    # Vicerrectorado (I-12/L-12): edificio al oeste de su entrada (nodo 317), sin tapar a Tasty Food 1
    for o in out:
        if o[1] == 'I-12':
            o[2] = [(-0.952985, -80.747565), (-0.952985, -80.747405), (-0.953150, -80.747405), (-0.953150, -80.747565)]
    # Iconos de cafeteria en lugares sin huella propia (El bosque)
    for nid in (312,):
        out.append(['CAFE', nodes[nid][0].strip(), [nodes[nid][1:]]])
    for p in WATER: out.append(['AGUA', 'Agua', [to_ll(*sim.fwd(np.array(p, float)))]])
    path = os.path.join(ROOT, 'app/src/main/assets/campus_shapes.csv')
    with open(path, 'w', encoding='utf-8', newline='') as f:
        w = csv.writer(f, lineterminator='\n'); w.writerow(['tipo', 'codigo', 'grupo', 'puntos'])
        grp = grupos_por_bloque()
        for t, c, p in out:
            g = grp.get(c, 'OTRO') if t == 'EDIFICIO' else 'OTRO'
            w.writerow([t, c, g, ' '.join('%.6f %.6f' % q for q in p)])
    print(len(out), 'figuras ->', path)


def grupos_por_bloque():
    res = {}
    with open(os.path.join(ROOT, 'app/src/main/assets/lugares.csv'), encoding='utf-8', newline='') as f:
        for r in csv.DictReader(f):
            m = re.match(r'([A-Z]+)·(B-\d+)', r['codigo'] or '')
            if m: res[m.group(2)] = m.group(1)
    return res


if __name__ == '__main__':
    main()
