package cl.tablero.launcher

import android.app.Application

/** Lo primero que corre al abrirse la app: deja encendido el registro de fallos. */
class TableroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
    }
}
