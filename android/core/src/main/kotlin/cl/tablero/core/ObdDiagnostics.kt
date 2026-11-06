package cl.tablero.core

/** Qué lista de códigos de falla pedir. */
enum class DtcKind(
    /** Comando del estándar OBD-II. */
    val command: String,
    /** Primer byte de la respuesta. */
    val header: Int,
) {
    /** Los guardados: los que encienden la luz del motor. */
    STORED("03", 0x43),

    /** Fallas detectadas que todavía no encienden la luz. */
    PENDING("07", 0x47),

    /** Las permanentes: no se borran con el comando de borrado, solo desaparecen al repararse. */
    PERMANENT("0A", 0x4A),
}

object Dtc {
    private val letters = charArrayOf('P', 'C', 'B', 'U')

    /**
     * Dos bytes de la respuesta -> código de falla, por ejemplo `0x01 0x33` -> `P0133`.
     * Los dos primeros bits eligen la letra (motor/transmisión, chasis, carrocería, comunicación),
     * los dos siguientes el primer dígito, y el resto son dígitos hexadecimales.
     */
    fun decode(a: Int, b: Int): String {
        val letter = letters[(a shr 6) and 0x3]
        val d1 = (a shr 4) and 0x3
        val d2 = a and 0xF
        return letter + d1.toString() + d2.toString(16).uppercase() + b.toString(16).uppercase().padStart(2, '0')
    }
}

/** Un chequeo interno que hace el auto (los "monitores" que se miran en una revisión de emisiones). */
data class Monitor(val name: String, val supported: Boolean, val complete: Boolean)

/** El estado general: luz del motor, cantidad de códigos y cómo van los monitores (modo 01, PID 01). */
data class MonitorStatus(
    val milOn: Boolean,
    val dtcCount: Int,
    /** True si es un motor diésel (los monitores son otros). */
    val compressionIgnition: Boolean,
    val monitors: List<Monitor>,
) {
    /** Los monitores que el auto tiene y todavía no terminó de correr. */
    val pending: List<Monitor> get() = monitors.filter { it.supported && !it.complete }

    companion object {
        private val common = listOf("Fallas de encendido", "Sistema de combustible", "Componentes del motor")
        private val sparkNames = listOf(
            "Catalizador", "Catalizador calefaccionado", "Sistema de evaporación", "Aire secundario",
            "Aire acondicionado", "Sensor de oxígeno", "Calefactor del sensor de oxígeno", "Recirculación de gases (EGR)",
        )
        private val dieselNames = listOf(
            "Catalizador NMHC", "Postratamiento de NOx", null, "Presión del turbo",
            null, "Sensor de gases de escape", "Filtro de partículas", "EGR o distribución variable",
        )

        /** Los 4 bytes de datos de la respuesta a `0101` (A, B, C, D). */
        fun decode(d: IntArray): MonitorStatus {
            require(d.size >= 4) { "Hacen falta 4 bytes" }
            val a = d[0]
            val b = d[1]
            val isDiesel = (b shr 3) and 1 == 1
            val monitors = ArrayList<Monitor>()
            // Los tres comunes: bits 0 a 2 de B dicen si el auto los tiene; bits 4 a 6, si todavía NO terminaron.
            common.forEachIndexed { i, name ->
                monitors += Monitor(name, supported = (b shr i) and 1 == 1, complete = (b shr (4 + i)) and 1 == 0)
            }
            // El resto: C dice cuáles tiene el auto, D cuáles todavía NO terminaron.
            val names = if (isDiesel) dieselNames else sparkNames
            names.forEachIndexed { i, name ->
                if (name != null) monitors += Monitor(name, supported = (d[2] shr i) and 1 == 1, complete = (d[3] shr i) and 1 == 0)
            }
            return MonitorStatus(
                milOn = (a shr 7) and 1 == 1,
                dtcCount = a and 0x7F,
                compressionIgnition = isDiesel,
                monitors = monitors,
            )
        }
    }
}
