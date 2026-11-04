package cl.tablero.launcher

import android.content.Context
import cl.tablero.core.LauncherConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * El diseño que el cliente armó en el sitio. Se descarga una vez al activar el código y queda guardado en la radio,
 * con su foto, para que la pantalla de inicio funcione sin internet.
 */
class DesignStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "design")
    private val file = File(dir, "design.json")

    fun has(): Boolean = file.exists()

    /** El diseño como texto JSON, o null si no hay (o está dañado). */
    fun loadText(): String? = try {
        file.readText().also { JSONObject(it) }
    } catch (e: Exception) {
        null
    }

    fun clear() {
        dir.deleteRecursively()
    }

    /**
     * Guarda el diseño y baja su foto. Bloquea el hilo: usar fuera del hilo principal.
     * Si la foto no se puede bajar, el diseño igual queda guardado (sin foto).
     */
    fun save(designJson: String) {
        val design = JSONObject(designJson)
        dir.mkdirs()
        val photo = design.optJSONObject("photo")
        if (photo != null) {
            val day = download(photo.optString("day"), "day.png")
            if (day == null) {
                design.remove("photo")
            } else {
                photo.put("day", day)
                val night = download(photo.optString("night"), "night.png")
                if (night != null) photo.put("night", night) else photo.remove("night")
            }
        }
        file.writeText(design.toString())
    }

    /** Pasa a la configuración de la app lo que el cliente eligió (nombre, saludo, restricción, velocidad...). */
    fun applyTo(base: LauncherConfig): LauncherConfig {
        val text = loadText() ?: return base
        val config = JSONObject(text).optJSONObject("config") ?: return base
        var out = base
        config.optJSONObject("car")?.let { car ->
            out = out.copy(
                name = car.optString("name", out.name),
                greeting = car.optString("greeting", out.greeting).ifBlank { out.greeting },
                phrase = car.optString("phrase", ""),
            )
        }
        config.optJSONObject("features")?.let { f ->
            out = out.copy(
                voice = f.optBoolean("voice", out.voice),
                speedShow = f.optBoolean("speedShow", out.speedShow),
                speedAlert = f.optString("speedAlert", out.speedAlert),
                manualLimit = f.optInt("manualLimit", out.manualLimit),
                restrOn = f.optBoolean("restrOn", false),
                restrType = f.optString("restrType", out.restrType),
                restrDigit = f.optString("restrDigit", ""),
                restrDays = strings(f.optJSONArray("restrDays")),
            )
        }
        return out
    }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

    /** Devuelve la ruta (file://) del archivo bajado, o null si no se pudo. */
    private fun download(url: String, name: String): String? {
        if (!url.startsWith("https://")) return null
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode !in 200..299) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty() || bytes.size > MAX_PHOTO_BYTES) return null
            val target = File(dir, name)
            target.writeBytes(bytes)
            "file://" + target.absolutePath
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val MAX_PHOTO_BYTES = 4_000_000
    }
}
