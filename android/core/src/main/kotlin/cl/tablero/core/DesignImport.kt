package cl.tablero.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * El diseño que viene dentro de la APK descargada del sitio (formato `v: 1`, armado por `api/app.js`): se aplica a la
 * configuración de la app. Todo se valida de nuevo aquí: lo que no existe o no es válido se ignora y queda lo que ya había.
 * La foto del auto no viaja en el diseño: se elige en Ajustes.
 */
object DesignImport {
    /** La configuración con el diseño aplicado, o null si el texto no es un diseño válido. */
    fun apply(base: LauncherConfig, json: String): LauncherConfig? {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return null
        }
        if (root.optInt("v") != 1) return null
        var c = base
        root.optJSONObject("car")?.let { car ->
            val type = car.text("type")
            val color = car.text("color")
            c = c.copy(
                name = Welcome.cleanName(car.text("name")),
                greeting = car.text("greeting").trim().take(16).ifBlank { "Hola" },
                phrase = car.text("phrase").trim().take(28),
                // La app aún no tiene «camioneta 4x4»: la más parecida es camioneta.
                carType = when (type) {
                    "auto", "suv", "camioneta" -> type
                    "4x4" -> "camioneta"
                    else -> c.carType
                },
                carColor = if (HEX.matches(color)) color.lowercase() else c.carColor,
            )
        }
        root.optJSONObject("style")?.let { st ->
            val apps = strings(st.optJSONArray("apps")).filter { it.isNotBlank() }.distinct().take(MAX_APPS)
            c = c.copy(
                look = Look(
                    theme = st.text("theme"),
                    layout = st.text("layout"),
                    scene = st.text("scene"),
                    sceneTime = st.text("sceneTime"),
                    season = st.text("season"),
                    accent = st.text("accent"),
                ).cleaned(),
                apps = apps.ifEmpty { c.apps },
            )
        }
        root.optJSONObject("features")?.let { f ->
            val speedAlert = f.text("speedAlert")
            val restrType = f.text("restrType")
            c = c.copy(
                oilOn = f.optBoolean("oilOn", c.oilOn),
                odometer = f.optInt("odometer", c.odometer).coerceIn(0, 999_999),
                lastOil = f.optInt("lastOil", c.lastOil).coerceIn(0, 999_999),
                oilEvery = f.optInt("oilEvery", c.oilEvery).coerceIn(1_000, 30_000),
                otherMaintenance = strings(f.optJSONArray("others")).filter { it.isNotBlank() }.distinct(),
                docsOn = f.optBoolean("docsOn", c.docsOn),
                rtMonth = f.optInt("rtMonth", c.rtMonth).coerceIn(1, 12),
                fuelOn = f.optBoolean("fuelOn", c.fuelOn),
                tankL = f.optInt("tankL", c.tankL).coerceIn(10, 150),
                kmPerL = f.optInt("kmPerL", c.kmPerL).coerceIn(3, 40),
                speedShow = f.optBoolean("speedShow", c.speedShow),
                speedAlert = if (speedAlert in SPEED_ALERTS) speedAlert else c.speedAlert,
                manualLimit = f.optInt("manualLimit", c.manualLimit).coerceIn(20, 140),
                restrOn = f.optBoolean("restrOn", c.restrOn),
                restrType = if (restrType in RESTR_TYPES) restrType else c.restrType,
                restrDigit = f.text("restrDigit").take(1).filter { it.isDigit() },
                restrDays = strings(f.optJSONArray("restrDays")).filter { it in WEEKDAYS },
                voice = f.optBoolean("voice", c.voice),
            )
        }
        return c
    }

    /** Lo que el diseño dice de su foto: (¿viene recortada?, ¿trae versión de noche?), o null si no trae foto. */
    fun photoInfo(json: String): Pair<Boolean, Boolean>? {
        val photo = try {
            JSONObject(json).optJSONObject("photo")
        } catch (e: Exception) {
            null
        } ?: return null
        return Pair(photo.optBoolean("cut", false), photo.optBoolean("night", false))
    }

    private val HEX = Regex("^#[0-9a-fA-F]{6}$")
    private val SPEED_ALERTS = setOf("auto", "manual", "off")
    private val RESTR_TYPES = setOf("sello", "sin", "nuevo")
    private val WEEKDAYS = setOf("lun", "mar", "mie", "jue", "vie")
    private const val MAX_APPS = 4

    /** Un texto del JSON, o vacío si falta o es null (org.json devolvería la palabra «null»). */
    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "")

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it, "") }
}
