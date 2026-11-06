package cl.tablero.core

/** Un dato que el auto puede entregar (un "PID" del modo 01 del estándar OBD-II, SAE J1979). */
class PidSpec(
    val pid: Int,
    /** Nombre corto estable para usar en el código (no se muestra). */
    val key: String,
    /** Cómo se llama en pantalla. */
    val name: String,
    val unit: String,
    /** Cuántos bytes de datos trae la respuesta. */
    val bytes: Int,
    /** Convierte los bytes (0..255) en el valor, en las unidades de [unit]. */
    val decode: (IntArray) -> Double,
)

/** Un valor leído del auto. */
data class ObdReading(val spec: PidSpec, val value: Double)

object ObdPids {
    val all: List<PidSpec> = listOf(
        PidSpec(0x04, "load", "Carga del motor", "%", 1) { it[0] * 100.0 / 255 },
        PidSpec(0x05, "coolant", "Temperatura del motor", "°C", 1) { it[0] - 40.0 },
        PidSpec(0x0B, "map", "Presión de admisión", "kPa", 1) { it[0].toDouble() },
        PidSpec(0x0C, "rpm", "Revoluciones", "rpm", 2) { (256 * it[0] + it[1]) / 4.0 },
        PidSpec(0x0D, "speed", "Velocidad", "km/h", 1) { it[0].toDouble() },
        PidSpec(0x0F, "intake", "Temperatura del aire de admisión", "°C", 1) { it[0] - 40.0 },
        PidSpec(0x11, "throttle", "Posición del acelerador", "%", 1) { it[0] * 100.0 / 255 },
        PidSpec(0x2F, "fuel", "Nivel de combustible", "%", 1) { it[0] * 100.0 / 255 },
        PidSpec(0x42, "voltage", "Voltaje del auto", "V", 2) { (256 * it[0] + it[1]) / 1000.0 },
        // Solo algunos autos recientes lo entregan; en la mayoría el kilometraje real del tablero no se puede leer por OBD.
        PidSpec(0xA6, "odometer", "Kilometraje", "km", 4) {
            ((it[0].toLong() shl 24) or (it[1].toLong() shl 16) or (it[2].toLong() shl 8) or it[3].toLong()) / 10.0
        },
    )

    private val byPid = all.associateBy { it.pid }

    fun byPid(pid: Int): PidSpec? = byPid[pid]

    /**
     * Los PIDs que el auto dice soportar en el bloque que empieza en [base] (0x00, 0x20, 0x40...), a partir de los 4 bytes
     * de máscara de la respuesta: el bit más alto del primer byte es el PID `base + 1`, y el último bit es `base + 0x20`
     * (que significa "hay otro bloque más").
     */
    fun supportedFrom(base: Int, mask: IntArray): Set<Int> {
        val out = LinkedHashSet<Int>()
        for (i in 0 until 32) {
            val byte = mask.getOrElse(i / 8) { 0 }
            if ((byte shr (7 - i % 8)) and 1 == 1) out += base + i + 1
        }
        return out
    }
}
