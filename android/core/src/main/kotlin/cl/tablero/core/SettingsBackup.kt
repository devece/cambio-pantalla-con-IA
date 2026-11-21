package cl.tablero.core

import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val settings: Map<String, Any>,
    val files: Map<String, ByteArray> = emptyMap(),
    val appVersion: String = "",
    val createdAtMillis: Long = 0,
)

sealed class BackupResult {
    data class Ok(val data: BackupData) : BackupResult()
    data class Fail(val reason: String) : BackupResult()
}

/** Base64 estándar con relleno, propio (java.util.Base64 y android.util.Base64 no sirven en ambos entornos). */
internal object Base64Propio {
    private const val ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val INVERSO = IntArray(128) { -1 }.also { t -> ALFABETO.forEachIndexed { i, c -> t[c.code] = i } }

    fun encode(b: ByteArray): String {
        val sb = StringBuilder((b.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < b.size) {
            val n = (b[i].toInt() and 0xFF shl 16) or (b[i + 1].toInt() and 0xFF shl 8) or (b[i + 2].toInt() and 0xFF)
            sb.append(ALFABETO[n shr 18 and 63]).append(ALFABETO[n shr 12 and 63])
                .append(ALFABETO[n shr 6 and 63]).append(ALFABETO[n and 63])
            i += 3
        }
        when (b.size - i) {
            1 -> {
                val n = b[i].toInt() and 0xFF shl 16
                sb.append(ALFABETO[n shr 18 and 63]).append(ALFABETO[n shr 12 and 63]).append("==")
            }
            2 -> {
                val n = (b[i].toInt() and 0xFF shl 16) or (b[i + 1].toInt() and 0xFF shl 8)
                sb.append(ALFABETO[n shr 18 and 63]).append(ALFABETO[n shr 12 and 63])
                    .append(ALFABETO[n shr 6 and 63]).append('=')
            }
        }
        return sb.toString()
    }

    /** null si el texto no es base64 válido (largo, caracteres o relleno incorrectos). */
    fun decode(s: String): ByteArray? {
        if (s.length % 4 != 0) return null
        if (s.isEmpty()) return ByteArray(0)
        var relleno = 0
        if (s.endsWith("==")) relleno = 2 else if (s.endsWith("=")) relleno = 1
        val utiles = s.length - relleno
        val out = ByteArray(s.length / 4 * 3 - relleno)
        var o = 0
        var i = 0
        while (i < s.length) {
            var n = 0
            for (k in 0 until 4) {
                val c = s[i + k]
                val v = if (i + k >= utiles) 0 else if (c.code < 128) INVERSO[c.code] else -1
                if (v < 0) return null
                n = (n shl 6) or v
            }
            if (o < out.size) out[o++] = (n shr 16).toByte()
            if (o < out.size) out[o++] = (n shr 8).toByte()
            if (o < out.size) out[o++] = n.toByte()
            i += 4
        }
        return out
    }
}

object SettingsBackup {
    const val FORMAT: Int = 1
    const val PREFIX = "Tablero-copia"
    const val EXTENSION = ".json"
    val FILE_DIRS: List<String> = listOf("mycar", "mycar-rear", "mybg")
    const val MAX_FILES_BYTES = 6_000_000

    private const val APP = "Tablero"
    private const val CLAVE_EXCLUIDA = "odometerCarryM"
    private const val MAX_CLAVE = 64
    private const val MAX_NOMBRE = 64

    private const val MSG_NO_ES = "El archivo no es una copia de Tablero."
    private const val MSG_NUEVA = "La copia viene de una versión más nueva de Tablero. Actualiza la app e inténtalo otra vez."
    private const val MSG_DANADA = "La copia está dañada."
    private const val MSG_GRANDE = "La copia trae archivos demasiado grandes."

    fun fileName(atMillis: Long): String = PREFIX + "-" + FechaUtc.fecha(atMillis) + EXTENSION

