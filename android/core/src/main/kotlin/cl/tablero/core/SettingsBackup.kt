package cl.tablero.core

import org.json.JSONArray
import org.json.JSONObject

/** Lo que se guarda en una copia de seguridad: los ajustes tal como están en la radio y los archivos propios (foto del auto, fondo). */
data class BackupData(
    /** Ajustes: clave → Boolean, Int, Long, Float o String. */
    val settings: Map<String, Any>,
    /** Archivos de la app: ruta relativa (`mycar/car.png`) → bytes. */
    val files: Map<String, ByteArray> = emptyMap(),
    val appVersion: String = "",
    val createdAtMillis: Long = 0,
)

/** Resultado de leer una copia: o los datos, o el motivo (en palabras simples) por el que no sirve. */
sealed class BackupResult {
    data class Ok(val data: BackupData) : BackupResult()
    data class Fail(val reason: String) : BackupResult()
}

/**
 * Copia de seguridad de la configuración en UN archivo de texto (JSON): lo que el usuario configuró y su foto, para las radios
 * que hay que restablecer de fábrica cada tanto, o para pasar todo a otra radio. Nada sale a internet: el archivo lo mueve el
 * usuario. Sin Android, para poder probarlo en cualquier PC.
 */
object SettingsBackup {
    const val FORMAT = 1
    const val APP = "tablero"
    const val EXTENSION = ".json"
    const val PREFIX = "Tablero-copia"

    /** Ajustes que no se copian: son de esta radio y de este momento (los metros sueltos que juntó el GPS). */
    val SKIP = setOf("odometerCarryM")

    /** Carpetas de la app cuyos archivos viajan en la copia. */
    val FILE_DIRS = listOf("mycar", "mycar-rear", "mybg")

    /** Tope de bytes de archivos en una copia (la foto ya viene achicada), para no leer un archivo enorme ni ajeno. */
    const val MAX_FILES_BYTES = 6_000_000

