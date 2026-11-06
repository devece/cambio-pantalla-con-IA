package cl.tablero.core

/**
 * Qué hace un botón del volante. [id] es como se guarda en la radio: no cambiarlo.
 * Un botón que no está asignado hace lo que haga Android con esa tecla (la música va a la app que suena, el volumen al sistema).
 */
enum class WheelAction(val id: String, val label: String) {
    PLAY_PAUSE("play", "Play / pausa"),
    NEXT("next", "Canción siguiente"),
    PREVIOUS("previous", "Canción anterior"),
    VOLUME_UP("volume_up", "Subir volumen"),
    VOLUME_DOWN("volume_down", "Bajar volumen"),
    MUTE("mute", "Silenciar"),
    NAVIGATE("navigate", "Abrir el mapa"),
    MUSIC("music", "Abrir la música"),
    VOICE("voice", "Asistente de voz"),
    HOME("home", "Ir al inicio"),
    NOTHING("nothing", "No hacer nada");

    companion object {
        fun fromId(id: String): WheelAction? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Los botones del volante que el usuario asignó: código de tecla de Android -> acción.
 * Casi todas las radios convierten los botones del volante en teclas normales (siguiente, anterior, volumen, voz…);
 * aquí solo se decide qué hace cada una mientras el launcher está al frente.
 */
object WheelKeys {
    /** Cuántos botones se pueden asignar (un volante trae unos 8 a 12). */
    const val MAX = 16

    // Códigos de KeyEvent. Se repiten aquí para poder probarlo sin Android.
    private const val KEYCODE_HOME = 3
    private const val KEYCODE_BACK = 4
    private const val KEYCODE_POWER = 26
    private const val KEYCODE_APP_SWITCH = 187

    /** Teclas que no se pueden asignar: sacarían al usuario de la pantalla o la apagarían. */
    private val RESERVED = setOf(KEYCODE_HOME, KEYCODE_BACK, KEYCODE_POWER, KEYCODE_APP_SWITCH)

    /** ¿Se puede asignar esta tecla? Los códigos de teclado de Android van de 1 a 300 y pico. */
    fun isAssignable(keyCode: Int): Boolean = keyCode > 0 && keyCode !in RESERVED

    /** Qué hacer con [keyCode], o null si no está asignado (entonces la tecla sigue su camino normal). */
    fun actionFor(keys: Map<Int, WheelAction>, keyCode: Int): WheelAction? = keys[keyCode]

    /** Asigna una acción a una tecla; reemplaza la anterior. Una tecla no asignable o pasar de [MAX] botones no cambia nada. */
    fun assign(keys: Map<Int, WheelAction>, keyCode: Int, action: WheelAction): Map<Int, WheelAction> {
        if (!isAssignable(keyCode)) return keys
        if (keyCode !in keys && keys.size >= MAX) return keys
        return keys + (keyCode to action)
    }

    /** "87:next,88:previous" (lo que se guarda en la radio). */
    fun serialize(keys: Map<Int, WheelAction>): String = keys.entries.joinToString(",") { "${it.key}:${it.value.id}" }

    /** Lo contrario de [serialize]; lo dañado o desconocido se ignora, nunca falla. */
    fun parse(raw: String?): Map<Int, WheelAction> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = linkedMapOf<Int, WheelAction>()
        raw.split(",").forEach { part ->
            val bits = part.split(":")
            if (bits.size != 2) return@forEach
            val key = bits[0].trim().toIntOrNull() ?: return@forEach
            val action = WheelAction.fromId(bits[1].trim()) ?: return@forEach
            if (isAssignable(key) && out.size < MAX) out[key] = action
        }
        return out
    }
}
