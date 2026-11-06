package cl.tablero.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import cl.tablero.core.ObdLinkConfig
import cl.tablero.core.ObdListener
import cl.tablero.core.ObdMessages
import cl.tablero.core.ObdPoller
import cl.tablero.core.ObdSnapshot
import cl.tablero.core.ObdState
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mantiene la conexión con el scanner OBD elegido y avisa al hilo principal del estado y de los datos.
 *
 * Vive mientras la pantalla de inicio está a la vista (la crea `MainActivity.onResume` y la detiene `onPause`):
 * así el adaptador descansa cuando se usa otra app, y no hace falta un servicio en primer plano ni otro aviso fijo.
 * El costo es que al volver al inicio se reconecta (unos segundos). Un servicio propio queda para cuando haga falta
 * (alertas con el motor caliente, registro de viajes).
 */
class ObdClient(
    private val context: Context,
    private val onState: (ObdState, String) -> Unit,
    private val onSnapshot: (ObdSnapshot) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var poller: ObdPoller? = null
    private var thread: Thread? = null
    private var running: AtomicBoolean? = null

    /** Empieza (o reemplaza) la conexión. Si no hay adaptador configurado, no hace nada. */
    @Synchronized
    fun start(config: ObdLinkConfig) {
        stop(notify = false)
        val connector = ObdConnectors.create(context, config) ?: return
        val alive = AtomicBoolean(true)
        var lastSnapshot = 0L
        val p = ObdPoller(
            connector,
            object : ObdListener {
                override fun onState(state: ObdState, detail: String) {
                    main.post { if (alive.get()) this@ObdClient.onState(state, detail) }
                }

                override fun onSnapshot(snapshot: ObdSnapshot) {
                    // Las lecturas llegan varias veces por segundo; a la pantalla le basta con unas pocas.
                    val now = System.currentTimeMillis()
                    if (now - lastSnapshot < MIN_SNAPSHOT_GAP_MS) return
                    lastSnapshot = now
                    main.post { if (alive.get()) this@ObdClient.onSnapshot(snapshot) }
                }
            },
        )
        running = alive
        poller = p
        thread = Thread({
            while (alive.get()) {
                val wait = try {
                    p.step()
                } catch (e: Exception) {
                    // Algo que el sondeo no previó (por ejemplo, un permiso retirado): se avisa y se reintenta con calma.
                    val detail = ObdMessages.explain(e)
                    main.post { if (alive.get()) onState(ObdState.DISCONNECTED, detail) }
                    RETRY_AFTER_UNEXPECTED_MS
                }
                if (!alive.get()) break
                try {
                    Thread.sleep(wait)
                } catch (e: InterruptedException) {
                    break
                }
            }
            p.close()
        }, "obd-sondeo").apply {
            isDaemon = true
            start()
        }
    }

    /** Corta la conexión y deja de reintentar. */
    @Synchronized
    fun stop() = stop(notify = true)

    @Synchronized
    private fun stop(notify: Boolean) {
        running?.set(false)
        poller?.close() // cerrar el canal destraba al hilo si está esperando al adaptador
        thread?.interrupt()
        poller = null
        thread = null
        running = null
        if (notify) main.post { onState(ObdState.IDLE, "Desconectado") }
    }

    companion object {
        private const val MIN_SNAPSHOT_GAP_MS = 250L
        private const val RETRY_AFTER_UNEXPECTED_MS = 5_000L

        /** Lo que se le manda a la pantalla web (`TBApp.setObd`): el estado y los últimos valores, por clave (`rpm`, `speed`, `coolant`...). */
        fun toJson(state: ObdState, detail: String, snapshot: ObdSnapshot?): String {
            val values = JSONObject()
            snapshot?.values?.forEach { (key, value) -> values.put(key, value) }
            return JSONObject()
                .put("state", state.name.lowercase())
                .put("detail", detail)
                .put("values", values)
                .toString()
        }
    }
}
