package cl.tablero.core

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Registro de fallos de la app: cierres inesperados y pantalla bloqueada. Sirve para saber qué le pasó a una radio que no
 * tenemos a mano: el usuario lo ve en Diagnóstico y lo comparte. No lleva nada personal (solo versión, hilo y pila de llamadas).
 * Sin Android, para poder probarlo en cualquier PC.
 */
object CrashLog {
    /** Tope del archivo: al pasarlo se descartan las entradas más viejas. */
    const val MAX_CHARS = 64 * 1024

    /** Separa una entrada de la siguiente (siempre al comienzo de línea). */
    const val MARK = "=== "

    /** Una entrada del registro. [kind] dice qué pasó (por ejemplo «Cierre inesperado» o «Pantalla bloqueada»). */
    fun entry(kind: String, atMillis: Long, version: String, thread: String, stack: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return buildString {
            append(MARK).append(fmt.format(Date(atMillis))).append(" UTC · ").append(kind).append('\n')
            append("Versión: ").append(version).append('\n')
            append("Hilo: ").append(thread).append('\n')
            append(stack.trimEnd()).append('\n')
        }
    }

    /** Agrega [entry] al final de [existing] y, si se pasa de [max], descarta las entradas más viejas (nunca parte una a medias). */
    fun append(existing: String, entry: String, max: Int = MAX_CHARS): String {
        val all = existing + entry
        if (all.length <= max) return all
        // Se corta al comienzo de una entrada. Si la última sola ya no cabe, se recorta su cola.
        val cut = all.length - max
        val next = all.indexOf("\n$MARK", cut - 1)
        return if (next >= 0 && next + 1 < all.length) all.substring(next + 1)
        else entry.take(max)
    }

    /** La pila de llamadas de un error como texto, con sus causas. */
    fun stackOf(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    /** La pila de un hilo ya vivo (por ejemplo el principal bloqueado), una llamada por línea. */
    fun stackOf(frames: Array<StackTraceElement>): String =
        frames.joinToString("\n") { "    at $it" }.ifEmpty { "(sin pila)" }

    /** Cuántas entradas tiene el registro. */
    fun count(log: String): Int = log.lineSequence().count { it.startsWith(MARK) }

    /** Lo que se muestra en el diagnóstico: las últimas [maxEntries] entradas, o un aviso si no hay. */
    fun summary(log: String, maxEntries: Int = 3): String {
        if (log.isBlank()) return "Ninguno registrado."
        val parts = log.split("\n$MARK").mapIndexed { i, s -> if (i == 0) s else MARK + s }.filter { it.isNotBlank() }
        val shown = parts.takeLast(maxEntries)
        val head = if (parts.size > shown.size) "Mostrando los últimos ${shown.size} de ${parts.size}.\n" else ""
        return head + shown.joinToString("\n") { it.trimEnd() }
    }
}
