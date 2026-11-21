package cl.tablero.launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * «Abrir Tablero al encender»: para radios que siempre arrancan en su menú de fábrica aunque Tablero sea el inicio. Solo actúa si el
 * usuario lo activó en Ajustes. En Android 10 o más nuevo el sistema puede impedir abrir una pantalla desde el fondo; ahí no hace nada.
 */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val encendido = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == ACCION_ENCENDIDO_RAPIDO
        if (!encendido || !SettingsStore(context).bootOpen()) return
        // MainActivity es singleTask: con NEW_TASK basta para traerla al frente si ya existe.
        val abrir = Intent.makeMainActivity(ComponentName(context, MainActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(abrir) } // si el sistema no lo permite, no hay nada más que hacer
    }

    private companion object {
        /** Aviso de «arranque rápido» que mandan algunas radios en vez del de Android. */
        const val ACCION_ENCENDIDO_RAPIDO = "android.intent.action.QUICKBOOT_POWERON"
    }
}
