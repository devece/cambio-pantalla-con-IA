package cl.tablero.core

import java.io.Closeable
import java.io.IOException

/**
 * Cómo se conecta la radio al adaptador OBD. Todos los adaptadores ELM327 hablan igual una vez conectados
 * (ver [ObdSession]); lo único que cambia es el cable invisible por el que llegan los bytes.
 *
 * Para sumar otro tipo (por ejemplo Bluetooth LE) basta con agregar un valor aquí y un [ObdConnector] en la app.
 */
enum class ObdLinkKind(val id: String, val label: String) {
    /** Sin adaptador: la app funciona como siempre. */
    OFF("off", "Sin adaptador"),

    /** Adaptador Bluetooth clásico (el más común y barato, por ejemplo «Scanner OBD2 ELM327 Bluetooth V2.1»). Se empareja antes con la radio. */
    BLUETOOTH("bluetooth", "Bluetooth"),

    /** Adaptador Wi-Fi: la radio se conecta a la red del adaptador y se habla por TCP. */
    WIFI("wifi", "Wi-Fi");

    companion object {
        fun of(id: String?): ObdLinkKind = entries.firstOrNull { it.id == id } ?: OFF
    }
}

/** El adaptador que el usuario eligió. Se guarda en la radio. */
data class ObdLinkConfig(
    val kind: ObdLinkKind = ObdLinkKind.OFF,
    /** Bluetooth: dirección del dispositivo (`AA:BB:CC:DD:EE:FF`). Wi-Fi: nombre o IP del adaptador. */
    val address: String = "",
    /** Bluetooth: el nombre con que lo emparejó el usuario, solo para mostrarlo. */
    val name: String = "",
    /** Wi-Fi: puerto TCP del adaptador. */
    val port: Int = DEFAULT_WIFI_PORT,
) {
    /** True si hay un adaptador elegido y sus datos tienen forma válida. */
    val isConfigured: Boolean
        get() = when (kind) {
            ObdLinkKind.OFF -> false
            ObdLinkKind.BLUETOOTH -> isValidMac(address)
            ObdLinkKind.WIFI -> isValidHost(address) && port in 1..65535
        }

    /** Cómo se llama en pantalla ("OBDII", "192.168.0.10:35000"...). */
    val display: String
        get() = when (kind) {
            ObdLinkKind.OFF -> "Sin adaptador"
            ObdLinkKind.BLUETOOTH -> name.ifBlank { address }
            ObdLinkKind.WIFI -> "$address:$port"
        }

    companion object {
        /** Los adaptadores Wi-Fi ELM327 de fábrica casi siempre usan esta dirección y puerto. */
        const val DEFAULT_WIFI_HOST = "192.168.0.10"
        const val DEFAULT_WIFI_PORT = 35000

        private val mac = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
        private val host = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$")

        fun isValidMac(s: String): Boolean = mac.matches(s.trim())

        /** Una IP o un nombre de equipo (sin espacios ni caracteres raros). */
        fun isValidHost(s: String): Boolean = host.matches(s.trim())

        /** Arma una configuración desde lo guardado, descartando lo que no tenga sentido. Nunca lanza. */
        fun fromStored(kind: String?, address: String?, name: String?, port: Int): ObdLinkConfig {
            val k = ObdLinkKind.of(kind)
            val a = (address ?: "").trim()
            val config = ObdLinkConfig(
                kind = k,
                address = if (k == ObdLinkKind.BLUETOOTH) a.uppercase() else a,
                name = (name ?: "").trim().take(40),
                port = if (port in 1..65535) port else DEFAULT_WIFI_PORT,
            )
            return if (k != ObdLinkKind.OFF && !config.isConfigured) config.copy(kind = ObdLinkKind.OFF) else config
        }
    }
}

/** Un canal ya abierto con el adaptador: se le mandan comandos y hay que cerrarlo al terminar. */
interface ObdChannel : ObdTransport, Closeable

/** Sabe abrir el canal con un adaptador en particular (Bluetooth, Wi-Fi...). La app tiene uno por cada [ObdLinkKind]. */
fun interface ObdConnector {
    /** Abre la conexión. Bloquea hasta lograrlo. @throws IOException si no se pudo. */
    @Throws(IOException::class)
    fun open(): ObdChannel
}
