package cl.tablero.core

import org.json.JSONArray
import org.json.JSONObject

/** Qué nivel de uso tiene la radio: gratis, o uno de los planes que desbloquea un código. */
enum class Tier(val id: String) {
    FREE("free"),
    ESENCIAL("esencial"),
    COMPLETO("completo");

    companion object {
        fun fromId(id: String?): Tier? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Límites de un nivel. Los conjuntos en `null` significan "sin límite" (`"*"` en editions.json).
 * Los conjuntos conservan el orden del JSON: el primero se usa como valor de respaldo.
 */
data class Limits(
    val themes: Set<String>?,
    val layouts: Set<String>?,
    val scenes: Set<String>?,
    val seasons: Set<String>?,
    val maxApps: Int,
    val oilOn: Boolean,
    val speedShow: Boolean,
    val speedAlerts: Set<String>?,
    val othersMaintenance: Boolean,
    val docsOn: Boolean,
    val fuelOn: Boolean,
    val restrOn: Boolean,
    val voice: Boolean,
)

/** Lee `public/shared/editions.json` (copiado a los assets de la app al compilar). */
class Editions private constructor(private val byTier: Map<Tier, Limits>) {
    fun limits(tier: Tier): Limits = byTier.getValue(tier)

    companion object {
        fun parse(json: String): Editions {
            val root = JSONObject(json)
            return Editions(Tier.entries.associateWith { readLimits(root.getJSONObject(it.id)) })
        }

        private fun readLimits(o: JSONObject): Limits {
            val f = o.getJSONObject("features")
            return Limits(
                themes = readSet(o, "themes"),
                layouts = readSet(o, "layouts"),
                scenes = readSet(o, "scenes"),
                seasons = readSet(o, "seasons"),
                maxApps = o.getInt("maxApps"),
                oilOn = f.getBoolean("oilOn"),
                speedShow = f.getBoolean("speedShow"),
                speedAlerts = readSet(f, "speedAlerts"),
                othersMaintenance = f.getBoolean("othersMaintenance"),
                docsOn = f.getBoolean("docsOn"),
                fuelOn = f.getBoolean("fuelOn"),
                restrOn = f.getBoolean("restrOn"),
                voice = f.getBoolean("voice"),
            )
        }

        private fun readSet(o: JSONObject, key: String): Set<String>? {
            val v = o.get(key)
            if (v == "*") return null
            val arr = v as? JSONArray ?: throw IllegalArgumentException("'$key' debe ser \"*\" o una lista")
            return (0 until arr.length()).map { arr.getString(it) }.toCollection(LinkedHashSet())
        }
    }
}
