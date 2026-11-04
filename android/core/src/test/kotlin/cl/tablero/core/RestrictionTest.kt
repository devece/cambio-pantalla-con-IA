package cl.tablero.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestrictionTest {
    private val rules = RestrictionRules.parse(File(System.getProperty("restriction.path")).readText())

    private fun cfg(type: String, digit: String, on: Boolean = true) =
        LauncherConfig(restrOn = on, restrType = type, restrDigit = digit)

    @Test
    fun `los dias salen de la tabla del sitio`() {
        assertEquals(listOf("lun"), rules.daysFor("sello", "8"))
        assertEquals(listOf("mar"), rules.daysFor("sello", "1"))
        assertEquals(listOf("vie"), rules.daysFor("sello", "7"))
        assertEquals(listOf("lun", "jue"), rules.daysFor("sin", "8"))
        assertEquals(listOf("mar", "jue"), rules.daysFor("sin", "0"))
        assertEquals(emptyList(), rules.daysFor("nuevo", "3"))
        assertEquals(emptyList(), rules.daysFor("sello", ""))
        assertEquals(emptyList(), rules.daysFor("sello", "12"))
    }

    @Test
    fun `el GPS distingue Santiago de otras ciudades`() {
        assertTrue(rules.inZone(-33.4378, -70.6505), "Plaza de Armas")
        assertTrue(rules.inZone(-33.408, -70.567), "Las Condes")
        assertTrue(rules.inZone(-33.51, -70.76), "Maipú")
        assertTrue(rules.inZone(-33.61, -70.575), "Puente Alto")
        assertTrue(rules.inZone(-33.59, -70.70), "San Bernardo")
        assertFalse(rules.inZone(-33.0472, -71.6127), "Valparaíso")
        assertFalse(rules.inZone(-34.1708, -70.7444), "Rancagua")
        assertFalse(rules.inZone(-38.7359, -72.5904), "Temuco")
        assertFalse(rules.inZone(-36.8201, -73.0444), "Concepción")
        assertFalse(rules.inZone(Double.NaN, -70.0))
    }

    @Test
    fun `avisa solo cuando corresponde`() {
        val sello = cfg("sello", "8") // lunes
        val n = rules.notice(sello, "lun", 6, 9 * 60)
        assertNotNull(n)
        assertTrue(n.warn)
        assertTrue("Hoy tienes restricción" in n.text && "terminada en 8" in n.text)
        assertNull(rules.notice(sello, "mar", 6, 9 * 60), "otro día")
        assertNull(rules.notice(sello, "lun", 10, 9 * 60), "fuera de temporada")
        assertNull(rules.notice(sello, "lun", 6, 7 * 60 + 29), "antes del horario")
        assertNull(rules.notice(sello, "lun", 6, 21 * 60), "después del horario")
        assertNull(rules.notice(sello, null, 6, 9 * 60), "fin de semana")
        assertNull(rules.notice(cfg("sello", "8", on = false), "lun", 6, 9 * 60), "apagado")
        assertNull(rules.notice(cfg("nuevo", ""), "lun", 6, 9 * 60), "auto nuevo")
    }

    @Test
    fun `sin sello verde tambien avisa del anillo`() {
        val sin = cfg("sin", "8") // lunes y jueves
        val lun = rules.notice(sin, "lun", 6, 9 * 60)
        assertNotNull(lun)
        assertTrue("Hoy tienes restricción" in lun.text && "anillo Américo Vespucio" in lun.text)
        val mie = rules.notice(sin, "mie", 6, 9 * 60)
        assertNotNull(mie)
        assertFalse("Hoy tienes restricción" in mie.text)
        assertTrue("anillo Américo Vespucio" in mie.text)
    }
}
