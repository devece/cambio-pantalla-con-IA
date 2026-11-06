package cl.tablero.core

import java.io.IOException

/** Explica en palabras simples qué pasó y qué puede hacer el usuario. */
object ObdMessages {
    fun explain(e: Throwable): String = when (e) {
        is ObdException -> explain(e.error)
        is IOException -> e.message?.takeIf { it.isNotBlank() }
            ?: "Se perdió la conexión con el adaptador. Revisa que esté bien enchufado."
        else -> "Pasó algo inesperado al hablar con el adaptador."
    }

    fun explain(error: ObdError): String = when (error) {
        ObdError.NO_DATA -> "El auto no entrega ese dato."
        ObdError.UNABLE_TO_CONNECT ->
            "El adaptador funciona, pero no logra hablar con el auto. Enciende el auto (o gira la llave a la posición de encendido) y vuelve a probar."
        ObdError.BUS_ERROR ->
            "Hay un error de comunicación con el auto. Revisa que el adaptador esté bien enchufado al puerto OBD."
        ObdError.ADAPTER_BUSY ->
            "El adaptador está ocupado o se reinició por falta de voltaje. Espera unos segundos y vuelve a probar."
        ObdError.UNKNOWN_COMMAND ->
            "El adaptador no entendió uno de los comandos. Puede ser un clon incompatible."
        ObdError.BAD_RESPONSE -> "El adaptador respondió algo inesperado."
        ObdError.NOT_ELM327 -> "El dispositivo elegido no parece un adaptador ELM327. Revisa que sea el correcto."
    }
}
