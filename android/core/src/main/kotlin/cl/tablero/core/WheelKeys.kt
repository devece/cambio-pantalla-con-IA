package cl.tablero.core

/**
 * Qué puede hacer un botón del volante. [id] es como se guarda en la radio: no cambiarlo.
 * Un botón que no está asignado hace lo que haga Android con esa tecla (la música va a la app que suena, el volumen al sistema).
 */
enum class WheelAction(val id: String, val label: String) {
    // Música
    SONG_NEXT("song_next", "Canción siguiente"),
    SONG_PREV("song_prev", "Canción anterior"),
    PLAYBACK("playback", "Reproducir o pausar"),

    // Volumen
    LOUDER("louder", "Subir el volumen"),
    QUIETER("quieter", "Bajar el volumen"),
    SILENCE("silence", "Silenciar o devolver el sonido"),

    // Apps y llamadas: los atajos son las apps que tú pusiste en la barra de abajo
    SHORTCUT_1("shortcut_1", "Abrir el atajo 1 de tu barra"),
    SHORTCUT_2("shortcut_2", "Abrir el atajo 2 de tu barra"),
    PHONE("phone", "Abrir el teléfono"),
    ASSISTANT("assistant", "Asistente de voz"),

    // Pantallas de Tablero
    GO_HOME("go_home", "Ir al Inicio"),
    GO_RADIO("go_radio", "Ir a la Radio"),
    GO_MAINTENANCE("go_maintenance", "Ir a Mantención"),
    SWAP_THEME("swap_theme", "Cambiar entre día y noche");

    /** Para los atajos: cuál app de la barra abre (0 es la primera), o null si no es un atajo. */
    val shortcutIndex: Int?
        get() = when (this) {
            SHORTCUT_1 -> 0
            SHORTCUT_2 -> 1
            else -> null
        }

    /** Para las acciones "Ir a…": id de la pantalla de Tablero (los mismos de la barra de abajo), o null. */
    val screen: String?
        get() = when (this) {
            GO_HOME -> "inicio"
            GO_RADIO -> "radio"
            GO_MAINTENANCE -> "mantenimiento"
            else -> null
        }

    /** Si mantener apretado el botón la repite (el volumen sube o baja mientras se aprieta). */
    val repeatsWhileHeld: Boolean get() = this == LOUDER || this == QUIETER

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
