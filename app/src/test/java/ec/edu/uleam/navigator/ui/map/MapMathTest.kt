package ec.edu.uleam.navigator.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import ec.edu.uleam.navigator.ui.UserPosition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pruebas de la matemática del mapa: zoom anclado al pellizco y avance sobre la ruta. */
class MapMathTest {
    private val size = IntSize(1080, 1920)

    private fun proj(zoom: Float, pan: Offset, rot: Float) =
        MapProjection(-0.9560, -0.9480, -80.7480, -80.7410, size, zoom, pan, rot)

    @Test fun `el zoom anclado mantiene fijo el punto bajo los dedos`() {
        for (rot in listOf(0f, 35f, -120f)) {
            for ((z0, z1) in listOf(1.2f to 2.4f, 4f to 1.5f, 3f to 3.01f)) {
                val pan0 = Offset(37f, -81f)
                val focal = Offset(300f, 1400f) // lejos del centro de pantalla
                val before = proj(z0, pan0, rot)
                val geo = before.toGeo(focal)

                val pan1 = before.panForZoomAt(focal, z1)
                val after = proj(z1, pan1, rot)
                val screen = after.toScreen(geo.first, geo.second)

                assertEquals(focal.x, screen.x, 0.01f, "x rot=$rot $z0->$z1")
                assertEquals(focal.y, screen.y, 0.01f, "y rot=$rot $z0->$z1")
            }
        }
    }

    @Test fun `hacer zoom en el centro de pantalla no mueve el mapa lateralmente`() {
        val p = proj(2f, Offset.Zero, 0f)
        val pan1 = p.panForZoomAt(p.center, 4f)
        assertEquals(0f, pan1.x, 1e-3f)
        assertEquals(0f, pan1.y, 1e-3f)
    }

    private val a = GeoPoint(-0.9500, -80.7460)
    private val b = GeoPoint(-0.9500, -80.7450) // ~111 m al este de a
    private val c = GeoPoint(-0.9490, -80.7450) // ~111 m al norte de b

    @Test fun `el avance proyecta al usuario sobre la ruta y calcula lo que falta`() {
        val total = RouteProgressCalc.distanceM(a, b) + RouteProgressCalc.distanceM(b, c)
        // Usuario a mitad del tramo a-b, 5 m al sur de la ruta
        val user = GeoPoint(-0.9500 - 5.0 / 111_320.0, -80.7455)
        val pr = RouteProgressCalc.compute(listOf(a, b, c), user)!!

        assertEquals(3, pr.remaining.size) // punto proyectado, b, c
        assertEquals(b, pr.remaining[1])
        assertEquals(5.0, pr.offRouteM, 0.2)
        assertEquals(total - RouteProgressCalc.distanceM(a, b) / 2, pr.remainingM, 0.5)
        assertEquals(2, pr.traveled.size) // a y el punto proyectado
    }

    @Test fun `al pasar al segundo tramo el primero queda como recorrido`() {
        val user = GeoPoint(-0.9495, -80.7450)
        val pr = RouteProgressCalc.compute(listOf(a, b, c), user)!!
        assertEquals(RouteProgressCalc.distanceM(c, user), pr.remainingM, 0.5)
        assertTrue(pr.traveled.size >= 3)
    }

    @Test fun `un usuario pasado del final se proyecta al ultimo punto`() {
        val pr = RouteProgressCalc.compute(listOf(a, b), GeoPoint(-0.9500, -80.7440))!!
        assertEquals(0.0, pr.remainingM, 0.01)
    }

    @Test fun `el rumbo del tramo apunta al norte, este, sur y oeste segun corresponda`() {
        val o = GeoPoint(-0.9500, -80.7450)
        assertEquals(0.0, RouteProgressCalc.bearingDeg(o, GeoPoint(-0.9490, -80.7450)), 0.01)   // norte
        assertEquals(90.0, RouteProgressCalc.bearingDeg(o, GeoPoint(-0.9500, -80.7440)), 0.01)  // este
        assertEquals(180.0, RouteProgressCalc.bearingDeg(o, GeoPoint(-0.9510, -80.7450)), 0.01) // sur
        assertEquals(270.0, RouteProgressCalc.bearingDeg(o, GeoPoint(-0.9500, -80.7460)), 0.01) // oeste
    }

    @Test fun `el avance reporta el rumbo del tramo que se recorre`() {
        // a->b va al este; b->c va al norte
        assertEquals(90.0, RouteProgressCalc.compute(listOf(a, b, c), GeoPoint(-0.9500, -80.7455))!!.bearingDeg, 0.01)
        assertEquals(0.0, RouteProgressCalc.compute(listOf(a, b, c), GeoPoint(-0.9495, -80.7450))!!.bearingDeg, 0.01)
    }

    @Test fun `el toque elige el lugar mas cercano dentro del radio y null si cae en vacio`() {
        val c = listOf(1 to Offset(100f, 100f), 2 to Offset(130f, 100f), 3 to Offset(500f, 500f))
        assertEquals(1, PlaceHitTest.nearest(c, Offset(105f, 100f), 40f))
        assertEquals(2, PlaceHitTest.nearest(c, Offset(120f, 100f), 40f)) // 2 está más cerca que 1
        assertEquals(3, PlaceHitTest.nearest(c, Offset(490f, 510f), 40f))
        assertNull(PlaceHitTest.nearest(c, Offset(300f, 300f), 40f))
        assertNull(PlaceHitTest.nearest(emptyList(), Offset(0f, 0f), 40f))
    }

    @Test fun `una ruta con menos de dos puntos no tiene avance`() {
        assertNull(RouteProgressCalc.compute(listOf(a), a))
        assertNotNull(RouteProgressCalc.compute(listOf(a, b), a))
    }

    @Test fun `la interpolacion de posicion conserva precision y llega al destino`() {
        val from = UserPosition(-0.952662, -80.745479, 5f)
        val to = UserPosition(-0.952762, -80.745379, 8f)
        val mid = lerpPosition(from, to, 0.5f)
        assertEquals(-0.952712, mid.lat, 1e-9)
        assertEquals(-80.745429, mid.lon, 1e-9)
        assertEquals(to.lat, lerpPosition(from, to, 1f).lat, 1e-12)
    }
}
