package cl.tablero.core

import java.io.IOException

/**
 * Un adaptador ELM327 simulado para las pruebas: contesta lo que se le preparó (o lo que decida [handler]),
 * anota cada comando que recibe y se puede cerrar.
 */
class FakeElm(replies: Map<String, String> = emptyMap()) : ObdChannel {
    private val replies = HashMap(replies)
    val sent = ArrayList<String>()
    var closed = false
        private set

    /** Si se pone, decide la respuesta de cada comando y manda sobre lo preparado. Puede lanzar [IOException]. */
    var handler: ((String) -> String)? = null

    /** Hace que el próximo comando falle con este error de conexión. */
    var failWith: IOException? = null

    fun reply(command: String, response: String) = apply { replies[command] = response }

    override fun exchange(command: String): String {
        sent += command
        if (closed) throw IOException("La conexión se cerró")
        failWith?.let { throw it }
        handler?.let { return it(command) }
        return replies[command] ?: throw IllegalStateException("No se preparó respuesta para «$command»")
    }

    override fun close() {
        closed = true
    }

    companion object {
        /** Las respuestas de un adaptador ELM327 al configurarlo. */
        val INIT_OK: Map<String, String> = mapOf(
            "ATZ" to "\r\rELM327 v2.1\r\r",
            "ATE0" to "ATE0\rOK\r\r",
            "ATL0" to "OK\r\r",
            "ATS0" to "OK\r\r",
            "ATH0" to "OK\r\r",
            "ATAT1" to "OK\r\r",
            "ATSP0" to "OK\r\r",
        )
    }
}
