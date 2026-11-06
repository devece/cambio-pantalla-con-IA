package cl.tablero.core

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Un adaptador ELM327 simulado: contesta lo que se le preparó y anota cada comando que recibe. */
private class FakeElm(replies: Map<String, String> = emptyMap()) : ObdTransport {
    private val replies = HashMap(replies)
    val sent = ArrayList<String>()
    var failWith: IOException? = null

    fun reply(command: String, response: String) = apply { replies[command] = response }

    override fun exchange(command: String): String {
        sent += command
        failWith?.let { throw it }
        return replies[command] ?: throw IllegalStateException("No se preparó respuesta para «$command»")
    }
}

class ObdSessionTest {
    private val initOk = mapOf(
        "ATZ" to "\r\rELM327 v1.5\r\r",
        "ATE0" to "ATE0\rOK\r\r",
        "ATL0" to "OK\r\r",
        "ATS0" to "OK\r\r",
        "ATH0" to "OK\r\r",
        "ATSP0" to "OK\r\r",
    )

    private fun session(vararg extra: Pair<String, String>): Pair<ObdSession, FakeElm> {
        val elm = FakeElm(initOk + extra)
        return ObdSession(elm) to elm
    }

    /* ---------- inicio ---------- */

    @Test
    fun `el inicio configura el adaptador en el orden esperado`() {
        val (s, elm) = session()
        s.init()
        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0"), elm.sent)
        assertEquals("ELM327 v1.5", s.adapter)
    }

    @Test
    fun `algo que no se presenta como ELM327 se rechaza`() {
        val elm = FakeElm(initOk + ("ATZ" to "HOLA\r\r"))
        val e = assertFailsWith<ObdException> { ObdSession(elm).init() }
        assertEquals(ObdError.NOT_ELM327, e.error)
    }

    @Test
    fun `un adaptador que no entiende un comando de inicio da un error claro`() {
        val elm = FakeElm(initOk + ("ATS0" to "?\r\r"))
        assertEquals(ObdError.UNKNOWN_COMMAND, assertFailsWith<ObdException> { ObdSession(elm).init() }.error)
    }

    @Test
    fun `un adaptador que no confirma OK se rechaza`() {
        val elm = FakeElm(initOk + ("ATL0" to "ALGO RARO\r\r"))
        assertEquals(ObdError.BAD_RESPONSE, assertFailsWith<ObdException> { ObdSession(elm).init() }.error)
    }

    @Test
    fun `un corte de conexion sale como IOException`() {
        val elm = FakeElm(initOk).apply { failWith = IOException("se cayó") }
        assertFailsWith<IOException> { ObdSession(elm).init() }
    }

    /* ---------- datos soportados y lecturas ---------- */

