package cl.tablero.core

import java.io.IOException

/**
 * El canal con el adaptador ELM327 (Bluetooth, Bluetooth LE, Wi-Fi o USB).
 * La app lo implementa con el canal real; en las pruebas se simula, así todo el protocolo se prueba sin adaptador.
 */
interface ObdTransport {
    /**
     * Envía [command] (sin salto de línea) y devuelve todo lo que respondió el adaptador hasta su aviso `>`,
     * sin incluir ese aviso. Bloquea hasta tener la respuesta.
     *
     * @throws IOException si se perdió la conexión o el adaptador no respondió a tiempo.
     */
    @Throws(IOException::class)
    fun exchange(command: String): String
}

/** Qué salió mal con el adaptador o con el auto, en términos que la pantalla puede explicar. */
enum class ObdError {
    /** El auto no entrega ese dato (normal: no todos los autos soportan todo). */
    NO_DATA,

    /** El adaptador no logra hablar con el auto (por ejemplo, el auto está apagado). */
    UNABLE_TO_CONNECT,

    /** Error en el bus del auto o en el cable. */
    BUS_ERROR,

    /** El adaptador está ocupado, se reinició por bajo voltaje o se quedó sin espacio. */
    ADAPTER_BUSY,

    /** El adaptador no entendió el comando (muchos clones baratos no soportan todos). */
    UNKNOWN_COMMAND,

    /** La respuesta no tiene el formato esperado. */
    BAD_RESPONSE,

    /** Lo que contestó no parece un ELM327. */
    NOT_ELM327,
}

class ObdException(val error: ObdError, message: String) : Exception(message)
