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
    fun apply(base: LauncherConfig, json: String, knownMaintenance: Set<String>? = null): LauncherConfig? {
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
                plate = car.text("plate").trim().uppercase().take(PLATE_MAX),
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
            val country = f.text("country")
            c = c.copy(
                country = if (country in COUNTRIES) country else c.country,
                miles = f.optBoolean("miles", c.miles),
                gallons = f.optBoolean("gallons", c.gallons),
                oilOn = f.optBoolean("oilOn", c.oilOn),
                odometer = f.optInt("odometer", c.odometer).coerceIn(0, 999_999),
                lastOil = f.optInt("lastOil", c.lastOil).coerceIn(0, 999_999),
                oilEvery = f.optInt("oilEvery", c.oilEvery).coerceIn(1_000, 30_000),
                // Solo mantenimientos que existen en el catálogo (si se conoce) y que vengan como texto: lo demás se descarta.
                otherMaintenance = onlyText(f.optJSONArray("others")).filter { it.isNotBlank() && (knownMaintenance == null || it in knownMaintenance) }.distinct(),
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
                restrDigit = digitOr(f.text("restrDigit"), c.restrDigit),
                restrDays = strings(f.optJSONArray("restrDays")).filter { it in WEEKDAYS },
                voice = f.optBoolean("voice", c.voice),
                clock12 = f.optBoolean("clock12", c.clock12),
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
    private const val PLATE_MAX = 10

    /** Un solo dígito del 0 al 9 (como en el sitio y en la pantalla de la radio); vacío = sin dígito; cualquier otra cosa se ignora. */
    private fun digitOr(raw: String, current: String): String = when {
        raw.isEmpty() -> ""
        raw.length == 1 && raw[0] in '0'..'9' -> raw
        else -> current
    }
    private val COUNTRIES = setOf("CL", "CO", "EC", "MX")
    private val SPEED_ALERTS = setOf("auto", "manual", "off")
    private val RESTR_TYPES = setOf("sello", "sin", "nuevo")
    private val WEEKDAYS = setOf("lun", "mar", "mie", "jue", "vie")
    private const val MAX_APPS = 4

    /** Un texto del JSON, o vacío si falta o es null (org.json devolvería la palabra «null»). */
    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "")

    /** Los elementos que son texto de verdad (un número como 1 no se convierte en «1»). */
    private fun onlyText(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.opt(it) as? String }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it, "") }
}
