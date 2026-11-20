# -*- coding: utf-8 -*-
"""Genera app/src/main/assets/campus_shapes.csv (huellas de edificios del campus, solo diseno)
y docs/mapa_preview.png a partir de coordenadas de la foto del mapa fisico (PHOTO_*), calibradas
con los nodos reales (nodos_u.csv) mediante una transformacion afin (minimos cuadrados).
Uso: python build_shapes.py   (requiere numpy y Pillow solo para la vista previa)"""
import csv, re, math, os
import numpy as np

ROOT = os.path.dirname(os.path.abspath(__file__))
LA0, LO0 = -0.9525, -80.745
KLAT, KLON = 111320.0, 111320.0 * math.cos(math.radians(0.9525))

# ---- Puntos de control: (px,py) en la foto  ->  nodo real ----
CTRL_BLOCKS = {'B-01': (405, 550), 'B-02': (395, 586), 'B-03': (382, 613), 'B-04': (513, 531),
               'B-06': (474, 710), 'B-07': (508, 757), 'B-08': (535, 858), 'B-09': (535, 960),
               'B-10': (547, 1088), 'B-11': (565, 1138), 'B-12': (522, 1138), 'B-13': (508, 1017),
               'B-14': (465, 1020), 'B-15': (478, 958), 'B-16': (308, 858), 'B-17': (210, 880),
               'B-18': (242, 935), 'B-19': (245, 980), 'B-20': (222, 1081), 'B-21': (146, 1127),
               'B-22': (237, 704), 'B-24': (172, 975), 'B-25': (222, 1048)}

