package cl.tablero.core

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Recorder : ObdListener {
    val states = ArrayList<Pair<ObdState, String>>()
    val snapshots = ArrayList<ObdSnapshot>()
    override fun onState(state: ObdState, detail: String) {
        states += state to detail
    }

    override fun onSnapshot(snapshot: ObdSnapshot) {
        snapshots += snapshot
    }

    val kinds get() = states.map { it.first }
}

class ObdPollerTest {
    /** Un auto con velocidad, revoluciones y temperatura (PIDs 05, 0C y 0D). */
    private fun car(rpm: String = "410C1AF8") = FakeElm(
        FakeElm.INIT_OK + mapOf(
            "0100" to "410008180000",
            "010C" to rpm,
            "010D" to "410D3C",
            "0105" to "41057B",
            "ATRV" to "12.6V\r",
        ),
    )

    private class Fixture(val connector: ObdConnector, val opens: () -> Int)

    private fun connector(vararg channels: FakeElm): Fixture {
        var n = 0
        return Fixture(ObdConnector { channels[minOf(n++, channels.size - 1)] }, { n })
    }

    @Test
    fun `se conecta, averigua que datos hay y empieza a entregarlos`() {
        val elm = car()
        val rec = Recorder()
        val p = ObdPoller(connector(elm).connector, rec)

        assertEquals(0, p.step()) // abre y configura
        assertEquals(0, p.step()) // averigua los datos soportados
        assertEquals(ObdState.CONNECTED, rec.kinds.last())
        assertTrue(rec.states.last().second.contains("ELM327 v2.1"))

        val wait = p.step() // primera lectura
        assertEquals(1, rec.snapshots.size)
        val s = rec.snapshots[0]
        assertEquals(1726.0, s.values.getValue("rpm"), 0.001)
        assertEquals(60.0, s.values.getValue("speed"), 0.001)
        assertEquals(83.0, s.values.getValue("coolant"), 0.001)
        assertEquals(setOf(0x05, 0x0C, 0x0D), s.supported)
        assertEquals(ObdPoller.FAST_MS, wait)
        p.close()
    }

    @Test
    fun `con el motor apagado consulta mas despacio`() {
        val rec = Recorder()
        val p = ObdPoller(connector(car(rpm = "410C0000")).connector, rec)
        p.step(); p.step()
        assertEquals(ObdPoller.ENGINE_OFF_MS, p.step())
        p.close()
    }

    @Test
    fun `los datos lentos se turnan y se conservan los ultimos`() {
        val elm = car()
        val rec = Recorder()
        val p = ObdPoller(connector(elm).connector, rec)
        repeat(5) { p.step() }
        val last = rec.snapshots.last()
        assertTrue("coolant" in last.values && "rpm" in last.values && "speed" in last.values)
        p.close()
    }

    @Test
    fun `si el auto esta apagado el adaptador sigue conectado y se espera`() {
        val elm = car().apply { reply("0100", "SEARCHING...\rUNABLE TO CONNECT\r") }
        val rec = Recorder()
        val c = connector(elm)
        val p = ObdPoller(c.connector, rec)
        p.step() // conecta
        assertEquals(ObdPoller.CAR_RETRY_MS, p.step())
        assertEquals(ObdState.WAITING_FOR_CAR, rec.kinds.last())
        assertTrue(rec.states.last().second.contains("Enciende el auto"))
        // reintenta sin volver a abrir el adaptador, y sin llenar de avisos repetidos
        assertEquals(ObdPoller.CAR_RETRY_MS, p.step())
        assertEquals(1, c.opens())
        assertEquals(1, rec.kinds.count { it == ObdState.WAITING_FOR_CAR })

        // el usuario enciende el auto
        elm.reply("0100", "410008180000")
        assertEquals(0, p.step())
        assertEquals(ObdState.CONNECTED, rec.kinds.last())
        p.step()
        assertEquals(1, rec.snapshots.size)
        p.close()
    }

