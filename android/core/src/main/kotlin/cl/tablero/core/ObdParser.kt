package cl.tablero.core

import java.util.TreeMap

/**
 * Lee lo que responde un adaptador ELM327.
 *
 * Los adaptadores (sobre todo los clones baratos) responden de formas distintas: con o sin eco del comando, con o sin espacios,
 * con avisos como `SEARCHING...`, y las respuestas largas vienen en varias líneas numeradas (`0:`, `1:`...).
 * Aquí se normaliza todo eso para que el resto del código trabaje con bytes.
 */
object ObdParser {
    private val hex = Regex("^[0-9A-F]+$")
    private val indexed = Regex("^([0-9A-F]):([0-9A-F]*)$")

    /** Tres dígitos hexadecimales seguidos solos en una línea: es el aviso del largo de una respuesta en varias líneas, no un dato (un dato tiene siempre dígitos pares). */
    private val lengthHeader = Regex("^[0-9A-F]{3}$")

    /** Sin espacios ni saltos de línea ni el aviso `>`, y en mayúsculas. */
    fun normalize(s: String): String = s.filter { !it.isWhitespace() && it != '>' }.uppercase()

    /**
     * Las líneas útiles de la respuesta: sin eco del comando, sin `SEARCHING...`, ya normalizadas.
     * Si el adaptador avisó de un error (`NO DATA`, `UNABLE TO CONNECT`, `?`...), lanza [ObdException].
     */
    fun lines(raw: String, command: String = ""): List<String> {
        val echo = normalize(command)
        val out = ArrayList<String>()
        for (line in raw.split('\r', '\n')) {
            val n = normalize(line)
            if (n.isEmpty()) continue
            if (echo.isNotEmpty() && n == echo) continue
            if (n.startsWith("SEARCHING")) continue
            errorOf(n)?.let { throw ObdException(it, "El adaptador respondió «${line.trim()}» a «$command»") }
            out += n
        }
        return out
    }

    private fun errorOf(n: String): ObdError? = when {
        n == "NODATA" -> ObdError.NO_DATA
        n == "UNABLETOCONNECT" -> ObdError.UNABLE_TO_CONNECT
        n == "?" -> ObdError.UNKNOWN_COMMAND
        n.startsWith("BUSINIT") -> if (n.contains("ERROR")) ObdError.BUS_ERROR else null // «BUS INIT: OK» es solo un aviso
        n == "BUSBUSY" || n == "STOPPED" || n == "BUFFERFULL" || n == "LVRESET" || n == "ACTALERT" || n == "LPALERT" -> ObdError.ADAPTER_BUSY
        n.contains("ERROR") || n.startsWith("ERR") -> ObdError.BUS_ERROR
        else -> null
    }

    /**
     * Los mensajes de datos de la respuesta, como bytes. Cada ECU del auto que contesta es un mensaje;
     * las respuestas largas (varias líneas `0:`, `1:`...) se unen en uno solo. Lo que no son datos (por ejemplo `OK`) se ignora.
     *
     * @throws ObdException si el adaptador avisó un error, o si no vino ningún dato.
     */
    fun messages(raw: String, command: String = ""): List<ByteArray> {
        val messages = ArrayList<ByteArray>()
        var junk: String? = null
        val group = TreeMap<Int, String>()

        fun flush() {
            if (group.isEmpty()) return
            toBytes(group.values.joinToString(""))?.let { messages += it }
            group.clear()
        }

        for (n in lines(raw, command)) {
            val m = indexed.matchEntire(n)
            if (m != null) {
                val index = m.groupValues[1].toInt(16)
                if (index == 0) flush() // empieza otra respuesta larga (otra ECU)
                group[index] = m.groupValues[2]
                continue
            }
            flush()
            if (lengthHeader.matches(n)) continue
            val bytes = toBytes(n)
            if (bytes != null) messages += bytes else junk = n
        }
        flush()
        if (messages.isEmpty()) {
            throw ObdException(ObdError.BAD_RESPONSE, "Sin datos en la respuesta a «$command»" + (junk?.let { ": $it" } ?: ""))
        }
        return messages
    }

    /** `"410C1AF8"` -> bytes. Null si no es hexadecimal válido de largo par. */
    fun toBytes(s: String): ByteArray? {
        if (s.isEmpty() || s.length % 2 != 0 || !hex.matches(s)) return null
        return ByteArray(s.length / 2) { i -> s.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
    }
}