    /** Nombre del archivo, con la fecha (`Tablero-copia-2026-11-21.json`). */
    fun fileName(atMillis: Long): String {
        val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(atMillis))
        return "$PREFIX-$d$EXTENSION"
    }

    /** ¿Es la ruta de un archivo permitido? Solo `carpeta/nombre` dentro de [FILE_DIRS], sin `..` ni rutas absolutas. */
    fun isSafePath(path: String): Boolean {
        val parts = path.split('/')
        return parts.size == 2 && parts[0] in FILE_DIRS && Regex("[A-Za-z0-9._-]{1,64}").matches(parts[1]) && ".." !in parts[1]
    }

    // Nombres de tipo dentro del archivo. Cada valor lleva su tipo porque SharedPreferences distingue Int de Long, etc.
    private const val T_TEXTO = "texto"
    private const val T_ENTERO = "entero"
    private const val T_LARGO = "largo"
    private const val T_DECIMAL = "decimal"
    private const val T_LOGICO = "logico"

    /** Arma el texto de la copia: una lista de ajustes `{clave, tipo, valor}` y una lista de archivos adjuntos `{ruta, base64}`. */
    fun write(data: BackupData): String {
        val valores = JSONArray()
        for ((clave, valor) in data.settings.toSortedMap()) {
            if (clave in SKIP) continue
            val item = JSONObject().put("clave", clave)
            when (valor) {
                is String -> item.put("tipo", T_TEXTO).put("valor", valor)
                is Int -> item.put("tipo", T_ENTERO).put("valor", valor)
                is Long -> item.put("tipo", T_LARGO).put("valor", valor)
                is Float -> item.put("tipo", T_DECIMAL).put("valor", valor.toDouble())
                is Boolean -> item.put("tipo", T_LOGICO).put("valor", valor)
                else -> continue // un tipo que Tablero no usa: no se copia
            }
            valores.put(item)
        }
        val adjuntos = JSONArray()
        for ((ruta, bytes) in data.files.toSortedMap()) {
            if (isSafePath(ruta)) adjuntos.put(JSONObject().put("ruta", ruta).put("base64", B64.encode(bytes)))
        }
        return JSONObject()
            .put("programa", APP)
            .put("esquema", FORMAT)
            .put("version", data.appVersion)
            .put("creada", data.createdAtMillis)
            .put("valores", valores)
            .put("adjuntos", adjuntos)
            .toString(1)
    }

    fun read(text: String): BackupResult {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            return BackupResult.Fail("El archivo no es una copia de Tablero.")
        }
        if (root.optString("programa") != APP) return BackupResult.Fail("El archivo no es una copia de Tablero.")
        val esquema = root.optInt("esquema", 0)
        if (esquema < 1) return BackupResult.Fail("La copia está dañada.")
        if (esquema > FORMAT) return BackupResult.Fail("La copia viene de una versión más nueva de Tablero. Actualiza la app e inténtalo otra vez.")
        val valores = root.optJSONArray("valores") ?: return BackupResult.Fail("La copia está dañada.")

        val settings = LinkedHashMap<String, Any>()
        for (i in 0 until valores.length()) {
            val item = valores.optJSONObject(i) ?: continue
            val clave = item.optString("clave")
            if (clave.isBlank() || clave.length > 64 || clave in SKIP || !item.has("valor")) continue
            val valor: Any = try {
                when (item.optString("tipo")) {
                    T_TEXTO -> item.getString("valor")
                    T_ENTERO -> item.getInt("valor")
                    T_LARGO -> item.getLong("valor")
                    T_DECIMAL -> item.getDouble("valor").toFloat()
                    T_LOGICO -> item.getBoolean("valor")
                    else -> continue
                }
            } catch (e: Exception) {
                continue // un valor mal escrito no tumba el resto
            }
            settings[clave] = valor
        }

        val files = LinkedHashMap<String, ByteArray>()
        var total = 0
        val adjuntos = root.optJSONArray("adjuntos")
        if (adjuntos != null) for (i in 0 until adjuntos.length()) {
            val item = adjuntos.optJSONObject(i) ?: continue
            val ruta = item.optString("ruta")
            if (!isSafePath(ruta)) continue
            val bytes = try {
                B64.decode(item.getString("base64"))
            } catch (e: Exception) {
                continue
            }
            total += bytes.size
            if (total > MAX_FILES_BYTES) return BackupResult.Fail("La copia trae archivos demasiado grandes.")
            files[ruta] = bytes
        }
        return BackupResult.Ok(BackupData(settings, files, root.optString("version"), root.optLong("creada", 0)))
    }
}

/** Base64 propio: `java.util.Base64` no existe en Android 5 a 7 y `android.util.Base64` no existe en las pruebas del PC. */
internal object B64 {
    private const val ABC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(b: ByteArray): String {
        val out = StringBuilder((b.size + 2) / 3 * 4)
        var i = 0
        while (i < b.size) {
            val n = (b[i].toInt() and 0xff shl 16) or
                ((if (i + 1 < b.size) b[i + 1].toInt() and 0xff else 0) shl 8) or
                (if (i + 2 < b.size) b[i + 2].toInt() and 0xff else 0)
            out.append(ABC[n shr 18 and 63]).append(ABC[n shr 12 and 63])
            out.append(if (i + 1 < b.size) ABC[n shr 6 and 63] else '=')
            out.append(if (i + 2 < b.size) ABC[n and 63] else '=')
            i += 3
        }
        return out.toString()
    }

    /** Lanza [IllegalArgumentException] si el texto no es Base64 válido. */
    fun decode(s: String): ByteArray {
        val clean = s.filterNot { it.isWhitespace() }.trimEnd('=')
        val out = java.io.ByteArrayOutputStream(clean.length * 3 / 4)
        var acc = 0
        var bits = 0
        for (ch in clean) {
            val v = ABC.indexOf(ch)
            require(v >= 0) { "Base64 inválido" }
            acc = acc shl 6 or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write(acc shr bits and 0xff)
            }
        }
        return out.toByteArray()
    }
}
