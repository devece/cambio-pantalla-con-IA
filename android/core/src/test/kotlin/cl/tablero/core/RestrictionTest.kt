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

    @Test
    fun `las tablas son las de la resolucion oficial 1698 de 2026`() {
        // Resolucion Exenta N.º 1698/2026 de la SEREMI de Transportes RM, resuelvo 1, letras b) y e) (leida el 2026-11-11).
        val sinSello = mapOf("lun" to setOf(6, 7, 8, 9), "mar" to setOf(0, 1, 2, 3), "mie" to setOf(4, 5, 6, 7), "jue" to setOf(8, 9, 0, 1), "vie" to setOf(2, 3, 4, 5))
        val conSello = mapOf("lun" to setOf(8, 9), "mar" to setOf(0, 1), "mie" to setOf(2, 3), "jue" to setOf(4, 5), "vie" to setOf(6, 7))
        for ((dia, digitos) in sinSello) for (d in 0..9) assertEquals(d in digitos, dia in rules.daysFor("sin", d.toString()), "sin sello, $dia, digito $d")
        for ((dia, digitos) in conSello) for (d in 0..9) assertEquals(d in digitos, dia in rules.daysFor("sello", d.toString()), "con sello, $dia, digito $d")
    }

    @Test
    fun `en otro ano no afirma nada y manda a revisar la regla vigente`() {
        val sello = cfg("sello", "8") // lunes
        // La tabla es de 2026: ese ano sigue avisando como siempre.
        assertEquals(true, rules.notice(sello, "lun", 6, 9 * 60, 2026)?.warn)
        // En 2027 no se afirma la tabla vieja: ni «te toca» ni «no te toca».
        val viejo = assertNotNull(rules.notice(sello, "lun", 6, 9 * 60, 2027))
        assertFalse(viejo.warn)
        assertTrue("2026" in viejo.text && "2027" in viejo.text)
        assertFalse("Hoy tienes restriccion" in viejo.text.replace("ó", "o"))
        assertNotNull(rules.notice(sello, "mar", 6, 9 * 60, 2027), "tampoco afirma que no le toque")
        // Las demas guardas siguen igual.
        assertNull(rules.notice(sello, "lun", 10, 9 * 60, 2027), "fuera de temporada")
        assertNull(rules.notice(sello, null, 6, 9 * 60, 2027), "fin de semana")
        assertNull(rules.notice(cfg("nuevo", ""), "lun", 6, 9 * 60, 2027), "auto nuevo")
        // Sin ano (las pruebas anteriores) no se comprueba.
        assertEquals(true, rules.notice(sello, "lun", 6, 9 * 60)?.warn)
    }
}
