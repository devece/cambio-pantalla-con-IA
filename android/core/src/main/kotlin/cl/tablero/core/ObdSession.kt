package cl.tablero.core

import java.util.TreeSet

/**
 * Los únicos comandos que la app le manda al adaptador. Nunca se envía texto libre.
 *
 * Son de solo lectura (modos 01, 03, 07, 09 y 0A del estándar OBD-II) más el borrado de códigos (modo 04, que solo
 * se pide desde [ObdSession.clearDtcs]) y unos pocos comandos `AT` para configurar el adaptador. No hay escritura de
 * parámetros ni modos de servicio del fabricante.
 */
object ObdCommands {
    private val adapter = setOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATDPN", "ATRV")
    private val data = Regex("^(01[0-9A-F]{2}|0902|03|04|07|0A)$")

    fun isAllowed(command: String): Boolean = command in adapter || data.matches(command)
}

/**
 * Habla con el auto a través de un adaptador ELM327. Todo es bloqueante: hay que usarlo fuera del hilo principal.
 * Una sesión no es segura para usar desde dos hilos a la vez.
 *
 * Los errores de comunicación del canal ([java.io.IOException]) se propagan tal cual; los que avisa el adaptador o el auto
 * salen como [ObdException]. Un dato que el auto simplemente no entrega no es un error: la función devuelve null.
 */
class ObdSession(private val transport: ObdTransport) {
    /** Cómo se presentó el adaptador (por ejemplo `ELM327 v1.5`), tras [init]. */
    var adapter: String? = null
        private set

    private var protocol: Int? = null

    /** Prepara el adaptador. Hay que llamarlo una vez al conectar. */
    fun init() {
        val reply = send("ATZ")
        ObdParser.lines(reply, "ATZ") // si el adaptador avisó un error, sale aquí
        adapter = reply.split('\r', '\n').map { it.trim().trimStart('>').trim() }.firstOrNull { it.uppercase().contains("ELM") }
            ?: throw ObdException(ObdError.NOT_ELM327, "El dispositivo no se presentó como un adaptador ELM327")
        // Sin eco, sin saltos de línea extra, sin espacios, sin cabeceras, y buscar el protocolo del auto solo.
        for (c in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) {
            val lines = ObdParser.lines(send(c), c)
            if (lines.none { it.endsWith("OK") }) throw ObdException(ObdError.BAD_RESPONSE, "El adaptador no aceptó «$c»")
        }
    }

    /** Los datos (PIDs) que el auto dice entregar. Los marcadores de bloque (0x20, 0x40...) no se incluyen. */
    fun supportedPids(): Set<Int> {
        val found = TreeSet<Int>()
        var base = 0x00
        while (base <= 0xC0) {
            val mask = try {
                readMode01(base)
            } catch (e: ObdException) {
                if (e.error == ObdError.NO_DATA && base > 0) break // el auto no tiene más bloques
                throw e
            }
            found += ObdPids.supportedFrom(base, mask)
            if ((base + 0x20) !in found) break
            base += 0x20
        }
        return found.filter { it % 0x20 != 0 }.toSet()
    }

    /** Lee un dato. Devuelve null si el auto no lo entrega. */
    fun read(pid: Int): ObdReading? {
        val spec = ObdPids.byPid(pid) ?: throw IllegalArgumentException("PID desconocido: ${hex2(pid)}")
        val data = try {
            readMode01(pid)
        } catch (e: ObdException) {
            if (e.error == ObdError.NO_DATA) return null
            throw e
        }
        if (data.size < spec.bytes) throw ObdException(ObdError.BAD_RESPONSE, "${spec.name}: faltan datos")
        return ObdReading(spec, spec.decode(data))
    }

    /** El voltaje que mide el propio adaptador en el puerto del auto (sirve aunque el motor esté apagado). Null si no lo informa. */
    fun adapterVoltage(): Double? {
        val line = try {
            ObdParser.lines(send("ATRV"), "ATRV").firstOrNull()
        } catch (e: ObdException) {
            if (e.error == ObdError.UNKNOWN_COMMAND) return null else throw e
        } ?: return null
        return Regex("^([0-9]+(?:\\.[0-9]+)?)V?$").matchEntire(line)?.groupValues?.get(1)?.toDouble()
    }

    /** Luz del motor, cantidad de códigos y estado de los chequeos del auto. Null si el auto no lo entrega. */
    fun monitorStatus(): MonitorStatus? {
        val data = try {
            readMode01(0x01)
        } catch (e: ObdException) {
            if (e.error == ObdError.NO_DATA) return null
            throw e
        }
        if (data.size < 4) throw ObdException(ObdError.BAD_RESPONSE, "Estado del auto incompleto")
        return MonitorStatus.decode(data)
    }

