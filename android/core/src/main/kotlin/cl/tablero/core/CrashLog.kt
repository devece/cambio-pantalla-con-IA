package cl.tablero.core

/** Fecha y hora UTC sin java.time (que en Android solo existe desde la API 26) ni SimpleDateFormat (no es seguro entre hilos). */
internal object FechaUtc {
    private fun pad(n: Int, ancho: Int = 2): String = n.toString().padStart(ancho, '0')

    /** Devuelve (año, mes, día) del calendario gregoriano para los días transcurridos desde 1970-01-01. */
    private fun civil(dias: Long): Triple<Int, Int, Int> {
        val z = dias + 719468
        val era = Math.floorDiv(z, 146097L)
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val dia = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val mes = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val anio = (yoe + era * 400 + (if (mes <= 2) 1 else 0)).toInt()
        return Triple(anio, mes, dia)
    }

    fun fecha(ms: Long): String {
        val (a, m, d) = civil(Math.floorDiv(ms, 86_400_000L))
        return "${pad(a, 4)}-${pad(m)}-${pad(d)}"
    }

    fun fechaHora(ms: Long): String {
        val resto = Math.floorMod(ms, 86_400_000L)
        val h = (resto / 3_600_000L).toInt()
        val mi = (resto / 60_000L % 60).toInt()
        val s = (resto / 1000L % 60).toInt()
        return "${fecha(ms)} ${pad(h)}:${pad(mi)}:${pad(s)} UTC"
    }
}

/** Registro de texto de fallos. Cada registro empieza con una línea que comienza con [MARCA]. */
object CrashLog {
    const val MAX_CHARS: Int = 40_000

    private const val MARCA = "##### "
    private const val SIN_PILA = "(sin pila disponible)"
    private const val MAX_CAUSAS = 8

    fun entry(kind: String, atMillis: Long, version: String, thread: String, stack: String): String {
        val sb = StringBuilder()
        sb.append(MARCA).append(FechaUtc.fechaHora(atMillis)).append(" | ").append(linea(kind)).append('\n')
        sb.append("Versión: ").append(linea(version)).append('\n')
        sb.append("Hilo: ").append(linea(thread)).append('\n')
        val cuerpo = stack.trimEnd('\n', '\r').ifEmpty { SIN_PILA }
        for (l in cuerpo.split('\n')) {
            val limpia = l.trimEnd('\r')
            // Una línea de la pila nunca debe parecer una marca de registro.
            if (limpia.startsWith(MARCA.trimEnd())) sb.append(' ')
            sb.append(limpia).append('\n')
        }
        return sb.toString()
    }

    private fun linea(s: String): String = s.replace('\r', ' ').replace('\n', ' ')

    fun append(existing: String, entry: String, max: Int = MAX_CHARS): String {
        if (max <= 0) return ""
        var nuevo = entry
        if (nuevo.isNotEmpty() && !nuevo.endsWith("\n")) nuevo += "\n"
        if (nuevo.length > max) {
            // El último solo no cabe: se recorta ese, conservando el principio y el salto final.
            var corte = max - 1
            if (corte > 0 && Character.isHighSurrogate(nuevo[corte - 1])) corte--
            return nuevo.substring(0, corte) + "\n"
        }
        val inicios = inicios(existing)
        if (inicios.isEmpty()) return nuevo
        var base = existing.substring(inicios[0])
        if (!base.endsWith("\n")) base += "\n"
        if (base.length + nuevo.length <= max) return base + nuevo
        // Descartar registros viejos enteros hasta que quepa.
        for (i in 1 until inicios.size) {
            val resto = existing.length - inicios[i] + (if (existing.endsWith("\n")) 0 else 1)
            if (resto + nuevo.length <= max) {
                val cola = existing.substring(inicios[i])
                return (if (cola.endsWith("\n")) cola else cola + "\n") + nuevo
            }
        }
        return nuevo
    }

    /** Posiciones donde empieza un registro (marca al comienzo de línea). */
    private fun inicios(log: String): List<Int> {
        val res = ArrayList<Int>()
        var i = 0
        while (i <= log.length - MARCA.length) {
            val enLinea = i == 0 || log[i - 1] == '\n'
            if (enLinea && log.startsWith(MARCA, i)) {
                res.add(i)
                i += MARCA.length
            } else {
                val sig = log.indexOf('\n', i)
                if (sig < 0) break
                i = sig + 1
            }
        }
        return res
    }

    fun stackOf(t: Throwable): String {
        val sb = StringBuilder()
        val vistos = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var actual: Throwable? = t
        var n = 0
        while (actual != null && vistos.add(actual) && n <= MAX_CAUSAS) {
            if (n > 0) sb.append("Caused by: ")
            sb.append(try { actual.toString() } catch (e: Throwable) { actual.javaClass.name }).append('\n')
            val frames = try { actual.stackTrace } catch (e: Throwable) { emptyArray() }
            for (f in frames) sb.append("\tat ").append(f).append('\n')
            actual = actual.cause
            n++
        }
        return sb.toString()
    }

    fun stackOf(frames: Array<StackTraceElement>): String {
        if (frames.isEmpty()) return SIN_PILA + "\n"
        val sb = StringBuilder()
        for (f in frames) sb.append("\tat ").append(f).append('\n')
        return sb.toString()
    }

    fun count(log: String): Int = inicios(log).size

    fun summary(log: String, maxEntries: Int = 3): String {
        if (log.isBlank()) return "Ninguno registrado."
        val inicios = inicios(log)
        if (inicios.isEmpty()) return log.trimEnd() + "\n"
        val mostrar = maxEntries.coerceAtLeast(1).coerceAtMost(inicios.size)
        val desde = inicios[inicios.size - mostrar]
        val cola = log.substring(desde)
        return if (inicios.size > mostrar) "Mostrando los últimos $mostrar de ${inicios.size}.\n$cola" else cola
    }
}