# ---- Huellas en pixeles de la foto: (tipo, codigo, grupo, [(x,y)...]) ----
S = []
def poly(t, c, g, pts): S.append((t, c, g, pts))
poly('EDIFICIO', 'A-01', 'ADMIN_C', [(199,515),(245,491),(274,545),(227,570)])
poly('EDIFICIO', 'B-01', '', [(372,544),(397,519),(441,552),(420,577)])
poly('EDIFICIO', 'B-02', '', [(374,576),(390,567),(418,590),(402,609)])
poly('EDIFICIO', 'B-03', '', [(367,600),(385,607),(399,622),(385,630)])
poly('EDIFICIO', 'B-04', '', [(490,505),(510,482),(545,505),(540,540),(512,570),(500,567)])
poly('EDIFICIO', 'B-05', '', [(555,567),(570,552),(595,570),(582,597),(560,590)])
poly('EDIFICIO', 'B-05', '', [(590,555),(602,542),(635,545),(625,572),(592,570)])
poly('EDIFICIO', 'B-06', '', [(442,707),(479,677),(502,710),(475,742)])
poly('EDIFICIO', 'B-07', '', [(474,757),(497,722),(540,747),(530,780),(505,791)])
poly('EDIFICIO', 'B-07', '', [(482,777),(495,772),(515,805),(500,812)])
poly('EDIFICIO', 'B-08', '', [(512,900),(550,805),(570,810),(532,912)])
poly('EDIFICIO', 'B-09', '', [(522,935),(535,934),(550,985),(537,992)])
poly('EDIFICIO', 'B-10', '', [(525,1080),(542,1072),(560,1087),(567,1092),(545,1107),(532,1097)])
poly('EDIFICIO', 'B-11', '', [(551,1110),(569,1107),(574,1140),(560,1145)])
poly('EDIFICIO', 'B-11', '', [(535,1131),(555,1129),(556,1147),(537,1147)])
poly('EDIFICIO', 'B-12', '', [(512,1130),(535,1125),(537,1147),(515,1150)])
poly('EDIFICIO', 'B-13', '', [(475,1047),(497,1007),(510,992),(540,1017),(532,1047),(502,1067)])
poly('EDIFICIO', 'B-14', '', [(445,1005),(460,994),(490,1017),(485,1031),(470,1047),(452,1048)])
poly('EDIFICIO', 'B-15', '', [(457,957),(482,937),(500,957),(475,974)])
poly('EDIFICIO', 'B-16', '', [(285,855),(310,840),(317,852),(345,857),(347,877),(330,897),(302,882),(290,872)])
poly('EDIFICIO', 'B-17', '', [(192,865),(207,860),(232,872),(232,895),(227,910),(212,905),(202,882)])
poly('EDIFICIO', 'B-18', '', [(217,922),(229,919),(256,917),(260,935),(256,947),(230,947),(217,935)])
poly('EDIFICIO', 'B-19', '', [(230,957),(256,955),(262,1002),(236,1006)])
poly('EDIFICIO', 'B-20', '', [(192,1075),(250,1070),(252,1087),(202,1102),(195,1102)])
poly('EDIFICIO', 'B-21', '', [(129,1110),(162,1112),(160,1135),(147,1145),(132,1135)])
poly('EDIFICIO', 'B-22', '', [(200,705),(265,687),(275,710),(237,737),(227,730),(212,715)])
poly('EDIFICIO', 'B-23', '', [(184,417),(190,412),(200,415),(212,430),(205,457),(190,447)])
poly('EDIFICIO', 'B-23', '', [(152,455),(165,454),(190,465),(182,480),(165,477)])
poly('EDIFICIO', 'B-24', '', [(157,940),(174,939),(185,965),(189,985),(187,1007),(165,1002),(161,980)])
poly('EDIFICIO', 'B-25', '', [(199,1045),(212,1041),(242,1038),(244,1050),(212,1055),(200,1052)])
# Otros edificios (gris): codigo de la leyenda
poly('OTRO', 'C-03', '', [(431,480),(465,457),(492,480),(480,510),(460,525)])
poly('OTRO', 'CIEC', '', [(444,415),(470,382),(625,522),(605,533)])
poly('OTRO', 'D-04', '', [(132,480),(147,470),(157,477),(167,487),(157,505),(142,507)])
poly('OTRO', 'E-05', '', [(300,625),(320,622),(342,640),(357,652),(335,670),(312,660),(300,650)])
poly('OTRO', 'F-06', '', [(401,650),(435,610),(474,640),(455,677),(424,675)])
poly('OTRO', 'I-09', '', [(242,572),(272,555),(290,577),(260,592)])
poly('OTRO', 'I-09', '', [(260,957),(275,950),(290,962),(295,982),(285,987),(277,972),(262,965)])
poly('OTRO', 'J-10', '', [(519,585),(555,566),(557,585),(532,592)])
poly('OTRO', 'K-11', '', [(169,545),(182,555),(190,570),(177,567)])
poly('OTRO', 'I-12', '', [(630,625),(648,620),(652,645),(637,650)])
poly('OTRO', 'M-13', '', [(250,810),(275,807),(285,825),(265,837),(252,830)])
poly('OTRO', 'N-14', '', [(175,800),(200,795),(215,808),(190,815)])
poly('OTRO', 'N-14', '', [(205,796),(235,800),(240,817),(215,815)])
poly('OTRO', 'N-15', '', [(240,885),(257,877),(265,895),(255,907),(242,905)])
poly('OTRO', 'JUAN MONTALVO ANTIGUO', '', [(340,1160),(375,1140),(390,1160),(375,1177),(352,1180)])
poly('OTRO', 'JUAN MONTALVO NUEVO', '', [(402,1160),(430,1147),(447,1150),(447,1170),(410,1172)])
poly('OTRO', 'EP ULEAM', '', [(454,1135),(485,1129),(486,1157),(455,1160)])
# Deporte / comida
poly('CANCHA', 'Canchas multiples', '', [(565,615),(630,568),(665,578),(600,640)])
poly('CANCHA', 'Canchas multiples', '', [(390,1122),(440,1077),(470,1107),(420,1150)])
poly('CANCHA', 'Cancha', '', [(216,665),(255,662),(257,692),(219,692)])
poly('CANCHA_VERDE', 'Cancha cesped', '', [(177,1140),(215,1145),(207,1192),(177,1190)])
poly('GRADA', 'Gradas', '', [(402,707),(412,706),(465,760),(457,767)])
poly('COMIDA', 'Tasty Food', '', [(582,642),(615,628),(615,660),(590,655)])
poly('COMIDA', 'Tasty Food', '', [(495,912),(520,910),(527,930),(510,944),(495,925)])
poly('COMIDA', 'Tasty Food', '', [(195,1117),(225,1115),(222,1127),(195,1127)])
# Estadio: pista elipse + cancha
def ellipse(cx, cy, a, b, ang, n=40):
    r = math.radians(ang)
    return [(cx + a*math.cos(t)*math.cos(r) - b*math.sin(t)*math.sin(r),
             cy + a*math.cos(t)*math.sin(r) + b*math.sin(t)*math.cos(r))
            for t in [2*math.pi*i/n for i in range(n)]]