    @Test
    fun `los datos soportados siguen la cadena de bloques del ejemplo clasico`() {
        val (s, elm) = session("0100" to "41 00 BE 1F A8 13 \r\r", "0120" to "41 20 00 00 00 00 \r\r")
        val got = s.supportedPids()
        assertEquals(setOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x13, 0x15, 0x1C, 0x1F), got)
        assertEquals(listOf("0100", "0120"), elm.sent) // el segundo bloque no anuncia más: se detiene
    }

    @Test
    fun `se recorren todos los bloques hasta encontrar el del kilometraje`() {
        val (s, elm) = session(
            "0100" to "410000000001", "0120" to "412000000001", "0140" to "414000000001",
            "0160" to "416000000001", "0180" to "418000000001", "01A0" to "41A004000000",
        )
        assertEquals(setOf(0xA6), s.supportedPids())
        assertEquals(listOf("0100", "0120", "0140", "0160", "0180", "01A0"), elm.sent)
    }

    @Test
    fun `si un bloque posterior no contesta se queda con lo que ya sabe`() {
        val (s, _) = session("0100" to "410000000001", "0120" to "NO DATA\r\r")
        assertEquals(emptySet(), s.supportedPids())
    }

    @Test
    fun `se leen los valores y se convierten`() {
        val (s, _) = session(
            "010C" to "410C1AF8", "010D" to "410D3C", "0105" to "41057B", "0142" to "414235E8",
            "01A6" to "41A60012D687", "012F" to "412F80",
        )
        assertEquals(1726.0, s.read(0x0C)!!.value, 0.001)
        assertEquals("rpm", s.read(0x0C)!!.spec.unit)
        assertEquals(60.0, s.read(0x0D)!!.value, 0.001)
        assertEquals(83.0, s.read(0x05)!!.value, 0.001)
        assertEquals(13.8, s.read(0x42)!!.value, 0.0001)
        assertEquals(123456.7, s.read(0xA6)!!.value, 0.0001)
        assertEquals(50.2, s.read(0x2F)!!.value, 0.05)
    }

    @Test
    fun `un dato que el auto no entrega es null y no un error`() {
        val (s, _) = session("012F" to "NO DATA\r\r")
        assertNull(s.read(0x2F))
    }

    @Test
    fun `los problemas de verdad si son errores`() {
        val (s, _) = session(
            "010C" to "SEARCHING...\rUNABLE TO CONNECT\r\r",
            "010D" to "410C1AF8", // contestó otro dato
            "0105" to "4105", // faltan los datos
            "0111" to "CAN ERROR\r",
        )
        assertEquals(ObdError.UNABLE_TO_CONNECT, assertFailsWith<ObdException> { s.read(0x0C) }.error)
        assertEquals(ObdError.BAD_RESPONSE, assertFailsWith<ObdException> { s.read(0x0D) }.error)
        assertEquals(ObdError.BAD_RESPONSE, assertFailsWith<ObdException> { s.read(0x05) }.error)
        assertEquals(ObdError.BUS_ERROR, assertFailsWith<ObdException> { s.read(0x11) }.error)
    }

    @Test
    fun `pedir un dato que la app no conoce es un error de programacion`() {
        val (s, _) = session()
        assertFailsWith<IllegalArgumentException> { s.read(0x99) }
    }

    @Test
    fun `el voltaje del adaptador se lee aunque el motor este apagado`() {
        assertEquals(12.6, session("ATRV" to "12.6V\r\r").first.adapterVoltage()!!, 0.0001)
        assertEquals(12.0, session("ATRV" to "12V\r\r").first.adapterVoltage()!!, 0.0001)
        assertNull(session("ATRV" to "?\r\r").first.adapterVoltage(), "clon que no lo soporta")
        assertNull(session("ATRV" to "ABC\r\r").first.adapterVoltage())
    }

    @Test
    fun `el estado del auto da la luz del motor y los monitores`() {
        val (s, _) = session("0101" to "41 01 82 07 65 04 \r\r")
        val st = assertNotNull(s.monitorStatus())
        assertTrue(st.milOn)
        assertEquals(2, st.dtcCount)
        assertFalse(st.compressionIgnition)
        // 0x65 = catalizador, evaporación, sensor de oxígeno y su calefactor; 0x04 = el de evaporación aún no terminó.
        assertEquals(listOf("Sistema de evaporación"), st.pending.map { it.name })
        assertTrue(st.monitors.first { it.name == "Catalizador" }.let { it.supported && it.complete })
        assertFalse(st.monitors.first { it.name == "Aire secundario" }.supported)
    }

    @Test
    fun `el estado de un diesel usa otros monitores`() {
        val st = MonitorStatus.decode(intArrayOf(0x00, 0x0F, 0x08, 0x08)) // diésel, presión del turbo soportada y sin terminar
        assertTrue(st.compressionIgnition)
        assertFalse(st.milOn)
        assertEquals(listOf("Presión del turbo"), st.pending.map { it.name })
    }

    @Test
    fun `sin estado disponible es null`() {
        assertNull(session("0101" to "NO DATA\r").first.monitorStatus())
    }

    /* ---------- códigos de falla ---------- */

    @Test
    fun `codigos en CAN traen primero cuantos son`() {
        val (s, _) = session("ATDPN" to "A6\r\r", "03" to "43 02 01 33 01 34 00 \r\r")
        assertEquals(listOf("P0133", "P0134"), s.readDtcs())
    }

    @Test
    fun `codigos en protocolos antiguos vienen directo de a tres pares`() {
        val (s, _) = session("ATDPN" to "3\r\r", "03" to "43 01 33 01 34 00 00 \r\r")
        assertEquals(listOf("P0133", "P0134"), s.readDtcs())
    }

    @Test
    fun `si el adaptador no dice el protocolo se deduce por el largo`() {
        assertEquals(listOf("P0133"), session("ATDPN" to "?\r\r", "03" to "430101 33".replace(" ", "")).first.readDtcs()) // impar: trae cuenta
        assertEquals(listOf("P0133"), session("ATDPN" to "?\r\r", "03" to "43013300000000".replace(" ", "")).first.readDtcs()) // par: directo
    }

    @Test
    fun `una lista larga en CAN llega en varias lineas`() {
        val raw = "00A\r0: 43 04 01 33 01 34\r1: 03 00 03 01\r"
        assertEquals(listOf("P0133", "P0134", "P0300", "P0301"), session("ATDPN" to "A6", "03" to raw).first.readDtcs())
    }

    @Test
    fun `sin codigos es una lista vacia`() {
        assertEquals(emptyList(), session("ATDPN" to "A6", "03" to "43 00 \r\r").first.readDtcs())
        assertEquals(emptyList(), session("ATDPN" to "A6", "03" to "NO DATA\r\r").first.readDtcs())
    }

    @Test
    fun `varias ECUs con el mismo codigo lo dan una sola vez`() {
        val raw = "43 01 01 33 \r43 01 01 33 \r43 01 03 00 \r"
        assertEquals(listOf("P0133", "P0300"), session("ATDPN" to "A6", "03" to raw).first.readDtcs())
    }

    @Test
    fun `los pendientes y permanentes usan su propia cabecera`() {
        val (s, elm) = session("ATDPN" to "A6", "07" to "47 01 01 71 \r", "0A" to "4A 01 04 20 \r")
        assertEquals(listOf("P0171"), s.readDtcs(DtcKind.PENDING))
        assertEquals(listOf("P0420"), s.readDtcs(DtcKind.PERMANENT))
        assertTrue("03" !in elm.sent)
    }

    @Test
    fun `una respuesta con otra cabecera no se toma como codigos`() {
        val (s, _) = session("ATDPN" to "A6", "07" to "43 01 01 33 \r")
        assertEquals(emptyList(), s.readDtcs(DtcKind.PENDING))
    }

    @Test
    fun `las letras del codigo salen de los dos primeros bits`() {
        assertEquals("P0133", Dtc.decode(0x01, 0x33))
        assertEquals("C0100", Dtc.decode(0x41, 0x00))
        assertEquals("B0100", Dtc.decode(0x81, 0x00))
        assertEquals("U0100", Dtc.decode(0xC1, 0x00))
        assertEquals("P1420", Dtc.decode(0x14, 0x20))
        assertEquals("P2A0F", Dtc.decode(0x2A, 0x0F))
        assertEquals("P0007", Dtc.decode(0x00, 0x07))
    }

    @Test
    fun `borrar codigos exige que el auto lo confirme`() {
        session("04" to "44\r\r").first.clearDtcs() // no lanza
        assertEquals(ObdError.NO_DATA, assertFailsWith<ObdException> { session("04" to "NO DATA\r").first.clearDtcs() }.error)
        assertEquals(ObdError.BAD_RESPONSE, assertFailsWith<ObdException> { session("04" to "7F 04 22\r").first.clearDtcs() }.error)
    }

    /* ---------- VIN ---------- */

    @Test
    fun `el VIN llega en un mensaje largo en CAN`() {
        val raw = "014\r0: 49 02 01 31 47 31\r1: 4A 43 35 34 34 34 52\r2: 37 32 35 32 33 36 37\r"
        assertEquals("1G1JC5444R7252367", session("0902" to raw).first.vin())
    }

    @Test
    fun `el VIN llega en cinco mensajes en protocolos antiguos, con relleno de ceros`() {
        val raw = "49 02 01 00 00 00 31 \r49 02 02 47 31 4A 43 \r49 02 03 35 34 34 34 \r49 02 04 52 37 32 35 \r49 02 05 32 33 36 37 \r"
        assertEquals("1G1JC5444R7252367", session("0902" to raw).first.vin())
    }

    @Test
    fun `el VIN se ordena aunque lleguen desordenados y sin el no entrega es null`() {
        val raw = "49 02 02 47 31 4A 43 \r49 02 01 00 00 00 31 \r"
        assertEquals("1G1JC", session("0902" to raw).first.vin())
        assertNull(session("0902" to "NO DATA\r").first.vin())
    }

    /* ---------- seguridad: solo comandos permitidos ---------- */

    @Test
    fun `solo se aceptan comandos de la lista blanca`() {
        for (ok in listOf("0100", "010C", "01A6", "0902", "03", "04", "07", "0A", "ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATDPN", "ATRV")) {
            assertTrue(ObdCommands.isAllowed(ok), ok)
        }
        for (bad in listOf("", "AT", "010", "01GG", "010c", "0100 ", "0100\r", "0A00", "0600", "0200", "0901", "2701", "3101", "22F190", "2F0301", "ATPP", "ATWS", "ATSH7E0", "ATMA", "ATD", "ATSP6")) {
            assertFalse(ObdCommands.isAllowed(bad), "«$bad» no debería estar permitido")
        }
    }

    @Test
    fun `una sesion completa nunca manda algo fuera de la lista blanca ni borra por su cuenta`() {
        val (s, elm) = session(
            "0100" to "41 00 BE 1F A8 13", "0120" to "NO DATA", "010C" to "410C1AF8", "0101" to "41 01 00 07 00 00",
            "ATRV" to "12.6V", "ATDPN" to "A6", "03" to "4300", "07" to "4700", "0A" to "4A00", "0902" to "NO DATA",
        )
        s.init()
        s.supportedPids(); s.read(0x0C); s.monitorStatus(); s.adapterVoltage()
        s.readDtcs(); s.readDtcs(DtcKind.PENDING); s.readDtcs(DtcKind.PERMANENT); s.vin()
        assertTrue(elm.sent.all { ObdCommands.isAllowed(it) }, elm.sent.toString())
        assertFalse("04" in elm.sent, "solo clearDtcs() puede borrar")
    }

    @Test
    fun `los comandos siguen limpios con adaptadores que repiten el eco`() {
        val elm = FakeElm(
            mapOf("ATZ" to "ATZ\r\rELM327 v2.1\r\r", "ATE0" to "ATE0\rOK\r", "ATL0" to "ATL0\rOK\r", "ATS0" to "ATS0\rOK\r", "ATH0" to "ATH0\rOK\r", "ATSP0" to "ATSP0\rOK\r", "010D" to "010D\rSEARCHING...\r41 0D 3C \r"),
        )
        val s = ObdSession(elm)
        s.init()
        assertEquals("ELM327 v2.1", s.adapter)
        assertContentEquals(doubleArrayOf(60.0), doubleArrayOf(s.read(0x0D)!!.value))
    }
}
