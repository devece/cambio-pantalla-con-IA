package cl.tablero.launcher

import android.app.Activity
import android.os.Bundle

/**
 * Pantalla de inicio «falsa»: vacía y desactivada. Se activa un instante para que Android olvide qué inicio estaba fijado y muestre
 * la lista «¿Qué app usar?», donde el usuario elige Tablero → Siempre (Android 9 o más antiguo). Ver [HomeRole].
 */
class InicioTemporal : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
