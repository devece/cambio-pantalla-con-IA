package cl.tablero.core

/** Una app de la radio que podría manejar la caja del CAN bus (Raise, Hiworld, etc.) y lo que ofrece a otras apps. */
data class CanCandidate(
    val pkg: String,
    val label: String,
    val system: Boolean,
    val exportedServices: List<String>,
    val exportedReceivers: List<String>,
    /** Autoridades de proveedores de contenido que otras apps podrían consultar. */
    val providers: List<String>,
    /** Acciones de broadcast que aparecen en el manifiesto de la app. */
    val actions: List<String>,
)

/** Un puerto serie del sistema (/dev/tty…) y si esta app puede leerlo o escribirlo. */
data class SerialPortInfo(val path: String, val canRead: Boolean, val canWrite: Boolean)

/** Un broadcast que llegó de una de esas apps mientras la pantalla estaba abierta. */
data class CapturedBroadcast(val action: String, val count: Int, val lastExtras: List<String>)

data class CanProbeInfo(
    val candidates: List<CanCandidate>,
    val serialPorts: List<SerialPortInfo>,
    val captured: List<CapturedBroadcast>,
    /** Cuántas acciones se están escuchando. */
    val listening: Int,
)

/**
 * Busca si la radio comparte los datos del auto (RPM, clima, puertas) con otras apps. La caja del CAN habla solo con el
 * procesador de la radio; lo único que Tablero podría leer es lo que la app de CanBus de la radio reenvíe. Sin Android,
 * para poder probarlo en cualquier PC.
 */
object CanProbe {
    private val KEYWORDS = listOf(
        "canbus", "can_bus", "can-bus", "raise", "hiworld", "syu", "fyt", "rzc", "mcu", "vehicle",
        "cardata", "carinfo", "carsetting", "cansetting", "obd", "ford",
    )

    /** ¿Esta app (por paquete o nombre) parece manejar el CAN bus o los datos del auto? */
    fun isCandidate(pkg: String, label: String): Boolean {
        val text = "$pkg $label".lowercase()
        return KEYWORDS.any { it in text }
    }

    /** ¿Este puerto /dev puede ser el de la caja del CAN? (serie o de la MCU). */
    fun isSerialName(name: String): Boolean =
        listOf("ttyS", "ttyUSB", "ttyACM", "ttyMT", "ttyHS", "ttyAMA", "ttyHSL", "ttyGS", "mcu").any { name.startsWith(it) }

    /**
     * Saca de un AndroidManifest.xml compilado (binario) las acciones de broadcast que declara la app. El formato binario
     * guarda todos los textos en una tabla al principio; basta leerla. Devuelve vacío si el archivo no se entiende.
     */
    fun manifestActions(manifest: ByteArray): List<String> {
        val strings = runCatching { stringPool(manifest) }.getOrNull() ?: return emptyList()
        return strings.asSequence()
            .filter { it.length in 6..120 && ' ' !in it && '.' in it }
            .filter { !it.startsWith("android.permission.") && !it.startsWith("android.intent.category.") }
            .filter { !it.startsWith("http") && !it.endsWith(".xml") }
            .filter { n -> listOf("Activity", "Service", "Receiver", "Provider", "Application").none { n.endsWith(it) } }
            .filter {
                val l = it.lowercase()
                "action" in l || "broadcast" in l || "canbus" in l || "can_bus" in l || "carinfo" in l || "mcu" in l
            }
            .distinct()
            .toList()
    }

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, o: Int) = u16(b, o) or (u16(b, o + 2) shl 16)

    private fun stringPool(b: ByteArray): List<String> {
        require(b.size > 8 && u16(b, 0) == 0x0003) { "no es un manifiesto binario" }
        var pos = u16(b, 2) // tamaño del encabezado del documento
        require(u16(b, pos) == 0x0001) { "falta la tabla de textos" }
        val chunk = pos
        val count = u32(b, chunk + 8)
        val flags = u32(b, chunk + 16)
        val stringsStart = u32(b, chunk + 20)
        val offsetsAt = chunk + u16(b, chunk + 2)
        require(count in 0..100_000)
        val utf8 = flags and 0x100 != 0
        val out = ArrayList<String>(count)
        for (i in 0 until count) {
            pos = chunk + stringsStart + u32(b, offsetsAt + i * 4)
            if (utf8) {
                pos += if (b[pos].toInt() and 0x80 != 0) 2 else 1 // largo en caracteres (no se usa)
                var bl = b[pos].toInt() and 0xFF; pos++
                if (bl and 0x80 != 0) { bl = ((bl and 0x7F) shl 8) or (b[pos].toInt() and 0xFF); pos++ }
                out.add(String(b, pos, bl, Charsets.UTF_8))
            } else {
                var n = u16(b, pos); pos += 2
                if (n and 0x8000 != 0) { n = ((n and 0x7FFF) shl 16) or u16(b, pos); pos += 2 }
                out.add(String(b, pos, n * 2, Charsets.UTF_16LE))
            }
        }
        return out
    }

    fun report(info: CanProbeInfo): String = buildString {
        appendLine("Datos del auto (CAN bus)")
        appendLine("La caja del CAN habla solo con la radio. Aquí se ve si la radio comparte esos datos con otras apps.")

        appendLine()
        appendLine("Apps de CanBus o datos del auto (${info.candidates.size})")
        if (info.candidates.isEmpty()) {
            appendLine("No hay ninguna con nombre conocido. Si la radio muestra el clima o las RPM, dime cómo se llama esa app.")
        }
        info.candidates.forEach { c ->
            appendLine("· ${c.label} (${c.pkg})${if (c.system) " · de sistema" else ""}")
            if (c.exportedServices.isNotEmpty()) appendLine("    servicios abiertos: ${c.exportedServices.joinToString(", ") { it.substringAfterLast('.') }}")
            if (c.exportedReceivers.isNotEmpty()) appendLine("    receptores abiertos: ${c.exportedReceivers.joinToString(", ") { it.substringAfterLast('.') }}")
            if (c.providers.isNotEmpty()) appendLine("    proveedores de datos: ${c.providers.joinToString(", ")}")
            if (c.actions.isNotEmpty()) appendLine("    acciones: ${c.actions.take(MAX_ACTIONS).joinToString(", ")}" + if (c.actions.size > MAX_ACTIONS) " … (+${c.actions.size - MAX_ACTIONS})" else "")
        }

        appendLine()
        appendLine("Puertos serie (/dev)")
        if (info.serialPorts.isEmpty()) {
            appendLine("No se pudo listar o no hay.")
        } else {
            val open = info.serialPorts.filter { it.canRead || it.canWrite }
            appendLine("${info.serialPorts.size} puertos; accesibles para Tablero: ${open.size}")
            open.forEach { appendLine("  · ${it.path} (${if (it.canRead) "lee" else ""}${if (it.canRead && it.canWrite) " y " else ""}${if (it.canWrite) "escribe" else ""})") }
        }

        appendLine()
        appendLine("Escuchando ${info.listening} acciones de broadcast")
        if (info.captured.isEmpty()) {
            appendLine("No ha llegado nada. Con esta pantalla abierta: sube las RPM, cambia el clima, abre una puerta y toca Actualizar.")
        } else {
            appendLine("Llegaron ${info.captured.sumOf { it.count }} avisos:")
            info.captured.forEach { c ->
                appendLine("· ${c.action} ×${c.count}")
                c.lastExtras.forEach { appendLine("    $it") }
            }
        }
    }.trimEnd()

    const val MAX_ACTIONS = 12
}