    /** Los códigos de falla de la lista pedida (por ejemplo `P0133`), sin repetidos. Vacío si no hay. */
    fun readDtcs(kind: DtcKind = DtcKind.STORED): List<String> {
        val messages = try {
            ObdParser.messages(send(kind.command), kind.command)
        } catch (e: ObdException) {
            if (e.error == ObdError.NO_DATA) return emptyList() // varios autos contestan así cuando no hay códigos
            throw e
        }
        val can = isCan()
        val out = LinkedHashSet<String>()
        for (m in messages) {
            if (m.isEmpty() || (m[0].toInt() and 0xFF) != kind.header) continue
            val body = m.drop(1).map { it.toInt() and 0xFF }
            // En CAN el primer dato es cuántos códigos vienen; en los protocolos antiguos vienen directo, de a pares.
            val hasCount = can ?: (body.size % 2 == 1)
            val pairs = if (hasCount) body.drop(1) else body
            for (i in 0 until pairs.size / 2) {
                val a = pairs[2 * i]
                val b = pairs[2 * i + 1]
                if (a == 0 && b == 0) continue // relleno
                out += Dtc.decode(a, b)
            }
        }
        return out.toList()
    }

    /**
     * Borra los códigos de falla y apaga la luz del motor. **Reinicia también los chequeos del auto** ([MonitorStatus]):
     * la pantalla tiene que avisarlo y pedir confirmación antes de llamar a esta función.
     */
    fun clearDtcs() {
        val messages = ObdParser.messages(send("04"), "04")
        if (messages.none { it.isNotEmpty() && (it[0].toInt() and 0xFF) == 0x44 }) {
            throw ObdException(ObdError.BAD_RESPONSE, "El auto no confirmó el borrado")
        }
    }

    /**
     * El número de identificación (VIN) del auto, solo para mostrarlo: **no se guarda ni se envía a ningún lado**.
     * Null si el auto no lo entrega.
     */
    fun vin(): String? {
        val messages = try {
            ObdParser.messages(send("0902"), "0902")
        } catch (e: ObdException) {
            if (e.error == ObdError.NO_DATA) return null
            throw e
        }
        // Cada mensaje: 49 02 <número de parte> <datos>. En CAN llega todo en uno; en los protocolos antiguos, en cinco.
        val chars = messages
            .filter { it.size > 3 && (it[0].toInt() and 0xFF) == 0x49 && (it[1].toInt() and 0xFF) == 0x02 }
            .sortedBy { it[2].toInt() and 0xFF }
            .flatMap { m -> m.drop(3).map { it.toInt() and 0xFF } }
            .filter { it in 0x20..0x7E } // el relleno son ceros
            .map { it.toChar() }
        return chars.joinToString("").trim().ifEmpty { null }
    }

    /* ---------- piezas internas ---------- */

    /** Pide un dato del modo 01 y devuelve sus bytes (sin la cabecera `41 xx`). */
    private fun readMode01(pid: Int): IntArray {
        val command = "01" + hex2(pid)
        val messages = ObdParser.messages(send(command), command)
        val match = messages.firstOrNull { it.size >= 2 && (it[0].toInt() and 0xFF) == 0x41 && (it[1].toInt() and 0xFF) == pid }
            ?: throw ObdException(ObdError.BAD_RESPONSE, "Respuesta inesperada a «$command»")
        return IntArray(match.size - 2) { match[it + 2].toInt() and 0xFF }
    }

    /** True si el auto habla CAN (los protocolos 6 a 9 del ELM327); null si el adaptador no lo dice. */
    private fun isCan(): Boolean? {
        val p = protocol ?: run {
            val line = try {
                ObdParser.lines(send("ATDPN"), "ATDPN").firstOrNull()
            } catch (e: ObdException) {
                if (e.error == ObdError.UNKNOWN_COMMAND) null else throw e
            } ?: return null
            // «A6» = protocolo 6 elegido automáticamente; «6» = elegido a mano.
            line.last().digitToIntOrNull(16)?.also { protocol = it } ?: return null
        }
        return p in 6..9
    }

    private fun send(command: String): String {
        require(ObdCommands.isAllowed(command)) { "Comando no permitido: $command" }
        return transport.exchange(command)
    }

    private fun hex2(n: Int): String = n.toString(16).uppercase().padStart(2, '0')
}
