package cl.tablero.core

/**
 * Movimiento de la pantalla de inicio (public/js/motion.js). Una sola opción con tres niveles:
 * ninguno, bienvenida + auto vivo, o además la carretera al manejar. Sin Android, para poder probarlo en cualquier PC.
 */
object Motion {
    const val OFF = "off"
    const val VIVO = "vivo"
    const val COMPLETO = "completo"
    const val DEFAULT = VIVO

    /** Nivel -> nombre que se ve en Ajustes. */
    val OPTIONS: Map<String, String> = linkedMapOf(
        OFF to "Sin movimiento",
        VIVO to "Bienvenida y auto vivo (liviano)",
        COMPLETO to "Además, carretera al manejar",
    )

    /** El nivel si es válido; si no, el valor de fábrica. */
    fun clean(raw: String?): String = if (raw != null && raw in OPTIONS) raw else DEFAULT
}
