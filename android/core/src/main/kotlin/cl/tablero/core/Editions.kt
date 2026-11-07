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
) {
    /**
     * Lo más amplio de los dos niveles: permite todo lo que permita cualquiera de ellos. El orden de los conjuntos es el de
     * este nivel primero (el primer elemento es el valor de respaldo).
     */
    fun orMoreOf(other: Limits): Limits = Limits(
        themes = widen(themes, other.themes),
        layouts = widen(layouts, other.layouts),
        scenes = widen(scenes, other.scenes),
        seasons = widen(seasons, other.seasons),
        maxApps = maxOf(maxApps, other.maxApps),
        oilOn = oilOn || other.oilOn,
        speedShow = speedShow || other.speedShow,
        speedAlerts = widen(speedAlerts, other.speedAlerts),
        othersMaintenance = othersMaintenance || other.othersMaintenance,
        docsOn = docsOn || other.docsOn,
        fuelOn = fuelOn || other.fuelOn,
        restrOn = restrOn || other.restrOn,
        voice = voice || other.voice,
    )

    private fun widen(a: Set<String>?, b: Set<String>?): Set<String>? =
        if (a == null || b == null) null else LinkedHashSet(a).also { it.addAll(b) } // null = sin límite
}

/** Lee `public/shared/editions.json` (copiado a los assets de la app al compilar). */
class Editions private constructor(private val byTier: Map<Tier, Limits>) {
    fun limits(tier: Tier): Limits = byTier.getValue(tier)

    /**
     * Lo que de verdad puede usar quien está en [tier]: lo de su plan **o** lo de la edición gratis, lo que sea más amplio.
     * Así un código nunca da menos que no tener código (por ejemplo, si la edición gratis lo incluye todo y el plan Esencial no).
     */
    fun effective(tier: Tier): Limits =
        if (tier == Tier.FREE) limits(tier) else limits(tier).orMoreOf(limits(Tier.FREE))

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
