package cl.tablero.core

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Un adaptador simulado a nivel de bytes: lo que la app escribe llega a [responder] (un comando por línea) y lo que este
 * devuelve se entrega como si lo hubiera mandado el adaptador. Sirve para probar el transporte sin Bluetooth ni Wi-Fi.
 */
private class FakeAdapterStreams(private val responder: (String) -> String?) {
    private val queue = LinkedBlockingQueue<Int>()
    val written = ArrayList<String>()

    val input = object : InputStream() {
        override fun read(): Int = queue.take()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val first = queue.take()
            if (first == -1) return -1
            b[off] = first.toByte()
            var n = 1
            while (n < len) {
                val next = queue.peek() ?: break
                if (next == -1) break
                queue.take()
                b[off + n] = next.toByte()
                n++
            }
            return n
        }
    }

    val output = object : OutputStream() {
        private val line = StringBuilder()

        override fun write(b: Int) {
            if (b == '\r'.code) {
                val command = line.toString()
                line.setLength(0)
                written += command
                responder(command)?.let { push(it) }
            } else {
                line.append(b.toChar())
            }
        }
    }

    fun push(text: String) = text.forEach { queue.put(it.code) }
    fun endOfStream() = queue.put(-1)
}

class ElmStreamTransportTest {
    private val opened = ArrayList<ElmStreamTransport>()

    private fun transport(
        streams: FakeAdapterStreams,
        timeouts: ObdTimeouts = ObdTimeouts(reset = 400, adapter = 400, data = 400),
        onClose: () -> Unit = {},
    ): ElmStreamTransport = ElmStreamTransport(streams.input, streams.output, onClose, timeouts).also { opened += it }

    @AfterTest
    fun cleanup() {
        opened.forEach { it.close() }
    }

    @Test
    fun `manda el comando terminado en retorno de carro y devuelve la respuesta sin el aviso mayor que`() {
        val s = FakeAdapterStreams { if (it == "010C") "410C1AF8\r\r>" else null }
        val reply = transport(s).exchange("010C")
        assertEquals(listOf("010C"), s.written)
        assertEquals("410C1AF8\r\r", reply)
    }

    @Test
    fun `junta una respuesta que llega en pedazos`() {
        val s = FakeAdapterStreams { null }
        val t = transport(s, ObdTimeouts(data = 3_000, adapter = 3_000, reset = 3_000))
        thread {
            Thread.sleep(100)
            s.push("41 0C ")
            Thread.sleep(100)
            s.push("1A F8 \r")
            Thread.sleep(100)
            s.push("\r>")
        }
        assertEquals("41 0C 1A F8 \r\r", t.exchange("010C"))
    }

    @Test
    fun `lo que quedo de una respuesta anterior no se mezcla con la siguiente`() {
        val s = FakeAdapterStreams { "BUENA\r>" }
        val t = transport(s) // el hilo lector ya está corriendo, como en una conexión real
        s.push("BASURA SIN TERMINAR")
        Thread.sleep(100) // que el hilo lector la reciba
        assertEquals("BUENA\r", t.exchange("010D"))
    }

    @Test
    fun `dos comandos seguidos reciben cada uno su respuesta`() {
        val s = FakeAdapterStreams { c -> if (c == "010C") "AAA\r>" else "BBB\r>" }
        val t = transport(s)
        assertEquals("AAA\r", t.exchange("010C"))
        assertEquals("BBB\r", t.exchange("010D"))
    }

    @Test
    fun `un adaptador mudo da error de tiempo en vez de colgarse`() {
        val s = FakeAdapterStreams { null }
        val started = System.nanoTime()
        val e = assertFailsWith<IOException> { transport(s).exchange("010C") }
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue(e.message!!.contains("010C"), e.message)
        assertTrue(ms in 300..3_000, "tardó $ms ms")
    }

    @Test
    fun `cada tipo de comando tiene su propio plazo`() {
        val s = FakeAdapterStreams { null }
        val t = transport(s, ObdTimeouts(reset = 150, adapter = 1_500, data = 1_500))
        val started = System.nanoTime()
        assertFailsWith<IOException> { t.exchange("ATZ") }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_000, "ATZ debió usar el plazo corto del reinicio")
    }

    @Test
    fun `si el adaptador corta la conexion sale un error claro`() {
        val s = FakeAdapterStreams { null }
        val t = transport(s)
        thread {
            Thread.sleep(100)
            s.endOfStream()
        }
        val e = assertFailsWith<IOException> { t.exchange("010C") }
        assertTrue(e.message!!.contains("cerró"), e.message)
        // y los intentos siguientes fallan al instante
        assertFailsWith<IOException> { t.exchange("010D") }
    }

    @Test
    fun `cerrar desde otro hilo libera a quien espera`() {
        val s = FakeAdapterStreams { null }
        var closedCalled = false
        val t = transport(s, ObdTimeouts(reset = 10_000, adapter = 10_000, data = 10_000)) { closedCalled = true }
        thread {
            Thread.sleep(150)
            t.close()
        }
        val started = System.nanoTime()
        assertFailsWith<IOException> { t.exchange("010C") }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000, "no se liberó")
        assertTrue(closedCalled, "debe cerrar el socket real")
    }

    @Test
    fun `un error al escribir se informa`() {
        val failing = object : OutputStream() {
            override fun write(b: Int) = throw IOException("tubería rota")
        }
        val s = FakeAdapterStreams { null }
        val t = ElmStreamTransport(s.input, failing, {}, ObdTimeouts(reset = 300, adapter = 300, data = 300)).also { opened += it }
        val e = assertFailsWith<IOException> { t.exchange("010C") }
        assertTrue(e.message!!.contains("tubería"), e.message)
    }

    @Test
    fun `una sesion completa funciona sobre el transporte`() {
        val s = FakeAdapterStreams { c ->
            when (c) {
                "ATZ" -> "\r\rELM327 v2.1\r\r>"
                "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0" -> "OK\r\r>"
                "010C" -> "SEARCHING...\r410C1AF8\r\r>"
                else -> "?\r\r>"
            }
        }
        val session = ObdSession(transport(s, ObdTimeouts(reset = 2_000, adapter = 2_000, data = 2_000)))
        session.init()
        assertEquals("ELM327 v2.1", session.adapter)
        assertEquals(1726.0, session.read(0x0C)!!.value, 0.001)
    }
}
