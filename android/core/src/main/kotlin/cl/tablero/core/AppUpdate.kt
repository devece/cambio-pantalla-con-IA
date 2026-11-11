package cl.tablero.core

import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest

/**
 * La versión más nueva publicada (el archivo version.json que sube la compilación junto al APK).
 * `sha256` y `size` son la huella y el tamaño de ese APK: la app los comprueba antes de instalar. `commit` dice de qué commit salió.
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val commit: String = "",
    val sha256: String = "",
    val size: Long = 0,
)

/** Resultado de comprobar un APK descargado contra lo que dice el version.json. */
enum class DownloadCheck { OK, SIN_HUELLA, TAMANO, HUELLA }

/** Reglas del aviso de actualización, sin Android para poder probarlas en cualquier PC. */
object AppUpdate {
    private val HEX64 = Regex("^[0-9a-f]{64}$")
    private val HEX_COMMIT = Regex("^[0-9a-f]{7,40}$")

    /** Lee el version.json. Si viene roto o sin número de versión, devuelve null (nunca se avisa por algo dudoso). */
    fun parse(json: String?): UpdateInfo? {
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(json)
            val code = o.optInt("versionCode", 0)
            if (code <= 0) return null
            val sha = o.optString("sha256", "").lowercase()
            val commit = o.optString("commit", "").lowercase()
            UpdateInfo(
                versionCode = code,
                versionName = o.optString("versionName", "").take(32),
                commit = if (HEX_COMMIT.matches(commit)) commit else "",
                sha256 = if (HEX64.matches(sha)) sha else "",
                size = o.optLong("size", 0).coerceAtLeast(0),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Hay que avisar solo si la publicada es más nueva que la instalada y el usuario no la dejó para después. */
    fun shouldOffer(installed: Int, latest: UpdateInfo?, postponed: Int): Boolean =
        latest != null && latest.versionCode > installed && latest.versionCode > postponed

    /** Solo se ofrece una actualización que se pueda verificar: tiene que traer la huella SHA-256 de su APK. */
    fun isVerifiable(info: UpdateInfo?): Boolean = info != null && HEX64.matches(info.sha256)

    /** Compara el APK descargado (su tamaño y su huella) con lo publicado. */
    fun checkDownload(info: UpdateInfo, size: Long, sha256: String): DownloadCheck = when {
        !isVerifiable(info) -> DownloadCheck.SIN_HUELLA
        info.size > 0 && info.size != size -> DownloadCheck.TAMANO
        !info.sha256.equals(sha256, ignoreCase = true) -> DownloadCheck.HUELLA
        else -> DownloadCheck.OK
    }

    fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun sha256Hex(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(32 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return hex(md.digest())
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}