    @Test
    fun `si se corta la conexion reintenta esperando mas cada vez, hasta un tope`() {
        val rec = Recorder()
        val p = ObdPoller(ObdConnector { throw IOException("sin conexión") }, rec)
        val waits = (1..8).map { p.step() }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L, 30_000L), waits)
        assertEquals(listOf(ObdState.CONNECTING, ObdState.DISCONNECTED), rec.kinds, "un solo aviso por motivo, no uno por reintento")
        assertEquals("sin conexión", rec.states.last().second)
        p.close()
    }

    @Test
    fun `tras recuperar la conexion la espera vuelve a empezar de cero`() {
        val first = car()
        val second = car()
        val c = connector(first, second)
        val rec = Recorder()
        val p = ObdPoller(c.connector, rec)
        p.step(); p.step(); p.step() // conectado y leyendo
        first.failWith = IOException("se cayó el Bluetooth")
        assertEquals(2_000, p.step()) // se perdió: primera espera
        assertTrue(first.closed, "el canal roto se cierra")
        assertEquals(ObdState.DISCONNECTED, rec.kinds.last())
        assertEquals(0, p.step()) // reabre (segundo canal)
        assertEquals(2, c.opens())
        p.step(); p.step()
        assertEquals(ObdState.CONNECTED, rec.kinds.last())
        // si vuelve a fallar, la espera es la inicial y no la acumulada
        second.failWith = IOException("otra vez")
        assertEquals(2_000, p.step())
        p.close()
    }

    @Test
    fun `un dispositivo que no es ELM327 se informa y se sigue intentando`() {
        val notElm = FakeElm(FakeElm.INIT_OK + ("ATZ" to "HOLA\r\r"))
        val rec = Recorder()
        val p = ObdPoller(connector(notElm).connector, rec)
        assertEquals(2_000, p.step())
        assertEquals(ObdState.DISCONNECTED, rec.kinds.last())
        assertTrue(rec.states.last().second.contains("ELM327"))
        assertTrue(notElm.closed)
        p.close()
    }

    @Test
    fun `tres silencios seguidos del auto durante la lectura vuelven a esperar al auto`() {
        val elm = car()
        val rec = Recorder()
        val p = ObdPoller(connector(elm).connector, rec)
        p.step(); p.step(); p.step() // leyendo bien
        elm.reply("010C", "CAN ERROR\r") // el auto dejó de contestar (por ejemplo se apagó)
        assertEquals(1_000, p.step())
        assertEquals(1_000, p.step())
        assertEquals(ObdPoller.CAR_RETRY_MS, p.step())
        assertEquals(ObdState.WAITING_FOR_CAR, rec.kinds.last())
        // al volver el auto, vuelve a averiguar qué datos entrega y sigue
        elm.reply("010C", "410C1AF8")
        p.step(); p.step()
        assertEquals(ObdState.CONNECTED, rec.kinds.last())
        p.close()
    }

    @Test
    fun `un dato que el auto no entrega no tumba la lectura`() {
        val elm = car().apply { reply("0105", "NO DATA\r") }
        val rec = Recorder()
        val p = ObdPoller(connector(elm).connector, rec)
        repeat(4) { p.step() }
        val s = rec.snapshots.last()
        assertNull(s.values["coolant"])
        assertNotNull(s.values["rpm"])
        p.close()
    }

    @Test
    fun `si el auto no da el voltaje se usa el del adaptador`() {
        val elm = car()
        val rec = Recorder()
        val p = ObdPoller(connector(elm).connector, rec)
        repeat(3) { p.step() }
        assertEquals(12.6, rec.snapshots.last().values.getValue("voltage"), 0.0001)
        p.close()
    }

    @Test
    fun `cerrar corta todo y deja de reintentar`() {
        val elm = car()
        val c = connector(elm)
        val rec = Recorder()
        val p = ObdPoller(c.connector, rec)
        p.step(); p.step()
        p.close()
        assertTrue(elm.closed)
        assertEquals(ObdPoller.STOPPED_MS, p.step())
        assertEquals(1, c.opens())
    }

    @Test
    fun `cerrar mientras se abre la conexion no deja nada abierto`() {
        val elm = car()
        lateinit var p: ObdPoller
        val rec = Recorder()
        p = ObdPoller(ObdConnector { p.close(); elm }, rec)
        assertEquals(ObdPoller.STOPPED_MS, p.step())
        assertTrue(elm.closed)
    }
}
