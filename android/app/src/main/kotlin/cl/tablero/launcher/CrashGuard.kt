package cl.tablero.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import cl.tablero.core.CrashLog
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Registro de fallos: anota los cierres inesperados y los bloqueos de la pantalla (el hilo principal sin responder) en un archivo
 * dentro de la app, para poder saber qué le pasó a una radio que no tenemos. Se ve y se comparte desde Ajustes → Diagnóstico.
 * No manda nada a ningún lado.
 */
object CrashGuard {
    private const val FILE = "registro-de-fallos.log"
    private const val FREEZE_MS = 10_000L
    private const val CHECK_MS = 3_000L
    private const val BEAT_MS = 1_000L

    private var app: Context? = null
    private val lock = Any()

    /** Último latido del hilo principal (reloj que no cuenta el sueño del equipo). */
    @Volatile private var beat = 0L
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
     * Detecta una pantalla pegada con el diseño inverso: el propio hilo principal marca su «latido» cada segundo y un vigía aparte,
     * cada 3 s, mira cuánto hace del último. Si pasan más de 10 s, anota en qué estaba trabado (una sola vez por bloqueo).
     * El vigía es un hilo liviano y de baja prioridad; no molesta aunque la radio esté justa de memoria.
     */
    private fun startWatchdog() {
        if (started) return
        started = true
        val main = Handler(Looper.getMainLooper())
        beat = SystemClock.uptimeMillis()
        main.post(object : Runnable {
            override fun run() {
                beat = SystemClock.uptimeMillis()
                main.postDelayed(this, BEAT_MS)
            }
        })
        var reported = false
        val pool = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "tablero-vigia").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }
        pool.scheduleWithFixedDelay({
            val silent = SystemClock.uptimeMillis() - beat
            if (silent > FREEZE_MS) {
                if (!reported) {
                    reported = true
                    runCatching {
                        record("Pantalla bloqueada (${silent / 1000} s sin responder)", "main", CrashLog.stackOf(Looper.getMainLooper().thread.stackTrace))
                    }
                }
            } else {
                reported = false
            }
        }, CHECK_MS, CHECK_MS, TimeUnit.MILLISECONDS)
    }
}
