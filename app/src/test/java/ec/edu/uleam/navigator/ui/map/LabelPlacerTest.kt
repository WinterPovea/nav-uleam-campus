package ec.edu.uleam.navigator.ui.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LabelPlacerTest {
    @Test
    fun etiquetaEncimadaSeOmite() {
        val p = LabelPlacer(paddingPx = 0f)
        assertTrue(p.tryPlace(0f, 0f, 100f, 20f))
        assertFalse(p.tryPlace(50f, 10f, 150f, 30f))  // se cruza con la primera
        assertTrue(p.tryPlace(0f, 25f, 100f, 45f))    // justo debajo: cabe
        assertEquals(2, p.count)
    }
}
