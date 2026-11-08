package cl.tablero.launcher

import android.content.Context
import android.content.SharedPreferences
import cl.tablero.core.LauncherConfig
import cl.tablero.core.ObdLinkConfig
import cl.tablero.core.WheelKeys

/** Guarda en la radio lo que el usuario eligió en Ajustes. Funciona sin internet. */
class SettingsStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("tablero_settings", Context.MODE_PRIVATE)

    fun load(): LauncherConfig {
        val d = LauncherConfig()
        return d.copy(
            name = p.getString("name", d.name) ?: d.name,
            greeting = p.getString("greeting", d.greeting) ?: d.greeting,
            phrase = p.getString("phrase", d.phrase) ?: d.phrase,
            carType = p.getString("carType", d.carType) ?: d.carType,
            carColor = p.getString("carColor", d.carColor) ?: d.carColor,
            apps = list("apps", d.apps),
            oilOn = p.getBoolean("oilOn", d.oilOn),
            odometer = p.getInt("odometer", d.odometer),
            lastOil = p.getInt("lastOil", d.lastOil),
            oilEvery = p.getInt("oilEvery", d.oilEvery),
            otherMaintenance = list("others", d.otherMaintenance),
            maintDone = list("maintDone", emptyList()).mapNotNull {
                val (id, km) = it.split(":").takeIf { p -> p.size == 2 } ?: return@mapNotNull null
                km.toIntOrNull()?.let { v -> id to v }
            }.toMap(),
            docsOn = p.getBoolean("docsOn", d.docsOn),
            rtMonth = p.getInt("rtMonth", d.rtMonth).coerceIn(1, 12),
            fuelOn = p.getBoolean("fuelOn", d.fuelOn),
            tankL = p.getInt("tankL", d.tankL),
            kmPerL = p.getInt("kmPerL", d.kmPerL),
            speedShow = p.getBoolean("speedShow", d.speedShow),
            speedAlert = p.getString("speedAlert", d.speedAlert) ?: d.speedAlert,
            manualLimit = p.getInt("manualLimit", d.manualLimit),
            restrOn = p.getBoolean("restrOn", d.restrOn),
            restrType = p.getString("restrType", d.restrType) ?: d.restrType,
            restrDigit = p.getString("restrDigit", d.restrDigit) ?: d.restrDigit,
            restrDays = list("restrDays", d.restrDays),
            voice = p.getBoolean("voice", d.voice),
            wheelKeys = WheelKeys.parse(p.getString("wheelKeys", null)),
            welcomeDone = p.getBoolean("welcomeDone", d.welcomeDone),
            odometerAuto = p.getBoolean("odometerAuto", d.odometerAuto),
            obd = ObdLinkConfig.fromStored(
                p.getString("obdKind", null),
                p.getString("obdAddress", null),
                p.getString("obdName", null),
                p.getInt("obdPort", ObdLinkConfig.DEFAULT_WIFI_PORT),
            ),
        )
    }

    fun save(c: LauncherConfig) {
        // Si el usuario corrigió el kilometraje a mano, los metros sueltos que el GPS había juntado ya no valen.
        val corrected = p.contains("odometer") && p.getInt("odometer", 0) != c.odometer
        p.edit()
            .apply { if (corrected) putInt(CARRY_KEY, 0) }
            .putString("name", c.name)
            .putString("greeting", c.greeting)
            .putString("phrase", c.phrase)
            .putString("carType", c.carType)
            .putString("carColor", c.carColor)
            .putString("apps", c.apps.joinToString(","))
            .putBoolean("oilOn", c.oilOn)
            .putInt("odometer", c.odometer)
            .putInt("lastOil", c.lastOil)
            .putInt("oilEvery", c.oilEvery)
            .putString("others", c.otherMaintenance.joinToString(","))
            .putString("maintDone", c.maintDone.entries.joinToString(",") { "${it.key}:${it.value}" })
            .putBoolean("docsOn", c.docsOn)
            .putInt("rtMonth", c.rtMonth)
            .putBoolean("fuelOn", c.fuelOn)
            .putInt("tankL", c.tankL)
            .putInt("kmPerL", c.kmPerL)
            .putBoolean("speedShow", c.speedShow)
            .putString("speedAlert", c.speedAlert)
            .putInt("manualLimit", c.manualLimit)
            .putBoolean("restrOn", c.restrOn)
            .putString("restrType", c.restrType)
            .putString("restrDigit", c.restrDigit)
            .putString("restrDays", c.restrDays.joinToString(","))
            .putBoolean("voice", c.voice)
            .putString("wheelKeys", WheelKeys.serialize(c.wheelKeys))
            .putBoolean("welcomeDone", c.welcomeDone)
            .putBoolean("odometerAuto", c.odometerAuto)
            .putString("obdKind", c.obd.kind.id)
            .putString("obdAddress", c.obd.address)
            .putString("obdName", c.obd.name)
            .putInt("obdPort", c.obd.port)
            .apply()
    }

    /* ---------- kilometraje automático (lo usa OdometerService) ---------- */

    /** Kilometraje anotado (0 = el usuario nunca lo anotó). */
    fun odometer(): Int = p.getInt("odometer", 0)

    /** ¿Sumar solo los km del GPS? Solo tiene sentido si ya hay un kilometraje anotado. */
    fun autoOdometerOn(): Boolean = p.getBoolean("odometerAuto", true) && odometer() > 0

    /** Metros sueltos (0..999) que el GPS juntó y todavía no completan un kilómetro. */
    fun odometerCarryM(): Int = p.getInt(CARRY_KEY, 0).coerceIn(0, 999)

    fun setOdometerCarryM(m: Int) {
        p.edit().putInt(CARRY_KEY, m.coerceIn(0, 999)).apply()
    }

    /** Suma [km] al kilometraje y devuelve el nuevo valor. Toca solo ese dato: no pisa lo que se esté editando en Ajustes. */
    fun addKm(km: Int): Int {
        val next = (odometer() + km).coerceIn(0, MAX_KM)
        p.edit().putInt("odometer", next).apply()
        return next
    }

    /**
     * Pone el kilometraje que informa el propio auto (scanner OBD), que es más exacto que el del GPS. Solo si el usuario ya
     * anotó uno (sin punto de partida el kilometraje está "sin usar") y la diferencia es de al menos 1 km, para no escribir
     * a cada lectura. Descarta los metros sueltos que el GPS había juntado. Devuelve el valor si lo cambió, o null.
     */
    fun setOdometerFromCar(km: Int): Int? {
        if (odometer() <= 0 || km !in 1..MAX_KM || km == odometer()) return null
        p.edit().putInt("odometer", km).putInt(CARRY_KEY, 0).apply()
        return km
    }

    /** Avisa cuando cambia el kilometraje (a mano o por el GPS). Hay que guardar una referencia: Android no la retiene. */
    fun onOdometerChange(callback: (Int) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "odometer") callback(odometer()) }
        p.registerOnSharedPreferenceChangeListener(l)
        return l
    }

    fun removeOdometerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        p.unregisterOnSharedPreferenceChangeListener(l)
    }

    private companion object {
        const val CARRY_KEY = "odometerCarryM"
        const val MAX_KM = 999_999
    }

    private fun list(key: String, default: List<String>): List<String> {
        val raw = p.getString(key, null) ?: return default
        return raw.split(",").filter { it.isNotBlank() }
    }
}
