package cl.tablero.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import cl.tablero.core.ElmStreamTransport
import cl.tablero.core.ObdChannel
import cl.tablero.core.ObdConnector
import cl.tablero.core.ObdLinkConfig
import cl.tablero.core.ObdLinkKind
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** Un dispositivo Bluetooth ya emparejado con la radio. */
data class PairedDevice(val name: String, val address: String) {
    /** Por el nombre parece un scanner OBD (los genéricos suelen llamarse «OBDII», «ELM327», «V-LINK»...). */
    val looksLikeObd: Boolean get() = LIKELY.containsMatchIn(name)

    private companion object {
        val LIKELY = Regex("obd|elm|v-?link|vgate|icar|konnwei|scan|diag", RegexOption.IGNORE_CASE)
    }
}

/** Todo lo de Bluetooth para el scanner. Solo se usa el permiso de conectarse a un dispositivo ya emparejado. */
object ObdBluetooth {
    /** El canal serie estándar (SPP) que usan los ELM327 Bluetooth. */
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** Desde Android 12 hay que pedir el permiso de Bluetooth en pantalla; antes basta con declararlo. */
    fun hasPermission(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun adapter(c: Context): BluetoothAdapter? =
        (c.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Los dispositivos emparejados, los que parecen scanners primero. Null si falta el permiso o la radio no tiene Bluetooth. */
    @SuppressLint("MissingPermission")
    fun paired(c: Context): List<PairedDevice>? {
        if (!hasPermission(c)) return null
        val adapter = adapter(c) ?: return null
        return try {
            adapter.bondedDevices.orEmpty()
                .map { PairedDevice(it.name?.takeIf { n -> n.isNotBlank() } ?: it.address, it.address) }
                .sortedWith(compareByDescending<PairedDevice> { it.looksLikeObd }.thenBy { it.name.lowercase() })
        } catch (e: SecurityException) {
            null
        }
    }
}

/**
 * Conecta con un scanner OBD Bluetooth clásico ya emparejado, como el «Scanner Automotriz OBD2 ELM327 Bluetooth V2.1».
 * Los clones baratos son caprichosos con el modo de conexión, así que se prueban tres maneras, de la más correcta a la más insistente.
 */
class BluetoothObdConnector(private val context: Context, private val address: String) : ObdConnector {
    @SuppressLint("MissingPermission")
    override fun open(): ObdChannel {
        if (!ObdBluetooth.hasPermission(context)) {
            throw IOException("Falta el permiso de Bluetooth. Dalo desde Ajustes, en Scanner OBD.")
        }
        val adapter = ObdBluetooth.adapter(context) ?: throw IOException("Esta radio no tiene Bluetooth.")
        try {
            if (!adapter.isEnabled) throw IOException("El Bluetooth de la radio está apagado. Enciéndelo e inténtalo de nuevo.")
            val device = try {
                adapter.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                throw IOException("La dirección del adaptador no es válida. Elígelo de nuevo en Ajustes.")
            }
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                throw IOException(
                    "El adaptador no está emparejado con la radio. Emparéjalo primero en los ajustes de Bluetooth de la radio (la clave suele ser 1234 o 0000).",
                )
            }
            try {
                adapter.cancelDiscovery() // una búsqueda en curso hace lenta o falla la conexión
            } catch (e: SecurityException) {
                // sin permiso de búsqueda: se sigue igual
            }
            val socket = connect(device)
            return try {
                ElmStreamTransport(socket.inputStream, socket.outputStream, closeAction = { closeQuietly(socket) })
            } catch (e: IOException) {
                closeQuietly(socket)
                throw e
            }
        } catch (e: SecurityException) {
            throw IOException("Falta el permiso de Bluetooth. Dalo desde Ajustes, en Scanner OBD.", e)
        }
    }

    @SuppressLint("MissingPermission", "DiscouragedPrivateApi")
    private fun connect(device: BluetoothDevice): BluetoothSocket {
        val attempts: List<() -> BluetoothSocket> = listOf(
            { device.createRfcommSocketToServiceRecord(ObdBluetooth.SPP_UUID) },
            { device.createInsecureRfcommSocketToServiceRecord(ObdBluetooth.SPP_UUID) },
            // Último recurso para clones que no anuncian el servicio: canal 1 directo (método oculto; puede no existir).
            { device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket },
        )
        var last: Exception? = null
        for (make in attempts) {
            var socket: BluetoothSocket? = null
            try {
                socket = make()
                socket.connect()
                return socket
            } catch (e: SecurityException) {
                socket?.let { closeQuietly(it) }
                throw e
            } catch (e: Exception) {
                last = e
                socket?.let { closeQuietly(it) }
            }
        }
        throw IOException(
            "No se pudo conectar al adaptador. Revisa que esté enchufado al auto y que ningún otro aparato (como tu teléfono) esté conectado a él.",
            last,
        )
    }

    private fun closeQuietly(socket: BluetoothSocket) {
        try {
            socket.close()
        } catch (e: IOException) {
            // ya estaba cerrado
        }
    }
}

/**
 * Conecta con un scanner OBD Wi-Fi: la radio debe estar conectada a la red Wi-Fi que crea el adaptador
 * (los de fábrica suelen usar 192.168.0.10, puerto 35000). Mientras tanto la radio no tiene internet por esa red.
 */
class WifiObdConnector(private val host: String, private val port: Int) : ObdConnector {
    override fun open(): ObdChannel {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.tcpNoDelay = true
            return ElmStreamTransport(socket.getInputStream(), socket.getOutputStream(), closeAction = { socket.close() })
        } catch (e: IOException) {
            try {
                socket.close()
            } catch (ignored: IOException) {
            }
            throw IOException("No se pudo conectar a $host:$port. ¿La radio está conectada a la red Wi-Fi del adaptador?", e)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 6_000
    }
}

object ObdConnectors {
    /** El conector que corresponde al adaptador elegido, o null si no hay ninguno configurado. */
    fun create(context: Context, config: ObdLinkConfig): ObdConnector? {
        if (!config.isConfigured) return null
        return when (config.kind) {
            ObdLinkKind.BLUETOOTH -> BluetoothObdConnector(context.applicationContext, config.address)
            ObdLinkKind.WIFI -> WifiObdConnector(config.address, config.port)
            ObdLinkKind.OFF -> null
        }
    }
}