poly('PISTA', 'Estadio', '', ellipse(381, 777, 112, 86, 35))
poly('CANCHA_ESTADIO', 'Estadio', '', [(307,735),(385,705),(465,805),(410,847)])
for x, y in [(260,482),(407,437),(147,607),(455,607),(555,660),(147,727),(237,782),(287,802),(340,905),(222,1025),(162,1060),(430,1060),(500,1142)]:
    poly('PARQUEO', 'P', '', [(x, y)])
for x, y in [(172,482),(417,525),(357,595),(551,607),(502,693),(524,827),(339,867),(188,894),(460,948),(548,1070),(374,1129),(397,1155)]:
    poly('AGUA', 'Agua', '', [(x, y)])


def load_nodes():
    nodes = {}
    with open(os.path.join(ROOT, 'data', 'nodos_u.csv'), encoding='utf-8', errors='replace', newline='') as f:
        for r in csv.DictReader(f):
            nodes[int(r['ID'])] = (r['Descri'], float(r['Latitud']), float(r['Longitud']))
    return nodes

def grupos_por_bloque():
    out = {}
    with open(os.path.join(ROOT, 'app/src/main/assets/lugares.csv'), encoding='utf-8', newline='') as f:
        for r in csv.DictReader(f):
            m = re.match(r'([A-Z]+)·(B-\d+)', r['codigo'] or '')
            if m: out[m.group(2)] = m.group(1)
    return out

def fit(nodes):
    P, G = [], []
    for nid, (d, la, lo) in nodes.items():
        m = re.search(r'Entrada Bloque B-(\d+)$', d.strip())
        if m:
            k = 'B-%02d' % int(m.group(1))
            if k in CTRL_BLOCKS:
                P.append(CTRL_BLOCKS[k]); G.append(((lo-LO0)*KLON, (la-LA0)*KLAT))
    X = np.c_[np.array(P, float), np.ones(len(P))]
    sol = np.linalg.lstsq(X, np.array(G), rcond=None)[0]
    err = np.hypot(*(X@sol - np.array(G)).T)
    return sol, err

def to_geo(sol, x, y):
    gx, gy = np.array([x, y, 1.0]) @ sol
    return LA0 + gy/KLAT, LO0 + gx/KLON

def main():
    nodes = load_nodes(); sol, err = fit(nodes)
    print('error medio de calibracion: %.1f m (max %.1f m)' % (err.mean(), err.max()))
    grp = grupos_por_bloque()
    rows = []
    for t, c, g, pts in S:
        g = g or grp.get(c, 'OTRO')
        if g == 'ADMIN_C': g = 'OTRO'
        geo = [to_geo(sol, x, y) for x, y in pts]
        rows.append([t, c, g, ' '.join('%.6f %.6f' % p for p in geo)])
    out = os.path.join(ROOT, 'app/src/main/assets/campus_shapes.csv')
    with open(out, 'w', encoding='utf-8', newline='') as f:
        w = csv.writer(f, lineterminator='\n'); w.writerow(['tipo', 'codigo', 'grupo', 'puntos']); w.writerows(rows)
    print(len(rows), 'figuras ->', out)

