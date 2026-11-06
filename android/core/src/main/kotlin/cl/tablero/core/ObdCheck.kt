package cl.tablero.core

import java.io.IOException
import java.util.Locale

/** Resultado de probar un adaptador (botón «Probar conexión» de Ajustes). */
data class ObdCheckResult(
    /** El adaptador se conectó y se presentó bien. */
    val adapterOk: Boolean,
    /** El auto contestó (no solo el adaptador). */
    val carOk: Boolean,
    /** Texto para mostrarle al usuario, una línea por cosa. */
    val report: String,
)

object ObdCheck {
    /**
     * Abre el adaptador, lo configura y trata de leer unos datos del auto. Siempre cierra la conexión al terminar.
     * Bloquea: hay que llamarlo fuera del hilo principal. Nunca lanza: todo problema sale explicado en el resultado.
     */
    fun run(connector: ObdConnector): ObdCheckResult {
        val lines = ArrayList<String>()
        var channel: ObdChannel? = null
        var adapterOk = false
        var carOk = false
        try {
            channel = connector.open()
            val session = ObdSession(channel)
            session.init()
            adapterOk = true
            lines += "Adaptador: ${session.adapter ?: "ELM327"}"
            session.adapterVoltage()?.let { lines += "Voltaje en el puerto OBD: ${num(it, 1)} V" }
            carOk = readCar(session, lines)
        } catch (e: ObdException) {
            lines += ObdMessages.explain(e)
        } catch (e: IOException) {
            lines += (if (adapterOk) "Se perdió la conexión con el adaptador. " else "No se pudo hablar con el adaptador. ") + ObdMessages.explain(e)
        } finally {
            try {
                channel?.close()
            } catch (e: Exception) {
                // ya estaba cerrado
            }
        }
        return ObdCheckResult(adapterOk, carOk, lines.joinToString("\n"))
    }

    /** Agrega al informe lo que se pueda leer del auto. Devuelve true si el auto contestó. */
    private fun readCar(session: ObdSession, out: MutableList<String>): Boolean {
        val pids = try {
            session.supportedPids()
        } catch (e: ObdException) {
            if (e.error == ObdError.UNABLE_TO_CONNECT || e.error == ObdError.BUS_ERROR || e.error == ObdError.NO_DATA) {
                out += "Auto: no responde. " + ObdMessages.explain(ObdError.UNABLE_TO_CONNECT)
                return false
            }
            throw e
        }
        val known = ObdPids.all.filter { it.pid in pids }
        out += "Auto: responde (${known.size} datos que esta app sabe leer)"
        for (key in listOf("rpm", "speed", "coolant")) {
            val spec = known.firstOrNull { it.key == key } ?: continue
            val reading = try {
                session.read(spec.pid)
            } catch (e: ObdException) {
                null // un dato que falla no tumba la prueba entera
            }
            if (reading != null) out += "${spec.name}: ${num(reading.value, 0)} ${spec.unit}"
        }
        session.monitorStatus()?.let { st ->
            out += if (st.milOn) "Luz del motor: encendida (${st.dtcCount} códigos de falla)" else "Luz del motor: apagada"
        }
        return true
    }

    private fun num(v: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", v)
}
