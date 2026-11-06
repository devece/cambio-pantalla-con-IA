package cl.tablero.core

/** Lo que el usuario eligió. Los ids son los mismos de public/shared/catalog.js. */
data class LauncherConfig(
    val name: String = "",
    val greeting: String = "Hola",
    val phrase: String = "",
    val carType: String = "suv",
    val carColor: String = "#b3261e",
    val theme: String = "ruta",
    val layout: String = "tarjetas",
    val scene: String = "tema",
    val season: String = "ninguna",
    val apps: List<String> = listOf("maps", "spotify"),
    val oilOn: Boolean = true,
    val odometer: Int = 0,
    val lastOil: Int = 0,
    val oilEvery: Int = 5000,
    val otherMaintenance: List<String> = emptyList(),
    /** Kilometraje al que se hizo por última vez cada mantenimiento de [otherMaintenance] (id -> km). */
    val maintDone: Map<String, Int> = emptyMap(),
    val docsOn: Boolean = false,
    val rtMonth: Int = 1,
    val fuelOn: Boolean = false,
    val tankL: Int = 45,
    val kmPerL: Int = 12,
    val speedShow: Boolean = true,
    val speedAlert: String = "auto",
    val manualLimit: Int = 60,
    val restrOn: Boolean = false,
    val restrType: String = "sello",
    val restrDigit: String = "",
    val restrDays: List<String> = emptyList(),
    val voice: Boolean = false,
    /** Botones del volante que el usuario asignó (código de tecla -> acción). */
    val wheelKeys: Map<Int, WheelAction> = emptyMap(),
    /** El primer arranque guiado ya se terminó o se omitió. */
    val welcomeDone: Boolean = false,
    /** Sumar al kilometraje los km que mide el GPS mientras se maneja (solo si ya se anotó un kilometraje). */
    val odometerAuto: Boolean = true,
    /** El adaptador OBD que el usuario eligió (por defecto ninguno). */
    val obd: ObdLinkConfig = ObdLinkConfig(),
)

/** El "candado": ajusta una configuración a lo que permite el nivel de la radio. */
object Gate {
    fun apply(c: LauncherConfig, l: Limits): LauncherConfig = c.copy(
        theme = pick(c.theme, l.themes, c.theme),
        layout = pick(c.layout, l.layouts, c.layout),
        scene = pick(c.scene, l.scenes, c.scene),
        season = pick(c.season, l.seasons, c.season),
        apps = c.apps.distinct().take(l.maxApps),
        oilOn = c.oilOn && l.oilOn,
        speedShow = c.speedShow && l.speedShow,
        speedAlert = pickAlert(c.speedAlert, l.speedAlerts),
        otherMaintenance = if (l.othersMaintenance) c.otherMaintenance else emptyList(),
        docsOn = c.docsOn && l.docsOn,
        fuelOn = c.fuelOn && l.fuelOn,
        restrOn = c.restrOn && l.restrOn,
        voice = c.voice && l.voice,
    )

    /** Funciones que el usuario pidió pero su nivel no incluye (para avisar "requiere código"). */
    fun locked(c: LauncherConfig, l: Limits): List<String> = buildList {
        if (c.docsOn && !l.docsOn) add("docs")
        if (c.fuelOn && !l.fuelOn) add("fuel")
        if (c.restrOn && !l.restrOn) add("restr")
        if (c.voice && !l.voice) add("voice")
        if (c.otherMaintenance.isNotEmpty() && !l.othersMaintenance) add("maintenance")
        if (l.themes != null && c.theme !in l.themes) add("theme")
        if (l.seasons != null && c.season !in l.seasons) add("season")
    }

    /** Si `value` no está permitido, usa el primero que sí lo está (el de respaldo si no hay lista). */
    private fun pick(value: String, allowed: Set<String>?, fallback: String): String =
        if (allowed == null || value in allowed) value else allowed.firstOrNull() ?: fallback

    private fun pickAlert(value: String, allowed: Set<String>?): String = when {
        allowed == null || value in allowed -> value
        "auto" in allowed -> "auto"
        else -> allowed.firstOrNull() ?: value
    }
}
