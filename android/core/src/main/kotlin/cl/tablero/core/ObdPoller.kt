package cl.tablero.core

import java.io.IOException

/** En qué punto está la conexión con el adaptador y el auto. */
enum class ObdState {
    /** Sin adaptador elegido o detenido. */
    IDLE,

    /** Abriendo la conexión con el adaptador. */
    CONNECTING,

    /** El adaptador responde pero el auto no (normalmente el auto está apagado). Se reintenta solo. */
    WAITING_FOR_CAR,

    /** Todo funciona: llegan datos. */
    CONNECTED,

    /** Se perdió o no se pudo abrir la conexión. Se reintenta solo, esperando más cada vez. */
    DISCONNECTED,
}

/** Los últimos valores leídos, por la clave de cada dato ([PidSpec.key]): `rpm`, `speed`, `coolant`, `voltage`... */
data class ObdSnapshot(
    val values: Map<String, Double>,
    /** Qué datos entrega este auto (PIDs). */
    val supported: Set<Int>,
    val adapter: String?,
    val atMs: Long,
)

/** Recibe lo que pasa con la conexión. Se llama desde el hilo que corre el sondeo: la app debe pasarlo al hilo principal. */
interface ObdListener {
    fun onState(state: ObdState, detail: String)
    fun onSnapshot(snapshot: ObdSnapshot)
}

/**
 * Mantiene la conexión con el adaptador y lee los datos del auto de forma continua, reconectando solo.
 *
 * Funciona por pasos: cada [step] hace un poco de trabajo y devuelve cuántos milisegundos esperar antes del siguiente.
 * Así la app solo tiene que llamarlo en un bucle desde un hilo, y las pruebas lo llevan paso a paso sin esperar.
 *
 * Qué hace según lo que encuentra:
 * - sin conexión con el adaptador: reintenta esperando 2, 4, 8... hasta 30 segundos, pero **se rinde**: tras 3 intentos
 *   si nunca llegó a conectar, o tras 5 reintentos si se cayó estando conectado. Insistir sin parar cansa al adaptador
 *   y, en una radio que reproduce música por Bluetooth, puede entrecortarla. Una vez rendido ya no hace nada hasta que
 *   la app lo vuelva a crear (por ejemplo al volver a la pantalla de inicio);
 * - el adaptador responde pero el auto no (auto apagado): lo avisa y reintenta cada 5 segundos sin cerrar la conexión;
 * - con el motor apagado (0 rpm) consulta cada 3 segundos para no cansar el adaptador;
 * - ya conectado, lee velocidad y revoluciones en cada paso y los demás datos de a uno.
 */
