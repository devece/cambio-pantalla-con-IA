package cl.tablero.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObdCodesTest {
    private val json = File(System.getProperty("obd.codes.path")).readText()
    private val codes = ObdCodes.parse(json)

    @Test
    fun `un codigo conocido se explica con su sistema`() {
        val i = codes.describe("P0133")
        assertTrue(i.known)
        assertEquals("P0133", i.code)
        assertEquals("Sensores del motor", i.system)
        assertTrue(i.text.contains("oxígeno"))
    }

    @Test
    fun `no importa si viene en minusculas o con espacios`() {
        assertEquals(codes.describe("P0300"), codes.describe("  p0300 "))
    }

    @Test
    fun `un codigo desconocido recibe una explicacion general segun su letra y digito`() {
        val p0 = codes.describe("P0999")
        assertFalse(p0.known)
        assertEquals("Motor y transmisión", p0.system)
        assertTrue(p0.text.isNotBlank())

        assertTrue(codes.describe("P1234").text.contains("marca"), "los P1xxx son propios de cada marca")
        assertEquals("Frenos y chasis", codes.describe("C1234").system)
        assertEquals("Carrocería", codes.describe("B1234").system)
        assertEquals("Comunicación", codes.describe("U1234").system)
        assertTrue(codes.describe("U1234").text.isNotBlank())
    }

    @Test
    fun `un texto raro no deja la explicacion vacia`() {
        val i = codes.describe("???")
        assertFalse(i.known)
        assertTrue(i.text.isNotBlank())
    }

    @Test
    fun `el catalogo esta bien formado`() {
        val systems = setOf(
            "Aire y admisión", "Combustible", "Encendido", "Escape y emisiones", "Refrigeración", "Sensores del motor",
            "Transmisión", "Electricidad", "Computador", "Comunicación", "Frenos y estabilidad",
        )
        val pattern = Regex("^[PCBU][0-3][0-9A-F]{3}$")
        val keys = org.json.JSONObject(json).getJSONObject("codigos").keys().asSequence().toList()
        assertTrue(keys.size >= 90, "pocos códigos: ${keys.size}")
        assertEquals(keys.size, codes.size)
        for (k in keys) {
            assertTrue(pattern.matches(k), "código mal escrito: $k")
            val info = codes.describe(k)
            assertTrue(info.known, k)
            assertTrue(info.system in systems, "$k: sistema desconocido «${info.system}»")
            assertTrue(info.text.length in 20..180, "$k: texto de ${info.text.length} caracteres")
            assertTrue(info.text.trim().endsWith("."), "$k: el texto debe terminar en punto")
        }
    }

    @Test
    fun `los codigos mas comunes estan`() {
        for (c in listOf("P0300", "P0301", "P0420", "P0171", "P0172", "P0442", "P0455", "P0128", "P0113", "P0562", "U0100", "P0500")) {
            assertTrue(codes.describe(c).known, c)
        }
    }

    @Test
    fun `decodificar bytes y explicar encajan`() {
        // 0x01 0x33 -> P0133 ; 0xC1 0x00 -> U0100
        assertEquals("P0133", Dtc.decode(0x01, 0x33))
        assertEquals("U0100", Dtc.decode(0xC1, 0x00))
        assertTrue(codes.describe(Dtc.decode(0x01, 0x33)).known)
        assertTrue(codes.describe(Dtc.decode(0xC1, 0x00)).known)
    }
}
