package ec.edu.uleam.navigator.routing.astar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeoMathTest {
    @Test fun `distancia entre el mismo punto es cero`() =
        assertEquals(0.0, GeoMath.haversine(-0.9546, -80.746, -0.9546, -80.746), 1e-9)

    @Test fun `un grado de latitud mide unos 111 km`() =
        assertEquals(111_195.0, GeoMath.haversine(0.0, 0.0, 1.0, 0.0), 111_195.0 * 0.002)

    @Test fun `es simetrica`() {
        val ab = GeoMath.haversine(-0.9546, -80.746, -0.9520, -80.743)
        val ba = GeoMath.haversine(-0.9520, -80.743, -0.9546, -80.746)
        assertEquals(ab, ba, 1e-9)
    }

    @Test fun `100 metros al este se miden como 100 metros`() {
        val a = mockNode(1, 0.0, 0.0); val b = mockNode(2, 100.0, 0.0)
        assertEquals(100.0, GeoMath.haversine(a, b), 0.5)
    }

    @Test fun `distancia ortogonal a un segmento sobre su punto medio coincide con desplazamiento perpendicular`() {
        val segStart = mockNode(1, 0.0, 0.0)
        val segEnd = mockNode(2, 100.0, 0.0)
        val point = mockNode(3, 50.0, 12.0) // 12 metros al norte de la mitad del segmento
        assertEquals(12.0, GeoMath.distanceToSegment(point.latitude, point.longitude, segStart, segEnd), 0.5)
    }

    @Test fun `distancia a un segmento fuera de sus extremos se mide al extremo mas cercano`() {
        val segStart = mockNode(1, 0.0, 0.0)
        val segEnd = mockNode(2, 100.0, 0.0)
        val beyondEnd = mockNode(3, 130.0, 40.0) // 30m al este y 40m al norte del extremo final -> hipotenusa 50m
        assertEquals(50.0, GeoMath.distanceToSegment(beyondEnd.latitude, beyondEnd.longitude, segStart, segEnd), 0.8)
    }

    @Test fun `distancia a segmento degenerado de longitud cero equivale a haversine al punto`() {
        val p = mockNode(1, 0.0, 0.0)
        val q = mockNode(2, 25.0, 0.0)
        assertEquals(25.0, GeoMath.distanceToSegment(q.latitude, q.longitude, p, p), 0.5)
    }
}
