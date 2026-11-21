package cl.tablero.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * «Abrir Tablero al encender»: para radios que siempre arrancan en su menú de fábrica aunque Tablero sea el inicio. Solo actúa si el
 * usuario lo activó en Ajustes. En Android 10 o más nuevo el sistema puede impedir abrir una pantalla desde el fondo; ahí no hace nada.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") return
        if (!SettingsStore(context).bootOpen()) return
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        } catch (e: Exception) {
            // El sistema no lo permitió: no hay nada más que hacer.
        }
    }
}
