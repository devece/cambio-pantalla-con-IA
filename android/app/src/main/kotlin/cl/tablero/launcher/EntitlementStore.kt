package cl.tablero.launcher

import android.content.Context
import cl.tablero.core.Editions
import cl.tablero.core.Limits
import cl.tablero.core.Tier

/**
 * Lee de la radio qué nivel quedó activado antes de que se retiraran los códigos (por defecto, el gratuito, que incluye todo).
 * Ya no se puede activar un código nuevo: solo se conserva lo que una radio ya tenía guardado.
 */
class EntitlementStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tablero", Context.MODE_PRIVATE)
    private val editions: Editions =
        Editions.parse(context.applicationContext.assets.open("editions.json").bufferedReader().use { it.readText() })

    val tier: Tier
        get() = Tier.fromId(prefs.getString("tier", null)) ?: Tier.FREE

    val limits: Limits
        get() = editions.effective(tier)
}
