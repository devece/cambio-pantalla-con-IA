package cl.tablero.core

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObdCheckTest {
    private fun car() = FakeElm(
        FakeElm.INIT_OK + mapOf(
            "ATRV" to "12.4V\r",
            "0100" to "410008180000",
            "010C" to "410C0000", // motor apagado
            "010D" to "410D00",
            "0105" to "41057B",
            "0101" to "41 01 82 07 65 04",
        ),
    )

    @Test
    fun `un adaptador y un auto sanos dan un informe completo`() {
        val elm = car()
        val r = ObdCheck.run { elm }
        assertTrue(r.adapterOk)
        assertTrue(r.carOk)
        val lines = r.report.lines()
        assertTrue(lines.contains("Adaptador: ELM327 v2.1"), r.report)
        assertTrue(lines.contains("Voltaje en el puerto OBD: 12.4 V"), r.report)
        assertTrue(lines.any { it.startsWith("Auto: responde (3 datos") }, r.report)
        assertTrue(lines.contains("Revoluciones: 0 rpm"), r.report)
        assertTrue(lines.contains("Velocidad: 0 km/h"), r.report)
        assertTrue(lines.contains("Temperatura del motor: 83 °C"), r.report)
        assertTrue(lines.contains("Luz del motor: encendida (2 códigos de falla)"), r.report)
        assertTrue(elm.closed, "siempre cierra el adaptador")
    }

    @Test
    fun `con el auto apagado el adaptador esta bien pero el auto no responde`() {
        val elm = car().apply { reply("0100", "SEARCHING...\rUNABLE TO CONNECT\r") }
        val r = ObdCheck.run { elm }
        assertTrue(r.adapterOk)
        assertFalse(r.carOk)
        assertTrue(r.report.contains("Adaptador: ELM327 v2.1"))
        assertTrue(r.report.contains("Auto: no responde"), r.report)
        assertTrue(r.report.contains("Enciende el auto"), r.report)
        assertTrue(elm.closed)
    }

    @Test
    fun `si no se logra abrir el adaptador lo explica`() {
        val r = ObdCheck.run { throw IOException("El Bluetooth de la radio está apagado") }
        assertFalse(r.adapterOk)
        assertFalse(r.carOk)
        assertTrue(r.report.contains("El Bluetooth de la radio está apagado"), r.report)
    }

    @Test
    fun `un dispositivo que no es ELM327 se rechaza con una explicacion`() {
        val elm = FakeElm(FakeElm.INIT_OK + ("ATZ" to "HOLA\r\r"))
        val r = ObdCheck.run { elm }
        assertFalse(r.adapterOk)
        assertTrue(r.report.contains("no parece un adaptador ELM327"), r.report)
        assertTrue(elm.closed)
    }

    @Test
    fun `si se corta a la mitad lo dice y el adaptador ya habia respondido bien`() {
        val elm = car()
        elm.handler = { c ->
            if (c == "0100") throw IOException("se cayó")
            FakeElm.INIT_OK[c] ?: "12.4V\r"
        }
        val r = ObdCheck.run { elm }
        assertTrue(r.adapterOk, "el adaptador sí había respondido")
        assertFalse(r.carOk)
        assertTrue(r.report.contains("Se perdió la conexión con el adaptador"), r.report)
        assertTrue(elm.closed)
    }

    @Test
    fun `un dato que falla no tumba la prueba entera`() {
        val elm = car().apply { reply("010C", "BAD\r") }
        val r = ObdCheck.run { elm }
        assertTrue(r.carOk)
        assertFalse(r.report.contains("Revoluciones"), r.report)
        assertTrue(r.report.contains("Velocidad"), r.report)
    }

    @Test
    fun `nunca lanza aunque el conector falle de forma inesperada con un error del adaptador`() {
        val elm = FakeElm(FakeElm.INIT_OK + ("ATE0" to "?\r"))
        val r = ObdCheck.run { elm }
        assertFalse(r.adapterOk)
        assertTrue(r.report.contains("no entendió"), r.report)
    }
}
