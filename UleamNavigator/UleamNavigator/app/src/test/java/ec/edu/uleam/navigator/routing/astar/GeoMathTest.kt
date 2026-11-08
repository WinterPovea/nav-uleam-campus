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
}
