package cl.tablero.core

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Cuánto esperar la respuesta del adaptador, en milisegundos, según el tipo de comando. */
data class ObdTimeouts(
    /** `ATZ` (reinicio): los clones baratos pueden tardar varios segundos. */
    val reset: Long = 6_000,
    /** Los demás comandos `AT`, que contesta el propio adaptador. */
    val adapter: Long = 3_000,
    /** Pedidos al auto. El primero busca el protocolo y puede tardar bastante (`SEARCHING...`). */
    val data: Long = 12_000,
)

/**
 * Habla con un adaptador ELM327 a través de un par de flujos de bytes (el socket Bluetooth o TCP que abra la app).
 *
 * Los flujos de Bluetooth no tienen tiempo de espera propio: si el adaptador se calla, una lectura se queda esperando
 * para siempre. Por eso la lectura corre en un hilo aparte y [exchange] espera hasta un plazo; si se acaba, falla con
 * [IOException] en vez de colgarse. Cerrar el canal desde otro hilo también libera a quien esté esperando.
 *
 * @param closeAction cierra el socket real (se llama al cerrar).
 */
class ElmStreamTransport(
    private val input: InputStream,
    private val output: OutputStream,
    private val closeAction: () -> Unit = {},
    private val timeouts: ObdTimeouts = ObdTimeouts(),
) : ObdChannel {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val buffer = StringBuilder()
    private var failure: IOException? = null
    private val sendLock = Any()

    private val reader = Thread({ readLoop() }, "obd-lector").apply {
        isDaemon = true
        start()
    }

    private fun readLoop() {
        val bytes = ByteArray(256)
        try {
            while (true) {
                val n = input.read(bytes)
                if (n < 0) {
                    fail(EOFException("El adaptador cerró la conexión"))
                    return
                }
                lock.withLock {
                    buffer.append(String(bytes, 0, n, Charsets.ISO_8859_1))
                    changed.signalAll()
                }
            }
        } catch (e: IOException) {
            fail(e)
        }
    }

    private fun fail(e: IOException) {
        lock.withLock {
            if (failure == null) failure = e
            changed.signalAll()
        }
    }

    @Throws(IOException::class)
    override fun exchange(command: String): String = synchronized(sendLock) { exchangeLocked(command) }

    private fun exchangeLocked(command: String): String {
        lock.lock()
        try {
            failure?.let { throw IOException(it.message ?: "Se perdió la conexión con el adaptador", it) }
            buffer.setLength(0) // lo que haya quedado de una respuesta anterior ya no sirve
        } finally {
            lock.unlock()
        }
        try {
            output.write((command + "\r").toByteArray(Charsets.ISO_8859_1))
            output.flush()
        } catch (e: IOException) {
            fail(e)
            throw e
        }
        val limit = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutFor(command))
        lock.lock()
        try {
            while (true) {
                val end = buffer.indexOf(">")
                if (end >= 0) {
                    val reply = buffer.substring(0, end)
                    buffer.delete(0, end + 1)
                    return reply
                }
                failure?.let { throw IOException(it.message ?: "Se perdió la conexión con el adaptador", it) }
                val left = limit - System.nanoTime()
                if (left <= 0) throw IOException("El adaptador no respondió a «$command»")
                changed.awaitNanos(left)
            }
        } finally {
            lock.unlock()
        }
    }

    private fun timeoutFor(command: String): Long = when {
        command == "ATZ" -> timeouts.reset
        command.startsWith("AT") -> timeouts.adapter
        else -> timeouts.data
    }

    override fun close() {
        fail(IOException("La conexión se cerró"))
        try {
            closeAction()
        } catch (e: Exception) {
            // ya estaba cerrado
        }
        try {
            input.close()
        } catch (e: Exception) {
        }
        try {
            output.close()
        } catch (e: Exception) {
        }
        reader.interrupt()
    }
}
