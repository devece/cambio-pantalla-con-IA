package cl.tablero.launcher

import android.content.Context
import cl.tablero.core.Editions
import cl.tablero.core.Limits
import cl.tablero.core.Tier

/**
 * Guarda en la radio qué nivel quedó activado. Así, una vez activado, el launcher funciona sin internet
 * (un túnel o una zona sin señal no puede bloquear lo que el cliente ya pagó).
 * Pendiente: volver a verificar con el servidor de vez en cuando para detectar códigos revocados.
 */
class EntitlementStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tablero", Context.MODE_PRIVATE)
    private val editions: Editions =
        Editions.parse(context.applicationContext.assets.open("editions.json").bufferedReader().use { it.readText() })

    val tier: Tier
        get() = Tier.fromId(prefs.getString("tier", null)) ?: Tier.FREE

    val limits: Limits
        get() = editions.limits(tier)

    fun activate(tier: Tier, code: String) {
        prefs.edit().putString("tier", tier.id).putString("code", code).apply()
    }
}
