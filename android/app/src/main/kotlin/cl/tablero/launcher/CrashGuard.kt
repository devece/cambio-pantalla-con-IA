package cl.tablero.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import cl.tablero.core.CrashLog
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Anota en un archivo privado las excepciones no capturadas (de cualquier hilo) y los bloqueos del hilo principal.
 * Nunca lanza excepciones hacia afuera.
 */
object CrashGuard {
    private const val ARCHIVO = "registro-fallos.txt"
    /** Tiempo sin respuesta del hilo principal que se considera bloqueo. */
    private const val UMBRAL_MS = 8_000L
    /** Cada cuánto despierta el vigilante: casi sin costo. */
    private const val PERIODO_MS = 2_000L

    private val instalado = AtomicBoolean(false)
    private val cerrojo = Any()
    private val enGuardado = ThreadLocal<Boolean>()

    @Volatile private var appContext: Context? = null
    @Volatile private var version: String = "?"

    // Estado del vigilante (lo escribe el vigilante y el hilo principal).
    @Volatile private var pingPendiente = false
    @Volatile private var pingEnviadoMs = 0L
    @Volatile private var bloqueoAnotado = false

    fun install(context: Context) {
        try {
            if (!instalado.compareAndSet(false, true)) return
            val app = context.applicationContext ?: context
            appContext = app
            version = calcularVersion(app)

            val anterior = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
                try {
                    anotar("Excepción no capturada", hilo?.name ?: "?", CrashLog.stackOf(error))
                } catch (_: Throwable) {
                }
                if (anterior != null) {
                    anterior.uncaughtException(hilo, error)
                } else {
                    // Sin manejador previo: comportamiento por omisión de la JVM de Android (terminar el proceso).
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(10)
                }
            }
            iniciarVigilante()
        } catch (_: Throwable) {
        }
    }

    fun read(context: Context): String = try {
        synchronized(cerrojo) {
            val f = archivo(context)
            if (f.isFile) f.readText(Charsets.UTF_8) else ""
        }
    } catch (_: Throwable) {
        ""
    }

    fun clear(context: Context) {
        try {
            synchronized(cerrojo) { archivo(context).delete() }
        } catch (_: Throwable) {
        }
    }

    private fun archivo(context: Context) = File(context.applicationContext?.filesDir ?: context.filesDir, ARCHIVO)

    private fun calcularVersion(context: Context): String {
        val nombre = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Throwable) {
            null
        }
        return "${nombre ?: "?"} (${BuildConfig.GIT_COMMIT})"
    }

    private fun anotar(tipo: String, hilo: String, pila: String) {
        val ctx = appContext ?: return
        if (enGuardado.get() == true) return // evita recursión si fallara el propio guardado
        enGuardado.set(true)
        try {
            synchronized(cerrojo) {
                val f = archivo(ctx)
                val previo = if (f.isFile) f.readText(Charsets.UTF_8) else ""
                val nuevo = CrashLog.append(previo, CrashLog.entry(tipo, System.currentTimeMillis(), version, hilo, pila))
                f.writeText(nuevo, Charsets.UTF_8)
            }
        } catch (_: Throwable) {
        } finally {
            enGuardado.set(false)
        }
    }

    private fun iniciarVigilante() {
        val principal = Looper.getMainLooper() ?: return
        val handler = Handler(principal)
        val pong = Runnable {
            pingPendiente = false
            bloqueoAnotado = false
        }
        val hilo = Thread({
            try {
                while (true) {
                    Thread.sleep(PERIODO_MS)
                    val ahora = SystemClock.uptimeMillis()
                    if (!pingPendiente) {
                        pingEnviadoMs = ahora
                        pingPendiente = true
                        handler.post(pong)
                    } else if (!bloqueoAnotado && ahora - pingEnviadoMs >= UMBRAL_MS) {
                        bloqueoAnotado = true
                        val pila = principal.thread.stackTrace
                        anotar("Bloqueo del hilo principal", principal.thread.name, CrashLog.stackOf(pila))
                    }
                }
            } catch (_: Throwable) {
                // Interrumpido o fallo inesperado: el vigilante termina sin afectar a la app.
            }
        }, "tablero-vigilante")
        hilo.isDaemon = true
        hilo.priority = Thread.MIN_PRIORITY
        hilo.start()
    }
}
