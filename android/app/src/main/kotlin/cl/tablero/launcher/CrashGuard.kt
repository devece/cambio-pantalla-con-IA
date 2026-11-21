package cl.tablero.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import cl.tablero.core.CrashLog
import java.io.File

/**
 * Registro de fallos: anota los cierres inesperados y los bloqueos de la pantalla (el hilo principal sin responder) en un archivo
 * dentro de la app, para poder saber qué le pasó a una radio que no tenemos. Se ve y se comparte desde Ajustes → Diagnóstico.
 * No manda nada a ningún lado.
 */
object CrashGuard {
    private const val FILE = "fallos.txt"
    private const val FREEZE_MS = 8_000L
    private const val TICK_MS = 2_000L

    private var app: Context? = null
    private val lock = Any()

    @Volatile private var pong = 0L
    @Volatile private var started = false

    fun install(context: Context) {
        if (app != null) return
        app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Sin tocar nada más que el archivo: la app ya está cayendo.
            runCatching { record("Cierre inesperado", thread.name, CrashLog.stackOf(error)) }
            previous?.uncaughtException(thread, error)
        }
        startWatchdog()
    }

    /** El registro completo, o "" si no hay. */
    fun read(context: Context): String = synchronized(lock) {
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull() ?: ""
    }

    fun clear(context: Context) {
        synchronized(lock) { runCatching { file(context).delete() } }
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    private fun record(kind: String, thread: String, stack: String) {
        val context = app ?: return
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        val entry = CrashLog.entry(kind, System.currentTimeMillis(), "$version (${BuildConfig.GIT_COMMIT})", thread, stack)
        synchronized(lock) {
            val f = file(context)
            val old = runCatching { if (f.exists()) f.readText() else "" }.getOrDefault("")
            f.writeText(CrashLog.append(old, entry))
        }
    }

    /**
     * Cada 2 s le pide al hilo principal que conteste. Si pasan 8 s sin respuesta, anota en qué estaba (una sola vez por bloqueo).
     * Es un hilo aparte y liviano; si la radio se queda sin memoria o se apaga la pantalla, no molesta.
     */
    private fun startWatchdog() {
        if (started) return
        started = true
        val main = Handler(Looper.getMainLooper())
        pong = System.currentTimeMillis()
        val reply = Runnable { pong = System.currentTimeMillis() }
        val thread = Thread({
            var reported = false
            while (true) {
                try {
                    Thread.sleep(TICK_MS)
                } catch (e: InterruptedException) {
                    return@Thread
                }
                main.post(reply)
                val silent = System.currentTimeMillis() - pong
                if (silent >= FREEZE_MS) {
                    if (!reported) {
                        reported = true
                        runCatching {
                            record("Pantalla bloqueada (${silent / 1000} s sin responder)", "main", CrashLog.stackOf(Looper.getMainLooper().thread.stackTrace))
                        }
                    }
                } else {
                    reported = false
                }
            }
        }, "tablero-watchdog")
        thread.isDaemon = true
        thread.start()
    }
}
