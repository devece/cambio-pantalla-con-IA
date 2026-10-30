package cl.tablero.core

import org.json.JSONArray

/** Un aviso de mantención o documento. `pct` (0 a 1) llena la barra; `warn` lo pinta en alerta. */
data class Reminder(val key: String, val text: String, val pct: Double, val warn: Boolean, val noBar: Boolean = false)

data class MaintenanceItem(val id: String, val name: String, val every: Int)

object Maintenance {
    /** Lee `public/shared/maintenance.json`. */
    fun parse(json: String): List<MaintenanceItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            MaintenanceItem(o.getString("id"), o.getString("name"), o.getInt("every"))
        }
    }
}

/** Mismas cuentas que la vista previa del sitio (public/js/preview.js), para que la radio muestre lo mismo. */
object Reminders {
    private val MONTHS = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )
    private val WEEKDAYS = mapOf("lun" to "lunes", "mar" to "martes", "mie" to "miércoles", "jue" to "jueves", "vie" to "viernes")

    /** Miles con punto, como en Chile: 15000 -> "15.000". */
    fun km(n: Int): String {
        val digits = Math.abs(n.toLong()).toString()
        val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
        return if (n < 0) "-$grouped" else grouped
    }

    /** Id de día del sitio (lun..vie) a partir de `Calendar.DAY_OF_WEEK` (domingo = 1). Sábado y domingo: null. */
    fun weekdayId(calendarDayOfWeek: Int): String? = when (calendarDayOfWeek) {
        2 -> "lun"; 3 -> "mar"; 4 -> "mie"; 5 -> "jue"; 6 -> "vie"
        else -> null
    }

    fun greeting(hour: Int): String = when {
        hour < 12 -> "Buenos días"
        hour < 20 -> "Buenas tardes"
        else -> "Buenas noches"
    }

    /** Texto del saludo ("Hola, Daniel"). */
    fun hello(c: LauncherConfig): String = "${c.greeting}, ${c.name.ifBlank { "amigo" }}"

    /** Kilómetros con el tanque lleno (aproximado). */
    fun fuelRange(c: LauncherConfig): Int = c.tankL * c.kmPerL

    /**
     * @param month mes actual, 1 a 12
     * @param calendarDayOfWeek hoy, con la numeración de `Calendar.DAY_OF_WEEK`
     */
    fun build(c: LauncherConfig, maintenance: List<MaintenanceItem>, month: Int, calendarDayOfWeek: Int): List<Reminder> {
        val out = mutableListOf<Reminder>()

        if (c.oilOn) {
            val left = c.lastOil + c.oilEvery - c.odometer
            out += Reminder(
                key = "Aceite",
                text = if (left >= 0) "faltan ${km(left)} km" else "pasado por ${km(-left)} km",
                pct = (1.0 - left.toDouble() / c.oilEvery).coerceIn(0.0, 1.0),
                warn = left <= 500,
            )
        }

        maintenance.filter { it.id in c.otherMaintenance }.forEach {
            // No llevamos el kilometraje de cada servicio: solo recordamos cada cuánto toca.
            out += Reminder(it.name, "cada ${km(it.every)} km (revisa tu manual)", 0.0, false, noBar = true)
        }

        if (c.docsOn) {
            val diff = (c.rtMonth - month + 12) % 12
            out += Reminder(
                key = "Revisión técnica",
                text = if (diff == 0) "vence este mes" else "vence en ${MONTHS[c.rtMonth - 1]}",
                pct = (1.0 - diff / 12.0).coerceIn(0.0, 1.0),
                warn = diff <= 1,
            )
            val permiso = if (month in 4..8) "agosto" else "marzo"
            out += Reminder("Permiso de circulación", "próximo pago en $permiso", 0.3, false)
            out += Reminder("Seguro obligatorio (SOAP)", "vence el 31 de marzo", 0.3, false)
        }

        if (c.fuelOn) {
            out += Reminder("Combustible", "~${km(fuelRange(c))} km con el tanque lleno", 1.0, false, noBar = true)
        }

        if (c.restrOn && c.restrDays.isNotEmpty()) {
            val today = weekdayId(calendarDayOfWeek)
            val names = WEEKDAYS.filterKeys { it in c.restrDays }.values.joinToString(", ")
            out += if (today != null && today in c.restrDays) {
                Reminder("Restricción vehicular", "HOY tienes restricción ($names)", 0.0, true, noBar = true)
            } else {
                Reminder("Restricción vehicular", names, 0.0, false, noBar = true)
            }
        }
        return out
    }
}

/** Alerta de velocidad. */
object Speed {
    /** Km/h por sobre el límite que se tolera antes de alertar (el GPS no es exacto). */
    const val TOLERANCE = 5

    /**
     * Límite que se muestra, o null si no hay (alerta apagada, o modo automático sin dato de la vía).
     * @param roadLimit límite de la vía según un mapa; hoy la app no lo consulta, por eso en automático es null.
     */
    fun limit(c: LauncherConfig, roadLimit: Int?): Int? = when (c.speedAlert) {
        "manual" -> c.manualLimit
        "auto" -> roadLimit
        else -> null
    }

    fun isOver(speedKmh: Int, limit: Int?): Boolean = limit != null && speedKmh > limit + TOLERANCE
}