class ObdPoller(
    private val connector: ObdConnector,
    private val listener: ObdListener,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var channel: ObdChannel? = null
    private var session: ObdSession? = null
    private var supported: Set<Int>? = null
    private var fast: List<PidSpec> = emptyList()
    private var slow: List<PidSpec> = emptyList()
    private var slowIndex = 0
    private var useAdapterVoltage = false
    private var voltageTick = 0
    private val latest = LinkedHashMap<String, Double>()
    private var state = ObdState.IDLE
    private var reconnectDelay = RECONNECT_MIN_MS
    private var carSilence = 0
    private var badReplies = 0
    private var everConnected = false
    private var failedAttempts = 0
    private var dropsInRow = 0
    @Volatile
    private var gaveUp = false

    @Volatile
    private var closed = false

    /** True cuando ya no hará nada más (se cerró o se rindió): quien lo maneja puede dejar de llamar a [step]. */
    val finished: Boolean get() = closed || gaveUp

    /** Hace un poco de trabajo y devuelve cuántos milisegundos esperar antes de volver a llamarlo. */
    fun step(): Long {
        if (closed || gaveUp) return STOPPED_MS
        return try {
            val s = session
            when {
                s == null -> connect()
                supported == null -> negotiate(s)
                else -> poll(s)
            }
        } catch (e: IOException) {
            if (closed) STOPPED_MS else lose(ObdMessages.explain(e))
        } catch (e: ObdException) {
            if (closed) STOPPED_MS else lose(ObdMessages.explain(e))
        }
    }

    /**
     * Cierra la conexión y deja de reintentar. Se puede llamar desde cualquier hilo, por ejemplo para destrabar un [step]
     * que está esperando al adaptador. Solo cierra el canal: el hilo del sondeo es el único que toca el resto del estado.
     */
    fun close() {
        closed = true
        val ch = channel
        try {
            ch?.close()
        } catch (e: Exception) {
            // ya estaba cerrado
        }
    }

    /* ---------- pasos ---------- */

    private fun connect(): Long {
        // En los reintentos se sigue mostrando «desconectado»: no se alterna el aviso con «conectando» cada vez.
        if (state == ObdState.IDLE) publish(ObdState.CONNECTING, "Conectando con el adaptador...")
        val ch = connector.open()
        channel = ch
        if (closed) { // se pidió cerrar mientras se abría
            dropChannel()
            return STOPPED_MS
        }
        val s = ObdSession(ch)
        s.init()
        session = s
        everConnected = true
        reconnectDelay = RECONNECT_MIN_MS
        return 0
    }

    private fun negotiate(s: ObdSession): Long {
        val pids = try {
            s.supportedPids()
        } catch (e: ObdException) {
            if (e.error == ObdError.UNABLE_TO_CONNECT || e.error == ObdError.BUS_ERROR || e.error == ObdError.NO_DATA) {
                return waitForCar()
            }
            throw e
        }
        if (pids.isEmpty()) return waitForCar()
        supported = pids
        val known = ObdPids.all.filter { it.pid in pids }
        fast = known.filter { it.key == "rpm" || it.key == "speed" }
        slow = known - fast.toSet()
        // Sin el dato de voltaje del auto, se usa el que mide el propio adaptador.
        useAdapterVoltage = known.none { it.key == "voltage" }
        carSilence = 0
        badReplies = 0
        publish(ObdState.CONNECTED, "Conectado: ${s.adapter ?: "adaptador"} · ${known.size} datos disponibles")
        return 0
    }

    private fun poll(s: ObdSession): Long {
        try {
            for (spec in fast) s.read(spec.pid)?.let { latest[spec.key] = it.value }
            if (slow.isNotEmpty()) {
                val spec = slow[slowIndex++ % slow.size]
                s.read(spec.pid)?.let { latest[spec.key] = it.value }
            }
            if (useAdapterVoltage && voltageTick++ % ADAPTER_VOLTAGE_EVERY == 0) {
                s.adapterVoltage()?.let { latest["voltage"] = it }
            }
        } catch (e: ObdException) {
            return when (e.error) {
                ObdError.UNABLE_TO_CONNECT, ObdError.BUS_ERROR, ObdError.ADAPTER_BUSY -> {
                    if (++carSilence >= CAR_SILENCE_LIMIT) {
                        supported = null // cuando el auto vuelva se vuelve a averiguar qué datos entrega
                        waitForCar()
                    } else {
                        1_000
                    }
                }
                else -> if (++badReplies >= BAD_REPLY_LIMIT) throw e else 500
            }
        }
        carSilence = 0
        badReplies = 0
        dropsInRow = 0 // leyendo bien: la conexión es estable
        if (state != ObdState.CONNECTED) publish(ObdState.CONNECTED, "Conectado")
        listener.onSnapshot(ObdSnapshot(LinkedHashMap(latest), supported ?: emptySet(), s.adapter, clock()))
        return if (latest["rpm"] == 0.0) ENGINE_OFF_MS else FAST_MS
    }

    private fun waitForCar(): Long {
        publish(ObdState.WAITING_FOR_CAR, ObdMessages.explain(ObdError.UNABLE_TO_CONNECT))
        return CAR_RETRY_MS
    }

    /** Se perdió la conexión: se cierra todo y se vuelve a empezar desde cero, esperando más cada vez. */
    private fun lose(detail: String): Long {
        dropChannel()
        val giveUp = if (everConnected) ++dropsInRow > MAX_RECONNECTS else ++failedAttempts >= MAX_FIRST_ATTEMPTS
        if (giveUp) {
            gaveUp = true
            publish(ObdState.DISCONNECTED, "$detail Se dejó de reintentar para no cansar el adaptador. Vuelve a abrir la pantalla de inicio para probar de nuevo.")
            return STOPPED_MS
        }
        publish(ObdState.DISCONNECTED, detail)
        val wait = reconnectDelay
        reconnectDelay = minOf(reconnectDelay * 2, RECONNECT_MAX_MS)
        return wait
    }

    private fun dropChannel() {
        session = null
        supported = null
        carSilence = 0
        badReplies = 0
        latest.clear()
        val ch = channel
        channel = null
        try {
            ch?.close()
        } catch (e: Exception) {
            // ya estaba cerrado
        }
    }

    private fun publish(newState: ObdState, detail: String) {
        // Cada estado se avisa una vez; mientras el motivo no cambie no se repite (los reintentos no deben llenar de avisos).
        if (newState == state && detail == lastDetail) return
        state = newState
        lastDetail = detail
        listener.onState(newState, detail)
    }

    private var lastDetail = ""

    companion object {
        const val RECONNECT_MIN_MS = 2_000L
        const val RECONNECT_MAX_MS = 30_000L
        const val CAR_RETRY_MS = 5_000L
        const val ENGINE_OFF_MS = 3_000L
        const val FAST_MS = 100L
        const val STOPPED_MS = 1_000L
        private const val CAR_SILENCE_LIMIT = 3
        private const val MAX_FIRST_ATTEMPTS = 3
        private const val MAX_RECONNECTS = 5
        private const val BAD_REPLY_LIMIT = 5
        private const val ADAPTER_VOLTAGE_EVERY = 10
    }
}
