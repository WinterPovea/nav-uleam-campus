"""Ajusta la forma de las aristas del grafo a las calles de OpenStreetMap (solo para el DIBUJO de la ruta).

Una arista que en QGIS corre a lo largo de una calle (dibujada a mano, con quiebres, por la vereda) se
reemplaza por el tramo de la línea central de esa calle entre las proyecciones de sus dos nodos. Así la
ruta se ve curva y siguiendo la carretera. Las aristas que no van por una calle (conexiones a entradas,
caminos internos, escaleras) conservan su forma de QGIS.

La forma ajustada empieza y termina en la PROYECCIÓN de cada nodo sobre la calle (no en el nodo): así dos
aristas seguidas se unen sobre la calle sin un pico hacia el nodo. La distancia de la arista (para A*) no
cambia: sigue siendo el largo dibujado en QGIS.

Solo biblioteca estándar.
"""
import heapq
import math

LAT0 = -0.9525
KX = 111_320.0 * math.cos(math.radians(LAT0))
KY = 110_574.0


def xy(p):
    return ((p[1] + 80.74) * KX, (p[0] - LAT0) * KY)


def ll(q):
    return (q[1] / KY + LAT0, q[0] / KX - 80.74)


def _proj(p, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    l2 = dx * dx + dy * dy
    t = 0.0 if l2 < 1e-12 else max(0.0, min(1.0, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / l2))
    q = (a[0] + t * dx, a[1] + t * dy)
    return q, t, math.dist(p, q)


def _dist_line(p, line):
    return min(_proj(p, line[i], line[i + 1])[2] for i in range(len(line) - 1))


def _sample(line, step=2.0):
    out = [line[0]]
    for i in range(len(line) - 1):
        a, b = line[i], line[i + 1]
        k = max(1, int(math.dist(a, b) // step))
        out += [(a[0] + (b[0] - a[0]) * j / k, a[1] + (b[1] - a[1]) * j / k) for j in range(1, k + 1)]
    return out


def _length(line):
    return sum(math.dist(line[i], line[i + 1]) for i in range(len(line) - 1))


class RoadNet:
    """Grafo de las calles: vértices de las polilíneas OSM, unidos donde las calles se tocan."""

    def __init__(self, roads_ll, join_m=2.0):
        self.pts = []          # id -> (x, y)
        self.adj = {}          # id -> {id: m}
        self.segs = []         # (id_a, id_b)
        index = {}

        def vid(q):
            key = (round(q[0] / join_m), round(q[1] / join_m))
            for dk in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                for i in index.get((key[0] + dk[0], key[1] + dk[1]), ()):
                    if math.dist(self.pts[i], q) <= join_m:
                        return i
            self.pts.append(q)
            i = len(self.pts) - 1
            index.setdefault(key, []).append(i)
            self.adj[i] = {}
            return i

        lines = [[xy(p) for p in r] for r in roads_ll if len(r) >= 2]
        for line in lines:
            ids = [vid(q) for q in line]
            for a, b in zip(ids, ids[1:]):
                if a != b:
                    self._link(a, b)
        # Extremos de una calle que caen sobre otra (cruce en T sin vértice común): se parte el segmento
        for i, q in enumerate(list(self.pts)):
            if len(self.adj[i]) != 1:
                continue
            best = None
            for a, b in list(self.segs):
                if i in (a, b):
                    continue
                r, t, d = _proj(q, self.pts[a], self.pts[b])
                if d <= 3.0 and 0 < t < 1 and (best is None or d < best[0]):
                    best = (d, a, b, r)
            if best:
                _, a, b, r = best
                m = self._split(a, b, r)
                self._link(i, m)

    def _link(self, a, b):
        d = math.dist(self.pts[a], self.pts[b])
        self.adj[a][b] = d
        self.adj[b][a] = d
        self.segs.append((a, b))

    def _split(self, a, b, r):
        self.pts.append(r)
        m = len(self.pts) - 1
        self.adj[m] = {}
        self.adj[a].pop(b, None); self.adj[b].pop(a, None)
        self.segs = [s for s in self.segs if s not in ((a, b), (b, a))]
        self._link(a, m); self._link(m, b)
        return m

    def project(self, p, max_m):
        """Proyecciones de p sobre las calles a <= max_m, de la más cercana a la más lejana."""
        out = []
        for a, b in self.segs:
            r, t, d = _proj(p, self.pts[a], self.pts[b])
            if d <= max_m:
                out.append((d, a, b, r))
        out.sort()
        return out

    def path(self, pa, pb):
        """Camino más corto por las calles entre dos proyecciones (d, a, b, punto)."""
        _, a1, b1, r1 = pa
        _, a2, b2, r2 = pb
        if {a1, b1} == {a2, b2}:
            return [r1, r2]
        start = {a1: math.dist(r1, self.pts[a1]), b1: math.dist(r1, self.pts[b1])}
        goal = {a2: math.dist(r2, self.pts[a2]), b2: math.dist(r2, self.pts[b2])}
        dist, prev, pq = {}, {}, []
        for v, d in start.items():
            dist[v] = d; prev[v] = None; heapq.heappush(pq, (d, v))
        best, best_v = math.inf, None
        while pq:
            d, v = heapq.heappop(pq)
            if d > dist.get(v, math.inf) or d >= best:
                continue
            if v in goal and d + goal[v] < best:
                best, best_v = d + goal[v], v
            for w, c in self.adj[v].items():
                if d + c < dist.get(w, math.inf):
                    dist[w] = d + c; prev[w] = v; heapq.heappush(pq, (d + c, w))
        if best_v is None:
            return None
        seq, v = [], best_v
        while v is not None:
            seq.append(self.pts[v]); v = prev[v]
        return [r1] + seq[::-1] + [r2]


def snap(geoms, roads_ll, max_off_m=13.0, max_end_m=13.0, max_ratio=1.35):
    """geoms: {(a, b): [(lat, lon), ...]} de a hacia b. Devuelve ({(a, b): puntos ajustados}, set de ajustadas)."""
    net = RoadNet(roads_ll)
    out, snapped = {}, set()
    for key, pts in geoms.items():
        line = [xy(p) for p in pts]
        out[key] = pts
        L = _length(line)
        if L < 8.0:
            continue
        best = None
        for pa in net.project(line[0], max_end_m)[:4]:
            for pb in net.project(line[-1], max_end_m)[:4]:
                road = net.path(pa, pb)
                if not road or len(road) < 2:
                    continue
                R = _length(road)
                if R < 0.5 * L or R > max_ratio * L + 6:
                    continue
                # La arista y el tramo de calle deben ir juntos en todo su largo (en ambos sentidos)
                d1 = max(_dist_line(q, road) for q in _sample(line))
                d2 = max(_dist_line(q, line) for q in _sample(road))
                if d1 <= max_off_m and d2 <= max_off_m:
                    score = d1 + d2 + abs(R - L) * 0.2
                    if best is None or score < best[0]:
                        best = (score, road)
        if best:
            road = best[1]
            # sin puntos repetidos
            clean = [road[0]] + [q for i, q in enumerate(road[1:], 1) if math.dist(q, road[i - 1]) > 0.05]
            out[key] = [ll(q) for q in clean]
            snapped.add(key)
    return out, snapped
