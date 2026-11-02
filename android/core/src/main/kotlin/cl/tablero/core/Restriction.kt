package cl.tablero.core

import org.json.JSONObject

/** Aviso al entrar a la zona de restricción. `warn` = hoy le toca (o no puede entrar al anillo). */
data class RestrictionNotice(val warn: Boolean, val text: String)

/**
 * Restricción vehicular permanente. Lee public/shared/restriction.json, el MISMO archivo que usa el sitio
 * (public/shared/catalog.js), así que la radio y la vista previa dicen lo mismo.
 * Solo rige en la zona de Santiago (Provincia de Santiago, San Bernardo y Puente Alto): la radio la detecta con el GPS.
 */
class RestrictionRules private constructor(
    private val months: Set<Int>,
    private val zoneName: String,
    private val polygon: List<Pair<Double, Double>>,
    private val digits: Map<String, Map<String, Set<Int>>>,
) {
    /** ¿Está el punto dentro de la zona? (algoritmo del rayo; mismo que `inRestrictionZone` del sitio) */
    fun inZone(lat: Double, lon: Double): Boolean {
        if (lat.isNaN() || lon.isNaN()) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val (yi, xi) = polygon[i]
            val (yj, xj) = polygon[j]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** Días (lun..vie) en que le toca restricción a esa patente. Vacío si el tipo no tiene restricción o falta el dígito. */
    fun daysFor(type: String, digit: String): List<String> {
        val table = digits[type] ?: return emptyList()
        val d = digit.toIntOrNull() ?: return emptyList()
        if (digit.length != 1 || d !in 0..9) return emptyList()
        return WEEKDAY_IDS.filter { d in (table[it] ?: emptySet()) }
    }

    /**
     * Qué avisar al entrar a la zona, o null si no hay nada que avisar.
     * @param weekday id del día (lun..vie) o null si es fin de semana, ver [Reminders.weekdayId]
     * @param minutes minutos desde la medianoche
     * No conoce los festivos: en un festivo avisará igual (mejor un aviso de más que uno de menos).
     */
    fun notice(c: LauncherConfig, weekday: String?, month: Int, minutes: Int): RestrictionNotice? {
        if (!c.restrOn || c.restrType == "nuevo") return null
        if (month !in months || weekday == null) return null
        if (minutes < 7 * 60 + 30 || minutes >= 21 * 60) return null
        val parts = mutableListOf<String>()
        var warn = false
        if (weekday in daysFor(c.restrType, c.restrDigit)) {
            warn = true
            parts += "Hoy tienes restricción vehicular (patente terminada en ${c.restrDigit}). Rige hasta las 21:00."
        }
        if (c.restrType == "sin") {
            warn = true
            parts += "Sin sello verde no puedes circular hoy dentro del anillo Américo Vespucio."
        }
        return if (parts.isEmpty()) null else RestrictionNotice(warn, "Entraste a $zoneName. ${parts.joinToString(" ")}")
    }

    companion object {
        private val WEEKDAY_IDS = listOf("lun", "mar", "mie", "jue", "vie")

        fun parse(json: String): RestrictionRules {
            val o = JSONObject(json)
            val months = o.getJSONObject("season").getJSONArray("months").let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() }
            val zone = o.getJSONObject("zone")
            val poly = zone.getJSONArray("polygon").let { a ->
                (0 until a.length()).map { i -> a.getJSONArray(i).let { p -> p.getDouble(0) to p.getDouble(1) } }
            }
            val d = o.getJSONObject("digits")
            val digits = d.keys().asSequence().associateWith { type ->
                val t = d.getJSONObject(type)
                t.keys().asSequence().associateWith { day -> t.getJSONArray(day).let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } }
            }
            return RestrictionRules(months, zone.getString("name"), poly, digits)
        }
    }
}