COLORES = {'FCS': '#29B6F6', 'FIIA': '#1E40AF', 'FACIVITEC': '#2E9E4F', 'FETAH': '#F08A3C',
           'FCACC': '#2563EB', 'FCSDB': '#B94A5A', 'OTRO': '#8FA6AE'}

def preview(path):
    """Vista previa norte-arriba (misma geometria que dibuja la app)."""
    from PIL import Image, ImageDraw
    rows = list(csv.DictReader(open(os.path.join(ROOT, 'app/src/main/assets/campus_shapes.csv'), encoding='utf-8')))
    nod = {}
    for r in csv.DictReader(open(os.path.join(ROOT, 'app/src/main/assets/nodos.csv'), encoding='utf-8')):
        nod[int(r['id'])] = (float(r['latitud']), float(r['longitud']))
    ars = [(int(r['id_origen']), int(r['id_destino'])) for r in csv.DictReader(open(os.path.join(ROOT, 'app/src/main/assets/aristas.csv'), encoding='utf-8'))]
    las = [v[0] for v in nod.values()]; los = [v[1] for v in nod.values()]
    mla, Mla, mlo, Mlo = min(las)-0.0004, max(las)+0.0004, min(los)-0.0004, max(los)+0.0004
    W = 1100; k = W/(Mlo-mlo)*1.0; H = int((Mla-mla)*k*(KLAT/KLON)**0 )
    kx = W/(Mlo-mlo); ky = kx*(1/math.cos(math.radians(0.9525)))**0
    H = int((Mla-mla)*kx)
    P = lambda la, lo: ((lo-mlo)*kx, (Mla-la)*kx)
    im = Image.new('RGB', (W, H), '#F6F5F0'); d = ImageDraw.Draw(im)
    for a, b in ars:
        if a in nod and b in nod: d.line([P(*nod[a]), P(*nod[b])], fill='#D1D5DB', width=4)
    order = ['PISTA','CANCHA_ESTADIO','GRADA','CANCHA_VERDE','CANCHA','COMIDA','OTRO','EDIFICIO']
    fill = {'PISTA':'#CFE8D5','CANCHA_ESTADIO':'#7FC98F','GRADA':'#B8C4C8','CANCHA_VERDE':'#7FC98F','CANCHA':'#8FD3F4','COMIDA':'#F6D58E'}
    for t in order:
        for r in rows:
            if r['tipo'] != t: continue
            v = list(map(float, r['puntos'].split())); pts = [P(v[i], v[i+1]) for i in range(0, len(v), 2)]
            col = COLORES.get(r['grupo'], '#8FA6AE') if t in ('EDIFICIO','OTRO') else fill[t]
            d.polygon(pts, fill=col, outline='#33434A')
            if t == 'EDIFICIO':
                cx = sum(p[0] for p in pts)/len(pts); cy = sum(p[1] for p in pts)/len(pts)
                d.text((cx-10, cy-5), r['codigo'], fill='white')
    for r in rows:
        v = list(map(float, r['puntos'].split()))
        if r['tipo'] in ('PARQUEO','AGUA'):
            x, y = P(v[0], v[1]); c = '#1565C0' if r['tipo']=='PARQUEO' else '#29B6F6'
            d.ellipse([x-6, y-6, x+6, y+6], fill=c); d.text((x-3, y-5), 'P' if r['tipo']=='PARQUEO' else '', fill='white')
    im.save(path); print('preview ->', path, im.size)

if __name__ == '__main__':
    main()
    import sys
    if '--preview' in sys.argv: preview(os.path.join(ROOT, 'docs', 'mapa_preview.png'))
