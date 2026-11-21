package cl.tablero.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Al terminar el arranque del equipo, trae Tablero al frente si el usuario lo pidió en los ajustes. */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val accion = intent?.action
            if (accion != Intent.ACTION_BOOT_COMPLETED && accion != "android.intent.action.QUICKBOOT_POWERON") return
            if (!SettingsStore(context).bootOpen()) return
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Throwable) {
            // El sistema puede negar abrir la pantalla en segundo plano: se ignora.
        }
    }
}