    fun isSafePath(path: String): Boolean {
        val partes = path.split('/')
        if (partes.size != 2) return false
        val (dir, nombre) = partes
        if (dir !in FILE_DIRS) return false
        if (nombre.isEmpty() || nombre.length > MAX_NOMBRE) return false
        if (nombre == "." || nombre.contains("..")) return false
        return nombre.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' }
    }

    private fun claveValida(k: String) = k.isNotBlank() && k.length <= MAX_CLAVE && k != CLAVE_EXCLUIDA

    fun write(data: BackupData): String {
        val ajustes = JSONArray()
        for ((k, v) in data.settings) {
            if (!claveValida(k)) continue
            val o = JSONObject().put("k", k)
            when (v) {
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v.toString())
                is Float -> o.put("t", "f").put("v", java.lang.Float.floatToRawIntBits(v))
                is String -> o.put("t", "s").put("v", v)
                else -> continue
            }
            ajustes.put(o)
        }
        val archivos = JSONArray()
        for ((p, b) in data.files) {
            if (!isSafePath(p)) continue
            archivos.put(JSONObject().put("p", p).put("d", Base64Propio.encode(b)))
        }
        return JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("appVersion", data.appVersion)
            .put("createdAt", data.createdAtMillis.toString())
            .put("settings", ajustes)
            .put("files", archivos)
            .toString(2)
    }

    fun read(text: String): BackupResult {
        val raiz = try { JSONObject(text) } catch (e: Exception) { return BackupResult.Fail(MSG_NO_ES) }
        if (raiz.opt("app") != APP) return BackupResult.Fail(MSG_NO_ES)

        val formato = raiz.opt("format")
        if (formato !is Number || formato is Double || formato is Float) return BackupResult.Fail(MSG_DANADA)
        val f = formato.toLong()
        if (f > FORMAT) return BackupResult.Fail(MSG_NUEVA)
        if (f < 1) return BackupResult.Fail(MSG_DANADA)

        val ajustesJson = raiz.opt("settings") as? JSONArray ?: return BackupResult.Fail(MSG_DANADA)

        val ajustes = LinkedHashMap<String, Any>()
        for (i in 0 until ajustesJson.length()) {
            val o = ajustesJson.optJSONObject(i) ?: continue
            val k = o.opt("k") as? String ?: continue
            if (!claveValida(k)) continue
            val valor = decodificarValor(o.opt("t"), o.opt("v")) ?: continue
            ajustes[k] = valor
        }

        val archivos = LinkedHashMap<String, ByteArray>()
        var total = 0L
        val archivosJson = raiz.opt("files") as? JSONArray
        if (archivosJson != null) {
            for (i in 0 until archivosJson.length()) {
                val o = archivosJson.optJSONObject(i) ?: continue
                val p = o.opt("p") as? String ?: continue
                if (!isSafePath(p)) continue
                val d = o.opt("d") as? String ?: continue
                val bytes = Base64Propio.decode(d) ?: continue
                total -= archivos[p]?.size ?: 0
                total += bytes.size
                if (total > MAX_FILES_BYTES) return BackupResult.Fail(MSG_GRANDE)
                archivos[p] = bytes
            }
        }

        val version = raiz.opt("appVersion") as? String ?: ""
        val creada = when (val c = raiz.opt("createdAt")) {
            is String -> c.toLongOrNull() ?: 0L
            is Number -> c.toLong()
            else -> 0L
        }
        return BackupResult.Ok(BackupData(ajustes, archivos, version, creada))
    }

    private fun decodificarValor(tipo: Any?, v: Any?): Any? = when (tipo) {
        "b" -> v as? Boolean
        "i" -> (v as? Number)?.takeIf { it is Int || (it is Long && it in Int.MIN_VALUE..Int.MAX_VALUE) }?.toInt()
        "l" -> when (v) { is String -> v.toLongOrNull(); is Int -> v.toLong(); is Long -> v; else -> null }
        "f" -> (v as? Number)?.takeIf { it is Int || it is Long }?.let { java.lang.Float.intBitsToFloat(it.toInt()) }
        "s" -> v as? String
        else -> null
    }
}
