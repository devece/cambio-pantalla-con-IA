package cl.tablero.launcher

import android.content.Context
import cl.tablero.core.LauncherConfig

/** Guarda en la radio lo que el usuario eligió en Ajustes. Funciona sin internet. */
class SettingsStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("tablero_settings", Context.MODE_PRIVATE)

    fun load(): LauncherConfig {
        val d = LauncherConfig()
        return d.copy(
            name = p.getString("name", d.name) ?: d.name,
            greeting = p.getString("greeting", d.greeting) ?: d.greeting,
            carType = p.getString("carType", d.carType) ?: d.carType,
            carColor = p.getString("carColor", d.carColor) ?: d.carColor,
            apps = list("apps", d.apps),
            oilOn = p.getBoolean("oilOn", d.oilOn),
            odometer = p.getInt("odometer", d.odometer),
            lastOil = p.getInt("lastOil", d.lastOil),
            oilEvery = p.getInt("oilEvery", d.oilEvery),
            otherMaintenance = list("others", d.otherMaintenance),
            docsOn = p.getBoolean("docsOn", d.docsOn),
            rtMonth = p.getInt("rtMonth", d.rtMonth).coerceIn(1, 12),
            fuelOn = p.getBoolean("fuelOn", d.fuelOn),
            tankL = p.getInt("tankL", d.tankL),
            kmPerL = p.getInt("kmPerL", d.kmPerL),
            speedShow = p.getBoolean("speedShow", d.speedShow),
            speedAlert = p.getString("speedAlert", d.speedAlert) ?: d.speedAlert,
            manualLimit = p.getInt("manualLimit", d.manualLimit),
            restrOn = p.getBoolean("restrOn", d.restrOn),
            restrDays = list("restrDays", d.restrDays),
            voice = p.getBoolean("voice", d.voice),
        )
    }

    fun save(c: LauncherConfig) {
        p.edit()
            .putString("name", c.name)
            .putString("greeting", c.greeting)
            .putString("carType", c.carType)
            .putString("carColor", c.carColor)
            .putString("apps", c.apps.joinToString(","))
            .putBoolean("oilOn", c.oilOn)
            .putInt("odometer", c.odometer)
            .putInt("lastOil", c.lastOil)
            .putInt("oilEvery", c.oilEvery)
            .putString("others", c.otherMaintenance.joinToString(","))
            .putBoolean("docsOn", c.docsOn)
            .putInt("rtMonth", c.rtMonth)
            .putBoolean("fuelOn", c.fuelOn)
            .putInt("tankL", c.tankL)
            .putInt("kmPerL", c.kmPerL)
            .putBoolean("speedShow", c.speedShow)
            .putString("speedAlert", c.speedAlert)
            .putInt("manualLimit", c.manualLimit)
            .putBoolean("restrOn", c.restrOn)
            .putString("restrDays", c.restrDays.joinToString(","))
            .putBoolean("voice", c.voice)
            .apply()
    }

    private fun list(key: String, default: List<String>): List<String> {
        val raw = p.getString(key, null) ?: return default
        return raw.split(",").filter { it.isNotBlank() }
    }
}
