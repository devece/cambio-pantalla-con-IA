package cl.tablero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ObdFuelTest {
    @Test
    fun `el dato directo del auto manda sobre la estimacion`() {
        assertEquals(6.0, ObdFuel.litersPerHour(mapOf("fuelrate" to 6.0, "maf" to 50.0))!!, 0.0001)
    }

    @Test
    fun `sin gasto directo se estima desde el flujo de aire`() {
        // 10 g/s de aire -> 36000 g/h / 14,7 = 2449 g/h de bencina -> 3,31 L/h
        assertEquals(3.309, ObdFuel.litersPerHour(mapOf("maf" to 10.0))!!, 0.001)
    }

    @Test
    fun `sin datos no hay gasto`() {
        assertNull(ObdFuel.litersPerHour(emptyMap()))
    }

    @Test
    fun `litros cada 100 km`() {
        assertEquals(6.0, ObdFuel.litersPer100Km(6.0, 100.0)!!, 0.0001)
    }

    @Test
    fun `casi detenido no se calcula`() {
        assertNull(ObdFuel.litersPer100Km(1.0, 3.0))
        assertNull(ObdFuel.litersPer100Km(null, 60.0))
        assertNull(ObdFuel.litersPer100Km(5.0, null))
    }

    @Test
    fun `agrega y quita los valores derivados`() {
        val v = mutableMapOf("fuelrate" to 5.0, "speed" to 50.0)
        ObdFuel.addDerived(v)
        assertEquals(5.0, v.getValue("flow"), 0.0001)
        assertEquals(10.0, v.getValue("l100"), 0.0001)
        v["speed"] = 0.0
        ObdFuel.addDerived(v)
        assertFalse("l100" in v)
    }
}
